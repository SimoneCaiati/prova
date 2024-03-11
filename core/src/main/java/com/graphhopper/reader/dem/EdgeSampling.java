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

import com.graphhopper.eccezionecore.threadException;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Ensures that elevation is sampled along a point list with no more than maxDistance between samples. Works by adding
 * points along long edges and fetching elevation at each inserted point.
 *
 * For short distances this uses a simple linear approximation to interpolate between points and for longer distances it
 * uses great circle interpolation.
 */
public class EdgeSampling {
    private static final double GREAT_CIRCLE_SEGMENT_LENGTH = DistanceCalcEarth.METERS_PER_DEGREE / 4;

    private EdgeSampling() {}

    public static PointList sample(PointList input, double maxDistance, DistanceCalc distCalc, ElevationProvider elevation) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        PointList output = new PointList(input.size() * 2, input.is3D());
        if (input.isEmpty()) return output;
        int nodes = input.size();
        double[] lat = new double[4];
        lat[0] = input.getLat(0);
        lat[1] = input.getLon(0);
        double lastEle = input.getEle(0);
        double thisEle;
        for (int i = 0; i < nodes; i++) {
            lat[2] = input.getLat(i);
            lat[3] = input.getLon(i);
            thisEle = input.getEle(i);
            if (i > 0) {
                double segmentLength = distCalc.calcDist3D(lat[0], lat[1], lastEle, lat[2], lat[3], thisEle);
                int segments = (int) Math.round(segmentLength / maxDistance);
                // for small distances, we use a simple and fast approximation to interpolate between points
                // for longer distances (or when crossing international date line) we use great circle interpolation
                boolean exact = segmentLength > GREAT_CIRCLE_SEGMENT_LENGTH || distCalc.isCrossBoundary(lat[1], lat[3]);
                for (int segment = 1; segment < segments; segment++) {
                    double ratio = (double) segment / segments;
                    methodM2(distCalc, elevation, output,lat, exact, ratio);
                }
            }
            output.add(lat[2], lat[3], thisEle);
            lat[0] = lat[2];
            lat[1] = lat[3];
            lastEle = thisEle;
        }
        return output;
    }

    private static void methodM2(DistanceCalc distCalc, ElevationProvider elevation, PointList output,double[] lat, boolean exact, double ratio) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        double latte;
        double lon;
        if (exact) {
            GHPoint point = distCalc.intermediatePoint(ratio, lat[0], lat[1], lat[2], lat[3]);
            latte = point.getLat();
            lon = point.getLon();
        } else {
            latte = lat[0] + (lat[2] - lat[0]) * ratio;
            lon = lat[1] + (lat[3] - lat[1]) * ratio;
        }
        double ele = elevation.getEle(latte, lon);
        methodES1(output, latte, lon, ele);
    }

    private static void methodES1(PointList output, double lat, double lon, double ele) {
        if (!Double.isNaN(ele)) {
            output.add(lat, lon, ele);
        }
    }
}
