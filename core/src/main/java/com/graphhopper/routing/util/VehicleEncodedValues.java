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

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

import java.util.Arrays;
import java.util.List;


import static com.graphhopper.routing.util.VehicleEncodedValuesFactory.*;

@SuppressWarnings("java:S2386")
public class VehicleEncodedValues {

    public static final List<String> OUTDOOR_VEHICLES = Arrays.asList(BIKE, RACINGBIKE, MOUNTAINBIKE, FOOT, HIKE, WHEELCHAIR);

    private final String name;
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue avgSpeedEnc;
    private final DecimalEncodedValue priorityEnc;
    private final DecimalEncodedValue turnCostEnc;
    private static final String MIMMO="max_turn_costs";
    private static final String MIRANDA="turn_costs";
    private static final String OTRA="speed_bits";
    private static final String ENIO="speed_factor";
    private static final String SERIO="speed_two_directions";
    private static final String NOME="name";

    public static VehicleEncodedValues foot(PMap properties) {
        String name = properties.getString(NOME, "foot");
        int speedBits = properties.getInt(OTRA, 4);
        double speedFactor = properties.getDouble(ENIO, 1);
        boolean speedTwoDirections = properties.getBool(SERIO, false);
        int maxTurnCosts = properties.getInt(MIMMO, properties.getBool(MIRANDA, false) ? 1 : 0);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = VehiclePriority.create(name, 4, PriorityCode.getFactor(1), false);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, turnCostEnc);
    }

    public static VehicleEncodedValues hike(PMap properties) {
        return foot(new PMap(properties).putObject(NOME, properties.getString(NOME, "hike")));
    }

    public static VehicleEncodedValues wheelchair(PMap properties) {
        if (properties.has(SERIO))
            throw new IllegalArgumentException("wheelchair always uses two directions");
        return foot(new PMap(properties)
                .putObject(NOME, properties.getString(NOME, "wheelchair"))
                .putObject(SERIO, true)
        );
    }

    public static VehicleEncodedValues bike(PMap properties) {
        String name = properties.getString(NOME, "bike");
        int speedBits = properties.getInt(OTRA, 4);
        double speedFactor = properties.getDouble(ENIO, 2);
        boolean speedTwoDirections = properties.getBool(SERIO, false);
        int maxTurnCosts = properties.getInt(MIMMO, properties.getBool(MIRANDA, false) ? 1 : 0);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = VehiclePriority.create(name, 4, PriorityCode.getFactor(1), false);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, turnCostEnc);
    }

    public static VehicleEncodedValues racingbike(PMap properties) {
        return bike(new PMap(properties).putObject(NOME, properties.getString(NOME, "racingbike")));
    }

    public static VehicleEncodedValues mountainbike(PMap properties) {
        return bike(new PMap(properties).putObject(NOME, properties.getString(NOME, "mtb")));
    }

    public static VehicleEncodedValues car(PMap properties) {
        String name = properties.getString(NOME, "car");
        int speedBits = properties.getInt(OTRA, 5);
        double speedFactor = properties.getDouble(ENIO, 5);
        boolean speedTwoDirections = properties.getBool(SERIO, false);
        int maxTurnCosts = properties.getInt(MIMMO, properties.getBool(MIRANDA, false) ? 1 : 0);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, null, turnCostEnc);
    }

    public static VehicleEncodedValues motorcycle(PMap properties) {
        String name = properties.getString(NOME, "motorcycle");
        int speedBits = properties.getInt(OTRA, 5);
        double speedFactor = properties.getDouble(ENIO, 5);
        boolean speedTwoDirections = properties.getBool(SERIO, true);
        int maxTurnCosts = properties.getInt(MIMMO, properties.getBool(MIRANDA, false) ? 1 : 0);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = VehiclePriority.create(name, 4, PriorityCode.getFactor(1), false);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, turnCostEnc);
    }

    public static VehicleEncodedValues roads(PMap properties) {
        String name = properties.getString(NOME, "roads");
        int speedBits = properties.getInt(OTRA, 7);
        double speedFactor = properties.getDouble(ENIO, 2);
        boolean speedTwoDirections = properties.getBool(SERIO, true);
        int maxTurnCosts = properties.getInt(MIMMO, properties.getBool(MIRANDA, true) ? 1 : 0);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, null, turnCostEnc);
    }

    public VehicleEncodedValues(String name, BooleanEncodedValue accessEnc, DecimalEncodedValue avgSpeedEnc,
                                DecimalEncodedValue priorityEnc, DecimalEncodedValue turnCostEnc) {
        this.name = name;
        this.accessEnc = accessEnc;
        this.avgSpeedEnc = avgSpeedEnc;
        this.priorityEnc = priorityEnc;
        this.turnCostEnc = turnCostEnc;
    }

    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue) {
        if (accessEnc != null)
            registerNewEncodedValue.add(accessEnc);
        if (avgSpeedEnc != null)
            registerNewEncodedValue.add(avgSpeedEnc);
        if (priorityEnc != null)
            registerNewEncodedValue.add(priorityEnc);
    }

    public void createTurnCostEncodedValues(List<EncodedValue> registerNewTurnCostEncodedValues) {
        if (turnCostEnc != null)
            registerNewTurnCostEncodedValues.add(turnCostEnc);
    }

    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    public DecimalEncodedValue getAverageSpeedEnc() {
        return avgSpeedEnc;
    }

    public DecimalEncodedValue getPriorityEnc() {
        return priorityEnc;
    }

    public DecimalEncodedValue getTurnCostEnc() {
        return turnCostEnc;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}