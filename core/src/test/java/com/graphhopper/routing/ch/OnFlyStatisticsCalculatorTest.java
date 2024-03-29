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
package com.graphhopper.routing.ch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

 class OnFlyStatisticsCalculatorTest {

    @Test
     void testOnFlyMeanAndVarianceCalculation() {
        OnFlyStatisticsCalculator calc = new OnFlyStatisticsCalculator();
        calc.addObservation(5);
        calc.addObservation(7);
        calc.addObservation(10);
        calc.addObservation(12);
        calc.addObservation(17);
        assertEquals(10.2, calc.getMean(), 1.e-6);
        assertEquals(17.36, calc.getVariance(), 1.e-6);

    }

}