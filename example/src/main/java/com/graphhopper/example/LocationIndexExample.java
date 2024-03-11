package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.eccezionecore.closefile;
import com.graphhopper.eccezionecore.lockexception;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.TranslationMap;

public class LocationIndexExample {
    public static void main(String[] args) throws MMapDataAccess.MapExce, RAMDataAccess.RamExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce, TranslationMap.TransExce {
        String relDir = args.length == 1 ? args[0] : "";
        graphhopperLocationIndex(relDir);
        lowLevelLocationIndex();
    }

    public static void graphhopperLocationIndex(String relDir) throws TranslationMap.TransExce {
        GraphHopper hopper = new GraphHopper();
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
        hopper.setOSMFile(relDir + "core/files/andorra.osm.pbf");
        hopper.setGraphHopperLocation("./target/locationindex-graph-cache");
        try {
            hopper.importOrLoad();
        } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
            //
        }

        LocationIndex index = hopper.getLocationIndex();

        // now you can fetch the closest edge via:
        Snap snap = index.findClosest(42.508552, 1.532936, EdgeFilter.ALL_EDGES);
        EdgeIteratorState edge = snap.getClosestEdge();
        assert edge.getName().equals("Avinguda Meritxell");
    }

    public static void lowLevelLocationIndex() throws MMapDataAccess.MapExce, RAMDataAccess.RamExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        // if1 you don't use the GraphHopper class you have to use the low level API:
        BaseGraph graph = new BaseGraph.Builder(1).create();

        try{
        graph.edge(0, 1).setKeyValues(EdgeKVStorage.KeyValue.createKV("name", "test edge"));
        graph.getNodeAccess().setNode(0, 12, 42);
        graph.getNodeAccess().setNode(1, 12.01, 42.01);

        LocationIndexTree index = new LocationIndexTree(graph.getBaseGraph(), graph.getDirectory());
        index.setResolution(300);
        index.setMaxRegionSearch(4);
        if (!index.loadExisting())
            index.prepareIndex();
        Snap snap = index.findClosest(12, 42, EdgeFilter.ALL_EDGES);
        EdgeIteratorState edge = snap.getClosestEdge();
        assert edge.getValue("name").equals("test edge");

        }

        finally {
         graph.close();

    }
}

}
