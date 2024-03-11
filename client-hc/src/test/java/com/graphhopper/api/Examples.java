package com.graphhopper.api;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;




import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Disabled
 class Examples {

    String apiKey = "<YOUR_API_KEY>";

    @Test
 void routing() throws GraphHopperWeb.GraphExce {
    GraphHopperWeb gh = new GraphHopperWeb();
    gh.setKey(apiKey);
    gh.setDownloader(new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build());

    GHRequest req = new GHRequest().addPoint(new GHPoint(49.6724, 11.3494)).addPoint(new GHPoint(49.6550, 11.4180));
    req.setProfile("bike");
    req.putHint("elevation", false);
    req.putHint("instructions", true);
    req.putHint("calc_points", true);
    req.setLocale(Locale.GERMAN);
    req.setPathDetails(Arrays.asList(Parameters.Details.STREET_NAME, Parameters.Details.AVERAGE_SPEED, Parameters.Details.EDGE_ID));

    GHResponse fullRes = gh.route(req);
    if (fullRes.hasErrors())
        throw new RuntimeException(fullRes.getErrors().toString());

    ResponsePath res = fullRes.getBest();
    PointList pl = res.getPoints();
    double distance = res.getDistance();
    long millis = res.getTime();
    InstructionList il = res.getInstructions();
    List<PathDetail> pathDetails = res.getPathDetails().get(Parameters.Details.STREET_NAME);

    assertGreaterThan(0, distance);
    assertGreaterThan(0, millis);
    
}

    @Test
     void matrix() throws GHMatrixSyncRequester.GhMExce, GHMatrixBatchRequester.MatrixExce {
        // Hint: create this thread safe instance only once in your application to allow the underlying library to cache the costly initial https handshake
        GraphHopperMatrixWeb matrixClient = new GraphHopperMatrixWeb();
        // for very large matrices you need:
        // GraphHopperMatrixWeb matrixClient = new GraphHopperMatrixWeb(new GHMatrixBatchRequester());
        matrixClient.setChiave(apiKey);

        GHMRequest ghmRequest = new GHMRequest();
        ghmRequest.setOutArrays(Arrays.asList("distances", "times"));
        ghmRequest.setProfile("car");

        // Option 1: init points for a symmetric matrix
        List<GHPoint> allPoints = Arrays.asList(new GHPoint(49.6724, 11.3494), new GHPoint(49.6550, 11.4180));
        ghmRequest.setPoints(allPoints);
        MatrixResponse responseSymm = matrixClient.route(ghmRequest);
        if (responseSymm.hasErrors())
            throw new RuntimeException(responseSymm.getErrors().toString());
        // get time from first to second point:
        // System.out.println(response.getTime(0, 1));

        // Option 2: for an asymmetric matrix do:
        ghmRequest = new GHMRequest();
        ghmRequest.setOutArrays(Arrays.asList("distances", "times"));
        ghmRequest.setProfile("car");
        ghmRequest.setFromPoints(Arrays.asList(new GHPoint(49.6724, 11.3494)));
        // or init e.g. a one-to-many matrix:
        ghmRequest.setToPoints(Arrays.asList(new GHPoint(49.6724, 11.3494), new GHPoint(49.6550, 11.4180)));

        MatrixResponse responseAsymm = matrixClient.route(ghmRequest);
        if (responseAsymm.hasErrors())
            throw new RuntimeException(responseAsymm.getErrors().toString());

        // use the distance between points 0 and 1
        double distance01 = responseSymm.getDistance(0, 1);
        double distance10 = responseSymm.getDistance(1, 0);

        // use the time from first to second point:
        double time01 = responseSymm.getTime(0, 1);
        double time10 = responseSymm.getTime(1, 0);

        // add an assertion to check that distance01 equals distance10
        assertEquals(distance01, distance10);
    }


    public void assertGreaterThan(double expected, double actual) {
        if (actual <= expected) {
            throw new AssertionError("Expected " + actual + " to be greater than " + expected);
        }
    }
    
    
}
