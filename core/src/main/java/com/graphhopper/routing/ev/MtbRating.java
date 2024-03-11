package com.graphhopper.routing.ev;

public class MtbRating {

    private MtbRating()
    {
        /*
        ciao
         */
    }
    public static final String KEY = "mtb_rating";

    public static IntEncodedValue create() {
        return new IntEncodedValueImpl(KEY, 3, false);
    }
}
