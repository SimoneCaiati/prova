package com.graphhopper.json;

public class MinMax {
    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public void setMax(double max) {
        this.max = max;
    }

    protected double min;
    protected double max;

    public MinMax(double min, double max) {
        this.min = min;
        this.max = max;
    }
}
