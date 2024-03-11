package com.graphhopper;

import com.graphhopper.util.InstructionList;
import com.graphhopper.util.details.PathDetail;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class Trip {
    private Trip()
    {
        /*
        YOLOWN
         */
    }

    public abstract static class Leg {
        public final String type;
        public final String departureLocation;
        public final Geometry geometry;
        public final double distance;

        protected Leg(String type, String departureLocation, Geometry geometry, double distance) {
            this.type = type;
            this.departureLocation = departureLocation;
            this.geometry = geometry;
            this.distance = distance;
        }

        public double getDistance() {
            return distance;
        }

        public abstract Date getDepartureTime();
        public abstract Date getArrivalTime();
    }
    public static class Innerstop{
        public final String stopid;
        public final String stopname;
        public final Point geometry;

        public final Date arrivalTime;
        public final Date plannedArrivalTime;
        public final Date predictedArrivalTime;

        public Innerstop(String stopid, String stopname, Point geometry, Date arrivalTime, Date plannedArrivalTime, Date predictedArrivalTime) {
            this.stopid = stopid;
            this.stopname = stopname;
            this.geometry = geometry;
            this.arrivalTime = arrivalTime;
            this.plannedArrivalTime = plannedArrivalTime;
            this.predictedArrivalTime = predictedArrivalTime;
        }
    }

    public static class Stop {
        public final Innerstop innerstop;
        public final boolean arrivalCancelled;

        public final Date departureTime;
        public final Date plannedDepartureTime;
        public final Date predictedDepartureTime;
        public final boolean departureCancelled;

        public Stop(Innerstop innerstop, boolean arrivalCancelled, Date departureTime, Date plannedDepartureTime, Date predictedDepartureTime, boolean departureCancelled) {
            this.innerstop=innerstop;
            this.arrivalCancelled = arrivalCancelled;
            this.departureTime = departureTime;
            this.plannedDepartureTime = plannedDepartureTime;
            this.predictedDepartureTime = predictedDepartureTime;
            this.departureCancelled = departureCancelled;
        }

        @Override
        public String toString() {
            return "Stop{" +
                    "stop_id='" + innerstop.stopid + '\'' +
                    ", arrivalTime=" + innerstop.arrivalTime +
                    ", departureTime=" + departureTime +
                    '}';
        }
    }
    public static class WalkLeg extends Leg {
        public final InstructionList instructions;
        public final Map<String, List<PathDetail>> details;
        private final Date departureTime;
        private final Date arrivalTime;

        public WalkLeg(String departureLocation, Date departureTime, Geometry geometry, double distance, InstructionList instructions, Map<String, List<PathDetail>> details, Date arrivalTime) {
            super("walk", departureLocation, geometry, distance);
            this.instructions = instructions;
            this.departureTime = departureTime;
            this.details = details;
            this.arrivalTime = arrivalTime;
        }

        @Override
        public Date getDepartureTime() {
            return departureTime;
        }

        @Override
        public Date getArrivalTime() {
            return arrivalTime;
        }

    }
    public static class InternalPtLeg{
        public final String feedId;
        public final boolean isInSameVehicleAsPrevious;
        public final String tripId;
        public final String routeId;

        public InternalPtLeg(String feedId, boolean isInSameVehicleAsPrevious, String tripId, String routeId) {
            this.feedId = feedId;
            this.isInSameVehicleAsPrevious = isInSameVehicleAsPrevious;
            this.tripId = tripId;
            this.routeId = routeId;
        }
    }
    public static class PtLeg extends Leg {
        public final InternalPtLeg internalptleg;
        public final String tripHeadsign;
        public final long travelTime;
        public final List<Stop> stops;


        public PtLeg(InternalPtLeg internalptleg, String headsign, List<Stop> stops, double distance, long travelTime, Geometry geometry) {
            super("pt", stops.get(0).innerstop.stopname, geometry, distance);
            this.internalptleg=internalptleg;
            this.tripHeadsign = headsign;
            this.travelTime = travelTime;
            this.stops = stops;
        }

        @Override
        public Date getDepartureTime() {
            return stops.get(0).departureTime;
        }

        @Override
        public Date getArrivalTime() {
            return stops.get(stops.size()-1).innerstop.arrivalTime;
        }
    }

}
