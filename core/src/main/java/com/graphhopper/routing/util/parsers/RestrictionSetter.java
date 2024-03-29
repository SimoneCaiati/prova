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

package com.graphhopper.routing.util.parsers;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.reader.osm.GraphRestriction;
import com.graphhopper.reader.osm.Pair;
import com.graphhopper.reader.osm.RestrictionType;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

import java.util.List;

import static com.graphhopper.reader.osm.RestrictionType.NO;
import static com.graphhopper.reader.osm.RestrictionType.ONLY;

@SuppressWarnings("java:S135")
public class RestrictionSetter {
    private final BaseGraph baseGraph;
    private final EdgeExplorer edgeExplorer;
    private final IntIntMap artificialEdgesByEdges = new IntIntHashMap();

    public RestrictionSetter(BaseGraph baseGraph) {
        this.baseGraph = baseGraph;
        this.edgeExplorer = baseGraph.createEdgeExplorer();
    }

    /**
     * Adds all the turn restriction entries to the graph that are needed to enforce the given restrictions, for
     * a single turn cost encoded value.
     * Implementing via-way turn restrictions requires adding artificial edges to the graph, which is also handled here.
     * Since we keep track of the added artificial edges here it is important to only use one RestrictionSetter instance
     * for **all** turn restrictions and vehicle types.
     */
    public void setRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        // we first need to add all the artificial edges, because we might need to restrict turns between artificial
        // edges created for different restrictions (when restrictions are overlapping)
        addArtificialEdges(restrictions);
        // now we can add all the via-way restrictions
        addViaWayRestrictions(restrictions, turnCostEnc);
        // ... and finally all the via-node restrictions
        addViaNodeRestrictions(restrictions, turnCostEnc);
    }

    private void addArtificialEdges(List<Pair<GraphRestriction, RestrictionType>> restrictions) {
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (p.getFirst().isViaWayRestriction()) {
                if (ignoreViaWayRestriction(p)) continue;
                int viaEdge = p.getFirst().getViaEdges().get(0);
                int artificialEdge = artificialEdgesByEdges.getOrDefault(viaEdge, -1);
                if (artificialEdge < 0) {
                    EdgeIteratorState viaEdgeState = baseGraph.getEdgeIteratorState(p.getFirst().getViaEdges().get(0), Integer.MIN_VALUE);
                    EdgeIteratorState artificialEdgeState = baseGraph.edge(viaEdgeState.getBaseNode(), viaEdgeState.getAdjNode())
                            .setFlags(viaEdgeState.getFlags())
                            .setWayGeometry(viaEdgeState.fetchWayGeometry(FetchMode.PILLAR_ONLY))
                            .setDistance(viaEdgeState.getDistance())
                            .setKeyValues(viaEdgeState.getKeyValues());
                    artificialEdge = artificialEdgeState.getEdge();
                    artificialEdgesByEdges.put(viaEdge, artificialEdge);
                }
            }
        }
    }

    private void addViaWayRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        IntSet viaEdgesUsedByOnlyRestrictions = new IntHashSet();
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (!p.getFirst().isViaWayRestriction()) continue;
            if (ignoreViaWayRestriction(p)) continue;
            final int fromEdge = p.getFirst().getFromEdges().get(0);
            final int viaEdge = p.getFirst().getViaEdges().get(0);
            final int toEdge = p.getFirst().getToEdges().get(0);
            final int artificialVia = artificialEdgesByEdges.getOrDefault(viaEdge, viaEdge);
            solo(viaEdgesUsedByOnlyRestrictions, p, viaEdge, artificialVia);
            final int artificialFrom = artificialEdgesByEdges.getOrDefault(fromEdge, fromEdge);
            final int artificialTo = artificialEdgesByEdges.getOrDefault(toEdge, toEdge);
            final int fromToViaNode = p.getFirst().getViaNodes().get(0);
            final int viaToToNode = p.getFirst().getViaNodes().get(1);

            // never turn between an artificial edge and its corresponding real edge
            restrictTurn(turnCostEnc, artificialVia, fromToViaNode, viaEdge);
            restrictTurn(turnCostEnc, viaEdge, fromToViaNode, artificialVia);
            restrictTurn(turnCostEnc, artificialVia, viaToToNode, viaEdge);
            restrictTurn(turnCostEnc, viaEdge, viaToToNode, artificialVia);

            if (p.getSecond() == NO) {
                // This is how we implement via-way NO restrictions: we deny turning from the from-edge onto the via-edge,
                // but allow turning onto the artificial edge instead. Then we deny turning from the artificial edge onto
                // the to edge.
                restrictTurn(turnCostEnc, fromEdge, fromToViaNode, viaEdge);
                restrictTurn(turnCostEnc, artificialVia, viaToToNode, toEdge);
            } else if (p.getSecond() == ONLY) {
                // For via-way ONLY restrictions we have to turn from the from-edge onto the via-edge and from the via-edge
                // onto the to-edge, but only if we actually start at the from-edge. Therefore we enforce turning onto
                // the artificial via-edge when we are coming from the from-edge and only allow turning onto the to-edge
                // when coming from the artificial via-edge.
                EdgeIterator iter = edgeExplorer.setBaseNode(fromToViaNode);
                semola(turnCostEnc, fromEdge, toEdge, artificialVia, fromToViaNode, viaToToNode, iter);
            } else {
                throw new IllegalArgumentException("Unexpected restriction type: " + p.getSecond());
            }

            // this is important for overlapping restrictions
            if (artificialFrom != fromEdge)
                restrictTurn(turnCostEnc, artificialFrom, fromToViaNode, artificialVia);
            if (artificialTo != toEdge)
                restrictTurn(turnCostEnc, artificialVia, viaToToNode, artificialTo);
        }
    }

    private void semola(DecimalEncodedValue turnCostEnc, int fromEdge, int toEdge, int artificialVia, int fromToViaNode, int viaToToNode, EdgeIterator iter) {
        while (iter.next())
            if (iter.getEdge() != fromEdge && iter.getEdge() != artificialVia)
                restrictTurn(turnCostEnc, fromEdge, fromToViaNode, iter.getEdge());
        iter = edgeExplorer.setBaseNode(viaToToNode);
        while (iter.next())
            if (iter.getEdge() != artificialVia && iter.getEdge() != toEdge)
                restrictTurn(turnCostEnc, artificialVia, viaToToNode, iter.getEdge());
    }

    private static void solo(IntSet viaEdgesUsedByOnlyRestrictions, Pair<GraphRestriction, RestrictionType> p, int viaEdge, int artificialVia) {
        if (artificialVia == viaEdge)
            throw new IllegalArgumentException("There should be an artificial edge for every via edge of a way restriction");
        if (p.getSecond() == ONLY && !viaEdgesUsedByOnlyRestrictions.add(viaEdge))
            throw new IllegalStateException("We cannot deal with 'only' via-way restrictions that use the same via edges");
    }

    private void addViaNodeRestrictions(List<Pair<GraphRestriction, RestrictionType>> restrictions, DecimalEncodedValue turnCostEnc) {
        for (Pair<GraphRestriction, RestrictionType> p : restrictions) {
            if (p.getFirst().isViaWayRestriction()) continue;
            final int viaNode = p.getFirst().getViaNodes().get(0);
            for (IntCursor fromEdgeCursor : p.getFirst().getFromEdges()) {
                for (IntCursor toEdgeCursor : p.getFirst().getToEdges()) {
                    final int fromEdge = fromEdgeCursor.value;
                    final int toEdge = toEdgeCursor.value;
                    final int artificialFrom = artificialEdgesByEdges.getOrDefault(fromEdge, fromEdge);
                    final int artificialTo = artificialEdgesByEdges.getOrDefault(toEdge, toEdge);
                    if (p.getSecond() == NO) {
                        restrictTurn(turnCostEnc, fromEdge, viaNode, toEdge);
                        // we also need to restrict this term in case there are artificial edges for the from- and/or to-edge
                        lello(turnCostEnc, viaNode, fromEdge, toEdge, artificialFrom, artificialTo);
                    } else if (p.getSecond() == ONLY) {
                        // we need to restrict all turns except the one, but that also means not restricting the
                        // artificial counterparts of these turns, if they exist.
                        // we do not explicitly restrict the U-turn from the from-edge back to the from-edge though.
                        EdgeIterator iter = edgeExplorer.setBaseNode(viaNode);
                        lellouno(turnCostEnc, viaNode, fromEdge, toEdge, artificialFrom, artificialTo, iter);
                    } else {
                        throw new IllegalArgumentException("Unexpected restriction type: " + p.getSecond());
                    }
                }
            }
        }
    }

    private void lellouno(DecimalEncodedValue turnCostEnc, int viaNode, int fromEdge, int toEdge, int artificialFrom, int artificialTo, EdgeIterator iter) {
        while (iter.next()) {
            if (iter.getEdge() != fromEdge && iter.getEdge() != toEdge && iter.getEdge() != artificialTo)
                restrictTurn(turnCostEnc, fromEdge, viaNode, iter.getEdge());
            // and the same for the artificial edge belonging to the from-edge if it exists
            if (fromEdge != artificialFrom && iter.getEdge() != artificialFrom && iter.getEdge() != toEdge && iter.getEdge() != artificialTo)
                restrictTurn(turnCostEnc, artificialFrom, viaNode, iter.getEdge());
        }
    }

    private void lello(DecimalEncodedValue turnCostEnc, int viaNode, int fromEdge, int toEdge, int artificialFrom, int artificialTo) {
        if (artificialFrom != fromEdge)
            restrictTurn(turnCostEnc, artificialFrom, viaNode, toEdge);
        if (artificialTo != toEdge)
            restrictTurn(turnCostEnc, fromEdge, viaNode, artificialTo);
        if (artificialFrom != fromEdge && artificialTo != toEdge)
            restrictTurn(turnCostEnc, artificialFrom, viaNode, artificialTo);
    }

    public IntIntMap getArtificialEdgesByEdges() {
        return artificialEdgesByEdges;
    }

    private void restrictTurn(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
        if (fromEdge < 0 || toEdge < 0 || viaNode < 0)
            throw new IllegalArgumentException("from/toEdge and viaNode must be >= 0");
        baseGraph.getTurnCostStorage().set(turnCostEnc, fromEdge, viaNode, toEdge, Double.POSITIVE_INFINITY);
    }

    private static boolean ignoreViaWayRestriction(Pair<GraphRestriction, RestrictionType> p) {
        //  how frequent are these?
        if (p.getFirst().getViaEdges().size() > 1)
            // no multi-restrictions yet
            return true;
        // no multi-from or -to yet
        return p.getFirst().getFromEdges().size() > 1 || p.getFirst().getToEdges().size() > 1;
    }

}