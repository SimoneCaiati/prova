package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.weighting.custom.FindMinMax.findMinMax;
import static org.junit.jupiter.api.Assertions.*;

class FindMinMaxTest {

    private EncodedValueLookup lookup;

    @BeforeEach
    void setup() {
        lookup = new EncodingManager.Builder().build();
    }

    @Test
     void testCheck() {
        CustomModel queryModel = new CustomModel();
        queryModel.addToPriority(if1("max_width < 3", MULTIPLY, "10"));
        assertEquals(1, CustomModel.merge(new CustomModel(), queryModel).getPriority().size());
        // priority bigger than 1 is not ok for CustomModel of query
        IllegalArgumentException ex = null;
        try {
            FindMinMax.checkLMConstraints(new CustomModel(), queryModel, lookup);
        } catch (IllegalArgumentException e) {
            ex = e;
        }

        assertNotNull(ex);
    }

    @Test
     void testFindMax() {
        List<Statement> statements = new ArrayList<>();
        statements.add(if1("true", LIMIT, "100"));
        assertEquals(100, findMinMax(new HashSet<>(), new MinMax(0, 120), statements, lookup).getMax());

        statements.add(else1(LIMIT, "20"));
        assertEquals(100, findMinMax(new HashSet<>(), new MinMax(0, 120), statements, lookup).getMax());

        statements = new ArrayList<>();
        statements.add(if1("road_environment == BRIDGE", LIMIT, "85"));
        statements.add(else1(LIMIT, "100"));
        assertEquals(100, findMinMax(new HashSet<>(), new MinMax(0, 120), statements, lookup).getMax());

        // find bigger speed than stored max_speed (30) in server-side custom_models
        statements = new ArrayList<>();
        statements.add(if1("true", MULTIPLY, "2"));
        statements.add(if1("true", LIMIT, "35"));
        assertEquals(35, findMinMax(new HashSet<>(), new MinMax(0, 30), statements, lookup).getMax());
    }

    @Test
     void findMax_limitAndMultiply() {
        List<Statement> statements = Arrays.asList(
                if1("road_class == TERTIARY", LIMIT, "90"),
                elseIf("road_class == SECONDARY", MULTIPLY, "1.0"),
                elseIf("road_class == PRIMARY", LIMIT, "30"),
                else1(LIMIT, "3")
        );
        assertEquals(140, findMinMax(new HashSet<>(), new MinMax(0, 140), statements, lookup).getMax());
    }

    @Test
     void testFindMaxPriority() {
        List<Statement> statements = new ArrayList<>();
        statements.add(if1("true", MULTIPLY, "2"));
        assertEquals(2, findMinMax(new HashSet<>(), new MinMax(0, 1), statements, lookup).getMax());

        statements = new ArrayList<>();
        statements.add(if1("true", MULTIPLY, "0.5"));
        assertEquals(0.5, findMinMax(new HashSet<>(), new MinMax(0, 1), statements, lookup).getMax());

        statements = new ArrayList<>();
        statements.add(if1("road_class == MOTORWAY", MULTIPLY, "0.5"));
        statements.add(else1(MULTIPLY, "-0.5"));
        MinMax minMax = findMinMax(new HashSet<>(), new MinMax(1, 1), statements, lookup);
        assertEquals(-0.5, minMax.getMin());
        assertEquals(0.5, minMax.getMax());
    }

    @Test
     void findMax_multipleBlocks() {
        List<Statement> statements = Arrays.asList(
                if1("road_class == TERTIARY", MULTIPLY, "0.2"),
                elseIf("road_class == SECONDARY", LIMIT, "25"),
                if1("road_environment == TUNNEL", LIMIT, "60"),
                elseIf("road_environment == BRIDGE", LIMIT, "50"),
                else1(MULTIPLY, "0.8")
        );
        assertEquals(120, findMinMax(new HashSet<>(), new MinMax(0, 150), statements, lookup).getMax());
        assertEquals(80, findMinMax(new HashSet<>(), new MinMax(0, 100), statements, lookup).getMax());
        assertEquals(60, findMinMax(new HashSet<>(), new MinMax(0, 60), statements, lookup).getMax());

        statements = Arrays.asList(
                if1("road_class == TERTIARY", MULTIPLY, "0.2"),
                elseIf("road_class == SECONDARY", LIMIT, "25"),
                else1(LIMIT, "40"),
                if1("road_environment == TUNNEL", MULTIPLY, "0.8"),
                elseIf("road_environment == BRIDGE", LIMIT, "30")
        );
        assertEquals(40, findMinMax(new HashSet<>(), new MinMax(0, 150), statements, lookup).getMax());
        assertEquals(40, findMinMax(new HashSet<>(), new MinMax(0, 40), statements, lookup).getMax());
    }
}