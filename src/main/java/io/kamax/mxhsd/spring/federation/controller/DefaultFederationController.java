/*
 * mxhsd - Corporate Matrix Homeserver
 * Copyright (C) 2017 Maxime Dor
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.mxhsd.spring.federation.controller;

import io.kamax.mxhsd.spring.common.controller.DefaultExceptionHandler;
import io.kamax.mxhsd.spring.common.controller.JsonController;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class DefaultFederationController extends JsonController {

    private final Logger logger = LoggerFactory.getLogger(DefaultFederationController.class);

    @RequestMapping("/**")
    public String catchAll(HttpServletRequest req, HttpServletResponse res) {
        log(logger, req);

        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            logger.info("{}: {}", name, req.getHeader(name));
        }

        StringBuffer postData = new StringBuffer();
        Enumeration<String> postParms = req.getParameterNames();
        while (postParms.hasMoreElements()) {
            String parm = postParms.nextElement();
            if ("access_token".equals(parm.toLowerCase())) {
                postData.append(parm).append("=").append("<redacted>");
            } else {
                if (postData.length() > 0) {
                    postData.append("&");
                }
                postData.append(parm).append("=").append(req.getParameter(parm));
            }
        }

        logger.warn("Unsupported URL: {} {}", req.getMethod(), req.getRequestURL());
        if (postData.length() > 0) {
            logger.info("POST data: {}", postData);
        }
        try {
            String body = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
            if (StringUtils.isNotBlank(body)) {
                logger.info("Body: {}", body);
            } else {
                logger.info("No body");
            }
        } catch (IOException e) {
            logger.debug("Body: Unable to read", e);
        }

        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return DefaultExceptionHandler.handle("M_NOT_IMPLEMENTED", "Not implemented");
    }

}
