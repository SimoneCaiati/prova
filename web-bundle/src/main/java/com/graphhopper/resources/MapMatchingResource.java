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
package com.graphhopper.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.jackson.Gpx;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.matching.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;

import static com.graphhopper.resources.RouteResource.removeLegacyParameters;
import static com.graphhopper.util.Parameters.Details.PATH_DETAILS;
import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Resource to use map matching of GraphHopper in a remote client application.
 *
 * @author Peter Karich
 */
@javax.ws.rs.Path("match")
public class MapMatchingResource {
    private static final String GX="X-GH-Took";

    public interface MapMatchingRouterFactory {
        public MapMatching.Router createMapMatchingRouter(PMap hints);
    }


    private final GraphHopper graphHopper;
    private final ProfileResolver profileResolver;
    private final TranslationMap trMap;
    private final MapMatchingRouterFactory mapMatchingRouterFactory;

    @Inject
    public MapMatchingResource(GraphHopper graphHopper, ProfileResolver profileResolver, TranslationMap trMap, MapMatchingRouterFactory mapMatchingRouterFactory) {
        this.graphHopper = graphHopper;
        this.profileResolver = profileResolver;
        this.trMap = trMap;
        this.mapMatchingRouterFactory = mapMatchingRouterFactory;
    }
    private static final String HOT="profile";
    @POST
    @Consumes({MediaType.APPLICATION_XML, "application/gpx+xml"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response match(
            Gpx gpx,
            @Context UriInfo uriInfo,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("type") @DefaultValue("json") String outType,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(HOT) String profile,
            @QueryParam(PATH_DETAILS) List<String> pathDetails,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("traversal_keys") @DefaultValue("false") boolean enableTraversalKeys,
            @QueryParam("gps_accuracy") @DefaultValue("40") double gpsAccuracy) throws MapExc {

        boolean writeGPX = "gpx".equalsIgnoreCase(outType);
        methodMapRes123(gpx);

        instructions = writeGPX || instructions;

        StopWatch sw = new StopWatch().start();

        PMap hints = new PMap();
        RouteResource.initHints(hints, uriInfo.getQueryParameters());

        // resolve profile and remove legacy vehicle/weighting parameters
        // we need to explicitly disable CH here because map matching does not use it
        PMap profileResolverHints = new PMap(hints);
        profileResolverHints.putObject(HOT, profile);
        profileResolverHints.putObject(Parameters.CH.DISABLE, true);
        profile = profileResolver.resolveProfile(profileResolverHints);
        hints.putObject(HOT, profile);
        removeLegacyParameters(hints);

        MapMatching matching = new MapMatching(graphHopper.getBaseGraph(), (LocationIndexTree) graphHopper.getLocationIndex(), mapMatchingRouterFactory.createMapMatchingRouter(hints));
        matching.setMeasurementErrorSigma(gpsAccuracy);

        List<Observation> measurements = GpxConversions.getEntries(gpx.trk.get(0));
        MatchResult matchResult = matching.match(measurements);

        sw.stop();


        if ("extended_json".equals(outType)) {
            return Response.ok(convertToTree(matchResult, enableElevation, pointsEncoded)).
                    header(GX, "" + Math.round(sw.getMillisDouble())).
                    build();
        } else {
            Translation tr = trMap.getWithFallBack(Helper.getLocale(localeStr));
            RamerDouglasPeucker simplifyAlgo = new RamerDouglasPeucker().setMaxDistance(minPathPrecision);
            PathMerger pathMerger = new PathMerger(matchResult.getGraph(), matchResult.getWeighting()).
                    setEnableInstructions(instructions).
                    setPathDetailsBuilders(graphHopper.getPathDetailsBuilderFactory(), pathDetails).
                    setRamerDouglasPeucker(simplifyAlgo).
                    setSimplifyResponse(minPathPrecision > 0);
            ResponsePath responsePath = pathMerger.doWork(PointList.EMPTY, Collections.singletonList(matchResult.getMergedPath()),
                    graphHopper.getEncodingManager(), tr);

            // GraphHopper thinks an empty path is an invalid path, and further that an invalid path is still a path but
            // marked with a non-empty list of Exception objects. I disagree, so I clear it.
            responsePath.getErrors().clear();
            GHResponse rsp = new GHResponse();
            rsp.add(responsePath);

            if (writeGPX) {
                long time = gpx.trk.get(0).getStartTime()
                        .map(Date::getTime)
                        .orElse(System.currentTimeMillis());
                return Response.ok(GpxConversions.createGPX(new GpxConversions.Carlo(rsp.getBest().getInstructions(), gpx.trk.get(0).name != null ? gpx.trk.get(0).name : "", time, enableElevation), withRoute, withTrack, false, Constants.VERSION, tr), "application/gpx+xml").
                        header(GX, "" + Math.round(sw.getMillisDouble())).
                        build();
            } else {
                ObjectNode map = ResponsePathSerializer.jsonObject(rsp, instructions, calcPoints, enableElevation, pointsEncoded, sw.getMillisDouble());

                Map<String, Object> matchStatistics = new HashMap<>();
                matchStatistics.put("distance", matchResult.getMatchLength());
                matchStatistics.put("time", matchResult.getMatchMillis());
                matchStatistics.put("original_distance", matchResult.getGpxEntriesLength());
                map.putPOJO("map_matching", matchStatistics);

                if (enableTraversalKeys) {
                    List<Integer> traversalKeylist = new ArrayList<>();
                    methodMapMatch123(matchResult, traversalKeylist);
                    map.putPOJO("traversal_keys", traversalKeylist);
                }
                return Response.ok(map).
                        header(GX, "" + Math.round(sw.getMillisDouble())).
                        build();
            }
        }
    }

    private static void methodMapMatch123(MatchResult matchResult, List<Integer> traversalKeylist) {
        for (EdgeMatch em : matchResult.getEdgeMatches()) {
            EdgeIteratorState edge = em.getEdgeState();
            // encode edges as traversal keys which includes orientation, decode simply by multiplying with 0.5
            traversalKeylist.add(edge.getEdgeKey());
        }
    }

    private static void methodMapRes123(Gpx gpx) {
        if (gpx.trk.isEmpty()) {
            throw new IllegalArgumentException("No tracks found in GPX document. Are you using waypoints or routes instead?");
        }
        if (gpx.trk.size() > 1) {
            throw new IllegalArgumentException("GPX documents with multiple tracks not supported yet.");
        }
    }

    public static JsonNode convertToTree(MatchResult result, boolean elevation, boolean pointsEncoded) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode diary = root.putObject("diary");
        ArrayNode entries = diary.putArray("entries");
        ObjectNode route = entries.addObject();
        ArrayNode links = route.putArray("links");
        for (int emIndex = 0; emIndex < result.getEdgeMatches().size(); emIndex++) {
            ObjectNode link = links.addObject();
            EdgeMatch edgeMatch = result.getEdgeMatches().get(emIndex);
            PointList pointList = edgeMatch.getEdgeState().fetchWayGeometry(emIndex == 0 ? FetchMode.ALL : FetchMode.PILLAR_AND_ADJ);
            final ObjectNode geometry = link.putObject("geometry");
            if (pointList.size() < 2) {
                geometry.putPOJO("coordinates", pointsEncoded ? ResponsePathSerializer.encodePolyline(pointList, elevation, 1e5) : pointList.toLineString(elevation));
                geometry.put("type", "Point");
            } else {
                geometry.putPOJO("coordinates", pointsEncoded ? ResponsePathSerializer.encodePolyline(pointList, elevation, 1e5) : pointList.toLineString(elevation));
                geometry.put("type", "LineString");
            }
            link.put("id", edgeMatch.getEdgeState().getEdge());
            ArrayNode wpts = link.putArray("wpts");
            for (State extension : edgeMatch.getStates()) {
                ObjectNode wpt = wpts.addObject();
                wpt.put("x", extension.getSnap().getSnappedPoint().getLon());
                wpt.put("y", extension.getSnap().getSnappedPoint().getLat());
            }
        }
        return root;
    }

}
