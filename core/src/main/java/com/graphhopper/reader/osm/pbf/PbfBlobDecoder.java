// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import com.carrotsearch.hppc.LongIndexedContainer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMFileHeader;
import com.graphhopper.util.Helper;
import org.openstreetmap.osmosis.osmbinary.Fileformat;
import org.openstreetmap.osmosis.osmbinary.Osmformat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Converts PBF block data into decoded entities ready to be passed into an Osmosis pipeline. This
 * class is designed to be passed into a pool of worker threads to allow multi-threaded decoding.
 * <p>
 *
 * @author Brett Henderson
 */
@SuppressWarnings("java:S3457")
public class PbfBlobDecoder implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(PbfBlobDecoder.class);
    private static final boolean CHECK_DATA = false;
    private final String blobType;
    private final byte[] rawBlob;
    private final PbfBlobDecoderListener listener;
    private List<ReaderElement> decodedEntities;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param blobType The type of blob.
     * @param rawBlob  The raw data of the blob.
     * @param listener The listener for receiving decoding results.
     */
    public PbfBlobDecoder(String blobType, byte[] rawBlob, PbfBlobDecoderListener listener) {
        this.blobType = blobType;
        this.rawBlob = rawBlob;
        this.listener = listener;
    }

    private byte[] readBlobContent() throws IOException, BlobExce1, BlobExce2 {
        Fileformat.Blob blob = Fileformat.Blob.parseFrom(rawBlob);
        byte[] blobData;

        if (blob.hasRaw()) {
            blobData = blob.getRaw().toByteArray();
        } else if (blob.hasZlibData()) {
            Inflater inflater = new Inflater();
            inflater.setInput(blob.getZlibData().toByteArray());
            blobData = new byte[blob.getRawSize()];
            try {
                inflater.inflate(blobData);
            } catch (DataFormatException e) {
                throw new BlobExce1("Unable to decompress PBF blob.", e);
            }
            if (!inflater.finished()) {
                throw new BlobExce2("PBF blob contains incomplete compressed data.");
            }
            inflater.end();
        } else {
            throw new BlobExce2("PBF blob uses unsupported compression, only raw or zlib may be used.");
        }

        return blobData;
    }

    private void processOsmHeader(byte[] data) throws InvalidProtocolBufferException, BlobExce3 {
        Osmformat.HeaderBlock header = Osmformat.HeaderBlock.parseFrom(data);

        // Build the list of active and unsupported features in the file.
        List<String> supportedFeatures = Arrays.asList("OsmSchema-V0.6", "DenseNodes");
        List<String> unsupportedFeatures = new ArrayList<>();
        for (String feature : header.getRequiredFeaturesList()) {
            if (supportedFeatures.contains(feature)) {
                //
            } else {
                unsupportedFeatures.add(feature);
            }
        }

        // We can't continue if there are any unsupported features. We wait
        // until now so that we can display all unsupported features instead of
        // just the first one we encounter.
        if (!unsupportedFeatures.isEmpty()) {
            throw new BlobExce3("PBF file contains unsupported features " + unsupportedFeatures);
        }

        OSMFileHeader fileheader = new OSMFileHeader();
        long milliSecondDate = header.getOsmosisReplicationTimestamp();
        fileheader.setTag("timestamp", Helper.createFormatter().format(new Date(milliSecondDate * 1000)));
        decodedEntities.add(fileheader);

        // Build a new bound object which corresponds to the header.

    }
    private static final String MATCH=") don't match";
    private Map<String, String> buildTags(List<Integer> keys, List<Integer> values, PbfFieldDecoder fieldDecoder) throws BlobExce3 {

        // Ensure parallel lists are of equal size.
        methodBlob1(keys.size() != values.size(), "Number of tag keys (" + keys.size() + ") and tag values ("
                + values.size() + MATCH);

        Iterator<Integer> keyIterator = keys.iterator();
        Iterator<Integer> valueIterator = values.iterator();
        if (keyIterator.hasNext()) {
            Map<String, String> tags = new HashMap<>(keys.size());
            while (keyIterator.hasNext()) {
                String key = fieldDecoder.decodeString(keyIterator.next());
                String value = fieldDecoder.decodeString(valueIterator.next());
                tags.put(key, value);
            }
            return tags;
        }
        return Collections.emptyMap();
    }

    private void processNodes(List<Osmformat.Node> nodes, PbfFieldDecoder fieldDecoder) throws BlobExce3 {
        for (Osmformat.Node node : nodes) {
            Map<String, String> tags = buildTags(node.getKeysList(), node.getValsList(), fieldDecoder);

            ReaderNode osmNode = new ReaderNode(node.getId(), fieldDecoder.decodeLatitude(node
                    .getLat()), fieldDecoder.decodeLatitude(node.getLon()));
            osmNode.setTags(tags);

            // Add the bound object to the results.
            decodedEntities.add(osmNode);
        }
    }

    private void processNodes(Osmformat.DenseNodes nodes, PbfFieldDecoder fieldDecoder) throws BlobExce3 {
        List<Long> idList = nodes.getIdList();
        List<Long> latList = nodes.getLatList();
        List<Long> lonList = nodes.getLonList();

        // Ensure parallel lists are of equal size.
        methodBlob1((idList.size() != latList.size()) || (idList.size() != lonList.size()), "Number of ids (" + idList.size() + "), latitudes (" + latList.size()
                + "), and longitudes (" + lonList.size() + MATCH);

        Iterator<Integer> keysValuesIterator = nodes.getKeysValsList().iterator();


        long nodeId = 0;
        long latitude = 0;
        long longitude = 0;


        for (int i = 0; i < idList.size(); i++) {
            // Delta decode node fields.
            nodeId += idList.get(i);
            latitude += latList.get(i);
            longitude += lonList.get(i);


            // Build the tags. The key and value string indexes are sequential
            // in the same PBF array. Each set of tags is delimited by an index
            // with a value of 0.
            Map<String, String> tags = null;
            while (keysValuesIterator.hasNext()) {
                int keyIndex = keysValuesIterator.next();
                if (keyIndex == 0) {
                    break;
                }
                methodBlob1(!keysValuesIterator.hasNext(), "The PBF DenseInfo keys/values list contains a key with no corresponding value.");
                int valueIndex = keysValuesIterator.next();

                if (tags == null) {
                    // divide by 2 as key&value, multiple by 2 because of the better approximation
                    tags = new HashMap<>(Math.max(3, 2 * (nodes.getKeysValsList().size() / 2) / idList.size()));
                }

                tags.put(fieldDecoder.decodeString(keyIndex), fieldDecoder.decodeString(valueIndex));
            }

            ReaderNode node = new ReaderNode(nodeId, fieldDecoder.decodeLatitude(latitude), fieldDecoder.decodeLongitude(longitude));
            node.setTags(tags);

            // Add the bound object to the results.
            decodedEntities.add(node);
        }
    }

    private void methodBlob1(boolean keysValuesIterator, String message) throws BlobExce3 {
        if (CHECK_DATA && (keysValuesIterator)) {
                throw new BlobExce3(message);

        }
    }

    private void processWays(List<Osmformat.Way> ways, PbfFieldDecoder fieldDecoder) throws BlobExce3 {
        for (Osmformat.Way way : ways) {
            Map<String, String> tags = buildTags(way.getKeysList(), way.getValsList(), fieldDecoder);
            ReaderWay osmWay = new ReaderWay(way.getId());
            osmWay.setTags(tags);

            // Build up the list of way nodes for the way. The node ids are
            // delta encoded meaning that each id is stored as a delta against
            // the previous one.
            long nodeId = 0;
            LongIndexedContainer wayNodes = osmWay.getNodes();
            for (long nodeIdOffset : way.getRefsList()) {
                nodeId += nodeIdOffset;
                wayNodes.add(nodeId);
            }

            decodedEntities.add(osmWay);
        }
    }

    private void buildRelationMembers(ReaderRelation relation,
                                      List<Long> memberIds, List<Integer> memberRoles, List<Osmformat.Relation.MemberType> memberTypes,
                                      PbfFieldDecoder fieldDecoder) throws BlobExce3 {

        // Ensure parallel lists are of equal size.
        methodBlob1((memberIds.size() != memberRoles.size()) || (memberIds.size() != memberTypes.size()), "Number of member ids (" + memberIds.size() + "), member roles ("
                + memberRoles.size() + "), and member types (" + memberTypes.size() + MATCH);

        Iterator<Long> memberIdIterator = memberIds.iterator();
        Iterator<Integer> memberRoleIterator = memberRoles.iterator();
        Iterator<Osmformat.Relation.MemberType> memberTypeIterator = memberTypes.iterator();

        // Build up the list of relation members for the way. The member ids are
        // delta encoded meaning that each id is stored as a delta against
        // the previous one.
        long refId = 0;
        while (memberIdIterator.hasNext()) {
            Osmformat.Relation.MemberType memberType = memberTypeIterator.next();
            refId += memberIdIterator.next();

            ReaderElement.Type entityType = ReaderElement.Type.NODE;
            if (memberType == Osmformat.Relation.MemberType.WAY) {
                entityType = ReaderElement.Type.WAY;
            } else if (memberType == Osmformat.Relation.MemberType.RELATION) {
                entityType = ReaderElement.Type.RELATION;
            }
            if (CHECK_DATA && entityType == ReaderElement.Type.NODE && memberType != Osmformat.Relation.MemberType.NODE) {
                    throw new BlobExce3("Member type of " + memberType + " is not supported.");
            }

            ReaderRelation.Member member = new ReaderRelation.Member(entityType, refId, fieldDecoder.decodeString(memberRoleIterator.next()));
            relation.add(member);
        }
    }

    private void processRelations(List<Osmformat.Relation> relations, PbfFieldDecoder fieldDecoder) throws BlobExce3 {
        for (Osmformat.Relation relation : relations) {
            Map<String, String> tags = buildTags(relation.getKeysList(), relation.getValsList(), fieldDecoder);

            ReaderRelation osmRelation = new ReaderRelation(relation.getId());
            osmRelation.setTags(tags);

            buildRelationMembers(osmRelation, relation.getMemidsList(), relation.getRolesSidList(),
                    relation.getTypesList(), fieldDecoder);

            // Add the bound object to the results.
            decodedEntities.add(osmRelation);
        }
    }

    private void processOsmPrimitives(byte[] data) throws InvalidProtocolBufferException, BlobExce3 {
        Osmformat.PrimitiveBlock block = Osmformat.PrimitiveBlock.parseFrom(data);
        PbfFieldDecoder fieldDecoder = new PbfFieldDecoder(block);

        for (Osmformat.PrimitiveGroup primitiveGroup : block.getPrimitivegroupList()) {
            processNodes(primitiveGroup.getDense(), fieldDecoder);
            processNodes(primitiveGroup.getNodesList(), fieldDecoder);
            processWays(primitiveGroup.getWaysList(), fieldDecoder);
            processRelations(primitiveGroup.getRelationsList(), fieldDecoder);
        }
    }

    private void runAndTrapExceptions() throws BlobExce {
        try {
            decodedEntities = new ArrayList<>();
            if ("OSMHeader".equals(blobType)) {
                processOsmHeader(readBlobContent());

            } else if ("OSMData".equals(blobType)) {
                processOsmPrimitives(readBlobContent());

            } else if (log.isDebugEnabled())
                log.debug("Skipping unrecognised blob type " + blobType);
        } catch (IOException | BlobExce1 | BlobExce2 | BlobExce3 e) {
            throw new BlobExce("Unable to process PBF blob", (IOException) e);
        }
    }

    @Override
    public void run() {
        try {
            runAndTrapExceptions();
            listener.complete(decodedEntities);

        } catch (RuntimeException | BlobExce e) {
            // exception is properly rethrown in PbfDecoder.sendResultsToSink
            listener.error(e);
        }
    }

    private class BlobExce extends Exception {
        public BlobExce(String unableToProcessPbfBlob, IOException e) {
        }
    }

    private class BlobExce1 extends Exception {
        public BlobExce1(String s, DataFormatException e) {
        }
    }

    private class BlobExce2 extends Exception {
        public BlobExce2(String s) {
        }
    }

    private class BlobExce3 extends Exception {
        public BlobExce3(String s) {
        }
    }
}
