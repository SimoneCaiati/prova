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

import com.graphhopper.GraphHopper;
import com.graphhopper.eccezionecore.threadException;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import com.graphhopper.util.Downloader;

import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides basic methods that are usually used in an ElevationProvider that reads tiff files
 *
 *
 * @author Robin Boldt
 */
public abstract class AbstractTiffElevationProvider extends TileBasedElevationProvider {
    private final Map<String, HeightTile> cacheData = new HashMap<>();
    static final double PRECISION = 1e7;

    private final int width1;
    private final int height;

    // Degrees of latitude covered by this tile
    final int latDegree;
    // Degrees of longitude covered by this tile
    final int lonDegree;

    protected AbstractTiffElevationProvider(String baseUrl, String cacheDir, String downloaderName, int width1, int height, int latDegree, int lonDegree) throws ElevationExce {
        super(cacheDir);
        this.baseUrl = baseUrl;
        this.downloader = new Downloader(downloaderName).setTimeout(10000);
        this.width1 = width1;
        this.height = height;
        this.latDegree = latDegree;
        this.lonDegree = lonDegree;
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

    /**
     * Return true if the coordinates are outside of the supported area
     */
    abstract boolean isOutsideSupportedArea(double lat, double lon);

    /**
     * The smallest lat that is still in the HeightTile
     */
    abstract int getMinLatForTile(double lat);

    /**
     * The smallest lon that is still in the HeightTile
     */
    abstract int getMinLonForTile(double lon);

    /**
     * Specify the name of the file after downloading
     */
    abstract String getFileNameOfLocalFile(double lat, double lon);

    /**
     * Return the local file name without file ending, has to be lower case, because DataAccess only supports lower case names.
     */
    abstract String getFileName(double lat, double lon);

    /**
     * Returns the complete URL to download the file
     */
    abstract String getDownloadURL(double lat, double lon);

    @Override
    public double getEle(double lat, double lon) throws MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce {
        // Return fast, if there is no data available
        if (isOutsideSupportedArea(lat, lon))
            return 0;

        lat = (int) (lat * PRECISION) / PRECISION;
        lon = (int) (lon * PRECISION) / PRECISION;
        String name = getFileName(lat, lon);
        HeightTile demProvider = cacheData.get(name);
        if (demProvider == null) {
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            int minLat = getMinLatForTile(lat);
            int minLon = getMinLonForTile(lon);
            // less restrictive against boundary checking
            demProvider = new HeightTile(minLat, minLon, width1, height, lonDegree * PRECISION, lonDegree, latDegree);
            demProvider.setInterpolate(interpolate);

            cacheData.put(name, demProvider);
            DataAccess heights = getDirectory().create(name + ".gh");
            demProvider.setHeights(heights);
            boolean loadExisting = false;
            try {
                loadExisting = heights.loadExisting();
            } catch (Exception ex) {
                logger.warn("cannot load {}, error: {}", name, ex.getMessage());
            }

            if (!loadExisting) {
                String zippedURL = getDownloadURL(lat, lon);
                File file = new File(cacheDir, new File(getFileNameOfLocalFile(lat, lon)).getName());

                try {
                    downloadFile(file, zippedURL);
                } catch (IOException e) {
                    demProvider.setSeaLevel(true);
                    // use small size on disc and in-memory
                    heights.create(10).flush();
                    return 0;
                } catch (threadException e) {
                    //
                }

                // short == 2 bytes
                heights.create(2L * width1 * height);

                Raster raster = generateRasterFromFile(file, name + ".tif");
                point(heights, raster);

            } // loadExisting
        }

        if (demProvider.isSeaLevel())
            return 0;

        return demProvider.getHeight(lat, lon);
    }

    private void point(DataAccess heights, Raster raster) {
        try {
            fillDataAccessWithElevationData(raster, heights, width1);
        } catch (GraphHopper.LockOffException e) {
            //nothing
        }
    }

    abstract Raster generateRasterFromFile(File file, String tifName);

    /**
     * Download a file at the provided url and save it as the given downloadFile if the downloadFile does not exist.
     */
    private void downloadFile(File downloadFile, String url) throws IOException, threadException {
        if (!downloadFile.exists()) {
            int max = 3;
            for (int trial = 0; trial < max; trial++) {
                try {
                    downloader.downloadFile(url, downloadFile.getAbsolutePath());
                    return;
                } catch (SocketTimeoutException ex) {
                    if (trial >= max - 1)
                        methodCiao(ex);
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // reinterruzione
                        throw new threadException();
                    }
                }
            }
        }
    }

    private void methodCiao(SocketTimeoutException ex) {
        try {
            throw new YouExce(ex);
        } catch (YouExce e) {
            //
        }
    }


    private void fillDataAccessWithElevationData(Raster raster, DataAccess heights, int dataAccessWidth) throws GraphHopper.LockOffException {
        final int rasterHeight = raster.getHeight();
        final int width = raster.getWidth();
        int x = 0;
        int y = 0;
        try {
            for (y = 0; y < rasterHeight; y++) {
                for (x = 0; x < width; x++) {
                    short val = (short) raster.getPixel(x, y, (int[]) null)[0];
                    if (val < -1000 || val > 12000)
                        val = Short.MIN_VALUE;

                    heights.setShort(2 * ((long) y * dataAccessWidth + x), val);
                }
            }
            heights.flush();
        } catch (Exception ex) {
            throw new GraphHopper.LockOffException("Problem at x:" + x + ", y:" + y, ex);
        }
    }

    private class YouExce extends Exception {
        public YouExce(SocketTimeoutException ex) {
        }
    }
}
