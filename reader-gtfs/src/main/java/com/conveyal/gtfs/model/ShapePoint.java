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
import org.mapdb.Fun.Tuple2;

import java.io.IOException;

public class ShapePoint extends Entity {

    private static final long serialVersionUID = 6751814959971086070L;
    public final String shapeId;
    public final double shapePtLat;
    public final double shapePtLon;
    public final int shapePtSequence;
    public final double shapeDistTraveled;

    // Similar to stoptime, we have to have a constructor, because fields are final so as to be immutable for storage in MapDB.
    public ShapePoint(String shapeId, double shapePtLat, double shapePtLon, int shapePtSequence, double shapeDistTraveled) {
        this.shapeId = shapeId;
        this.shapePtLat = shapePtLat;
        this.shapePtLon = shapePtLon;
        this.shapePtSequence = shapePtSequence;
        this.shapeDistTraveled = shapeDistTraveled;
    }

    public static class Loader extends Entity.Loader<ShapePoint> {

        public Loader(GTFSFeed feed) {
            super(feed, "shapes");
        }

        @Override
        protected boolean isRequired() {
            return false;
        }

        @Override
        public void loadOneRow() throws IOException {
            String shapeId = getStringField("shapeId", true);
            double shapePtLat = getDoubleField("shapePtLat", true, -90D, 90D);
            double shapePtLon = getDoubleField("shapePtLon", true, -180D, 180D);
            int shapePtSequence = getIntField("shapePtSequence", true, 0, Integer.MAX_VALUE);
            double shapeDistTraveled1 = getDoubleField("shapeDistTraveled1", false, 0D, Double.MAX_VALUE);

            ShapePoint s = new ShapePoint(shapeId, shapePtLat, shapePtLon, shapePtSequence, shapeDistTraveled1);
            s.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            s.feed = null; // since we're putting this into MapDB, we don't want circular serialization
            feed.shapePoints.put(new Tuple2<>(s.shapeId, s.shapePtSequence), s);
        }
    }

}
