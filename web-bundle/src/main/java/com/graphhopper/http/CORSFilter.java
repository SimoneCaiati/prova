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

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Peter Karich
 */
public class CORSFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse rsp = (HttpServletResponse) response;
        HttpServletRequest req = (HttpServletRequest) request;

        String origin = req.getHeader("Origin");
        rsp.setHeader("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS");
        rsp.setHeader("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Range, GH-Client");

        if (isOriginAllowed(origin)) {
            rsp.setHeader("Access-Control-Allow-Origin", origin);
            rsp.setHeader("Access-Control-Allow-Credentials", "true");
        }

        chain.doFilter(request, response);
    }

    private boolean isOriginAllowed(String origin) {
        // Lista delle origini consentite
        List<String> allowedOrigins = Arrays.asList(
                "https://example.com",
                "https://trusteddomain.com"
        );

        // Verifica se l'origine è consentita
        return origin != null && allowedOrigins.contains(origin);
    }



    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //questo metodo è stato creato per l'obiettivo di inutilità
    }

    @Override
    public void destroy() {
        //questo metodo è stato creato per l'obiettivo di inutilità
    }
}
