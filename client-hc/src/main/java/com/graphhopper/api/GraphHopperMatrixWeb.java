package com.graphhopper.api;

import com.graphhopper.util.Helper;
import okhttp3.MediaType;

/**
 * @author Peter Karich
 */
public class GraphHopperMatrixWeb {

    static final String SERVICE_URL = "service_url";
    public static final String KEY = "key";
    static final MediaType MT_JSON = MediaType.parse("application/json; charset=utf-8");
    private final GHMatrixAbstractRequester requester;
    private String chiave;

    public GraphHopperMatrixWeb() {
        this(new GHMatrixSyncRequester());
    }

    public GraphHopperMatrixWeb(String serviceUrl) {
        this(new GHMatrixSyncRequester(serviceUrl));
    }

    public GraphHopperMatrixWeb(GHMatrixAbstractRequester requester) {
        this.requester = requester;
    }

    public GraphHopperMatrixWeb setChiave(String chiave) {
        if (chiave == null || chiave.isEmpty()) {
            throw new IllegalStateException("Key cannot be empty");
        }

        this.chiave = chiave;
        return this;
    }

    public MatrixResponse route(GHMRequest request) throws GHMatrixSyncRequester.GhMExce, GHMatrixBatchRequester.MatrixExce {
        if (!Helper.isEmpty(chiave))
            request.getHints().putObject(KEY, chiave);
        return requester.route(request);
    }
}
