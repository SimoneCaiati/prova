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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.TransportationMode;

import static com.graphhopper.util.Helper.toLowerCase;

public class DefaultTagParserFactory implements TagParserFactory {
    @Override
    public TagParser create(EncodedValueLookup lookup, String name) {
        name = name.trim();
        if (!name.equals(toLowerCase(name)))
            throw new IllegalArgumentException("Use lower case for TagParsers: " + name);

        return quantiIf(lookup, name);
    }

    private static TagParser quantiIf(EncodedValueLookup lookup, String name) {
        switch (name) {
            case Roundabout.KEY:
                return new OSMRoundaboutParser(lookup.getBooleanEncodedValue(Roundabout.KEY));
            case RoadClass.KEY:
                return new OSMRoadClassParser(lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class));
            case RoadClassLink.KEY:
                return new OSMRoadClassLinkParser(lookup.getBooleanEncodedValue(RoadClassLink.KEY));
            case RoadEnvironment.KEY:
                return new OSMRoadEnvironmentParser(lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class));
            case RoadAccess.KEY:
                return new OSMRoadAccessParser(lookup.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class), OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR));
            case MaxSpeed.KEY:
                return new OSMMaxSpeedParser(lookup.getDecimalEncodedValue(MaxSpeed.KEY));
            case MaxWeight.KEY:
                return new OSMMaxWeightParser(lookup.getDecimalEncodedValue(MaxWeight.KEY));
            case MaxHeight.KEY:
                return new OSMMaxHeightParser(lookup.getDecimalEncodedValue(MaxHeight.KEY));
            case MaxWidth.KEY:
                return new OSMMaxWidthParser(lookup.getDecimalEncodedValue(MaxWidth.KEY));
            case MaxAxleLoad.KEY:
                return new OSMMaxAxleLoadParser(lookup.getDecimalEncodedValue(MaxAxleLoad.KEY));
            case MaxLength.KEY:
                return new OSMMaxLengthParser(lookup.getDecimalEncodedValue(MaxLength.KEY));
            case Surface.KEY:
                return new OSMSurfaceParser(lookup.getEnumEncodedValue(Surface.KEY, Surface.class));
            case Smoothness.KEY:
                return new OSMSmoothnessParser(lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class));
            case Toll.KEY:
                return new OSMTollParser(lookup.getEnumEncodedValue(Toll.KEY, Toll.class));
            case TrackType.KEY:
                return new OSMTrackTypeParser(lookup.getEnumEncodedValue(TrackType.KEY, TrackType.class));
            case Hgv.KEY:
                return new OSMHgvParser(lookup.getEnumEncodedValue(Hgv.KEY, Hgv.class));
            case Hazmat.KEY:
                return new OSMHazmatParser(lookup.getEnumEncodedValue(Hazmat.KEY, Hazmat.class));
            case HazmatTunnel.KEY:
                return new OSMHazmatTunnelParser(lookup.getEnumEncodedValue(HazmatTunnel.KEY, HazmatTunnel.class));
            case HazmatWater.KEY:
                return new OSMHazmatWaterParser(lookup.getEnumEncodedValue(HazmatWater.KEY, HazmatWater.class));
            case Lanes.KEY:
                return new OSMLanesParser(lookup.getIntEncodedValue(Lanes.KEY));
            case OSMWayID.KEY:
                return new OSMWayIDParser(lookup.getIntEncodedValue(OSMWayID.KEY));
            case MtbRating.KEY:
                return new OSMMtbRatingParser(lookup.getIntEncodedValue(MtbRating.KEY));
            case HikeRating.KEY:
                return new OSMHikeRatingParser(lookup.getIntEncodedValue(HikeRating.KEY));
            case HorseRating.KEY:
                return new OSMHorseRatingParser(lookup.getIntEncodedValue(HorseRating.KEY));
            case Footway.KEY:
                return new OSMFootwayParser(lookup.getEnumEncodedValue(Footway.KEY, Footway.class));
            case Country.KEY:
                return new CountryParser(lookup.getEnumEncodedValue(Country.KEY, Country.class));
            default:
                return null;
        }
    }

}
