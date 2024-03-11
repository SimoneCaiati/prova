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
package com.graphhopper.routing;

import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

import java.util.Objects;
import java.util.PriorityQueue;
@SuppressWarnings("java:S135")
/**
 * This class implements the A* algorithm according to
 * http://en.wikipedia.org/wiki/A*_search_algorithm
 * <p>
 * Different distance calculations can be used via setApproximation.
 * <p>
 *
 * @author Peter Karich
 */
public class AStar extends AbstractRoutingAlgorithm {
    private GHIntObjectHashMap<AStarEntry> fromMap;
    private PriorityQueue<AStarEntry> fromHeap;
    private AStarEntry currEdge;
    private int visitedNodes;
    private int to = -1;
    private WeightApproximator weightApprox;

    public AStar(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(DistancePlaneProjection.getInstancePlane());
        setApproximation(defaultApprox);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AStar aStar = (AStar) o;
        return visitedNodes == aStar.visitedNodes && to == aStar.to && Objects.equals(fromMap, aStar.fromMap) && Objects.equals(fromHeap, aStar.fromHeap) && Objects.equals(currEdge, aStar.currEdge) && Objects.equals(weightApprox, aStar.weightApprox);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromMap, fromHeap, currEdge, visitedNodes, to, weightApprox);
    }

    /**
     * @param approx defines how distance to goal Node is approximated
     */
    public AStar setApproximation(WeightApproximator approx) {
        weightApprox = approx;
        return this;
    }

    protected void initCollections(int size) {
        fromMap = new GHIntObjectHashMap<>();
        fromHeap = new PriorityQueue<>(size);
    }

    @Override
    public Path calcPath(int from, int to) {
        checkAlreadyRun();
        this.to = to;
        weightApprox.setTo(to);
        double weightToGoal = weightApprox.approximate(from);
        AStarEntry startEntry = new AStarEntry(EdgeIterator.NO_EDGE, from, 0 + weightToGoal, 0);
        fromHeap.add(startEntry);
        if (!traversalMode.isEdgeBased())
            fromMap.put(from, currEdge);
        runAlgo();
        return extractPath();
    }

    private void runAlgo() {
        while (!fromHeap.isEmpty()) {
            currEdge = fromHeap.poll();
            if (currEdge.isDeleted())
                continue;
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                if (accept(iter, currEdge.edge)) {
                    double tmpWeight = GHUtility.calcWeightWithTurnWeightWithAccess(weighting, iter, false, currEdge.edge) + currEdge.weightOfVisitedPath;

                    if (!Double.isInfinite(tmpWeight)) {
                        int traversalId = traversalMode.createTraversalId(iter, false);
                        AStarEntry ase = fromMap.get(traversalId);
                        millodue(iter, tmpWeight, traversalId, ase);
                    }
                }
            }

        }
    }

    private void millodue(EdgeIterator iter, double tmpWeight, int traversalId, AStarEntry ase) {
        double estimationFullWeight;
        double currWeightToGoal;
        if (ase == null || ase.weightOfVisitedPath > tmpWeight) {
            int neighborNode = iter.getAdjNode();
            currWeightToGoal = weightApprox.approximate(neighborNode);
            estimationFullWeight = tmpWeight + currWeightToGoal;
            ase = millo(estimationFullWeight, iter, tmpWeight, traversalId, ase, neighborNode);
            fromHeap.add(ase);
            updateBestPath(iter, ase, traversalId);
        }
    }

    private AStarEntry millo(double estimationFullWeight, EdgeIterator iter, double tmpWeight, int traversalId, AStarEntry ase, int neighborNode) {
        if (ase == null) {
            ase = new AStarEntry(iter.getEdge(), neighborNode, estimationFullWeight, tmpWeight, currEdge);
            fromMap.put(traversalId, ase);
        } else {
//
            ase.setDeleted();
            ase = new AStarEntry(iter.getEdge(), neighborNode, estimationFullWeight, tmpWeight, currEdge);
            fromMap.put(traversalId, ase);
        }
        return ase;
    }

    @Override
    protected boolean finished() {
        return currEdge.adjNode == to;
    }

    @Override
    protected Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return PathExtractor.extractPath(graph, weighting, currEdge);
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry bestSPTEntry, int traversalId) {
        /*
        Non so perchè non ci sia l'implementazione
         */
    }

    public static class AStarEntry extends SPTEntry {
        double weightOfVisitedPath;

        public AStarEntry(int edgeId, int adjNode, double weightForHeap, double weightOfVisitedPath) {
            this(edgeId, adjNode, weightForHeap, weightOfVisitedPath, null);
        }

        public AStarEntry(int edgeId, int adjNode, double weightForHeap, double weightOfVisitedPath, SPTEntry parent) {
            super(edgeId, adjNode, weightForHeap, parent);
            this.weightOfVisitedPath = weightOfVisitedPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            AStarEntry that = (AStarEntry) o;
            return Double.compare(that.weightOfVisitedPath, weightOfVisitedPath) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), weightOfVisitedPath);
        }

        @Override
        public final double getWeightOfVisitedPath() {
            return weightOfVisitedPath;
        }

        @Override
        public AStarEntry getParent() {
            return (AStarEntry) parent;
        }
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ASTAR + "|" + weightApprox;
    }
}
