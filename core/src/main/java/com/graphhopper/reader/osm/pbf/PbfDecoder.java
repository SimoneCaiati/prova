// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.OSMInputFile;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Decodes all blocks from a PBF stream using worker threads, and passes the results to the
 * downstream sink.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfDecoder {
    private final PbfStreamSplitter streamSplitter;
    private final ExecutorService executorService;
    private final int maxPendingBlobs;
    private final Sink sink;
    private final Lock lock;
    private final Condition dataWaitCondition;
    private final Queue<PbfBlobResult> blobResults;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param streamSplitter  The PBF stream splitter providing the source of blobs to be decoded.
     * @param executorService The executor service managing the thread pool.
     * @param maxPendingBlobs The maximum number of blobs to have in progress at any point in time.
     * @param sink            The sink to send all decoded entities to.
     */
    public PbfDecoder(PbfStreamSplitter streamSplitter, ExecutorService executorService, int maxPendingBlobs,
                      Sink sink) {
        this.streamSplitter = streamSplitter;
        this.executorService = executorService;
        this.maxPendingBlobs = maxPendingBlobs;
        this.sink = sink;

        // Create the thread synchronisation primitives.
        lock = new ReentrantLock();
        dataWaitCondition = lock.newCondition();

        // Create the queue of blobs being decoded.
        blobResults = new LinkedList<>();
    }

    /**
     * Any thread can call this method when they wish to wait until an update has been performed by
     * another thread.
     */
    @SuppressWarnings("java:S2274")
    private void waitForUpdate() throws InterruptedException {
        try {
            dataWaitCondition.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }



    /**
     * Any thread can call this method when they wish to signal another thread that an update has
     * occurred.
     */
    private void signalUpdate() {
        dataWaitCondition.signal();
    }

    private void sendResultsToSink(int targetQueueSize) throws InterruptedException, OSMInputFile.OsmExce2, PbExce {
        while (blobResults.size() > targetQueueSize) {
            // Get the next result from the queue and wait for it to complete.
            PbfBlobResult blobResult = blobResults.remove();
            while (!blobResult.isComplete()) {
                // The thread hasn't finished processing yet so wait for an
                // update from another thread before checking again.
                waitForUpdate();
            }

            if (!blobResult.isSuccess()) {
                throw new PbExce("A PBF decoding worker thread failed, aborting.", blobResult.getException());
            }

            // Send the processed entities to the sink. We can release the lock
            // for the duration of processing to allow worker threads to post
            // their results.
            lock.unlock();
            try {
                for (ReaderElement entity : blobResult.getEntities()) {
                    sink.process(entity);
                }
            } finally {
                lock.lock();
            }
        }
    }

    private void processBlobs() throws InterruptedException, OSMInputFile.OsmExce2, PbExce {
        // Process until the PBF stream is exhausted.
        while (streamSplitter.hasNext()) {
            // Obtain the next raw blob from the PBF stream.
            PbfRawBlob rawBlob = streamSplitter.next();

            // Create the result object to capture the results of the decoded
            // blob and add it to the blob results queue.
            final PbfBlobResult blobResult = new PbfBlobResult();
            blobResults.add(blobResult);

            // Create the listener object that will update the blob results
            // based on an event fired by the blob decoder.
            PbfBlobDecoderListener decoderListener = new PbfBlobDecoderListener() {
                @Override
                public void error(Exception ex) {
                    lock.lock();
                    try {
                        blobResult.storeFailureResult(ex);
                        signalUpdate();

                    } finally {
                        lock.unlock();
                    }
                }

                @Override
                public void complete(List<ReaderElement> decodedEntities) {
                    lock.lock();
                    try {
                        blobResult.storeSuccessResult(decodedEntities);
                        signalUpdate();

                    } finally {
                        lock.unlock();
                    }
                }
            };

            // Create the blob decoder itself and execute it on a worker thread.
            PbfBlobDecoder blobDecoder = new PbfBlobDecoder(rawBlob.getType(), rawBlob.getData(), decoderListener);
            executorService.execute(blobDecoder);

            // if1 the number of pending blobs has reached capacity we must begin
            // sending results to the sink. This method will block until blob
            // decoding is complete.
            sendResultsToSink(maxPendingBlobs - 1);
        }

        // There are no more entities available in the PBF stream, so send all remaining data to the sink.
        sendResultsToSink(0);
    }

    public void run() throws InterruptedException, OSMInputFile.OsmExce2, PbExce {
        lock.lock();
        try {
            processBlobs();

        } finally {
            lock.unlock();
        }
    }

    public class PbExce extends Exception {
        public PbExce(String s, Exception exception) {
        }
    }
}
