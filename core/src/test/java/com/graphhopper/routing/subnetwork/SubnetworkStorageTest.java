package com.graphhopper.routing.subnetwork;

import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDirectory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

 class SubnetworkStorageTest {

    @Test
     void testSimple() throws MMapDataAccess.MappaExce {
        SubnetworkStorage storage = new SubnetworkStorage(new RAMDirectory().create("fastest"));
        storage.create(2000);
        storage.setSubnetwork(1, 88);
        assertEquals(88, storage.getSubnetwork(1));
        assertEquals(0, storage.getSubnetwork(0));

        storage.close();
    }
}