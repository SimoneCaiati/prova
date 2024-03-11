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
import com.conveyal.gtfs.error.DuplicateKeyError;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public class Calendar extends Entity implements Serializable {

    private static final long serialVersionUID = 6634236680822635875L;
    protected int monday;
     int tuesday;
    protected int wednesday;
     int thursday;
     int friday;
     int saturday;
     int sunday;
     int start_date;

    public int getStartDate() {
        return start_date;
    }

    int end_date;

    public int getendDate() {
        return end_date;
    }

    protected String feed_id;
     String service_id;

    public static class Loader extends Entity.Loader<Calendar> {

        private final Map<String, Service> services;

        /**
         * Create a loader. The map parameter should be an in-memory map that will be modified. We can't write directly
         * to MapDB because we modify services as we load calendar dates, and this creates concurrentmodificationexceptions.
         */
        public Loader(GTFSFeed feed, Map<String, Service> services) {
            super(feed, "calendar");
            this.services = services;
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        public void loadOneRow() throws IOException {

            /* Calendars and Fares are special: they are stored as joined tables rather than simple maps. */
            String serviceId = getStringField("service_id", true); //  service_id can reference either calendar or calendar_dates.
            Service service = services.computeIfAbsent(serviceId, Service::new);
            if (service.calendar != null) {
                feed.errors.add(new DuplicateKeyError(tableName, row, "service_id"));
            } else {
                Calendar c = new Calendar();
                c.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
                c.service_id = service.service_id;
                c.monday = getIntField("monday", true, 0, 1);
                c.tuesday = getIntField("tuesday", true, 0, 1);
                c.wednesday = getIntField("wednesday", true, 0, 1);
                c.thursday = getIntField("thursday", true, 0, 1);
                c.friday = getIntField("friday", true, 0, 1);
                c.saturday = getIntField("saturday", true, 0, 1);
                c.sunday = getIntField("sunday", true, 0, 1);
                //check valid dates
                c.start_date = getIntField("start_date", true, 18500101, 22001231);
                c.end_date = getIntField("end_date", true, 18500101, 22001231);
                c.feed = feed;
                c.feed_id = feed.getFeedId();
                service.calendar = c;
            }

        }    
    }

}
