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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.*;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;
import java.util.*;

public class ResponsePathDeserializer extends JsonDeserializer<ResponsePath> {

    public static final String MESSAGGIO= "message";
    public static final String HEADING="heading";
    public static final String DETAILS="details";
    @Override
    public ResponsePath deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return createResponsePath((ObjectMapper) p.getCodec(), p.readValueAsTree(), false, true);
    }

    public static ResponsePath createResponsePath(ObjectMapper objectMapper, JsonNode path, boolean hasElevation, boolean turnDescription) {
        ResponsePath responsePath = new ResponsePath();
        responsePath.addErrors(readErrors(objectMapper, path));
        ResponsePath responsePath1 = mio(objectMapper, path, hasElevation, responsePath);
        ResponsePath responsePath11 = miotre(path, responsePath, responsePath1);
        if (responsePath11 != null) return responsePath11;
        miodue(path, responsePath);

        if (path.has("points")) {
            final PointList pointList = deserializePointList(objectMapper, path.get("points"), hasElevation);
            responsePath.setPoints(pointList);

            if (path.has("instructions")) {
                JsonNode instrArr = path.get("instructions");

                InstructionList il = new InstructionList(null);
                int viaCount = 1;
                for (JsonNode jsonObj : instrArr) {
                    double instDist = jsonObj.get("distance").asDouble();
                    String text = turnDescription ? jsonObj.get("text").asText() : jsonObj.get(Parameters.Details.STREET_NAME).asText();
                    long instTime = jsonObj.get("time").asLong();
                    int sign = jsonObj.get("sign").asInt();
                    JsonNode iv = jsonObj.get("interval");
                    int from = iv.get(0).asInt();
                    int to = iv.get(1).asInt();
                    PointList instPL = new PointList(to - from, hasElevation);
                    ciao(pointList, from, to, instPL);

                    Instruction instr;
                    switch (sign) {
                        case Instruction.USE_ROUNDABOUT:
                        case Instruction.LEAVE_ROUNDABOUT:
                            RoundaboutInstruction ri = new RoundaboutInstruction(sign, text, instPL);
                            mioquattro(jsonObj, ri);
                            instr = ri;
                            break;
                        case Instruction.REACHED_VIA:
                            ViaInstruction tmpInstr = new ViaInstruction(text, instPL);
                            tmpInstr.setViaCount(viaCount);
                            viaCount++;
                            instr = tmpInstr;
                            break;
                        case Instruction.FINISH:
                            instr = new FinishInstruction(text, instPL, 0);
                            break;
                        default:
                            instr = new Instruction(sign, text, instPL);
                            falso(jsonObj, sign, instr);
                            break;
                    }

                    // Usually, the translation is done from the routing service so just use the provided string
                    // instead of creating a combination with sign and name etc.
                    // This is called the turn description.
                    // This can be changed by passing <code>turn_description=false</code>.
                    seno(turnDescription, il, instDist, instTime, instr);
                }
                responsePath.setInstructions(il);
            }

            dio(objectMapper, path, responsePath);
        }

        diodue(objectMapper, path, responsePath);

        double distance = path.get("distance").asDouble();
        long time = path.get("time").asLong();
        responsePath.setDistance(distance).setTime(time);
        return responsePath;
    }

    private static void ciao(PointList pointList, int from, int to, PointList instPL) {
        for (int j = from; j <= to; j++) {
            instPL.add(pointList, j);
        }
    }

    private static void diodue(ObjectMapper objectMapper, JsonNode path, ResponsePath responsePath) {
        if (path.has("points_order")) {
            responsePath.setPointsOrder( objectMapper.convertValue(path.get("points_order"), List.class));
        } else {
            List<Integer> list = new ArrayList<>(responsePath.getWaypoints().size());
            for (int i = 0; i < responsePath.getWaypoints().size(); i++) {
                list.add(i);
            }
            responsePath.setPointsOrder(list);
        }
    }

    private static void dio(ObjectMapper objectMapper, JsonNode path, ResponsePath responsePath) {
        if (path.has(DETAILS)) {
            JsonNode details = path.get(DETAILS);
            Map<String, List<PathDetail>> pathDetails = new HashMap<>(details.size());
            Iterator<Map.Entry<String, JsonNode>> detailIterator = details.fields();
            while (detailIterator.hasNext()) {
                Map.Entry<String, JsonNode> detailEntry = detailIterator.next();
                List<PathDetail> pathDetailList = new ArrayList<>();
                for (JsonNode pathDetail : detailEntry.getValue()) {
                    PathDetail pd = objectMapper.convertValue(pathDetail, PathDetail.class);
                    pathDetailList.add(pd);
                }
                pathDetails.put(detailEntry.getKey(), pathDetailList);
            }
            responsePath.addPathDetails(pathDetails);
        }
    }

    private static void seno(boolean turnDescription, InstructionList il, double instDist, long instTime, Instruction instr) {
        if (turnDescription)
            instr.setUseRawName();

        instr.setDistance(instDist).setTime(instTime);
        il.add(instr);
    }

    private static void falso(JsonNode jsonObj, int sign, Instruction instr) {
        if (sign == Instruction.CONTINUE_ON_STREET && jsonObj.has(HEADING)) {
                instr.setExtraInfo(HEADING, jsonObj.get(HEADING).asDouble());
            }
        }


    private static void mioquattro(JsonNode jsonObj, RoundaboutInstruction ri) {
        if (jsonObj.has("exit_number")) {
            ri.setExitNumber(jsonObj.get("exit_number").asInt());
        }

        if (jsonObj.has("exited") && jsonObj.get("exited").asBoolean()) {
            ri.setExited();
        }


        if (jsonObj.has("turn_angle")) {
            //  provide setTurnAngle setter
            double angle = jsonObj.get("turn_angle").asDouble();
            ri.setDirOfRotation(angle);
            ri.setRadian((angle < 0 ? -Math.PI : Math.PI) - angle);
        }
    }

    private static ResponsePath miotre(JsonNode path, ResponsePath responsePath, ResponsePath responsePath1) {
        if (responsePath1 != null) return responsePath1;
        if (path.has("weight")) {
            responsePath.setRouteWeight(path.get("weight").asDouble());
        }
        return null;
    }

    private static void miodue(JsonNode path, ResponsePath responsePath) {
        if (path.has("description")) {
            JsonNode descriptionNode = path.get("description");
            if (descriptionNode.isArray()) {
                List<String> description = new ArrayList<>(descriptionNode.size());
                for (JsonNode descNode : descriptionNode) {
                    description.add(descNode.asText());
                }
                responsePath.setDescription(description);
            } else {
                throw new IllegalStateException("Description has to be an array");
            }
        }
    }

    private static ResponsePath mio(ObjectMapper objectMapper, JsonNode path, boolean hasElevation, ResponsePath responsePath) {
        if (responsePath.hasErrors())
            return responsePath;

        if (path.has("snapped_waypoints")) {
            JsonNode snappedWaypoints = path.get("snapped_waypoints");
            PointList snappedPoints = deserializePointList(objectMapper, snappedWaypoints, hasElevation);
            responsePath.setWaypoints(snappedPoints);
        }

        if (path.has("ascend")) {
            responsePath.setAscend(path.get("ascend").asDouble());
        }
        if (path.has("descend")) {
            responsePath.setDescend(path.get("descend").asDouble());
        }
        return null;
    }

    private static PointList deserializePointList(ObjectMapper objectMapper, JsonNode jsonNode, boolean hasElevation) {
        PointList snappedPoints;
        if (jsonNode.isTextual()) {
            snappedPoints = decodePolyline(jsonNode.asText(), Math.max(10, jsonNode.asText().length() / 4), hasElevation);
        } else {
            LineString lineString = objectMapper.convertValue(jsonNode, LineString.class);
            snappedPoints = PointList.fromLineString(lineString);
        }
        return snappedPoints;
    }

    public static PointList decodePolyline(String encoded, int initCap, boolean is3D) {
        PointList poly = new PointList(initCap, is3D);
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;
        int ele = 0;
        while (index < len) {
            // latitude
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLatitude = methodAbRe1(result);
            lat += deltaLatitude;

            // longitude
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLongitude = methodAbRe1(result);
            lng += deltaLongitude;

            if (is3D) {
                // elevation
                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaElevation = methodAbRe1(result);
                ele += deltaElevation;
                poly.add( lat / 1e5,  lng / 1e5, (double) ele / 100);
            } else
                poly.add( lat / 1e5,  lng / 1e5);


        }
        return poly;
    }

    private static int methodAbRe1(int result) {
        return ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
    }


    public static List<Throwable> readErrors(ObjectMapper objectMapper, JsonNode json) {
        List<Throwable> errors = new ArrayList<>();
        JsonNode errorJson;

        if (json.has(MESSAGGIO)) {
            if (json.has("hints")) {
                errorJson = json.get("hints");
            } else {
                // should not happen
                errors.add(new RuntimeException(json.get(MESSAGGIO).asText()));
                return errors;
            }
        } else
            return errors;

        for (JsonNode error : errorJson) {
            String exClass = "";
            exClass = mollyuno(error, exClass);

            String exMessage = error.get(MESSAGGIO).asText();

            seno(objectMapper, errors, error, exClass, exMessage);
        }

        molly(json, errors);

        return errors;
    }

    private static void seno(ObjectMapper objectMapper, List<Throwable> errors, JsonNode error, String exClass, String exMessage) {
        if (exClass.equals(UnsupportedOperationException.class.getName()))
            errors.add(new UnsupportedOperationException(exMessage));
        else if (exClass.equals(IllegalStateException.class.getName()))
            errors.add(new IllegalStateException(exMessage));
        else if (exClass.equals(RuntimeException.class.getName()))
            errors.add(new DetailedRuntimeException(exMessage, toMap(objectMapper, error)));
        else if (exClass.equals(IllegalArgumentException.class.getName()))
            errors.add(new DetailedIllegalArgumentException(exMessage, toMap(objectMapper, error)));
        else if (exClass.equals(ConnectionNotFoundException.class.getName())) {
            errors.add(new ConnectionNotFoundException(exMessage, toMap(objectMapper, error)));
        } else if (exClass.equals(MaximumNodesExceededException.class.getName())) {
            int maxVisitedNodes = error.get(MaximumNodesExceededException.NODES_KEY).asInt();
            errors.add(new MaximumNodesExceededException(exMessage, maxVisitedNodes));
        } else if (exClass.equals(PointNotFoundException.class.getName())) {
            int pointIndex = error.get(PointNotFoundException.INDEX_KEY).asInt();
            errors.add(new PointNotFoundException(exMessage, pointIndex));
        } else if (exClass.equals(PointOutOfBoundsException.class.getName())) {
            int pointIndex = error.get(PointNotFoundException.INDEX_KEY).asInt();
            errors.add(new PointOutOfBoundsException(exMessage, pointIndex));
        } else if (exClass.isEmpty())
            errors.add(new DetailedRuntimeException(exMessage, toMap(objectMapper, error)));
        else
            errors.add(new DetailedRuntimeException(exClass + " " + exMessage, toMap(objectMapper, error)));
    }

    private static String mollyuno(JsonNode error, String exClass) {
        if (error.has(DETAILS))
            exClass = error.get(DETAILS).asText();
        return exClass;
    }

    private static void molly(JsonNode json, List<Throwable> errors) {
        if (json.has(MESSAGGIO) && errors.isEmpty())
            errors.add(new RuntimeException(json.get(MESSAGGIO).asText()));
    }

    // Credits to: http://stackoverflow.com/a/24012023/194609
    private static Map<String, Object> toMap(ObjectMapper objectMapper, JsonNode object) {
        return objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {
        });
    }

}
