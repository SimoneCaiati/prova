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

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.eccezionecore.threadException;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.storage.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointAccess;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;

import static com.graphhopper.reader.osm.OSMNodeData.*;
import static com.graphhopper.util.Helper.nf;
import static java.util.Collections.emptyMap;

/**
 * This class parses a given OSM file and splits OSM ways into 'segments' at all intersections (or 'junctions').
 * Intersections can be either crossings of different OSM ways or duplicate appearances of the same node within one
 * way (when the way contains a loop). Furthermore, this class creates artificial segments at certain nodes. This class
 * also provides several hooks/callbacks to customize the processing of nodes, ways and relations.
 * <p>
 * The OSM file is read twice. The first time we ignore OSM nodes and only determine the OSM node IDs at which accepted
 * ways are intersecting. During the second pass we split the OSM ways at intersections, introduce the artificial
 * segments and pass the way information along with the corresponding nodes to a given callback.
 * <p>
 * We assume a strict order of the OSM file: nodes, ways, then relations.
 * <p>
 * The main difficulty is that the OSM ID range is very large (64bit integers) and to be able to provide the full
 * node information for each segment we have to efficiently store the node data temporarily. This is addressed by
 * {@link OSMNodeData}.
 */
public class WaySegmentParser {
    public static class InnerWay{
        public final ElevationProvider eleProvider;
        public final Predicate<ReaderWay> wayFilter;
        public final Predicate<ReaderNode> splitNodeFilter;
        public final WaySegmentParser.WayPreprocessor wayPreprocessor;
        public final Consumer<ReaderRelation> relationPreprocessor;

        public InnerWay(ElevationProvider eleProvider, Predicate<ReaderWay> wayFilter, Predicate<ReaderNode> splitNodeFilter, WayPreprocessor wayPreprocessor, Consumer<ReaderRelation> relationPreprocessor) {
            this.eleProvider = eleProvider;
            this.wayFilter = wayFilter;
            this.splitNodeFilter = splitNodeFilter;
            this.wayPreprocessor = wayPreprocessor;
            this.relationPreprocessor = relationPreprocessor;
        }
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(WaySegmentParser.class);

    private final RelationProcessor relationProcessor;
    private final EdgeHandler edgeHandler;
    private final int workerThreads;
    private final InnerWay innerway;
    private final OSMNodeData nodeData;
    private Date timestamp;

    private WaySegmentParser(PointAccess nodeAccess, Directory directory, InnerWay innerway, RelationProcessor relationProcessor,
                             EdgeHandler edgeHandler, int workerThreads) throws MMapDataAccess.MappaExce {
        this.innerway=innerway;
        this.relationProcessor = relationProcessor;
        this.edgeHandler = edgeHandler;
        this.workerThreads = workerThreads;

        this.nodeData = new OSMNodeData(nodeAccess, directory);
    }

    /**
     * @param osmFile the OSM file to parse, supported formats include .osm.xml, .osm.gz and .xml.pbf
     */
    public void readOSM(File osmFile) throws MMapDataAccess.MapExce {
        if (nodeData.getNodeCount() > 0)
            throw new IllegalStateException("You can only run way segment parser once");

        LOGGER.info("Start reading OSM file: '{}'", osmFile);

        LOGGER.info("pass1 - start");
        StopWatch sw1 = StopWatch.started();
        readOSM(osmFile, new Pass1Handler());
        LOGGER.info("pass1 - finished, took: {}", sw1.stop().getTimeString());

        long nodes = nodeData.getNodeCount();

        LOGGER.info("Creating graph. Node count (pillar+tower): {}, {}", nodes, Helper.getMemInfo());


        LOGGER.info("pass2 - start");
        StopWatch sw2 = new StopWatch().start();
        readOSM(osmFile, new Pass2Handler());
        LOGGER.info("pass2 - finished, took: {}", sw2.stop().getTimeString());

        nodeData.release();

        LOGGER.info("Finished reading OSM file. pass1: {}s, pass2: {}s, total: {}s",
                (int) sw1.getSeconds(), (int) sw2.getSeconds(), (int) (sw1.getSeconds() + sw2.getSeconds()));

    }

    /**
     * @return the timestamp read from the OSM file, or null if nothing was read yet
     */
    public Date getTimeStamp() {
        return timestamp;
    }

    private class Pass1Handler implements ReaderElementHandler {
        private boolean handledWays;
        private boolean handledRelations;
        private long wayCounter = 0;
        private long acceptedWays = 0;
        private long relationsCounter = 0;

        @Override
        public void handleWay(ReaderWay way) {
            if (!handledWays) {
                LOGGER.info("pass1 - start reading OSM ways");
                handledWays = true;
            }
            if (handledRelations)
                throw new IllegalStateException("OSM way elements must be located before relation elements in OSM file");

            process();


            if (!innerway.wayFilter.test(way))
                return;
            acceptedWays++;

            for (LongCursor node : way.getNodes()) {
                final boolean isEnd = node.index == 0 || node.index == way.getNodes().size() - 1;
                final long osmId = node.value;
                nodeData.setOrUpdateNodeType(osmId,
                        isEnd ? END_NODE : INTERMEDIATE_NODE,
                        // connection nodes are those where (only) two OSM ways are connected at their ends
                        prev -> prev == END_NODE && isEnd ? CONNECTION_NODE : JUNCTION_NODE);
            }
        }

        private void process() {
            if (++wayCounter % 10_000_000 == 0) {
                LOGGER.info("pass1 - processed ways: {}, accepted ways: {}, way nodes: {}, {}",
                        (wayCounter>acceptedWays ? nf(wayCounter):wayCounter),
                        (wayCounter>acceptedWays ? nf(acceptedWays):acceptedWays),
                        (wayCounter>acceptedWays ? nf(nodeData.getNodeCount()):nodeData.getNodeCount()), Helper.getMemInfo());
            }
        }

        @Override
        public void handleRelation(ReaderRelation relation) {
            if (!handledRelations) {
                LOGGER.info("pass1 - start reading OSM relations");
                handledRelations = true;
            }

            if (++relationsCounter % 1_000_000 == 0) {
                LOGGER.info("pass1 - processed relations: {}, {}",
                        (relationsCounter>0 ? nf(relationsCounter):relationsCounter), Helper.getMemInfo());
            }


            innerway.relationPreprocessor.accept(relation);
        }

        @Override
        public void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
            timestamp = Helper.createFormatter().parse(fileHeader.getTag("timestamp"));
        }

        @Override
        public void onFinish() {
            LOGGER.info("pass1 - finished, processed ways: {}, accepted ways: {}, way nodes: {}, relations: {}, {}",
                    (wayCounter>acceptedWays ? nf(wayCounter):wayCounter),
                    (wayCounter>acceptedWays ? nf(acceptedWays):acceptedWays),
                    (wayCounter>acceptedWays ? nf(nodeData.getNodeCount()):nodeData.getNodeCount()),
                    (wayCounter>acceptedWays ? nf(relationsCounter):relationsCounter), Helper.getMemInfo());
        }

    }

    private class Pass2Handler implements ReaderElementHandler {
        private boolean handledNodes;
        private boolean handledWays;
        private boolean handledRelations;
        private long nodeCounter = 0;
        private long acceptedNodes = 0;
        private long ignoredSplitNodes = 0;
        private long wayCounter = 0;

        @Override
        public void handleNode(ReaderNode node) {
            if (!handledNodes) {
                LOGGER.info("pass2 - start reading OSM nodes");
                handledNodes = true;
            }
            if (handledWays)
                throw new IllegalStateException("OSM node elements must be located before way elements in OSM file");
            if (handledRelations)
                throw new IllegalStateException("OSM node elements must be located before relation elements in OSM file");

            if (++nodeCounter % 10_000_000 == 0) {
                LOGGER.info("pass2 - processed nodes: {}, accepted nodes: {}, {}",
                        (nodeCounter>acceptedNodes ? nf(nodeCounter):nodeCounter),
                        (nodeCounter>acceptedNodes ? nf(acceptedNodes):acceptedNodes)
                        , Helper.getMemInfo());
            }



            int nodeType = nodeData.addCoordinatesIfMapped(node.getId(), node.getLat(), node.getLon(), () -> {
                try {
                    return innerway.eleProvider.getEle(node);
                } catch (threadException | MMapDataAccess.MapExce | RAMDataAccess.RamExce2 |
                         RAMIntDataAccess.RamIntExce | MMapDataAccess.MappaExce e) {
                    //
                }
                return 0;
            });
            if (nodeType == EMPTY_NODE)
                return;

            acceptedNodes++;

            // we keep node tags for barrier nodes
            if (innerway.splitNodeFilter.test(node)) {
                if (nodeType == JUNCTION_NODE) {
                    LOGGER.debug("OSM node {} at {},{} is a barrier node at a junction. The barrier will be ignored",
                            node.getId(), Helper.round(node.getLat(), 7), Helper.round(node.getLon(), 7));
                    ignoredSplitNodes++;
                } else
                    nodeData.setTags(node);
            }
        }

        @Override
        public void handleWay(ReaderWay way) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
            if (!handledWays) {
                LOGGER.info("pass2 - start reading OSM ways");
                handledWays = true;
            }
            if (handledRelations)
                throw new IllegalStateException("OSM way elements must be located before relation elements in OSM file");

            if (++wayCounter % 10_000_000 == 0) {
                LOGGER.info("pass2 - processed ways: {}, {}",
                        (wayCounter>0 ? nf(wayCounter):wayCounter), Helper.getMemInfo());
            }


            if (!innerway.wayFilter.test(way))
                return;
            List<SegmentNode> segment = new ArrayList<>(way.getNodes().size());
            for (LongCursor node : way.getNodes())
                segment.add(new SegmentNode(node.value, nodeData.getId(node.value)));
            innerway.wayPreprocessor.preprocessWay(way, osmNodeId -> nodeData.getCoordinates(nodeData.getId(osmNodeId)));
            splitWayAtJunctionsAndEmptySections(segment, way);
        }

        private void splitWayAtJunctionsAndEmptySections(List<SegmentNode> fullSegment, ReaderWay way) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
            List<SegmentNode> segment = new ArrayList<>();
            for (SegmentNode node : fullSegment) {
                if (!isNodeId(node.id)) {
                    // this node exists in ways, but not in nodes. we ignore it, but we split the way when we encounter
                    // such a missing node. for example an OSM way might lead out of an area where nodes are available and
                    // back into it. we do not want to connect the exit/entry points using a straight line. this usually
                    // should only happen for OSM extracts
                    if (segment.size() > 1) {
                        splitLoopSegments(segment, way);
                        segment = new ArrayList<>();
                    }
                } else if (isTowerNode(node.id)) {
                    if (!segment.isEmpty()) {
                        segment.add(node);
                        splitLoopSegments(segment, way);
                        segment = new ArrayList<>();
                    }
                    segment.add(node);
                } else {
                    segment.add(node);
                }
            }
            // the last segment might end at the end of the way
            if (segment.size() > 1)
                splitLoopSegments(segment, way);
        }

        private void splitLoopSegments(List<SegmentNode> segment, ReaderWay way) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
            if (segment.size() < 2)
                throw new IllegalStateException("Segment size must be >= 2, but was: " + segment.size());

            boolean isLoop = segment.get(0).osmNodeId == segment.get(segment.size() - 1).osmNodeId;
            if (segment.size() == 2 && isLoop) {
                LOGGER.warn("Loop in OSM way: {}, will be ignored, duplicate node: {}", way.getId(), segment.get(0).osmNodeId);
            } else if (isLoop) {
                // split into two segments
                splitSegmentAtSplitNodes(segment.subList(0, segment.size() - 1), way);
                splitSegmentAtSplitNodes(segment.subList(segment.size() - 2, segment.size()), way);
            } else {
                splitSegmentAtSplitNodes(segment, way);
            }
        }

        private void splitSegmentAtSplitNodes(List<SegmentNode> parentSegment, ReaderWay way) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
            List<SegmentNode> segment = new ArrayList<>();
            for (int i = 0; i < parentSegment.size(); i++) {
                SegmentNode node = parentSegment.get(i);
                Map<String, Object> nodeTags = nodeData.getTags(node.osmNodeId);
                // so far we only consider node tags of split nodes, so if there are node tags we split the node
                if (!nodeTags.isEmpty()) {
                    // this node is a barrier. we will copy it and add an extra edge
                    SegmentNode barrierFrom = node;
                    SegmentNode barrierTo = nodeData.addCopyOfNode(node);
                    if (i == parentSegment.size() - 1) {
                        // make sure the barrier node is always on the inside of the segment
                        SegmentNode tmp = barrierFrom;
                        barrierFrom = barrierTo;
                        barrierTo = tmp;
                    }
                    if (!segment.isEmpty()) {
                        segment.add(barrierFrom);
                        handleSegment(segment, way, emptyMap());
                        segment = new ArrayList<>();
                    }
                    segment.add(barrierFrom);
                    segment.add(barrierTo);
                    handleSegment(segment, way, nodeTags);
                    segment = new ArrayList<>();
                    segment.add(barrierTo);

                    // ignore this barrier node from now. for example a barrier can be connecting two ways (appear in both
                    // ways) and we only want to add a barrier edge once (but we want to add one).
                    nodeData.removeTags(node.osmNodeId);
                } else {
                    segment.add(node);
                }
            }
            if (segment.size() > 1)
                handleSegment(segment, way, emptyMap());
        }

        void handleSegment(List<SegmentNode> segment, ReaderWay way, Map<String, Object> nodeTags) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
            final PointList pointList = new PointList(segment.size(), nodeData.is3D());
            int from = -1;
            int to = -1;
            for (int i = 0; i < segment.size(); i++) {
                SegmentNode node = segment.get(i);
                int id = node.id;
                if (!isNodeId(id))
                    throw new IllegalStateException("Invalid id for node: " + node.osmNodeId + " when handling segment " + segment + " for way: " + way.getId());
                if (isPillarNode(id) && (i == 0 || i == segment.size() - 1)) {
                    id = nodeData.convertPillarToTowerNode(id, node.osmNodeId);
                    node.id = id;
                }

                if (i == 0)
                    from = nodeData.idToTowerNode(id);
                else if (i == segment.size() - 1)
                    to = nodeData.idToTowerNode(id);
                else if (isTowerNode(id))
                    throw new IllegalStateException("Tower nodes should only appear at the end of segments, way: " + way.getId());
                nodeData.addCoordinatesToPointList(id, pointList);
            }
            if (from < 0 || to < 0)
                throw new IllegalStateException("The first and last nodes of a segment must be tower nodes, way: " + way.getId());
            edgeHandler.handleEdge(from, to, pointList, way, nodeTags);
        }

        @Override
        public void handleRelation(ReaderRelation relation) {
            if (!handledRelations) {
                LOGGER.info("pass2 - start reading OSM relations");
                handledRelations = true;
            }

            relationProcessor.processRelation(relation, this::getInternalNodeIdOfOSMNode);
        }

        @Override
        public void onFinish() {
            LOGGER.info("pass2 - finished, processed ways: {}, way nodes: {}, with tags: {}, ignored barriers at junctions: {}",
                    (wayCounter>acceptedNodes ? nf(wayCounter) : wayCounter),
                    (wayCounter>acceptedNodes  ? nf(acceptedNodes) : acceptedNodes),
                    (wayCounter>acceptedNodes  ? nf(nodeData.getTaggedNodeCount()) : nodeData.getTaggedNodeCount()),
                    (wayCounter>acceptedNodes  ? nf(ignoredSplitNodes) : ignoredSplitNodes));

        }


        public int getInternalNodeIdOfOSMNode(long nodeOsmId) {
            int id = nodeData.getId(nodeOsmId);
            if (isTowerNode(id))
                return -id - 3;
            return -1;
        }
    }

    private void readOSM(File file, ReaderElementHandler handler) throws MMapDataAccess.MapExce {
        try (OSMInput osmInput = new OSMInputFile(file).setWorkerThreads(workerThreads).open()) {
            ReaderElement elem;
            while ((elem = osmInput.getNext()) != null)
                handler.handleElement(elem);
            handler.onFinish();
            if (osmInput.getUnprocessedElements() > 0)
                throw new IllegalStateException("There were some remaining elements in the reader queue " + osmInput.getUnprocessedElements());
        } catch (Exception e) {
            throw new MMapDataAccess.MapExce("Could not parse OSM file: " + file.getAbsolutePath(), e);
        }
    }



    public static class Builder {
        private final PointAccess nodeAccess;
        private Directory directory = new RAMDirectory();
        private ElevationProvider elevationProvider = ElevationProvider.NOOP;
        private Predicate<ReaderWay> wayFilter = way -> true;
        private Predicate<ReaderNode> splitNodeFilter = node -> false;
        private WayPreprocessor wayPreprocessor = (way, supplier) -> {
        };
        private Consumer<ReaderRelation> relationPreprocessor = relation -> {
        };
        private RelationProcessor relationProcessor = (relation, map) -> {
        };
        private static final Logger logger = LoggerFactory.getLogger(Builder.class);
        private EdgeHandler edgeHandler = (from, to, pointList, way, nodeTags) ->
            logger.info("edge {}->{} ({} points)", from, to, pointList.size());

            private int workerThreads = 2;

        /**
         * @param nodeAccess used to store tower node coordinates while parsing the ways
         */
        public Builder(PointAccess nodeAccess) {
            // instead of requiring a PointAccess here we could also just use some temporary in-memory storage by default
            this.nodeAccess = nodeAccess;
        }

        /**
         * @param directory the directory to be used to store temporary data
         */
        public Builder setDirectory(Directory directory) {
            this.directory = directory;
            return this;
        }

        /**
         * @param elevationProvider used to determine the elevation of an OSM node
         */
        public Builder setElevationProvider(ElevationProvider elevationProvider) {
            this.elevationProvider = elevationProvider;
            return this;
        }

        /**
         * @param wayFilter return true for OSM ways that should be considered and false otherwise
         */
        public Builder setWayFilter(Predicate<ReaderWay> wayFilter) {
            this.wayFilter = wayFilter;
            return this;
        }

        /**
         * @param splitNodeFilter return true if the given OSM node should be duplicated to create an artificial edge
         */
        public Builder setSplitNodeFilter(Predicate<ReaderNode> splitNodeFilter) {
            this.splitNodeFilter = splitNodeFilter;
            return this;
        }

        /**
         * @param wayPreprocessor callback function that is called for each accepted OSM way during the second pass
         */
        public Builder setWayPreprocessor(WayPreprocessor wayPreprocessor) {
            this.wayPreprocessor = wayPreprocessor;
            return this;
        }

        /**
         * @param relationPreprocessor callback function that receives OSM relations during the first pass
         */
        public Builder setRelationPreprocessor(Consumer<ReaderRelation> relationPreprocessor) {
            this.relationPreprocessor = relationPreprocessor;
            return this;
        }

        /**
         * @param relationProcessor callback function that receives OSM relations during the second pass
         */
        public Builder setRelationProcessor(RelationProcessor relationProcessor) {
            this.relationProcessor = relationProcessor;
            return this;
        }

        /**
         * @param edgeHandler callback function that is called for each edge (way segment)
         */
        public Builder setEdgeHandler(EdgeHandler edgeHandler) {
            this.edgeHandler = edgeHandler;
            return this;
        }

        /**
         * @param workerThreads the number of threads used for the low level reading of the OSM file
         */
        public Builder setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        public WaySegmentParser build() throws MMapDataAccess.MappaExce {
            return new WaySegmentParser(
                    nodeAccess, directory, new InnerWay(elevationProvider, wayFilter, splitNodeFilter, wayPreprocessor, relationPreprocessor), relationProcessor,
                    edgeHandler, workerThreads
            );
        }
    }

    private interface ReaderElementHandler {
        default void handleElement(ReaderElement elem) throws ParseException, threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
            switch (elem.getType()) {
                case NODE:
                    handleNode((ReaderNode) elem);
                    break;
                case WAY:
                    handleWay((ReaderWay) elem);
                    break;
                case RELATION:
                    handleRelation((ReaderRelation) elem);
                    break;
                case FILEHEADER:
                    handleFileHeader((OSMFileHeader) elem);
                    break;
                default:
                    throw new IllegalStateException("Unknown reader element type: " + elem.getType());
            }
        }

        default void handleNode(ReaderNode node) {
        }

        default void handleWay(ReaderWay way) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        }

        default void handleRelation(ReaderRelation relation) {
        }

        default void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
        }

        default void onFinish() {
        }
    }

    public interface EdgeHandler {
        void handleEdge(int from, int to, PointList pointList, ReaderWay way, Map<String, Object> nodeTags) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce;
    }

    public interface RelationProcessor {
        void processRelation(ReaderRelation relation, LongToIntFunction getNodeIdForOSMNodeId);
    }

    public interface WayPreprocessor {
        /**
         * @param coordinateSupplier maps an OSM node ID (as it can be obtained by way.getNodes()) to the coordinates
         *                           of this node. if1 elevation is disabled it will be NaN. Returns null if no such OSM
         *                           node exists.
         */
        void preprocessWay(ReaderWay way, CoordinateSupplier coordinateSupplier);
    }

    public interface CoordinateSupplier {
        GHPoint3D getCoordinate(long osmNodeId);
    }
}
