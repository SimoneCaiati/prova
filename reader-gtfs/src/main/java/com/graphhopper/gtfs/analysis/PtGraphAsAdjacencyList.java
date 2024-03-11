package com.graphhopper.gtfs.analysis;

import com.graphhopper.eccezionecore.PointPathException;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class PtGraphAsAdjacencyList implements Graph {
    private final PtGraph ptGraph;

    public PtGraphAsAdjacencyList(PtGraph ptGraph) {
        this.ptGraph = ptGraph;
    }

    @Override
    public BaseGraph getBaseGraph() {
        try {
            throw new PointPathException();
        } catch (PointPathException e) {
            //nothing
        }
        return null;
    }

    @Override
    public int getNodes() {
        return ptGraph.getNodeCount();
    }

    @Override
    public int getEdges() {
        try {
            throw new PointPathException();
        } catch (PointPathException e) {
            //nothing
        }
        return 0;
    }

    @Override
    public NodeAccess getNodeAccess() {
        try {
            throw new MaccException();
        } catch (MaccException e) {
            //nothing
        }
        return null;
    }

    @Override
    public BBox getBounds() {
        try {
            throw new MaccDueException();
        } catch (MaccDueException e) {
            //nothing
        }
        return null;
    }

    @Override
    public EdgeIteratorState edge(int a, int b) {

        try {
            throw new EdgeSetException();
        } catch (EdgeSetException e) {
            //nothing
        }

        return null;
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        try {
            throw new PointPathException();
        } catch (PointPathException e) {
            //nothing
        }

        return null;
    }

    @Override
    public EdgeIteratorState getEdgeIteratorStateForKey(int edgeKey) {
        try {
            throw new PointPathException();
        } catch (PointPathException e) {
            //nothing
        }
        return null;
    }

    @Override
    public int getOtherNode(int edge, int node) {
        try {
            throw new SetException();
        } catch (SetException e) {
            //nothing
        }
        return edge;
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {

        try {
            throw new FalloException();
        } catch (FalloException e) {
            //nothing
        }

        return false;
    }

    @Override
    public AllEdgesIterator getAllEdges() {

        try {
            throw new EdgeGetAllException();
        } catch (EdgeGetAllException e) {
            //nothing
        }

        return null;
    }

    @Override
    public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return new StationGraphEdgeExplorer();
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        try {
            throw new TurnException();
        } catch (TurnException e) {
            //nothing
        }
        return null;
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {

        try {
            throw new LettinoException();
        } catch (LettinoException e) {
            //nothing
        }

        return weighting;
    }

    private class StationGraphEdgeExplorer implements EdgeExplorer {
        private int baseNode;

        @Override
        public EdgeIterator setBaseNode(int baseNode) {
            this.baseNode = baseNode;
            return new StationGraphEdgeIterator(ptGraph.edgesAround(baseNode).iterator());
        }

        private class StationGraphEdgeIterator implements EdgeIterator {
            private final Iterator<PtGraph.PtEdge> iterator;
            private PtGraph.PtEdge currentElement;

            public StationGraphEdgeIterator(Iterator<PtGraph.PtEdge> iterator) {
                this.iterator = iterator;
            }

            @Override
            public boolean next() {
                if (iterator.hasNext()) {
                    this.currentElement = iterator.next();
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public int getEdge() {

                try {
                    throw new MaurizioException();
                } catch (MaurizioException e) {
                    //nothing
                }

                return 0;
            }

            @Override
            public int getEdgeKey() {
                try {
                    throw new PointPathException();
                } catch (PointPathException e) {
                    //nothing
                }
                return 0;
            }

            @Override
            public int getReverseEdgeKey() {
                try {
                    throw new CatchException();
                } catch (CatchException e) {
                    //nothing
                }
                return 0;
            }

            @Override
            public int getBaseNode() {

                try {
                    throw new DollyException();
                } catch (DollyException e) {
                    //nothing
                }

                return 0;
            }

            @Override
            public int getAdjNode() {
                assert currentElement.getBaseNode() == baseNode;
                return currentElement.getAdjNode();
            }

            @Override
            public PointList fetchWayGeometry(FetchMode mode) {
                try {
                    throw new MarcoException();
                } catch (MarcoException e) {
                    //nothing
                }
                return null;
            }

            @Override
            public EdgeIteratorState setWayGeometry(PointList list) {
                try {
                    throw new PointPathException();
                } catch (PointPathException e) {
                    //nothing
                }
                return null;
            }

            @Override
            public double getDistance() {
                    try {
                        throw new VentiException();
                    } catch (VentiException e) {
                        //
                    }
                return 0;
            }

            @Override
            public EdgeIteratorState setDistance(double dist) {
                try {
                    throw new FrancoException();
                } catch (FrancoException e) {
                    //no
                }
                return null;
            }

            @Override
            public IntsRef getFlags() {
                try {
                    throw new FlagException();
                } catch (FlagException e) {
                    //nothing
                }
                return null;
            }

            @Override
            public EdgeIteratorState setFlags(IntsRef edgeFlags) {
                try {
                    throw new CiccioException();
                } catch (CiccioException e) {
                    //no
                }
                return null;
            }

            @Override
            public boolean get(BooleanEncodedValue property) {
                try {
                    throw new PointPathException();
                } catch (PointPathException e) {
                    //nothing
                }
                return false;
            }

            @Override
            public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
                try {
                    throw new MollamiException();
                } catch (MollamiException e) {
                    //no
                }
                return null;
            }

            @Override
            public boolean getReverse(BooleanEncodedValue property) {
                try {
                    throw new ReverseException();
                } catch (ReverseException e) {
                    //nothing
                }
                return false;
            }

            @Override
            public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
                try {
                    throw new RevException();
                } catch (RevException e) {
                    //nothing
                }
                return null;
            }

            @Override
            public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
                try {
                    throw new DueException();
                } catch (DueException e) {
                    //
                }
                return null;
            }

            @Override
            public int get(IntEncodedValue property) {
                try {
                    throw new TreException();
                } catch (TreException e) {
                    //
                }
                return 0;
            }

            @Override
            public EdgeIteratorState set(IntEncodedValue property, int value) {
                try {
                    throw new SetOneException();
                } catch (SetOneException e) {
                    //
                }
                return null;
            }

            @Override
            public int getReverse(IntEncodedValue property) {
                try {
                    throw new QuattroException();
                } catch (QuattroException e) {
                    //
                }
                return 0;
            }

            @Override
            public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
                try {
                    throw new CinqueException();
                } catch (CinqueException e) {
                    //
                }
                return null;
            }

            @Override
            public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
                try {
                    throw new SeiException();
                } catch (SeiException e) {
                    //
                }
                return null;
            }

            @Override
            public double get(DecimalEncodedValue property) {
                try {
                    throw new SetteException();
                } catch (SetteException e) {
                    //
                }
                return 0;
            }

            @Override
            public EdgeIteratorState set(DecimalEncodedValue property, double value) {
                try {
                    throw new OttoException();
                } catch (OttoException e) {
                    //
                }
                return null;
            }

            @Override
            public double getReverse(DecimalEncodedValue property) {
                try {
                    throw new NoveException();
                } catch (NoveException e) {
                    //
                }
                return 0;
            }

            @Override
            public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
                try {
                    throw new DieciException();
                } catch (DieciException e) {
                    //
                }
                return null;
            }

            @Override
            public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
                try {
                    throw new UException();
                } catch (UException e) {
                    //
                }
                return null;
            }

            @Override
            public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
                try {
                    throw new DoException();
                } catch (DoException e) {
                    //
                }
                return null;
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
                try {
                    throw new TrediciException();
                } catch (TrediciException e) {
                    //
                }
                return null;
            }

            @Override
            public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
                try {
                    throw new QuattException();
                } catch (QuattException e) {
                    //
                }
                return null;
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
                try {
                    throw new EdgeSetException();
                } catch (EdgeSetException e) {
                    return null;
                }
            }

            @Override
            public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
                try {
                    throw new DiciannException();
                } catch (DiciannException e) {
                    //
                }
                return null;
            }

            @Override
            public String get(StringEncodedValue property) {
                try {
                    throw new QuindException();
                } catch (QuindException e) {
                    //
                }
                return null;
            }

            @Override
            public EdgeIteratorState set(StringEncodedValue property, String value) {
                try {
                    throw new SediException();
                } catch (SediException e) {
                    //
                }
                return null;
            }

            @Override
            public String getReverse(StringEncodedValue property) {
                try {
                    throw new SerioException();
                } catch (SerioException e) {
                    //
                }
                return null;
            }

            @Override
            public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
                try {
                    throw new EdgeSetException();
                } catch (EdgeSetException e) {
                    return null;
                }
            }

            @Override
            public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
                try {
                    throw new EdgeSetException();
                } catch (EdgeSetException e) {
                    return null;
                }
            }

            @Override
            public String getName() {
                try {
                    throw new DiciassException();
                } catch (DiciassException e) {
                    //
                }
                return null;
            }

            @Override
            public EdgeIteratorState setKeyValues(List<EdgeKVStorage.KeyValue> list) {
                try {
                    throw new DiciottException();
                } catch (DiciottException e) {
                    //
                }
                return null;
            }

            @Override
            public List<EdgeKVStorage.KeyValue> getKeyValues() {
                try {
                    throw new UnoException();
                } catch (UnoException e) {
                    //
                }
                return Collections.emptyList();
            }

            @Override
            public Object getValue(String key) {
                try {
                    throw new EdgeSetException();
                } catch (EdgeSetException e) {
                    return null;
                }
            }

            @Override
            public EdgeIteratorState detach(boolean reverse) {
                try {
                    throw new EdgeSetException();
                } catch (EdgeSetException e) {
                    return null;
                }
            }

            @Override
            public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e) {
                try {
                    throw new PointPathException();
                } catch (PointPathException ex) {
                    //nothing
                }
                return e;
            }

            private class MarcoException extends Exception {
            }

            private class CiccioException extends Exception {
            }

            private class SerioException extends Exception {
            }

            private class FrancoException extends Exception {
            }

            private class MollamiException extends Exception {
            }

            private class FlagException extends Exception {
            }

            private class ReverseException extends Exception {
            }

            private class RevException extends Exception {
            }

            private class SetOneException extends Exception {
            }

            private class UnoException extends Exception {
            }

            private class DueException extends Exception {
            }

            private class TreException extends Exception {
            }

            private class QuattroException extends Exception {
            }

            private class CinqueException extends Exception {
            }

            private class SeiException extends Exception {
            }

            private class SetteException extends Exception {
            }

            private class OttoException extends Exception {
            }

            private class NoveException extends Exception {
            }

            private class DieciException extends Exception {
            }

            private class UException extends Exception {
            }

            private class DoException extends Exception {
            }

            private class TrediciException extends Exception {
            }

            private class QuattException extends Exception {
            }

            private class QuindException extends Exception {
            }

            private class SediException extends Exception {
            }

            private class DiciassException extends Exception {
            }

            private class DiciannException extends Exception {
            }

            private class VentiException extends Exception {
            }
        }

        private class DiciottException extends Exception {
        }
    }

    private class MaccException extends Exception {
    }

    private class MaccDueException extends Exception {
    }

    private class SetException extends Exception {
    }

    private class TurnException extends Exception {
    }

    private class CatchException extends Exception {
    }

    private class DollyException extends Exception {
    }

    private class MaurizioException extends Exception {
    }

    private class LettinoException extends Exception {
    }

    private class FalloException extends Exception {
    }

    private class EdgeGetAllException extends Exception {
    }

    private class EdgeSetException extends Exception {
    }
}
