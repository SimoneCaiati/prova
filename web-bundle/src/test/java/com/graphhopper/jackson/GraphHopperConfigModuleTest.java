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

package com.graphhopper.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GraphHopperConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

 class GraphHopperConfigModuleTest {

    @Test
     void testDeserializeConfig() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        GraphHopperConfig graphHopperConfig = objectMapper.readValue(getClass().getResourceAsStream("config.yml"), GraphHopperConfig.class);
        // The dot in the key is no special symbol in YAML. It's just part of the string.
        Assertions.assertEquals(100,graphHopperConfig.getInt("index.max_region_search", 0));
        // So when I think this refers to a YAML hierarchy, I'll be disappointed!
        Assertions.assertEquals(0,graphHopperConfig.getInt("index.pups", 0)); // sadly

        // Note: This also doesn't work in DropWizard's config. It's just not a feature. It does work in Spring,
        // but because Spring does it, not because YAML does it.
    }

}
