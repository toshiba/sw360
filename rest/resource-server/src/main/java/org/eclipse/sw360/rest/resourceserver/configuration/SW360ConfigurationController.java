/*
 * Copyright TOSHIBA CORPORATION, 2024. Part of the SW360 Portal Project.
 * Copyright Toshiba Software Development (Vietnam) Co., Ltd., 2024. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.configuration;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@BasePathAwareController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SW360ConfigurationController implements RepresentationModelProcessor<RepositoryLinksResource> {
    private static final String SW360_CONFIG_URL = "/configurations";

    @NonNull
    private final RestControllerHelper restControllerHelper;

    @NonNull
    SW360ConfigurationService sw360ConfigurationService;

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(SW360ConfigurationController.class)
                .slash("api" + SW360_CONFIG_URL).withRel("configurations"));
        return resource;
    }

    @GetMapping(value = SW360_CONFIG_URL)
    public ResponseEntity<?> getSW360Configuration() throws TException {
        return ResponseEntity.ok(sw360ConfigurationService.getSW360Config());
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PatchMapping(value = SW360_CONFIG_URL, consumes = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> updateSW360Configuration(@RequestBody Map<String, String> configuration) throws TException, InvalidPropertiesFormatException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        RequestStatus updateStatus = sw360ConfigurationService.updateSW360Config(configuration, sw360User);
        if (updateStatus.equals(RequestStatus.ACCESS_DENIED)) {
            return new ResponseEntity<>("Only ADMIN users are allowed", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(HttpStatus.Series.SUCCESSFUL);
    }

}
