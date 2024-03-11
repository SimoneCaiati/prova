// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import com.graphhopper.eccezionecore.PointPathException;
import org.openstreetmap.osmosis.osmbinary.Fileformat;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses a PBF data stream and extracts the raw data of each blob in sequence until the end of the
 * stream is reached.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfStreamSplitter implements Iterator<PbfRawBlob> {
    private static Logger log = Logger.getLogger(PbfStreamSplitter.class.getName());
    private DataInputStream dis;
    private int dataBlockCount;
    private boolean eof;
    private PbfRawBlob nextBlob;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param pbfStream The PBF data stream to be parsed.
     */
    public PbfStreamSplitter(DataInputStream pbfStream) {
        dis = pbfStream;
        dataBlockCount = 0;
        eof = false;
    }

    private Fileformat.BlobHeader readHeader(int headerLength) throws IOException {
        byte[] headerBuffer = new byte[headerLength];
        dis.readFully(headerBuffer);

        return Fileformat.BlobHeader.parseFrom(headerBuffer);
    }

    private byte[] readRawBlob(Fileformat.BlobHeader blobHeader) throws IOException {
        byte[] rawBlob = new byte[blobHeader.getDatasize()];

        dis.readFully(rawBlob);

        return rawBlob;
    }

    private void getNextBlob() throws SplitterExce {
        try {
            // Read the length of the next header block. This is the only time
            // we should expect to encounter an EOF exception. In all other
            // cases it indicates a corrupt or truncated file.
            Integer headerLength = getmolla();
            if (headerLength == null) return;

            if (log.isLoggable(Level.FINER)) {
                log.finer("Reading header for blob " + dataBlockCount++);
            }
            Fileformat.BlobHeader blobHeader = readHeader(headerLength);

            if (log.isLoggable(Level.FINER)) {
                log.finer("Processing blob of type " + blobHeader.getType() + ".");
            }
            byte[] blobData = readRawBlob(blobHeader);

            nextBlob = new PbfRawBlob(blobHeader.getType(), blobData);

        } catch (IOException e) {
            throw new SplitterExce("Unable to get next blob from PBF stream.", e);
        }
    }

    private Integer getmolla() throws IOException {
        int headerLength;
        try {
            headerLength = dis.readInt();
        } catch (EOFException e) {
            eof = true;
            return null;
        }
        return headerLength;
    }

    @Override
    public boolean hasNext() {
        if (nextBlob == null && !eof) {
            try {
                getNextBlob();
            } catch (SplitterExce e) {
                //
            }
        }

        return nextBlob != null;
    }

    @Override
    public PbfRawBlob next() {
        if (nextBlob == null) {
            throw new NoSuchElementException("No more elements to iterate");
        }
        PbfRawBlob result = nextBlob;
        nextBlob = null;
        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public void release() {
        if (dis != null) {
            try {
                dis.close();
            } catch (IOException e) {
                try {
                    throw new PointPathException();
                } catch (PointPathException ex) {
                    //nothing
                }
            }
        }
        dis = null;
    }

    public class SplitterExce extends Exception {
        public SplitterExce(String s, IOException e) {
        }
    }
}
