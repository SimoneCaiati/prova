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
package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.pbf.PbfReader;
import com.graphhopper.reader.osm.pbf.Sink;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
@SuppressWarnings("java:S3740")
/**
 * A readable OSM file.
 * <p>
 *
 * @author Nop
 */
public class OSMInputFile implements Sink, OSMInput {
    private static final int MAX_BATCH_SIZE = 1_000;
    private final InputStream bis;
    private final BlockingQueue<ReaderElement> itemQueue;
    private final Queue<ReaderElement> itemBatch;
    private boolean eof;
    // for xml parsing
    private XMLStreamReader xmlParser;
    // for pbf parsing
    private boolean binary = false;
    private PbfReader pbfReader;
    private Thread pbfReaderThread;
    private boolean hasIncomingData;
    private int workerThreads = -1;
    private OSMFileHeader fileheader;

    public OSMInputFile(File file) throws IOException, OsmExce {
        bis = decode(file);
        itemQueue = new LinkedBlockingQueue<>(50_000);
        itemBatch = new ArrayDeque<>(MAX_BATCH_SIZE);
    }

    public OSMInputFile open() throws XMLStreamException {
        if (binary) {
            openPBFReader(bis);
        } else {
            openXMLStream(bis);
        }
        return this;
    }

    /**
     * Currently on for pbf format. Default is number of cores.
     */
    public OSMInputFile setWorkerThreads(int threads) {
        workerThreads = threads;
        return this;
    }

    @SuppressWarnings("unchecked")
    private InputStream decode(File file) throws IOException, OsmExce {
        final String name = file.getName();

        InputStream ips = null;
        ips = six(file);
        ips.mark(10);

        // check file header
        byte[] header = new byte[6];
        one(file, ips, header);


        if (header[0] == 31 && header[1] == -117) {
            ips.reset();
            return new GZIPInputStream(ips, 50000);
        } else if (header[0] == 0 && header[1] == 0 && header[2] == 0
                && header[4] == 10 && header[5] == 9
                && (header[3] == 13 || header[3] == 14)) {
            ips.reset();
            binary = true;
            return ips;
        } else if (header[0] == 'P' && header[1] == 'K') {
            ips.reset();
            ZipInputStream zip1 = four(ips);
            if (zip1 != null) return zip1;

        }
         else if (name.endsWith(".osm") || name.endsWith(".xml")) {
            ips.reset();
            return ips;
        } else if (name.endsWith(".bz2") || name.endsWith(".bzip2")) {
            String clName = "org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream";
            return seven(ips, clName);
        } else {
            three(ips);
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
        }
        five(ips);
        return null;
    }

    private static InputStream seven(InputStream ips, String clName) {
        try {
            Class clazz = Class.forName(clName);
            ips.reset();
            Constructor<InputStream> ctor = clazz.getConstructor(InputStream.class, boolean.class);
            return ctor.newInstance(ips, true);
        } catch (Exception e) {
            three(ips);
            throw new IllegalArgumentException("Cannot instantiate " + clName, e);
        }
    }

    private static InputStream six(File file) throws OsmExce {
        InputStream ips;
        try {
            ips = new BufferedInputStream(new FileInputStream(file), 50000);
        } catch (FileNotFoundException e) {
            throw new OsmExce(e);
        }
        return ips;
    }

    private static void five(InputStream ips) throws IOException {
        if(ips != null)
            ips.close();
    }

    private static ZipInputStream four(InputStream ips) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(ips)) {
            ZipInputStream zip1 = two(zip);
            if (zip1 != null) return zip1;
        }
        return null;
    }

    private static void three(InputStream ips) {
        try {
            ips.close();
        } catch (IOException e) {
            // log or handle the exception
        }
    }

    private static ZipInputStream two(ZipInputStream zip) {
        try {
            ZipEntry ze = zip.getNextEntry();
            int thresholdEntries = 10000;
            int thresholdSize = 1000000000; // 1 GB
            double thresholdRatio = 10;
            int totalSizeArchive = 0;
            int totalEntryArchive = 0;

            while (ze != null && totalSizeArchive <= thresholdSize && totalEntryArchive <= thresholdEntries) {
                totalEntryArchive++;

                int nBytes;
                byte[] buffer = new byte[2048];
                int totalSizeEntry = 0;

                while ((nBytes = zip.read(buffer)) > 0) {
                    totalSizeEntry += nBytes;
                    totalSizeArchive += nBytes;

                    double compressionRatio = (double) totalSizeEntry / ze.getCompressedSize();
                    if (compressionRatio > thresholdRatio) {
                        // Il rapporto tra dati compressi e non compressi è sospetto, sembra un attacco Zip Bomb
                        break;
                    }
                }

                if (totalSizeArchive > thresholdSize || totalEntryArchive > thresholdEntries) {
                    // La dimensione dei dati non compressi è troppo elevata per le risorse dell'applicazione
                    // o ci sono troppe voci nell'archivio
                    break;
                }

                ze = zip.getNextEntry();
            }


            return zip;
        } catch (IOException e) {
            // Gestione dell'eccezione
        }

        return null;
    }


    private static void one(File file, InputStream ips, byte[] header) throws IOException {
        if (ips.read(header) < 0) {
            three(ips);
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
        }
    }


    private void openXMLStream(InputStream in)
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false); // Disable external entities
        xmlParser = factory.createXMLStreamReader(in, "UTF-8");

        int event = xmlParser.next();
        if (event != XMLStreamConstants.START_ELEMENT || !xmlParser.getLocalName().equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("File is not a valid OSM stream");
        }
        // See https://wiki.openstreetmap.org/wiki/PBF_Format#Definition_of_the_OSMHeader_fileblock
        String timestamp = xmlParser.getAttributeValue(null, "osmosis_replication_timestamp");

        if (timestamp == null)
            timestamp = xmlParser.getAttributeValue(null, "timestamp");

        if (timestamp != null) {
            try {
                fileheader = new OSMFileHeader();
                fileheader.setTag("timestamp", timestamp);
            } catch (Exception ex) {
                //
            }
        }

        eof = false;
    }

    @Override
    public ReaderElement getNext() throws XMLStreamException {
        if (eof)
            throw new IllegalStateException("EOF reached");

        ReaderElement item;
        if (binary)
            item = getNextPBF();
        else
            item = getNextXML();

        if (item != null)
            return item;

        eof = true;
        return null;
    }

    private ReaderElement getNextXML() throws XMLStreamException {

        int event = xmlParser.next();
        if (fileheader != null) {
            ReaderElement copyfileheader = fileheader;
            fileheader = null;
            return copyfileheader;
        }

        while (event != XMLStreamConstants.END_DOCUMENT) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String idStr = xmlParser.getAttributeValue(null, "id");
                if (idStr != null) {
                    String name = xmlParser.getLocalName();
                    ReaderElement id1 = methodInput1(idStr, name);
                    if (id1 != null) return id1;
                }
            }
            event = xmlParser.next();
        }
        xmlParser.close();
        return null;
    }

    private ReaderElement methodInput1(String idStr, String name) throws XMLStreamException {
        long id;
        switch (name.charAt(0)) {
            case 'n':
                // note vs. node
                if ("node".equals(name)) {
                    id = Long.parseLong(idStr);
                    return OSMXMLHelper.createNode(id, xmlParser);
                }
                break;

            case 'w': {
                id = Long.parseLong(idStr);
                return OSMXMLHelper.createWay(id, xmlParser);
            }
            case 'r':
                id = Long.parseLong(idStr);
                return OSMXMLHelper.createRelation(id, xmlParser);
            default:
        }
        return null;
    }

    public boolean isEOF() {
        return eof;
    }

    @Override
    public void close() throws IOException {
        try {
            if (binary)
                pbfReader.close();
            else
                xmlParser.close();
        } catch (XMLStreamException ex) {
            throw new IOException(ex);
        } finally {
            eof = true;
            bis.close();
            // if exception happened on OSMInputFile-thread we need to shutdown the pbf handling
            if (pbfReaderThread != null && pbfReaderThread.isAlive())
                pbfReaderThread.interrupt();
        }
    }

    private void openPBFReader(InputStream stream) {
        hasIncomingData = true;
        if (workerThreads <= 0)
            workerThreads = 1;

        pbfReader = new PbfReader(stream, this, workerThreads);
        pbfReaderThread = new Thread(pbfReader, "PBF Reader");
        pbfReaderThread.start();
    }

    @Override
    public void process(ReaderElement item) throws RuntimeException, OsmExce2 {
        try {
            itemQueue.put(item);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // re-interrupt the thread
            throw new OsmExce2(ex);
        }
    }

    public int getUnprocessedElements() {
        return itemQueue.size() + itemBatch.size();
    }

    @Override
    public void complete() {
        hasIncomingData = false;
    }

    private ReaderElement getNextPBF() {
        while (itemBatch.isEmpty()) {
            if (!hasIncomingData && itemQueue.isEmpty()) {
                return null; // signal EOF
            }

            if (itemQueue.drainTo(itemBatch, MAX_BATCH_SIZE) == 0) {
                try {
                    ReaderElement element = itemQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (element != null) {
                        return element; // short circuit
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null; // signal EOF
                }
            }
        }

        return itemBatch.poll();
    }

    public static class OsmExce extends Exception {
        public OsmExce(FileNotFoundException e) {
        }
    }

    public class OsmExce2 extends Exception {
        public OsmExce2(InterruptedException ex) {
        }
    }
}
