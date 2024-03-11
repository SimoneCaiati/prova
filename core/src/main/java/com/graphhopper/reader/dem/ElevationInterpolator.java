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
package com.graphhopper.reader.dem;

import com.graphhopper.util.PointList;

import static com.graphhopper.util.Helper.round2;

/**
 * Elevation interpolator calculates elevation for the given lat/lon coordinates
 * based on lat/lon/ele coordinates of the given points.
 * <p>
 * In case of two points, elevation is calculated using linear interpolation
 * (see
 * ).
 * <p>
 * In case of three points, elevation is calculated using planar interpolation
 * (see
 * ).
 * <p>
 * In case of more than three points, elevation is calculated using the
 * interpolation method described in the
 * <a href="http://math.stackexchange.com/a/1930758/140512">following post</a>
 * (see {@link #calculateElevationBasedOnPointList(double, double, PointList)}.
 *
 * @author Alexey Valikov
 */

public class ElevationInterpolator {

    public static final double EPSILON = 0.00001;
    public static final double EPSILON2 = EPSILON * EPSILON;
    public static class InnerElevation {
         double lat;
         double lon;

        public InnerElevation(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }
    public double calculateElevationBasedOnTwoPoints(InnerElevation innerelevation, double lat0,
                                                     double lon0, double ele0, double lat1, double lon1, double ele1) {
        double dlat0 = lat0 - innerelevation.lat;
        double dlon0 = lon0 - innerelevation.lon;
        double dlat1 = lat1 - innerelevation.lat;
        double dlon1 = lon1 - innerelevation.lon;
        double l0 = Math.sqrt(dlon0 * dlon0 + dlat0 * dlat0);
        double l1 = Math.sqrt(dlon1 * dlon1 + dlat1 * dlat1);
        double l = l0 + l1;
        if (l < EPSILON) {
            return l0 <= l1 ? ele0 : ele1;
        } else {
            // Otherwise do linear interpolation
            return round2(ele0 + (ele1 - ele0) * l0 / l);
        }
    }
    public static class InnerCalculate {
         double lat;
         double lon;
         double lat0;
         double lon0;
         double ele0;

        public InnerCalculate(double lat, double lon, double lat0, double lon0, double ele0) {
            this.lat = lat;
            this.lon = lon;
            this.lat0 = lat0;
            this.lon0 = lon0;
            this.ele0 = ele0;
        }
    }
    public double calculateElevationBasedOnThreePoints(InnerCalculate innercalculate, double lat1, double lon1, double ele1, double lat2,
                                                       double lon2, double ele2) {

        double dlat10 = lat1 - innercalculate.lat0;
        double dlon10 = lon1 - innercalculate.lon0;
        double dele10 = ele1 - innercalculate.ele0;
        double dlat20 = lat2 - innercalculate.lat0;
        double dlon20 = lon2 - innercalculate.lon0;
        double dele20 = ele2 - innercalculate.ele0;

        double a = dlon10 * dele20 - dele10 * dlon20;
        double b = dele10 * dlat20 - dlat10 * dele20;
        double c = dlat10 * dlon20 - dlon10 * dlat20;

        if (Math.abs(c) < EPSILON) {
            double dlat21 = lat2 - lat1;
            double dlon21 = lon2 - lon1;
            double dele21 = ele2 - ele1;

            double l10 = dlat10 * dlat10 + dlon10 * dlon10 + dele10 * dele10;
            double l20 = dlat20 * dlat20 + dlon20 * dlon20 + dele20 * dele20;
            double l21 = dlat21 * dlat21 + dlon21 * dlon21 + dele21 * dele21;

            if (l21 > l10 && l21 > l20) {
                return calculateElevationBasedOnTwoPoints(new InnerElevation(innercalculate.lat, innercalculate.lon), lat1, lon1, ele1, lat2, lon2,
                        ele2);
            } else if (l20 > l10 && l20 > l21) {
                return calculateElevationBasedOnTwoPoints(new InnerElevation(innercalculate.lat, innercalculate.lon), innercalculate.lat0, innercalculate.lon0, innercalculate.ele0, lat2, lon2,
                        ele2);
            } else {
                return calculateElevationBasedOnTwoPoints(new InnerElevation(innercalculate.lat, innercalculate.lon), innercalculate.lat0, innercalculate.lon0, innercalculate.ele0, lat1, lon1,
                        ele1);
            }

        } else {
            double d = a * innercalculate.lat0 + b * innercalculate.lon0 + c * innercalculate.ele0;
            double ele = (d - a * innercalculate.lat - b * innercalculate.lon) / c;
            return round2(ele);
        }
    }

    public double calculateElevationBasedOnPointList(double lat, double lon, PointList pointList) {
        // See http://math.stackexchange.com/a/1930758/140512 for the
        // explanation
        final int size = pointList.size();
        if (size == 0) {
            throw new IllegalArgumentException("At least one point is required in the pointList.");
        } else if (size == 1) {
            return pointList.getEle(0);
        } else if (size == 2) {
            return calculateElevationBasedOnTwoPoints(new InnerElevation(lat, lon), pointList.getLat(0),
                    pointList.getLon(0), pointList.getEle(0), pointList.getLat(1),
                    pointList.getLon(1), pointList.getEle(1));
        } else if (size == 3) {
            return calculateElevationBasedOnThreePoints(new InnerCalculate(lat, lon, pointList.getLat(0),
                            pointList.getLon(0), pointList.getEle(0)), pointList.getLat(1),
                    pointList.getLon(1), pointList.getEle(1), pointList.getLat(2),
                    pointList.getLon(2), pointList.getEle(2));
        } else {
            double[] vs = new double[size];
            double[] eles = new double[size];
            double v = 0;
            for (int index = 0; index < size; index++) {
                double lati = pointList.getLat(index);
                double loni = pointList.getLon(index);
                double dlati = lati - lat;
                double dloni = loni - lon;
                double l2 = (dlati * dlati + dloni * dloni);
                eles[index] = pointList.getEle(index);
                if (l2 < EPSILON2) {
                    return eles[index];
                }
                vs[index] = 1 / l2;
                v += vs[index];
            }

            double ele = 0;
            if (v == 0) {
                // Handle the case where v is zero
                throw new IllegalArgumentException("v cannot be zero");
            }
            
            for (int index = 0; index < size; index++) {
                ele += eles[index] * vs[index] / v;
            }
            
            
            return round2(ele);
        }
    }
}
