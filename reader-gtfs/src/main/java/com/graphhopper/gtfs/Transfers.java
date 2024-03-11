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

package com.graphhopper.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Transfer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Transfers {

    private final Map<String, List<Transfer>> transfersFromStop;
    private final Map<String, List<Transfer>> transfersToStop;
    private final Map<String, Set<String>> routesByStop;

    public Transfers(GTFSFeed feed) {
        this.transfersToStop = explodeTransfers(feed).collect(Collectors.groupingBy(t -> t.to_stop_id));
        this.transfersFromStop = explodeTransfers(feed).collect(Collectors.groupingBy(Transfer::getfromStopId));
        this.routesByStop = feed.stopTims.values().stream()
                .collect(Collectors.groupingBy(StopTime::getStopId,
                        Collectors.mapping(stopTime -> feed.trips.get(stopTime.trip_id).route_id, Collectors.toSet())));
    }

    private Stream<Transfer> explodeTransfers(GTFSFeed feed) {
        return feed.transfers.values().stream()
                .flatMap(t -> {
                    Stop fromStop = feed.stops.get(t.getfromStopId());
                    if (fromStop.getLocationType() == 1) {
                        return feed.stops.values().stream()
                                .filter(location -> location.getLocationType() == 0)
                                .filter(stop -> fromStop.getStopId().equals(stop.getParentStation()))
                                .map(platform -> {
                                    Transfer transferCopy = Transfer.copyTransfer(t);
                                    transferCopy.setFromStopId(platform.getStopId());
                                    return transferCopy;
                                });
                    } else {
                        return Stream.of(t);
                    }
                })
                .flatMap(t -> {
                    Stop toStop = feed.stops.get(t.to_stop_id);
                    if (toStop.getLocationType() == 1) {
                        return feed.stops.values().stream()
                                .filter(location -> location.getLocationType() == 0)
                                .filter(stop -> toStop.getStopId().equals(stop.getParentStation()))
                                .map(platform -> {
                                    Transfer transferCopy = Transfer.copyTransfer(t);
                                    transferCopy.to_stop_id = platform.getStopId();
                                    return transferCopy;
                                });
                    } else {
                        return Stream.of(t);
                    }
                });
    }

    // Starts implementing the proposed GTFS extension for route and trip specific transfer rules.
    // So far, only the route is supported.
    List<Transfer> getTransfersToStop(String toStopId, String toRouteId) {
        final List<Transfer> allInboundTransfers = transfersToStop.getOrDefault(toStopId, Collections.emptyList());
        final Map<String, List<Transfer>> byFromStop = allInboundTransfers.stream()
                .filter(t -> t.getTransferType() == 0 || t.getTransferType() == 2)
                .filter(t -> t.getToRouteId() == null || toRouteId.equals(t.getToRouteId()))
                .collect(Collectors.groupingBy(t -> t.getfromStopId()));
        final List<Transfer> result = new ArrayList<>();
        byFromStop.forEach((fromStop, transfers) -> {
            if (hasNoRouteSpecificArrivalTransferRules(fromStop)) {
                Transfer myRule = new Transfer();
                myRule.setFromStopId(fromStop);
                myRule.to_stop_id = toStopId;

                if(transfers.size() == 1)
                    myRule.setminTransferTime(transfers.get(0).getminTransferTime());

                result.add(myRule);
            } else {
                routesByStop.getOrDefault(fromStop, Collections.emptySet()).forEach(fromRoute -> {
                    final Transfer mostSpecificRule = findMostSpecificRule(transfers, fromRoute, toRouteId);
                    final Transfer myRule = new Transfer();
                    myRule.setToRouteId(toRouteId);
                    myRule.setFromRouteId(fromRoute);
                    myRule.to_stop_id = mostSpecificRule.to_stop_id;
                    myRule.setFromStopId(mostSpecificRule.getfromStopId());
                    myRule.setTransferType(mostSpecificRule.getTransferType());
                    myRule.setminTransferTime(mostSpecificRule.getminTransferTime());
                    myRule.setFromTripId(mostSpecificRule.getFromTripId());
                    myRule.to_trip_id = mostSpecificRule.to_trip_id;
                    result.add(myRule);
                });
            }
        });
        if (result.stream().noneMatch(t -> t.getfromStopId().equals(toStopId))) {
            final Transfer withinStationTransfer = new Transfer();
            withinStationTransfer.setFromStopId(toStopId);
            withinStationTransfer.to_stop_id = toStopId;
            result.add(withinStationTransfer);
        }
        return result;
    }

    List<Transfer> getTransfersFromStop(String fromStopId, String fromRouteId) {
        final List<Transfer> allOutboundTransfers = transfersFromStop.getOrDefault(fromStopId, Collections.emptyList());
        final Map<String, List<Transfer>> byToStop = allOutboundTransfers.stream()
                .filter(t -> t.getTransferType() == 0 || t.getTransferType() == 2)
                .filter(t -> t.getFromRouteId() == null || fromRouteId.equals(t.getFromRouteId()))
                .collect(Collectors.groupingBy(t -> t.to_stop_id));
        final List<Transfer> result = new ArrayList<>();
        byToStop.forEach((toStop, transfers) ->
                routesByStop.getOrDefault(toStop, Collections.emptySet()).forEach(toRouteId -> {
                    final Transfer mostSpecificRule = findMostSpecificRule(transfers, fromRouteId, toRouteId);
                    final Transfer myRule = new Transfer();
                    myRule.setToRouteId(toRouteId);
                    myRule.setFromRouteId(fromRouteId);
                    myRule.to_stop_id = mostSpecificRule.to_stop_id;
                    myRule.setFromStopId(mostSpecificRule.getfromStopId());
                    myRule.setTransferType(mostSpecificRule.getTransferType());
                    myRule.setminTransferTime(mostSpecificRule.getminTransferTime());
                    myRule.setFromTripId(mostSpecificRule.getFromTripId());
                    myRule.to_trip_id = mostSpecificRule.to_trip_id;
                    result.add(myRule);
                })
        );

        return result;
    }

    private Transfer findMostSpecificRule(List<Transfer> transfers, String fromRouteId, String toRouteId) {
        final ArrayList<Transfer> transfersBySpecificity = new ArrayList<>(transfers);
        transfersBySpecificity.sort(Comparator.comparingInt(t -> {
            int score = 0;
            if (Objects.equals(fromRouteId, t.getFromRouteId())) {
                score++;
            }
            if (Objects.equals(toRouteId, t.getToRouteId())) {
                score++;
            }
            return -score;
        }));
        if (transfersBySpecificity.isEmpty()) {
            try {
                throw new TranExce();
            } catch (TranExce e) {
                //
            }
        }
        return transfersBySpecificity.get(0);
    }

    public boolean hasNoRouteSpecificDepartureTransferRules(String stopId) {
        return transfersToStop.getOrDefault(stopId, Collections.emptyList()).stream().allMatch(transfer -> transfer.getToRouteId() == null);
    }

    public boolean hasNoRouteSpecificArrivalTransferRules(String stopId) {
        return transfersFromStop.getOrDefault(stopId, Collections.emptyList()).stream().allMatch(transfer -> transfer.getFromRouteId() == null);
    }

    private class TranExce extends Exception {
    }
}
