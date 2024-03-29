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
package com.graphhopper.routing.ev;

/**
 * This EncodedValue, which should be available per profile, stores a boolean value per edge that indicates if the edge
 * is part of a too small subnetwork.
 *
 * @see com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks
 */
public class Subnetwork {
    private Subnetwork(){
        //
    }
    public static String key(String prefix) {
        if (prefix.contains("subnetwork"))
            throw new IllegalArgumentException("Cannot create key containing 'subnetwork' in prefix: " + prefix);
        return prefix + "_subnetwork";
    }

    public static BooleanEncodedValue create(String prefix) {
        return new SimpleBooleanEncodedValue(key(prefix));
    }
}

