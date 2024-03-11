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

package com.graphhopper.reader.dem;

import com.graphhopper.eccezionecore.threadException;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HGTProviderTest {

    @Disabled("boh")
    @Test
    void getEle() throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce, TileBasedElevationProvider.ElevationExce {
        HGTProvider hgt = new HGTProvider("/your/path/to/hgt/");
        assertEquals(511, hgt.getEle(49.1, 11.7), 1);
        assertEquals(0, hgt.getEle(0.6, 0.6), 1);
    }

}