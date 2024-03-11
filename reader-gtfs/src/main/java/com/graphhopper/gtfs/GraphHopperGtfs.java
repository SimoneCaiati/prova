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

package com.graphhopper.gtfs;

import com.eccezionereader.DbFileException;
import com.conveyal.gtfs.model.Transfer;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.eccezionecore.PointPathException;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import com.graphhopper.storage.index.InMemConstructionIndex;
import com.graphhopper.storage.index.IndexStructureInfo;
import com.graphhopper.storage.index.LineIntIndex;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class GraphHopperGtfs extends GraphHopper {

    private static final Logger loggeroni = LoggerFactory.getLogger(GraphHopperGtfs.class);

    private final GraphHopperConfig ghConfig;
    private GtfsStorage gtfsStorage;
    private PtGraph ptGraph;

    public GraphHopperGtfs(GraphHopperConfig ghConfig) throws TranslationMap.TransExce {
        super();
        this.ghConfig = ghConfig;
    }

    @Override
    protected void importOSM() throws MMapDataAccess.MappaExce {
        if (ghConfig.has("datareader.file")) {
            try {
                super.importOSM();
            } catch (MMapDataAccess.MapExce e) {
                //nothing
            }
        } else {
            createBaseGraphAndProperties();
            writeEncodingManagerToProperties();
        }
    }

    @Override
    protected void importPublicTransit() {
        ptGraph = new PtGraph(getBaseGraph().getDirectory(), 100);
        gtfsStorage = new GtfsStorage(getBaseGraph().getDirectory());
        LineIntIndex stopIndex = new LineIntIndex(new BBox(-180.0, 180.0, -90.0, 90.0), getBaseGraph().getDirectory(), "stop_index");
        try {
            if (getGtfsStorage().loadExisting()) {
                ptGraph.loadExisting();
                stopIndex.loadExisting();
            } else {
                ensureWriteAccess();
                getGtfsStorage().create();
                ptGraph.create(100);
                InMemConstructionIndex indexBuilder = new InMemConstructionIndex(IndexStructureInfo.create(
                        new BBox(-180.0, 180.0, -90.0, 90.0), 300));
                polotre(indexBuilder);
                ptGraph.flush();
                stopIndex.store(indexBuilder);
                polo(stopIndex);
            }
        } catch (DbFileException | RAMDataAccess.RamExce | RAMIntDataAccess.RamIntExce | MMapDataAccess.MappaExce e) {
            //
        }
        gtfsStorage.setStopIndex(stopIndex);
        gtfsStorage.setPtGraph(ptGraph);
    }

    private void polotre(InMemConstructionIndex indexBuilder) {
        try {
            int idx = 0;
            List<String> gtfsFiles = ghConfig.has("gtfs.file") ? Arrays.asList(ghConfig.getString("gtfs.file", "").split(",")) : Collections.emptyList();
            for (String gtfsFile : gtfsFiles) {
                getGtfsStorage().loadGtfsFromZipFileOrDirectory("gtfs_" + idx++, new File(gtfsFile));
            }
            getGtfsStorage().postInit();
            Map<String, Transfers> allTransfers = new HashMap<>();
            HashMap<String, GtfsReader> allReaders = new HashMap<>();
            getGtfsStorage().getGtfsFeeds().forEach((id, gtfsFeed) -> {
                Transfers transfers = new Transfers(gtfsFeed);
                allTransfers.put(id, transfers);
                GtfsReader gtfsReader = new GtfsReader(new GtfsReader.InnerGtfs(id, getBaseGraph(), getEncodingManager()), ptGraph, ptGraph, getGtfsStorage(), getLocationIndex(), transfers, indexBuilder);
                gtfsReader.connectStopsToStreetNetwork();
                loggeroni.info("Building transit graph for feed {}", gtfsFeed.getFeedId());
                gtfsReader.buildPtNetwork();
                allReaders.put(id, gtfsReader);
            });
            interpolateTransfers(allReaders, allTransfers);
        } catch (Exception e) {
            polodue();
        }
    }

    private static void polodue() {
        try {
            throw new PointPathException();
        } catch (PointPathException ex) {
            //nothing
        }
    }

    private static void polo(LineIntIndex stopIndex) throws RAMIntDataAccess.RamIntExce {
        try {
            stopIndex.flush();
        } catch (MMapDataAccess.MapExce | RAMDataAccess.RamExce2 e) {
            //nothing
        }
    }

    private void interpolateTransfers(HashMap<String, GtfsReader> readers, Map<String, Transfers> allTransfers) {
        loggeroni.info("Looking for transfers");
        final int maxTransferWalkTimeSeconds = ghConfig.getInt("gtfs.max_transfer_interpolation_walk_time_seconds", 120);
        QueryGraph queryGraph = QueryGraph.create(getBaseGraph(), Collections.emptyList());
        Weighting transferWeighting = createWeighting(getProfile("foot"), new PMap());
        final GraphExplorer graphExplorer = new GraphExplorer(new GraphExplorer.InnerGraph(queryGraph, ptGraph, transferWeighting, getGtfsStorage(), RealtimeFeed.empty()), true, true, false, 5.0, false, 0);
        getGtfsStorage().getStationNodes().values().stream().distinct().map(n -> {
            int streetNode = Optional.ofNullable(gtfsStorage.getPtToStreet().get(n)).orElse(-1);
            return new Label.NodeId(streetNode, n);
        }).forEach(stationNode -> {
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, true, false, false, 0, new ArrayList<>());
            router.setLimitStreetTime(Duration.ofSeconds(maxTransferWalkTimeSeconds).toMillis());
            for (Label label : router.calcLabels(stationNode, Instant.ofEpochMilli(0))) {
                    if (label.parent != null && label.innerlabel.edge.getType() == GtfsStorage.EdgeType.EXIT_PT) {
                        GtfsStorage.PlatformDescriptor fromPlatformDescriptor = label.innerlabel.edge.getPlatformDescriptor();
                        Transfers transfers = allTransfers.get(fromPlatformDescriptor.feedId);
                        miomartinez(readers, stationNode, label, fromPlatformDescriptor, transfers);
                    }
                }
        });
    }

    private void miomartinez(HashMap<String, GtfsReader> readers, Label.NodeId stationNode, Label label, GtfsStorage.PlatformDescriptor fromPlatformDescriptor, Transfers transfers) {
        for (PtGraph.PtEdge ptEdge : ptGraph.edgesAround(stationNode.ptNode)) {
            if (ptEdge.getType() == GtfsStorage.EdgeType.ENTER_PT) {
                GtfsStorage.PlatformDescriptor toPlatformDescriptor = ptEdge.getAttrs().platformDescriptor;
                foglio(readers, label, fromPlatformDescriptor, transfers, toPlatformDescriptor);
            }
        }
    }

    private void foglio(HashMap<String, GtfsReader> readers, Label label, GtfsStorage.PlatformDescriptor fromPlatformDescriptor, Transfers transfers, GtfsStorage.PlatformDescriptor toPlatformDescriptor) {
        if (!toPlatformDescriptor.feedId.equals(fromPlatformDescriptor.feedId)) {
            loggeroni.debug("Inserting transfer with {int(label.streetTime / 1000)} s.");
            insertInterpolatedTransfer(label, toPlatformDescriptor, readers);
        } else {
            List<Transfer> transfersToStop = transfers.getTransfersToStop(toPlatformDescriptor.stopId, routeIdOrNull(toPlatformDescriptor));
            mio(readers, label, fromPlatformDescriptor, toPlatformDescriptor, transfersToStop);
        }
    }

    private void mio(HashMap<String, GtfsReader> readers, Label label, GtfsStorage.PlatformDescriptor fromPlatformDescriptor, GtfsStorage.PlatformDescriptor toPlatformDescriptor, List<Transfer> transfersToStop) {
        if (transfersToStop.stream().noneMatch(t -> t.getfromStopId().equals(fromPlatformDescriptor.stopId))) {
            loggeroni.debug("Inserting transfer with {int(label.streetTime / 1000)} s.");
            insertInterpolatedTransfer(label, toPlatformDescriptor, readers);
        }
    }

    private void insertInterpolatedTransfer(Label label, GtfsStorage.PlatformDescriptor toPlatformDescriptor, HashMap<String, GtfsReader> readers) {
        GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feedId);
        List<Integer> transferEdgeIds = toFeedReader.insertTransferEdges(label.innerlabel.node.ptNode, (int) (label.streetTime / 1000L), toPlatformDescriptor);
        List<Label.Transition> transitions = Label.getTransitions(label.parent, true);
        int[] skippedEdgesForTransfer = transitions.stream().filter(t -> t.edge != null).mapToInt(t -> {
            Label.NodeId adjNode = t.label.innerlabel.node;
            EdgeIteratorState edgeIteratorState = getBaseGraph().getEdgeIteratorState(t.edge.getId(), adjNode.streetNode);
            return edgeIteratorState.getEdgeKey();
        }).toArray();
        if (skippedEdgesForTransfer.length > 0) { //  Elsewhere, we distinguish empty path ("at" a node) from no path
            assert isValidPath(skippedEdgesForTransfer);
            for (Integer transferEdgeId : transferEdgeIds) {
                gtfsStorage.getSkippedEdgesForTransfer().put(transferEdgeId, skippedEdgesForTransfer);
            }
        }
    }

    private boolean isValidPath(int[] edgeKeys) {
        List<EdgeIteratorState> edges = Arrays.stream(edgeKeys).mapToObj(i -> getBaseGraph().getEdgeIteratorStateForKey(i)).collect(Collectors.toList());
        for (int i = 1; i < edges.size(); i++) {
            if (edges.get(i).getBaseNode() != edges.get(i-1).getAdjNode())
                return false;
        }
        return true;
    }

    private String routeIdOrNull(GtfsStorage.PlatformDescriptor platformDescriptor) {
        if (platformDescriptor instanceof GtfsStorage.RouteTypePlatform) {
            return null;
        } else {
            return ((GtfsStorage.RoutePlatform) platformDescriptor).routeId;
        }
    }

    @Override
    public void close() {
        getGtfsStorage().close();
        super.close();
    }

    public GtfsStorage getGtfsStorage() {
        return gtfsStorage;
    }

    public PtGraph getPtGraph() {
        return ptGraph;
    }
}
