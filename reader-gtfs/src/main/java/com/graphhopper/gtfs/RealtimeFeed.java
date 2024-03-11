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

import com.conveyal.gtfs.model.Agency;
import com.eccezionereader.DbFileException;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntLongHashMap;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.eccezionecore.PointPathException;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static java.time.temporal.ChronoUnit.DAYS;

public class RealtimeFeed {
    private static final Logger logger = LoggerFactory.getLogger(RealtimeFeed.class);
    private final IntHashSet blockedEdges;
    private final IntLongHashMap delaysForBoardEdges;
    private final IntLongHashMap delaysForAlightEdges;
    private final List<PtGraph.PtEdge> additionalEdges;
    public final Map<String, GtfsRealtime.FeedMessage> feedMessages;

    private RealtimeFeed(Map<String, GtfsRealtime.FeedMessage> feedMessages, IntHashSet blockedEdges,
                         IntLongHashMap delaysForBoardEdges, IntLongHashMap delaysForAlightEdges, List<PtGraph.PtEdge> additionalEdges) {
        this.feedMessages = feedMessages;
        this.blockedEdges = blockedEdges;
        this.delaysForBoardEdges = delaysForBoardEdges;
        this.delaysForAlightEdges = delaysForAlightEdges;
        this.additionalEdges = additionalEdges;
    }

    public static RealtimeFeed empty() {
        return new RealtimeFeed(Collections.emptyMap(), new IntHashSet(), new IntLongHashMap(), new IntLongHashMap(), Collections.emptyList());
    }

    public static RealtimeFeed fromProtobuf(BaseGraph baseGraph, EncodingManager encodingManager, GtfsStorage staticGtfs, Map<String, Transfers> transfers, Map<String, GtfsRealtime.FeedMessage> feedMessages) {
        final IntHashSet blockedEdges = new IntHashSet();
        final IntLongHashMap delaysForBoardEdges = new IntLongHashMap();
        final IntLongHashMap delaysForAlightEdges = new IntLongHashMap();
        final LinkedList<PtGraph.PtEdge> additionalEdges = new LinkedList<>();
        final GtfsReader.PtGraphOut overlayGraph = new GtfsReader.PtGraphOut() {
            int nextEdge = staticGtfs.getPtGraph().getEdgeCount();
            int nextNode = staticGtfs.getPtGraph().getNodeCount();

            @Override
            public int createEdge(int src, int dest, PtEdgeAttributes attrs) {
                int edgeId = nextEdge++;
                additionalEdges.add(new PtGraph.PtEdge(edgeId, src, dest, attrs));
                return edgeId;
            }

            @Override
            public int createNode() {
                return nextNode++;
            }

        };

        feedMessages.forEach((feedKey, feedMessage) -> {
            GTFSFeed feed = staticGtfs.getGtfsFeeds().get(feedKey);
            ZoneId timezone = ZoneId.of(feed.agency.values().stream().findFirst().get().getagencyTimezone());
            PtGraph ptGraphNodesAndEdges = staticGtfs.getPtGraph();
            final GtfsReader gtfsReader = new GtfsReader(new GtfsReader.InnerGtfs(feedKey, baseGraph, encodingManager), ptGraphNodesAndEdges, overlayGraph, staticGtfs, null, transfers.get(feedKey), null);
            Instant timestamp = Instant.ofEpochSecond(feedMessage.getHeader().getTimestamp());
            LocalDate dateToChange = timestamp.atZone(timezone).toLocalDate();
            BitSet validOnDay = new BitSet();
            LocalDate startDate = feed.getStartDate();
            validOnDay.set((int) DAYS.between(startDate, dateToChange));
            feedMessage.getEntityList().stream()
                    .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED)
                    .forEach(tripUpdate -> {
                        Collection<Frequency> frequencies = feed.getFrequencies(tripUpdate.getTrip().getTripId());
                        int timeOffset = (tripUpdate.getTrip().hasStartTime() && !frequencies.isEmpty()) ? LocalTime.parse(tripUpdate.getTrip().getStartTime()).toSecondOfDay() : 0;
                        final int[] boardEdges = findBoardEdgesForTrip(staticGtfs, feedKey, feed, tripUpdate);
                        final int[] leaveEdges = findLeaveEdgesForTrip(staticGtfs, feedKey, feed, tripUpdate);
                        if (methodRealFeed2(tripUpdate, boardEdges, leaveEdges)) return;
                        tripUpdate.getStopTimeUpdateList().stream()
                                .filter(stopTimeUpdate -> stopTimeUpdate.getScheduleRelationship() == SKIPPED)
                                .mapToInt(GtfsRealtime.TripUpdate.StopTimeUpdate::getStopSequence)
                                .forEach(skippedStopSequenceNumber -> {
                                    blockedEdges.add(boardEdges[skippedStopSequenceNumber]);
                                    blockedEdges.add(leaveEdges[skippedStopSequenceNumber]);
                                });
                        GtfsReader.TripWithStopTimes tripWithStopTimes = null;
                        tripWithStopTimes = methodFeed1(feed, tripUpdate, tripWithStopTimes);
                        tripWithStopTimes.stopTimes.forEach(stopTime -> {
                            if (stopTime.getStopSequence() > leaveEdges.length - 1) {
                                logger.warn("Stop sequence number too high {} vs {}", stopTime.getStopSequence(), leaveEdges.length);
                                return;
                            }
                            final StopTime originalStopTime = feed.stopTims.get(new Fun.Tuple2<>(tripUpdate.getTrip().getTripId(), stopTime.getStopSequence()));
                            int arrivalDelay = stopTime.getArrivalTime() - originalStopTime.getArrivalTime();
                            delaysForAlightEdges.put(leaveEdges[stopTime.getStopSequence()], arrivalDelay * (long)1000);
                            int departureDelay = stopTime.getDepartureTime() - originalStopTime.getDepartureTime();
                            if (departureDelay > 0) {
                                int boardEdge = boardEdges[stopTime.getStopSequence()];
                                int departureNode = ptGraphNodesAndEdges.edge(boardEdge).getAdjNode();
                                int delayedBoardEdge = gtfsReader.addDelayedBoardEdge(timezone, tripUpdate.getTrip(), stopTime.getStopSequence(), stopTime.getDepartureTime() + timeOffset, departureNode, validOnDay);
                                delaysForBoardEdges.put(delayedBoardEdge, departureDelay * (long)1000);
                            }
                        });
                    });
            feedMessage.getEntityList().stream()
                    .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
                    .forEach(tripUpdate -> {
                        Trip trip = new Trip();
                        trip.trip_id = tripUpdate.getTrip().getTripId();
                        trip.route_id = tripUpdate.getTrip().getRouteId();
                        final List<StopTime> stopTimes = tripUpdate.getStopTimeUpdateList().stream()
                                .map(stopTimeUpdate -> {
                                    final StopTime stopTime = new StopTime();
                                    stopTime.setStopSequence(stopTimeUpdate.getStopSequence());
                                    stopTime.setStopId(stopTimeUpdate.getStopId());
                                    stopTime.trip_id = trip.trip_id;
                                    final ZonedDateTime arrivatime = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                                    stopTime.setArrivalTime((int) Duration.between(arrivatime.truncatedTo(ChronoUnit.DAYS), arrivatime).getSeconds());
                                    final ZonedDateTime depatime = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                                    stopTime.setDepartureTime((int) Duration.between(depatime.truncatedTo(ChronoUnit.DAYS), depatime).getSeconds());
                                    return stopTime;
                                })
                                .collect(Collectors.toList());
                        GtfsReader.TripWithStopTimes tripWithStopTimes = new GtfsReader.TripWithStopTimes(trip, stopTimes, validOnDay, Collections.emptySet(), Collections.emptySet());
                        gtfsReader.addTrip(timezone, 0, new ArrayList<>(), tripWithStopTimes, tripUpdate.getTrip());
                    });
            gtfsReader.wireUpAdditionalDeparturesAndArrivals(timezone);
        });

        return new RealtimeFeed(feedMessages, blockedEdges, delaysForBoardEdges, delaysForAlightEdges, additionalEdges);
    }

    private static GtfsReader.TripWithStopTimes methodFeed1(GTFSFeed feed, GtfsRealtime.TripUpdate tripUpdate, GtfsReader.TripWithStopTimes tripWithStopTimes) {
        try {
            tripWithStopTimes = toTripWithStopTimes(feed, tripUpdate);
        } catch (FeedExce | FeedExce2 e) {
            //
        }
        return tripWithStopTimes;
    }

    private static boolean methodRealFeed2(GtfsRealtime.TripUpdate tripUpdate, int[] boardEdges, int[] leaveEdges) {
        if (boardEdges == null || leaveEdges == null) {
            logger.warn("Trip not found: {}", tripUpdate.getTrip());
            return true;
        }
        return false;
    }

    private static int[] findLeaveEdgesForTrip(GtfsStorage staticGtfs, String feedKey, GTFSFeed feed, GtfsRealtime.TripUpdate tripUpdate) {
        Trip trip = feed.trips.get(tripUpdate.getTrip().getTripId());
        StopTime next = feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().next();
        int station = staticGtfs.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(feedKey, next.getStopId()));
        Optional<PtGraph.PtEdge> firstBoarding = StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(station).spliterator(), false)
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(e.getAdjNode()).spliterator(), false))
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(e.getAdjNode()).spliterator(), false))
                .filter(e -> e.getType() == GtfsStorage.EdgeType.ALIGHT)
                .filter(e -> normalize(e.getAttrs().tripDescriptor).equals(tripUpdate.getTrip()))
                .findAny();
        if (firstBoarding.isPresent()) {
            int n = firstBoarding.get().getAdjNode();
            Stream<PtGraph.PtEdge> boardEdges = evenIndexed(nodes(hopDwellChain(staticGtfs, n)))
                    .mapToObj(e -> alightForBaseNode(staticGtfs, e));
            return collectWithPadding(boardEdges);
        } else {
            return new int[0]; // or throw an exception, or return some other value as appropriate
        }
    }


    private static int[] findBoardEdgesForTrip(GtfsStorage staticGtfs, String feedKey, GTFSFeed feed, GtfsRealtime.TripUpdate tripUpdate) {
        Trip trip = feed.trips.get(tripUpdate.getTrip().getTripId());
        StopTime next = feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().next();
        int station = staticGtfs.getStationNodes().get(new GtfsStorage.FeedIdWithStopId(feedKey, next.getStopId()));
        Optional<PtGraph.PtEdge> firstBoarding = StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(station).spliterator(), false)
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(e.getAdjNode()).spliterator(), false))
                .flatMap(e -> StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(e.getAdjNode()).spliterator(), false))
                .filter(e -> e.getType() == GtfsStorage.EdgeType.BOARD)
                .filter(e -> normalize(e.getAttrs().tripDescriptor).equals(tripUpdate.getTrip()))
                .findAny();
        if (firstBoarding.isPresent()) {
            int n = firstBoarding.get().getAdjNode();
            Stream<PtGraph.PtEdge> boardEdges = evenIndexed(nodes(hopDwellChain(staticGtfs, n)))
                .mapToObj(e -> boardForAdjNode(staticGtfs, e));
            return collectWithPadding(boardEdges);
        } else {
            // gestione del caso in cui l'Optional è vuoto
            throw new NoSuchElementException("Nessun elemento trovato per la ricerca di board edges");
        }
    }
    

    private static int[] collectWithPadding(Stream<PtGraph.PtEdge> boardEdges) {
        IntArrayList result = new IntArrayList();
        boardEdges.forEach(boardEdge -> {
            while (result.size() < boardEdge.getAttrs().stopSequence) {
                result.add(-1); // Padding, so that index == stop_sequence
            }
            result.add(boardEdge.getId());
        });
        return result.toArray();
    }

    private static PtGraph.PtEdge alightForBaseNode(GtfsStorage staticGtfs, int n) {
        Optional<PtGraph.PtEdge> alightEdge = StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(n).spliterator(), false)
                .filter(e -> e.getType() == GtfsStorage.EdgeType.ALIGHT)
                .findAny();
        if (alightEdge.isPresent()) {
            return alightEdge.get();
        } else {
            // gestione del caso in cui l'Optional è vuoto
            throw new NoSuchElementException("Nessun elemento trovato per la ricerca di alight edge");
        }
    }
    

    private static PtGraph.PtEdge boardForAdjNode(GtfsStorage staticGtfs, int n) {
        Optional<PtGraph.PtEdge> optionalEdge = StreamSupport.stream(staticGtfs.getPtGraph().backEdgesAround(n).spliterator(), false)
                .filter(e -> e.getType() == GtfsStorage.EdgeType.BOARD)
                .findAny();
        if (optionalEdge.isPresent()) {
            return optionalEdge.get();
        } else {
            // gestione del caso in cui l'Optional è vuoto
            throw new NoSuchElementException("Nessun elemento trovato per il nodo " + n);
        }
    }
    

    private static IntStream evenIndexed(IntStream nodes) {
        int[] ints = nodes.toArray();
        IntStream.Builder builder = IntStream.builder();
        for (int i = 0; i < ints.length; i++) {
            if (i % 2 == 0)
                builder.add(ints[i]);
        }
        return builder.build();
    }

    private static IntStream nodes(Stream<PtGraph.PtEdge> path) {
        List<PtGraph.PtEdge> edges = path.collect(Collectors.toList());
        IntStream.Builder builder = IntStream.builder();
        builder.accept(edges.get(0).getBaseNode());
        for (PtGraph.PtEdge edge : edges) {
            builder.accept(edge.getAdjNode());
        }
        return builder.build();
    }

    private static Stream<PtGraph.PtEdge> hopDwellChain(GtfsStorage staticGtfs, int n) {
        Stream.Builder<PtGraph.PtEdge> builder = Stream.builder();
        Optional<PtGraph.PtEdge> any = StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(n).spliterator(), false)
                .filter(e -> e.getType() == GtfsStorage.EdgeType.HOP || e.getType() == GtfsStorage.EdgeType.DWELL)
                .findAny();
        while (any.isPresent()) {
            builder.accept(any.get());
            any = StreamSupport.stream(staticGtfs.getPtGraph().edgesAround(any.get().getAdjNode()).spliterator(), false)
                    .filter(e -> e.getType() == GtfsStorage.EdgeType.HOP || e.getType() == GtfsStorage.EdgeType.DWELL)
                    .findAny();
        }
        return builder.build();
    }

    boolean isBlocked(int edgeId) {
        return blockedEdges.contains(edgeId);
    }

    List<PtGraph.PtEdge> getAdditionalEdges() {
        return additionalEdges;
    }

    public Optional<GtfsReader.TripWithStopTimes> getTripUpdate(GTFSFeed staticFeed, GtfsRealtime.TripDescriptor tripDescriptor, Instant boardTime) {
        try {
            logger.trace("getTripUpdate {}", tripDescriptor);
            if (!isThisRealtimeUpdateAboutThisLineRun(boardTime)) {
                return Optional.empty();
            } else {
                GtfsRealtime.TripDescriptor normalizedTripDescriptor = normalize(tripDescriptor);
                return feedMessages.values().stream().flatMap(feedMessage -> feedMessage.getEntityList().stream()
                        .filter(e -> e.hasTripUpdate())
                        .map(e -> e.getTripUpdate())
                        .filter(tu -> normalize(tu.getTrip()).equals(normalizedTripDescriptor))
                        .map(tu -> {
                            try {
                                return toTripWithStopTimes(staticFeed, tu);
                            } catch (FeedExce | FeedExce2 e) {
                                return null;
                            }
                        }))
                        .findFirst();
            }
        } catch (RuntimeException e) {
            feedMessages.forEach((name, feed) -> {
                try (OutputStream s = new FileOutputStream(name+".gtfsdump")) {
                    feed.writeTo(s);
                } catch (IOException e1) {
                    try {
                        throw new PointPathException();
                    } catch (PointPathException ex) {
                        //nothing
                    }
                }
            });
            return Optional.empty();
        }
    }

    public static GtfsRealtime.TripDescriptor normalize(GtfsRealtime.TripDescriptor tripDescriptor) {
        return GtfsRealtime.TripDescriptor.newBuilder(tripDescriptor).clearRouteId().build();
    }
    private static final String PARE="Number of stop times: {}";
    public static GtfsReader.TripWithStopTimes toTripWithStopTimes(GTFSFeed feed, GtfsRealtime.TripUpdate tripUpdate) throws FeedExce, FeedExce2 {
        Optional<String> timezoneOptional = feed.agency.values().stream().findFirst().map(Agency::getagencyTimezone);
        ZoneId timezone;
        timezone = mino(timezoneOptional);
        logger.trace("{}", tripUpdate.getTrip());
        final List<StopTime> stopTimes = new ArrayList<>();
        Set<Integer> cancelledArrivals = new HashSet<>();
        Set<Integer> cancelledDepartures = new HashSet<>();
        Trip originalTrip = feed.trips.get(tripUpdate.getTrip().getTripId());
        Trip trip = new Trip();
        if (originalTrip != null) {
            trip.trip_id = originalTrip.trip_id;
            trip.route_id = originalTrip.route_id;
        } else {
            trip.trip_id = tripUpdate.getTrip().getTripId();
            trip.route_id = tripUpdate.getTrip().getRouteId();
        }
        int delay = 0;
        int time = -1;
        List<GtfsRealtime.TripUpdate.StopTimeUpdate> stopTimeUpdateListWithSentinel = new ArrayList<>(tripUpdate.getStopTimeUpdateList());
        Iterable<StopTime> interpolatedStopTimesForTrip;
        try {
            interpolatedStopTimesForTrip = feed.getInterpolatedStopTimesForTrip(tripUpdate.getTrip().getTripId());
        } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes firstAndLastStopsDoNotHaveTimes) {
            throw new FeedExce(firstAndLastStopsDoNotHaveTimes);
        }
        int stopSequenceCeiling = Math.max(stopTimeUpdateListWithSentinel.isEmpty() ? 0 : stopTimeUpdateListWithSentinel.get(stopTimeUpdateListWithSentinel.size() - 1).getStopSequence(),
                StreamSupport.stream(interpolatedStopTimesForTrip.spliterator(), false).mapToInt(StopTime::getStopSequence).max().orElse(0)
        ) + 1;
        stopTimeUpdateListWithSentinel.add(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder().setStopSequence(stopSequenceCeiling).setScheduleRelationship(NO_DATA).build());
        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : stopTimeUpdateListWithSentinel) {
            int nextStopSequence = stopTimes.isEmpty() ? 1 : stopTimes.get(stopTimes.size() - 1).getStopSequence() + 1;
            time = minouno(feed, tripUpdate, stopTimes, delay, time, stopTimeUpdate, nextStopSequence);

            final StopTime originalStopTime = feed.stopTims.get(new Fun.Tuple2<>(tripUpdate.getTrip().getTripId(), stopTimeUpdate.getStopSequence()));
            if (originalStopTime != null) {
                StopTime updatedStopTime = StopTime.copyStopTime(originalStopTime);
                delay = minotre(delay, stopTimeUpdate);
                updatedStopTime.setArrivalTime(Math.max(originalStopTime.getArrivalTime() + delay, time));
                logger.trace("stop_sequence {} scheduled arrival {} updated arrival {}", stopTimeUpdate.getStopSequence(), originalStopTime.getArrivalTime(), updatedStopTime.getArrivalTime());
                time = updatedStopTime.getArrivalTime();
                delay = minoquattro(delay, stopTimeUpdate);
                updatedStopTime.setDepartureTime(Math.max(originalStopTime.getDepartureTime() + delay, time));
                logger.trace("stop_sequence {} scheduled departure {} updated departure {}", stopTimeUpdate.getStopSequence(), originalStopTime.getDepartureTime(), updatedStopTime.getDepartureTime());
                time = updatedStopTime.getDepartureTime();
                stopTimes.add(updatedStopTime);
                logger.trace(PARE, stopTimes.size());
                if (stopTimeUpdate.getScheduleRelationship() == SKIPPED) {
                    cancelledArrivals.add(stopTimeUpdate.getStopSequence());
                    cancelledDepartures.add(stopTimeUpdate.getStopSequence());
                }
            } else if (stopTimeUpdate.getScheduleRelationship() == NO_DATA) {
                /*
                Bella ragazzi
                 */
            } else if (tripUpdate.getTrip().getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED) {
                final StopTime stopTime = new StopTime();
                stopTime.setStopSequence(stopTimeUpdate.getStopSequence());
                stopTime.setStopId(stopTimeUpdate.getStopId());
                stopTime.trip_id = trip.trip_id;
                final ZonedDateTime arrivalTime = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                stopTime.setArrivalTime((int) Duration.between(arrivalTime.truncatedTo(ChronoUnit.DAYS), arrivalTime).getSeconds());
                final ZonedDateTime departureTime = Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()).atZone(timezone);
                stopTime.setDepartureTime((int) Duration.between(departureTime.truncatedTo(ChronoUnit.DAYS), departureTime).getSeconds());
                stopTimes.add(stopTime);
                logger.trace(PARE, stopTimes.size());
            } else {
                // http://localhost:3000/route?point=45.51043713898763%2C-122.68381118774415&point=45.522104713562825%2C-122.6455307006836&weighting=fastest&pt.earliest_departure_time=2018-08-24T16%3A56%3A17Z&arrive_by=false&pt.max_walk_distance_per_leg=1000&pt.limit_solutions=5&locale=en-US&profile=pt&elevation=false&use_miles=false&points_encoded=false&pt.profile=true
                // long query:
                // http://localhost:3000/route?point=45.518526513612244%2C-122.68612861633302&point=45.52908004573869%2C-122.6862144470215&weighting=fastest&pt.earliest_departure_time=2018-08-24T16%3A51%3A20Z&arrive_by=false&pt.max_walk_distance_per_leg=10000&pt.limit_solutions=4&locale=en-US&profile=pt&elevation=false&use_miles=false&points_encoded=false&pt.profile=true
                throw new FeedExce2();
            }
        }
        logger.trace(PARE, stopTimes.size());
        BitSet validOnDay = new BitSet(); // Not valid on any day. Just a template.

        return new GtfsReader.TripWithStopTimes(trip, stopTimes, validOnDay, cancelledArrivals, cancelledDepartures);
    }

    private static int minoquattro(int delay, GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate) {
        if (stopTimeUpdate.hasDeparture()) {
            delay = stopTimeUpdate.getDeparture().getDelay();
        }
        return delay;
    }

    private static int minotre(int delay, GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate) {
        if (stopTimeUpdate.getScheduleRelationship() == NO_DATA) {
            delay = 0;
        }
        if (stopTimeUpdate.hasArrival()) {
            delay = stopTimeUpdate.getArrival().getDelay();
        }
        return delay;
    }

    private static int minouno(GTFSFeed feed, GtfsRealtime.TripUpdate tripUpdate, List<StopTime> stopTimes, int delay, int time, GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate, int nextStopSequence) {
        for (int i = nextStopSequence; i < stopTimeUpdate.getStopSequence(); i++) {
            StopTime previousOriginalStopTime = feed.stopTims.get(new Fun.Tuple2<>(tripUpdate.getTrip().getTripId(), i));
            if (previousOriginalStopTime == null) {
                continue; // This can and does happen. Stop sequence numbers can be left out.
            }
            StopTime updatedPreviousStopTime = StopTime.copyStopTime(previousOriginalStopTime);
            updatedPreviousStopTime.setArrivalTime(Math.max(previousOriginalStopTime.getArrivalTime() + delay, time));
            logger.trace("stop_sequence {} scheduled arrival {} updated arrival {}", i, previousOriginalStopTime.getArrivalTime(), updatedPreviousStopTime.getArrivalTime());
            time = updatedPreviousStopTime.getArrivalTime();
            updatedPreviousStopTime.setDepartureTime(Math.max(previousOriginalStopTime.getDepartureTime() + delay, time));
            logger.trace("stop_sequence {} scheduled departure {} updated departure {}", i, previousOriginalStopTime.getDepartureTime(), updatedPreviousStopTime.getDepartureTime());
            time = updatedPreviousStopTime.getDepartureTime();
            stopTimes.add(updatedPreviousStopTime);
            logger.trace(PARE, stopTimes.size());
        }
        return time;
    }

    private static ZoneId mino(Optional<String> timezoneOptional) {
        ZoneId timezone;
        if (timezoneOptional.isPresent()) {
            timezone = ZoneId.of(timezoneOptional.get());
        } else {
            timezone = ZoneId.systemDefault();
            logger.warn("Timezone information is missing in the GTFS feed, using system default timezone: {}", timezone);
        }
        return timezone;
    }

    public long getDelayForBoardEdge(PtGraph.PtEdge edge, Instant now) {
        if (isThisRealtimeUpdateAboutThisLineRun(now)) {
            return delaysForBoardEdges.getOrDefault(edge.getId(), 0);
        } else {
            return 0;
        }
    }

    public long getDelayForAlightEdge(PtGraph.PtEdge edge, Instant now) {
        if (isThisRealtimeUpdateAboutThisLineRun(now)) {
            return delaysForAlightEdges.getOrDefault(edge.getId(), 0);
        } else {
            return 0;
        }
    }

    boolean isThisRealtimeUpdateAboutThisLineRun(Instant now) {
        return Duration.between(feedTimestampOrNow(), now).toHours() <= 24;
    }

    private Instant feedTimestampOrNow() {
        return feedMessages.values().stream().map(feedMessage -> {
            if (feedMessage.getHeader().hasTimestamp()) {
                return Instant.ofEpochSecond(feedMessage.getHeader().getTimestamp());
            } else {
                return Instant.now();
            }
        }).findFirst().orElse(Instant.now());
    }

    public StopTime getStopTime(GTFSFeed staticFeed, GtfsRealtime.TripDescriptor tripDescriptor, Instant boardTime, int stopSequence) throws DbFileException {
        StopTime stopTime = staticFeed.stopTims.get(new Fun.Tuple2<>(tripDescriptor.getTripId(), stopSequence));
        if (stopTime == null) {
            Optional<GtfsReader.TripWithStopTimes> tripUpdate = getTripUpdate(staticFeed, tripDescriptor, boardTime);
                if (tripUpdate.isPresent()) {
                    return tripUpdate.get().stopTimes.get(stopSequence - 1);
                } else {
                    // handle the case where tripUpdate is empty
                    throw new DbFileException();
                }
        } else {
            return stopTime;
        }
    }

    public static class FeedExce extends Exception {
        public FeedExce(GTFSFeed.FirstAndLastStopsDoNotHaveTimes firstAndLastStopsDoNotHaveTimes) {
        }
    }

    public static class FeedExce2 extends Exception {
    }
}

