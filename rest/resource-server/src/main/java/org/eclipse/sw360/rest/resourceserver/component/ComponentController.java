/*
 * Copyright Siemens AG, 2017-2019.
 * Copyright Bosch Software Innovations GmbH, 2017-2018.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.component;

import com.google.common.collect.Sets;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.resourcelists.PaginationParameterException;
import org.eclipse.sw360.datahandler.resourcelists.PaginationResult;
import org.eclipse.sw360.datahandler.resourcelists.ResourceClassNotFoundException;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.Source;
import org.eclipse.sw360.datahandler.thrift.VerificationStateInfo;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.ReleaseVulnerabilityRelation;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.ReleaseVulnerabilityRelationDTO;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityDTO;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityState;
import org.eclipse.sw360.rest.resourceserver.attachment.Sw360AttachmentService;
import org.eclipse.sw360.rest.resourceserver.core.HalResource;
import org.eclipse.sw360.rest.resourceserver.core.MultiStatus;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.eclipse.sw360.rest.resourceserver.release.Sw360ReleaseService;
import org.eclipse.sw360.rest.resourceserver.user.UserController;
import org.eclipse.sw360.rest.resourceserver.vendor.Sw360VendorService;
import org.eclipse.sw360.rest.resourceserver.vendor.VendorController;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;
import org.eclipse.sw360.rest.resourceserver.vulnerability.Sw360VulnerabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.*;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Strings.isNullOrEmpty;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@BasePathAwareController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ComponentController implements RepresentationModelProcessor<RepositoryLinksResource> {

    public static final String COMPONENTS_URL = "/components";
    private static final Logger log = LogManager.getLogger(ComponentController.class);
    private static final ImmutableMap<String, String> RESPONSE_BODY_FOR_MODERATION_REQUEST = ImmutableMap.<String, String>builder()
            .put("message", "Moderation request is created").build();

    @NonNull
    private final Sw360ComponentService componentService;

    @NonNull
    private final Sw360ReleaseService releaseService;

    @NonNull
    private final Sw360VendorService vendorService;

    @NonNull
    private final Sw360UserService userService;

    @NonNull
    private final Sw360AttachmentService attachmentService;

    @NonNull
    private final RestControllerHelper restControllerHelper;

    @NonNull
    private final Sw360VulnerabilityService vulnerabilityService;
    @RequestMapping(value = COMPONENTS_URL, method = RequestMethod.GET)
    public ResponseEntity<CollectionModel> getComponents(Pageable pageable,
                                                                        @RequestParam(value = "name", required = false) String name,
                                                                        @RequestParam(value = "type", required = false) String componentType,
                                                                        @RequestParam(value = "fields", required = false) List<String> fields,
                                                                        @RequestParam(value = "allDetails", required = false) boolean allDetails,
                                                                        HttpServletRequest request) throws TException, URISyntaxException, PaginationParameterException, ResourceClassNotFoundException {

        User sw360User = restControllerHelper.getSw360UserFromAuthentication();

        List<Component> allComponents = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            allComponents.addAll(componentService.searchComponentByName(name));
        } else {
            allComponents.addAll(componentService.getComponentsForUser(sw360User));
        }

        PaginationResult<Component> paginationResult = restControllerHelper.createPaginationResult(request, pageable, allComponents, SW360Constants.TYPE_COMPONENT);

        List<EntityModel<Component>> componentResources = new ArrayList<>();
        paginationResult.getResources().stream()
                .filter(component -> componentType == null || (component.isSetComponentType() && componentType.equals(component.componentType.name())))
                .forEach(c -> {
                    EntityModel<Component> embeddedComponentResource = null;
                    if (!allDetails) {
                        Component embeddedComponent = restControllerHelper.convertToEmbeddedComponent(c, fields);
                        embeddedComponentResource = EntityModel.of(embeddedComponent);
                    } else {
                        try {
                            embeddedComponentResource = createHalComponent(c, sw360User);
                        } catch (TException e) {
                            throw new RuntimeException(e);
                        }
                        if (embeddedComponentResource == null) {
                            return;
                        }
                    }
                    componentResources.add(embeddedComponentResource);
                });

        CollectionModel resources;
        if (componentResources.size() == 0) {
            resources = restControllerHelper.emptyPageResource(Component.class, paginationResult);
        } else {
            resources = restControllerHelper.generatePagesResource(paginationResult, componentResources);
        }
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    @RequestMapping(value = COMPONENTS_URL + "/usedBy" + "/{id}", method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel>> getUsedByResourceDetails(@PathVariable("id") String id)
            throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication(); // Project
        Set<Project> sw360Projects = componentService.getProjectsByComponentId(id, user);
        Set<Component> sw360Components = componentService.getUsingComponentsForComponent(id, user);

        List<EntityModel> resources = new ArrayList<>();
        sw360Projects.forEach(p -> {
            Project embeddedProject = restControllerHelper.convertToEmbeddedProject(p);
            resources.add(EntityModel.of(embeddedProject));
        });

        sw360Components.forEach(c -> {
                    Component embeddedComponent = restControllerHelper.convertToEmbeddedComponent(c);
                    resources.add(EntityModel.of(embeddedComponent));
                });

        CollectionModel<EntityModel> finalResources = CollectionModel.of(resources);
        return new ResponseEntity(finalResources, HttpStatus.OK);
    }

    @RequestMapping(value = COMPONENTS_URL + "/{id}", method = RequestMethod.GET)
    public ResponseEntity<EntityModel<Component>> getComponent(
            @PathVariable("id") String id) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Component sw360Component = componentService.getComponentForUserById(id, user);
        HalResource<Component> userHalResource = createHalComponent(sw360Component, user);
        return new ResponseEntity<>(userHalResource, HttpStatus.OK);
    }

    @RequestMapping(value = COMPONENTS_URL + "/recentComponents", method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel>> getRecentComponent () throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        List<Component> sw360Components = componentService.getRecentComponents(user);

        List<EntityModel> resources = new ArrayList<>();
        sw360Components.forEach(c -> {
            Component embeddedComponent = restControllerHelper.convertToEmbeddedComponent(c);
            resources.add(EntityModel.of(embeddedComponent));
        });

        CollectionModel<EntityModel> finalResources = CollectionModel.of(resources);
        return new ResponseEntity<>(finalResources, HttpStatus.OK);
    }

    @RequestMapping(value = COMPONENTS_URL + "/mySubscriptions", method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel>> getMySubscriptions () throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        List<Component> sw360Components = componentService.getComponentSubscriptions(user);

        List<EntityModel> resources = new ArrayList<>();
        sw360Components.forEach(c -> {
            Component embeddedComponent = restControllerHelper.convertToEmbeddedComponent(c);
            resources.add(EntityModel.of(embeddedComponent));
        });

        CollectionModel<EntityModel> finalResources = CollectionModel.of(resources);
        return new ResponseEntity<>(finalResources, HttpStatus.OK);
    }

    @RequestMapping(value = COMPONENTS_URL + "/searchByExternalIds", method = RequestMethod.GET)
    public ResponseEntity searchByExternalIds(@RequestParam MultiValueMap<String, String> externalIdsMultiMap) throws TException {
        return restControllerHelper.searchByExternalIds(externalIdsMultiMap, componentService, null);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = COMPONENTS_URL + "/{id}", method = RequestMethod.PATCH)
    public ResponseEntity<EntityModel<Component>> patchComponent(
            @PathVariable("id") String id,
            @RequestBody Component updateComponent) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Component sw360Component = componentService.getComponentForUserById(id, user);
        sw360Component = this.restControllerHelper.updateComponent(sw360Component, updateComponent);
        RequestStatus updateComponentStatus = componentService.updateComponent(sw360Component, user);
        HalResource<Component> userHalResource = createHalComponent(sw360Component, user);
        if (updateComponentStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>(userHalResource, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = COMPONENTS_URL + "/{ids}", method = RequestMethod.DELETE)
    public ResponseEntity<List<MultiStatus>> deleteComponents(
            @PathVariable("ids") List<String> idsToDelete) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        List<MultiStatus> results = new ArrayList<>();
        for(String id:idsToDelete) {
            RequestStatus requestStatus = componentService.deleteComponent(id, user);
            if(requestStatus == RequestStatus.SUCCESS) {
                results.add(new MultiStatus(id, HttpStatus.OK));
            } else if(requestStatus == RequestStatus.IN_USE) {
                results.add(new MultiStatus(id, HttpStatus.CONFLICT));
            } else if (requestStatus == RequestStatus.SENT_TO_MODERATOR) {
                results.add(new MultiStatus(id, HttpStatus.ACCEPTED));
            } else {
                results.add(new MultiStatus(id, HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }
        return new ResponseEntity<>(results, HttpStatus.MULTI_STATUS);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = COMPONENTS_URL, method = RequestMethod.POST)
    public ResponseEntity<EntityModel<Component>> createComponent(@RequestBody Component component) throws URISyntaxException, TException {

        User user = restControllerHelper.getSw360UserFromAuthentication();
        if(component.getComponentType() == null) {
            throw new HttpMessageNotReadableException("Required field componentType is not present");
        }

        if (component.getVendorNames() != null) {
            Set<String> vendors = new HashSet<>();
            for (String vendorUriString : component.getVendorNames()) {
                URI vendorURI = new URI(vendorUriString);
                String path = vendorURI.getPath();
                String vendorId = path.substring(path.lastIndexOf('/') + 1);
                Vendor vendor = vendorService.getVendorById(vendorId);
                String vendorFullName = vendor.getFullname();
                vendors.add(vendorFullName);
            }
            component.setVendorNames(vendors);
        }

        component.setBusinessUnit(user.getDepartment());
        Component sw360Component = componentService.createComponent(component, user);
        HalResource<Component> halResource = createHalComponent(sw360Component, user);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(sw360Component.getId()).toUri();

        return ResponseEntity.created(location).body(halResource);
    }

    @RequestMapping(value = COMPONENTS_URL + "/{id}/attachments", method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel<Attachment>>> getComponentAttachments(
            @PathVariable("id") String id) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Component sw360Component = componentService.getComponentForUserById(id, sw360User);
        final CollectionModel<EntityModel<Attachment>> resources = attachmentService.getResourcesFromList(sw360Component.getAttachments());
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = COMPONENTS_URL + "/{id}/attachment/{attachmentId}", method = RequestMethod.PATCH)
    public ResponseEntity<EntityModel<Attachment>> patchComponentAttachmentInfo(@PathVariable("id") String id,
            @PathVariable("attachmentId") String attachmentId, @RequestBody Attachment attachmentData)
            throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Component sw360Component = componentService.getComponentForUserById(id, sw360User);
        Set<Attachment> attachments = sw360Component.getAttachments();
        Attachment updatedAttachment = attachmentService.updateAttachment(attachments, attachmentData, attachmentId, sw360User);
        RequestStatus updateComponentStatus = componentService.updateComponent(sw360Component, sw360User);
        if (updateComponentStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        EntityModel<Attachment> attachmentResource = EntityModel.of(updatedAttachment);
        return new ResponseEntity<>(attachmentResource, HttpStatus.OK);
    }

    @RequestMapping(value = COMPONENTS_URL + "/{componentId}/attachments", method = RequestMethod.POST, consumes = {"multipart/mixed", "multipart/form-data"})
    public ResponseEntity<HalResource> addAttachmentToComponent(@PathVariable("componentId") String componentId,
                                                                @RequestPart("file") MultipartFile file,
                                                                @RequestPart("attachment") Attachment newAttachment) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Component component = componentService.getComponentForUserById(componentId, sw360User);
        Attachment attachment = null;
        try {
            attachment = attachmentService.uploadAttachment(file, newAttachment, sw360User);
        } catch (IOException e) {
            log.error("failed to upload attachment", e);
            throw new RuntimeException("failed to upload attachment", e);
        }

        component.addToAttachments(attachment);
        RequestStatus updateComponentStatus = componentService.updateComponent(component, sw360User);
        HalResource halComponent = createHalComponent(component, sw360User);
        if (updateComponentStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>(halComponent, HttpStatus.OK);
    }

    @RequestMapping(value = COMPONENTS_URL + "/{componentId}/attachments/{attachmentId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void downloadAttachmentFromComponent(
            @PathVariable("componentId") String componentId,
            @PathVariable("attachmentId") String attachmentId,
            HttpServletResponse response) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Component component = componentService.getComponentForUserById(componentId, sw360User);
        attachmentService.downloadAttachmentWithContext(component, attachmentId, response, sw360User);
    }

    @DeleteMapping(COMPONENTS_URL + "/{componentId}/attachments/{attachmentIds}")
    public ResponseEntity<HalResource<Component>> deleteAttachmentsFromComponent(
            @PathVariable("componentId") String componentId,
            @PathVariable("attachmentIds") List<String> attachmentIds) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Component component = componentService.getComponentForUserById(componentId, user);

        Set<Attachment> attachmentsToDelete = attachmentService.filterAttachmentsToRemove(Source.componentId(componentId),
                component.getAttachments(), attachmentIds);
        if (attachmentsToDelete.isEmpty()) {
            // let the whole action fail if nothing can be deleted
            throw new RuntimeException("Could not delete attachments " + attachmentIds + " from component " + componentId);
        }
        log.debug("Deleting the following attachments from component " + componentId + ": " + attachmentsToDelete);
        component.getAttachments().removeAll(attachmentsToDelete);
        RequestStatus updateComponentStatus = componentService.updateComponent(component, user);
        HalResource<Component> halComponent = createHalComponent(component, user);
        if (updateComponentStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>(halComponent, HttpStatus.OK);
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(ComponentController.class).slash("api/components").withRel("components"));
        return resource;
    }

    private HalResource<Component> createHalComponent(Component sw360Component, User user) throws TException {
        HalResource<Component> halComponent = new HalResource<>(sw360Component);
        User componentCreator = restControllerHelper.getUserByEmail(sw360Component.getCreatedBy());

        if (sw360Component.getReleaseIds() != null) {
            Set<String> releases = sw360Component.getReleaseIds();
            restControllerHelper.addEmbeddedReleases(halComponent, releases, releaseService, user);
        }

        if (sw360Component.getReleases() != null) {
            List<Release> releases = sw360Component.getReleases();
            restControllerHelper.addEmbeddedReleases(halComponent, releases);
        }

        if (sw360Component.getModerators() != null) {
            Set<String> moderators = sw360Component.getModerators();
            restControllerHelper.addEmbeddedModerators(halComponent, moderators);
        }

        if (!isNullOrEmpty(sw360Component.getDefaultVendorId())) {
            Vendor defaultVendor;
            if (sw360Component.getDefaultVendor() == null
                    || !sw360Component.getDefaultVendor().getId().equals(sw360Component.getDefaultVendorId())) {
                defaultVendor = vendorService.getVendorById(sw360Component.getDefaultVendorId());
            } else {
                defaultVendor = sw360Component.getDefaultVendor();
            }
            addEmbeddedDefaultVendor(halComponent, defaultVendor);
            // delete default vendor so that this object is not put directly in the
            // component object (it is available in the embedded resources path though)
            // but keep id so that this field is put directly into the component json
            sw360Component.setDefaultVendor(null);
        }

        if (sw360Component.getVendorNames() != null) {
            Set<String> vendors = sw360Component.getVendorNames();
            restControllerHelper.addEmbeddedVendors(halComponent, vendors);
            sw360Component.setVendorNames(null);
        }

        if (sw360Component.getAttachments() != null) {
            restControllerHelper.addEmbeddedAttachments(halComponent, sw360Component.getAttachments());
        }

        restControllerHelper.addEmbeddedUser(halComponent, componentCreator, "createdBy");

        return halComponent;
    }

    private void addEmbeddedDefaultVendor(HalResource<Component> halComponent, Vendor defaultVendor) {
        HalResource<Vendor> halDefaultVendor = new HalResource<>(defaultVendor);
        Link vendorSelfLink = linkTo(UserController.class)
                .slash("api" + VendorController.VENDORS_URL + "/" + defaultVendor.getId()).withSelfRel();
        halDefaultVendor.add(vendorSelfLink);
        halComponent.addEmbeddedResource("defaultVendor", halDefaultVendor);
    }

    @RequestMapping(value = COMPONENTS_URL + "/mycomponents", method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel>> getMyComponents(Pageable pageable, HttpServletRequest request)
            throws TException, URISyntaxException, PaginationParameterException, ResourceClassNotFoundException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        List<Component> sw360Components = componentService.getMyComponentsForUser(sw360User);
        PaginationResult<Component> paginationResult = restControllerHelper.createPaginationResult(request, pageable,
                sw360Components, SW360Constants.TYPE_COMPONENT);
        List<EntityModel<Component>> componentResources = new ArrayList<>();

        paginationResult.getResources().stream().forEach(c -> {
            Component embeddedComponent = restControllerHelper.convertToEmbeddedComponent(c, null);
            EntityModel<Component> embeddedComponentResource = EntityModel.of(embeddedComponent);
            if (embeddedComponentResource == null) {
                return;
            }
            componentResources.add(embeddedComponentResource);
        });

        CollectionModel finalResources = restControllerHelper.generatePagesResource(paginationResult,
                componentResources);
        HttpStatus status = finalResources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(finalResources, status);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = COMPONENTS_URL + "/{id}/vulnerabilities")
    public ResponseEntity<CollectionModel<EntityModel<VulnerabilityDTO>>> patchReleaseVulnerabilityRelation(@PathVariable("id") String componentId,
                          @RequestBody VulnerabilityState vulnerabilityState) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        if(CommonUtils.isNullOrEmptyCollection(vulnerabilityState.getReleaseVulnerabilityRelationDTOs())) {
            throw new HttpMessageNotReadableException("Required field ReleaseVulnerabilityRelation is not present");
        }
        if(vulnerabilityState.getVerificationState() == null) {
            throw new HttpMessageNotReadableException("Required field verificationState is not present");
        }
        List<String> releaseIds = componentService.getReleaseIdsFromComponentId(componentId, user);

        Map<String,List<VulnerabilityDTO>> releaseIdsWithVulnerabilityDTOsActual = new HashMap<>();
        for (String releaseId: releaseIds) {
            List<VulnerabilityDTO> vulnerabilityDTOs = vulnerabilityService.getVulnerabilitiesByReleaseId(releaseId, user);
            releaseIdsWithVulnerabilityDTOsActual.put(releaseId,vulnerabilityDTOs);
        }
        Set<ReleaseVulnerabilityRelationDTO> releaseVulnerabilityRelationDTOsFromRequest = vulnerabilityState.getReleaseVulnerabilityRelationDTOs();
        // search releaseIdsWithExternalIdsFromRequest
        Map<String,Set<String>> releaseIdsWithExternalIdsFromRequest = getReleaseIdsWithExternalIdsFromRequest(releaseIdsWithVulnerabilityDTOsActual, releaseVulnerabilityRelationDTOsFromRequest);
        if (validateReleaseVulnerabilityRelationDTO(releaseIdsWithExternalIdsFromRequest, vulnerabilityState)) {
            throw new HttpMessageNotReadableException("ReleaseVulnerabilityRelationDTO is not valid");
        }
        Map<String, List<VulnerabilityDTO>> releaseVulnerabilityRelations = getReleaseVulnerabilityRelations(releaseIdsWithVulnerabilityDTOsActual, releaseIdsWithExternalIdsFromRequest);

        RequestStatus requestStatus = null;
        int countRequestStatus = 0;
        for (Map.Entry<String, List<VulnerabilityDTO>> entry : releaseVulnerabilityRelations.entrySet()) {
            for (VulnerabilityDTO vulnerabilityDTO: entry.getValue()) {
                requestStatus = updateReleaseVulnerabilityRelation(entry.getKey(), user, vulnerabilityState,vulnerabilityDTO.getExternalId());
                if (requestStatus != RequestStatus.SUCCESS) {
                    countRequestStatus ++;
                    break;
                }
            };
            if (countRequestStatus != 0){
                break;
            }
        }
        if (requestStatus == RequestStatus.ACCESS_DENIED){
            throw new HttpMessageNotReadableException("User not allowed!");
        }

        List<VulnerabilityDTO> vulnerabilityDTOList = new ArrayList<>();
        for (Map.Entry<String, Set<String>> releaseIdsWithExternalIds: releaseIdsWithExternalIdsFromRequest.entrySet()) {
            vulnerabilityDTOList.addAll(vulnerabilityService.getVulnerabilityDTOByExternalId(releaseIdsWithExternalIds.getValue(), releaseIdsWithExternalIds.getKey()));
        }
        final List<EntityModel<VulnerabilityDTO>> vulnerabilityResources = new ArrayList<>();
        vulnerabilityDTOList.forEach(dto->{
            final EntityModel<VulnerabilityDTO> vulnerabilityDTOEntityModel = EntityModel.of(dto);
            vulnerabilityResources.add(vulnerabilityDTOEntityModel);
        });
        CollectionModel<EntityModel<VulnerabilityDTO>> resources = restControllerHelper.createResources(vulnerabilityResources);
        HttpStatus status = resources == null ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    private static Map<String, List<VulnerabilityDTO>> getReleaseVulnerabilityRelations(Map<String, List<VulnerabilityDTO>> releaseIdsWithVulnerabilityDTOsActual, Map<String, Set<String>> releaseIdsWithExternalIdsFromRequest ) {
        Map<String, List<VulnerabilityDTO>> releaseVulnerabilityRelations = new HashMap<>();
        for (Map.Entry<String, List<VulnerabilityDTO>> releaseIdWithVulnerabilityDTO: releaseIdsWithVulnerabilityDTOsActual.entrySet()) {
            for (Map.Entry<String,Set<String>> releaseIdsWithExternalIds: releaseIdsWithExternalIdsFromRequest.entrySet()) {
                if (releaseIdsWithExternalIds.getKey().equals(releaseIdWithVulnerabilityDTO.getKey())){
                    List<VulnerabilityDTO> vulnerabilityDTOs = new ArrayList<>();
                    for (String externalId: releaseIdsWithExternalIds.getValue()) {
                        for (VulnerabilityDTO vulnerabilityDTO:  releaseIdWithVulnerabilityDTO.getValue()) {
                            if (externalId.equals(vulnerabilityDTO.getExternalId())){
                                vulnerabilityDTOs.add(vulnerabilityDTO);
                            }
                        }
                    }
                    releaseVulnerabilityRelations.put(releaseIdsWithExternalIds.getKey(),vulnerabilityDTOs);
                }
            }
        }
        return releaseVulnerabilityRelations;
    }

    private static Map<String,Set<String>> getReleaseIdsWithExternalIdsFromRequest(Map<String,List<VulnerabilityDTO>> releaseIdsWithVulnerabilityDTOsActual, Set<ReleaseVulnerabilityRelationDTO> releaseVulnerabilityRelationDTOs) {
        Map<String,Set<String>> releaseIdsWithExternalIdsFromRequest = new HashMap<>();
        for (Map.Entry<String, List<VulnerabilityDTO>> entry : releaseIdsWithVulnerabilityDTOsActual.entrySet()) {
            List<VulnerabilityDTO> vulnerabilityDTOs = new ArrayList<>();
            Set<String> externalIds = new HashSet<>();
            entry.getValue().forEach(vulnerabilityDTO -> {
                releaseVulnerabilityRelationDTOs.forEach(releaseVulnerabilityRelationDTO -> {
                    if (vulnerabilityDTO.getExternalId().equals(releaseVulnerabilityRelationDTO.getExternalId())
                            && vulnerabilityDTO.getIntReleaseName().equals(releaseVulnerabilityRelationDTO.getReleaseName())) {
                        externalIds.add(releaseVulnerabilityRelationDTO.getExternalId());
                        vulnerabilityDTOs.add(vulnerabilityDTO);
                    }
                });
            });
            if (CommonUtils.isNullOrEmptyCollection(externalIds) || CommonUtils.isNullOrEmptyCollection(vulnerabilityDTOs)){
                continue;
            }
            releaseIdsWithExternalIdsFromRequest.put(entry.getKey(),externalIds);
        }
        return releaseIdsWithExternalIdsFromRequest;
    }

    private RequestStatus updateReleaseVulnerabilityRelation(String releaseId, User user, VulnerabilityState vulnerabilityState, String externalIdRequest) throws TException {
        List<VulnerabilityDTO> vulnerabilityDTOs = vulnerabilityService.getVulnerabilitiesByReleaseId(releaseId, user);
        ReleaseVulnerabilityRelation releaseVulnerabilityRelation = new ReleaseVulnerabilityRelation();
        for (VulnerabilityDTO vulnerabilityDTO: vulnerabilityDTOs) {
            if (vulnerabilityDTO.getExternalId().equals(externalIdRequest)) {
                releaseVulnerabilityRelation = vulnerabilityDTO.getReleaseVulnerabilityRelation();
            }
        }
        ReleaseVulnerabilityRelation relation = updateReleaseVulnerabilityRelationFromRequest(releaseVulnerabilityRelation, vulnerabilityState, user);
        return vulnerabilityService.updateReleaseVulnerabilityRelation(relation,user);
    }

    private static ReleaseVulnerabilityRelation updateReleaseVulnerabilityRelationFromRequest(ReleaseVulnerabilityRelation dbRelation, VulnerabilityState vulnerabilityState, User user) {
        if (!dbRelation.isSetVerificationStateInfo()) {
            dbRelation.setVerificationStateInfo(new ArrayList<>());
        }
        VerificationStateInfo verificationStateInfo = new VerificationStateInfo();
        List<VerificationStateInfo> verificationStateHistory = dbRelation.getVerificationStateInfo();

        verificationStateInfo.setCheckedBy(user.getEmail());
        verificationStateInfo.setCheckedOn(SW360Utils.getCreatedOn());
        verificationStateInfo.setVerificationState(vulnerabilityState.getVerificationState());
        verificationStateInfo.setComment(vulnerabilityState.getComment());

        verificationStateHistory.add(verificationStateInfo);
        dbRelation.setVerificationStateInfo(verificationStateHistory);
        return dbRelation;
    }

    private boolean validateReleaseVulnerabilityRelationDTO(Map<String,Set<String>> releaseIdsWithExternalIdsFromRequest, VulnerabilityState vulnerabilityState ) {
        long countExternalIdsActual = 0;
        for (Map.Entry<String, Set<String>> releaseIdWithVulnerabilityId: releaseIdsWithExternalIdsFromRequest.entrySet()){
            countExternalIdsActual += releaseIdWithVulnerabilityId.getValue().stream().count();
        }
        long countExternalIdsFromRequest = vulnerabilityState.getReleaseVulnerabilityRelationDTOs().stream().count();
        return countExternalIdsActual != countExternalIdsFromRequest;
    }
}
