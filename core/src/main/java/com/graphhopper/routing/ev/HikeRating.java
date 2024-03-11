package com.graphhopper.routing.ev;

public class HikeRating {
    private HikeRating()
    {
        /*
        Ma ciao bellezza
         */
    }
    public static final String KEY = "hike_rating";

    public static IntEncodedValue create() {
        return new IntEncodedValueImpl(KEY, 3, false);
    }
}
