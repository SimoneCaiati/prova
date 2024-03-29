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
package com.graphhopper.util;

import static java.lang.Math.sqrt;

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Calculates the distance of two points or one point and an edge in euclidean space.
 * <p>
 *
 * @author Peter Karich
 */
public class DistanceCalcEuclidean extends DistanceCalcEarth {
    @Override
    public double calcDist(double fromY, double fromX, double toY, double toX) {
        return sqrt(calcNormalizedDist(fromY, fromX, toY, toX));
    }

    @Override
    public double calcDist3D(double fromY, double fromX, double fromHeight, double toY, double toX, double toHeight) {
        return sqrt(calcNormalizedDist(fromY, fromX, toY, toX) + calcNormalizedDist(toHeight - fromHeight));
    }

    @Override
    public double calcDenormalizedDist(double normedDist) {
        return sqrt(normedDist);
    }

    /**
     * Returns the specified length in normalized meter.
     */
    @Override
    public double calcNormalizedDist(double dist) {
        return dist * dist;
    }

    @Override
    double calcShrinkFactor(double aLatDeg, double bLatDeg) {
        return 1.;
    }

    /**
     * Calculates in normalized meter
     */
    @Override
    public double calcNormalizedDist(double fromY, double fromX, double toY, double toX) {
        double dX = fromX - toX;
        double dY = fromY - toY;
        return dX * dX + dY * dY;
    }

    @Override
    public String toString() {
        return "2D";
    }
    private static final String EUCL="Not supported for the 2D Euclidean space";
    @Override
    public double calcCircumference(double lat) {
        throw new UnsupportedOperationException(EUCL);
    }

    @Override
    public boolean isDateLineCrossOver(double lon1, double lon2) {
        throw new UnsupportedOperationException(EUCL);
    }

    @Override
    public BBox createBBox(double lat, double lon, double radiusInMeter) {
        throw new UnsupportedOperationException(EUCL);
    }

    @Override
    public GHPoint projectCoordinate(double latInDeg, double lonInDeg, double distanceInMeter,
            double headingClockwiseFromNorth) {
        throw new UnsupportedOperationException(EUCL);
    }

    @Override
    public GHPoint intermediatePoint(double f, double lat1, double lon1, double lat2, double lon2) {
        double delatLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;
        double midLat = lat1 + delatLat * f;
        double midLon = lon1 + deltaLon * f;
        return new GHPoint(midLat, midLon);
    }

    @Override
    public boolean isCrossBoundary(double lon1, double lon2) {
        throw new UnsupportedOperationException(EUCL);
    }

    @Override
    public double calcNormalizedEdgeDistance(double rlatdeg, double rlonleg,
                                             double aletleg, double alondeg,
                                             double bletleg, double blondeg) {
        return calcNormalizedEdgeDistance3D(
            new InnerCalc(rlatdeg, rlonleg, 0),
                aletleg, alondeg, 0,
                bletleg, blondeg, 0
        );
    }

    @Override
    public double calcNormalizedEdgeDistance3D(InnerCalc innercalc,
                                               double aLatDeg, double aLonDeg, double aEleM,
                                               double bLatDeg, double bLonDeg, double bEleM) {
        double dx = bLonDeg - aLonDeg;
        double dy = bLatDeg - aLatDeg;
        double dz = bEleM - aEleM;

        double norm = dx * dx + dy * dy + dz * dz;
        double factor = ((innercalc.rlondeg - aLonDeg) * dx + (innercalc.rlatdeg - aLatDeg) * dy + (innercalc.relem - aEleM) * dz) / norm;
        if (Double.isNaN(factor)) factor = 0;

        // x,y,z is projection of r onto segment a-b
        double cx = aLonDeg + factor * dx;
        double cy = aLatDeg + factor * dy;
        double cz = aEleM + factor * dz;

        double rdx = cx - innercalc.rlondeg;
        double rdy = cy - innercalc.rlatdeg;
        double rdz = cz - innercalc.relem;

        return rdx * rdx + rdy * rdy + rdz * rdz;
    }
}
