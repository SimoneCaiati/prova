/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.storage;

import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.util.Constants;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.function.Consumer;

import static com.graphhopper.util.Helper.nf;

/**
 * DataAccess-based storage for CH shortcuts. Stores shortcuts and CH levels sequentially using two DataAccess objects
 * and gives read/write access to the different shortcut and node fields.
 * <p>
 * This can be seen as an extension to a base graph: We assign a CH level to each node and add additional edges to
 * the graph ('shortcuts'). The shortcuts need to be ordered in a certain way, but this is not enforced here.
 *
 * @see CHStorageBuilder to build a valid storage that can be used for routing
 */
public class CHStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHStorage.class);
    // we store double weights as integers (rounded to three decimal digits)
    private static final double WEIGHT_FACTOR = 1000;
    // the maximum integer value we can store
    private static final long MAX_STORED_INTEGER_WEIGHT = ((long) Integer.MAX_VALUE) << 1;
    // the maximum double weight we can store. if this is exceeded the shortcut will gain infinite weight, potentially yielding connection-not-found errors
    private static final double MAX_WEIGHT = MAX_STORED_INTEGER_WEIGHT / WEIGHT_FACTOR;
    private static final double MIN_WEIGHT = 1 / WEIGHT_FACTOR;

    // shortcuts
    private final DataAccess shortcuts;
    private final int sNODEA;
    private final int sNodeb;
    private final int sWeight;
    private final int sSkipEdge1;
    private final int sSkipEdge2;
    private final int sOrigKeyFirst;
    private final int sOrigKeyLast;
    private int shortcutEntryBytes;
    private int shortcutCount = 0;

    // nodes
    private final DataAccess nodesCH;
    private final int nLevel;
    private final int nLastSc;
    private int nodeCHEntryBytes;
    private int nodeCount = -1;

    private boolean edgeBased;
    // some shortcuts exceed the maximum storable weight, and we count them here
    private int numShortcutsExceedingWeight;

    // use this to report shortcuts with too small weights
    private Consumer<LowWeightShortcut> lowShortcutWeightConsumer;

    public static CHStorage fromGraph(BaseGraph baseGraph, CHConfig chConfig) throws MMapDataAccess.MappaExce {
        String name = chConfig.getName();
        boolean edgeBased = chConfig.isEdgeBased();
        if (!baseGraph.isFrozen())
            throw new IllegalStateException("graph must be frozen before we can create ch graphs");
        CHStorage store = new CHStorage(baseGraph.getDirectory(), name, baseGraph.getSegmentSize(), edgeBased);
        store.setLowShortcutWeightConsumer(s -> {
            // we just log these to find mapping errors
            NodeAccess nodeAccess = baseGraph.getNodeAccess();
            LOGGER.warn("Setting weights smaller than {} is not allowed. You passed: {} for the shortcut nodeA ({},{}) nodeB ({},{})",
                    s.minWeight, s.weight, nodeAccess.getLat(s.nodeA), nodeAccess.getLon(s.nodeA), nodeAccess.getLat(s.nodeB), nodeAccess.getLon(s.nodeB));

        });
        // we use a rather small value here. this might result in more allocations later, but they should
        // not matter that much. if we expect a too large value the shortcuts DataAccess will end up
        // larger than needed, because we do not do something like trimToSize in the end.
        double expectedShortcuts = 0.3 * baseGraph.getEdges();
        store.create(baseGraph.getNodes(), (int) expectedShortcuts);
        return store;
    }

    public CHStorage(Directory dir, String name, int segmentSize, boolean edgeBased) {
        this.edgeBased = edgeBased;
        this.nodesCH = dir.create("nodes_ch_" + name, dir.getDefaultType("nodes_ch_" + name, true), segmentSize);
        this.shortcuts = dir.create("shortcuts_" + name, dir.getDefaultType("shortcuts_" + name, true), segmentSize);
        // shortcuts are stored consecutively using this layout (the last two entries only exist for edge-based):
        // NODEA | NODEB | WEIGHT | SKIP_EDGE1 | SKIP_EDGE2 | S_ORIG_FIRST | S_ORIG_LAST
        sNODEA = 0;
        sNodeb = sNODEA + 4;
        sWeight = sNodeb + 4;
        sSkipEdge1 = sWeight + 4;
        sSkipEdge2 = sSkipEdge1 + 4;
        sOrigKeyFirst = sSkipEdge2 + (edgeBased ? 4 : 0);
        sOrigKeyLast = sOrigKeyFirst + (edgeBased ? 4 : 0);
        shortcutEntryBytes = sOrigKeyLast + 4;

        // nodes/levels are stored consecutively using this layout:
        // LEVEL | N_LAST_SC
        nLevel = 0;
        nLastSc = nLevel + 4;
        nodeCHEntryBytes = nLastSc + 4;
    }

    /**
     * Sets a callback called for shortcuts that are below the minimum weight. e.g. used to find/log mapping errors
     */
    public void setLowShortcutWeightConsumer(Consumer<LowWeightShortcut> lowWeightShortcutConsumer) {
        this.lowShortcutWeightConsumer = lowWeightShortcutConsumer;
    }

    /**
     * Creates a new storage. Alternatively we could load an existing one using {@link #loadExisting()}}.
     * The number of nodes must be given here while the expected number of shortcuts can
     * be given to prevent some memory allocations, but is not a requirement. When in doubt rather use a small value
     * so the resulting files/byte arrays won't be unnecessarily large.
     *  we could also trim down the shortcuts DataAccess when we are done adding shortcuts
     */
    public void create(int nodes, int expectedShortcuts) throws MMapDataAccess.MappaExce {
        if (nodeCount >= 0)
            throw new IllegalStateException("CHStorage can only be created once");
        if (nodes < 0)
            throw new IllegalStateException("CHStorage must be created with a positive number of nodes");
        nodesCH.create((long) nodes * nodeCHEntryBytes);
        nodeCount = nodes;
        for (int node = 0; node < nodes; node++)
            setLastShortcut(toNodePointer(node), -1);
        shortcuts.create((long) expectedShortcuts * shortcutEntryBytes);
    }

    public void flush() throws MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce {
        // nodes
        nodesCH.setHeader(0, Constants.VERSION_NODE_CH);
        nodesCH.setHeader(4, nodeCount);
        nodesCH.setHeader(8, nodeCHEntryBytes);
        nodesCH.flush();

        // shortcuts
        shortcuts.setHeader(0, Constants.VERSION_SHORTCUT);
        shortcuts.setHeader(4, shortcutCount);
        shortcuts.setHeader(8, shortcutEntryBytes);
        shortcuts.setHeader(12, numShortcutsExceedingWeight);
        shortcuts.setHeader(16, edgeBased ? 1 : 0);
        shortcuts.flush();
    }

    public boolean loadExisting() throws RAMDataAccess.RamExce, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        if (!nodesCH.loadExisting() || !shortcuts.loadExisting())
            return false;

        // nodes
        int nodesCHVersion = nodesCH.getHeader(0);
        GHUtility.checkDAVersion(nodesCH.getName(), Constants.VERSION_NODE_CH, nodesCHVersion);
        nodeCount = nodesCH.getHeader(4);
        nodeCHEntryBytes = nodesCH.getHeader(8);

        // shortcuts
        int shortcutsVersion = shortcuts.getHeader(0);
        GHUtility.checkDAVersion(shortcuts.getName(), Constants.VERSION_SHORTCUT, shortcutsVersion);
        shortcutCount = shortcuts.getHeader(4);
        shortcutEntryBytes = shortcuts.getHeader(8);
        numShortcutsExceedingWeight = shortcuts.getHeader(12);
        edgeBased = shortcuts.getHeader(16) == 1;

        return true;
    }

    public void close() {
        nodesCH.close();
        shortcuts.close();
    }

    /**
     * Adds a shortcut to the storage. Shortcuts are stored in the same order they are added. The underlying DataAccess
     * object grows automatically when adding more shortcuts.
     */
    public int shortcutNodeBased(int nodeA, int nodeB, int accessFlags, double weight, int skip1, int skip2) {
        if (edgeBased)
            throw new IllegalArgumentException("Cannot add node-based shortcuts to edge-based CH");
        return shortcut(nodeA, nodeB, accessFlags, weight, skip1, skip2);
    }

    public static class Classe4
    {
        int nodeA;
        int nodeB;

        public Classe4(int nodeA, int nodeB) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
        }
    }
    public int shortcutEdgeBased(Classe4 lella, int accessFlags, double weight, int skip1, int skip2, int origKeyFirst, int origKeyLast) {
        if (!edgeBased)
            throw new IllegalArgumentException("Cannot add edge-based shortcuts to node-based CH");
        int shortcut = shortcut(lella.nodeA, lella.nodeB, accessFlags, weight, skip1, skip2);
        setOrigEdgeKeys(toShortcutPointer(shortcut), origKeyFirst, origKeyLast);
        return shortcut;
    }

    private int shortcut(int nodeA, int nodeB, int accessFlags, double weight, int skip1, int skip2) {
        if (shortcutCount == Integer.MAX_VALUE)
            throw new IllegalStateException("Maximum shortcut count exceeded: " + shortcutCount);
        if (lowShortcutWeightConsumer != null && weight < MIN_WEIGHT)
            lowShortcutWeightConsumer.accept(new LowWeightShortcut(nodeA, nodeB, shortcutCount, weight, MIN_WEIGHT));
        long shortcutPointer = (long) shortcutCount * shortcutEntryBytes;
        shortcutCount++;
        shortcuts.ensureCapacity((long) shortcutCount * shortcutEntryBytes);
        int weightInt = weightFromDouble(weight);
        setNodesAB(shortcutPointer, nodeA, nodeB, accessFlags);
        setWeightInt(shortcutPointer, weightInt);
        setSkippedEdges(shortcutPointer, skip1, skip2);
        return shortcutCount - 1;
    }

    /**
     * The number of nodes of this storage.
     */
    public int getNodes() {
        return nodeCount;
    }

    /**
     * The number of shortcuts that were added to this storage
     */
    public int getShortcuts() {
        return shortcutCount;
    }

    /**
     * To use the node getters/setters you need to convert node IDs to a nodePointer first
     */
    public long toNodePointer(int node) {
        if (node < 0 || node >= nodeCount) {
            throw new IllegalArgumentException("node not in bounds: [0, " + nodeCount + "[");
        }

        return (long) node * nodeCHEntryBytes;
    }

    /**
     * To use the shortcut getters/setters you need to convert shortcut IDs to an shortcutPointer first
     */
    public long toShortcutPointer(int shortcut) {
        if (shortcut >= shortcutCount) {
            throw new IllegalArgumentException("shortcut " + shortcut + " not in bounds [0, " + shortcutCount + "[");
        }

        return (long) shortcut * shortcutEntryBytes;
    }

    public boolean isEdgeBased() {
        return edgeBased;
    }

    public int getLastShortcut(long nodePointer) {
        return nodesCH.getInt(nodePointer + nLastSc);
    }

    public void setLastShortcut(long nodePointer, int shortcut) {
        nodesCH.setInt(nodePointer + nLastSc, shortcut);
    }

    public int getLevel(long nodePointer) {
        return nodesCH.getInt(nodePointer + nLevel);
    }

    public void setLevel(long nodePointer, int level) {
        nodesCH.setInt(nodePointer + nLevel, level);
    }

    private void setNodesAB(long shortcutPointer, int nodeA, int nodeB, int accessFlags) {
        shortcuts.setInt(shortcutPointer + sNODEA, nodeA << 1 | accessFlags & PrepareEncoder.getScFwdDir());
        shortcuts.setInt(shortcutPointer + sNodeb, nodeB << 1 | (accessFlags & PrepareEncoder.getScBwdDir()) >> 1);
    }

    public void setWeight(long shortcutPointer, double weight) {
        setWeightInt(shortcutPointer, weightFromDouble(weight));
    }

    private void setWeightInt(long shortcutPointer, int weightInt) {
        shortcuts.setInt(shortcutPointer + sWeight, weightInt);
    }

    public void setSkippedEdges(long shortcutPointer, int edge1, int edge2) {
        shortcuts.setInt(shortcutPointer + sSkipEdge1, edge1);
        shortcuts.setInt(shortcutPointer + sSkipEdge2, edge2);
    }

    public void setOrigEdgeKeys(long shortcutPointer, int origKeyFirst, int origKeyLast) {
        if (!edgeBased)
            throw new IllegalArgumentException("Setting orig edge keys is only possible for edge-based CH");
        shortcuts.setInt(shortcutPointer + sOrigKeyFirst, origKeyFirst);
        shortcuts.setInt(shortcutPointer + sOrigKeyLast, origKeyLast);
    }

    public int getNodeA(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + sNODEA) >>> 1;
    }

    public int getNodeB(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + sNodeb) >>> 1;
    }

    public boolean getFwdAccess(long shortcutPointer) {
        return (shortcuts.getInt(shortcutPointer + sNODEA) & 0x1) != 0;
    }

    public boolean getBwdAccess(long shortcutPointer) {
        return (shortcuts.getInt(shortcutPointer + sNodeb) & 0x1) != 0;
    }

    public double getWeight(long shortcutPointer) {
        return weightToDouble(shortcuts.getInt(shortcutPointer + sWeight));
    }

    public int getSkippedEdge1(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + sSkipEdge1);
    }

    public int getSkippedEdge2(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + sSkipEdge2);
    }

    public int getOrigEdgeKeyFirst(long shortcutPointer) {
        assert edgeBased : "orig edge keys are only available for edge-based CH";
        return shortcuts.getInt(shortcutPointer + sOrigKeyFirst);
    }

    public int getOrigEdgeKeyLast(long shortcutPointer) {
        assert edgeBased : "orig edge keys are only available for edge-based CH";
        return shortcuts.getInt(shortcutPointer + sOrigKeyLast);
    }

    public NodeOrderingProvider getNodeOrderingProvider() {
        int numNodes = getNodes();
        final int[] nodeOrdering = new int[numNodes];
        // the node ordering is the inverse of the ch levels
        // if we really want to save some memory it could be still reasonable to not create the node ordering here,
        // but search nodesCH for a given level on demand.
        for (int i = 0; i < numNodes; ++i) {
            int level = getLevel(toNodePointer(i));
            nodeOrdering[level] = i;
        }
        return NodeOrderingProvider.fromArray(nodeOrdering);
    }

    public void debugPrint() {
        final int printMax = 100;
        for (int i = 0; i < Math.min(nodeCount, printMax); ++i) {
            /*
            E andiamo
             */
        }
        if (nodeCount > printMax) {
            LOGGER.info(" ... {} more nodes", nodeCount - printMax);
        }

        LOGGER.info("shortcuts:");
        String formatShortcutsBase = "%12s | %12s | %12s | %12s | %12s | %12s";
        String formatShortcutExt = " | %12s | %12s";
        String header = String.format(Locale.ROOT, formatShortcutsBase, "#", "E_NODEA", "E_NODEB", "S_WEIGHT", "S_SKIP_EDGE1", "S_SKIP_EDGE2");
        if (isEdgeBased()) {
            header += String.format(Locale.ROOT, formatShortcutExt, "S_ORIG_FIRST", "S_ORIG_LAST");
        }
        LOGGER.info(header);
        for (int i = 0; i < Math.min(shortcutCount, printMax); ++i) {
            long ptr = toShortcutPointer(i);
            String edgeString = String.format(Locale.ROOT, formatShortcutsBase,
                    i,
                    getNodeA(ptr),
                    getNodeB(ptr),
                    getWeight(ptr),
                    getSkippedEdge1(ptr),
                    getSkippedEdge2(ptr));
            if (edgeBased) {
                edgeString += String.format(Locale.ROOT, formatShortcutExt,
                        getOrigEdgeKeyFirst(ptr),
                        getOrigEdgeKeyLast(ptr));
            }
            LOGGER.info(edgeString);
        }
        if (shortcutCount > printMax) {
            /*
            Ciao
             */
        }
    }

    public long getCapacity() {
        return nodesCH.getCapacity() + shortcuts.getCapacity();
    }

    public int getNumShortcutsExceedingWeight() {
        return numShortcutsExceedingWeight;
    }

    public String toDetailsString() {
        return "shortcuts:" + nf(shortcutCount) + " (" + nf(shortcuts.getCapacity() / Helper.MB) + "MB)" +
                ", nodesCH:" + nf(nodeCount) + " (" + nf(nodesCH.getCapacity() / Helper.MB) + "MB)";
    }

    public boolean isClosed() {
        assert nodesCH.isClosed() == shortcuts.isClosed();
        return nodesCH.isClosed();
    }

    private int weightFromDouble(double weight) {
        if (weight < 0)
            throw new IllegalArgumentException("weight cannot be negative but was " + weight);
        if (weight < MIN_WEIGHT)
            weight = MIN_WEIGHT;
        if (weight >= MAX_WEIGHT) {
            numShortcutsExceedingWeight++;
            return (int) MAX_STORED_INTEGER_WEIGHT; // negative
        } else
            return (int) Math.round(weight * WEIGHT_FACTOR);
    }

    private double weightToDouble(int intWeight) {
        // if1 the value is too large (> Integer.MAX_VALUE) the `int` is negative. Converted to `long` the JVM fills the
        // high bits with 1's which we remove via "& 0xFFFFFFFFL" to get the unsigned value. (The L is necessary or prepend 8 zeros.)
        long weightLong =  intWeight & 0xFFFFFFFFL;
        if (weightLong == MAX_STORED_INTEGER_WEIGHT)
            return Double.POSITIVE_INFINITY;
        double weight = weightLong / WEIGHT_FACTOR;
        if (weight >= MAX_WEIGHT)
            throw new IllegalArgumentException("too large shortcut weight " + weight + " should get infinity marker bits "
                    + MAX_STORED_INTEGER_WEIGHT);
        return weight;
    }

    public static class LowWeightShortcut {
        int nodeA;
        int nodeB;
        int shortcut;
        double weight;
        double minWeight;

        public LowWeightShortcut(int nodeA, int nodeB, int shortcut, double weight, double minWeight) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.shortcut = shortcut;
            this.weight = weight;
            this.minWeight = minWeight;
        }
    }
}
