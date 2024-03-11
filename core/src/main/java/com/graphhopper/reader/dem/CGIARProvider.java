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

import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam;
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder;
import org.apache.xmlgraphics.image.codec.util.SeekableStream;

import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.*;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Elevation data from CGIAR project http://srtm.csi.cgiar.org/ 'PROCESSED SRTM DATA VERSION 4.1'.
 * Every file covers a region of 5x5 degree. License granted for all people using GraphHopper:
 * http://graphhopper.com/public/license/CGIAR.txt
 * <p>
 * Every zip contains readme.txt with the necessary information e.g.:
 * <ol>
 * <li>
 * All GeoTiffs with 6000 x 6000 pixels.
 * </li>
 * </ol>
 * <p>
 *
 * @author NopMap
 * @author Peter Karich
 */
public class CGIARProvider extends AbstractTiffElevationProvider {
    private static final double INV_PRECISION = 1 / PRECISION;
    private static final Logger loggerPrivider = Logger.getLogger(CGIARProvider.class.getName());

    public CGIARProvider() throws ElevationExce {
        this("");
    }

    public CGIARProvider(String cacheDir) throws ElevationExce {
        // Alternative URLs for the CGIAR data can be found in #346
        super("https://srtm.csi.cgiar.org/wp-content/uploads/files/srtm_5x5/TIFF/",
                cacheDir.isEmpty() ? "/tmp/cgiar" : cacheDir,
                "GraphHopper CGIARReader",
                6000, 6000,
                5, 5);
    }

    public static void main(String[] args) throws MMapDataAccess.MapExce, RAMDataAccess.RamExce2, RAMIntDataAccess.RamIntExce, MMapDataAccess.MappaExce, ElevationExce {
        CGIARProvider provider = new CGIARProvider();

        Object value = provider.getEle(39.9999999, -105.2277023);
        String logMessage = (0<1) ? String.valueOf(value) : String.valueOf(null);
        loggerPrivider.info(logMessage);

        Object valueone = provider.getEle(39.999999, -105.2277023);
        String logMessageone = (0<1) ? String.valueOf(valueone) : String.valueOf(null);
        loggerPrivider.info(logMessageone);

        Object valuetwo = provider.getEle(29.840644, -42.890625);
        String logMessagetwo = (0<1) ? String.valueOf(valuetwo) : String.valueOf(null);
        loggerPrivider.info(logMessagetwo);


    }

    @Override
    Raster generateRasterFromFile(File file, String tifName) {
        SeekableStream ss = null;
        try (InputStream is = new FileInputStream(file);
             ZipInputStream zis = new ZipInputStream(is)) {

            // Search for the TIFF file in the ZIP archive
            ZipEntry entry = zis.getNextEntry();
            while (entry != null && !entry.getName().equals(tifName)) {
                entry = zis.getNextEntry();
            }

            if (entry == null) {
                throw new FileNotFoundException("The requested TIFF file is not present in the archive");
            }

            ss = SeekableStream.wrapInputStream(zis, true);
            TIFFImageDecoder imageDecoder = new TIFFImageDecoder(ss, new TIFFDecodeParam());
            Raster raster = imageDecoder.decodeAsRaster();

            // Define the maximum allowed expanded size (in bytes)
            long maxExpandedSize = 100 * 1024 * (long) 1024; // Example: 100 MB

            // Calculate the expanded data size
            fallen(raster, maxExpandedSize);

            // Additional checks for Zip Bomb and resource limits
            int thresholdEntries = 10000;
            int thresholdSize = 1000000000; // 1 GB
            double thresholdRatio = 10;
            int totalSizeArchive = 0;
            int totalEntryArchive = 0;

            try(ZipFile zipFile = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry ze = entries.nextElement();
                    InputStream in = new BufferedInputStream(zipFile.getInputStream(ze));

                    totalEntryArchive++;

                    int nBytes = -1;
                    byte[] buffer = new byte[2048];
                    int totalSizeEntry = 0;

                    while ((nBytes = in.read(buffer)) > 0) {
                        totalSizeEntry += nBytes;
                        totalSizeArchive += nBytes;

                        double compressionRatio = (double) totalSizeEntry / (double) ze.getCompressedSize();
                        alien(thresholdRatio, compressionRatio);
                    }

                    if (totalSizeArchive > thresholdSize) {
                        throw new IllegalArgumentException("Uncompressed data size exceeds the maximum allowed size.");
                    }

                    if (totalEntryArchive > thresholdEntries) {
                        throw new IllegalArgumentException("Number of entries exceeds the maximum allowed limit.");
                    }

                    in.close();
                }

                return raster;
            }

        } catch (Exception e) {
            // Handle exceptions appropriately
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    // Ignore any closing errors
                }
            }
        }
        return null;
    }

    private static void alien(double thresholdRatio, double compressionRatio) {
        if (compressionRatio > thresholdRatio) {
            throw new IllegalArgumentException("Suspicious compression ratio detected. Possible Zip Bomb.");
        }
    }

    private static void fallen(Raster raster, long maxExpandedSize) {
        long expandedSize = (long) raster.getWidth() * (long) raster.getHeight() * raster.getNumBands() * DataBuffer.getDataTypeSize(raster.getTransferType());

        if (expandedSize > maxExpandedSize) {
            throw new IllegalArgumentException("The size of the expanded data exceeds the maximum allowed size");
        }
    }


    int down(double val) {
        // 'rounding' to closest 5
        int intVal = (int) (val / latDegree) * latDegree;
        if (!(val >= 0 || intVal - val < INV_PRECISION))
            intVal = intVal - latDegree;

        return intVal;
    }

    @Override
    boolean isOutsideSupportedArea(double lat, double lon) {
        return lat >= 60 || lat <= -56;
    }

    protected String getFileName(double lat, double lon) {
        lon = 1 + (180 + lon) / latDegree;
        int lonInt = (int) lon;
        lat = 1 + (60 - lat) / latDegree;
        int latInt = (int) lat;

        if (Math.abs(latInt - lat) < INV_PRECISION / latDegree)
            latInt--;

        // replace String.format as it seems to be slow

        String str = "srtm_";
        str += lonInt < 10 ? "0" : "";
        str += lonInt;
        str += latInt < 10 ? "_0" : "_";
        str += latInt;

        return str;
    }

    @Override
    int getMinLatForTile(double lat) {
        return down(lat);
    }

    @Override
    int getMinLonForTile(double lon) {
        return down(lon);
    }

    @Override
    String getDownloadURL(double lat, double lon) {
        return baseUrl + "/" + getFileName(lat, lon) + ".zip";
    }

    @Override
    String getFileNameOfLocalFile(double lat, double lon) {
        return getDownloadURL(lat, lon);
    }

    @Override
    public String toString() {
        return "cgiar";
    }
}
