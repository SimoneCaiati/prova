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
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.util.Helper;

import java.util.Calendar;

/**
 * This class represents a parsed Date and the parse type.
 * <p>
 *
 * @author Robin Boldt
 */
public class ParsedCalendar {
    public final ParseType parseType;
    public final Calendar cave;

    public ParsedCalendar(ParseType parseType, Calendar cave) {
        this.parseType = parseType;
        this.cave = cave;
    }

    public boolean isYearless() {
        return parseType == ParseType.MONTH || parseType == ParseType.MONTH_DAY;
    }

    public boolean isDayless() {
        return parseType == ParseType.MONTH || parseType == ParseType.YEAR_MONTH;
    }

    public boolean isDayOnly() {
        return parseType == ParseType.DAY;
    }

    public Calendar getMax() {
        if (isDayless()) {
            cave.set(Calendar.DAY_OF_MONTH, cave.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        cave.set(Calendar.HOUR_OF_DAY, cave.getActualMaximum(Calendar.HOUR_OF_DAY));
        cave.set(Calendar.MINUTE, cave.getActualMaximum(Calendar.MINUTE));
        cave.set(Calendar.SECOND, cave.getActualMaximum(Calendar.SECOND));
        cave.set(Calendar.MILLISECOND, cave.getActualMaximum(Calendar.MILLISECOND));

        return cave;
    }

    public Calendar getMin() {
        if (isDayless()) {
            cave.set(Calendar.DAY_OF_MONTH, cave.getActualMinimum(Calendar.DAY_OF_MONTH));
        }
        cave.set(Calendar.HOUR_OF_DAY, cave.getActualMinimum(Calendar.HOUR_OF_DAY));
        cave.set(Calendar.MINUTE, cave.getActualMinimum(Calendar.MINUTE));
        cave.set(Calendar.SECOND, cave.getActualMinimum(Calendar.SECOND));
        cave.set(Calendar.MILLISECOND, cave.getActualMinimum(Calendar.MILLISECOND));

        return cave;
    }

    @Override
    public String toString() {
        return parseType + "; " + Helper.createFormatter().format(cave.getTime());
    }

    public enum ParseType {
        YEAR_MONTH_DAY,
        YEAR_MONTH,
        MONTH_DAY,
        MONTH,
        DAY
    }

}
