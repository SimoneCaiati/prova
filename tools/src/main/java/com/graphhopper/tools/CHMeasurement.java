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
package com.graphhopper.tools;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.eccezionecore.closefile;
import com.graphhopper.eccezionecore.lockexception;
import com.graphhopper.reader.dem.TileBasedElevationProvider;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static java.lang.System.nanoTime;

public class CHMeasurement {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHMeasurement.class);
    private static final String LELLO="car_profile";

    public static void main(String[] args) throws MiniPerfTest.MeasurExce2, MiniPerfTest.Task.MeasureExce3, MiniPerfTest.MeasureExce, TileBasedElevationProvider.ElevationExce, TranslationMap.TransExce {
        testPerformanceAutomaticNodeOrdering(args);
    }

    /**
     * Parses a given osm file, contracts the graph and runs random routing queries on it. This is useful to test
     * the node contraction heuristics with regards to the performance of the automatic graph contraction (the node
     * contraction order determines how many and which shortcuts will be introduced) and the resulting query speed.
     * The queries are compared with a normal AStar search for comparison and to ensure correctness.
     */
    private static void testPerformanceAutomaticNodeOrdering(String[] args) throws MiniPerfTest.MeasurExce2, MiniPerfTest.Task.MeasureExce3, MiniPerfTest.MeasureExce, TileBasedElevationProvider.ElevationExce, TranslationMap.TransExce {
        // example args:
        // map=berlin.pbf stats_file=stats.dat period_updates=0 lazy_updates=100 neighbor_updates=50 max_neighbor_updatse=3 contract_nodes=100 log_messages=20 edge_quotient_weight=100.0 orig_edge_quotient_weight=100.0 hierarchy_depth_weight=20.0 landmarks=0 cleanup=true turncosts=true threshold=0.1 seed=456 comp_iterations=10 perf_iterations=100 quick=false

        PMap map = PMap.read(args);
        GraphHopperConfig ghConfig = new GraphHopperConfig(map);
        LOGGER.info("Running analysis with parameters {}", ghConfig);
        String osmFile = ghConfig.getString("map", "map-matching/files/leipzig_germany.osm.pbf");
        ghConfig.putObject("datareader.file", osmFile);
        final String statsFile = ghConfig.getString("stats_file", null);
        final int periodicUpdates = ghConfig.getInt("period_updates", 0);
        final int lazyUpdates = ghConfig.getInt("lazy_updates", 100);
        final int neighborUpdates = ghConfig.getInt("neighbor_updates", 50);
        final int maxNeighborUpdates = ghConfig.getInt("max_neighbor_updates", 3);
        final int contractedNodes = ghConfig.getInt("contract_nodes", 100);
        final int logMessages = ghConfig.getInt("log_messages", 20);
        final float edgeQuotientWeight = ghConfig.getFloat("edge_quotient_weight", 100.0f);
        final float origEdgeQuotientWeight = ghConfig.getFloat("orig_edge_quotient_weight", 100.0f);
        final float hierarchyDepthWeight = ghConfig.getFloat("hierarchy_depth_weight", 20.0f);
        final int pollFactorHeuristic = ghConfig.getInt("poll_factor_heur", 5);
        final int pollFactorContraction = ghConfig.getInt("poll_factor_contr", 200);
        final int landmarks = ghConfig.getInt("landmarks", 0);
        final boolean cleanup = ghConfig.getBool("cleanup", true);
        final boolean withTurnCosts = ghConfig.getBool("turncosts", true);
        final int uTurnCosts = ghConfig.getInt(Parameters.Routing.U_TURN_COSTS, 80);
        final double errorThreshold = ghConfig.getDouble("threshold", 0.1);
        final long seed = ghConfig.getLong("seed", 456);
        final int compIterations = ghConfig.getInt("comp_iterations", 100);
        final int perfIterations = ghConfig.getInt("perf_iterations", 1000);
        final boolean quick = ghConfig.getBool("quick", false);

        final GraphHopper graphHopper = new GraphHopper();
        String profile = LELLO;
        if (withTurnCosts) {
            ghConfig.putObject("graph.vehicles", "car|turn_costs=true");
            ghConfig.setProfiles(Collections.singletonList(
                    new Profile(profile).setVehicle("car").setWeighting("fastest").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, uTurnCosts)
            ));
            ghConfig.setCHProfiles(Collections.singletonList(
                    new CHProfile(profile)
            ));
            fono(ghConfig, landmarks, profile);
        } else {
            ghConfig.putObject("graph.vehicles", "car");
            ghConfig.setProfiles(Collections.singletonList(
                    new Profile(profile).setVehicle("car").setWeighting("fastest").setTurnCosts(false)
            ));
        }

        ghConfig.putObject(PERIODIC_UPDATES, periodicUpdates);
        ghConfig.putObject(LAST_LAZY_NODES_UPDATES, lazyUpdates);
        ghConfig.putObject(NEIGHBOR_UPDATES, neighborUpdates);
        ghConfig.putObject(NEIGHBOR_UPDATES_MAX, maxNeighborUpdates);
        ghConfig.putObject(CONTRACTED_NODES, contractedNodes);
        ghConfig.putObject(LOG_MESSAGES, logMessages);
        serio(ghConfig, edgeQuotientWeight, origEdgeQuotientWeight, hierarchyDepthWeight, pollFactorHeuristic, pollFactorContraction, withTurnCosts);

        LOGGER.info("Initializing graph hopper with args: {}", ghConfig);
        graphHopper.init(ghConfig);

        dio(cleanup, graphHopper);

        PMap results = new PMap(ghConfig.asPMap());

        StopWatch sw = new StopWatch();
        sw.start();
        try {
            graphHopper.importOrLoad();
        } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
            //
        }
        sw.stop();
        results.putObject("_prepare_time", sw.getSeconds());
        LOGGER.info("Import and preparation took {}s", sw.getMillis() / 1000);

        mamma(uTurnCosts, errorThreshold, seed, compIterations, quick, graphHopper, results);

        mammauno(seed, perfIterations, quick, graphHopper, results);

        runPerformanceTest(ASTAR_BI, graphHopper, seed, perfIterations, results);

        if (!quick && landmarks > 0) {
            runPerformanceTest("lm", graphHopper, seed, perfIterations, results);
        }

        graphHopper.close();

        Map<String, Object> resultMap = results.toMap();
        TreeSet<String> sortedKeys = new TreeSet<>(resultMap.keySet());
        for (String key : sortedKeys) {
            LOGGER.info("{}={}", key, resultMap.get(key));
        }

        if (statsFile != null) {
            File f = new File(statsFile);
            boolean writeHeader = !f.exists();
            try (OutputStream os = new FileOutputStream(f, true);
                 Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                if (writeHeader)
                    writer.write(getHeader(sortedKeys));
                writer.write(getStatLine(sortedKeys, resultMap));
            } catch (IOException e) {
                LOGGER.error("Could not write summary to file '{}'", statsFile, e);
            }
        }

        // output to be used by external caller
        StringBuilder sb = new StringBuilder();
        for (String key : sortedKeys) {
            sb.append(key).append(":").append(resultMap.get(key)).append(";");
        }
        sb.deleteCharAt(sb.lastIndexOf(";"));

    }

    private static void fono(GraphHopperConfig ghConfig, int landmarks, String profile) {
        if (landmarks > 0) {
            ghConfig.setLMProfiles(Collections.singletonList(
                    new LMProfile(profile)
            ));
            ghConfig.putObject("prepare.lm.landmarks", landmarks);
        }
    }

    private static void dio(boolean cleanup, GraphHopper graphHopper) {
        if (cleanup) {
            graphHopper.clean();
        }
    }

    private static void mammauno( long seed, int perfIterations, boolean quick, GraphHopper graphHopper, PMap results) throws MiniPerfTest.MeasurExce2, MiniPerfTest.Task.MeasureExce3, MiniPerfTest.MeasureExce {
        if (!quick) {
            runPerformanceTest(DIJKSTRA_BI, graphHopper, seed, perfIterations, results);
        }
    }

    private static void mamma(int uTurnCosts, double errorThreshold, long seed, int compIterations, boolean quick, GraphHopper graphHopper, PMap results) throws MiniPerfTest.MeasurExce2, MiniPerfTest.Task.MeasureExce3, MiniPerfTest.MeasureExce {
        if (!quick) {
            runCompareTest(DIJKSTRA_BI, graphHopper, uTurnCosts, seed, compIterations, errorThreshold, results);
            runCompareTest(ASTAR_BI, graphHopper, uTurnCosts, seed, compIterations, errorThreshold, results);
        }
    }

    private static void serio(GraphHopperConfig ghConfig, float edgeQuotientWeight, float origEdgeQuotientWeight, float hierarchyDepthWeight, int pollFactorHeuristic, int pollFactorContraction, boolean withTurnCosts) {
        if (withTurnCosts) {
            ghConfig.putObject(EDGE_QUOTIENT_WEIGHT, edgeQuotientWeight);
            ghConfig.putObject(ORIGINAL_EDGE_QUOTIENT_WEIGHT, origEdgeQuotientWeight);
            ghConfig.putObject(HIERARCHY_DEPTH_WEIGHT, hierarchyDepthWeight);
            ghConfig.putObject(MAX_POLL_FACTOR_HEURISTIC_EDGE, pollFactorHeuristic);
            ghConfig.putObject(MAX_POLL_FACTOR_CONTRACTION_EDGE, pollFactorContraction);
        } else {
            ghConfig.putObject(MAX_POLL_FACTOR_HEURISTIC_NODE, pollFactorHeuristic);
            ghConfig.putObject(MAX_POLL_FACTOR_CONTRACTION_NODE, pollFactorContraction);
        }
    }

    private static String getHeader(TreeSet<String> keys) {
        StringBuilder sb = new StringBuilder("#");
        for (String key : keys) {
            sb.append(key).append(";");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String getStatLine(TreeSet<String> keys, Map<String, Object> results) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(results.get(key)).append(";");
        }
        sb.append("\n");
        return sb.toString();
    }
    private static long chErrors = 0;
    private static long noChErrors = 0;
    private static long chTime = 0;
    private static long noChTime = 0;
    private static void runCompareTest(final String algo, final GraphHopper graphHopper, final int uTurnCosts,
                                       long seed, final int iterations, final double threshold, final PMap results) throws MiniPerfTest.MeasurExce2, MiniPerfTest.Task.MeasureExce3, MiniPerfTest.MeasureExce {
        LOGGER.info("Running compare test for {}, using seed {}", algo, seed);
        Graph g = graphHopper.getBaseGraph();
        final int numNodes = g.getNodes();
        final NodeAccess nodeAccess = g.getNodeAccess();
        final Random random = new Random(seed);

        MiniPerfTest compareTest = new MiniPerfTest();
        compareTest.setIterations(iterations).start(new MiniPerfTest.Task() {
            long chDeviations = 0;

            @Override
            public int doCalc(boolean warmup, int run) {
                if (!warmup && run % 100 == 0) {
                    LOGGER.info("Finished {} of {} runs. {}", run, iterations,
                            run > 0 ? String.format(Locale.ROOT, " CH: %6.2fms, without CH: %6.2fms",
                                    chTime * 1.e-6 / run, noChTime * 1.e-6 / run) : "");
                }
                if (run == iterations - 1) {
                    String avgChTime = fmt(chTime * 1.e-6 / run);
                    String avgNoChTime = fmt(noChTime * 1.e-6 / run);
                    LOGGER.info("Finished all ({}) runs, CH: {}ms, without CH: {}ms", iterations, avgChTime, avgNoChTime);
                    results.putObject("_" + algo + ".time_comp_ch", avgChTime);
                    results.putObject("_" + algo + ".time_comp", avgNoChTime);
                    results.putObject("_" + algo + ".errors_ch", chErrors);
                    results.putObject("_" + algo + ".errors", noChErrors);
                    results.putObject("_" + algo + ".deviations", chDeviations);
                }
                GHRequest req = buildRequest(random, numNodes, nodeAccess, uTurnCosts, algo);
                long start = nanoTime();
                GHResponse chRoute = graphHopper.route(req);
                GHResponse nonChRoute = sentiero(warmup, req, start, chRoute,graphHopper);
                Integer x = mio(chRoute, nonChRoute, algo);
                if (x != null) return x;

                double chWeight = chRoute.getBest().getRouteWeight();
                double nonCHWeight = nonChRoute.getBest().getRouteWeight();
                if (Math.abs(chWeight - nonCHWeight) > threshold) {
                    LOGGER.warn("error for {}: difference between best paths with and without CH is above threshold ({}), {}",
                            algo, threshold, getWeightDifferenceString(chWeight, nonCHWeight));
                    chDeviations++;
                }
                if (!chRoute.getBest().getPoints().equals(nonChRoute.getBest().getPoints())) {
                    // small negative deviations are due to weight truncation when shortcuts are stored
                    LOGGER.warn("error for {}: found different points for query from {} to {}, {}", algo,
                            req.getPoints().get(0).toShortString(), req.getPoints().get(1).toShortString(),
                            getWeightDifferenceString(chWeight, nonCHWeight));
                }
                return chRoute.getErrors().size();
            }
        });
    }

    private static Integer mio(GHResponse chRoute, GHResponse nonChRoute, String algo) {
        if (nonChRoute == null) return 0;

        Integer chRoute1 = getlolla(chRoute, nonChRoute, algo);
        if (chRoute1 != null) return chRoute1;
        return null;
    }

    private static GHResponse sentiero(boolean warmup, GHRequest req, long start, GHResponse chRoute, GraphHopper graphHopper) {
        if (!warmup)
            chTime += (nanoTime() - start);

        req.getHints().putObject(Parameters.CH.DISABLE, true);
        start = nanoTime();
        GHResponse nonChRoute = graphHopper.route(req);
        if (!warmup)
            noChTime += nanoTime() - start;

        if (connectionNotFound(chRoute) && connectionNotFound(nonChRoute)) {
            // random query was not well defined -> ignore
            return null;
        }
        return nonChRoute;
    }
    private static Integer getlolla(GHResponse chRoute, GHResponse nonChRoute, String algo) {
        if (!chRoute.getErrors().isEmpty() || !nonChRoute.getErrors().isEmpty()) {
            LOGGER.warn("there were errors for {}: \n with CH: {} \n without CH: {}", algo, chRoute.getErrors(), nonChRoute.getErrors());
            return mino(chRoute, nonChRoute);
        }
        return null;
    }

    private static int mino(GHResponse chRoute, GHResponse nonChRoute) {
        if (!chRoute.getErrors().isEmpty()) {
            chErrors++;
        }
        if (!nonChRoute.getErrors().isEmpty()) {
            noChErrors++;
        }
        return chRoute.getErrors().size();
    }
    private static GHRequest buildRequest(Random random, int numNodes, NodeAccess nodeAccess, int uTurnCosts, String algo) {
        GHRequest req = buildRandomRequest(random, numNodes, nodeAccess);
        req.setProfile(LELLO);
        req.getHints().putObject(Parameters.CH.DISABLE, false);
        req.getHints().putObject(Parameters.Landmark.DISABLE, true);
        req.getHints().putObject(Parameters.Routing.U_TURN_COSTS, uTurnCosts);
        req.setAlgorithm(algo);
        return req;
    }

    private static void runPerformanceTest(final String algo, final GraphHopper graphHopper,
                                           long seed, final int iterations, final PMap results) throws MiniPerfTest.MeasurExce2, MiniPerfTest.Task.MeasureExce3, MiniPerfTest.MeasureExce {
        Graph g = graphHopper.getBaseGraph();
        final int numNodes = g.getNodes();
        final NodeAccess nodeAccess = g.getNodeAccess();
        final Random random = new Random(seed);
        final boolean lm = "lm".equals(algo);

        LOGGER.info("Running performance test for {}, seed = {}", algo, seed);
        final long[] numVisitedNodes = {0};
        MiniPerfTest performanceTest = new MiniPerfTest();
        performanceTest.setIterations(iterations).start(new MiniPerfTest.Task() {
            private long queryTime;

            @Override
            public int doCalc(boolean warmup, int run) {
                if (!warmup && run % 100 == 0) {
                    LOGGER.info("Finished {} of {} runs. {}", run, iterations,
                            run > 0 ? String.format(Locale.ROOT, " Time: %6.2fms", queryTime * 1.e-6 / run) : "");
                }
                if (run == iterations - 1) {
                    String avg = fmt(queryTime * 1.e-6 / run);
                    LOGGER.info("Finished all ({}) runs, avg time: {}ms", iterations, avg);
                    results.putObject("_" + algo + ".time_ch", avg);
                }
                GHRequest req = buildRandomRequest(random, numNodes, nodeAccess);
                req.putHint(Parameters.CH.DISABLE, lm);
                req.putHint(Parameters.Landmark.DISABLE, !lm);
                req.setProfile(LELLO);
                if (!lm) {
                    req.setAlgorithm(algo);
                } else {
                    req.putHint(Parameters.Landmark.ACTIVE_COUNT, 8);
                }
                long start = nanoTime();
                GHResponse route = graphHopper.route(req);
                numVisitedNodes[0] += route.getHints().getInt("visited_nodes.sum", 0);
                if (!warmup)
                    queryTime += nanoTime() - start;
                return getRealErrors(route).size();
            }
        });
        if (performanceTest.getDummySum() > 0.01 * iterations) {
            throw new IllegalStateException("too many errors, probably something is wrong");
        }
        LOGGER.info("Average query time for {}: {}ms", algo, performanceTest.getMean());
        LOGGER.info("Visited nodes for {}: {}", algo,
                (0<1?Helper.nf(numVisitedNodes[0]):null));
    }

    private static String getWeightDifferenceString(double chWeight, double noChWeight) {
        return String.format(Locale.ROOT, "route weight: %.6f (CH) vs. %.6f (no CH) (diff = %.6f)",
                chWeight, noChWeight, (chWeight - noChWeight));
    }

    private static boolean connectionNotFound(GHResponse response) {
        for (Throwable t : response.getErrors()) {
            if (t instanceof ConnectionNotFoundException) {
                return true;
            }
        }
        return false;
    }

    private static List<Throwable> getRealErrors(GHResponse response) {
        List<Throwable> realErrors = new ArrayList<>();
        for (Throwable t : response.getErrors()) {
            if (!(t instanceof ConnectionNotFoundException)) {
                realErrors.add(t);
            }
        }
        return realErrors;
    }

    private static GHRequest buildRandomRequest(Random random, int numNodes, NodeAccess nodeAccess) {
        int from = random.nextInt(numNodes);
        int to = random.nextInt(numNodes);
        double fromLat = nodeAccess.getLat(from);
        double fromLon = nodeAccess.getLon(from);
        double toLat = nodeAccess.getLat(to);
        double toLon = nodeAccess.getLon(to);
        return new GHRequest(fromLat, fromLon, toLat, toLon);
    }

    private static String fmt(double number) {
        return String.format(Locale.ROOT, "%.2f", number);
    }

}
