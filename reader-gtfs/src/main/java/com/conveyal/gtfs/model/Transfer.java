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

public class Transfer extends Entity {

    private static final long serialVersionUID = -4944512120812641063L;
     String from_stop_id;

    public String getfromStopId() {
        return from_stop_id;
    }

    public void setFromStopId(String fromStopId) {
        this.from_stop_id = fromStopId;
    }

    public String to_stop_id;
    int  transfer_type;

    public void setTransferType(int transferType) {
        this.transfer_type = transferType;
    }

    public int getTransferType() {
        return transfer_type;
    }

    public int getminTransferTime() {
        return min_transfer_time;
    }

    public void setminTransferTime(int minTransferTime) {
        this.min_transfer_time = minTransferTime;
    }

    protected int  min_transfer_time;
     String from_route_id;

    public String getFromRouteId() {
        return from_route_id;
    }

    public void setFromRouteId(String fromRouteId) {
        this.from_route_id = fromRouteId;
    }

    String to_route_id;

    public String getToRouteId() {
        return to_route_id;
    }

    public void setToRouteId(String toRouteId) {
        this.to_route_id = toRouteId;
    }

     String from_trip_id;

    public String getFromTripId() {
        return from_trip_id;
    }

    public void setFromTripId(String fromTripId) {
        this.from_trip_id = fromTripId;
    }

    public String to_trip_id;

    @Override
    public String toString() {
        return "Transfer{" +
                "from_stop_id='" + from_stop_id + '\'' +
                ", to_stop_id='" + to_stop_id + '\'' +
                ", transfer_type=" + transfer_type +
                ", min_transfer_time=" + min_transfer_time +
                ", from_route_id='" + from_route_id + '\'' +
                ", to_route_id='" + to_route_id + '\'' +
                ", from_trip_id='" + from_trip_id + '\'' +
                ", to_trip_id='" + to_trip_id + '\'' +
                '}';
    }
    public static Transfer copyTransfer(Transfer transfer) {
        Transfer transferCopy = new Transfer();
        transferCopy.from_stop_id = transfer.from_stop_id;
        transferCopy.to_stop_id = transfer.to_stop_id;
        transferCopy.transfer_type = transfer.transfer_type;
        transferCopy.min_transfer_time = transfer.min_transfer_time;
        transferCopy.from_route_id = transfer.from_route_id;
        transferCopy.to_route_id = transfer.to_route_id;
        transferCopy.from_trip_id = transfer.from_trip_id;
        transferCopy.to_trip_id = transfer.to_trip_id;
        return transferCopy;
    }

    public static class Loader extends Entity.Loader<Transfer> {

        public Loader(GTFSFeed feed) {
            super(feed, "transfers");
        }

        @Override
        protected boolean isRequired() {
            return false;
        }

        @Override
        public void loadOneRow() throws IOException {
            Transfer tr = new Transfer();
            tr.sourceFileLine    = row + 1; // offset line number by 1 to account for 0-based row index
            tr.from_stop_id      = getStringField("from_stop_id", true);
            tr.to_stop_id        = getStringField("to_stop_id", true);
            tr.transfer_type     = getIntField("transfer_type", true, 0, 3);
            tr.min_transfer_time = getIntField("min_transfer_time", false, 0, Integer.MAX_VALUE);
            tr.from_route_id     = getStringField("from_route_id", false);
            tr.to_route_id       = getStringField("to_route_id", false);
            tr.from_trip_id      = getStringField("from_trip_id", false);
            tr.to_trip_id        = getStringField("to_trip_id", false);

            getRefField("from_stop_id", true, feed.stops);
            getRefField("to_stop_id", true, feed.stops);
            getRefField("from_route_id", false, feed.routes);
            getRefField("to_route_id", false, feed.routes);
            getRefField("from_trip_id", false, feed.trips);
            getRefField("to_trip_id", false, feed.trips);

            tr.feed = feed;
            feed.transfers.put(Long.toString(row), tr);
        }

    }
}
