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

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import static java.lang.Math.*;

/**
 * @author Peter Karich
 */
@SuppressWarnings("java:S6548")
public class DistanceCalcEarth implements DistanceCalc {
    /**
     * mean radius of the earth
     */
    public static final double R = 6371000; // m
    /**
     * Radius of the earth at equator
     */
    public static final double R_EQ = 6378137; // m
    /**
     * Circumference of the earth
     */
    public static final double C = 2 * PI * R;
    public static final double KM_MILE = 1.609344;
    public static final double METERS_PER_DEGREE = C / 360.0;
    public static final DistanceCalcEarth DIST_EARTH = new DistanceCalcEarth();

    /**
     * Calculates distance of (from, to) in meter.
     * <p>
     * http://en.wikipedia.org/wiki/Haversine_formula a = sin²(Δlat/2) +
     * cos(lat1).cos(lat2).sin²(Δlong/2) c = 2.atan2(√a, √(1−a)) d = R.c
     */
    @Override
    public double calcDist(double fromLat, double fromLon, double toLat, double toLon) {
        double normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon);
        return R * 2 * asin(sqrt(normedDist));
    }

    /**
     * This implements a rather quick solution to calculate 3D distances on earth using euclidean
     * geometry mixed with Haversine formula used for the on earth distance. The haversine formula makes
     * not so much sense as it is only important for large distances where then the rather smallish
     * heights would becomes negligible.
     */
    @Override
    public double calcDist3D(double fromLat, double fromLon, double fromHeight,
                             double toLat, double toLon, double toHeight) {
        double eleDelta = hasElevationDiff(fromHeight, toHeight) ? (toHeight - fromHeight) : 0;
        double len = calcDist(fromLat, fromLon, toLat, toLon);
        return Math.sqrt(eleDelta * eleDelta + len * len);
    }

    @Override
    public double calcDenormalizedDist(double normedDist) {
        return R * 2 * asin(sqrt(normedDist));
    }

    /**
     * Returns the specified length in normalized meter.
     */
    @Override
    public double calcNormalizedDist(double dist) {
        double tmp = sin(dist / 2 / R);
        return tmp * tmp;
    }

    @Override
    public double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon) {
        double sinDeltaLat = sin(toRadians(toLat - fromLat) / 2);
        double sinDeltaLon = sin(toRadians(toLon - fromLon) / 2);
        return sinDeltaLat * sinDeltaLat
                + sinDeltaLon * sinDeltaLon * cos(toRadians(fromLat)) * cos(toRadians(toLat));
    }

    /**
     * Circumference of the earth at different latitudes (breitengrad)
     */
    public double calcCircumference(double lat) {
        return 2 * PI * R * cos(toRadians(lat));
    }

    public boolean isDateLineCrossOver(double lon1, double lon2) {
        return abs(lon1 - lon2) > 180.0;
    }

    @Override
    public BBox createBBox(double lat, double lon, double radiusInMeter) {
        if (radiusInMeter <= 0)
            throw new IllegalArgumentException("Distance must not be zero or negative! " + radiusInMeter + " lat,lon:" + lat + "," + lon);

        // length of a circle at specified lat / dist
        double dLon = (360 / (calcCircumference(lat) / radiusInMeter));

        // length of a circle is independent of the longitude
        double dLat = (360 / (DistanceCalcEarth.C / radiusInMeter));

        // Now return bounding box in coordinates
        return new BBox(lon - dLon, lon + dLon, lat - dLat, lat + dLat);
    }

    @Override
    public double calcNormalizedEdgeDistance(double rlatdeg, double rlonleg,
                                             double aletleg, double alondeg,
                                             double bletleg, double blondeg) {
        double shrinkFactor = calcShrinkFactor(aletleg, bletleg);

        double aLat = aletleg;
        double aLon = alondeg * shrinkFactor;

        double bLat = bletleg;
        double bLon = blondeg * shrinkFactor;

        double rLat = rlatdeg;
        double rLon = rlonleg * shrinkFactor;

        double deltaLon = bLon - aLon;
        double deltaLat = bLat - aLat;

        if (deltaLat == 0)
            // special case: horizontal edge
            return calcNormalizedDist(aletleg, rlonleg, rlatdeg, rlonleg);

        if (deltaLon == 0)
            // special case: vertical edge
            return calcNormalizedDist(rlatdeg, alondeg, rlatdeg, rlonleg);

        double norm = deltaLon * deltaLon + deltaLat * deltaLat;
        double factor = ((rLon - aLon) * deltaLon + (rLat - aLat) * deltaLat) / norm;

        // x,y is projection of r onto segment a-b
        double cLon = aLon + factor * deltaLon;
        double cLat = aLat + factor * deltaLat;
        return calcNormalizedDist(cLat, cLon / shrinkFactor, rlatdeg, rlonleg);
    }

    @Override
    public double calcNormalizedEdgeDistance3D(InnerCalc innerCalc,
                                               double aLatDeg, double aLonDeg, double aEleM,
                                               double bLatDeg, double bLonDeg, double bEleM) {
        if (Double.isNaN(innerCalc.relem) || Double.isNaN(aEleM) || Double.isNaN(bEleM))
            return calcNormalizedEdgeDistance(innerCalc.rlatdeg, innerCalc.rlondeg, aLatDeg, aLonDeg, bLatDeg, bLonDeg);

        double shrinkFactor = calcShrinkFactor(aLatDeg, bLatDeg);

        double aLat = aLatDeg;
        double aLon = aLonDeg * shrinkFactor;
        double aEle = aEleM / METERS_PER_DEGREE;

        double bLat = bLatDeg;
        double bLon = bLonDeg * shrinkFactor;
        double bEle = bEleM / METERS_PER_DEGREE;

        double rLat = innerCalc.rlatdeg;
        double rLon = innerCalc.rlondeg * shrinkFactor;
        double rEle = innerCalc.relem / METERS_PER_DEGREE;

        double deltaLon = bLon - aLon;
        double deltaLat = bLat - aLat;
        double deltaEle = bEle - aEle;

        double norm = deltaLon * deltaLon + deltaLat * deltaLat + deltaEle * deltaEle;
        double factor = ((rLon - aLon) * deltaLon + (rLat - aLat) * deltaLat + (rEle - aEle) * deltaEle) / norm;
        if (Double.isNaN(factor)) factor = 0;

        // x,y,z is projection of r onto segment a-b
        double cLon = aLon + factor * deltaLon;
        double cLat = aLat + factor * deltaLat;
        double cEleM = (aEle + factor * deltaEle) * METERS_PER_DEGREE;
        return calcNormalizedDist(cLat, cLon / shrinkFactor, innerCalc.rlatdeg, innerCalc.rlondeg) + calcNormalizedDist(innerCalc.relem - cEleM);
    }

    double calcShrinkFactor(double aLatDeg, double bLatDeg) {
        return cos(toRadians((aLatDeg + bLatDeg) / 2));
    }

    @Override
    public GHPoint calcCrossingPointToEdge(double rLatDeg, double rLonDeg,
                                           double aLatDeg, double aLonDeg,
                                           double bLatDeg, double bLonDeg) {
        double shrinkFactor = calcShrinkFactor(aLatDeg, bLatDeg);
        double aLat = aLatDeg;
        double aLon = aLonDeg * shrinkFactor;

        double bLat = bLatDeg;
        double bLon = bLonDeg * shrinkFactor;

        double rLat = rLatDeg;
        double rLon = rLonDeg * shrinkFactor;

        double deltaLon = bLon - aLon;
        double deltaLat = bLat - aLat;

        if (deltaLat == 0)
            // special case: horizontal edge
            return new GHPoint(aLatDeg, rLonDeg);

        if (deltaLon == 0)
            // special case: vertical edge        
            return new GHPoint(rLatDeg, aLonDeg);

        double norm = deltaLon * deltaLon + deltaLat * deltaLat;
        double factor = ((rLon - aLon) * deltaLon + (rLat - aLat) * deltaLat) / norm;

        // x,y is projection of r onto segment a-b
        double cLon = aLon + factor * deltaLon;
        double cLat = aLat + factor * deltaLat;
        return new GHPoint(cLat, cLon / shrinkFactor);
    }

    @Override
    public boolean validEdgeDistance(double rLatDeg, double rLonDeg,
                                     double aLatDeg, double aLonDeg,
                                     double bLatDeg, double bLonDeg) {
        double shrinkFactor = calcShrinkFactor(aLatDeg, bLatDeg);
        double aLat = aLatDeg;
        double aLon = aLonDeg * shrinkFactor;

        double bLat = bLatDeg;
        double bLon = bLonDeg * shrinkFactor;

        double rLat = rLatDeg;
        double rLon = rLonDeg * shrinkFactor;

        double arX = rLon - aLon;
        double arY = rLat - aLat;
        double abX = bLon - aLon;
        double abY = bLat - aLat;
        double abAr = arX * abX + arY * abY;

        double rbX = bLon - rLon;
        double rbY = bLat - rLat;
        double abRb = rbX * abX + rbY * abY;



        return abAr > 0 && abRb > 0;
    }

    @Override
    public GHPoint projectCoordinate(double latInDeg, double lonInDeg, double distanceInMeter, double headingClockwiseFromNorth) {
        double angularDistance = distanceInMeter / R;

        double latInRadians = Math.toRadians(latInDeg);
        double lonInRadians = Math.toRadians(lonInDeg);
        double headingInRadians = Math.toRadians(headingClockwiseFromNorth);

        // This formula is taken from: http://williams.best.vwh.net/avform.htm#LL (http://www.movable-type.co.uk/scripts/latlong.html -> https://github.com/chrisveness/geodesy MIT)
        // θ=heading,δ=distance,φ1=latInRadians
        // lat2 = asin( sin φ1 ⋅ cos δ + cos φ1 ⋅ sin δ ⋅ cos θ )     
        // lon2 = λ1 + atan2( sin θ ⋅ sin δ ⋅ cos φ1, cos δ − sin φ1 ⋅ sin φ2 )
        double projectedLat = Math.asin(Math.sin(latInRadians) * Math.cos(angularDistance)
                + Math.cos(latInRadians) * Math.sin(angularDistance) * Math.cos(headingInRadians));
        double projectedLon = lonInRadians + Math.atan2(Math.sin(headingInRadians) * Math.sin(angularDistance) * Math.cos(latInRadians),
                Math.cos(angularDistance) - Math.sin(latInRadians) * Math.sin(projectedLat));

        projectedLon = (projectedLon + 3 * Math.PI) % (2 * Math.PI) - Math.PI; // normalise to -180..+180°

        projectedLat = Math.toDegrees(projectedLat);
        projectedLon = Math.toDegrees(projectedLon);

        return new GHPoint(projectedLat, projectedLon);
    }

    @Override
    public GHPoint intermediatePoint(double f, double lat1, double lon1, double lat2, double lon2) {
        double lat1radians = Math.toRadians(lat1);
        double lon1radians = Math.toRadians(lon1);
        double lat2radians = Math.toRadians(lat2);
        double lon2radians = Math.toRadians(lon2);

        // This formula is taken from: (http://www.movable-type.co.uk/scripts/latlong.html -> https://github.com/chrisveness/geodesy MIT)

        double deltaLat = lat2radians - lat1radians;
        double deltaLon = lon2radians - lon1radians;
        double cosLat1 = cos(lat1radians);
        double cosLat2 = cos(lat2radians);
        double sinHalfDeltaLat = sin(deltaLat / 2);
        double sinHalfDeltaLon = sin(deltaLon / 2);

        double a = sinHalfDeltaLat * sinHalfDeltaLat + cosLat1 * cosLat2 * sinHalfDeltaLon * sinHalfDeltaLon;
        double angularDistance = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double sinDistance = sin(angularDistance);

        if (angularDistance == 0) return new GHPoint(lat1, lon1);

        double v = Math.sin((1 - f) * angularDistance) / sinDistance;
        double b = Math.sin(f * angularDistance) / sinDistance;

        double x = v * cosLat1 * cos(lon1radians) + b * cosLat2 * cos(lon2radians);
        double y = v * cosLat1 * sin(lon1radians) + b * cosLat2 * sin(lon2radians);
        double z = v * sin(lat1radians) + b * sin(lat2radians);

        double midLat = Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)));
        double midLon = Math.toDegrees(Math.atan2(y, x));

        return new GHPoint(midLat, midLon);
    }

    @Override
    public double calcDistance(PointList pointList) {
        return internCalcDistance(pointList, pointList.is3D());
    }

    public static double calcDistance(PointList pointList, boolean is3d) {
        return DistanceCalcEarth.DIST_EARTH.internCalcDistance(pointList, is3d);
    }

    private double internCalcDistance(PointList pointList, boolean is3d) {
        double prevLat = Double.NaN;
        double prevLon = Double.NaN;
        double prevEle = Double.NaN;
        double dist = 0;
        for (int i = 0; i < pointList.size(); i++) {
            if (i > 0) {
                if (is3d)
                    dist += calcDist3D(prevLat, prevLon, prevEle, pointList.getLat(i), pointList.getLon(i), pointList.getEle(i));
                else
                    dist += calcDist(prevLat, prevLon, pointList.getLat(i), pointList.getLon(i));
            }

            prevLat = pointList.getLat(i);
            prevLon = pointList.getLon(i);
            if (pointList.is3D())
                prevEle = pointList.getEle(i);
        }
        return dist;
    }

    @Override
    public boolean isCrossBoundary(double lon1, double lon2) {
        return abs(lon1 - lon2) > 300;
    }

    protected boolean hasElevationDiff(double a, double b) {
        return a != b && !Double.isNaN(a) && !Double.isNaN(b);
    }

    @Override
    public String toString() {
        return "EXACT";
    }
}
