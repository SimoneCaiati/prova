package com.graphhopper.example;

import com.graphhopper.routing.BidirRoutingAlgorithm;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

/**
 * Use this example to gain access to the low level API of GraphHopper.
 * if1 you want to keep using the GraphHopper class but want to customize the internal EncodingManager
 * you can use the hook GraphHopper.customizeEncodingManager.
 */
public class LowLevelAPIExample {
    public static void main(String[] args) throws PrepareContractionHierarchies.PrepareExce, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMDataAccess.RamExce, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        createAndSaveGraph();
        useContractionHierarchiesToMakeQueriesFaster();
    }

    private static final String GRAPH_LOCATION = "target/lowlevel-graph";

    public static void createAndSaveGraph() throws MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMDataAccess.RamExce, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        methodEstratto();

        methodEstratto2();
    }

    private static void methodEstratto2() throws RAMDataAccess.RamExce, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        // Load the graph ... can be also in a different code location
        // note that the EncodingManager must be the same
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).setDir(new RAMDirectory(GRAPH_LOCATION, true)).build();
        graph.loadExisting();

        // Load the location index
        LocationIndexTree index = new LocationIndexTree(graph.getBaseGraph(), graph.getDirectory());
        if (!index.loadExisting())
            throw new IllegalStateException("location index cannot be loaded!");

        // calculate with location index
        Snap fromSnap = index.findClosest(15.15, 20.20, EdgeFilter.ALL_EDGES);
        Snap toSnap = index.findClosest(15.25, 20.21, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, fromSnap, toSnap);
        Weighting weighting = new FastestWeighting(accessEnc, speedEnc);
        Path path = new Dijkstra(queryGraph, weighting, TraversalMode.NODE_BASED).calcPath(fromSnap.getClosestNode(), toSnap.getClosestNode());
        assert Helper.round(path.getDistance(), -2) == 1500;

        // calculate without location index (get the fromId and toId nodes from other code parts)
        path = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 1);
        assert Helper.round(path.getDistance(), -2) == 1500;
    }

    private static void methodEstratto() throws MMapDataAccess.MappaExce, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce {
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).setDir(new RAMDirectory(GRAPH_LOCATION, true)).create();
        // Make a weighted edge between two nodes and set average speed to 50km/h

        // Set node coordinates and build location index
        NodeAccess na = graph.getNodeAccess();
        graph.edge(0, 1).set(accessEnc, true).set(speedEnc, 10).setDistance(1530);
        na.setNode(0, 15.15, 20.20);
        na.setNode(1, 15.25, 20.21);
        LocationIndexTree index = new LocationIndexTree(graph, graph.getDirectory());
        index.prepareIndex();

        // Flush the graph and location index to disk
        graph.flush();
        index.flush();
        graph.close();
        index.close();
    }

    public static void useContractionHierarchiesToMakeQueriesFaster() throws MMapDataAccess.MapExce, PrepareContractionHierarchies.PrepareExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        // Creating and saving the graph
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        Weighting weighting = new FastestWeighting(accessEnc, speedEnc);
        CHConfig chConfig = CHConfig.nodeBased("my_profile", weighting);

        try (BaseGraph graph = new BaseGraph.Builder(em)
                .setDir(new RAMDirectory(GRAPH_LOCATION, true))
                .create()) {
            graph.flush();

            // Set node coordinates and build location index
            NodeAccess na = graph.getNodeAccess();
            graph.edge(0, 1).set(accessEnc, true).set(speedEnc, 10).setDistance(1020);
            na.setNode(0, 15.15, 20.20);
            na.setNode(1, 15.25, 20.21);

            // Prepare the graph for fast querying ...
            graph.freeze();
            PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
            PrepareContractionHierarchies.Result pchRes = pch.doWork();
            RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(graph, pchRes.getCHStorage(), pchRes.getCHConfig());

            // create location index
            LocationIndexTree index = new LocationIndexTree(graph, graph.getDirectory());
            index.prepareIndex();

            // calculate a path with location index
            Snap fromSnap = index.findClosest(15.15, 20.20, EdgeFilter.ALL_EDGES);
            Snap toSnap = index.findClosest(15.25, 20.21, EdgeFilter.ALL_EDGES);
            QueryGraph queryGraph = QueryGraph.create(graph, fromSnap, toSnap);
            BidirRoutingAlgorithm algo = new CHRoutingAlgorithmFactory(chGraph, queryGraph).createAlgo(new PMap());
            Path path = algo.calcPath(fromSnap.getClosestNode(), toSnap.getClosestNode());
            assert Helper.round(path.getDistance(), -2) == 1000;
        }
    }

}
