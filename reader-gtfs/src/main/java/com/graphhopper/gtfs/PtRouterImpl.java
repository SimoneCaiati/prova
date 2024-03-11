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

import com.conveyal.gtfs.GTFSFeed;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.MaximumNodesExceededException;
import java.util.logging.Logger;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingLong;

@SuppressWarnings("java:S3358")

public final class PtRouterImpl implements PtRouter {

    private final GraphHopperConfig config;
    private final TranslationMap translationMap;
    private final BaseGraph baseGraph;
    private final EncodingManager encodingManager;
    private final LocationIndex locationIndex;
    private final GtfsStorage gtfsStorage;
    private final PtGraph ptGraph;
    private final RealtimeFeed realtimeFeed;
    private final PathDetailsBuilderFactory pathDetailsBuilderFactory;
    private final WeightingFactory weightingFactory;

    @Inject
    public PtRouterImpl(GraphHopperConfig config, TranslationMap translationMap, BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, PathDetailsBuilderFactory pathDetailsBuilderFactory) {
        this.config = config;
        this.weightingFactory = new DefaultWeightingFactory(baseGraph, encodingManager);
        this.translationMap = translationMap;
        this.baseGraph = baseGraph;
        this.encodingManager = encodingManager;
        this.locationIndex = locationIndex;
        this.gtfsStorage = gtfsStorage;
        this.ptGraph = gtfsStorage.getPtGraph();
        this.realtimeFeed = realtimeFeed;
        this.pathDetailsBuilderFactory = pathDetailsBuilderFactory;
    }

    @Override
    public GHResponse route(Request request) {
        return new RequestHandler(request).route();
    }

    public static class Factory {
        private final GraphHopperConfig config;
        private final TranslationMap translationMap;
        private final BaseGraph baseGraph;
        private final EncodingManager encodingManager;
        private final LocationIndex locationIndex;
        private final GtfsStorage gtfsStorage;
        private final Map<String, Transfers> transfers;

        public Factory(GraphHopperConfig config, TranslationMap translationMap, BaseGraph baseGraph, EncodingManager encodingManager, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
            this.config = config;
            this.translationMap = translationMap;
            this.baseGraph = baseGraph;
            this.encodingManager = encodingManager;
            this.locationIndex = locationIndex;
            this.gtfsStorage = gtfsStorage;
            this.transfers = new HashMap<>();
            for (Map.Entry<String, GTFSFeed> entry : this.gtfsStorage.getGtfsFeeds().entrySet()) {
                this.transfers.put(entry.getKey(), new Transfers(entry.getValue()));
            }
        }

        public PtRouter createWith(GtfsRealtime.FeedMessage realtimeFeed) {
            Map<String, GtfsRealtime.FeedMessage> realtimeFeeds = new HashMap<>();
            realtimeFeeds.put("gtfs_0", realtimeFeed);
            return new PtRouterImpl(config, translationMap, baseGraph, encodingManager, locationIndex, gtfsStorage, RealtimeFeed.fromProtobuf(baseGraph, encodingManager, gtfsStorage, this.transfers, realtimeFeeds), new PathDetailsBuilderFactory());
        }

        public PtRouter createWithoutRealtimeFeed() {
            return new PtRouterImpl(config, translationMap, baseGraph, encodingManager, locationIndex, gtfsStorage, RealtimeFeed.empty(), new PathDetailsBuilderFactory());
        }
    }

    private class RequestHandler {
        private final int maxVisitedNodesForRequest;
        private final int limitSolutions;
        private final long maxProfileDuration;
        private final Instant initialTime;
        private final boolean profileQuery;
        private final boolean arriveBy;
        private final boolean ignoreTransfers;
        private final double betaTransfers;
        private final double betaStreetTime;
        private final double walkSpeedKmH;
        private final int blockedRouteTypes;
        private final Map<Integer, Long> transferPenaltiesByRouteType;
        private final GHLocation enter;
        private final GHLocation exit;
        private final Translation translation;
        private final List<String> requestedPathDetails;

        private final GHResponse response = new GHResponse();
        private final long limitTripTime;
        private final long limitStreetTime;
        private QueryGraph queryGraph;
        private int visitedNodes;
        private MultiCriteriaLabelSetting router;

        private final Optional<Profile> accessProfile;
        private final EdgeFilter accessSnapFilter;
        private final Weighting accessWeighting;
        private final Optional<Profile> egressProfile;
        private final EdgeFilter egressSnapFilter;
        private final Weighting egressWeighting;

        RequestHandler(Request request) {
            maxVisitedNodesForRequest = request.getMaxVisitedNodes();
            profileQuery = request.isProfileQuery();
            ignoreTransfers = Optional.ofNullable(request.getIgnoreTransfers()).orElse(request.isProfileQuery());
            betaTransfers = request.getBetaTransfers();
            betaStreetTime = request.getBetaStreetTime();
            limitSolutions = Optional.ofNullable(request.getLimitSolutions()).orElse(profileQuery ? 50 : ignoreTransfers ? 1 : Integer.MAX_VALUE);
            initialTime = request.getEarliestDepartureTime();
            maxProfileDuration = request.getMaxProfileDuration().toMillis();
            arriveBy = request.isArriveBy();
            walkSpeedKmH = request.getWalkSpeedKmH();
            blockedRouteTypes = request.getBlockedRouteTypes();
            transferPenaltiesByRouteType = request.getBoardingPenaltiesByRouteType();
            translation = translationMap.getWithFallBack(request.getLocale());
            enter = request.getPoints().get(0);
            exit = request.getPoints().get(1);
            limitTripTime = request.getLimitTripTime() != null ? request.getLimitTripTime().toMillis() : Long.MAX_VALUE;
            limitStreetTime = request.getLimitStreetTime() != null ? request.getLimitStreetTime().toMillis() : Long.MAX_VALUE;
            requestedPathDetails = request.getPathDetails();
            accessProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getAccessProfile())).findFirst();
            if(accessProfile.isPresent()){
            accessWeighting = weightingFactory.createWeighting(accessProfile.get(), new PMap(), false);
            accessSnapFilter = new DefaultSnapFilter(new FastestWeighting(
                    encodingManager.getBooleanEncodedValue(VehicleAccess.key(accessProfile.get().getVehicle())),
                    encodingManager.getDecimalEncodedValue(VehicleSpeed.key(accessProfile.get().getVehicle()))
            ), encodingManager.getBooleanEncodedValue(Subnetwork.key(accessProfile.get().getVehicle())));
            } else {
                // handle case where requested profile is not found
                final Logger logger = Logger.getLogger(RequestHandler.class.getName());
                logger.warning("Accesso al profilo non effettuato");
                accessWeighting = null;
                accessSnapFilter = null;
            }
            

            egressProfile = config.getProfiles().stream().filter(p -> p.getName().equals(request.getEgressProfile())).findFirst();
            if (egressProfile.isPresent()) {
                egressWeighting = weightingFactory.createWeighting(egressProfile.get(), new PMap(), false);
                egressSnapFilter = new DefaultSnapFilter(new FastestWeighting(
                        encodingManager.getBooleanEncodedValue(VehicleAccess.key(egressProfile.get().getVehicle())),
                        encodingManager.getDecimalEncodedValue(VehicleSpeed.key(egressProfile.get().getVehicle()))
                ), encodingManager.getBooleanEncodedValue(Subnetwork.key(egressProfile.get().getVehicle())));
            } else {
                // handle case where requested profile is not found
                final Logger logger = Logger.getLogger(RequestHandler.class.getName());
                logger.warning("profilo richiesto non trovato");
                egressWeighting = null;
                egressSnapFilter = null;
            }


        }

        GHResponse route() {
            StopWatch stopWatch = new StopWatch().start();
            PtLocationSnapper.Result result = new PtLocationSnapper(baseGraph, locationIndex, gtfsStorage).snapAll(Arrays.asList(enter, exit), Arrays.asList(accessSnapFilter, egressSnapFilter));
            queryGraph = result.queryGraph;
            response.addDebugInfo("idLookup:" + stopWatch.stop().getSeconds() + "s");

            Label.NodeId startNode;
            Label.NodeId destNode;
            if (arriveBy) {
                startNode = result.nodes.get(1);
                destNode = result.nodes.get(0);
            } else {
                startNode = result.nodes.get(0);
                destNode = result.nodes.get(1);
            }
            List<List<Label.Transition>> solutions = findPaths(startNode, destNode);
            parseSolutionsAndAddToResponse(solutions, result.points);
            return response;
        }

        private void parseSolutionsAndAddToResponse(List<List<Label.Transition>> solutions, PointList waypoints) {
            TripFromLabel tripFromLabel = new TripFromLabel(queryGraph, encodingManager, gtfsStorage, realtimeFeed, pathDetailsBuilderFactory, walkSpeedKmH);
            for (List<Label.Transition> solution : solutions) {
                final ResponsePath responsePath = tripFromLabel.createResponsePath(translation, waypoints, queryGraph, accessWeighting, egressWeighting, solution, requestedPathDetails);
                responsePath.setImpossible(solution.stream().anyMatch(t -> t.label.impossible));
                responsePath.setTime((solution.get(solution.size() - 1).label.innerlabel.currentTime - solution.get(0).label.innerlabel.currentTime));
                responsePath.setRouteWeight(router.weight(solution.get(solution.size() - 1).label));
                response.add(responsePath);
            }
            Comparator<ResponsePath> c = Comparator.comparingInt(p -> (p.isImpossible() ? 1 : 0));
            Comparator<ResponsePath> d = Comparator.comparingDouble(ResponsePath::getTime);
            response.getAll().sort(c.thenComparing(d));
        }

        private List<List<Label.Transition>> findPaths(Label.NodeId startNode, Label.NodeId destNode) {
            StopWatch stopWatch = new StopWatch().start();
            boolean isEgress = !arriveBy;
            Reno reno = getreno(isEgress);
            reno.stationRouter.setBetaStreetTime(betaStreetTime);
            reno.stationRouter.setLimitStreetTime(limitStreetTime);
            List<Label> stationLabels = new ArrayList<>();
            mimmo(startNode, destNode, reno.edgeType, reno.stationRouter, stationLabels);

            Map<Label.NodeId, Label> reverseSettledSet = new HashMap<>();
            vino(stationLabels, reverseSettledSet);

            GraphExplorer graphExplorer = new GraphExplorer(new GraphExplorer.InnerGraph(queryGraph, ptGraph, arriveBy ? egressWeighting : accessWeighting, gtfsStorage, realtimeFeed), arriveBy, false, true, walkSpeedKmH, false, blockedRouteTypes);
            List<Label> discoveredSolutions = new ArrayList<>();
            router = new MultiCriteriaLabelSetting(graphExplorer, arriveBy, !ignoreTransfers, profileQuery, maxProfileDuration, discoveredSolutions);
            router.setBetaTransfers(betaTransfers);
            router.setBetaStreetTime(betaStreetTime);
            router.setBoardingPenaltyByRouteType(routeType -> transferPenaltiesByRouteType.getOrDefault(routeType, 0L));
            final long smallestStationLabelWalkTime = stationLabels.stream()
                    .mapToLong(l -> l.streetTime).min()
                    .orElse(Long.MAX_VALUE);
            router.setLimitTripTime(Math.max(0, limitTripTime - smallestStationLabelWalkTime));
            router.setLimitStreetTime(Math.max(0, limitStreetTime - smallestStationLabelWalkTime));
            final long smallestStationLabelWeight;
            smallestStationLabelWeight = semivui(reno.stationRouter, stationLabels);
            Map<Label, Label> originalSolutions = new HashMap<>();

            Label accessEgressModeOnlySolution = null;
            long highestWeightForDominationTest = Long.MAX_VALUE;
            for (Label label : router.calcLabels(startNode, initialTime)) {
                visitedNodes++;
                if (sen(discoveredSolutions, smallestStationLabelWeight, accessEgressModeOnlySolution, highestWeightForDominationTest, label))
                    break;
                Label reverseLabel = reverseSettledSet.get(label.innerlabel.node);
                if (reverseLabel != null) {
                    Label combinedSolution = new Label(new Label.InnerLabel(label.innerlabel.currentTime - reverseLabel.innerlabel.currentTime + initialTime.toEpochMilli(), null, label.innerlabel.node, label.innerlabel.nTransfers + reverseLabel.innerlabel.nTransfers, label.innerlabel.departureTime), label.streetTime + reverseLabel.streetTime, label.extraWeight + reverseLabel.extraWeight, 0, label.impossible, null);
                    Predicate<Label> filter;
                    filter = fallo(combinedSolution);
                    if (router.isNotDominatedByAnyOf(combinedSolution, discoveredSolutions, filter)) {
                        router.removeDominated(combinedSolution, discoveredSolutions, filter);
                        discoveredSolutions.add(combinedSolution);
                        discoveredSolutions.sort(comparingLong(s -> Optional.ofNullable(s.innerlabel.departureTime).orElse(0L)));
                        originalSolutions.put(combinedSolution, label);
                        accessEgressModeOnlySolution = getsiso(accessEgressModeOnlySolution, label, reverseLabel, combinedSolution);
                        highestWeightForDominationTest = ciccio(discoveredSolutions, accessEgressModeOnlySolution);
                    }
                }
            }

            List<List<Label.Transition>> paths = new ArrayList<>();
            cibodue(reverseSettledSet, discoveredSolutions, originalSolutions, paths);

            response.addDebugInfo("routing:" + stopWatch.stop().getSeconds() + "s");
            cibouno(discoveredSolutions);
            return paths;
        }

        private boolean sen(List<Label> discoveredSolutions, long smallestStationLabelWeight, Label accessEgressModeOnlySolution, long highestWeightForDominationTest, Label label) {
            return men() || (!profileQuery || profileFinished(router, discoveredSolutions, accessEgressModeOnlySolution)) && router.weight(label) + smallestStationLabelWeight > highestWeightForDominationTest;
        }


        private boolean men() {
            return visitedNodes >= maxVisitedNodesForRequest;
        }


        private Reno getreno(boolean isEgress) {
            final GraphExplorer accessEgressGraphExplorer = new GraphExplorer(new GraphExplorer.InnerGraph(queryGraph, ptGraph, isEgress ? egressWeighting : accessWeighting, gtfsStorage, realtimeFeed), isEgress, true, false, walkSpeedKmH, false, blockedRouteTypes);
            GtfsStorage.EdgeType edgeType = isEgress ? GtfsStorage.EdgeType.EXIT_PT : GtfsStorage.EdgeType.ENTER_PT;
            MultiCriteriaLabelSetting stationRouter = new MultiCriteriaLabelSetting(accessEgressGraphExplorer, isEgress, false, false, maxProfileDuration, new ArrayList<>());
            return  new Reno(edgeType, stationRouter);
        }

        private class Reno {
            public final GtfsStorage.EdgeType edgeType;
            public final MultiCriteriaLabelSetting stationRouter;

            public Reno(GtfsStorage.EdgeType edgeType, MultiCriteriaLabelSetting stationRouter) {
                this.edgeType = edgeType;
                this.stationRouter = stationRouter;
            }
        }




        private Label getsiso(Label accessEgressModeOnlySolution, Label label, Label reverseLabel, Label combinedSolution) {
            if (label.innerlabel.nTransfers == 0 && reverseLabel.innerlabel.nTransfers == 0) {
                accessEgressModeOnlySolution = combinedSolution;
            }
            return accessEgressModeOnlySolution;
        }

        private void cibodue(Map<Label.NodeId, Label> reverseSettledSet, List<Label> discoveredSolutions, Map<Label, Label> originalSolutions, List<List<Label.Transition>> paths) {
            for (Label discoveredSolution : discoveredSolutions) {
                Label originalSolution = originalSolutions.get(discoveredSolution);
                List<Label.Transition> pathToDestinationStop = Label.getTransitions(originalSolution, arriveBy);
                cibo(reverseSettledSet, paths, pathToDestinationStop);
            }
        }

        private void cibouno(List<Label> discoveredSolutions) {
            if (discoveredSolutions.isEmpty() && visitedNodes >= maxVisitedNodesForRequest) {
                response.addError(new MaximumNodesExceededException("No path found - maximum number of nodes exceeded: " + maxVisitedNodesForRequest, maxVisitedNodesForRequest));
            }
            response.getHints().putObject("visited_nodes.sum", visitedNodes);
            response.getHints().putObject("visited_nodes.average", visitedNodes);
            if (discoveredSolutions.isEmpty()) {
                response.addError(new ConnectionNotFoundException("No route found", Collections.emptyMap()));
            }
        }

        private void cibo(Map<Label.NodeId, Label> reverseSettledSet, List<List<Label.Transition>> paths, List<Label.Transition> pathToDestinationStop) {
            if (arriveBy) {
                List<Label.Transition> pathFromStation = Label.getTransitions(reverseSettledSet.get(pathToDestinationStop.get(0).label.innerlabel.node), false);
                long diff = pathToDestinationStop.get(0).label.innerlabel.currentTime - pathFromStation.get(pathFromStation.size() - 1).label.innerlabel.currentTime;
                List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> new Label.Transition(new Label(new Label.InnerLabel(t.label.innerlabel.currentTime + diff, t.label.innerlabel.edge, t.label.innerlabel.node, t.label.innerlabel.nTransfers, t.label.innerlabel.departureTime), t.label.streetTime, t.label.extraWeight, t.label.residualDelay, t.label.impossible, null), t.edge)).collect(Collectors.toList());
                List<Label.Transition> pp = new ArrayList<>(pathToDestinationStop.subList(1, pathToDestinationStop.size()));
                pp.addAll(0, patchedPathFromStation);
                paths.add(pp);
            } else {
                Label destinationStopLabel = pathToDestinationStop.get(pathToDestinationStop.size() - 1).label;
                List<Label.Transition> pathFromStation = Label.getTransitions(reverseSettledSet.get(destinationStopLabel.innerlabel.node), true);
                long diff = destinationStopLabel.innerlabel.currentTime - pathFromStation.get(0).label.innerlabel.currentTime;
                List<Label.Transition> patchedPathFromStation = pathFromStation.stream().map(t -> new Label.Transition(new Label(new Label.InnerLabel(t.label.innerlabel.currentTime + diff, t.label.innerlabel.edge, t.label.innerlabel.node, destinationStopLabel.innerlabel.nTransfers + t.label.innerlabel.nTransfers, t.label.innerlabel.departureTime), destinationStopLabel.streetTime + pathFromStation.get(0).label.streetTime, destinationStopLabel.extraWeight + t.label.extraWeight, t.label.residualDelay, t.label.impossible, null), t.edge)).collect(Collectors.toList());
                List<Label.Transition> pp = new ArrayList<>(pathToDestinationStop);
                pp.addAll(patchedPathFromStation.subList(1, pathFromStation.size()));
                paths.add(pp);
            }
        }

        private void vino(List<Label> stationLabels, Map<Label.NodeId, Label> reverseSettledSet) {
            for (Label stationLabel : stationLabels) {
                reverseSettledSet.put(stationLabel.innerlabel.node, stationLabel);
            }
        }

        private long semivui(MultiCriteriaLabelSetting stationRouter, List<Label> stationLabels) {
            final long smallestStationLabelWeight;
            if (!stationLabels.isEmpty()) {
                smallestStationLabelWeight = stationRouter.weight(stationLabels.get(0));
            } else {
                smallestStationLabelWeight = Long.MAX_VALUE;
            }
            return smallestStationLabelWeight;
        }

        private long ciccio(List<Label> discoveredSolutions, Label accessEgressModeOnlySolution) {
            long highestWeightForDominationTest;
            if (profileQuery) {
                highestWeightForDominationTest = discoveredSolutions.stream().mapToLong(router::weight).max().orElse(Long.MAX_VALUE);
                highestWeightForDominationTest = dillo(discoveredSolutions, accessEgressModeOnlySolution, highestWeightForDominationTest);
            } else {
                highestWeightForDominationTest = discoveredSolutions.stream().filter(s -> !s.impossible && (ignoreTransfers || s.innerlabel.nTransfers <= 1)).mapToLong(router::weight).min().orElse(Long.MAX_VALUE);
            }
            return highestWeightForDominationTest;
        }

        private long dillo(List<Label> discoveredSolutions, Label accessEgressModeOnlySolution, long highestWeightForDominationTest) {
            if (accessEgressModeOnlySolution != null && discoveredSolutions.size() < limitSolutions) {
                // if1 we have a walk solution, we have it at every point in time in the profile.
                // (I can start walking any time I want, unlike with bus departures.)
                // Here we virtually add it to the end of the profile, so it acts as a sentinel
                // to remind us that we still have to search that far to close the set.
                highestWeightForDominationTest = Math.max(highestWeightForDominationTest, router.weight(accessEgressModeOnlySolution) + maxProfileDuration);
            }
            return highestWeightForDominationTest;
        }

        private Predicate<Label> fallo(Label combinedSolution) {
            Predicate<Label> filter;
            if (profileQuery && combinedSolution.innerlabel.departureTime != null)
                filter = targetLabel -> (!arriveBy ? router.prc(combinedSolution, targetLabel) : router.rprc(combinedSolution, targetLabel));
            else
                filter = tagetLabel -> true;
            return filter;
        }

        private void mimmo(Label.NodeId startNode, Label.NodeId destNode, GtfsStorage.EdgeType edgeType, MultiCriteriaLabelSetting stationRouter, List<Label> stationLabels) {
            for (Label label : stationRouter.calcLabels(destNode, initialTime)) {
                visitedNodes++;
                if (label.innerlabel.node.equals(startNode)) {
                    stationLabels.add(label);
                    break;
                } else if (label.innerlabel.edge != null && label.innerlabel.edge.getType() == edgeType) {
                    stationLabels.add(label);
                }
            }
        }

        private boolean profileFinished(MultiCriteriaLabelSetting router, List<Label> discoveredSolutions, Label walkSolution) {
            return discoveredSolutions.size() >= limitSolutions ||
                    (!discoveredSolutions.isEmpty() && router.departureTimeSinceStartTime(discoveredSolutions.get(discoveredSolutions.size() - 1)) != null && router.departureTimeSinceStartTime(discoveredSolutions.get(discoveredSolutions.size() - 1)) > maxProfileDuration) ||
                    walkSolution != null;
            // Imagine we can always add the walk solution again to the end of the list (it can start any time).
            // In turn, we must also think of this virtual walk solution in the other test (where we check if all labels are closed).
        }

    }

}
