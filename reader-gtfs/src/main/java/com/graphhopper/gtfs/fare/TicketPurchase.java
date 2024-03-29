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

package com.graphhopper.gtfs.fare;

import java.util.*;

class TicketPurchase {
    public final List<FareAssignment> fareAssignments;

    TicketPurchase(List<FareAssignment> fareAssignments) {
        this.fareAssignments = fareAssignments;
    }

    List<Ticket> getTickets() {
        Map<String, TicketPurchaseScoreCalculator.TempTicket> currentTickets = new HashMap<>();
        for (FareAssignment fareAssignment : fareAssignments) {
            if (fareAssignment.fare != null) {
                currentTickets.computeIfAbsent(fareKey(fareAssignment), fareId -> new TicketPurchaseScoreCalculator.TempTicket());
                currentTickets.compute(fareKey(fareAssignment), (s, tempTicket) -> {
                    if (fareAssignment.segment.getStartTime() > tempTicket.validUntil
                            || tempTicket.nMoreTransfers == 0) {
                        tempTicket.feedId = fareAssignment.segment.feedId;
                        tempTicket.fare = fareAssignment.fare;
                        tempTicket.validUntil = fareAssignment.segment.getStartTime() + fareAssignment.fare.getFareAttribute().getTransferDuration();
                        tempTicket.nMoreTransfers = fareAssignment.fare.getFareAttribute().getTransfers();
                        tempTicket.totalNumber++;
                        return tempTicket;
                    } else {
                        tempTicket.nMoreTransfers--;
                        return tempTicket;
                    }
                });
            }
        }
        ArrayList<Ticket> tickets = new ArrayList<>();
        for (TicketPurchaseScoreCalculator.TempTicket t : currentTickets.values()) {
            for (int i = 0; i<t.totalNumber; i++) {
                tickets.add(new Ticket(t.feedId, t.fare));
            }
        }
        return tickets;
    }

    private String fareKey(FareAssignment fareAssignment) {
        return fareAssignment.fare.getFareId()+"_"+ fareAssignment.fare.getFareAttribute().getfeedId();
    }

    int getNSchwarzfahrTrips() {
        return (int) fareAssignments.stream().filter(assignment -> assignment.fare == null).count();
    }
}
