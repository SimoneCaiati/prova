/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.coll;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
 class GHLongIntBTreeTest {
    @Test
     void testThrowException_IfPutting_NoNumber() {
        GHLongIntBTree instance = new GHLongIntBTree(2);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> instance.put(-1, 1));
        assertTrue(ex.getMessage().contains("Illegal key -1"));
    }

    @Test
     void testEmptyValueIfMissing() {
        GHLongIntBTree instance = new GHLongIntBTree(2);
        long key = 9485854858458484L;
        instance.put(key, 21);
        assertEquals(21, instance.get(key));
        assertEquals(-1, instance.get(404));
    }

    @Test
     void testTwoSplits() {
        GHLongIntBTree instance = new GHLongIntBTree(3);
        instance.put(1, 2);
        instance.put(2, 4);
        instance.put(3, 6);

        assertEquals(1, instance.height());
        instance.put(4, 8);
        assertEquals(2, instance.height());

        instance.put(5, 10);
        instance.put(6, 12);
        instance.put(7, 14);
        instance.put(8, 16);
        instance.put(9, 18);

        assertEquals(2, instance.height());
        instance.put(10, 20);
        assertEquals(3, instance.height());

        assertEquals(3, instance.height());
        assertEquals(10, instance.getSize());
        assertEquals(0, instance.getMemoryUsage());

        check(instance, 1);
    }

    @Test
     void testSplitAndOverwrite() {
        GHLongIntBTree instance = new GHLongIntBTree(3);
        instance.put(1, 2);
        instance.put(2, 4);
        instance.put(3, 6);
        instance.put(2, 5);

        assertEquals(3, instance.getSize());
        assertEquals(1, instance.height());

        assertEquals(5, instance.get(2));
        assertEquals(6, instance.get(3));
    }

    void check(GHLongIntBTree instance, int from) {
        for (int i = from; i < instance.getSize(); i++) {
            assertEquals(i * 2, instance.get(i));
        }
    }

    @Test
     void testPut() {
        GHLongIntBTree instance = new GHLongIntBTree(3);
        instance.put(2, 4);
        instance.put(7, 14);
        instance.put(5, 10);
        instance.put(6, 12);
        instance.put(3, 6);
        instance.put(4, 8);
        instance.put(9, 18);
        instance.put(0, 0);

        instance.put(1, 2);
        instance.put(8, 16);

        check(instance, 0);

        instance.put(10, 20);
        instance.put(11, 22);

        assertEquals(12, instance.getSize());
        assertEquals(3, instance.height());

        assertEquals(12, instance.get(6));
        check(instance, 0);
    }

    @Test
     void testUpdate() {
        GHLongIntBTree instance = new GHLongIntBTree(2);
        int result = instance.put(100, 10);
        assertEquals(instance.getNoNumberValue(), result);

        result = instance.get(100);
        assertEquals(10, result);

        result = instance.put(100, 9);
        assertEquals(10, result);

        result = instance.get(100);
        assertEquals(9, result);
    }

    @Test
     void testRandom() {
        for (int j = 3; j < 12; j += 4) {
            GHLongIntBTree instance = new GHLongIntBTree(j);
            final int size = 500;
            final long seed = System.nanoTime();
            Random rand = new Random(seed);
            Set<Integer> addedValues = new LinkedHashSet<>(size);
            for (int i = 0; i < size; i++) {
                int val = rand.nextInt(size);
                addedValues.add(val);
                try {
                    instance.put(val, val);
//                    System.out.println(i + "--------------" + val);
//                    instance.print();
//                    System.out.println("\n\n");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    fail(j + "| Problem with " + i + ", seed: " + seed + " " + ex);
                }

                assertEquals(addedValues.size(), instance.getSize(), j + "| Size not equal to set! In " + i + " added " + val);
            }
            int i = 0;
            for (int val : addedValues) {
                assertEquals(val, instance.get(val), j + "| Problem with " + i);
                i++;
            }
            instance.optimize();
            i = 0;
            for (int val : addedValues) {
                assertEquals(val, instance.get(val), j + "| Problem with " + i);
                i++;
            }
        }
    }
}
