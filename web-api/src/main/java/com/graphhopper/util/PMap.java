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
package com.graphhopper.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A properties map (String to Object) with convenient methods to access the content.
 * <p>
 *
 * @author Peter Karich
 */
public class PMap {
    private final LinkedHashMap<String, Object> map;

    public PMap() {
        this(5);
    }

    public PMap(int capacity) {
        this.map = new LinkedHashMap<>(capacity);
    }

    public PMap(Map<String, Object> map) {
        this.map = new LinkedHashMap<>(map);
    }

    public PMap(PMap map) {
        this.map = new LinkedHashMap<>(map.map);
    }

    public PMap(String propertiesString) {
        // five chosen as arbitrary initial capacity
        this.map = new LinkedHashMap<>(5);

        for (String s : propertiesString.split("\\|")) {
            s = s.trim();
            int index = s.indexOf("=");
            if (index < 0)
                continue;

            put(s.substring(0, index), s.substring(index + 1));
        }
    }

    /**
     * Reads a PMap from a string array consisting of key=value pairs
     */
    public static PMap read(String[] args) {
        PMap map = new PMap();
        for (String arg : args) {
            int index = arg.indexOf("=");
            if (index <= 0) {
                continue;
            }

            String key = arg.substring(0, index);
            if (key.startsWith("-")) {
                key = key.substring(1);
            }

            if (key.startsWith("-")) {
                key = key.substring(1);
            }

            String value = arg.substring(index + 1);
            Object old = map.map.put(Helper.camelCaseToUnderScore(key), Helper.toObject(value));
            if (old != null)
                throw new IllegalArgumentException("Pair '" + Helper.camelCaseToUnderScore(key) + "'='" + value + "' not possible to " +
                        "add to the PMap-object as the key already exists with '" + old + "'");
        }
        return map;
    }

    public PMap putAll(PMap map) {
        this.map.putAll(map.map);
        return this;
    }


    public PMap put(String key, String str) {
        if (str == null)
            throw new NullPointerException("Value cannot be null. Use remove instead.");
        map.put(Helper.camelCaseToUnderScore(key), Helper.toObject(str));
        return this;
    }

    public PMap remove(String key) {
        map.remove(key);
        return this;
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public boolean getBool(String key, boolean b) {
        Object object = map.get(key);
        return object instanceof Boolean ? (Boolean) object : b;
    }

    public int getInt(String key, int i) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).intValue() : i;
    }

    public long getLong(String key, long l) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).longValue() : l;
    }

    public float getFloat(String key, float def) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).floatValue() : def;
    }

    public double getDouble(String key, double def) {
        Object object = map.get(key);
        return object instanceof Number ? ((Number) object).doubleValue() : def;
    }

    public String getString(String key, String s) {
        Object object = map.get(key);
        return object instanceof String ? (String) object : s;
    }

    public <T> T getObject(String key, T t) {
        Object object = map.get(key);
        return object == null ? t : (T) object;
    }

    public PMap putObject(String key, Object object) {
        map.put(key, object);
        return this;
    }

    /**
     * This method copies the underlying structure into a new Map object
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(map);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
