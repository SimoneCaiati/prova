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

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntToLongFunction;
import java.util.function.Predicate;

/**
 * Implements a Multi-Criteria Label Setting (MLS) path finding algorithm
 * with the criteria earliest arrival time and number of transfers.
 * <p>
 *
 * @author Michael Zilske
 * @author Peter Karich
 * @author Wesam Herbawi
 */
public class MultiCriteriaLabelSetting {

    private final Comparator<Label> queueComparator;
    private final List<Label> targetLabels;
    private long startTime;
    private final Map<Label.NodeId, List<Label>> fromMap;
    private final PriorityQueue<Label> fromHeap;
    private final long maxProfileDuration;
    private final boolean reverse;
    private final boolean mindTransfers;
    private final boolean profileQuery;
    private final GraphExplorer explorer;
    private double betaTransfers = 0.0;
    private IntToLongFunction transferPenaltiesByRouteType = (routeType -> 0L);
    private double betaStreetTime = 1.0;
    private long limitTripTime = Long.MAX_VALUE;
    private long limitStreetTime = Long.MAX_VALUE;

    public MultiCriteriaLabelSetting(GraphExplorer explorer, boolean reverse, boolean mindTransfers, boolean profileQuery, long maxProfileDuration, List<Label> solutions) {
        this.explorer = explorer;
        this.reverse = reverse;
        this.mindTransfers = mindTransfers;
        this.profileQuery = profileQuery;
        this.maxProfileDuration = maxProfileDuration;
        this.targetLabels = solutions;

        queueComparator = new LabelComparator();
        fromHeap = new PriorityQueue<>(queueComparator);
        fromMap = new HashMap<>();
    }

    public Iterable<Label> calcLabels(Label.NodeId from, Instant startTime) {
        this.startTime = startTime.toEpochMilli();
        return () -> Spliterators.iterator(new MultiCriteriaLabelSettingSpliterator(from));
    }

    void setBetaTransfers(double betaTransfers) {
        this.betaTransfers = betaTransfers;
    }

    void setBetaStreetTime(double betaWalkTime) {
        this.betaStreetTime = betaWalkTime;
    }

    void setBoardingPenaltyByRouteType(IntToLongFunction transferPenaltiesByRouteType) {
        this.transferPenaltiesByRouteType = transferPenaltiesByRouteType;
    }

    private class MultiCriteriaLabelSettingSpliterator extends Spliterators.AbstractSpliterator<Label> {

        MultiCriteriaLabelSettingSpliterator(Label.NodeId from) {
            super(0, 0);
            Label label = new Label(new Label.InnerLabel(startTime, null, from, 0, null), 0, 0L, 0, false, null);
            ArrayList<Label> labels = new ArrayList<>(1);
            labels.add(label);
            fromMap.put(from, labels);
            fromHeap.add(label);
        }

        @Override
        public boolean tryAdvance(Consumer<? super Label> action) {
            filouno();
            if (fromHeap.isEmpty()) {
                return false;
            } else {
                Label label = fromHeap.poll();
                action.accept(label);
                melloni(label);
                return true;
            }
        }

        private void melloni(Label label) {
            for (GraphExplorer.MultiModalEdge edge : explorer.exploreEdgesAround(label)) {
                long nextTime;
                nextTime = filo(label, edge);
                int nTransfers = label.innerlabel.nTransfers + edge.getTransfers();
                long extraWeight = label.extraWeight;
                Long firstPtDepartureTime = label.innerlabel.departureTime;
                GtfsStorage.EdgeType edgeType = edge.getType();
                extraWeight = mellouno(edge, extraWeight, edgeType);
                firstPtDepartureTime = mellosue(label, nextTime, firstPtDepartureTime, edgeType);
                long walkTime;
                if (edgeType == GtfsStorage.EdgeType.HIGHWAY || edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT) {
                    walkTime = label.streetTime + ((reverse ? -1 : 1) * (nextTime - label.innerlabel.currentTime));
                } else {
                    walkTime = label.streetTime;
                }
                if (splitterquattro(label, nextTime, edgeType, walkTime)) continue;
                boolean impossible = mod(label, edge, edgeType);
                long residualDelay;
                residualDelay = splitterdue(label, edge, edgeType);
                if (!reverse && edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK && residualDelay > 0) {
                    Label newImpossibleLabelForDelayedTrip = new Label(new Label.InnerLabel(nextTime, edge, edge.getAdjNode(), nTransfers, firstPtDepartureTime), walkTime, extraWeight, residualDelay, true, label);
                    insertIfNotDominated(newImpossibleLabelForDelayedTrip);
                    nextTime += residualDelay;
                    residualDelay = 0;
                    Label newLabel = new Label(new Label.InnerLabel(nextTime, edge, edge.getAdjNode(), nTransfers, firstPtDepartureTime), walkTime, extraWeight, residualDelay, impossible, label);
                    insertIfNotDominated(newLabel);
                } else {
                    Label newLabel = new Label(new Label.InnerLabel(nextTime, edge, edge.getAdjNode(), nTransfers, firstPtDepartureTime), walkTime, extraWeight, residualDelay, impossible, label);
                    insertIfNotDominated(newLabel);
                }
            }
        }

        private boolean splitterquattro(Label label, long nextTime, GtfsStorage.EdgeType edgeType, long walkTime) {
            boolean result = fildue(label, false);
            return splittertre(nextTime, walkTime) || (edgeType == GtfsStorage.EdgeType.ENTER_PT && result);
        }


        private boolean splittertre(long nextTime, long walkTime) {
            return (walkTime > limitStreetTime) || (Math.abs(nextTime - startTime) > limitTripTime);
        }


        private long splitterdue(Label label, GraphExplorer.MultiModalEdge edge, GtfsStorage.EdgeType edgeType) {
            long residualDelay;
            if (!reverse) {
                residualDelay = splitter(label, edge, edgeType);
            } else {
                if (edgeType == GtfsStorage.EdgeType.WAIT || edgeType == GtfsStorage.EdgeType.TRANSFER) {
                    residualDelay = label.residualDelay + explorer.calcTravelTimeMillis(edge, label.innerlabel.currentTime);
                } else {
                    residualDelay = 0;
                }
            }
            return residualDelay;
        }

        private long splitter(Label label, GraphExplorer.MultiModalEdge edge, GtfsStorage.EdgeType edgeType) {
            long residualDelay;
            if (edgeType == GtfsStorage.EdgeType.WAIT || edgeType == GtfsStorage.EdgeType.TRANSFER) {
                residualDelay = Math.max(0, label.residualDelay - explorer.calcTravelTimeMillis(edge, label.innerlabel.currentTime));
            } else if (edgeType == GtfsStorage.EdgeType.ALIGHT) {
                residualDelay = label.residualDelay + explorer.getDelayFromAlightEdge(edge, label.innerlabel.currentTime);
            } else if (edgeType == GtfsStorage.EdgeType.BOARD) {
                residualDelay = -explorer.getDelayFromBoardEdge(edge, label.innerlabel.currentTime);
            } else {
                residualDelay = label.residualDelay;
            }
            return residualDelay;
        }

        private boolean mod(Label label, GraphExplorer.MultiModalEdge edge, GtfsStorage.EdgeType edgeType) {
            return label.impossible
                    || explorer.isBlocked(edge)
                    || (!reverse) && edgeType == GtfsStorage.EdgeType.BOARD && label.residualDelay > 0
                    || reverse && edgeType == GtfsStorage.EdgeType.ALIGHT && label.residualDelay < explorer.getDelayFromAlightEdge(edge, label.innerlabel.currentTime);
        }

        private boolean fildue(Label label, boolean result) {
            if (label.innerlabel.edge != null) {
                result = label.innerlabel.edge.getType() == GtfsStorage.EdgeType.EXIT_PT;
            }
            return result;
        }

        private void filouno() {
            while (!fromHeap.isEmpty() && fromHeap.peek().deleted)
                fromHeap.poll();
        }

        private Long mellosue(Label label, long nextTime, Long firstPtDepartureTime, GtfsStorage.EdgeType edgeType) {
            if (!reverse && (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT)) {
                if (label.innerlabel.nTransfers == 0) {
                    firstPtDepartureTime = nextTime - label.streetTime;
                }
            } else if (reverse && (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL)&&label.innerlabel.nTransfers == 0) {
                firstPtDepartureTime = nextTime + label.streetTime;
                }
            return firstPtDepartureTime;
        }

        private long mellouno(GraphExplorer.MultiModalEdge edge, long extraWeight, GtfsStorage.EdgeType edgeType) {
            if (!reverse && (edgeType == GtfsStorage.EdgeType.ENTER_PT) || reverse && (edgeType == GtfsStorage.EdgeType.EXIT_PT)) {
                extraWeight += transferPenaltiesByRouteType.applyAsLong(edge.getRouteType());
            }
            if (edgeType == GtfsStorage.EdgeType.TRANSFER) {
                extraWeight += transferPenaltiesByRouteType.applyAsLong(edge.getRouteType());
            }
            return extraWeight;
        }

        private long filo(Label label, GraphExplorer.MultiModalEdge edge) {
            long nextTime;
            if (reverse) {
                nextTime = label.innerlabel.currentTime - explorer.calcTravelTimeMillis(edge, label.innerlabel.currentTime);
            } else {
                nextTime = label.innerlabel.currentTime + explorer.calcTravelTimeMillis(edge, label.innerlabel.currentTime);
            }
            return nextTime;
        }
    }


    void insertIfNotDominated(Label me) {
        Predicate<Label> filter;
        if (profileQuery && me.innerlabel.departureTime != null) {
            filter = targetLabel -> (!reverse ? prc(me, targetLabel) : rprc(me, targetLabel));
        } else {
            filter = label -> true;
        }
        if (isNotDominatedByAnyOf(me, targetLabels, filter)) {
            List<Label> sptEntries = fromMap.computeIfAbsent(me.innerlabel.node, k -> new ArrayList<>(1));
            if (isNotDominatedByAnyOf(me, sptEntries, filter)) {
                removeDominated(me, sptEntries, filter);
                sptEntries.add(me);
                fromHeap.add(me);
            }
        }
    }

    boolean rprc(Label me, Label they) {
        return they.innerlabel.departureTime != null && (they.innerlabel.departureTime <= me.innerlabel.departureTime || they.innerlabel.departureTime <= startTime - maxProfileDuration);
    }

    boolean prc(Label me, Label they) {
        return they.innerlabel.departureTime != null && (they.innerlabel.departureTime >= me.innerlabel.departureTime || they.innerlabel.departureTime >= startTime + maxProfileDuration);
    }

    boolean isNotDominatedByAnyOf(Label me, Collection<Label> sptEntries, Predicate<Label> filter) {
        for (Label they : sptEntries) {
            if (filter.test(they) && dominates(they, me)) {
                return false;
            }
        }
        return true;
    }

    void removeDominated(Label me, Collection<Label> sptEntries, Predicate<Label> filter) {
        for (Iterator<Label> iterator = sptEntries.iterator(); iterator.hasNext(); ) {
            Label sptEntry = iterator.next();
            if (filter.test(sptEntry) && dominates(me, sptEntry)) {
                sptEntry.deleted = true;
                iterator.remove();
            }
        }
    }

    private boolean dominates(Label mio, Label they) {
        if (weight(mio) > weight(they))
            return false;

        if (mindTransfers && mio.innerlabel.nTransfers > they.innerlabel.nTransfers)
            return false;
        if (mio.impossible && !they.impossible)
            return false;

        if (weight(mio) < weight(they))
            return true;
        if (mindTransfers && mio.innerlabel.nTransfers < they.innerlabel.nTransfers)
            return true;

        return queueComparator.compare(mio, they) <= 0;
    }

    long weight(Label label) {
        return timeSinceStartTime(label) + (long) (label.innerlabel.nTransfers * betaTransfers) + (long) (label.streetTime * (betaStreetTime - 1.0)) + label.extraWeight;
    }

    long timeSinceStartTime(Label label) {
        return (reverse ? -1 : 1) * (label.innerlabel.currentTime - startTime);
    }

    Long departureTimeSinceStartTime(Label label) {
        Long result;
        if (label.innerlabel.departureTime != null) {
            result = (reverse ? -1 : 1) * (label.innerlabel.departureTime - startTime);
        } else {
            result = null;
        }

        return result;
    }

    public void setLimitTripTime(long limitTripTime) {
        this.limitTripTime = limitTripTime;
    }

    public void setLimitStreetTime(long limitStreetTime) {
        this.limitStreetTime = limitStreetTime;
    }

    private class LabelComparator implements Comparator<Label> {

        @Override
        public int compare(Label o1, Label o2) {
            int c = Long.compare(weight(o1), weight(o2));
            if (c != 0)
                return c;

            c = Integer.compare(o1.innerlabel.nTransfers, o2.innerlabel.nTransfers);
            if (c != 0)
                return c;

            c = Long.compare(o1.streetTime, o2.streetTime);
            if (c != 0)
                return c;

            long o1DepartureTime = getDepartureTime(o1);
            long o2DepartureTime = getDepartureTime(o2);
            c = Long.compare(o1DepartureTime, o2DepartureTime);
            if (c != 0)
                return c;

            int o1Impossible = o1.impossible ? 1 : 0;
            int o2Impossible = o2.impossible ? 1 : 0;
            c = Integer.compare(o1Impossible, o2Impossible);
            return c;
        }

        private long getDepartureTime(Label label) {
            if (label.innerlabel.departureTime != null) {
                if (reverse) {
                    return label.innerlabel.departureTime;
                } else {
                    return -label.innerlabel.departureTime;
                }
            } else {
                return 0;
            }
        }
    }


}
