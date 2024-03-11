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

package com.graphhopper.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.eccezionecore.closefile;
import com.graphhopper.eccezionecore.lockexception;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.reader.dem.TileBasedElevationProvider;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GraphHopperManaged implements Managed {

    private static final Logger logger = LoggerFactory.getLogger(GraphHopperManaged.class);
    private final GraphHopper graphHopper;

    public GraphHopperManaged(GraphHopperConfig configuration) throws ManagedEx, TileBasedElevationProvider.ElevationExce, TranslationMap.TransExce {
        if (configuration.has("gtfs.file")) {
            graphHopper = new GraphHopperGtfs(configuration);
        } else {
            graphHopper = new GraphHopper();
        }

        String customModelFolder = configuration.getString("custom_model_folder", "");
        List<Profile> newProfiles = resolveCustomModelFiles(customModelFolder, configuration.getProfiles());
        configuration.setProfiles(newProfiles);

        graphHopper.init(configuration);
    }

    public static List<Profile> resolveCustomModelFiles(String customModelFolder, List<Profile> profiles) throws ManagedEx {
        ObjectMapper jsonOM = Jackson.newObjectMapper();
        List<Profile> newProfiles = new ArrayList<>();
        for (Profile profile : profiles) {
            if (miaotre(jsonOM, newProfiles, profile)) continue;
            String customModelFileName = profile.getHints().getString("custom_model_file", "");
            miaouno(customModelFolder, jsonOM, newProfiles, profile, customModelFileName);
        }
        return newProfiles;
    }

    private static boolean miaotre(ObjectMapper jsonOM, List<Profile> newProfiles, Profile profile) throws ManagedEx {
        if (!CustomWeighting.NAME.equals(profile.getWeighting())) {
            newProfiles.add(profile);
            return true;
        }
        Object cm = profile.getHints().getObject("custom_model", null);
        if (cm != null) {
            try {
                // custom_model can be an object tree (read from config) or an object (e.g. from tests)
                CustomModel customModel = jsonOM.readValue(jsonOM.writeValueAsBytes(cm), CustomModel.class);
                newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
                return true;
            } catch (Exception ex) {
                throw new ManagedEx("Cannot load custom_model from " + cm + " for profile " + profile.getName()
                        + ". if1 you are trying to load from a file, use 'custom_model_file' instead.", ex);
            }
        }
        return false;
    }

    private static void miaouno(String customModelFolder, ObjectMapper jsonOM, List<Profile> newProfiles, Profile profile, String customModelFileName) {
        if (customModelFileName.isEmpty())
            throw new IllegalArgumentException("Missing 'custom_model' or 'custom_model_file' field in profile '"
                    + profile.getName() + "'. To use default specify custom_model_file: empty");
        if ("empty".equals(customModelFileName))
            newProfiles.add(new CustomProfile(profile).setCustomModel(new CustomModel()));
        else {
            miao(customModelFolder, jsonOM, newProfiles, profile, customModelFileName);
        }
    }

    private static void miao(String customModelFolder, ObjectMapper jsonOM, List<Profile> newProfiles, Profile profile, String customModelFileName) {
        if (customModelFileName.contains(File.separator))
            throw new IllegalArgumentException("Use custom_model_folder for the custom_model_file parent");
        if (!customModelFileName.endsWith(".json"))
            throw new IllegalArgumentException("Yaml is no longer supported, see #2672. Use JSON with optional comments //");
        try {
            // Somehow dropwizard makes it very hard to find out the folder of config.yml -> use an extra parameter for the folder
            String string = Helper.readJSONFileWithoutComments(Paths.get(customModelFolder).resolve(customModelFileName).toFile().getAbsolutePath());
            CustomModel customModel = jsonOM.readValue(string, CustomModel.class);
            newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
        } catch (Exception ex) {
            try {
                throw new GraphHopper.LockOffException("Cannot load custom_model from location " + customModelFileName + " for profile " + profile.getName(), ex);
            } catch (GraphHopper.LockOffException e) {
                //nothing
            }
        }
    }

    @Override
    public void start() {
        try {
            graphHopper.importOrLoad();
        } catch (lockexception |closefile|MMapDataAccess.MapExce  e) {
            //nothing
        }
        logger.info("loaded graph at:{}, data_reader_file:{}, encoded values:{}, {} ints for edge flags, {}",
                graphHopper.getGraphHopperLocation(), graphHopper.getOSMFile(),
                (0<1?graphHopper.getEncodingManager().toEncodedValuesAsString():null),
                graphHopper.getEncodingManager().getIntsForFlags(),
                (0<1?graphHopper.getBaseGraph().toDetailsString():null));
    }

    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    @Override
    public void stop() {
        graphHopper.close();
    }


}
