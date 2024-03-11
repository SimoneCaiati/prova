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

package com.graphhopper.application.cli;

import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.eccezionecore.closefile;
import com.graphhopper.eccezionecore.lockexception;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.ManagedEx;
import com.graphhopper.reader.dem.TileBasedElevationProvider;
import com.graphhopper.storage.MMapDataAccess;
import com.graphhopper.util.TranslationMap;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

public class ImportCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {

    public ImportCommand() {
        super("import", "creates the graphhopper files used for later (faster) starts");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) throws lockexception {
        final GraphHopperManaged graphHopper;
        try {
            graphHopper = new GraphHopperManaged(configuration.getGraphHopperConfiguration());
            eraProprioNecessario(graphHopper);
        }catch (ManagedEx | TileBasedElevationProvider.ElevationExce | TranslationMap.TransExce e) {
            //nothing
        }
    }

    private static void eraProprioNecessario(GraphHopperManaged graphHopper) throws lockexception {
        try {
            graphHopper.getGraphHopper().importAndClose();
        } catch (closefile | MMapDataAccess.MapExce e) {
            //nothing
        }
    }

}
