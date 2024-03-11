package com.graphhopper.util.exceptions;

import java.util.Collections;
import java.util.Map;

/**
 * Refinement of the CannotFindPointException that indicates that a point is placed out of the graphs bounds
 *
 * @author Robin Boldt
 */
public class PointOutOfBoundsException extends IllegalArgumentException implements GHException{

    private static final long serialVersionUID = 1L;
    private final transient Map<String, Object> details;

    public static final String INDEX_KEY = "point_index";
    
    public PointOutOfBoundsException(String var1, int pointIndex) {
        super(var1);
        this.details = Collections.singletonMap(INDEX_KEY, pointIndex);
    }
    public int getPointIndex() {
        return (int) getDetails().get(INDEX_KEY);
    }
    @Override
    public Map<String, Object> getDetails() {
        return details;
    }
}
