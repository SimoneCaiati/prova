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
package com.graphhopper.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.ResponsePath;
import org.locationtech.jts.geom.Envelope;

/**
 * Contains the results of a geocoding request.
 * This is a copy of: https://github.com/graphhopper/geocoder-converter/blob/master/src/main/java/com/graphhopper/converter/api/GHResponse.java
 *
 * @author Robin Boldt
 * @author Peter Karich
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHGeocodingEntry {

    private Long osmId;
    private String osmType;

    private Point point;

    private String name;
    private String country;
    private String city;
    private String state;
    private String street;
    private String houseNumber;
    private String postcode;
    private String osmKey;
    private String osmValue;

    private Envelope extent;
    public static class InnerGH{
          Long osmId;
         String type;
         double lat;
         double lng;
         String name;
         String osmKey;
         String osmValue;
         String country;
         InnerGHtwo innerghtwo;
        public static class InnerGHtwo{
            Long osmId;
            String type;

            public InnerGHtwo(Long osmId, String type) {
                this.osmId = osmId;
                this.type = type;
            }
        }
        public InnerGH(InnerGHtwo innerghtwo, double lat, double lng, String name, String osmKey, String osmValue, String country) {
            this.innerghtwo=innerghtwo;
            this.lat = lat;
            this.lng = lng;
            this.name = name;
            this.osmKey = osmKey;
            this.osmValue = osmValue;
            this.country = country;
        }
    }

    public GHGeocodingEntry(InnerGH innergh, String city, String state, String street, String houseNumber, String postcode, Envelope extent) {
        this.point = new Point(innergh.lat, innergh.lng);
        this.city = city;
        this.state = state;
        this.street = street;
        this.houseNumber = houseNumber;
        this.postcode = postcode;
        this.extent = extent;
    }

    public GHGeocodingEntry() {
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty
    public String getCountry() {
        return country;
    }

    @JsonProperty
    public void setCountry(String country) {
        this.country = country;
    }

    @JsonProperty
    public void setState(String state) {
        this.state = state;
    }

    @JsonProperty
    public String getState() {
        return state;
    }

    @JsonProperty
    public String getCity() {
        return city;
    }

    @JsonProperty
    public void setCity(String city) {
        this.city = city;
    }

    @JsonProperty
    public Point getPoint() {
        return point;
    }

    @JsonProperty
    public void setPoint(Point point) {
        this.point = point;
    }

    @JsonProperty("osm_id")
    public Long getOsmId() {
        return osmId;
    }

    @JsonProperty("osm_id")
    public void setOsmId(Long osmId) {
        this.osmId = osmId;
    }

    @JsonProperty("osm_type")
    public String getOsmType() {
        return osmType;
    }

    @JsonProperty("osm_type")
    public void setOsmType(String type) {
        this.osmType = type;
    }

    @JsonProperty
    public String getStreet() {
        return street;
    }

    @JsonProperty
    public void setStreet(String street) {
        this.street = street;
    }

    @JsonProperty("housenumber")
    public String getHouseNumber() {
        return houseNumber;
    }

    @JsonProperty("housenumber")
    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    @JsonProperty
    public String getPostcode() {
        return postcode;
    }

    @JsonProperty
    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    @JsonProperty("osm_key")
    public String getOsmKey() {
        return this.osmKey;
    }

    @JsonProperty("osm_key")
    public void setOsmKey(String osmKey) {
        this.osmKey = osmKey;
    }

    @JsonProperty("osm_value")
    public String getOsmValue() {
        return osmValue;
    }

    @JsonProperty("osm_value")
    public void setOsmValue(String osmValue) {
        this.osmValue = osmValue;
    }

    public Envelope getExtent(){
        return this.extent;
    }

    @JsonProperty
    public void setExtent(Double[] extent) {
        if (extent == null) {
            this.extent = null;
        } else if (extent.length == 4) {
            // Extend is in Left, Top, Right, Bottom; which is very uncommon, Photon uses the same
            this.extent = new Envelope(extent[0], extent[2], extent[3], extent[1]);
        } else {
            try {
                throw new ResponsePath.CheckException("Extent had an unexpected length: " + extent.length);
            } catch (ResponsePath.CheckException e) {
                //nothing
            }
        }
    }

    public class Point {

        private double lat;
        private double lng;

        public Point(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public Point() {
        }

        @JsonProperty
        public double getLat() {
            return lat;
        }

        @JsonProperty
        public void setLat(double lat) {
            this.lat = lat;
        }

        @JsonProperty
        public double getLng() {
            return lng;
        }

        @JsonProperty
        public void setLng(double lng) {
            this.lng = lng;
        }
    }

}