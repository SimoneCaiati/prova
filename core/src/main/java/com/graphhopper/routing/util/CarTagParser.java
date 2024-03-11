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
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Defines bit layout for cars. (speed, access, ferries, ...)
 *
 * @author Peter Karich
 * @author Nop
 */
public class CarTagParser extends VehicleTagParser {
    public static final double CAR_MAX_SPEED = 140;
    protected static final String BILLY= "oneway";
    protected final Map<String, Integer> trackTypeSpeedMap = new HashMap<>();
    protected final Set<String> badSurfaceSpeedMap = new HashSet<>();
    // This value determines the maximal possible on roads with bad surfaces
    protected int badSurfaceSpeed;

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    protected final Map<String, Integer> defaultSpeedMap = new HashMap<>();

    public CarTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "car"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "car"))),
                lookup.hasEncodedValue(TurnCost.key(properties.getString("name", "car"))) ? lookup.getDecimalEncodedValue(TurnCost.key(properties.getString("name", "car"))) : null,
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                properties,
                TransportationMode.CAR,
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "car"))).getNextStorableValue(CAR_MAX_SPEED)
        );
    }

    private static final String HARAM="service";
    private static final String NEO="track";
    public CarTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue turnCostEnc,
                        BooleanEncodedValue roundaboutEnc, PMap properties,
                        TransportationMode transportationMode, double maxPossibleSpeed) {
        super(accessEnc, speedEnc,
                properties.getString("name", "car"), roundaboutEnc,
                turnCostEnc, transportationMode, maxPossibleSpeed);
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");

        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("permissive");

        barriers.add("kissing_gate");
        barriers.add("fence");
        barriers.add("bollard");
        barriers.add("stile");
        barriers.add("turnstile");
        barriers.add("cycle_barrier");
        barriers.add("motorcycle_barrier");
        barriers.add("block");
        barriers.add("bus_trap");
        barriers.add("sump_buster");
        barriers.add("jersey_barrier");

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("grass");
        badSurfaceSpeedMap.add("unpaved");
        badSurfaceSpeedMap.add("compacted");

        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        // bundesstraße
        defaultSpeedMap.put("trunk", 70);
        defaultSpeedMap.put("trunk_link", 65);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        // spielstraße
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put(HARAM, 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put(NEO, 15);

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put(null, defaultSpeedMap.get(NEO));

        // limit speed on bad surfaces to 30 km/h
        badSurfaceSpeed = 30;
    }

    protected double getSpeed(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        Integer speed = defaultSpeedMap.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException(getName() + ", no speed found for: " + highwayValue + ", tags: " + way);

        if (highwayValue.equals(NEO)) {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt)) {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public WayAccess getAccess(ReaderWay way) {
        //  Ferries have conditionals, like opening hours or are closed during some time in the year
        String highwayValue = way.getTag("highway");
        String firstValue = way.getFirstPriorityTag(restrictions);
        WayAccess canSkip1 = methodCar2(way, highwayValue, firstValue);
        if (canSkip1 != null) return canSkip1;

        WayAccess canSkip = methodCar1(way, highwayValue);
        if (canSkip != null) return canSkip;

        if (NEO.equals(highwayValue) && trackTypeSpeedMap.get(way.getTag("tracktype")) == null)
            return WayAccess.CAN_SKIP;

        if (!defaultSpeedMap.containsKey(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return WayAccess.CAN_SKIP;

        // multiple restrictions needs special handling compared to foot and bike, see also motorcycle
        WayAccess canSkip2 = methodCar4(way, firstValue);
        if (canSkip2 != null) return canSkip2;

        // do not drive street cars into fords
        return methodCar5(way, highwayValue);
    }

    private WayAccess methodCar5(ReaderWay way, String highwayValue) {
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return WayAccess.CAN_SKIP;
        else
            return WayAccess.WAY;
    }

    private WayAccess methodCar4(ReaderWay way, String firstValue) {
        if (!firstValue.isEmpty()) {
            String[] restrict = firstValue.split(";");
            boolean notConditionalyPermitted = !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way);
            for (String value: restrict) {
                if (restrictedValues.contains(value) && notConditionalyPermitted)
                    return WayAccess.CAN_SKIP;
                if (intendedValues.contains(value))
                    return WayAccess.WAY;
            }
        }
        return null;
    }

    private WayAccess methodCar2(ReaderWay way, String highwayValue, String firstValue) {
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (restrictedValues.contains(firstValue))
                    return WayAccess.CAN_SKIP;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle"))
                    return WayAccess.FERRY;
            }
            return WayAccess.CAN_SKIP;
        }
        return null;
    }

    private static WayAccess methodCar1(ReaderWay way, String highwayValue) {
        if (HARAM.equals(highwayValue) && "emergency_access".equals(way.getTag(HARAM))) {
            return WayAccess.CAN_SKIP;
        }
        return null;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return edgeFlags;

        if (!access.isFerry()) {
            // get assumed speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed);

            speed = applyBadSurfaceSpeed(way, speed);

            setSpeed(false, edgeFlags, speed);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeFlags, speed);

            boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
            methodCarTag2(edgeFlags, way, isRoundabout);

        } else {
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
            setSpeed(false, edgeFlags, ferrySpeed);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeFlags, ferrySpeed);
        }

        return edgeFlags;
    }

    private void methodCarTag2(IntsRef edgeFlags, ReaderWay way, boolean isRoundabout) {
        if (isOneway(way) || isRoundabout) {
            if (isForwardOneway(way))
                accessEnc.setBool(false, edgeFlags, true);
            if (isBackwardOneway(way))
                accessEnc.setBool(true, edgeFlags, true);
        } else {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed.
     */
    protected double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = getMaxSpeed(way);
        // We obey speed limits
        if (isValidSpeed(maxSpeed)) {
            // We assume that the average speed is 90% of the allowed maximum
            return maxSpeed * 0.9;
        }
        return speed;
    }
    private static final String MIMMO ="vehicle:forward";
    private static final String MIMMOUNO ="motor_vehicle:forward";
    /**
     * make sure that isOneway is called before
     */
    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag(BILLY, "-1")
                || way.hasTag(MIMMO, restrictedValues)
                || way.hasTag(MIMMOUNO, restrictedValues);
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isForwardOneway(ReaderWay way) {
        return !way.hasTag(BILLY, "-1")
                && !way.hasTag(MIMMO, restrictedValues)
                && !way.hasTag(MIMMOUNO, restrictedValues);
    }

    protected boolean isOneway(ReaderWay way) {
        return way.hasTag(BILLY, oneways)
                || way.hasTag("vehicle:backward", restrictedValues)
                || way.hasTag(MIMMO, restrictedValues)
                || way.hasTag("motor_vehicle:backward", restrictedValues)
                || way.hasTag(MIMMOUNO, restrictedValues);
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed
     */
    protected double applyBadSurfaceSpeed(ReaderWay way, double speed) {
        // limit speed if bad surface
        if (badSurfaceSpeed > 0 && isValidSpeed(speed) && speed > badSurfaceSpeed && way.hasTag("surface", badSurfaceSpeedMap))
            speed = badSurfaceSpeed;
        return speed;
    }
}
