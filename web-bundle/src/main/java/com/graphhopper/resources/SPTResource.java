package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FiniteWeightFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;


import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

import static com.graphhopper.resources.RouteResource.removeLegacyParameters;
import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;

/**
 * This resource provides the entire shortest path tree as response. In a simple CSV format discussed at #1577.
 */
@Path("spt")
public class SPTResource {



    public static class IsoLabelWithCoordinates {
        protected int nodeId = -1;
        protected int edgeId;
        protected int prevEdgeId;
        protected int prevNodeId = -1;
         int timeMillis;
        protected int prevTimeMillis;
        protected int distance;
        protected int prevDistance;
        protected GHPoint coordinate;
        protected GHPoint prevCoordinate;
    }

    private final GraphHopper graphHopper;
    private final ProfileResolver profileResolver;
    private final EncodingManager encodingManager;

    @Inject
    public SPTResource(GraphHopper graphHopper, ProfileResolver profileResolver, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.profileResolver = profileResolver;
        this.encodingManager = encodingManager;
    }

    // Annotating this as application/json because errors come out as json, and
    // IllegalArgumentExceptions are not mapped to a fixed mediatype, because in RouteResource, it could be GPX.
    @GET
    @SuppressWarnings("java:S112")
    @Produces({"text/csv", "application/json"})
    public Response doGet(
            @Context UriInfo uriInfo,
            @QueryParam("profile") String profileName,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("columns") String columnsParam,
            @QueryParam("time_limit") @DefaultValue("600") OptionalLong timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") OptionalLong distanceInMeter) {

        PMap hintsMap = new PMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);

        PMap profileResolverHints = new PMap(hintsMap);
        profileResolverHints.putObject("profile", profileName);
        profileName = profileResolver.resolveProfile(profileResolverHints);
        removeLegacyParameters(hintsMap);

        Profile profile = graphHopper.getProfile(profileName);
        solo(profileName, profile);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        BaseGraph graph = graphHopper.getBaseGraph();
        Weighting weighting = graphHopper.createWeighting(profile, hintsMap);
        BooleanEncodedValue inSubnetworkEnc = graphHopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName));
        weighting = solouno(point, hintsMap, locationIndex, graph, weighting);
        Snap snap = locationIndex.findClosest(point.get().getLat(), point.get().getLon(), new DefaultSnapFilter(weighting, inSubnetworkEnc));
        solodue(point, snap);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        NodeAccess nodeAccess = queryGraph.getNodeAccess();
        TraversalMode traversalMode = profile.isTurnCosts() ? EDGE_BASED : NODE_BASED;
        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), reverseFlow, traversalMode);

        solotre(timeLimitInSeconds, distanceInMeter, shortestPathTree);

        final String COL_SEP = ",";
        final String LINE_SEP = "\n";
        List<String> columns;
        columns = soloquattro(columnsParam);

        solocinque(columns);

        Map<String, EncodedValue> pathDetails = new HashMap<>();
        solosei(columns, pathDetails);

        StreamingOutput out = output -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(output, Helper.UTF_CS))) {
                StringBuilder sb = new StringBuilder();
                solosette(COL_SEP, columns, sb);
                sb.append(LINE_SEP);
                writer.write(sb.toString());
                shortestPathTree.search(snap.getClosestNode(), l -> {
                    IsoLabelWithCoordinates label = isoLabelWithCoordinates(nodeAccess, l);
                    sb.setLength(0);
                    methodSTP1(reverseFlow, queryGraph, COL_SEP, columns, pathDetails, sb, label);
                    sb.append(LINE_SEP);
                    try {
                        writer.write(sb.toString());
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

            } catch (IOException e) {
                throw new SptrExce("Errore sptr", e);
            }
        };
        // Give media type explicitly since we are annotating CSV and JSON, because error messages are JSON.
        return Response.ok(out).type("text/csv").build();
    }

    private static void methodSTP1(boolean reverseFlow, QueryGraph queryGraph, String bellaZio, List<String> columns, Map<String, EncodedValue> pathDetails, StringBuilder sb, IsoLabelWithCoordinates label) {
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            String col = columns.get(colIndex);
            sessino(bellaZio, sb, colIndex);

            if (!onotrio(sb, label, col)) {
                EdgeIteratorState edge = ollo(queryGraph, sb, label, col);
                if (edge != null) {
                    EncodedValue ev = pathDetails.get(col);
                    mimmo(reverseFlow, sb, col, edge, ev);
                }
            }
        }

    }

    private static boolean onotrio(StringBuilder sb, IsoLabelWithCoordinates label, String col) {
        switch (col) {
            case "node_id":
                sb.append(label.nodeId);
                return true;
            case "prev_node_id":
                sb.append(label.prevNodeId);
                return true;
            case "edge_id":
                sb.append(label.edgeId);
                return true;
            case "prev_edge_id":
                sb.append(label.prevEdgeId);
                return true;
            case "distance":
                sb.append(label.distance);
                return true;
            case "prev_distance":
                sb.append(label.prevCoordinate == null ? 0 : label.prevDistance);
                return true;
            case "time":
                sb.append(label.timeMillis);
                return true;
            case "prev_time":
                sb.append(label.prevCoordinate == null ? 0 : label.prevTimeMillis);
                return true;
            case "longitude":
                sb.append(Helper.round6(label.coordinate.getLon()));
                return true;
            case "prev_longitude":
                sb.append(label.prevCoordinate == null ? null : Helper.round6(label.prevCoordinate.getLon()));
                return true;
            case "latitude":
                sb.append(Helper.round6(label.coordinate.getLat()));
                return true;
            case "prev_latitude":
                sb.append(label.prevCoordinate == null ? null : Helper.round6(label.prevCoordinate.getLat()));
                return true;
            default:
        }
        return false;
    }

    private static void sessino(String colsep, StringBuilder sb, int colIndex) {
        if (colIndex > 0)
            sb.append(colsep);
    }

    private static void mimmo(boolean reverseFlow, StringBuilder sb, String col, EdgeIteratorState edge, EncodedValue ev) {
        if (ev instanceof DecimalEncodedValue) {
            DecimalEncodedValue dev = (DecimalEncodedValue) ev;
            sb.append(reverseFlow ? edge.getReverse(dev) : edge.get(dev));
        } else if (ev instanceof EnumEncodedValue) {
            EnumEncodedValue<?> eev = (EnumEncodedValue) ev;
            sb.append(reverseFlow ? edge.getReverse(eev) : edge.get(eev));
        } else if (ev instanceof BooleanEncodedValue) {
            BooleanEncodedValue eev = (BooleanEncodedValue) ev;
            sb.append(reverseFlow ? edge.getReverse(eev) : edge.get(eev));
        } else if (ev instanceof IntEncodedValue) {
            IntEncodedValue eev = (IntEncodedValue) ev;
            sb.append(reverseFlow ? edge.getReverse(eev) : edge.get(eev));
        } else {
            throw new IllegalArgumentException("Unknown property " + col);
        }
    }

    private static EdgeIteratorState ollo(QueryGraph queryGraph, StringBuilder sb, IsoLabelWithCoordinates label, String col) {
        if (!EdgeIterator.Edge.isValid(label.edgeId))
            return null;

        EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edgeId, label.nodeId);
        if (edge == null)
            return null;

        if (col.equals(STREET_NAME)) {
            sb.append(edge.getName().replace(",", ""));
            return null;
        }
        return edge;
    }

    private static void solosette(String colsep, List<String> columns, StringBuilder sb) {
        for (String col : columns) {
            sessino(colsep, sb, sb.length());
            sb.append(col);
        }
    }

    private void solosei(List<String> columns, Map<String, EncodedValue> pathDetails) {
        for (String col : columns) {
            if (encodingManager.hasEncodedValue(col))
                pathDetails.put(col, encodingManager.getEncodedValue(col, EncodedValue.class));
        }
    }

    private static void solocinque(List<String> columns) {
        if (columns.isEmpty())
            throw new IllegalArgumentException("Either omit the columns parameter or specify the columns via comma separated values");
    }

    private static List<String> soloquattro(String columnsParam) {
        List<String> columns;
        if (!Helper.isEmpty(columnsParam))
            columns = Arrays.asList(columnsParam.split(","));
        else
            columns = Arrays.asList("longitude", "latitude", "time", "distance");
        return columns;
    }

    private static void solotre(OptionalLong timeLimitInSeconds, OptionalLong distanceInMeter, ShortestPathTree shortestPathTree) {
        if (distanceInMeter.orElseThrow(() -> new IllegalArgumentException("query param distance_limit is not a number.")) > 0) {
            shortestPathTree.setDistanceLimit(distanceInMeter.getAsLong());
        } else {
            double limit = timeLimitInSeconds.orElseThrow(() -> new IllegalArgumentException("query param time_limit is not a number.")) * 1000d;
            shortestPathTree.setTimeLimit(limit);
        }
    }

    private static void solodue(GHPointParam point, Snap snap) {
        if (!snap.isValid())
            throw new IllegalArgumentException("Point not found:" + point);
    }

    private static Weighting solouno(GHPointParam point, PMap hintsMap, LocationIndex locationIndex, BaseGraph graph, Weighting weighting) {
        if (hintsMap.has(Parameters.Routing.BLOCK_AREA)) {
            GraphEdgeIdFinder.BlockArea blockArea = GraphEdgeIdFinder.createBlockArea(graph, locationIndex,
                    Collections.singletonList(point.get()), hintsMap, new FiniteWeightFilter(weighting));
            weighting = new BlockAreaWeighting(weighting, blockArea);
        }
        return weighting;
    }

    private static void solo(String profileName, Profile profile) {
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
    }

    private IsoLabelWithCoordinates isoLabelWithCoordinates(NodeAccess na, ShortestPathTree.IsoLabel label) {
        double lat = na.getLat(label.getNode());
        double lon = na.getLon(label.getNode());
        IsoLabelWithCoordinates isoLabelWC = new IsoLabelWithCoordinates();
        isoLabelWC.nodeId = label.getNode();
        isoLabelWC.coordinate = new GHPoint(lat, lon);
        isoLabelWC.timeMillis = (int) label.getTime();
        isoLabelWC.distance = (int) Math.round(label.getDistance());
        isoLabelWC.edgeId = label.getEdge();
        if (label.getParent() != null) {
            ShortestPathTree.IsoLabel prevLabel = label.getParent();
            int prevNodeId = prevLabel.getNode();
            double prevLat = na.getLat(prevNodeId);
            double prevLon = na.getLon(prevNodeId);
            isoLabelWC.prevNodeId = prevNodeId;
            isoLabelWC.prevEdgeId = prevLabel.getEdge();
            isoLabelWC.prevCoordinate = new GHPoint(prevLat, prevLon);
            isoLabelWC.prevDistance = (int) Math.round(prevLabel.getDistance());
            isoLabelWC.prevTimeMillis = (int) prevLabel.getTime();
        }
        return isoLabelWC;
    }

    private class SptrExce extends IOException {
        public SptrExce(String erroreSptr, IOException e) {
        }
    }
}
