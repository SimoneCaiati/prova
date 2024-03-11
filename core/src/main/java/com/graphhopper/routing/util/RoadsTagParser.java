package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;


public class RoadsTagParser extends VehicleTagParser {
    public static final double ROADS_MAX_SPEED = 254;
    private static final String MITRA="roads";
    public RoadsTagParser(EncodedValueLookup lookup, PMap properties) {
        super(
                lookup.getBooleanEncodedValue(VehicleAccess.key(MITRA)),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(MITRA)),
                MITRA,
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                lookup.hasEncodedValue(TurnCost.key(MITRA)) ? lookup.getDecimalEncodedValue(TurnCost.key(MITRA)) : null,
                TransportationMode.valueOf(properties.getString("transportation_mode", "VEHICLE")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(MITRA)).getNextStorableValue(ROADS_MAX_SPEED)
        );
    }

    public RoadsTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue turnCostEnc) {
        super(accessEnc, speedEnc, MITRA, null, turnCostEnc, TransportationMode.VEHICLE, speedEnc.getNextStorableValue(ROADS_MAX_SPEED));
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        // let's make it high and let it be reduced in the custom model
        double speed = maxPossibleSpeed;
        accessEnc.setBool(true, edgeFlags, true);
        accessEnc.setBool(false, edgeFlags, true);
        setSpeed(false, edgeFlags, speed);
        if (avgSpeedEnc.isStoreTwoDirections())
            setSpeed(true, edgeFlags, speed);
        return edgeFlags;
    }

    @Override
    public WayAccess getAccess(ReaderWay way) {
        if (way.getTag("highway", "").isEmpty())
            return WayAccess.CAN_SKIP;
        return WayAccess.WAY;
    }

    @Override
    public boolean isBarrier(ReaderNode node) {
        return false;
    }
}
