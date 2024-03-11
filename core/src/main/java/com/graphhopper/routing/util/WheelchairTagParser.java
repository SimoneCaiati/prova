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
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.AVOID;
import static com.graphhopper.routing.util.PriorityCode.VERY_NICE;

/**
 * A flag encoder for wheelchairs.
 *
 * @author don-philipe
 */
public class WheelchairTagParser extends FootTagParser {
    private final Set<String> excludeSurfaces = new HashSet<>();
    public static final String CAMMINO= "sidewalk";
    private final Set<String> excludeSmoothness = new HashSet<>();
    private static final int MAX_INCLINE_PERCENT = 6;

    private static final String WHEEL="wheelchair";
    public WheelchairTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(WHEEL)),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(WHEEL)),
                lookup.getDecimalEncodedValue(VehiclePriority.key(WHEEL)),
                lookup.getEnumEncodedValue(FootNetwork.KEY, RouteNetwork.class)
        );
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected WheelchairTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                                  EnumEncodedValue<RouteNetwork> footRouteEnc) {
        super(accessEnc, speedEnc, priorityEnc, footRouteEnc, WHEEL);

        restrictions.add(WHEEL);

        barriers.add("handrail");
        barriers.add("wall");
        barriers.add("turnstile");
        barriers.add("kissing_gate");
        barriers.add("stile");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");
        safeHighwayTags.add("platform");

        safeHighwayTags.remove("steps");
        safeHighwayTags.remove("track");

        allowedHighwayTags.clear();
        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.addAll(avoidHighwayTags);
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");

        excludeSurfaces.add("cobblestone");
        excludeSurfaces.add("gravel");
        excludeSurfaces.add("sand");

        excludeSmoothness.add("bad");
        excludeSmoothness.add("very_bad");
        excludeSmoothness.add("horrible");
        excludeSmoothness.add("very_horrible");
        excludeSmoothness.add("impassable");

        allowedSacScale.clear();
    }

    /**
     * Avoid some more ways than for pedestrian like hiking trails.
     */
    @Override
    public WayAccess getAccess(ReaderWay way) {
        WayAccess canSkip = millo(way);
        if (canSkip != null) return canSkip;

        WayAccess canSkip1 = millouno(way);
        if (canSkip1 != null) return canSkip1;

        WayAccess canSkip2 = millodue(way);
        if (canSkip2 != null) return canSkip2;

        if (way.hasTag("kerb", "raised"))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("kerb")) {
            String tagValue = way.getTag("kerb");
            WayAccess canSkip3 = millotre(tagValue);
            if (canSkip3 != null) return canSkip3;
        }

        return super.getAccess(way);
    }

    private static WayAccess millotre(String tagValue) {
        if (tagValue.endsWith("cm") || tagValue.endsWith("mm")) {
            try {
                float kerbHeight = Float.parseFloat(tagValue.substring(0, tagValue.length() - 2));
                if (tagValue.endsWith("mm")) {
                    kerbHeight /= 100;
                }

                int maxKerbHeightCm = 3;
                if (kerbHeight > maxKerbHeightCm) {
                    return WayAccess.CAN_SKIP;
                }
            } catch (NumberFormatException ex) {
                //
            }
        }
        return null;
    }

    private static WayAccess millodue(ReaderWay way) {
        if (way.hasTag("incline")) {
            String tagValue = way.getTag("incline");
            if (tagValue.endsWith("%") || tagValue.endsWith("°")) {
                try {
                    double incline = Double.parseDouble(tagValue.substring(0, tagValue.length() - 1));
                    if (tagValue.endsWith("°")) {
                        incline = Math.tan(incline * Math.PI / 180) * 100;
                    }

                    if (-MAX_INCLINE_PERCENT > incline || incline > MAX_INCLINE_PERCENT) {
                        return WayAccess.CAN_SKIP;
                    }
                } catch (NumberFormatException ex) {
                    //
                }
            }
        }
        return null;
    }

    private WayAccess millouno(ReaderWay way) {
        if (way.hasTag("smoothness", excludeSmoothness)) {
            if (!way.hasTag(CAMMINO, sidewalkValues)) {
                return WayAccess.CAN_SKIP;
            } else {
                String sidewalk = way.getTag(CAMMINO);
                if (way.hasTag("sidewalk:" + sidewalk + ":smoothness", excludeSmoothness)) {
                    return WayAccess.CAN_SKIP;
                }
            }
        }
        return null;
    }

    private WayAccess millo(ReaderWay way) {
        if (way.hasTag("surface", excludeSurfaces)) {
            if (!way.hasTag(CAMMINO, sidewalkValues)) {
                return WayAccess.CAN_SKIP;
            } else {
                String sidewalk = way.getTag(CAMMINO);
                if (way.hasTag("sidewalk:" + sidewalk + ":surface", excludeSurfaces)) {
                    return WayAccess.CAN_SKIP;
                }
            }
        }
        return null;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return edgeFlags;

        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        if (!access.isFerry()) {
            setSpeed(edgeFlags, true, true, MEAN_SPEED);
        } else {
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            setSpeed(edgeFlags, true, true, ferrySpeed);
        }

        Integer priorityFromRelation = routeMap.get(footRouteEnc.getEnum(false, edgeFlags));
        priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getValue(handlePriority(way, priorityFromRelation)));

        edgeFlags = applyWayTags(way, edgeFlags);
        return edgeFlags;
    }

    /**
     * Calculate slopes from elevation data and set speed according to that. In-/declines between smallInclinePercent
     * and maxInclinePercent will reduce speed to SLOW_SPEED. In-/declines above maxInclinePercent will result in zero
     * speed.
     */
    public IntsRef applyWayTags(ReaderWay way, IntsRef edgeFlags) {
        PointList pl = way.getTag("point_list", null);
        double fullDist2D = pollo(way, pl);

        // skip elevation data adjustment for too short segments,  improve the elevation data handling and/or use the same mechanism as we used to do in bike2
        if (fullDist2D < 20 || !pl.is3D())
            return edgeFlags;

        double prevEle = pl.getEle(0);
        double eleDelta = pl.getEle(pl.size() - 1) - prevEle;
        double elePercent = eleDelta / fullDist2D * 100;
        int smallInclinePercent = 3;
        double fwdSpeed = 0;
        double bwdSpeed = 0;
        if (elePercent > smallInclinePercent && elePercent < MAX_INCLINE_PERCENT) {
            fwdSpeed = SLOW_SPEED;
            bwdSpeed = MEAN_SPEED;
        } else if (elePercent < -smallInclinePercent && elePercent > -MAX_INCLINE_PERCENT) {
            fwdSpeed = MEAN_SPEED;
            bwdSpeed = SLOW_SPEED;
        } else if (elePercent > MAX_INCLINE_PERCENT || elePercent < -MAX_INCLINE_PERCENT) {
            // it can be problematic to exclude roads due to potential bad elevation data (e.g.delta for narrow nodes could be too high)
            // so exclude only when we are certain
            pollouno(edgeFlags, fullDist2D);

            fwdSpeed = SLOW_SPEED;
            bwdSpeed = SLOW_SPEED;
            priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getValue(PriorityCode.REACH_DESTINATION.getValue()));
        }

        pollodue(edgeFlags, fwdSpeed, bwdSpeed);

        return edgeFlags;
    }

    private void pollodue(IntsRef edgeFlags, double fwdSpeed, double bwdSpeed) {
        if (fwdSpeed > 0 && accessEnc.getBool(false, edgeFlags))
            setSpeed(edgeFlags, true, false, fwdSpeed);
        if (bwdSpeed > 0 && accessEnc.getBool(true, edgeFlags))
            setSpeed(edgeFlags, false, true, bwdSpeed);
    }

    private void pollouno(IntsRef edgeFlags, double fullDist2D) {
        if (fullDist2D > 50) {
            accessEnc.setBool(false, edgeFlags, false);
            accessEnc.setBool(true, edgeFlags, false);
        }
    }

    private static double pollo(ReaderWay way, PointList pl) {
        if (pl == null)
            throw new IllegalArgumentException("The artificial point_list tag is missing");
        if (!way.hasTag("edge_distance"))
            throw new IllegalArgumentException("The artificial edge_distance tag is missing");
        double fullDist2D = way.getTag("edge_distance", 0d);
        if (Double.isInfinite(fullDist2D))
            throw new IllegalStateException("Infinite distance should not happen due to #435. way ID=" + way.getId());
        return fullDist2D;
    }

    /**
     * First get priority from {@link FootTagParser#handlePriority(ReaderWay, Integer)} then evaluate wheelchair specific
     * tags.
     *
     * @return a priority for the given way
     */
    @Override
    protected int handlePriority(ReaderWay way, Integer priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();

        weightToPrioMap.put(100d, super.handlePriority(way, priorityFromRelation));

        if (way.hasTag(WHEEL, "designated")) {
            weightToPrioMap.put(102d, VERY_NICE.getValue());
        } else if (way.hasTag(WHEEL, "limited")) {
            weightToPrioMap.put(102d, AVOID.getValue());
        }

        return weightToPrioMap.lastEntry().getValue();
    }
}
