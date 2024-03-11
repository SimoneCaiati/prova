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

package com.graphhopper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.eccezionecore.closefile;
import com.graphhopper.eccezionecore.lockexception;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.lm.LMPreparationHandler;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.storage.RAMDataAccess;
import com.graphhopper.storage.RAMIntDataAccess;
import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

 class GraphHopperProfileTest {

    private static final String GH_LOCATION = "target/gh-profile-config-gh";

    @Test
     void deserialize() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        String json = "{\"name\":\"my_car\",\"vehicle\":\"car\",\"weighting\":\"fastest\",\"turn_costs\":true,\"foo\":\"bar\",\"baz\":\"buzz\"}";
        Profile profile = objectMapper.readValue(json, Profile.class);
        assertEquals("my_car", profile.getName());
        assertEquals("car", profile.getVehicle());
        assertEquals("fastest", profile.getWeighting());
        assertTrue(profile.isTurnCosts());
        assertEquals(2, profile.getHints().toMap().size());
        assertEquals("bar", profile.getHints().getString("foo", ""));
        assertEquals("buzz", profile.getHints().getString("baz", ""));
    }

    @Test
     void duplicateProfileName_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        assertIllegalArgument(() -> hopper.setProfiles(
                new Profile("my_profile").setVehicle("car").setWeighting("fastest"),
                new Profile("your_profile").setVehicle("car").setWeighting("short_fastest"),
                new Profile("my_profile").setVehicle("car").setWeighting("shortest")
        ), "Profile names must be unique. Duplicate name: 'my_profile'");
    }

    @Test
     void vehicleDoesNotExist_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = new GraphHopper();
        hopper.setGraphHopperLocation(GH_LOCATION).setStoreOnFlush(false).
                setProfiles(new Profile("profile").setVehicle("your_car"));
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "entry in vehicle list not supported: your_car");
    }


    @Test
     void vehicleDoesNotExist_error2() throws TranslationMap.TransExce {
        final GraphHopper hopper = new GraphHopper().setGraphHopperLocation(GH_LOCATION).setStoreOnFlush(false).
                setProfiles(new Profile("profile").setVehicle("your_car"));
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "entry in vehicle list not supported: your_car");
    }


    @Test
     void oneVehicleTwoProfilesWithAndWithoutTC_noError() throws lockexception, closefile, MMapDataAccess.MapExce, TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile1").setVehicle("car").setTurnCosts(false),
                new Profile("profile2").setVehicle("car").setTurnCosts(true));
        try {
            hopper.load();
        } catch (GraphHopper.LockOffException | RAMDataAccess.RamExce | RAMDataAccess.RamExce2 |
                 RAMIntDataAccess.RamIntExce | MMapDataAccess.MappaExce | LMPreparationHandler.LMExce e) {
            //nothing
        }
        assertNotNull(hopper);
    }

    @Test
     void oneVehicleTwoProfilesWithAndWithoutTC2_noError() throws lockexception, closefile, MMapDataAccess.MapExce, TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile2").setVehicle("car").setTurnCosts(true),
                new Profile("profile1").setVehicle("car").setTurnCosts(false));
        try {
            hopper.load();
        } catch (GraphHopper.LockOffException | RAMDataAccess.RamExce | RAMDataAccess.RamExce2 |
                 RAMIntDataAccess.RamIntExce | MMapDataAccess.MappaExce | LMPreparationHandler.LMExce e) {
            //nothing
        }
        assertNotNull(hopper);
    }

    @Test
     void profileWithUnknownWeighting_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car").setWeighting("your_weighting"));
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "Could not create weighting for profile: 'profile'", "Weighting 'your_weighting' not supported");
    }


    @Test
     void chProfileDoesNotExist_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile1").setVehicle("car"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("other_profile"));
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "CH profile references unknown profile 'other_profile'");
    }


    @Test
     void duplicateCHProfile_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("profile"),
                new CHProfile("profile")
        );
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "Duplicate CH reference to profile 'profile'");
    }


    @Test
     void lmProfileDoesNotExist_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile1").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("other_profile"));
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "LM profile references unknown profile 'other_profile'");
    }


    @Test
     void duplicateLMProfile_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile"),
                new LMProfile("profile")
        );
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "Multiple LM profiles are using the same profile 'profile'");
    }


    @Test
     void unknownLMPreparationProfile_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile").setPreparationProfile("xyz")
        );
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "LM profile references unknown preparation profile 'xyz'");
    }


    @Test
     void lmPreparationProfileChain_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile1").setVehicle("car"),
                new Profile("profile2").setVehicle("bike"),
                new Profile("profile3").setVehicle("foot")
        );
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile1"),
                new LMProfile("profile2").setPreparationProfile("profile1"),
                new LMProfile("profile3").setPreparationProfile("profile2")
        );
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "Cannot use 'profile2' as preparation_profile for LM profile 'profile3', because it uses another profile for preparation itself.");
    }


    @Test
     void noLMProfileForPreparationProfile_error() throws TranslationMap.TransExce {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile1").setVehicle("car"),
                new Profile("profile2").setVehicle("bike"),
                new Profile("profile3").setVehicle("foot")
        );
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile1").setPreparationProfile("profile2")
        );
        assertIllegalArgument(() -> {
            try {
                hopper.importOrLoad();
            } catch (lockexception | closefile | MMapDataAccess.MapExce e) {
                e.printStackTrace();
            }
        }, "Unknown LM preparation profile 'profile2' in LM profile 'profile1' cannot be used as preparation_profile");
    }


    private GraphHopper createHopper() throws TranslationMap.TransExce {
        final GraphHopper hopper = new GraphHopper();
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setStoreOnFlush(false);
        return hopper;
    }

    private static void assertIllegalArgument(Runnable runnable, String... messageParts) {
        try {
            runnable.run();
            throw new AssertionError("There should have been an error containing:\n\t" + Arrays.asList(messageParts));
        } catch (IllegalArgumentException e) {
            for (String messagePart : messageParts) {
                assertTrue(e.getMessage().contains(messagePart), "Unexpected error message:\n\t" + e.getMessage() + "\nExpected the message to contain:\n\t" + messagePart);
            }
        }

    }
}