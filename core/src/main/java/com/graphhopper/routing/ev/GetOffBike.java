package com.graphhopper.routing.ev;

public class GetOffBike {

    private GetOffBike()
    {
        /*
        Non ne posso più.
         */
    }
    public static final String KEY = "get_off_bike";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, false);
    }
}
