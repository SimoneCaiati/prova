package com.graphhopper.reader.dem;

import com.graphhopper.eccezionecore.threadException;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("java:S5976")
 class EdgeSamplingTest {
    private final ElevationProvider elevation = new ElevationProvider() {
        @Override
        public double getEle(double lat, double lon) {
            return 10;
        }

        @Override
        public boolean canInterpolate() {
            return false;
        }

        @Override
        public void release() {
        }

    };

    private double round(double d) {
        return Math.round(d * 1000) / 1000.0;
    }

    private PointList round(PointList list) {
        for (int i = 0; i < list.size(); i++) {
            list.set(i, round(list.getLat(i)), round(list.getLon(i)), list.getEle(i));
        }
        return list;
    }

    @Test
     void doesNotAddExtraPointBelowThreshold() throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(1.4, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (1.4,0.0,0.0)", round(out).toString());
    }

    @Test
     void addsExtraPointAboveThreshold() throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(0.8, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE / 2,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (0.4,0.0,10.0), (0.8,0.0,0.0)", round(out).toString());
    }

    @Test
     void addsExtraPointBelowSecondThreshold() throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(0.8, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE / 3,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (0.4,0.0,10.0), (0.8,0.0,0.0)", round(out).toString());
    }

    @Test
     void addsTwoPointsAboveThreshold() throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        PointList in = new PointList(2, true);
        in.add(0, 0, 0);
        in.add(0.75, 0, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE / 4,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,0.0,0.0), (0.25,0.0,10.0), (0.5,0.0,10.0), (0.75,0.0,0.0)", round(out).toString());
    }

    @Test
     void doesntAddPointsCrossingInternationalDateLine() throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        PointList in = new PointList(2, true);
        in.add(0, -178.5, 0);
        in.add(0.0, 178.5, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(0.0,-178.5,0.0), (0.0,-179.5,10.0), (0.0,179.5,10.0), (0.0,178.5,0.0)", round(out).toString());
    }

    @Test
     void usesGreatCircleInterpolationOnLongPaths() throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        PointList in = new PointList(2, true);
        in.add(88.5, -90, 0);
        in.add(88.5, 90, 0);

        PointList out = EdgeSampling.sample(
                in,
                DistanceCalcEarth.METERS_PER_DEGREE,
                new DistanceCalcEarth(),
                elevation
        );

        assertEquals("(88.5,-90.0,0.0), (89.5,-90.0,10.0), (89.5,90.0,10.0), (88.5,90.0,0.0)", round(out).toString());
    }

}