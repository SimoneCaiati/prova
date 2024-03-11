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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.eccezionecore.PointPathException;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;

/**
 * The methods here can be used to calculate routes with or without via points and implement possible restrictions
 * like snap preventions, headings and curbsides.
 *
 * @author Peter Karich
 * @author easbar
 */
public class ViaRouting {

    public static class Nonno
    {
        EncodedValueLookup lookup;
        List<GHPoint> points;


        public Nonno(EncodedValueLookup lookup, List<GHPoint> points) {
            this.lookup = lookup;
            this.points = points;
        }
    }

    public static class Nonna
    {
        List<GHPoint> points;
        QueryGraph queryGraph;
        List<Snap> snaps;

        public Nonna(List<GHPoint> points, QueryGraph queryGraph, List<Snap> snaps) {
            this.points = points;
            this.queryGraph = queryGraph;
            this.snaps= snaps;
        }
    }


    private ViaRouting() {
    }

    /**
     * @throws MultiplePointsNotFoundException in case one or more points could not be resolved
     */
    public static List<Snap> lookup(Nonno oggetto, EdgeFilter snapFilter,
                                    LocationIndex locationIndex, List<String> snapPreventions, List<String> pointHints,
                                    DirectedEdgeFilter directedSnapFilter, List<Double> headings) {
        if (oggetto.points.size() < 2)
            throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + oggetto.points.size());

        final EnumEncodedValue<RoadClass> roadClassEnc = oggetto.lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final EnumEncodedValue<RoadEnvironment> roadEnvEnc = oggetto.lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        EdgeFilter strictEdgeFilter = snapPreventions.isEmpty()
                ? snapFilter
                : new SnapPreventionEdgeFilter(snapFilter, roadClassEnc, roadEnvEnc, snapPreventions);
        List<Snap> snaps = new ArrayList<>(oggetto.points.size());
        IntArrayList pointsNotFound = new IntArrayList();
        for (int placeIndex = 0; placeIndex < oggetto.points.size(); placeIndex++) {
            GHPoint point = oggetto.points.get(placeIndex);
            Snap snap = null;
            if (placeIndex < headings.size() && !Double.isNaN(headings.get(placeIndex))) {
                if (!pointHints.isEmpty() && !Helper.isEmpty(pointHints.get(placeIndex)))
                    throw new IllegalArgumentException("Cannot specify heading and point_hint at the same time. " +
                            "Make sure you specify either an empty point_hint (String) or a NaN heading (double) for point " + placeIndex);
                snap = locationIndex.findClosest(point.getLat(), point.getLon(), new HeadingEdgeFilter(directedSnapFilter, headings.get(placeIndex), point));
            } else if (!pointHints.isEmpty()) {
                snap = locationIndex.findClosest(point.getLat(), point.getLon(), new NameSimilarityEdgeFilter(strictEdgeFilter,
                        pointHints.get(placeIndex), point, 100));
            } else if (!snapPreventions.isEmpty()) {
                snap = locationIndex.findClosest(point.getLat(), point.getLon(), strictEdgeFilter);
            }

            snap = methodViaR34(snapFilter, locationIndex, pointsNotFound, placeIndex, point, snap);

            snaps.add(snap);
        }

        if (!pointsNotFound.isEmpty())
            throw new MultiplePointsNotFoundException(pointsNotFound);

        return snaps;
    }

    private static Snap methodViaR34(EdgeFilter snapFilter, LocationIndex locationIndex, IntArrayList pointsNotFound, int placeIndex, GHPoint point, Snap snap) {
        if (snap == null || !snap.isValid())
            snap = locationIndex.findClosest(point.getLat(), point.getLon(), snapFilter);
        if (!snap.isValid())
            pointsNotFound.add(placeIndex);
        return snap;
    }

    public static Result calcPaths(Nonna oggetto, DirectedEdgeFilter directedEdgeFilter, PathCalculator pathCalculator, List<String> curbsides, boolean forceCurbsides, List<Double> headings, boolean passThrough) throws PointPathException {
        methodVR3(oggetto.points, curbsides, headings);

        final int legs = oggetto.snaps.size() - 1;
        Result result = new Result(legs);
        for (int leg = 0; leg < legs; ++leg) {
            Snap[] snap=new Snap[2];
            snap[0] = oggetto.snaps.get(leg);
            snap[1] = oggetto.snaps.get(leg + 1);

            // enforce headings
            // at via-nodes and the target node the heading parameter is interpreted as the direction we want
            // to enforce for arriving (not starting) at this node. the starting direction is not enforced at
            // all for these points (unless using pass through). see this forum discussion:
            // https://discuss.graphhopper.com/t/meaning-of-heading-parameter-for-via-routing/5643/6
            double[] heading=new double[2];
            heading[0] = (leg == 0 && !headings.isEmpty()) ? headings.get(0) : Double.NaN;
            heading[1] = (oggetto.snaps.size() == headings.size() && !Double.isNaN(headings.get(leg + 1))) ? headings.get(leg + 1) : Double.NaN;

            // enforce pass-through
            int incomingEdge = NO_EDGE;
            incomingEdge = methodVR2(result, leg, incomingEdge);

            // enforce curbsides
            final String[] curbside=new String[2];
            curbside[0] = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg);
            curbside[1] = curbsides.isEmpty() ? CURBSIDE_ANY : curbsides.get(leg + 1);

            EdgeRestrictions edgeRestrictions = buildEdgeRestrictions(oggetto.queryGraph,snap,
                    heading, incomingEdge, passThrough,
                    curbside, directedEdgeFilter);

            edgeRestrictions.setSourceOutEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getSourceOutEdge(), leg, forceCurbsides));
            edgeRestrictions.setTargetInEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getTargetInEdge(), leg + 1, forceCurbsides));

            // calculate paths
            List<Path> paths = pathCalculator.calcPaths(snap[0].getClosestNode(), snap[1].getClosestNode(), edgeRestrictions);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(result.debug);
            stringBuilder.append(pathCalculator.getDebugString());
            result.debug = stringBuilder.toString();

            // for alternative routing we get multiple paths and add all of them (which is ok, because we do not allow
            // via-points for alternatives at the moment). otherwise we would have to return a list<list<path>> and find
            // a good method to decide how to combine the different legs
            methodVR1(result, paths);

            result.visitedNodes += pathCalculator.getVisitedNodes();
            stringBuilder = new StringBuilder();
            stringBuilder.append(result.debug);
            stringBuilder.append(", visited nodes sum: ");
            stringBuilder.append(result.visitedNodes);
            result.debug = stringBuilder.toString();
        }
        return result;
    }


    private static void methodVR3(List<GHPoint> points, List<String> curbsides, List<Double> headings) {
        if (!curbsides.isEmpty() && curbsides.size() != points.size())
            throw new IllegalArgumentException("if1 you pass " + CURBSIDE + ", you need to pass exactly one curbside for every point, empty curbsides will be ignored");
        if (!curbsides.isEmpty() && !headings.isEmpty())
            throw new IllegalArgumentException("You cannot use curbsides and headings or pass_through at the same time");
    }

    private static int methodVR2(Result result, int leg, int incomingEdge) {
        if (leg != 0) {
            // enforce straight start after via stop
            Path prevRoute = result.paths.get(leg - 1);
            if (prevRoute.getEdgeCount() > 0)
                incomingEdge = prevRoute.getFinalEdge().getEdge();
        }
        return incomingEdge;
    }

    private static void methodVR1(Result result, List<Path> paths) throws PointPathException {
        for (int i = 0; i < paths.size(); i++) {
            Path path = paths.get(i);
            if (path.getTime() < 0)
                throw new PointPathException();

            result.paths.add(path);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(result.debug);
            stringBuilder.append(", ");
            stringBuilder.append(path.getDebugInfo());
            result.debug = stringBuilder.toString();
        }
    }

    public static class Result {
        protected List<Path> paths;
        protected long visitedNodes;
        protected String debug = "";

        Result(int legs) {
            paths = new ArrayList<>(legs);
        }
    }

    /**
     * Determines restrictions for the start/target edges to account for the heading, pass_through and curbside parameters
     * for a single via-route leg.
     *
     * @param heading  array containing the heading at the start node of this leg, or NaN if no restriction should be applied
     *                 and the heading at the target node (the vehicle's heading when arriving at the target), or NaN if
     *                 no restriction should be applied
     * @param incomingEdge the last edge of the previous leg (or {@link EdgeIterator#NO_EDGE} if not available
     */
    private static EdgeRestrictions buildEdgeRestrictions(
            QueryGraph queryGraph, Snap[] snap,
            double[] heading, int incomingEdge, boolean passThrough,
            String[] curbside, DirectedEdgeFilter edgeFilter) {
        EdgeRestrictions edgeRestrictions = new EdgeRestrictions();

        // curbsides
        if (!curbside[0].equals(CURBSIDE_ANY) || !curbside[1].equals(CURBSIDE_ANY)) {
            DirectionResolver directionResolver = new DirectionResolver(queryGraph, edgeFilter);
            DirectionResolverResult fromDirection = directionResolver.resolveDirections(snap[0].getClosestNode(), snap[0].getQueryPoint());
            DirectionResolverResult toDirection = directionResolver.resolveDirections(snap[1].getClosestNode(), snap[1].getQueryPoint());
            int sourceOutEdge = DirectionResolverResult.getOutEdge(fromDirection, curbside[0]);
            int targetInEdge = DirectionResolverResult.getInEdge(toDirection, curbside[1]);
                // special case where we go from one point back to itself. for example going from a point A
                // with curbside right to the same point with curbside right is interpreted as 'being there
                // already' -> empty path. Similarly if the curbside for the start/target is not even specified
                // there is no need to drive a loop. However, going from point A/right to point A/left (or the
                // other way around) means we need to drive some kind of loop to get back to the same location
                // (arriving on the other side of the road).
                if (snap[0].getClosestNode() == snap[1].getClosestNode()&&(Helper.isEmpty(curbside[0]) || Helper.isEmpty(curbside[1]) ||
                        curbside[0].equals(CURBSIDE_ANY) || curbside[1].equals(CURBSIDE_ANY) ||
                        curbside[0].equals(curbside[1]))) {
                    // we just disable start/target edge constraints to get an empty path
                    sourceOutEdge = ANY_EDGE;
                    targetInEdge = ANY_EDGE;
                }

            edgeRestrictions.setSourceOutEdge(sourceOutEdge);
            edgeRestrictions.setTargetInEdge(targetInEdge);
        }

        // heading
        if (!Double.isNaN(heading[0]) || !Double.isNaN(heading[1])) {
            // for heading/pass_through with edge-based routing (especially CH) we have to find the edge closest
            // to the heading and use it as sourceOutEdge/targetInEdge here. the heading penalty will not be applied
            // this way (unless we implement this), but this is more or less ok as we can use finite u-turn costs
            // instead. maybe the hardest part is dealing with headings that cannot be fulfilled, like in one-way
            // streets. see also #1765
            HeadingResolver headingResolver = new HeadingResolver(queryGraph);
            if (!Double.isNaN(heading[0]))
                edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(snap[0].getClosestNode(), heading[0]));

            methodViaR1(snap, heading, edgeRestrictions, headingResolver);
        }

        // pass through
        if (incomingEdge != NO_EDGE && passThrough)
            edgeRestrictions.getUnfavoredEdges().add(incomingEdge);
        return edgeRestrictions;
    }

    private static void methodViaR1(Snap[] snap, double[] heading, EdgeRestrictions edgeRestrictions, HeadingResolver headingResolver) {
        if (!Double.isNaN(heading[1])) {
            heading[1] += 180;
            if (heading[1] > 360)
                heading[1] -= 360;
            edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(snap[1].getClosestNode(), heading[1]));
        }
    }

    private static int ignoreThrowOrAcceptImpossibleCurbsides(List<String> curbsides, int edge, int placeIndex, boolean forceCurbsides) {
        if (edge != NO_EDGE) {
            return edge;
        }
        if (forceCurbsides) {
            return throwImpossibleCurbsideConstraint(curbsides, placeIndex);
        } else {
            return ANY_EDGE;
        }
    }

    private static int throwImpossibleCurbsideConstraint(List<String> curbsides, int placeIndex) {
        throw new IllegalArgumentException("Impossible curbside constraint: 'curbside=" + curbsides.get(placeIndex) + "' at point " + placeIndex);
    }

}
