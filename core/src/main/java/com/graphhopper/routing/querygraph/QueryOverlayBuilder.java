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

package com.graphhopper.routing.querygraph;

import com.carrotsearch.hppc.predicates.IntObjectPredicate;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.*;

class QueryOverlayBuilder {
    private final int firstVirtualNodeId;
    private final int firstVirtualEdgeId;
    private final boolean is3D;
    private QueryOverlay queryOverlay;

    public static QueryOverlay build(Graph graph, List<Snap> snaps) {
        return build(graph.getNodes(), graph.getEdges(), graph.getNodeAccess().is3D(), snaps);
    }

    public static QueryOverlay build(int firstVirtualNodeId, int firstVirtualEdgeId, boolean is3D, List<Snap> snaps) {
        return new QueryOverlayBuilder(firstVirtualNodeId, firstVirtualEdgeId, is3D).build(snaps);
    }

    private QueryOverlayBuilder(int firstVirtualNodeId, int firstVirtualEdgeId, boolean is3D) {
        this.firstVirtualNodeId = firstVirtualNodeId;
        this.firstVirtualEdgeId = firstVirtualEdgeId;
        this.is3D = is3D;
    }

    private QueryOverlay build(List<Snap> resList) {
        queryOverlay = new QueryOverlay(resList.size(), is3D);
        buildVirtualEdges(resList);
        buildEdgeChangesAtRealNodes();
        return queryOverlay;
    }

    /**
     * For all specified snaps calculate the snapped point and if necessary set the closest node
     * to a virtual one and reverse the closest edge. Additionally the wayIndex can change if an edge is
     * swapped.
     */
    private void buildVirtualEdges(List<Snap> snaps) {
        GHIntObjectHashMap<List<Snap>> edge2res = new GHIntObjectHashMap<>(snaps.size());

        // Phase 1
        // calculate snapped point and swap direction of closest edge if necessary
        serioquattro(snaps, edge2res);

        // Phase 2 - now it is clear which points cut one edge
        // 1. create point lists
        // 2. create virtual edges between virtual nodes and its neighbor (virtual or normal nodes)
        edge2res.forEach((IntObjectPredicate<List<Snap>>) (edgeId, results) -> {
            // we can expect at least one entry in the results
            EdgeIteratorState closestEdge = results.get(0).getClosestEdge();
            final PointList fullPL = closestEdge.fetchWayGeometry(FetchMode.ALL);
            int baseNode = closestEdge.getBaseNode();
            Collections.sort(results, new Comparator<Snap>() {
                @Override
                public int compare(Snap o1, Snap o2) {
                    int diff = Integer.compare(o1.getWayIndex(), o2.getWayIndex());
                    if (diff == 0) {
                        return Double.compare(distanceOfSnappedPointToPillarNode(o1), distanceOfSnappedPointToPillarNode(o2));
                    } else {
                        return diff;
                    }
                }

                private double distanceOfSnappedPointToPillarNode(Snap o) {
                    GHPoint snappedPoint = o.getSnappedPoint();
                    double fromLat = fullPL.getLat(o.getWayIndex());
                    double fromLon = fullPL.getLon(o.getWayIndex());
                    return DistancePlaneProjection.getInstancePlane().calcNormalizedDist(fromLat, fromLon, snappedPoint.getLat(), snappedPoint.getLon());
                }
            });

            GHPoint3D prevPoint = fullPL.get(0);
            int adjNode = closestEdge.getAdjNode();
            int origEdgeKey = closestEdge.getEdgeKey();
            int origRevEdgeKey = closestEdge.getReverseEdgeKey();
            int prevWayIndex = 1;
            int prevNodeId = baseNode;
            int virtNodeId = queryOverlay.getVirtualNodes().size() + firstVirtualNodeId;
            boolean addedEdges = false;

            // Create base and adjacent PointLists for all non-equal virtual nodes.
            // We do so via inserting them at the correct position of fullPL and cutting the
            // fullPL into the right pieces.
            for (Snap res : results) {
                if (res.getClosestEdge().getBaseNode() != baseNode)
                    throw new IllegalStateException("Base nodes have to be identical but were not: " + closestEdge + " vs " + res.getClosestEdge());

                GHPoint3D currSnapped = res.getSnappedPoint();

                // no new virtual nodes if exactly the same snapped point
                if (prevPoint.equals(currSnapped)) {
                    res.setClosestNode(prevNodeId);
                    continue;
                }

                queryOverlay.getClosestEdges().add(res.getClosestEdge().getEdge());
                boolean isPillar = res.getSnappedPosition() == Snap.Position.PILLAR;
                createEdges(new InnerEdge(origEdgeKey, origRevEdgeKey,
                                prevPoint, prevWayIndex, isPillar),
                        res.getSnappedPoint(), res.getWayIndex(),
                        fullPL, closestEdge, prevNodeId, virtNodeId);

                queryOverlay.getVirtualNodes().add(currSnapped.getLat(), currSnapped.getLon(), currSnapped.getEle());

                // add edges again to set adjacent edges for newVirtNodeId
                if (addedEdges) {
                    queryOverlay.addVirtualEdge(queryOverlay.getVirtualEdge(queryOverlay.getNumVirtualEdges() - 2));
                    queryOverlay.addVirtualEdge(queryOverlay.getVirtualEdge(queryOverlay.getNumVirtualEdges() - 2));
                }

                addedEdges = true;
                res.setClosestNode(virtNodeId);
                prevNodeId = virtNodeId;
                prevWayIndex = res.getWayIndex() + 1;
                prevPoint = currSnapped;
                virtNodeId++;
            }

            // two edges between last result and adjacent node are still missing if not all points skipped
            int[] ser=new int[3];
            ser[0]=adjNode;
            ser[1]=origEdgeKey;
            ser[2]=origRevEdgeKey;
            molla(closestEdge, fullPL, prevPoint, ser, prevWayIndex, virtNodeId, addedEdges);

            return true;
        });
    }

    private void molla(EdgeIteratorState closestEdge, PointList fullPL, GHPoint3D prevPoint, int[] ser, int prevWayIndex, int virtNodeId, boolean addedEdges) {
        if (addedEdges)
            createEdges(new InnerEdge(ser[1], ser[2],
                            prevPoint, prevWayIndex, false),
                    fullPL.get(fullPL.size() - 1), fullPL.size() - 2,
                    fullPL, closestEdge, virtNodeId - 1, ser[0]);
    }

    private static void serioquattro(List<Snap> snaps, GHIntObjectHashMap<List<Snap>> edge2res) {
        for (Snap snap : snaps) {
            // Do not create virtual node for a snap if it is directly on a tower node or not found
            EdgeIteratorState closestEdge = serio(snap);
            if (closestEdge == null) continue;

            int base = closestEdge.getBaseNode();

            // Force the identical direction for all closest edges.
            // It is important to sort multiple results for the same edge by its wayIndex
            boolean doReverse = base > closestEdge.getAdjNode();
            doReverse = seriouno(closestEdge, base, doReverse);

            closestEdge = seriodue(snap, closestEdge, doReverse);

            // find multiple results on same edge
            int edgeId = closestEdge.getEdge();
            List<Snap> list = edge2res.get(edgeId);
            if (list == null) {
                list = new ArrayList<>(5);
                edge2res.put(edgeId, list);
            }
            list.add(snap);
        }
    }

    private static EdgeIteratorState seriodue(Snap snap, EdgeIteratorState closestEdge, boolean doReverse) {
        if (doReverse) {
            closestEdge = closestEdge.detach(true);
            PointList fullPL = closestEdge.fetchWayGeometry(FetchMode.ALL);
            snap.setClosestEdge(closestEdge);
            if (snap.getSnappedPosition() == Snap.Position.PILLAR)
                // ON pillar node
                snap.setWayIndex(fullPL.size() - snap.getWayIndex() - 1);
            else
                // for case "OFF pillar node"
                snap.setWayIndex(fullPL.size() - snap.getWayIndex() - 2);

            if (snap.getWayIndex() < 0)
                throw new IllegalStateException("Problem with wayIndex while reversing closest edge:" + closestEdge + ", " + snap);
        }
        return closestEdge;
    }

    private static boolean seriouno(EdgeIteratorState closestEdge, int base, boolean doReverse) {
        if (base == closestEdge.getAdjNode()) {
            // check for special case #162 where adj == base and force direction via latitude comparison
            PointList pl = closestEdge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
            if (pl.size() > 1)
                doReverse = pl.getLat(0) > pl.getLat(pl.size() - 1);
        }
        return doReverse;
    }

    private static EdgeIteratorState serio(Snap snap) {
        if (snap.getSnappedPosition() == Snap.Position.TOWER)
            return null;

        EdgeIteratorState closestEdge = snap.getClosestEdge();
        if (closestEdge == null)
            throw new IllegalStateException("Do not call QueryGraph.create with invalid Snap " + snap);
        return closestEdge;
    }
    public static class InnerEdge {
         int origEdgeKey;
        protected int origRevEdgeKey;
        protected GHPoint3D prevSnapped;
        protected int prevWayIndex;
         boolean isPillar;

        public InnerEdge(int origEdgeKey, int origRevEdgeKey, GHPoint3D prevSnapped, int prevWayIndex, boolean isPillar) {
            this.origEdgeKey = origEdgeKey;
            this.origRevEdgeKey = origRevEdgeKey;
            this.prevSnapped = prevSnapped;
            this.prevWayIndex = prevWayIndex;
            this.isPillar = isPillar;
        }
    }
    private void createEdges(InnerEdge inneredge, GHPoint3D currSnapped, int wayIndex,
                             PointList fullPL, EdgeIteratorState closestEdge,
                             int prevNodeId, int nodeId) {
        int max = wayIndex + 1;
        PointList basePoints = new PointList(max - inneredge.prevWayIndex + 1, is3D);
        basePoints.add(inneredge.prevSnapped.getLat(), inneredge.prevSnapped.getLon(), inneredge.prevSnapped.getEle());
        for (int i = inneredge.prevWayIndex; i < max; i++) {
            basePoints.add(fullPL, i);
        }
        if (!inneredge.isPillar) {
            basePoints.add(currSnapped.getLat(), currSnapped.getLon(), currSnapped.getEle());
        }
        // basePoints must have at least the size of 2 to make sure fetchWayGeometry(FetchMode.ALL) returns at least 2
        assert basePoints.size() >= 2 : "basePoints must have at least two points";

        PointList baseReversePoints = basePoints.clone(true);
        double baseDistance = DistancePlaneProjection.getInstancePlane().calcDistance(basePoints);
        int virtEdgeId = firstVirtualEdgeId + queryOverlay.getNumVirtualEdges() / 2;

        boolean reverse = closestEdge.get(EdgeIteratorState.REVERSE_STATE);
        // edges between base and snapped point
        List<EdgeKVStorage.KeyValue> keyValues = closestEdge.getKeyValues();
        VirtualEdgeIteratorState baseEdge = new VirtualEdgeIteratorState(new VirtualEdgeIteratorState.InnerVirtual(inneredge.origEdgeKey, GHUtility.createEdgeKey(virtEdgeId, prevNodeId == nodeId, false),
                prevNodeId), nodeId, baseDistance, closestEdge.getFlags(), keyValues, basePoints, reverse);
        VirtualEdgeIteratorState baseReverseEdge = new VirtualEdgeIteratorState(new VirtualEdgeIteratorState.InnerVirtual(inneredge.origRevEdgeKey, GHUtility.createEdgeKey(virtEdgeId, prevNodeId == nodeId, true),
                nodeId), prevNodeId, baseDistance, IntsRef.deepCopyOf(closestEdge.getFlags()), keyValues, baseReversePoints, !reverse);

        baseEdge.setReverseEdge(baseReverseEdge);
        baseReverseEdge.setReverseEdge(baseEdge);
        queryOverlay.addVirtualEdge(baseEdge);
        queryOverlay.addVirtualEdge(baseReverseEdge);
    }

    private void buildEdgeChangesAtRealNodes() {
        EdgeChangeBuilder.build(queryOverlay.getClosestEdges(), queryOverlay.getVirtualEdges(), firstVirtualNodeId, queryOverlay.getEdgeChangesAtRealNodes());
    }
}