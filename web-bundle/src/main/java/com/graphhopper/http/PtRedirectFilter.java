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

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import java.net.URI;

@PreMatching
public class PtRedirectFilter implements ContainerRequestFilter {
    private static final String HOT="vehicle";
    private static final String PROFILO="profile";
    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (shouldRedirect(requestContext)) {
            if (requestContext.getUriInfo().getPath().equals("route")) {
                URI forwardURI = requestContext.getUriInfo().getRequestUriBuilder().replacePath("/route-pt")
                        .replaceQueryParam(HOT)
                        .replaceQueryParam(PROFILO)
                        .build();
                requestContext.setRequestUri(forwardURI);
            } else if (requestContext.getUriInfo().getPath().equals("isochrone")) {
                URI forwardURI = requestContext.getUriInfo().getRequestUriBuilder().replacePath("/isochrone-pt")
                        .replaceQueryParam(HOT)
                        .replaceQueryParam(PROFILO)
                        .build();
                requestContext.setRequestUri(forwardURI);
            }
        }
    }

    private boolean shouldRedirect(ContainerRequestContext requestContext) {
        String maybeVehicle = requestContext.getUriInfo().getQueryParameters().getFirst(HOT);
        String maybeProfile = requestContext.getUriInfo().getQueryParameters().getFirst(PROFILO);
        return "pt".equals(maybeVehicle) || "pt".equals(maybeProfile);
    }
}
