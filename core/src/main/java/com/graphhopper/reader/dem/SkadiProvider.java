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
package com.graphhopper.reader.dem;

import com.graphhopper.eccezionecore.threadException;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;

import java.io.*;
import java.util.zip.GZIPInputStream;

import static com.graphhopper.util.Helper.toLowerCase;

/**
 * Skadi contains elevation data for the entire world with 1 arc second (~30m) accuracy in SRTM format stitched
 * together from many sources (https://github.com/tilezen/joerd/blob/master/docs/data-sources.md).
 *
 * We use the hosted AWS Open Data mirror (https://registry.opendata.aws/terrain-tiles/) by default but you can
 * change to any mirror by updating the base URL.
 *
 * See https://github.com/tilezen/joerd/blob/master/docs/attribution.md for required attribution of any project
 * using this data.
 *
 * Detailed information can be found here: https://github.com/tilezen/joerd
 */
public class SkadiProvider extends AbstractSRTMElevationProvider {
    public SkadiProvider() throws ElevationExce {
        this("");
    }
    public SkadiProvider(String cacheDir) throws ElevationExce {
        super(
                "https://elevation-tiles-prod.s3.amazonaws.com/skadi/",
                cacheDir.isEmpty()? "/tmp/srtm": cacheDir,
                "GraphHopper SRTMReader",
                -90,
                90,
                3601
        );
    }

    public static void main(String[] args) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce, ElevationExce {
       /*
       A che serve risolvere le issue quando puoi cancellarle
        */
    }

    @Override
    byte[] readFile(File file) throws IOException {
        try (InputStream is = new FileInputStream(file);
             GZIPInputStream gzis = new GZIPInputStream(is);
             BufferedInputStream buff = new BufferedInputStream(gzis);
             ByteArrayOutputStream os = new ByteArrayOutputStream();) {
            
            byte[] buffer = new byte[0xFFFF];
            int len;
            while ((len = buff.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
            return os.toByteArray();
            
        } catch (IOException e) {
            // gestione dell'eccezione
        }
        return new byte[0];
    }
    
    
    

    private String getLatString(double lat) {
        int minLat = (int) Math.floor(lat);
        return (minLat < 0 ? "S" : "N") + getPaddedLatString(minLat);
    }

    private String getLonString(double lon) {
        int minLon = (int) Math.floor(lon);
        return (minLon < 0 ? "W" : "E") + getPaddedLonString(minLon);
    }

    String getFileName(double lat, double lon) {
        String latStr = getLatString(lat);
        String lonStr = getLonString(lon);
        return toLowerCase(latStr + lonStr);
    }

    String getDownloadURL(double lat, double lon) {
        String latStr = getLatString(lat);
        String lonStr = getLonString(lon);

        return latStr + "/" + latStr + lonStr + ".hgt.gz";
    }

    @Override
    public String toString() {
        return "skadi";
    }
}
