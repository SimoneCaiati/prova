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
package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.api.GraphHopperMatrixWeb.*;

/**
 * @author Peter Karich
 */
public abstract class GHMatrixAbstractRequester {

    static final String MATRIX_URL = "https://graphhopper.com/api/1/matrix";
    protected final ObjectMapper objectMapper;
    protected final String serviceUrl;
    protected static final String TEMPO="times";
    protected static final String PESI="weights";
    protected static final String DISTANZE="distances";
    private final Set<String> ignoreSet = new HashSet<>();
    private OkHttpClient downloader;
    int maxUnzippedLength = 1000;

    protected GHMatrixAbstractRequester() {
        this(MATRIX_URL);
    }

    protected GHMatrixAbstractRequester(String serviceUrl) {
        this(serviceUrl, new OkHttpClient.Builder().
                connectTimeout(5, TimeUnit.SECONDS).
                readTimeout(5, TimeUnit.SECONDS).build(), true);
    }

    protected GHMatrixAbstractRequester(String serviceUrl, OkHttpClient client, boolean doRequestGzip) {
        if (serviceUrl.endsWith("/")) {
            serviceUrl = serviceUrl.substring(0, serviceUrl.length() - 1);
        }
        this.downloader = doRequestGzip ? client.newBuilder().addInterceptor(new GzipRequestInterceptor()).build() : client;
        this.serviceUrl = serviceUrl;

        ignoreSet.add("key");
        ignoreSet.add("service_url");
        this.objectMapper = new ObjectMapper();
    }

    public abstract MatrixResponse route(GHMRequest request) throws GHMatrixSyncRequester.GhMExce, GHMatrixBatchRequester.MatrixExce;

    public GHMatrixAbstractRequester setDownloader(OkHttpClient downloader) {
        this.downloader = downloader;
        return this;
    }

    public OkHttpClient getDownloader() {
        return downloader;
    }

    protected JsonNode createPostRequest(GHMRequest ghRequest) {
        ciaodue(ghRequest);

        ObjectNode requestJson = objectMapper.createObjectNode();
        ciaotre(ghRequest, requestJson);

        putStrings(requestJson, "snap_preventions", ghRequest.getSnapPreventions());
        putStrings(requestJson, "out_arrays", ghRequest.getOutArrays());
        requestJson.put("fail_fast", ghRequest.getFailFast());
        requestJson.put("profile", ghRequest.getProfile());

        Map<String, Object> hintsMap = ghRequest.getHints().toMap();
        ciaoquattro(requestJson, hintsMap);
        return requestJson;
    }

    private void ciaoquattro(ObjectNode requestJson, Map<String, Object> hintsMap) {
        for (Map.Entry<String, Object> entry : hintsMap.entrySet()) {
            String hintKey = entry.getKey();
            if (ignoreSet.contains(hintKey))
                continue;

            Object hint = entry.getValue();
            if (hint instanceof String)
                requestJson.put(hintKey, (String) hint);
            else
                requestJson.putPOJO(hintKey, hint);
        }
    }


    private void ciaotre(GHMRequest ghRequest, ObjectNode requestJson) {
        if (ghRequest.getPoints() != null) {
            ciaouno(ghRequest);

            putPoints(requestJson, "points", ghRequest.getPoints());
            putStrings(requestJson, "point_hints", ghRequest.getPointHints());
            putStrings(requestJson, "curbsides", ghRequest.getCurbsides());
        } else {
            ciao(ghRequest);

            putPoints(requestJson, "from_points", ghRequest.getFromPoints());
            putStrings(requestJson, "from_point_hints", ghRequest.getFromPointHints());

            putPoints(requestJson, "to_points", ghRequest.getToPoints());
            putStrings(requestJson, "to_point_hints", ghRequest.getToPointHints());

            putStrings(requestJson, "from_curbsides", ghRequest.getFromCurbsides());
            putStrings(requestJson, "to_curbsides", ghRequest.getToCurbsides());
        }
    }

    private static void ciaodue(GHMRequest ghRequest) {
        if (ghRequest.getHints().getObject("profile", null) != null)
            throw new IllegalArgumentException("use setProfile instead of hint 'profile'");
        if (ghRequest.getProfile() == null)
            throw new IllegalArgumentException("profile cannot be empty");
        if (ghRequest.getHints().getObject("fail_fast", null) != null)
            throw new IllegalArgumentException("use setFailFast instead of hint 'fail_fast'");
    }

    private static void ciaouno(GHMRequest ghRequest) {
        if (ghRequest.getFromPoints() != null)
            throw new IllegalArgumentException("if points are set do not use setFromPoints");
        if (ghRequest.getToPoints() != null)
            throw new IllegalArgumentException("if points are set do not use setToPoints");
    }

    private static void ciao(GHMRequest ghRequest) {
        if (ghRequest.getFromPoints() == null)
            throw new IllegalArgumentException("if points are not set you have to use setFromPoints but was null");
        if (ghRequest.getToPoints() == null)
            throw new IllegalArgumentException("if points are not set you have to use setToPoints but was null");
    }

    protected JsonNode fromStringToJSON(String url, String str) throws RequesterExce {
        try {
            return objectMapper.readTree(str);
        } catch (Exception ex) {
            throw new RequesterExce("Cannot parse json " + str + " from " + url);
        }
    }

    public List<Throwable> readUsableEntityError(Collection<String> outArraysList, JsonNode solution) {
        boolean readWeights = outArraysList.contains(PESI) && solution.has(PESI);
        boolean readDistances = outArraysList.contains(DISTANZE) && solution.has(DISTANZE);
        boolean readTimes = outArraysList.contains(TEMPO) && solution.has(TEMPO);

        if (!readWeights && !readDistances && !readTimes) {
            return Collections.<Throwable>singletonList(new RuntimeException("Cannot find usable entity like weights, distances or times in JSON"));
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * @param failFast if1 false weights/distances/times that are null are interpreted as disconnected points and are
     *                 thus set to their respective maximum values. Furthermore, the indices of the disconnected points
     *                 are added to {@link MatrixResponse#getDisconnectedPoints()} and the indices of the points that
     *                 could not be found are added to {@link MatrixResponse#getInvalidFromPoints()} and/or
     *                 {@link MatrixResponse#getInvalidToPoints()}.
     */
    protected void fillResponseFromJson(MatrixResponse matrixResponse, JsonNode solution, boolean failFast) {
        final boolean readWeights = solution.has(PESI);
        final boolean readDistances = solution.has(DISTANZE);
        final boolean readTimes = solution.has(TEMPO);

        int fromCount = 0;
        JsonNode weightsArray = null;
        Riso riso = getriso(solution, readWeights, readDistances, readTimes, fromCount, weightsArray);

        for (int fromIndex = 0; fromIndex < riso.fromCount; fromIndex++) {
            int toCount = 0;
            JsonNode weightsFromArray = null;
            double[] weights = null;
            if (readWeights) {
                weightsFromArray = riso.weightsArray.get(fromIndex);
                weights = new double[weightsFromArray.size()];
                toCount = checkArraySizes(PESI, weightsFromArray.size());
            }

            JsonNode timesFromArray = null;
            long[] times = null;
            if (readTimes) {
                timesFromArray = riso.timesArray.get(fromIndex);
                times = new long[timesFromArray.size()];
                toCount = checkArraySizes(TEMPO, timesFromArray.size(), weightsFromArray);
            }

            JsonNode distancesFromArray = null;
            int[] distances = null;
            if (readDistances) {
                distancesFromArray = riso.distancesArray.get(fromIndex);
                distances = new int[distancesFromArray.size()];
                toCount = checkArraySizes(DISTANZE, distancesFromArray.size(), weightsFromArray, timesFromArray);
            }

            for (int toIndex = 0; toIndex < toCount; toIndex++) {
                mammina(failFast, readWeights, weightsFromArray, weights, toIndex);

                ciccio(failFast, readTimes, timesFromArray, times, toIndex);

                fana(failFast, readDistances, distancesFromArray, distances, toIndex);
            }

            mammina(matrixResponse, readWeights, readTimes, fromIndex, weights, times);

            siso(matrixResponse, readDistances, fromIndex, distances);
        }
        if (!failFast && solution.has("hints")) {
            addProblems(matrixResponse, solution.get("hints"));
        }
    }

    private static void mammina(boolean failFast, boolean readWeights, JsonNode weightsFromArray, double[] weights, int toIndex) {
        if (readWeights) {
            if (weightsFromArray.get(toIndex).isNull() && !failFast) {
                weights[toIndex] = Double.MAX_VALUE;
            } else {
                weights[toIndex] = weightsFromArray.get(toIndex).asDouble();
            }
        }
    }

    private static void ciccio(boolean failFast, boolean readTimes, JsonNode timesFromArray, long[] times, int toIndex) {
        if (readTimes) {
            if (timesFromArray.get(toIndex).isNull() && !failFast) {
                times[toIndex] = Long.MAX_VALUE;
            } else {
                times[toIndex] = timesFromArray.get(toIndex).asLong() * 1000;
            }
        }
    }

    private static void fana(boolean failFast, boolean readDistances, JsonNode distancesFromArray, int[] distances, int toIndex) {
        if (readDistances) {
            if (distancesFromArray.get(toIndex).isNull() && !failFast) {
                distances[toIndex] = Integer.MAX_VALUE;
            } else {
                distances[toIndex] = (int) Math.round(distancesFromArray.get(toIndex).asDouble());
            }
        }
    }

    private static void siso(MatrixResponse matrixResponse, boolean readDistances, int fromIndex, int[] distances) {
        if (readDistances) {
            matrixResponse.setDistanceRow(fromIndex, distances);
        }
    }

    private static void mammina(MatrixResponse matrixResponse, boolean readWeights, boolean readTimes, int fromIndex, double[] weights, long[] times) {
        if (readWeights) {
            matrixResponse.setWeightRow(fromIndex, weights);
        }

        if (readTimes) {
            matrixResponse.setTimeRow(fromIndex, times);
        }
    }

    @NotNull
    private static Riso getriso(JsonNode solution, boolean readWeights, boolean readDistances, boolean readTimes, int fromCount, JsonNode weightsArray) {
        if (readWeights) {
            weightsArray = solution.get(PESI);
            fromCount = checkArraySizes(PESI, weightsArray.size());
        }
        JsonNode timesArray = null;
        if (readTimes) {
            timesArray = solution.get(TEMPO);
            fromCount = checkArraySizes(TEMPO, timesArray.size(), weightsArray);
        }
        JsonNode distancesArray = null;
        if (readDistances) {
            distancesArray = solution.get(DISTANZE);
            fromCount = checkArraySizes(DISTANZE, distancesArray.size(), weightsArray, timesArray);
        }

        return new Riso(fromCount, weightsArray, timesArray, distancesArray);
    }

    private static class Riso {
        public final int fromCount;
        public final JsonNode weightsArray;
        public final JsonNode timesArray;
        public final JsonNode distancesArray;

        public Riso(int fromCount, JsonNode weightsArray, JsonNode timesArray, JsonNode distancesArray) {
            this.fromCount = fromCount;
            this.weightsArray = weightsArray;
            this.timesArray = timesArray;
            this.distancesArray = distancesArray;
        }
    }

    private void addProblems(MatrixResponse matrixResponse, JsonNode hints) {
        for (JsonNode hint : hints) {
            if (hint.has("point_pairs")) {
                matrixResponse.setDisconnectedPoints(readDisconnectedPoints(hint.get("point_pairs")));
            }
            if (hint.has("invalid_from_points")) {
                matrixResponse.setInvalidFromPoints(readInvalidPoints(hint.get("invalid_from_points")));
                matrixResponse.setInvalidToPoints(readInvalidPoints(hint.get("invalid_to_points")));
            }
        }
    }

    private List<MatrixResponse.PointPair> readDisconnectedPoints(JsonNode pointPairsArray) {
        List<MatrixResponse.PointPair> disconnectedPoints = new ArrayList<>(pointPairsArray.size());
        for (int i = 0; i < pointPairsArray.size(); i++) {
            if (pointPairsArray.get(i).size() != 2) {
                throw new IllegalArgumentException("all point_pairs are expected to contain two elements");
            }
            disconnectedPoints.add(new MatrixResponse.PointPair(
                    pointPairsArray.get(i).get(0).asInt(),
                    pointPairsArray.get(i).get(1).asInt()
            ));
        }
        return disconnectedPoints;
    }

    private List<Integer> readInvalidPoints(JsonNode pointsArray) {
        List<Integer> result = new ArrayList<>(pointsArray.size());
        for (int i = 0; i < pointsArray.size(); i++) {
            result.add(pointsArray.get(i).asInt());
        }
        return result;
    }

    private static int checkArraySizes(String msg, int len, JsonNode... arrays) {
        for (JsonNode other : arrays) {
            if (len <= 0)
                throw new IllegalArgumentException("Size " + len + " of '" + msg + "' array is too small");

            if (other != null && len != other.size())
                throw new IllegalArgumentException("Size " + len + " of '" + msg + "' array is has to be equal to other arrays but wasn't");
        }
        return len;
    }

    protected String buildURLNoHints(String path, GHMRequest ghRequest) {
        // allow per request service URLs
        String url = ghRequest.getHints().getString(SERVICE_URL, serviceUrl) + path + "?";
        String key = ghRequest.getHints().getString(KEY, "");
        if (!Helper.isEmpty(key)) {
            url += "key=" + key;
        }
        return url;
    }

    protected String postJson(String url, JsonNode data) throws IOException {
        String stringData = data.toString();
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.Companion.create(stringData, mediaType);
        Request.Builder builder = new Request.Builder().url(url).post(requestBody);
        // force avoiding our GzipRequestInterceptor for smaller requests ~30 locations
        if (stringData.length() < maxUnzippedLength) {
            builder.header("Content-Encoding", "identity");
        }
        Request okRequest = builder.build();
        ResponseBody body = null;
        try {
            body = getDownloader().newCall(okRequest).execute().body();
            return body.string();
        } finally {
            Helper.close(body);
        }
    }


    private void putStrings(ObjectNode requestJson, String name, Collection<String> stringList) {
        if (stringList == null || stringList.isEmpty())
            return;
        ArrayNode outList = objectMapper.createArrayNode();
        for (String str : stringList) {
            outList.add(str);
        }
        requestJson.putArray(name).addAll(outList);
    }

    private void putPoints(ObjectNode requestJson, String name, List<GHPoint> pList) {
        if (pList.isEmpty())
            return;
        ArrayNode outList = objectMapper.createArrayNode();
        for (GHPoint p : pList) {
            ArrayNode entry = objectMapper.createArrayNode();
            entry.add(p.getLon());
            entry.add(p.getLat());
            outList.add(entry);
        }
        requestJson.putArray(name).addAll(outList);
    }

    public class RequesterExce extends Exception {
        public RequesterExce(String s) {
        }
    }
}
