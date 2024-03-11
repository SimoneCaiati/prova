package com.graphhopper.gtfs;

import com.google.transit.realtime.GtfsRealtime;

public class PtEdgeAttributes {


    public final Classe2 classe2;

     int routeType;
    protected GtfsStorage.FeedIdWithTimezone feedIdWithTimezone;
     int transfers;

    public int getTransfers() {
        return transfers;
    }

    int stopSequence;

    public int getstopSequence() {
        return stopSequence;
    }

    protected GtfsRealtime.TripDescriptor tripDescriptor;

    public GtfsStorage.PlatformDescriptor getPlatformDescriptor() {
        return platformDescriptor;
    }

    protected GtfsStorage.PlatformDescriptor platformDescriptor;

    @Override
    public String toString() {
        return "PtEdgeAttributes{" +
                "type=" + classe2.type +
                ", time=" + classe2.time +
                ", transfers=" + transfers +
                '}';
    }

    public static class Classe2
    {
        GtfsStorage.EdgeType type;
        int time;
        GtfsStorage.Validity validity;

        public Classe2(GtfsStorage.EdgeType type, int time, GtfsStorage.Validity validity) {
            this.type = type;
            this.time = time;
            this.validity = validity;
        }
    }

    public PtEdgeAttributes(Classe2 classe2, int routeType, GtfsStorage.FeedIdWithTimezone feedIdWithTimezone, int transfers, int stopSequence, GtfsRealtime.TripDescriptor tripDescriptor, GtfsStorage.PlatformDescriptor platformDescriptor) {

        this.classe2= classe2;
        this.routeType = routeType;
        this.feedIdWithTimezone = feedIdWithTimezone;
        this.transfers = transfers;
        this.stopSequence = stopSequence;
        this.tripDescriptor = tripDescriptor;
        this.platformDescriptor = platformDescriptor;
    }

}
