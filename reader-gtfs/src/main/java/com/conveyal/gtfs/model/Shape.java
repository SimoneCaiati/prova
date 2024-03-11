/*
 * Copyright (c) 2015, Conveyal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.conveyal.gtfs.model;

import com.conveyal.gtfs.GTFSFeed;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.mapdb.Fun;

import java.util.Map;

/**
 * Represents a collection of GTFS shape points. Never saved in MapDB but constructed on the fly.
 */
public class Shape {
     static GeometryFactory geometryFactory = new GeometryFactory();
    /** The shape itself */
     LineString geometry;

    public LineString getGeometry() {
        return geometry;
    }

    public void setGeometry(LineString geometry) {
        this.geometry = geometry;
    }

    /** shape_dist_traveled for each point in the geometry.  how to handle shape dist traveled not specified, or not specified on all stops? */
     double[] shapeDistTraveled;

    public double[] getShapeDistTraveled() {
        return shapeDistTraveled;
    }

    public void setShapeDistTraveled(double[] shapeDistTraveled) {
        this.shapeDistTraveled = shapeDistTraveled;
    }

    public Shape (GTFSFeed feed, String shapeId) {
        Map<Fun.Tuple2<String, Integer>, ShapePoint> points =
                feed.shapePoints.subMap(new Fun.Tuple2<>(shapeId, null), new Fun.Tuple2<>(shapeId, Fun.HI()));

        Coordinate[] coords = points.values().stream()
                .map(point -> new Coordinate(point.shapePtLon, point.shapePtLat))
                .toArray(i -> new Coordinate[i]);
        geometry = geometryFactory.createLineString(coords);
        shapeDistTraveled = points.values().stream().mapToDouble(point -> point.shapeDistTraveled).toArray();
    }
}
