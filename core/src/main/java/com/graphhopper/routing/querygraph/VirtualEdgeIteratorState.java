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
package com.graphhopper.routing.querygraph;

import com.graphhopper.eccezionecore.PointPathException;
import com.graphhopper.routing.ev.*;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;

import java.util.List;

/**
 * Creates an edge state decoupled from a graph where nodes, pointList, etc are kept in memory.
 * <p>
 * Note, this class is not suited for public use and can change with minor releases unexpectedly or
 * even gets removed.
 */
public class VirtualEdgeIteratorState implements EdgeIteratorState {
    private final PointList pointList;
    private final int adjNode;
    private double distance;
    private IntsRef edgeFlags;
    private List<EdgeKVStorage.KeyValue> keyValues;
    // true if edge should be avoided as start/stop
    private boolean unfavored;
    private EdgeIteratorState reverseEdge;
    private final boolean reverse;
     InnerVirtual innervirtual;
    public static class InnerVirtual{
         int originalEdgeKey;
         int edgeKey;
         int baseNode;

        public InnerVirtual(int originalEdgeKey, int edgeKey, int baseNode) {
            this.originalEdgeKey = originalEdgeKey;
            this.edgeKey = edgeKey;
            this.baseNode = baseNode;
        }
    }
    public VirtualEdgeIteratorState(InnerVirtual innervirtual, int adjNode, double distance,
                                    IntsRef edgeFlags, List<EdgeKVStorage.KeyValue> keyValues, PointList pointList, boolean reverse) {
        this.innervirtual=innervirtual;
        this.adjNode = adjNode;
        this.distance = distance;
        this.edgeFlags = edgeFlags;
        this.keyValues = keyValues;
        this.pointList = pointList;
        this.reverse = reverse;
    }

    /**
     * This method returns the original (not virtual!) edge key. I.e. also the direction is
     * already correctly encoded.
     *
     * @see EdgeIteratorState#getEdgeKey()
     */
    public int getOriginalEdgeKey() {
        return innervirtual.originalEdgeKey;
    }

    @Override
    public int getEdge() {
        return GHUtility.getEdgeFromEdgeKey(innervirtual.edgeKey);
    }

    @Override
    public int getEdgeKey() {
        return innervirtual.edgeKey;
    }

    @Override
    public int getReverseEdgeKey() {
        return innervirtual.baseNode == adjNode ? innervirtual.edgeKey : GHUtility.reverseEdgeKey(innervirtual.edgeKey);
    }

    @Override
    public int getBaseNode() {
        return innervirtual.baseNode;
    }

    @Override
    public int getAdjNode() {
        return adjNode;
    }

    @Override
    public PointList fetchWayGeometry(FetchMode mode) {
        if (pointList.size() == 0)
            return PointList.EMPTY;
        // due to API we need to create a new instance per call!
        if (mode == FetchMode.TOWER_ONLY) {
            if (pointList.size() < 3)
                return pointList.clone(false);
            PointList towerNodes = new PointList(2, pointList.is3D());
            towerNodes.add(pointList, 0);
            towerNodes.add(pointList, pointList.size() - 1);
            return towerNodes;
        } else if (mode == FetchMode.ALL)
            return pointList.clone(false);
        else if (mode == FetchMode.BASE_AND_PILLAR)
            return pointList.copy(0, pointList.size() - 1);
        else if (mode == FetchMode.PILLAR_AND_ADJ)
            return pointList.copy(1, pointList.size());
        else if (mode == FetchMode.PILLAR_ONLY) {
            if (pointList.size() == 1)
                return PointList.EMPTY;
            return pointList.copy(1, pointList.size() - 1);
        }
        throw new UnsupportedOperationException("Illegal mode:" + mode);
    }

    @Override
    public EdgeIteratorState setWayGeometry(PointList list) {
        throw new UnsupportedOperationException("Not supported for virtual edge. Set when creating it.");
    }

    @Override
    public double getDistance() {
        return distance;
    }

    @Override
    public EdgeIteratorState setDistance(double dist) {
        this.distance = dist;
        return this;
    }

    @Override
    public IntsRef getFlags() {
        return edgeFlags;
    }

    @Override
    public EdgeIteratorState setFlags(IntsRef flags) {
        this.edgeFlags = flags;
        return this;
    }

    @Override
    public boolean get(BooleanEncodedValue property) {
        if (property == EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored;

        return property.getBool(reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
        property.setBool(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public boolean getReverse(BooleanEncodedValue property) {
        if (property == EdgeIteratorState.UNFAVORED_EDGE)
            return unfavored;
        return property.getBool(!reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
        property.setBool(!reverse, edgeFlags, value);
        return this;
    }
    private static final String ENINO ="EncodedValue ";
    private static final String ENUNO =" supports only one direction";
    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException(ENINO + property.getName() + ENUNO);
        property.setBool(reverse, edgeFlags, fwd);
        property.setBool(!reverse, edgeFlags, bwd);
        return this;
    }

    @Override
    public int get(IntEncodedValue property) {
        return property.getInt(reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int value) {
        property.setInt(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public int getReverse(IntEncodedValue property) {
        return property.getInt(!reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
        property.setInt(!reverse, edgeFlags, value);
        return this;
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException(ENINO + property.getName() + ENUNO);
        property.setInt(reverse, edgeFlags, fwd);
        property.setInt(!reverse, edgeFlags, bwd);
        return this;
    }

    @Override
    public double get(DecimalEncodedValue property) {
        return property.getDecimal(reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
        property.setDecimal(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public double getReverse(DecimalEncodedValue property) {
        return property.getDecimal(!reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
        property.setDecimal(!reverse, edgeFlags, value);
        return this;
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException(ENINO + property.getName() + ENUNO);
        property.setDecimal(reverse, edgeFlags, fwd);
        property.setDecimal(!reverse, edgeFlags, bwd);
        return this;
    }

    @Override
    public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
        return property.getEnum(reverse, edgeFlags);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
        property.setEnum(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
        return property.getEnum(!reverse, edgeFlags);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
        property.setEnum(!reverse, edgeFlags, value);
        return this;
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException(ENINO + property.getName() + ENUNO);
        property.setEnum(reverse, edgeFlags, fwd);
        property.setEnum(!reverse, edgeFlags, bwd);
        return this;
    }

    @Override
    public String get(StringEncodedValue property) {
        return property.getString(reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String value) {
        property.setString(reverse, edgeFlags, value);
        return this;
    }

    @Override
    public String getReverse(StringEncodedValue property) {
        return property.getString(!reverse, edgeFlags);
    }

    @Override
    public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
        property.setString(!reverse, edgeFlags, value);
        return this;
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
        if (!property.isStoreTwoDirections())
            throw new IllegalArgumentException(ENINO + property.getName() + ENUNO);
        property.setString(reverse, edgeFlags, fwd);
        property.setString(!reverse, edgeFlags, bwd);
        return this;
    }

    @Override
    public String getName() {
        String name = (String) getValue(EdgeKVStorage.KeyValue.STREET_NAME);
        // preserve backward compatibility (returns empty string if name tag missing)
        return name == null ? "" : name;
    }

    @Override
    public EdgeIteratorState setKeyValues(List<EdgeKVStorage.KeyValue> list) {
        this.keyValues = list;
        return this;
    }

    @Override
    public List<EdgeKVStorage.KeyValue> getKeyValues() {
        return keyValues;
    }

    @Override
    public Object getValue(String key) {
        for (EdgeKVStorage.KeyValue keyValue : keyValues) {
            if (keyValue.getKey().equals(key)) return keyValue.getValue();
        }
        return null;
    }

    /**
     * This method sets edge to unfavored status for routing from the start or to the stop location.
     */
    public void setUnfavored(boolean unfavored) {
        this.unfavored = unfavored;
    }

    @Override
    public String toString() {
        return innervirtual.baseNode + "->" + adjNode;
    }

    @Override
    public EdgeIteratorState detach(boolean reverse) {
        if (reverse) {
            // update properties of reverse edge
            reverseEdge.setFlags(getFlags());
            reverseEdge.setKeyValues(getKeyValues());
            reverseEdge.setDistance(getDistance());
            return reverseEdge;
        } else {
            return this;
        }
    }

    @Override
    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState fromEdge) {
        try {
            throw new PointPathException();
        } catch (PointPathException e) {
            //nothing
        }
        return fromEdge;
    }

    public void setReverseEdge(EdgeIteratorState reverseEdge) {
        this.reverseEdge = reverseEdge;
    }

}
