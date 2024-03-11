package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;


class LocationIndexExampleTest {

    @Test
     void main() {
        try {
            LocationIndexExample.main(new String[]{"../"});
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

}