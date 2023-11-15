/*
 * Copyright Siemens AG, 2017-2018.
 * Copyright Bosch Software Innovations GmbH, 2017.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.license;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.resourcelists.PaginationParameterException;
import org.eclipse.sw360.datahandler.resourcelists.PaginationResult;
import org.eclipse.sw360.datahandler.resourcelists.ResourceClassNotFoundException;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.licenses.LicenseType;
import org.eclipse.sw360.datahandler.thrift.licenses.Obligation;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.core.HalResource;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@BasePathAwareController
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class LicenseController implements RepresentationModelProcessor<RepositoryLinksResource> {
    public static final String LICENSES_URL = "/licenses";
    public static final String LICENSE_TYPES_URL = "/licenseTypes";

    @NonNull
    private final Sw360LicenseService licenseService;

    @NonNull
    private final RestControllerHelper restControllerHelper;

    private static final ImmutableMap<String, String> RESPONSE_BODY_FOR_MODERATION_REQUEST = ImmutableMap.<String, String>builder()
            .put("message", "Moderation request is created").build();

    @RequestMapping(value = LICENSES_URL, method = RequestMethod.GET)
    public ResponseEntity<CollectionModel> getLicenses(Pageable pageable, HttpServletRequest request) throws TException, ResourceClassNotFoundException, PaginationParameterException, URISyntaxException {
        List<License> sw360Licenses = licenseService.getLicenses();
        PaginationResult<License> paginationResult = restControllerHelper.createPaginationResult(request, pageable, sw360Licenses, SW360Constants.TYPE_LICENSE);
        List<EntityModel<License>> licenseResources = new ArrayList<>();
        paginationResult.getResources().stream()
                .forEach(license -> {
                    License embeddedLicense = restControllerHelper.convertToEmbeddedLicense(license);
                    EntityModel<License> licenseResource = EntityModel.of(embeddedLicense);
                    licenseResources.add(licenseResource);
                });
        CollectionModel resources;
        if (licenseResources.size() == 0) {
            resources = restControllerHelper.emptyPageResource(License.class, paginationResult);
        } else {
            resources = restControllerHelper.generatePagesResource(paginationResult, licenseResources);
        }
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    @RequestMapping(value = LICENSE_TYPES_URL, method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel<LicenseType>>> getLicenseTypes() throws TException {
        List<LicenseType> sw360LicenseTypes = licenseService.getLicenseTypes();

        List<EntityModel<LicenseType>> licenseTypeResources = new ArrayList<>();
        for (LicenseType sw360LicenseType : sw360LicenseTypes) {
            LicenseType embeddedLicenseType = restControllerHelper.convertToEmbeddedLicenseType(sw360LicenseType);
            EntityModel<LicenseType> licenseResource = EntityModel.of(embeddedLicenseType);
            licenseTypeResources.add(licenseResource);
        }
        CollectionModel<EntityModel<LicenseType>> resources = CollectionModel.of(licenseTypeResources);
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    @RequestMapping(value = LICENSES_URL + "/{id:.+}", method = RequestMethod.GET)
    public ResponseEntity<EntityModel<License>> getLicense(
            @PathVariable("id") String id) throws TException {
        License sw360License = licenseService.getLicenseById(id);
        HalResource<License> licenseHalResource = createHalLicense(sw360License);
        return new ResponseEntity<>(licenseHalResource, HttpStatus.OK);
    }

    @RequestMapping(value = LICENSES_URL + "/{id}/obligations", method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel<Obligation>>> getObligationsByLicenseId(
            @PathVariable("id") String id) throws TException {
        List<Obligation> obligations = licenseService.getObligationsByLicenseId(id);
        List<EntityModel<Obligation>> obligationResources = new ArrayList<>();
        obligations.forEach(o -> {
            Obligation embeddedObligation = restControllerHelper.convertToEmbeddedObligation(o);
            obligationResources.add(EntityModel.of(embeddedObligation));
        });
        CollectionModel<EntityModel<Obligation>> resources = CollectionModel.of(obligationResources);
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }
    
    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL + "/{id:.+}", method = RequestMethod.DELETE)
    public ResponseEntity deleteLicense(
            @PathVariable("id") String id) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        licenseService.deleteLicenseById(id, sw360User);
        return new ResponseEntity<>(HttpStatus.OK);
    }
    
    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL, method = RequestMethod.POST)
    public ResponseEntity<EntityModel<License>> createLicense(
            @RequestBody License license) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        List<License> sw360Licenses = licenseService.getLicenses();
        if(restControllerHelper.checkDuplicateLicense(sw360Licenses, license.shortname)) {
            return new ResponseEntity("sw360 component with name" + license.shortname +"already exists.", HttpStatus.CONFLICT);
        }
        license = licenseService.createLicense(license, sw360User);
        HalResource<License> halResource = createHalLicense(license);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(license.getId()).toUri();

        return ResponseEntity.created(location).body(halResource);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL+ "/{id}", method = RequestMethod.PATCH)
    public ResponseEntity<EntityModel<License>> updateLicense(
            @PathVariable("id") String id,
            @RequestBody License licenseRequestBody) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        License licenseUpdate = licenseService.getLicenseById(id);
        if (licenseUpdate.isChecked() && !licenseRequestBody.isChecked()) {
            return new ResponseEntity("Reject license update due to: an already checked license is not allowed to become unchecked again", HttpStatus.METHOD_NOT_ALLOWED);
        }
        licenseUpdate = restControllerHelper.mapLicenseRequestToLicense(licenseRequestBody, licenseUpdate);
        RequestStatus requestStatus = licenseService.updateLicense(licenseUpdate, sw360User);
        if (requestStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        HalResource<License> halResource = createHalLicense(licenseUpdate);
        return new ResponseEntity<>(halResource, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL+ "/{id}/externalLink", method = RequestMethod.PATCH)
    public ResponseEntity<EntityModel<License>> updateExternalLink(
            @PathVariable("id") String id,
            @RequestBody Map<String, String> reqBodyMaps) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        License licenseUpdate = licenseService.getLicenseById(id);
        String externalLink = "";
        for (Map.Entry<String, String> reqBodyMap: reqBodyMaps.entrySet()) {
            if(reqBodyMap.getKey().equalsIgnoreCase("externalLicenseLink")) {
                externalLink = reqBodyMap.getValue();
            }
        }
        licenseUpdate.setExternalLicenseLink(externalLink);
        RequestStatus requestStatus = licenseService.getStatusUpdateExternalLinkToLicense(licenseUpdate, sw360User);
        HalResource<License> halResource = null;
        if (requestStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        } else if (requestStatus == RequestStatus.SUCCESS) {
            halResource = createHalLicense(licenseUpdate);
            return new ResponseEntity<>(halResource, HttpStatus.OK);
        } else {
            return new ResponseEntity("Update External Link to License Fail!",HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Set<String> getObligationIdsFromRequestWithValueTrue(Map<String, Boolean> reqBodyMaps) {
        Map<String, Boolean> obligationIdsRequest = reqBodyMaps.entrySet().stream()
                .filter(reqBodyMap-> reqBodyMap.getValue().equals(true))
                .collect(Collectors.toMap(reqBodyMap-> reqBodyMap.getKey(),reqBodyMap -> reqBodyMap.getValue()));
        return obligationIdsRequest.keySet();
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL+ "/{id}/whitelist", method = RequestMethod.PATCH)
    public ResponseEntity<EntityModel<License>> updateWhitelist(
            @PathVariable("id") String licenseId,
            @RequestBody Map<String, Boolean> reqBodyMaps) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        License license = licenseService.getLicenseById(licenseId);
        Set<String> obligationIdsByLicense = new HashSet<>();
        if (!CommonUtils.isNullOrEmptyCollection(license.getObligationDatabaseIds())) {
            obligationIdsByLicense = license.getObligationDatabaseIds();
        }
        Map<String, Boolean> obligationIdsRequest = reqBodyMaps.entrySet().stream()
                .collect(Collectors.toMap(reqBodyMap-> reqBodyMap.getKey(),reqBodyMap -> reqBodyMap.getValue()));
        Set<String> obligationIds = obligationIdsRequest.keySet();

        Set<String> commonExtIds = Sets.intersection(obligationIdsByLicense, obligationIds);
        Set<String> diffIds = Sets.difference(obligationIdsByLicense, obligationIds);
        if (commonExtIds.size() != obligationIds.size()) {
            throw new HttpMessageNotReadableException("Obligation Ids not in license!" + license.getShortname());
        }

        Set<String> obligationIdTrue = licenseService.getIdObligationsContainWhitelist(sw360User, licenseId, diffIds);
        obligationIdTrue.addAll(getObligationIdsFromRequestWithValueTrue(reqBodyMaps));

        RequestStatus requestStatus = licenseService.updateWhitelist(obligationIdTrue, licenseId, sw360User);
        HalResource<License> halResource;
        if (requestStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        } else if (requestStatus == RequestStatus.SUCCESS) {
            License licenseUpdate = licenseService.getLicenseById(licenseId);
            halResource = createHalLicense(licenseUpdate);
            return new ResponseEntity<>(halResource, HttpStatus.OK);
        } else {
            return new ResponseEntity("Update Whitelist to Obligation Fail!",HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL + "/{id}/obligations", method = RequestMethod.POST)
    public ResponseEntity linkObligation(
            @PathVariable("id") String id,
            @RequestBody Set<String> obligationIds) throws TException {
        updateLicenseObligations(obligationIds, id, false);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL + "/{id}/obligations", method = RequestMethod.PATCH)
    public ResponseEntity unlinkObligation(
            @PathVariable("id") String id,
            @RequestBody Set<String> obligationIds) throws TException {
        updateLicenseObligations(obligationIds, id, true);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void updateLicenseObligations(Set<String> obligationIds, String licenseId, boolean unLink) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        License license = licenseService.getLicenseById(licenseId);
        licenseService.checkObligationIds(obligationIds);
        Set<String> obligationIdsLink = obligationIds;
        if (unLink) {
            Set<String> licenseObligationIds = license.getObligationDatabaseIds();
            List<String> obligationIdsIncorrect = new ArrayList<>();
            for (String obligationId : obligationIds) {
                if (!licenseObligationIds.contains(obligationId)) {
                    obligationIdsIncorrect.add(obligationId);
                }
            }
            if (!obligationIdsIncorrect.isEmpty()) {
                throw new HttpMessageNotReadableException("Obligation ids: " + obligationIdsIncorrect + " are not linked to license");
            }
            licenseObligationIds.removeAll(obligationIds);
            obligationIdsLink = licenseObligationIds;
        }
        licenseService.updateLicenseToDB(license, obligationIdsLink, sw360User);
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(LicenseController.class).slash("api/licenses").withRel("licenses"));
        return resource;
    }

    private HalResource<License> createHalLicense(License sw360License) {
        HalResource<License> halLicense = new HalResource<>(sw360License);
        if (sw360License.getObligations() != null) {
            List<Obligation> obligations = sw360License.getObligations();
            restControllerHelper.addEmbeddedObligations(halLicense, obligations);
        }
        return halLicense;
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL + "/deleteAll", method = RequestMethod.DELETE)
    public ResponseEntity deleteAllLicense() throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        licenseService.deleteAllLicenseInfo(sw360User);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL + "/import/SPDX", method = RequestMethod.POST)
    public ResponseEntity importSPDX() throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        licenseService.importSpdxInformation(sw360User);
        return new ResponseEntity<>(HttpStatus.OK);
    }   

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = LICENSES_URL + "/downloadLicenses", method = RequestMethod.GET, produces = "application/zip")
    public void downloadLicenseArchive(HttpServletRequest request,HttpServletResponse response) throws TException,IOException  {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        licenseService.getDownloadLicenseArchive(sw360User,request,response);

    }
}
