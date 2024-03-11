package com.graphhopper.example;

import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;


 class LowLevelAPIExampleTest {

    @Test
     void main() {
        try {
            LowLevelAPIExample.main(null);
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        } catch (PrepareContractionHierarchies.PrepareExce e) {
            throw new RuntimeException(e);
        }
    }
    
}
