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

package com.graphhopper.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.ResponsePath;
import com.graphhopper.eccezionecore.PointPathException;
import com.graphhopper.routing.ch.CHPreparationGraph;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIterator;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static com.graphhopper.gtfs.GtfsStorage.EdgeType.BOARD;

public class PtGraph implements GtfsReader.PtGraphOut {

    // nodes
    private final DataAccess nodes;
    private final int nodeEntryBytes;
    private final Directory dir;
    private int nodeCount;

    // edges
    private final DataAccess edges;
    private final int eNODEA;
    private final int eNODEB;
    private final int eLINKA;
    private final int eLINKB;
    private final int eATTRS;
    private final int edgeEntryBytes;
    private int edgeCount;

    private final DataAccess attrs;
    private static final GtfsStorage.EdgeType[] edgeTypeValues = GtfsStorage.EdgeType.values();

    public PtGraph(Directory dir, int firstNode) {
        this.dir = dir;
        nextNode = firstNode;
        nodes = dir.create("pt_nodes", dir.getDefaultType("pt_nodes", true), -1);
        edges = dir.create("pt_edges", dir.getDefaultType("pt_edges", true), -1);
        attrs = dir.create("pt_edge_attrs", dir.getDefaultType("pt_edge_attrs", true), -1);

        nodeEntryBytes = 8;

        // memory layout for edges
        eNODEA = 0;
        eNODEB = 4;
        eLINKA = 8;
        eLINKB = 12;
        eATTRS = 16;
        edgeEntryBytes = eATTRS + 8;
    }

    public void create(long initSize) throws MMapDataAccess.MappaExce {
        nodes.create(initSize);
        edges.create(initSize);
        attrs.create(initSize);
    }

    public boolean loadExisting() throws RAMDataAccess.RamExce, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        if (!nodes.loadExisting() || !edges.loadExisting() || !attrs.loadExisting())
            return false;

        nodeCount = nodes.getHeader(2 * 4);
        edgeCount = edges.getHeader(2 * 4);
        try {
            deserializeExtraStuff();
        } catch (IOException | ClassNotFoundException e) {
            try {
                throw new PointPathException();
            } catch (PointPathException ex) {
                //nothing
            }
        }
        return true;
    }

    public void flush() {
        nodes.setHeader(2 * 4, nodeCount);
        edges.setHeader(2 * 4, edgeCount);


        try {
            edges.flush();
            nodes.flush();
            attrs.flush();
            serializeExtraStuff();
        } catch (IOException | RAMIntDataAccess.RamIntExce e) {
            try {
                throw new BabidiExce(e);
            } catch (BabidiExce ex) {
                //
            }
        } catch (MMapDataAccess.MapExce | RAMDataAccess.RamExce2 e) {
            //nothing
        }
    }

    public void close() {
        edges.close();
        nodes.close();
        try {
            attrs.flush();
        } catch (MMapDataAccess.MapExce | RAMDataAccess.RamExce2 | RAMIntDataAccess.RamIntExce e) {
            //nothing
        }
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public boolean isClosed() {
        assert nodes.isClosed() == edges.isClosed();
        return nodes.isClosed();
    }

    public int addEdge(int nodeA, int nodeB, long attrPointer) {
        if (edgeCount == Integer.MAX_VALUE)
            throw new IllegalStateException("Maximum edge count exceeded: " + edgeCount);
        ensureNodeCapacity(Math.max(nodeA, nodeB));
        final int edge = edgeCount;
        final long edgePointer = (long) edgeCount * edgeEntryBytes;
        edgeCount++;
        edges.ensureCapacity((long) edgeCount * edgeEntryBytes);

        setNodeA(edgePointer, nodeA);
        setNodeB(edgePointer, nodeB);
        setAttrPointer(edgePointer, attrPointer);
        // we keep a linked list of edges at each node. here we prepend the new edge at the already existing linked
        // list of edges.
        long nodePointerA = toNodePointer(nodeA);
        int edgeRefA = getEdgeRefOut(nodePointerA);
        setLinkA(edgePointer, edgeRefA >= 0 ? edgeRefA : -1);
        setEdgeRefOut(nodePointerA, edge);

        if (nodeA != nodeB) {
            long nodePointerB = toNodePointer(nodeB);
            int edgeRefB = getEdgeRefIn(nodePointerB);
            setLinkB(edgePointer, EdgeIterator.Edge.isValid(edgeRefB) ? edgeRefB : -1);
            setEdgeRefIn(nodePointerB, edge);
        }
        return edge;
    }

    public void ensureNodeCapacity(int node) {
        if (node < nodeCount)
            return;

        int oldNodes = nodeCount;
        nodeCount = node + 1;
        nodes.ensureCapacity((long) nodeCount * nodeEntryBytes);
        for (int n = oldNodes; n < nodeCount; ++n) {
            setEdgeRefOut(toNodePointer(n), -1);
            setEdgeRefIn(toNodePointer(n), -1);
        }
    }

    public long toNodePointer(int node) {
        if (node < 0 || node >= nodeCount)
            throw new IllegalArgumentException("node: " + node + " out of bounds [0," + nodeCount + "[");
        return (long) node * nodeEntryBytes;
    }

    public long toEdgePointer(int edge) {
        if (edge < 0 || edge >= edgeCount)
            throw new IllegalArgumentException("edge: " + edge + " out of bounds [0," + edgeCount + "[");
        return (long) edge * edgeEntryBytes;
    }

    public void setNodeA(long edgePointer, int nodeA) {
        edges.setInt(edgePointer + eNODEA, nodeA);
    }

    private void setAttrPointer(long edgePointer, long attrPointer) {
        edges.setInt(edgePointer + eATTRS, getIntLow(attrPointer));
        edges.setInt(edgePointer + eATTRS + 4, getIntHigh(attrPointer));
    }

    private long getAttrPointer(long edgePointer) {
        return combineIntsToLong(
                edges.getInt(edgePointer + eATTRS),
                edges.getInt(edgePointer + eATTRS + 4));
    }

    final int getIntLow(long longValue) {
        return (int) (longValue & 0xFFFFFFFFL);
    }

    final int getIntHigh(long longValue) {
        return (int) (longValue >> 32);
    }

    final long combineIntsToLong(int intLow, int intHigh) {
        return ((long) intHigh << 32) | (intLow & 0xFFFFFFFFL);
    }


    public void setNodeB(long edgePointer, int nodeB) {
        edges.setInt(edgePointer + eNODEB, nodeB);
    }

    public void setLinkA(long edgePointer, int linkA) {
        edges.setInt(edgePointer + eLINKA, linkA);
    }

    public void setLinkB(long edgePointer, int linkB) {
        edges.setInt(edgePointer + eLINKB, linkB);
    }

    public int getNodeA(long edgePointer) {
        return edges.getInt(edgePointer + eNODEA);
    }

    public int getNodeB(long edgePointer) {
        return edges.getInt(edgePointer + eNODEB);
    }

    public int getLinkA(long edgePointer) {
        return edges.getInt(edgePointer + eLINKA);
    }

    public int getLinkB(long edgePointer) {
        return edges.getInt(edgePointer + eLINKB);
    }

    public void setEdgeRefOut(long nodePointer, int edgeRef) {
        nodes.setInt(nodePointer, edgeRef);
    }

    public void setEdgeRefIn(long nodePointer, int edgeRef) {
        nodes.setInt(nodePointer + 4, edgeRef);
    }

    public int getEdgeRefOut(long nodePointer) {
        return nodes.getInt(nodePointer);
    }

    public int getEdgeRefIn(long nodePointer) {
        return nodes.getInt(nodePointer + 4);
    }

    int nextNode = 0;

    long currentPointer = 0;

    Map<GtfsStorage.Validity, Integer> validities = new HashMap<>();
    List<GtfsStorage.Validity> validityList = new ArrayList<>();

    Map<GtfsStorage.PlatformDescriptor, Integer> platformDescriptors = new HashMap<>();
    List<GtfsStorage.PlatformDescriptor> platformDescriptorList = new ArrayList<>();

    Map<GtfsRealtime.TripDescriptor, Integer> tripDescriptors = new HashMap<>();
    List<GtfsRealtime.TripDescriptor> tripDescriptorList = new ArrayList<>();

    Map<GtfsStorage.FeedIdWithTimezone, Integer> feedIdWithTimezones = new HashMap<>();
    List<GtfsStorage.FeedIdWithTimezone> feedIdWithTimezoneList = new ArrayList<>();

    private void serializeExtraStuff() throws IOException {
        try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(dir.getLocation() + "/pt_extra"))) {
            os.writeObject(validityList);
            os.writeObject(platformDescriptorList);
            os.writeObject(tripDescriptorList);
            os.writeObject(feedIdWithTimezoneList);
        }
    }

    private void deserializeExtraStuff() throws IOException, ClassNotFoundException {
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(dir.getLocation() + "/pt_extra"))) {
            validityList = ((List<GtfsStorage.Validity>) is.readObject());
            platformDescriptorList = ((List<GtfsStorage.PlatformDescriptor>) is.readObject());
            tripDescriptorList = ((List<GtfsRealtime.TripDescriptor>) is.readObject());
            feedIdWithTimezoneList = ((List<GtfsStorage.FeedIdWithTimezone>) is.readObject());
        }
    }

    @Override
    public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
        this.attrs.ensureCapacity(currentPointer + 10000);

        int edge = addEdge(src, dest, currentPointer);
        this.attrs.setInt(currentPointer, attrs.classe2.type.ordinal());
        currentPointer += 4;
        this.attrs.setInt(currentPointer, attrs.classe2.time);
        currentPointer += 4;

        switch (attrs.classe2.type) {
            case ENTER_PT:
                this.attrs.setInt(currentPointer, attrs.routeType);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, sharePlatformDescriptor(attrs.platformDescriptor));
                currentPointer += 4;

                break;
            case EXIT_PT:
                this.attrs.setInt(currentPointer, sharePlatformDescriptor(attrs.platformDescriptor));
                currentPointer += 4;

                break;
            case ENTER_TIME_EXPANDED_NETWORK:
                this.attrs.setInt(currentPointer, shareFeedIdWithTimezone(attrs.feedIdWithTimezone));
                currentPointer += 4;
                Logger logger = Logger.getLogger(CHPreparationGraph.class.getName());
                logger.info("main theme has been overload");
                break;
            case LEAVE_TIME_EXPANDED_NETWORK:
                this.attrs.setInt(currentPointer, shareFeedIdWithTimezone(attrs.feedIdWithTimezone));
                currentPointer += 4;

                break;
            case BOARD:
                this.attrs.setInt(currentPointer, attrs.stopSequence);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareTripDescriptor(attrs.tripDescriptor));
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareValidity(attrs.classe2.validity));
                currentPointer += 4;

                this.attrs.setInt(currentPointer, attrs.transfers);
                currentPointer += 4;

                break;
            case ALIGHT:
                this.attrs.setInt(currentPointer, attrs.stopSequence);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareTripDescriptor(attrs.tripDescriptor));
                currentPointer += 4;

                this.attrs.setInt(currentPointer, shareValidity(attrs.classe2.validity));
                currentPointer += 4;

                break;
            case WAIT:
                break;
            case WAIT_ARRIVAL:
                break;
            case OVERNIGHT:
                break;
            case HOP:
                this.attrs.setInt(currentPointer, attrs.stopSequence);
                currentPointer += 4;

                break;
            case DWELL:
                break;
            case TRANSFER:
                this.attrs.setInt(currentPointer, attrs.routeType);
                currentPointer += 4;

                this.attrs.setInt(currentPointer, sharePlatformDescriptor(attrs.platformDescriptor));
                currentPointer += 4;
                logger = Logger.getLogger(CHPreparationGraph.class.getName());
                logger.info("main theme has been overload");
                break;
            default:
                try {
                    throw new VegetaExce();
                } catch (VegetaExce e) {
                    //
                }
        }
        return edge;
    }

    private int shareValidity(GtfsStorage.Validity validity) {
        int validityId = validities.getOrDefault(validity, -1);
        if (validityId == -1) {
            validityId = validityList.size();
            validities.put(validity, validityId);
            validityList.add(validity);
        }
        return validityId;
    }


    private int shareTripDescriptor(GtfsRealtime.TripDescriptor tripDescriptor) {
        return tripDescriptors.computeIfAbsent(tripDescriptor, key -> {
            int tripDescriptorId = tripDescriptorList.size();
            tripDescriptorList.add(key);
            return tripDescriptorId;
        });
    }


    private Integer shareFeedIdWithTimezone(GtfsStorage.FeedIdWithTimezone feedIdWithTimezone1) {
        return feedIdWithTimezones.computeIfAbsent(feedIdWithTimezone1, k -> {
            Integer newFeedIdWithTimezone = feedIdWithTimezoneList.size();
            feedIdWithTimezoneList.add(feedIdWithTimezone1);
            return newFeedIdWithTimezone;
        });
    }


    private Integer sharePlatformDescriptor(GtfsStorage.PlatformDescriptor platformDescriptor) {
        return platformDescriptors.computeIfAbsent(platformDescriptor, key -> {
            platformDescriptorList.add(key);
            return platformDescriptorList.size() - 1;
        });
    }


    public int createNode() {
        return nextNode++;
    }

    public Iterable<PtEdge> edgesAround(int baseNode) {
        Spliterators.AbstractSpliterator<PtEdge> spliterator = new Spliterators.AbstractSpliterator<PtEdge>(0, 0) {
            int edgeId = getEdgeRefOut(toNodePointer(baseNode));

            @Override
            public boolean tryAdvance(Consumer<? super PtEdge> action) {
                if (edgeId < 0)
                    return false;

                long edgePointer = toEdgePointer(edgeId);

                int nodeA = getNodeA(edgePointer);
                int nodeB = getNodeB(edgePointer);
                PtEdgeAttributes attrsA = pullAttrs(edgeId);
                action.accept(new PtEdge(edgeId, nodeA, nodeB, attrsA));
                edgeId = getLinkA(edgePointer);
                return true;
            }

        };
        return () -> Spliterators.iterator(spliterator);
    }


    private PtEdgeAttributes pullAttrs(int edgeId) {
        long attrPointer = getAttrPointer(toEdgePointer(edgeId));
        GtfsStorage.EdgeType type = edgeTypeValues[attrs.getInt(attrPointer)];
        attrPointer += 4;
        int time = attrs.getInt(attrPointer);
        attrPointer += 4;
        switch (type) {
            case BOARD: {
                int stopSequence = attrs.getInt(attrPointer);
                attrPointer += 4;
                int tripDescriptor = attrs.getInt(attrPointer);
                attrPointer += 4;
                int validity = attrs.getInt(attrPointer);
                attrPointer += 4;
                int transfers = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(BOARD, time, validityList.get(validity)), -1, null,
                        transfers, stopSequence, tripDescriptorList.get(tripDescriptor), null);
            }
            case ALIGHT: {
                int stopSequence = attrs.getInt(attrPointer);
                attrPointer += 4;
                int tripDescriptor = attrs.getInt(attrPointer);
                attrPointer += 4;
                int validity = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.ALIGHT, time, validityList.get(validity)), -1, null,
                        0, stopSequence, tripDescriptorList.get(tripDescriptor), null);
            }
            case ENTER_PT: {
                int routeType = attrs.getInt(attrPointer);
                attrPointer += 4;
                int platformDescriptor = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.ENTER_PT, time, null), routeType, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            case EXIT_PT: {
                int platformDescriptor = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.EXIT_PT, time, null), -1, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            case HOP: {
                int stopSequence = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.HOP, time, null), -1, null,
                        0, stopSequence, null, null);
            }
            case DWELL: {
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.DWELL, time, null), -1, null,
                        0, -1, null, null);
            }
            case ENTER_TIME_EXPANDED_NETWORK: {
                int feedId = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK, time, null), -1, feedIdWithTimezoneList.get(feedId),
                        0, -1, null, null);
            }
            case LEAVE_TIME_EXPANDED_NETWORK: {
                int feedId = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK, time, null), -1, feedIdWithTimezoneList.get(feedId),
                        0, -1, null, null);
            }
            case WAIT: {
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.WAIT, time, null), -1, null,
                        0, -1, null, null);
            }
            case WAIT_ARRIVAL: {
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.WAIT_ARRIVAL, time, null), -1, null,
                        0, -1, null, null);
            }
            case OVERNIGHT: {
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.OVERNIGHT, time, null), -1, null,
                        0, -1, null, null);
            }
            case TRANSFER: {
                int routeType = attrs.getInt(attrPointer);
                attrPointer += 4;
                int platformDescriptor = attrs.getInt(attrPointer);
                return new PtEdgeAttributes(new PtEdgeAttributes.Classe2(GtfsStorage.EdgeType.TRANSFER, time, null), routeType, null,
                        0, -1, null, platformDescriptorList.get(platformDescriptor));
            }
            default:
                try {
                    throw new VegetaExce();
                } catch (VegetaExce e) {
                    return null;
                }
        }
    }

    public PtEdge edge(int edgeId) {
        long edgePointer = toEdgePointer(edgeId);
        int nodeA = getNodeA(edgePointer);
        int nodeB = getNodeB(edgePointer);
        return new PtEdge(edgeId, nodeA, nodeB, pullAttrs(edgeId));
    }

    public Iterable<PtEdge> backEdgesAround(int adjNode) {
        Spliterators.AbstractSpliterator<PtEdge> spliterator = new Spliterators.AbstractSpliterator<PtEdge>(0, 0) {
            int edgeId = getEdgeRefIn(toNodePointer(adjNode));

            @Override
            public boolean tryAdvance(Consumer<? super PtEdge> action) {
                if (edgeId < 0)
                    return false;

                long edgePointer = toEdgePointer(edgeId);

                int nodeA = getNodeA(edgePointer);
                int nodeB = getNodeB(edgePointer);
                action.accept(new PtEdge(edgeId, nodeB, nodeA, pullAttrs(edgeId)));
                edgeId = getLinkB(edgePointer);

                return true;
            }
        };
        return () -> Spliterators.iterator(spliterator);
    }

    public static class PtEdge {
        private final int edgeId;
        private final int baseNode;

        @Override
        public String toString() {
            return "PtEdge{" +
                    "edgeId=" + edgeId +
                    ", baseNode=" + baseNode +
                    ", adjNode=" + adjNode +
                    ", attrs=" + attrs +
                    '}';
        }

        private final int adjNode;
        private final PtEdgeAttributes attrs;

        public PtEdge(int edgeId, int baseNode, int adjNode, PtEdgeAttributes attrs) {
            this.edgeId = edgeId;
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.attrs = attrs;
        }

        public GtfsStorage.EdgeType getType() {
            return attrs.classe2.type;
        }

        public int getTime() {
            return attrs.classe2.time;
        }

        public int getAdjNode() {
            return adjNode;
        }

        public PtEdgeAttributes getAttrs() {
            return attrs;
        }

        public int getId() {
            return edgeId;
        }



        public int getRouteType() {
            GtfsStorage.EdgeType edgeType = getType();
            if ((edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT || edgeType == GtfsStorage.EdgeType.TRANSFER)) {
                return getAttrs().routeType;
            }
            try {
                throw new ResponsePath.CheckException("Edge type "+edgeType+" doesn't encode route type.");
            } catch (ResponsePath.CheckException e) {
                //nothing
            }
            return 0;
        }

        public int getBaseNode() {
            return baseNode;
        }
    }

    private class VegetaExce extends Exception {
    }

    private class BabidiExce extends Exception {
        public BabidiExce(Exception e) {
        }
    }
}
