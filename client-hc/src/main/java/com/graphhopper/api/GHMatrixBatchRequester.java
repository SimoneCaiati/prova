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
import com.graphhopper.jackson.ResponsePathDeserializer;
import com.graphhopper.util.Helper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * @author Peter Karich
 */
public class GHMatrixBatchRequester extends GHMatrixAbstractRequester {
    private int maxIterations = 100;
    private long sleepAfterGET = 1000;

    public GHMatrixBatchRequester() {
        this(MATRIX_URL);
    }

    public GHMatrixBatchRequester(String serviceUrl) {
        this(serviceUrl, new OkHttpClient.Builder().
                connectTimeout(5, TimeUnit.SECONDS).
                readTimeout(5, TimeUnit.SECONDS).build(), true);
    }

    public GHMatrixBatchRequester(String serviceUrl, OkHttpClient client, boolean doRequestGzip) {
        super(serviceUrl, client, doRequestGzip);
    }

    /**
     * Internal parameter. Increase only if you have very large matrices.
     */
    public GHMatrixBatchRequester setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    /**
     * Internal parameter. Increase only if you have very large matrices.
     */
    public GHMatrixBatchRequester setSleepAfterGET(long sleepAfterGETMillis) {
        this.sleepAfterGET = sleepAfterGETMillis;
        return this;
    }

    @Override
    public MatrixResponse route(GHMRequest ghRequest) throws MatrixExce {
        JsonNode requestJson = createPostRequest(ghRequest);

        boolean withTimes = ghRequest.getOutArrays().contains("times");
        boolean withDistances = ghRequest.getOutArrays().contains("distances");
        boolean withWeights = ghRequest.getOutArrays().contains("weights");
        final MatrixResponse matrixResponse = seno(ghRequest, withTimes, withDistances, withWeights);
        try {
            String postUrl = buildURLNoHints("/calculate", ghRequest);
            String postResponseStr = postJson(postUrl, requestJson);
            boolean debug = ghRequest.getHints().getBool("debug", false);
            if (debug) {
                //
            }

            JsonNode responseJson = fromStringToJSON(postUrl, postResponseStr);
            MatrixResponse matrixResponse1 = miouno(requestJson, matrixResponse, postUrl, postResponseStr, responseJson);
            if (matrixResponse1 != null) return matrixResponse1;

            final String id = responseJson.get("job_id").asText();
            int i = 0;
            for (; i < maxIterations; i++) {
                // SLEEP a bit and GET solution
                if (sleepAfterGET > 0) {
                    Thread.sleep(sleepAfterGET);
                }
                String getUrl = buildURLNoHints("/solution/" + id, ghRequest);

                String getResponseStr;
                getResponseStr = senouno(getUrl);

                JsonNode getResponseJson = fromStringToJSON(getUrl, getResponseStr);


                boolean shouldBreak = false;

                shouldBreak = akunaMatata2(ghRequest, matrixResponse, debug, getResponseJson, shouldBreak);

                if (shouldBreak) {
                    break;
                }

            }

            akunaMatata(i);

        } catch (InterruptedException | IOException | RequesterExce ex) {
            Thread.currentThread().interrupt();
            throw new MatrixExce(ex);
        }

        return matrixResponse;
    }

    private boolean akunaMatata2(GHMRequest ghRequest, MatrixResponse matrixResponse, boolean debug, JsonNode getResponseJson, boolean shouldBreak) {
        if (miodue(matrixResponse, debug, getResponseJson)) {
            shouldBreak = true;
        } else {
            String status = getResponseJson.get("status").asText();

            if ("processing".equals(status) || "waiting".equals(status)) {
                /*
                Ciao
                 */
            } else if (senodue(ghRequest, matrixResponse, getResponseJson, status)) {
                shouldBreak = true;
            } else {
                matrixResponse.addError(new RuntimeException("Status not supported: " + status + " - illegal JSON format?"));
                shouldBreak = true;
            }
        }
        return shouldBreak;
    }

    private void akunaMatata(int i) {
        if (i >= maxIterations) {
            throw new IllegalStateException("Maximum number of iterations reached " + maxIterations + ", increasing should only be necessary for big matrices. For smaller ones this is a bug, please contact us");
        }
    }

    private boolean senodue(GHMRequest ghRequest, MatrixResponse matrixResponse, JsonNode getResponseJson, String status) {
        if ("finished".equals(status)) {
            JsonNode solution = getResponseJson.get("solution");
            matrixResponse.addErrors(readUsableEntityError(ghRequest.getOutArrays(), solution));
            if (!matrixResponse.hasErrors())
                fillResponseFromJson(matrixResponse, solution, ghRequest.getFailFast());

            return true;
        }
        return false;
    }

    private String senouno(String getUrl) throws IOException {
        String getResponseStr;
        try {
            getResponseStr = getJson(getUrl);
        } catch (SocketTimeoutException ex) {
            // if timeout exception try once again:
            getResponseStr = getJson(getUrl);
        }
        return getResponseStr;
    }

    private boolean miodue(MatrixResponse matrixResponse, boolean debug, JsonNode getResponseJson) {
        if (debug) {
            //
        }
        matrixResponse.addErrors(ResponsePathDeserializer.readErrors(objectMapper, getResponseJson));
        return matrixResponse.hasErrors();
    }


    @Nullable
    private MatrixResponse miouno(JsonNode requestJson, MatrixResponse matrixResponse, String postUrl, String postResponseStr, JsonNode responseJson) {
        if (responseJson.has("message")) {
            matrixResponse.addErrors(ResponsePathDeserializer.readErrors(objectMapper, responseJson));
            return matrixResponse;
        }
        if (!responseJson.has("job_id")) {
            throw new IllegalStateException("Response should contain job_id but was "
                    + postResponseStr + ", json:" + requestJson + ",url:" + postUrl);
        }
        return null;
    }

    @NotNull
    private static MatrixResponse seno(GHMRequest ghRequest, boolean withTimes, boolean withDistances, boolean withWeights) {
        return new MatrixResponse(
                ghRequest.getPoints() == null ? ghRequest.getFromPoints().size() : ghRequest.getPoints().size(),
                ghRequest.getPoints() == null ? ghRequest.getToPoints().size() : ghRequest.getPoints().size(),
                withTimes, withDistances, withWeights);
    }

    protected String getJson(String url) throws IOException {
        Request okRequest = new Request.Builder().url(url).build();
        ResponseBody body = null;
        try {
            body = getDownloader().newCall(okRequest).execute().body();
            return body.string();
        } finally {
            Helper.close(body);
        }
    }

    public class MatrixExce extends Exception {
        public MatrixExce(Exception ex) {
        }
    }
}
