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

package com.graphhopper.isochrone.algorithm;

import com.carrotsearch.hppc.IntObjectHashMap;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.locationtech.jts.util.Assert;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Triangulation {

    Map<String, QuadEdge> edges = new HashMap<>();
    IntObjectHashMap<Vertex> vertices = new IntObjectHashMap<>();

    public IntObjectHashMap<QuadEdge> getVertexQuadEdges() {
        return vertexQuadEdges;
    }

    IntObjectHashMap<QuadEdge> vertexQuadEdges = new IntObjectHashMap<>();

    public QuadEdge getEdge(int o, int d) {
        if (o < d) {
            return edges.get(o + "," + d);
        } else {
            QuadEdge quadEdge = edges.get(d + "," + o);
            return quadEdge != null ? quadEdge.sym() : null;
        }

    }

    private void putEdge(int o, int d, QuadEdge quadEdge) {
        vertexQuadEdges.put(o, quadEdge);
        if (o < d) {
            edges.put(o + "," + d, quadEdge);
        } else {
            edges.put(d + "," + o, quadEdge.sym());
        }
    }

    public void makeTriangle(int v1, int v2, int v3) throws TrianExce {
        QuadEdge e1 = getEdge(v1, v2);
        QuadEdge e2 = getEdge(v2, v3);
        QuadEdge e3 = getEdge(v3, v1);
        if (e1 == null && e2 != null) {
            makeTriangle(v2, v3, v1, e2, e3, e1);
        } else if (e2 == null && e3 != null) {
            makeTriangle(v3, v1, v2, e3, e1, e2);
        } else {
            makeTriangle(v1, v2, v3, e1, e2, e3);
        }
    }

    private void makeTriangle(int valore1, int valore2, int valore3, QuadEdge eno1, QuadEdge eno2, QuadEdge eno3) throws TrianExce {
        if (eno1 == null) {
            eno1 = QuadEdge.makeEdge(getVertex(valore1), getVertex(valore2));
            putEdge(valore1, valore2, eno1);
            putEdge(valore2, valore1, eno1.sym());
        }
        if (eno2 == null) {
            eno2 = QuadEdge.makeEdge(getVertex(valore2), getVertex(valore3));
            QuadEdge.splice(eno1.lNext(), eno2);
            putEdge(valore2, valore3, eno2);
            putEdge(valore3, valore2, eno2.sym());
        }
        if (eno3 == null) {
            if (eno1.lNext() == eno2) {
                eno3 = QuadEdge.connect(eno2, eno1);
            } else if (eno2.lNext() == eno1) {
                throw new TrianExce();
            } else {
                QuadEdge.splice(eno1.lNext(), eno2);
                eno3 = QuadEdge.connect(eno2, eno1);
            }
            putEdge(valore3, valore1, eno3);
            putEdge(valore1, valore3, eno3.sym());
        } else {
            if (eno1.lNext() != eno2) {
                QuadEdge.splice(eno1.lNext(), eno2);
            }
            if (eno2.lNext() != eno3) {
                QuadEdge.splice(eno2.lNext(), eno3);
            }
            if (eno3.lNext() != eno1) {
                QuadEdge.splice(eno3.lNext(), eno1);
            }
        }
        assertTriangle(eno1, eno2, eno3);
    }

    private Vertex getVertex(int v3) {
        return vertices.get(v3);
    }

    public IntObjectHashMap<Vertex> getVertices() {
        return vertices;
    }

    public Collection<ReadableQuadEdge> getEdges() {
        return edges.values().stream().map(QuadEdgeAsReadableQuadEdge::new).collect(Collectors.toList());
    }

    public void assertTriangle(QuadEdge e1, QuadEdge e2, QuadEdge e3) {
        Assert.equals(e2, e1.lNext());
        Assert.equals(e3, e2.lNext());
        Assert.equals(e1, e3.lNext());
    }

    public void assertTriangle(int v1, int v2, int v3) {
        assertTriangle(getEdge(v1, v2), getEdge(v2, v3), getEdge(v3, v1));
    }

    class TrianExce extends Exception {
    }
}
