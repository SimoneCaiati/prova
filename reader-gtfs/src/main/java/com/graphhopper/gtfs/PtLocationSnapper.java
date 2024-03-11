package com.graphhopper.gtfs;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class PtLocationSnapper {

    public static class Result {
        public final QueryGraph queryGraph;
        public final List<Label.NodeId> nodes;
        public final PointList points;

        public Result(QueryGraph queryGraph, List<Label.NodeId> nodes, PointList points) {
            this.queryGraph = queryGraph;
            this.nodes = nodes;
            this.points = points;
        }
    }

    BaseGraph baseGraph;
    LocationIndex locationIndex;
    GtfsStorage gtfsStorage;

    public PtLocationSnapper(BaseGraph baseGraph, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        this.baseGraph = baseGraph;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
    }

    public Result snapAll(List<GHLocation> locations, List<EdgeFilter> snapFilters) {
        PointList points = new PointList(2, false);
        ArrayList<Snap> pointSnaps = new ArrayList<>();
        ArrayList<Supplier<Label.NodeId>> allSnaps = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            GHLocation location = locations.get(i);
            if (location instanceof GHPointLocation) {
                GHPoint point = ((GHPointLocation) location).ghPoint;
                final Snap closest = locationIndex.findClosest(point.getLat(), point.getLon(), snapFilters.get(i));
                if (!closest.isValid()) {
                    IntHashSet result = new IntHashSet();
                    gtfsStorage.getStopIndex().findEdgeIdsInNeighborhood(point.getLat(), point.getLon(), 0, result::add);
                    gtfsStorage.getStopIndex().findEdgeIdsInNeighborhood(point.getLat(), point.getLon(), 1, result::add);
                    molly(i, point, result);
                    IntCursor stopNodeId = result.iterator().next();
                    mollyuno(points, allSnaps, stopNodeId);
                } else {
                    pointSnaps.add(closest);
                    allSnaps.add(() -> new Label.NodeId(closest.getClosestNode(), Optional.ofNullable(gtfsStorage.getStreetToPt().get(closest.getClosestNode())).orElse(-1)));
                    points.add(closest.getSnappedPoint());
                }
            } else if (location instanceof GHStationLocation) {
                final Snap stopSnap = findByStopId((GHStationLocation) location, i);
                allSnaps.add(() -> new Label.NodeId(Optional.ofNullable(gtfsStorage.getPtToStreet().get(stopSnap.getClosestNode())).orElse(-1), stopSnap.getClosestNode()));
                points.add(stopSnap.getQueryPoint().getLat(), stopSnap.getQueryPoint().getLon());
            }
        }
        QueryGraph queryGraph = QueryGraph.create(baseGraph.getBaseGraph(), pointSnaps); // modifies pointSnaps!

        List<Label.NodeId> nodes = new ArrayList<>();
        for (Supplier<Label.NodeId> supplier : allSnaps) {
            nodes.add(supplier.get());
        }
        return new Result(queryGraph, nodes, points);
    }

    private void mollyuno(PointList points, ArrayList<Supplier<Label.NodeId>> allSnaps, IntCursor stopNodeId) {
        for (Map.Entry<GtfsStorage.FeedIdWithStopId, Integer> e : gtfsStorage.getStationNodes().entrySet()) {
            if (e.getValue() == stopNodeId.value) {
                Stop stop = gtfsStorage.getGtfsFeeds().get(e.getKey().feedId).stops.get(e.getKey().stopId);
                final Snap stopSnap = new Snap(stop.getStopLat(), stop.getStopLon());
                stopSnap.setClosestNode(stopNodeId.value);
                allSnaps.add(() -> new Label.NodeId(Optional.ofNullable(gtfsStorage.getPtToStreet().get(stopSnap.getClosestNode())).orElse(-1), stopSnap.getClosestNode()));
                points.add(stopSnap.getQueryPoint().getLat(), stopSnap.getQueryPoint().getLon());
            }
        }
    }

    private static void molly(int i, GHPoint point, IntHashSet result) {
        if (result.isEmpty()) {
            throw new PointNotFoundException("Cannot find point: " + point, i);
        }
    }

    private Snap findByStopId(GHStationLocation station, int indexForErrorMessage) {
        for (Map.Entry<String, GTFSFeed> entry : gtfsStorage.getGtfsFeeds().entrySet()) {
            final Integer node = gtfsStorage.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(entry.getKey(), station.stopId));
            if (node != null) {
                Stop stop = gtfsStorage.getGtfsFeeds().get(entry.getKey()).stops.get(station.stopId);
                final Snap stationSnap = new Snap(stop.getStopLat(), stop.getStopLon());
                stationSnap.setClosestNode(node);
                return stationSnap;
            }
        }
        throw new PointNotFoundException("Cannot find station: " + station.stopId, indexForErrorMessage);
    }


}
