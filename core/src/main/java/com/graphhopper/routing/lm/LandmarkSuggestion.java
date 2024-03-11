package com.graphhopper.routing.lm;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class collects landmarks from an external source for one subnetwork to avoid the expensive and sometimes
 * suboptimal automatic landmark finding process.
 */
public class LandmarkSuggestion {
    private final List<Integer> nodeIds;
    private final BBox box;

    public LandmarkSuggestion(List<Integer> nodeIds, BBox box) {
        this.nodeIds = nodeIds;
        this.box = box;
    }

    public List<Integer> getNodeIds() {
        return nodeIds;
    }

    public BBox getBox() {
        return box;
    }

    /**
     * The expected format is lon,lat per line where lines starting with characters will be ignored. You can create
     * such a file manually via geojson.io -> Save as CSV. Optionally add a second line with
     * <pre>#BBOX:minLat,minLon,maxLat,maxLon</pre>
     * <p>
     * to specify an explicit bounding box.  support GeoJSON instead.
     */
    public static LandmarkSuggestion readLandmarks(String file, LocationIndex locationIndex) throws IOException, LandExce {
        // landmarks should be suited for all vehicles
        EdgeFilter edgeFilter = EdgeFilter.ALL_EDGES;
        List<String> lines = Helper.readFile(file);
        List<Integer> landmarkNodeIds = new ArrayList<>();
        BBox bbox = BBox.createInverse(false);
        int lmSuggestionIdx = 0;
        String errors = "";
        for (String lmStr : lines) {
            boolean shouldContinue = false;

            if (lmStr.startsWith("#BBOX:")) {
                bbox = BBox.parseTwoPoints(lmStr.substring("#BBOX:".length()));
                shouldContinue = true;
            } else if (lmStr.isEmpty() || Character.isAlphabetic(lmStr.charAt(0))) {
                shouldContinue = true;
            }

            GHPoint point = GHPoint.fromStringLonLat(lmStr);
            eccezioneTimeYes(lmSuggestionIdx, lmStr, point);

            lmSuggestionIdx++;
            Snap result = locationIndex.findClosest(point.getLat(), point.getLon(), edgeFilter);

            if (shouldContinue || !result.isValid()) {
                if (!result.isValid()) {
                    StringBuilder errors1 = new StringBuilder();

                    errors1.append("Cannot find close node found for landmark suggestion[")
                            .append(lmSuggestionIdx)
                            .append("]=")
                            .append(point)
                            .append(".\n");
                }
                continue;
            }


            bbox.update(point.getLat(), point.getLon());
            landmarkNodeIds.add(result.getClosestNode());
        }

        if (!errors.isEmpty())
            try {
                throw new LandExce(errors);
            } catch (LandExce e) {
                //
            }

        return new LandmarkSuggestion(landmarkNodeIds, bbox);
    }

    private static void eccezioneTimeYes(int lmSuggestionIdx, String lmStr, GHPoint point) throws LandExce {
        if (point == null) {
            throw new LandExce("Invalid format " + lmStr + " for point " + lmSuggestionIdx);
        }
    }

    public static class LandExce extends Throwable {
        public LandExce(String errors) {
        }
    }
}
