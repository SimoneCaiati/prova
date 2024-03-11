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

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout of bicycles (not motorcycles) for speed, access and relations (network).
 *
 * @author Peter Karich
 * @author Nop
 * @author ratrun
 */

@SuppressWarnings("java:S1640")
public abstract class BikeCommonTagParser extends VehicleTagParser {

    public static final double MAX_SPEED = 30;
    private static final String CAMION= "track";
    private static final String UFFICIALE="official";
    private static final String MOTORINO="motorway_link";
    private static final String CICLO="cycleway";
    private static final String MOTORE="motorway";
    private static final String GINO="segregated";
    private static final String YES="yes";
    public static final String DESIGNATO = "designated"
;
    private static final String BICICLETTA ="bicycle";
    protected static final int PUSHING_SECTION_SPEED = 4;
    protected static final  String SERVIZIETTO= "service";
    protected static final int MIN_SPEED = 2;
    // Pushing section highways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    protected final HashSet<String> oppositeLanes = new HashSet<>();
    protected final Set<String> preferHighwayTags = new HashSet<>();
    protected final Set<String> avoidHighwayTags = new HashSet<>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<>();
    private final Map<String, Integer> trackTypeSpeeds = new HashMap<>();
    private final Map<String, Integer> surfaceSpeeds = new HashMap<>();
    private final Map<Smoothness, Double> smoothnessFactor = new HashMap<>();
    private final Map<String, Integer> highwaySpeeds = new HashMap<>();
    protected final DecimalEncodedValue priorityEnc;
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    private int avoidSpeedLimit;
    EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    EnumEncodedValue<Smoothness> smoothnessEnc;
    Map<RouteNetwork, Integer> routeMap = new EnumMap<>(RouteNetwork.class);


    // This is the specific bicycle class
    private String classBicycleKey;
    private static final String BRI="bridleway";
    private static final String LEL="steps";

    protected BikeCommonTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                                  EnumEncodedValue<RouteNetwork> bikeRouteEnc, EnumEncodedValue<Smoothness> smoothnessEnc,
                                  String name, BooleanEncodedValue roundaboutEnc, DecimalEncodedValue turnCostEnc) {
        super(accessEnc, speedEnc, name, roundaboutEnc, turnCostEnc, TransportationMode.BIKE, speedEnc.getNextStorableValue(MAX_SPEED));
        this.bikeRouteEnc = bikeRouteEnc;
        this.smoothnessEnc = smoothnessEnc;
        this.priorityEnc = priorityEnc;

        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");

        intendedValues.add(YES);
        intendedValues.add(DESIGNATO);
        intendedValues.add(UFFICIALE);
        intendedValues.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");

        barriers.add("fence");

        unpavedSurfaceTags.add("unpaved");
        unpavedSurfaceTags.add("gravel");
        unpavedSurfaceTags.add("ground");
        unpavedSurfaceTags.add("dirt");
        unpavedSurfaceTags.add("grass");
        unpavedSurfaceTags.add("compacted");
        unpavedSurfaceTags.add("earth");
        unpavedSurfaceTags.add("fine_gravel");
        unpavedSurfaceTags.add("grass_paver");
        unpavedSurfaceTags.add("ice");
        unpavedSurfaceTags.add("mud");
        unpavedSurfaceTags.add("salt");
        unpavedSurfaceTags.add("sand");
        unpavedSurfaceTags.add("wood");

        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 12); // now unpaved ...
        setTrackTypeSpeed("grade3", 8);
        setTrackTypeSpeed("grade4", 6);
        setTrackTypeSpeed("grade5", 4); // like sand/grass     

        setSurfaceSpeed("paved", 18);
        setSurfaceSpeed("asphalt", 18);
        setSurfaceSpeed("cobblestone", 8);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett", 10);
        setSurfaceSpeed("concrete", 18);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 14);
        setSurfaceSpeed("paving_stones:30", 14);
        setSurfaceSpeed("unpaved", 12);
        setSurfaceSpeed("compacted", 14);
        setSurfaceSpeed("dirt", 10);
        setSurfaceSpeed("earth", 12);
        setSurfaceSpeed("fine_gravel", 18);
        setSurfaceSpeed("grass", 8);
        setSurfaceSpeed("grass_paver", 8);
        setSurfaceSpeed("gravel", 12);
        setSurfaceSpeed("ground", 12);
        setSurfaceSpeed("ice", MIN_SPEED);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 10);
        setSurfaceSpeed("pebblestone", 14);
        setSurfaceSpeed("salt", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("sand", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("wood", PUSHING_SECTION_SPEED);

        setHighwaySpeed("living_street", PUSHING_SECTION_SPEED);
        setHighwaySpeed(LEL, MIN_SPEED);
        avoidHighwayTags.add(LEL);

        final int CYCLEWAY_SPEED = 18;  // Make sure cycleway and path use same speed value, see #634
        setHighwaySpeed(CICLO, CYCLEWAY_SPEED);
        setHighwaySpeed("path", 10);
        setHighwaySpeed("footway", 6);
        setHighwaySpeed("platform", PUSHING_SECTION_SPEED);
        setHighwaySpeed("pedestrian", PUSHING_SECTION_SPEED);
        setHighwaySpeed(CAMION, 12);
        setHighwaySpeed(SERVIZIETTO, 14);
        setHighwaySpeed("residential", 18);
        // no other highway applies:
        setHighwaySpeed("unclassified", 16);
        // unknown road:
        setHighwaySpeed("road", 12);

        setHighwaySpeed("trunk", 18);
        setHighwaySpeed("trunk_link", 18);
        setHighwaySpeed("primary", 18);
        setHighwaySpeed("primary_link", 18);
        setHighwaySpeed("secondary", 18);
        setHighwaySpeed("secondary_link", 18);
        setHighwaySpeed("tertiary", 18);
        setHighwaySpeed("tertiary_link", 18);

        // special case see tests and #191
        setHighwaySpeed(MOTORE, 18);
        setHighwaySpeed(MOTORINO, 18);
        avoidHighwayTags.add(MOTORE);
        avoidHighwayTags.add(MOTORINO);

        setHighwaySpeed(BRI, PUSHING_SECTION_SPEED);
        avoidHighwayTags.add(BRI);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, PREFER.getValue());

        // note that this factor reduces the speed but only until MIN_SPEED
        setSmoothnessSpeedFactor(Smoothness.MISSING, 1.0d);
        setSmoothnessSpeedFactor(Smoothness.OTHER, 0.7d);
        setSmoothnessSpeedFactor(Smoothness.EXCELLENT, 1.1d);
        setSmoothnessSpeedFactor(Smoothness.GOOD, 1.0d);
        setSmoothnessSpeedFactor(Smoothness.INTERMEDIATE, 0.9d);
        setSmoothnessSpeedFactor(Smoothness.BAD, 0.7d);
        setSmoothnessSpeedFactor(Smoothness.VERY_BAD, 0.4d);
        setSmoothnessSpeedFactor(Smoothness.HORRIBLE, 0.3d);
        setSmoothnessSpeedFactor(Smoothness.VERY_HORRIBLE, 0.1d);
        setSmoothnessSpeedFactor(Smoothness.IMPASSABLE, 0);

        setAvoidSpeedLimit(71);
    }

    private static final String HIGH="highway";
    @Override
    public WayAccess getAccess(ReaderWay way) {
        String highwayValue = way.getTag(HIGH);
        if (highwayValue == null) {
            WayAccess access = WayAccess.CAN_SKIP;

            access = bici1(way, access);

            // special case not for all acceptedRailways, only platform
            access = bici4(way, access);

            access = bici3(way, access);

            WayAccess canSkip = okBro2(way, access);
            if (canSkip != null) return canSkip;

            return WayAccess.CAN_SKIP;
        }

        if (!highwaySpeeds.containsKey(highwayValue))
            return WayAccess.CAN_SKIP;

        String sacScale = way.getTag("sac_scale");
        WayAccess canSkip1 = okBro(sacScale);
        if (canSkip1 != null) return canSkip1;

        // use the way if it is tagged for bikes
        if (bici6(way))
            return WayAccess.WAY;

        // accept only if explicitly tagged for bike usage
        if (MOTORE.equals(highwayValue) || MOTORINO.equals(highwayValue) || BRI.equals(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("motorroad", YES))
            return WayAccess.CAN_SKIP;

        // do not use fords with normal bikes, flagged fords are in included above
        if (isBlockFords() && (way.hasTag(HIGH, "ford") || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        // check access restrictions
        boolean notRestrictedWayConditionallyPermitted = !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way);
        WayAccess canSkip = questaDura(way, notRestrictedWayConditionallyPermitted);
        if (canSkip != null) return canSkip;

        return abbiamoFinito(way);
    }

    private WayAccess okBro2(ReaderWay way, WayAccess access) {
        if (!access.canSkip()) {
            if (bici2(way))
                return WayAccess.CAN_SKIP;
            return access;
        }
        return null;
    }

    private WayAccess okBro(String sacScale) {
        if (sacScale != null && bici5(sacScale)) {
            return WayAccess.CAN_SKIP;
        }

        return null;
    }

    private WayAccess abbiamoFinito(ReaderWay way) {
        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return WayAccess.CAN_SKIP;
        else
            return WayAccess.WAY;
    }

    private WayAccess questaDura(ReaderWay way, boolean notRestrictedWayConditionallyPermitted) {
        for (String restriction: restrictions ) {
            String complexAccess = way.getTag(restriction);
            if (complexAccess != null) {
               String[] simpleAccess = complexAccess.split(";");
               for (String access: simpleAccess) {
                  if (restrictedValues.contains(access) && notRestrictedWayConditionallyPermitted)
                      return WayAccess.CAN_SKIP;
               }
            }
        }
        return null;
    }

    private boolean bici6(ReaderWay way) {
        return way.hasTag(BICICLETTA, intendedValues) ||
                way.hasTag(BICICLETTA, "dismount") ||
                way.hasTag(HIGH, CICLO);
    }

    private boolean bici5(String sacScale) {
        return !isSacScaleAllowed(sacScale);
    }

    private static WayAccess bici4(ReaderWay way, WayAccess access) {
        if (way.hasTag("railway", "platform"))
            access = WayAccess.WAY;
        return access;
    }

    private static WayAccess bici3(ReaderWay way, WayAccess access) {
        if (way.hasTag("man_made", "pier"))
            access = WayAccess.WAY;
        return access;
    }

    private boolean bici2(ReaderWay way) {
        return way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way);
    }

    private WayAccess bici1(ReaderWay way, WayAccess access) {
        if (way.hasTag("route", ferries)) {
            // if bike is NOT explicitly tagged allow bike but only if foot is not specified either
            String bikeTag = way.getTag(BICICLETTA);
            if (bikeTag == null && !way.hasTag("foot") || intendedValues.contains(bikeTag))
                access = WayAccess.FERRY;
        }
        return access;
    }

    boolean isSacScaleAllowed(String sacScale) {
        // other scales are nearly impossible by an ordinary bike, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        return "hiking".equals(sacScale);
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed average speed.
     */
    protected double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = getMaxSpeed(way);
        // We strictly obey speed limits, see #600
        if (isValidSpeed(maxSpeed) && speed > maxSpeed) {
            return maxSpeed;
        }
        if (isValidSpeed(speed) && speed > maxPossibleSpeed)
            return maxPossibleSpeed;
        return speed;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return edgeFlags;

        Integer priorityFromRelation = routeMap.get(bikeRouteEnc.getEnum(false, edgeFlags));
        double wayTypeSpeed = getSpeed(way);
        if (!access.isFerry()) {
            wayTypeSpeed = applyMaxSpeed(way, wayTypeSpeed);
            Smoothness smoothness = smoothnessEnc.getEnum(false, edgeFlags);
            wayTypeSpeed = Math.max(MIN_SPEED, smoothnessFactor.get(smoothness) * wayTypeSpeed);

            avgSpeedEnc.setDecimal(false, edgeFlags, wayTypeSpeed);
            if (avgSpeedEnc.isStoreTwoDirections())
                avgSpeedEnc.setDecimal(true, edgeFlags, wayTypeSpeed);
            handleAccess(edgeFlags, way);
        } else {
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            avgSpeedEnc.setDecimal(false, edgeFlags, ferrySpeed);
            if (avgSpeedEnc.isStoreTwoDirections())
                avgSpeedEnc.setDecimal(true, edgeFlags, ferrySpeed);
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
            priorityFromRelation = SLIGHT_AVOID.getValue();
        }

        priorityEnc.setDecimal(false, edgeFlags, PriorityCode.getValue(handlePriority(way, wayTypeSpeed, priorityFromRelation)));
        return edgeFlags;
    }

    int getSpeed(ReaderWay way) {
        int speed = PUSHING_SECTION_SPEED;
        String highwayTag = way.getTag(HIGH);
        Integer highwaySpeed = highwaySpeeds.get(highwayTag);

        // Under certain conditions we need to increase the speed of pushing sections to the speed of a "highway=cycleway"
        highwaySpeed = mamma(way, highwaySpeed);

        String s = way.getTag("surface");
        Integer surfaceSpeed = 0;
        if (!Helper.isEmpty(s)) {
            surfaceSpeed = surfaceSpeeds.get(s);
            speed = mammauno(speed, highwayTag, highwaySpeed, surfaceSpeed);
        } else {
            String tt = way.getTag("tracktype");
            speed = mammadue(way, speed, highwaySpeed, tt);
        }

        // Until now we assumed that the way is no pushing section
        // Now we check that, but only in case that our speed computed so far is bigger compared to the PUSHING_SECTION_SPEED
        speed = mammina(way, speed, surfaceSpeed);
        return speed;
    }

    private int mammina(ReaderWay way, int speed, Integer surfaceSpeed) {
        if (speed > PUSHING_SECTION_SPEED
                && (way.hasTag(HIGH, pushingSectionsHighways) || way.hasTag(BICICLETTA, "dismount"))) {
            if (!way.hasTag(BICICLETTA, intendedValues)) {
                // Here we set the speed for pushing sections and set speed for steps as even lower:
                speed = way.hasTag(HIGH, LEL) ? MIN_SPEED : PUSHING_SECTION_SPEED;
            } else if (way.hasTag(BICICLETTA, DESIGNATO) || way.hasTag(BICICLETTA, UFFICIALE) ||
                    way.hasTag(GINO, YES) || way.hasTag(BICICLETTA, YES)) {
                // Here we handle the cases where the OSM tagging results in something similar to "highway=cycleway"
                speed = mammaaa(way);

                // valid surface speed?
                speed = mammaca(speed, surfaceSpeed);
            }
        }
        return speed;
    }

    private static int mammaca(int speed, Integer surfaceSpeed) {
        if (surfaceSpeed > 0)
            speed = Math.min(speed, surfaceSpeed);
        return speed;
    }

    private int mammaaa(ReaderWay way) {
        int speed;
        if (way.hasTag(GINO, YES))
            speed = highwaySpeeds.get(CICLO);
        else
            speed = way.hasTag(BICICLETTA, YES) ? 10 : highwaySpeeds.get(CICLO);
        return speed;
    }

    private int mammadue(ReaderWay way, int speed, Integer highwaySpeed, String tt) {
        if (!Helper.isEmpty(tt)) {
            Integer tInt = trackTypeSpeeds.get(tt);
            if (tInt != null)
                speed = tInt;
        } else if (highwaySpeed != null) {
            if (!way.hasTag(SERVIZIETTO))
                speed = highwaySpeed;
            else
                speed = highwaySpeeds.get("living_street");
        }
        return speed;
    }

    private int mammauno(int speed, String highwayTag, Integer highwaySpeed, Integer surfaceSpeed) {
        if (surfaceSpeed != null) {
            speed = surfaceSpeed;
            // boost handling for good surfaces but avoid boosting if pushing section
            if (highwaySpeed != null && surfaceSpeed > highwaySpeed && pushingSectionsHighways.contains(highwayTag)) {
                speed = highwaySpeed;
            }

        }
        return speed;
    }

    private Integer mamma(ReaderWay way, Integer highwaySpeed) {
        if (way.hasTag(HIGH, pushingSectionsHighways)
                && ((way.hasTag("foot", YES) && way.hasTag(GINO, YES))
                || (way.hasTag(BICICLETTA, intendedValues))))
            highwaySpeed = getHighwaySpeed(CICLO);
        return highwaySpeed;
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and avoid big roads
     * or roads with trams or pedestrian.
     *
     * @return new priority based on priorityFromRelation and on the tags in ReaderWay.
     */
    int handlePriority(ReaderWay way, double wayTypeSpeed, Integer priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();
        if (priorityFromRelation == null)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        collect(way, wayTypeSpeed, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private PriorityCode convertClassValueToPriority(String tagvalue) {
        int classvalue;
        try {
            classvalue = Integer.parseInt(tagvalue);
        } catch (NumberFormatException e) {
            return UNCHANGED;
        }

        switch (classvalue) {
            case 3:
                return BEST;
            case 2:
                return VERY_NICE;
            case 1:
                return PREFER;
            case 0:
                return UNCHANGED;
            case -1:
                return SLIGHT_AVOID;
            case -2:
                return AVOID;
            case -3:
                return AVOID_MORE;
            default:
                return UNCHANGED;
        }
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, Integer> weightToPrioMap) {
        String service = way.getTag(SERVIZIETTO);
        String highway = way.getTag(HIGH);
        miootto(way, weightToPrioMap, highway);

        miosette(way, weightToPrioMap, highway);

        double maxSpeed = getMaxSpeed(way);
        miosei(way, weightToPrioMap, highway, maxSpeed);

        String cycleway = way.getFirstPriorityTag(Arrays.asList(CICLO, "cycleway:left", "cycleway:right"));
        miocinque(way, weightToPrioMap, service, highway, cycleway);

        miotre(way, weightToPrioMap);

        String classBicycleValue = way.getTag(classBicycleKey);
        miouno(way, weightToPrioMap, classBicycleValue);

        // Increase the priority for scenic routes or in case that maxspeed limits our average speed as compensation. See #630
        mio(way, wayTypeSpeed, weightToPrioMap, maxSpeed);
    }

    private static void miootto(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap, String highway) {
        if (way.hasTag(BICICLETTA, DESIGNATO) || way.hasTag(BICICLETTA, UFFICIALE)) {
            if ("path".equals(highway))
                weightToPrioMap.put(100d, VERY_NICE.getValue());
            else
                weightToPrioMap.put(100d, PREFER.getValue());
        }
    }

    private void miosette(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap, String highway) {
        if (CICLO.equals(highway)) {
            if (way.hasTag("foot", intendedValues) && !way.hasTag(GINO, YES))
                weightToPrioMap.put(100d, PREFER.getValue());
            else
                weightToPrioMap.put(100d, VERY_NICE.getValue());
        }
    }

    private void miosei(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap, String highway, double maxSpeed) {
        if (preferHighwayTags.contains(highway) || (isValidSpeed(maxSpeed) && maxSpeed <= 30)) {
            if (!isValidSpeed(maxSpeed) || maxSpeed < avoidSpeedLimit) {
                weightToPrioMap.put(40d, PREFER.getValue());
                if (way.hasTag("tunnel", intendedValues))
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if (avoidHighwayTags.contains(highway)
                || isValidSpeed(maxSpeed) && maxSpeed >= avoidSpeedLimit && !CAMION.equals(highway)) {
            weightToPrioMap.put(50d, AVOID.getValue());
            if (way.hasTag("tunnel", intendedValues) || way.hasTag("hazmat", intendedValues))
                weightToPrioMap.put(50d, BAD.getValue());
        }
    }

    private void miocinque(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap, String service, String highway, String cycleway) {
        if (Arrays.asList("lane", "shared_lane", "share_busway", "shoulder").contains(cycleway)) {
            weightToPrioMap.put(100d, UNCHANGED.getValue());
        } else if (CAMION.equals(cycleway)) {
            weightToPrioMap.put(100d, PREFER.getValue());
        }

        if (way.hasTag(BICICLETTA, "use_sidepath")) {
            weightToPrioMap.put(100d, REACH_DESTINATION.getValue());
        }

        if (pushingSectionsHighways.contains(highway)
                || "parking_aisle".equals(service)) {
            int pushingSectionPrio = SLIGHT_AVOID.getValue();
            pushingSectionPrio = mioquattro(way, pushingSectionPrio);
            weightToPrioMap.put(100d, pushingSectionPrio);
        }
    }

    private static int mioquattro(ReaderWay way, int pushingSectionPrio) {
        if (way.hasTag(BICICLETTA, YES) || way.hasTag(BICICLETTA, "permissive"))
            pushingSectionPrio = PREFER.getValue();
        if (way.hasTag(BICICLETTA, DESIGNATO) || way.hasTag(BICICLETTA, UFFICIALE))
            pushingSectionPrio = VERY_NICE.getValue();
        if (way.hasTag("foot", YES)) {
            pushingSectionPrio = Math.max(pushingSectionPrio - 1, BAD.getValue());
            if (way.hasTag(GINO, YES))
                pushingSectionPrio = Math.min(pushingSectionPrio + 1, BEST.getValue());
        }
        return pushingSectionPrio;
    }

    private static void miotre(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap) {
        if (way.hasTag("railway", "tram"))
            weightToPrioMap.put(50d, AVOID_MORE.getValue());

        if (way.hasTag("lcn", YES))
            weightToPrioMap.put(100d, PREFER.getValue());
    }

    private void miouno(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap, String classBicycleValue) {
        if (classBicycleValue != null) {
            // We assume that humans are better in classifying preferences compared to our algorithm above -> weight = 100
            weightToPrioMap.put(100d, convertClassValueToPriority(classBicycleValue).getValue());
        } else {
            String classBicycle = way.getTag("class:bicycle");
            if (classBicycle != null)
                weightToPrioMap.put(100d, convertClassValueToPriority(classBicycle).getValue());
        }
    }

    private static void mio(ReaderWay way, double wayTypeSpeed, TreeMap<Double, Integer> weightToPrioMap, double maxSpeed) {
        if (way.hasTag("scenic", YES) || maxSpeed > 0 && maxSpeed < wayTypeSpeed) {
            if (weightToPrioMap.lastEntry().getValue() < BEST.getValue())
                // Increase the prio by one step
                weightToPrioMap.put(110d, weightToPrioMap.lastEntry().getValue() + 1);
        }
    }
    private static final String MERLO="bicycle:forward";
    private static final String MIMMO="bicycle:backward";
    private static final String RINO="oneway";
    private static final String MELLO="cycleway:right:oneway";
    private static final String SERIO="cycleway:left:oneway";
    private static final String KLOA="oneway:bicycle";
    protected void handleAccess(IntsRef edgeFlags, ReaderWay way) {
        // handle oneways. The value -1 means it is a oneway but for reverse direction of stored geometry.
        // The tagging oneway:bicycle=no or cycleway:right:oneway=no or cycleway:left:oneway=no lifts the generic oneway restriction of the way for bike
        boolean isOneway = way.hasTag(RINO, oneways) && !way.hasTag(RINO, "-1") && !way.hasTag(MIMMO, intendedValues)
                || way.hasTag(RINO, "-1") && !way.hasTag(MERLO, intendedValues)
                || way.hasTag(KLOA, oneways)
                || way.hasTag(SERIO, oneways)
                || way.hasTag(MELLO, oneways)
                || way.hasTag("vehicle:backward", restrictedValues) && !way.hasTag(MERLO, intendedValues)
                || way.hasTag("vehicle:forward", restrictedValues) && !way.hasTag(MIMMO, intendedValues)
                || way.hasTag(MERLO, restrictedValues)
                || way.hasTag(MIMMO, restrictedValues);

        if ((isOneway || roundaboutEnc.getBool(false, edgeFlags))
                && !way.hasTag(KLOA, "no")
                && !way.hasTag(CICLO, oppositeLanes)
                && !way.hasTag("cycleway:left", oppositeLanes)
                && !way.hasTag("cycleway:right", oppositeLanes)
                && !way.hasTag(SERIO, "no")
                && !way.hasTag(MELLO, "no")) {
            boolean isBackward = way.hasTag(RINO, "-1")
                    || way.hasTag(KLOA, "-1")
                    || way.hasTag(SERIO, "-1")
                    || way.hasTag(MELLO, "-1")
                    || way.hasTag("vehicle:forward", restrictedValues)
                    || way.hasTag(MERLO, restrictedValues);
            accessEnc.setBool(isBackward, edgeFlags, true);

        } else {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }
    }

    void setHighwaySpeed(String highway, int speed) {
        highwaySpeeds.put(highway, speed);
    }

    int getHighwaySpeed(String key) {
        return highwaySpeeds.get(key);
    }

    void setTrackTypeSpeed(String tracktype, int speed) {
        trackTypeSpeeds.put(tracktype, speed);
    }

    void setSurfaceSpeed(String surface, int speed) {
        surfaceSpeeds.put(surface, speed);
    }

    void setSmoothnessSpeedFactor(Smoothness smoothness, double speedfactor) {
        smoothnessFactor.put(smoothness, speedfactor);
    }

    void addPushingSection(String highway) {
        pushingSectionsHighways.add(highway);
    }

    void setAvoidSpeedLimit(int limit) {
        avoidSpeedLimit = limit;
    }

    void setSpecificClassBicycle(String subkey) {
        classBicycleKey = "class:bicycle:" + subkey;
    }
}
