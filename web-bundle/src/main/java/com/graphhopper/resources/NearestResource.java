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
package com.graphhopper.resources;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * @author svantulden
 * @author Michael Zilske
 */
@Path("nearest")
@Produces(MediaType.APPLICATION_JSON)
public class NearestResource {

    private static final DistanceCalc calc = DistanceCalcEarth.DIST_EARTH;
    private final LocationIndex index;
    private final boolean hasElevation;

    @Inject
    NearestResource(LocationIndex index, @Named("hasElevation") Boolean hasElevation) {
        this.index = index;
        this.hasElevation = hasElevation;
    }

    public static class Response {
        public static final String TYPE = "Point";
        public final double[] coordinates;
        public final double distance; // Distance from input to snapped point in meters

        @JsonCreator
        Response(@JsonProperty("coordinates") double[] coordinates, @JsonProperty("distance") double distance) {
            this.coordinates = coordinates;
            this.distance = distance;
        }
    }

    @GET
    public Response doGet(@QueryParam("point") GHPoint point, @QueryParam("elevation") @DefaultValue("false") boolean elevation) {
        Snap snap = index.findClosest(point.getLat(), point.getLon(), EdgeFilter.ALL_EDGES);
        if (snap.isValid()) {
            GHPoint3D snappedPoint = snap.getSnappedPoint();
            double[] coordinates = hasElevation && elevation ? new double[]{snappedPoint.getLon(), snappedPoint.getLat(), snappedPoint.getEle()} : new double[]{snappedPoint.getLon(), snappedPoint.getLat()};
            return new Response(coordinates, calc.calcDist(point.getLat(), point.getLon(), snappedPoint.getLat(), snappedPoint.getLon()));
        } else {
            throw new WebApplicationException("Nearest point cannot be found!");
        }
    }

}
