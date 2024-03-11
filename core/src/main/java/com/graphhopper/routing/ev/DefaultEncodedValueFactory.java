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
package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public class DefaultEncodedValueFactory implements EncodedValueFactory {
    @Override
    public EncodedValue create(String string) {
        if (Helper.isEmpty(string))
            throw new IllegalArgumentException("No string provided to load EncodedValue");

        final EncodedValue enc;
        String name = string.split("\\|")[0];
        if (name.isEmpty())
            throw new IllegalArgumentException("To load EncodedValue a name is required. " + string);

        switch (name) {
            case Roundabout.KEY:
                enc = Roundabout.create();
                break;
            case GetOffBike.KEY:
                enc = GetOffBike.create();
                break;
            case RoadClass.KEY:
                enc = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
                break;
            case RoadClassLink.KEY:
                enc = new SimpleBooleanEncodedValue(RoadClassLink.KEY);
                break;
            case RoadEnvironment.KEY:
                enc = new EnumEncodedValue<>(RoadEnvironment.KEY, RoadEnvironment.class);
                break;
            case RoadAccess.KEY:
                enc = new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class);
                break;
            case MaxSpeed.KEY:
                enc = MaxSpeed.create();
                break;
            case MaxWeight.KEY:
                enc = MaxWeight.create();
                break;
            case MaxHeight.KEY:
                enc = MaxHeight.create();
                break;
            case MaxWidth.KEY:
                enc = MaxWidth.create();
                break;
            case MaxAxleLoad.KEY:
                enc = MaxAxleLoad.create();
                break;
            case MaxLength.KEY:
                enc = MaxLength.create();
                break;
            case Hgv.KEY:
                enc = new EnumEncodedValue<>(Hgv.KEY, Hgv.class);
                break;
            case Surface.KEY:
                enc = new EnumEncodedValue<>(Surface.KEY, Surface.class);
                break;
            case Smoothness.KEY:
                enc = new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class);
                break;
            case Toll.KEY:
                enc = new EnumEncodedValue<>(Toll.KEY, Toll.class);
                break;
            case TrackType.KEY:
                enc = new EnumEncodedValue<>(TrackType.KEY, TrackType.class);
                break;
            case "BikeNetwork":
            case "FootNetwork":
            case "RouteNetwork":
                enc = new EnumEncodedValue<>(name, RouteNetwork.class);
                break;
            case Hazmat.KEY:
                enc = new EnumEncodedValue<>(Hazmat.KEY, Hazmat.class);
                break;
            case HazmatTunnel.KEY:
                enc = new EnumEncodedValue<>(HazmatTunnel.KEY, HazmatTunnel.class);
                break;
            case HazmatWater.KEY:
                enc = new EnumEncodedValue<>(HazmatWater.KEY, HazmatWater.class);
                break;
            case Lanes.KEY:
                enc = Lanes.create();
                break;
            case Footway.KEY:
                enc = new EnumEncodedValue<>(Footway.KEY, Footway.class);
                break;
            case OSMWayID.KEY:
                enc = OSMWayID.create();
                break;
            case MtbRating.KEY:
            case HikeRating.KEY:
            case HorseRating.KEY:
                enc = MtbRating.create();
                break;

            case Country.KEY:
                enc = Country.create();
                break;
            case MaxSlope.KEY:
                enc = MaxSlope.create();
                break;
            case AverageSlope.KEY:
                enc = AverageSlope.create();
                break;
            case Curvature.KEY:
                enc = Curvature.create();
                break;
            default:
                if (name.endsWith(Subnetwork.key(""))) {
                    enc = new SimpleBooleanEncodedValue(name);
                } else {
                    throw new IllegalArgumentException("DefaultEncodedValueFactory cannot find EncodedValue " + name);
                }
                break;
        }

        return enc;
    }
}
