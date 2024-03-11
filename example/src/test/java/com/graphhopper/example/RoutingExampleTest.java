package com.graphhopper.example;

import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

 class RoutingExampleTest {

    @Test
     void main() throws RoutingExample.ExamExce, RoutingExampleTC.ExamExce1, TranslationMap.TransExce {
        Helper.removeDir(new File("target/routing-graph-cache"));
        RoutingExample.main(new String[]{"../"});

        // Verifica che la directory di cache del grafo sia stata creata correttamente
        assertTrue(new File("target/routing-graph-cache").exists());

        Helper.removeDir(new File("target/routing-tc-graph-cache"));
        RoutingExampleTC.main(new String[]{"../"});

        // Verifica che la directory di cache del grafo transitive closure sia stata creata correttamente
        assertTrue(new File("target/routing-tc-graph-cache").exists());
    }

}
