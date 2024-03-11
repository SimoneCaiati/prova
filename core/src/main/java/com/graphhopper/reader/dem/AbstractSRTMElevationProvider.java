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

import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.eccezionecore.threadException;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Downloader;
import com.graphhopper.util.Helper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Common functionality used when working with SRTM hgt data.
 *
 * @author Robin Boldt
 */
public abstract class AbstractSRTMElevationProvider extends TileBasedElevationProvider {
    private final int defaultWidth;
    private final int minLat;
    private final int maxLat;
    private static final int WIDTH_BYTE_INDEX = 0;
    private static final int DEGREE = 1;
    // use a map as an array is not quite useful if we want to hold only parts of the world
    private final GHIntObjectHashMap<HeightTile> cacheData = new GHIntObjectHashMap<>();
    private static final double PRECISION = 1e7;
    private static final double DOUBLE = 1 / PRECISION;

    protected AbstractSRTMElevationProvider(String baseUrl, String cacheDir, String downloaderName, int minLat, int maxLat, int defaultWidth) throws ElevationExce {
        super(cacheDir);
        this.baseUrl = baseUrl;
        downloader = new Downloader(downloaderName).setTimeout(10000);
        this.defaultWidth = defaultWidth;
        this.minLat = minLat;
        this.maxLat = maxLat;
    }

    // use int key instead of string for lower memory usage
    int calcIntKey(double lat, double lon) {
        // we could use LinearKeyAlgo but this is simpler as we only need integer precision:
        return (down(lat) + 90) * 1000 + down(lon) + 180;
    }

    @Override
    public void release() {
        cacheData.clear();
        if (dir != null) {
            // for memory mapped type we remove temporary files
            if (autoRemoveTemporary)
                dir.clear();
            else
                dir.close();
        }
    }

    int down(double val) {
        int intVal = (int) val;
        if (val >= 0 || intVal - val < DOUBLE)
            return intVal;
        return intVal - 1;
    }

    @Override
    public double getEle(double lat, double lon) throws threadException, MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        // Return fast, if there is no data available
        // See https://www2.jpl.nasa.gov/srtm/faq.html
        if (speroSialUltimo(lat))
            return 0;

        lat = (int) (lat * PRECISION) / PRECISION;
        lon = (int) (lon * PRECISION) / PRECISION;
        int intKey = calcIntKey(lat, lon);
        HeightTile demProvider = cacheData.get(intKey);
        if (demProvider == null) {
            methodSRTM3();

            int down = down(lat);
            int minLon = down(lon);

            String fileName = getFileName(lat, lon);
            if (itsOk(fileName))
                return 0;

            DataAccess heights = getDirectory().create("dem" + intKey);
            boolean loadExisting = false;
            loadExisting = methodExc(heights, loadExisting);
            if (!loadExisting) {
                try {
                    updateHeightsFromFile(lat, lon, heights);
                } catch (FileNotFoundException ex) {
                    demProvider = new HeightTile(down, minLon, defaultWidth, defaultWidth, PRECISION, DEGREE, DEGREE);
                    cacheData.put(intKey, demProvider);
                    demProvider.setHeights(heights);
                    demProvider.setSeaLevel(true);
                    // use small size on disc and in-memory
                    heights.create(10)
                            .flush();
                    return 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new threadException();
                }
            }

            int width = (int) (Math.sqrt(heights.getHeader(WIDTH_BYTE_INDEX)) + 0.5);
            if (width == 0)
                width = defaultWidth;

            demProvider = new HeightTile(down, minLon, width, width, PRECISION, DEGREE, DEGREE);
            cacheData.put(intKey, demProvider);
            demProvider.setInterpolate(interpolate);
            demProvider.setHeights(heights);
        }

        if (demProvider.isSeaLevel())
            return 0;

        return demProvider.getHeight(lat, lon);
    }

    private boolean speroSialUltimo(double lat) {
        return lat >= maxLat || lat <= minLat;
    }

    private boolean itsOk(String fileName) {
        return fileName == null || (Helper.isEmpty(baseUrl) && !new File(fileName).exists());
    }

    private boolean methodExc(DataAccess heights, boolean loadExisting) {
        try {
            loadExisting = heights.loadExisting();
        } catch (Exception ex) {
            //
        }
        return loadExisting;
    }

    private void methodSRTM3() {
        if (!cacheDir.exists())
            cacheDir.mkdirs();
    }


    private void updateHeightsFromFile(double lat, double lon, DataAccess heights) throws FileNotFoundException, InterruptedException, MMapDataAccess.MapExce, MMapDataAccess.MappaExce {
        try {
            byte[] bytes = getByteArrayFromFile(lat, lon);
            heights.create(bytes.length);
            for (int bytePos = 0; bytePos < bytes.length; bytePos += 2) {
                // we need big endianess to read the SRTM files
                short val = BitUtil.BIG.toShort(bytes, bytePos);
                if (val < -1000 || val > 12000)
                    val = Short.MIN_VALUE;

                heights.setShort(bytePos, val);

                // Check for interruption
                if (Thread.interrupted()) {
                    // Set the interrupted flag again and throw InterruptedException
                    Thread.currentThread().interrupt();
                    throw new InterruptedException();
                }
            }
            heights.setHeader(WIDTH_BYTE_INDEX, bytes.length / 2);
            heights.flush();

        } catch (FileNotFoundException | MMapDataAccess.MappaExce ex) {
            throw ex;
        } catch (InterruptedException | IOException | RAMDataAccess.RamExce2 | RAMIntDataAccess.RamIntExce ex) {
            throw new InterruptedException();
        }
    }


    private byte[] getByteArrayFromFile(double lat, double lon) throws InterruptedException, IOException {
        String zippedURL = baseUrl + getDownloadURL(lat, lon);
        File file = new File(cacheDir, new File(zippedURL).getName());
        // get zip file if not already in cacheDir
        if (!file.exists())
            for (int i = 0; i < 3; i++) {
                try {
                    downloader.downloadFile(zippedURL, file.getAbsolutePath());
                    break;
                } catch (SocketTimeoutException ex) {
                    // just try again after a little nap
                    Thread.sleep(2000);
                } catch (FileNotFoundException ex) {
                    if (zippedURL.contains(".hgt.zip")) {
                        zippedURL = zippedURL.replace(".hgt.zip", "hgt.zip");
                    } else {
                        throw ex;
                    }
                }
            }

        return readFile(file);
    }

    protected String getPaddedLonString(int lonInt) {
        lonInt = Math.abs(lonInt);
        String lonString = lonInt < 100 ? "0" : "";
        if (lonInt < 10)
            lonString += "0";
        lonString += lonInt;
        return lonString;
    }

    protected String getPaddedLatString(int latInt) {
        latInt = Math.abs(latInt);
        String latString = latInt < 10 ? "0" : "";
        latString += latInt;
        return latString;
    }

    abstract byte[] readFile(File file) throws IOException;

    /**
     * Return the local file name without file ending, has to be lower case, because DataAccess only supports lower case names.
     */
    abstract String getFileName(double lat, double lon);

    /**
     * Returns the complete URL to download the file
     */
    abstract String getDownloadURL(double lat, double lon);

}
