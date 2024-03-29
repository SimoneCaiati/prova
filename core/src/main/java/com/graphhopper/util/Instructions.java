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

public class Instructions {

    private Instructions()
    {
        /*
        Bella ragazzi
         */
    }

    /**
     * This method is useful for navigation devices to find the next instruction for the specified
     * coordinate (e.g. the current position).
     * <p>
     *
     * @param instructions the instructions to query
     * @param maxDistance the maximum acceptable distance to the instruction (in meter)
     * @return the next Instruction or null if too far away.
     */
    public static Instruction find(InstructionList instructions, double lat, double lon, double maxDistance) {
        // handle special cases
        if (instructions.isEmpty()) {
            return null;
        }
        PointList points = instructions.get(0).getPoints();
        double prevLat = points.getLat(0);
        double prevLon = points.getLon(0);

        DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
        double foundMinDistance = distCalc.calcNormalizedDist(lat, lon, prevLat, prevLon);
        int foundInstruction = 0;

// Search the closest edge to the query point
        for (int instructionIndex = 0; instructionIndex < instructions.size(); instructionIndex++) {
            points = instructions.get(instructionIndex).getPoints();
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                double currLat = points.getLat(pointIndex);
                double currLon = points.getLon(pointIndex);

                if (instructionIndex == 0 && pointIndex == 0) {
                    continue;
                }

                // calculate the distance from the point to the edge
                int index = instructionIndex;
                double[] arrayA = new double[4];
                arrayA[0] = lat;
                arrayA[1] = lon;
                arrayA[2] = prevLat;
                arrayA[3] = prevLon;
                Mamma mia = getMamma(arrayA, distCalc, pointIndex, currLat, currLon, index);

                if (mia.distance < foundMinDistance) {
                    foundMinDistance = mia.distance;
                    foundInstruction = mia.index;
                }

                prevLat = currLat;
                prevLon = currLon;
            }
        }

        if (distCalc.calcDenormalizedDist(foundMinDistance) > maxDistance) {
            return null;
        }

        foundInstruction = millo(instructions, foundInstruction);
        return instructions.get(foundInstruction);
    }

    private static int millo(InstructionList instructions, int foundInstruction) {
        if (foundInstruction == instructions.size())
            foundInstruction--;
        return foundInstruction;
    }

    private static Mamma getMamma(double[] array, DistanceCalc distCalc, int pointIndex, double currLat, double currLon, int index) {
        double distance;
        if (distCalc.validEdgeDistance(array[0], array[1], currLat, currLon, array[2], array[3])) {
            distance = distCalc.calcNormalizedEdgeDistance(array[0], array[1], currLat, currLon, array[2], array[3]);
            if (pointIndex > 0)
                index++;
        } else {
            distance = distCalc.calcNormalizedDist(array[0], array[1], currLat, currLon);
            if (pointIndex > 0)
                index++;
        }
        return new Mamma(distance, index);
    }

    private static class Mamma {
        public final double distance;
        public final int index;

        public Mamma(double distance, int index) {
            this.distance = distance;
            this.index = index;
        }
    }
}
