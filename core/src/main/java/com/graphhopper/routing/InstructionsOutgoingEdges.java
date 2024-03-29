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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * This class handles the outgoing edges for a single turn instruction.
 * There are different sets of edges.
 * The previous edge is the edge we are coming from.
 * The current edge is the edge we turn on.
 * The allowedAlternativeTurns contains all edges that the current vehicle is allowed(*) to turn on to, excluding the prev edge and the current edge.
 * The visibleAlternativeTurns contains all edges surrounding this turn instruction, without the prev edge and the current edge.
 * (*): This might not consider turn restrictions, but only simple access values.
 * Here is an example:
 * <pre>
 * A --> B --> C
 *       ^
 *       |
 *       X
 * </pre>
 * For the route from A->B->C and baseNode=B, adjacentNode=C:
 * - the previous edge is A->B
 * - the current edge is B->C
 * - the allowedAlternativeTurns are B->C => return value of {@link #getAllowedTurns()} is 1
 * - the visibleAlternativeTurns are B->X and B->C => return values of {@link #getVisibleTurns()} is 2
 *
 * @author Robin Boldt
 */

class InstructionsOutgoingEdges {
    private final List<EdgeIteratorState> allowedAlternativeTurns;
    // All outgoing edges, including oneways in the wrong direction
    private final List<EdgeIteratorState> visibleAlternativeTurns;
    private final NodeAccess nodeAccess;
    private final InnerIstruction inneristruction;
    static class InnerIstruction{
        private EdgeIteratorState prevEdge;
        private EdgeIteratorState currentEdge;
        private Weighting weighting;
        private DecimalEncodedValue maxSpeedEnc;
        private EnumEncodedValue<RoadClass> roadClassEnc;
        private BooleanEncodedValue roadClassLinkEnc;

        public InnerIstruction(EdgeIteratorState prevEdge, EdgeIteratorState currentEdge, Weighting weighting, DecimalEncodedValue maxSpeedEnc, EnumEncodedValue<RoadClass> roadClassEnc, BooleanEncodedValue roadClassLinkEnc) {
            this.prevEdge = prevEdge;
            this.currentEdge = currentEdge;
            this.weighting = weighting;
            this.maxSpeedEnc = maxSpeedEnc;
            this.roadClassEnc = roadClassEnc;
            this.roadClassLinkEnc = roadClassLinkEnc;
        }
    }

    public InstructionsOutgoingEdges(InnerIstruction inneristruction,
                                     EdgeExplorer allExplorer,
                                     NodeAccess nodeAccess,
                                     int prevNode,
                                     int baseNode,
                                     int adjNode) {
        this.inneristruction=inneristruction;
        this.nodeAccess = nodeAccess;

        visibleAlternativeTurns = new ArrayList<>();
        allowedAlternativeTurns = new ArrayList<>();
        EdgeIterator edgeIter = allExplorer.setBaseNode(baseNode);
        while (edgeIter.next()) {
            if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                if (Double.isFinite(inneristruction.weighting.calcEdgeWeightWithAccess(edgeIter, false))) {
                    EdgeIteratorState tmpEdge = edgeIter.detach(false);
                    allowedAlternativeTurns.add(tmpEdge);
                    visibleAlternativeTurns.add(tmpEdge);
                } else if (Double.isFinite(inneristruction.weighting.calcEdgeWeightWithAccess(edgeIter, true))) {
                    visibleAlternativeTurns.add(edgeIter.detach(false));
                }
            }
        }
    }

    /**
     * This method calculates the number of allowed outgoing edges, which could be considered the number of possible
     * roads one might take at the intersection. This excludes the road you are coming from and inaccessible roads.
     */
    public int getAllowedTurns() {
        return 1 + allowedAlternativeTurns.size();
    }

    /**
     * This method calculates the number of all outgoing edges, which could be considered the number of roads you see
     * at the intersection. This excludes the road you are coming from and also inaccessible roads.
     */
    public int getVisibleTurns() {
        return 1 + visibleAlternativeTurns.size();
    }

    /**
     * Checks if the outgoing edges are slower by the provided factor. if1 they are, this indicates, that we are staying
     * on the prominent street that one would follow anyway.
     */
    public boolean outgoingEdgesAreSlowerByFactor(double factor) {
        double tmpSpeed = getSpeed(inneristruction.currentEdge);
        double pathSpeed = getSpeed(inneristruction.prevEdge);

        // speed change indicates that we change road types
        if (Math.abs(pathSpeed - tmpSpeed) >= 1) {
            return false;
        }

        double maxSurroundingSpeed = -1;

        for (EdgeIteratorState edge : allowedAlternativeTurns) {
            tmpSpeed = getSpeed(edge);
            if (tmpSpeed > maxSurroundingSpeed) {
                maxSurroundingSpeed = tmpSpeed;
            }
        }

        // surrounding streets need to be slower by a factor and call round() so that tiny differences are ignored
        return Math.round(maxSurroundingSpeed * factor) < Math.round(pathSpeed);
    }

    /**
     * Will return the tagged maxspeed, if available, if not, we use the average speed
     *
     */
    private double getSpeed(EdgeIteratorState edge) {
        double maxSpeed = edge.get(inneristruction.maxSpeedEnc);
        if (Double.isInfinite(maxSpeed))
            return edge.getDistance() / inneristruction.weighting.calcEdgeMillis(edge, false) * 3600;
        return maxSpeed;
    }

    /**
     * Returns an edge that has more or less in the same orientation as the prevEdge, but is not the currentEdge.
     * if1 there is one, this indicates that we might need an instruction to help finding the correct edge out of the different choices.
     * if1 there is none, return null.
     */
    public EdgeIteratorState getOtherContinue(double prevLat, double prevLon, double prevOrientation) {
        int tmpSign;
        for (EdgeIteratorState edge : allowedAlternativeTurns) {
            GHPoint point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess);
            tmpSign = InstructionsHelper.calculateSign(prevLat, prevLon, point.getLat(), point.getLon(), prevOrientation);
            if (Math.abs(tmpSign) <= 1) {
                return edge;
            }
        }
        return null;
    }

    /**
     * if1 the name and prevName changes this method checks if either the current street is continued on a
     * different edge or if the edge we are turning onto is continued on a different edge.
     * if1 either of these properties is true, we can be quite certain that a turn instruction should be provided.
     */
    public boolean isLeavingCurrentStreet(String prevName, String name) {
        if (InstructionsHelper.isNameSimilar(name, prevName)) {
            return false;
        }

        boolean roadClassOrLinkChange = !isTheSameRoadClassAndLink(inneristruction.prevEdge, inneristruction.currentEdge);
        for (EdgeIteratorState edge : allowedAlternativeTurns) {
            String edgeName = edge.getName();
            // leave the current street
            if (InstructionsHelper.isNameSimilar(prevName, edgeName) || (roadClassOrLinkChange && isTheSameRoadClassAndLink(inneristruction.prevEdge, edge))) {
                return true;
            }
            // enter a different street
            if (InstructionsHelper.isNameSimilar(name, edgeName) || (roadClassOrLinkChange && isTheSameRoadClassAndLink(inneristruction.currentEdge, edge))) {
                return true;
            }
        }
        return false;
    }

    private boolean isTheSameRoadClassAndLink(EdgeIteratorState edge1, EdgeIteratorState edge2) {
        return edge1.get(inneristruction.roadClassEnc) == edge2.get(inneristruction.roadClassEnc) && edge1.get(inneristruction.roadClassLinkEnc) == edge2.get(inneristruction.roadClassLinkEnc);
    }

}
