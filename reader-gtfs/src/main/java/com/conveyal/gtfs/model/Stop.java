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

import java.io.IOException;
import java.net.URL;

public class Stop extends Entity {

    private static final long serialVersionUID = 464065335273514677L;
     String stop_id;

    public String getStopId() {
        return stop_id;
    }

    public void setStopId(String stopId) {
        this.stop_id = stopId;
    }

    String stop_code;
     String stop_name;

    public String getStopName() {
        return stop_name;
    }

    public void setStopName(String stopName) {
        this.stop_name = stopName;
    }

     String stop_desc;
     double stop_lat;

    public double getStopLat() {
        return stop_lat;
    }

    public void setStopLat(double stopLat) {
        this.stop_lat = stopLat;
    }

     double stop_lon;

    public double getStopLon() {
        return stop_lon;
    }

    public void setStopLon(double stopLon) {
        this.stop_lon = stopLon;
    }

    public String getzoneId() {
        return zone_id;
    }



    protected String zone_id;
     URL    stop_url;

    public int getLocationType() {
        return location_type;
    }

    protected int    location_type;

    public String getParentStation() {
        return parent_station;
    }

    protected String parent_station;
     String stop_timezone;
    //  should be int
    protected String wheelchair_boarding;
    protected String feed_id;

    public static class Loader extends Entity.Loader<Stop> {

        public Loader(GTFSFeed feed) {
            super(feed, "stops");
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        public void loadOneRow() throws IOException {
            Stop s = new Stop();
            s.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            s.stop_id   = getStringField("stop_id", true);
            s.stop_code = getStringField("stop_code", false);
            s.stop_name = getStringField("stop_name", true);
            s.stop_desc = getStringField("stop_desc", false);
            s.stop_lat  = getDoubleField("stop_lat", true, -90D, 90D);
            s.stop_lon  = getDoubleField("stop_lon", true, -180D, 180D);
            s.zone_id   = getStringField("zone_id", false);
            s.stop_url  = getUrlField("stop_url", false);
            s.location_type  = getIntField("location_type", false, 0, 4);
            s.parent_station = getStringField("parent_station", false);
            s.stop_timezone  = getStringField("stop_timezone", false);
            s.wheelchair_boarding = getStringField("wheelchair_boarding", false);
            s.feed = feed;
            s.feed_id = feed.getFeedId();
            /*  check ref integrity later, this table self-references via parent_station */

            feed.stops.put(s.stop_id, s);
        }

    }

    @Override
    public String toString() {
        return "Stop{" +
                "stop_id='" + stop_id + '\'' +
                '}';
    }
}
