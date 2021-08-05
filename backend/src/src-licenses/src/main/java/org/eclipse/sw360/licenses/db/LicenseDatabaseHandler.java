/*
 * Copyright Siemens AG, 2013-2018. Part of the SW360 Portal Project.
 * With contributions by Bosch Software Innovations GmbH, 2016-2017.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.licenses.db;

import org.eclipse.sw360.components.summary.SummaryType;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseRepositoryCloudantClient;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.db.CustomPropertiesRepository;
import org.eclipse.sw360.datahandler.db.ReleaseRepository;
import org.eclipse.sw360.datahandler.db.VendorRepository;
import org.eclipse.sw360.datahandler.entitlement.LicenseModerator;
import org.eclipse.sw360.datahandler.permissions.PermissionUtils;
import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.licenses.*;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationRequest;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.licenses.tools.SpdxConnector;
import org.eclipse.sw360.licenses.tools.OSADLObligationConnector;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ektorp.DocumentOperationResult;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.model.Response;
import com.google.common.collect.Sets;

import java.net.MalformedURLException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.eclipse.sw360.datahandler.common.CommonUtils.*;
import static org.eclipse.sw360.datahandler.common.SW360Assert.assertNotNull;
import static org.eclipse.sw360.datahandler.permissions.PermissionUtils.makePermission;
import static org.eclipse.sw360.datahandler.thrift.ThriftValidate.*;

import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONArray;
/**
 * Class for accessing the CouchDB database
 *
 * @author cedric.bodet@tngtech.com
 */
public class LicenseDatabaseHandler {

    /**
     * Connection to the couchDB database
     */
    private final DatabaseConnectorCloudant db;

    /**
     * License Repository
     */
    private final LicenseRepository licenseRepository;
    private final TodoRepository obligRepository;
    private final ObligationElementRepository obligationElementRepository;
    private final ObligationNodeRepository obligationNodeRepository;
    private final LicenseTypeRepository licenseTypeRepository;
    private final LicenseModerator moderator;
    private final CustomPropertiesRepository customPropertiesRepository;
    private final DatabaseRepositoryCloudantClient[] repositories;

    private static boolean IMPORT_STATUS = false;
    private String obligationText;
    private final Logger log = LogManager.getLogger(LicenseDatabaseHandler.class);

    public LicenseDatabaseHandler(Supplier<CloudantClient> httpClient, String dbName) throws MalformedURLException {
        // Create the connector
        db = new DatabaseConnectorCloudant(httpClient, dbName);

        // Create the repository
        licenseRepository = new LicenseRepository(db);
        obligRepository = new TodoRepository(db);
        obligationElementRepository = new ObligationElementRepository(db);
        obligationNodeRepository = new ObligationNodeRepository(db);
        licenseTypeRepository = new LicenseTypeRepository(db);
        customPropertiesRepository = new CustomPropertiesRepository(db);

        repositories = new DatabaseRepositoryCloudantClient[]{
                licenseRepository,
                licenseTypeRepository,
                obligRepository,
                customPropertiesRepository,
                obligationElementRepository,
                obligationNodeRepository
        };

        moderator = new LicenseModerator();
    }


    /////////////////////
    // SUMMARY GETTERS //
    /////////////////////


    /**
     * Get a summary of all licenses from the database
     */
    public List<License> getLicenseSummary() {
        final List<License> licenses = licenseRepository.getAll();
        final List<LicenseType> licenseTypes = licenseTypeRepository.getAll();
        putLicenseTypesInLicenses(licenses, licenseTypes);
        /*Note that risks are not set here*/
        return licenseRepository.makeSummaryFromFullDocs(SummaryType.SUMMARY, licenses);

    }

    /**
     * Get a summary of all licenses from the database for Excel export
     */
    public List<License> getLicenseSummaryForExport() {
        return licenseRepository.getLicenseSummaryForExport();
    }
    
    ////////////////////////////
    // GET INDIVIDUAL OBJECTS //
    ////////////////////////////

    /**
     * Get license from the database and fill its obligations
     */

    public License getLicenseForOrganisation(String id, String organisation) throws SW360Exception {
        License license = licenseRepository.get(id);

        if (license == null) {
            throw new SW360Exception("No license details found in the database for id " + id + ".");
        }

        fillLicenseForOrganisation(organisation, license);

        return license;
    }

    public License getLicenseForOrganisationWithOwnModerationRequests(String id, String organisation, User user) throws SW360Exception {
        List<ModerationRequest> moderationRequestsForDocumentId = moderator.getModerationRequestsForDocumentId(id);

        License license = getLicenseForOrganisation(id, organisation);
        DocumentState documentState;

        if (moderationRequestsForDocumentId.isEmpty()) {
            documentState = CommonUtils.getOriginalDocumentState();
        } else {
            final String email = user.getEmail();
            Optional<ModerationRequest> moderationRequestOptional = CommonUtils.getFirstModerationRequestOfUser(moderationRequestsForDocumentId, email);
            if (moderationRequestOptional.isPresent()
                    && isInProgressOrPending(moderationRequestOptional.get())) {
                ModerationRequest moderationRequest = moderationRequestOptional.get();

                license = moderator.updateLicenseFromModerationRequest(
                        license,
                        moderationRequest.getLicenseAdditions(),
                        moderationRequest.getLicenseDeletions(),
                        organisation);

                for (Obligation oblig : license.getObligations()) {
                    //remove other organisations from whitelist of oblig
                    oblig.setWhitelist(SW360Utils.filterBUSet(organisation, oblig.whitelist));
                }

                documentState = CommonUtils.getModeratedDocumentState(moderationRequest);
            } else {
                documentState = new DocumentState().setIsOriginalDocument(true).setModerationState(moderationRequestsForDocumentId.get(0).getModerationState());
            }
        }
        license.setPermissions(makePermission(license, user).getPermissionMap());
        license.setDocumentState(documentState);
        return license;
    }

    private void fillLicenseForOrganisation(String organisation, License license) {
        if (license.isSetObligationDatabaseIds()) {
            license.setObligations(getObligationsByIds(license.obligationDatabaseIds));
        }


        if (license.isSetLicenseTypeDatabaseId()) {
            final LicenseType licenseType = licenseTypeRepository.get(license.getLicenseTypeDatabaseId());
            license.setLicenseType(licenseType);
        }

        license.setShortname(license.getId());
    }

    ////////////////////
    // BUSINESS LOGIC //
    ////////////////////

    /**
     * Adds a new obligation to the database.
     *
     * @return ID of the added obligations.
     */
    public String addObligations(@NotNull Obligation obligs, User user) throws SW360Exception {
        if (!PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user)){
            return null;
        }
        prepareTodo(obligs);
        obligRepository.add(obligs);

        return obligs.getId();
    }

    /**
     * Adds a new obligation element to the database.
     *
     * @return ID of the added obligations element.
     */
    public String addObligationElements(@NotNull ObligationElement obligationElement, User user) throws SW360Exception {
        if (!PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user)){
            return null;
        }
        prepareObligationElement(obligationElement);
        // check existed obligation element
        List<ObligationElement> existedObligationElement = new ArrayList<>();
        existedObligationElement = isExistedObligationElement(obligationElement);

        if (existedObligationElement.isEmpty()) {
            obligationElementRepository.add(obligationElement);
            return obligationElement.getId();
        } else {
            return existedObligationElement.get(0).getId();
        }
    }


    /**
     * Adds a new obligation node to the database.
     *
     * @return ID of the added obligations node.
     */
    public String addObligationNodes(@NotNull ObligationNode obligationNode, User user) throws SW360Exception {
        if (!PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user)){
            return null;
        }
        prepareObligationNode(obligationNode);
        // check existed node
        List<ObligationNode> existedObligationNode = new ArrayList<>();
        existedObligationNode = isExistedObligationNode(obligationNode);

        if (existedObligationNode.isEmpty()) {
            obligationNodeRepository.add(obligationNode);
            return obligationNode.getId();
        } else {
            return existedObligationNode.get(0).getId();
        }
    }

    /**
     * Add oblig id to a given license
     */
    public RequestStatus addObligationsToLicense(Set<Obligation> obligs, License license, User user) throws SW360Exception {
        license.setObligationDatabaseIds(Sets.newHashSet());
        if (makePermission(license, user).isActionAllowed(RequestedAction.WRITE)) {
            assertNotNull(license);
            for (Obligation oblig : obligs) {
                obligRepository.update(oblig);
                license.addToObligationDatabaseIds(oblig.getId());
            }
            licenseRepository.update(license);
            return RequestStatus.SUCCESS;
        } else {
            License licenseForModerationRequest = getLicenseForOrganisationWithOwnModerationRequests(license.getId(), user.getDepartment(),user);
            assertNotNull(licenseForModerationRequest);
            for (Obligation oblig : obligs) {
                licenseForModerationRequest.addToObligations(oblig);
            }
            return moderator.updateLicense(licenseForModerationRequest, user); // Only moderators can change licenses!
        }
    }

    /**
     * Update the whitelisted obligations for an organisation
     */
    public RequestStatus updateWhitelist(String licenseId, Set<String> whitelistTodos, User user) throws SW360Exception {
        License license = licenseRepository.get(licenseId);
        assertNotNull(license);

        String organisation = user.getDepartment();
        String businessUnit = SW360Utils.getBUFromOrganisation(organisation);

        if (makePermission(license, user).isActionAllowed(RequestedAction.WRITE)) {

            List<Obligation> obligations = obligRepository.get(license.obligationDatabaseIds);
            for (Obligation oblig : obligations) {
                String obligId = oblig.getId();
                Set<String> currentWhitelist = oblig.whitelist != null ? oblig.whitelist : new HashSet<>();

                // Add to whitelist if necessary
                if (whitelistTodos.contains(obligId) && !currentWhitelist.contains(businessUnit)) {
                    oblig.addToWhitelist(businessUnit);
                    obligRepository.update(oblig);
                }

                // Remove from whitelist if necessary
                if (!whitelistTodos.contains(obligId) && currentWhitelist.contains(businessUnit)) {
                    currentWhitelist.remove(businessUnit);
                    obligRepository.update(oblig);
                }

            }
            return RequestStatus.SUCCESS;
        } else {
            //add updated whitelists to obligations in moderation request, not yet in database
            License licenseForModerationRequest = getLicenseForOrganisationWithOwnModerationRequests(licenseId, user.getDepartment(),user);
            List<Obligation> obligations = licenseForModerationRequest.getObligations();
            for (Obligation oblig : obligations) {
                String obligId = oblig.getId();
                Set<String> currentWhitelist = oblig.whitelist != null ? oblig.whitelist : new HashSet<>();

                // Add to whitelist if necessary
                if (whitelistTodos.contains(obligId) && !currentWhitelist.contains(businessUnit)) {
                    oblig.addToWhitelist(businessUnit);
                }

                // Remove from whitelist if necessary
                if (!whitelistTodos.contains(obligId) && currentWhitelist.contains(businessUnit)) {
                    currentWhitelist.remove(businessUnit);
                }

            }
            return moderator.updateLicense(licenseForModerationRequest, user); // Only moderators can edit whitelists!
        }
    }

    public List<License> getLicenses(Set<String> ids, String organisation) {
        final List<License> licenses = licenseRepository.get(ids);
        final List<Obligation> obligationsFromLicenses = getTodosFromLicenses(licenses);
        final List<LicenseType> licenseTypes = getLicenseTypesFromLicenses(licenses);
        filterTodoWhiteListAndFillTodosRisksAndLicenseTypeInLicense(organisation, licenses, obligationsFromLicenses, licenseTypes);
        return licenses;
    }

    public List<License> getDetailedLicenseSummaryForExport(String organisation) {

        final List<License> licenses = licenseRepository.getAll();
        final List<Obligation> obligations = obligRepository.getAll();
        final List<LicenseType> licenseTypes = licenseTypeRepository.getAll();
        return filterTodoWhiteListAndFillTodosRisksAndLicenseTypeInLicense(organisation, licenses, obligations, licenseTypes);
    }

    @NotNull
    private List<License> filterTodoWhiteListAndFillTodosRisksAndLicenseTypeInLicense(String organisation, List<License> licenses, List<Obligation> obligations, List<LicenseType> licenseTypes) {
        filterTodoWhiteList(organisation, obligations);
        fillTodosRisksAndLicenseTypes(licenses, obligations, licenseTypes);
        return licenses;
    }

    private void putLicenseTypesInLicenses(List<License> licenses, List<LicenseType> licenseTypes) {
        final Map<String, LicenseType> licenseTypesById = ThriftUtils.getIdMap(licenseTypes);

        for (License license : licenses) {
            license.setLicenseType(licenseTypesById.get(license.getLicenseTypeDatabaseId()));
            license.unsetLicenseTypeDatabaseId();
        }
    }

    private void putTodosInLicenses(List<License> licenses, List<Obligation> obligations) {
        final Map<String, Obligation> obligationsById = ThriftUtils.getIdMap(obligations);

        for (License license : licenses) {
            license.setObligations(getEntriesFromIds(obligationsById, CommonUtils.nullToEmptySet(license.getObligationDatabaseIds())));
            license.unsetObligationDatabaseIds();
        }
    }

    private void filterTodoWhiteList(String organisation, List<Obligation> obligations) {
        for (Obligation oblig : obligations) {
            oblig.setWhitelist(SW360Utils.filterBUSet(organisation, oblig.getWhitelist()));
        }
    }


    private static <T> List<T> getEntriesFromIds(final Map<String, T> map, Set<String> ids) {
        return ids
                .stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public RequestStatus updateLicense(License inputLicense, User user, User requestingUser) throws SW360Exception {
        if (! makePermission(inputLicense, user).isActionAllowed(RequestedAction.CLEARING)) {
            inputLicense.setChecked(false);
        }
        if (makePermission(inputLicense, user).isActionAllowed(RequestedAction.WRITE)) {

            String businessUnit = SW360Utils.getBUFromOrganisation(requestingUser.getDepartment());

            Optional<License> oldLicense = Optional.ofNullable(inputLicense.getId())
                    .map(id -> licenseRepository.get(inputLicense.getId()));
            boolean isNewLicense = ! oldLicense.isPresent();

            if(isNewLicense){
                validateNewLicense(inputLicense);
            } else {
                validateExistingLicense(inputLicense);
            }

            boolean oldLicenseWasChecked = oldLicense.map(License::isChecked).orElse(false);

            License resultLicense = updateLicenseFromInputLicense(oldLicense, inputLicense, businessUnit, user);

            if (oldLicenseWasChecked && ! resultLicense.isChecked()){
                log.debug("reject license update due to: an already checked license is not allowed to become unchecked again");
                return RequestStatus.FAILURE;
            }

            if(isNewLicense) {
                licenseRepository.add(resultLicense);
            } else {
                licenseRepository.update(resultLicense);
            }
            return RequestStatus.SUCCESS;
        }
        return RequestStatus.FAILURE;
    }

    private License updateLicenseFromInputLicense(Optional<License> oldLicense, License inputLicense, String businessUnit, User user){
        License license = oldLicense.orElse(new License());
        if(inputLicense.isSetObligations()) {
            for (Obligation oblig : inputLicense.getObligations()) {
                if (isTemporaryObligation(oblig)) {
                    oblig.unsetId();
                    try {
                        String obligDatabaseId = addObligations(oblig, user);
                        license.addToObligationDatabaseIds(obligDatabaseId);
                    } catch (SW360Exception e) {
                        log.error("Error adding oblig to database.");
                    }
                } else if (oblig.isSetId()) {
                    Obligation dbTodo = obligRepository.get(oblig.id);
                    if (oblig.whitelist.contains(businessUnit) && !dbTodo.whitelist.contains(businessUnit)) {
                        dbTodo.addToWhitelist(businessUnit);
                        obligRepository.update(dbTodo);
                    }
                    if (!oblig.whitelist.contains(businessUnit) && dbTodo.whitelist.contains(businessUnit)) {
                        dbTodo.whitelist.remove(businessUnit);
                        obligRepository.update(dbTodo);
                    }
                }
            }
        }
        license.setText(inputLicense.getText());
        license.setFullname(inputLicense.getFullname());
        // only a new license gets its id from the shortname. Id of an existing license isn't supposed to be changed anyway
        if (!license.isSetId()) license.setId(inputLicense.getShortname());
        license.unsetShortname();
        license.setLicenseTypeDatabaseId(inputLicense.getLicenseTypeDatabaseId());
        license.unsetLicenseType();
        license.setGPLv2Compat(Optional.ofNullable(inputLicense.getGPLv2Compat())
                .orElse(Ternary.UNDEFINED));
        license.setGPLv3Compat(Optional.ofNullable(inputLicense.getGPLv3Compat())
                .orElse(Ternary.UNDEFINED));
        license.setExternalLicenseLink(inputLicense.getExternalLicenseLink());
        license.setChecked(inputLicense.isChecked());
        license.setObligationDatabaseIds(inputLicense.getObligationDatabaseIds());

        return license;
    }

    public RequestStatus updateLicenseFromAdditionsAndDeletions(License licenseAdditions,
                                                                License licenseDeletions,
                                                                User user,
                                                                User requestingUser){
        try {
            License license = getLicenseForOrganisation(licenseAdditions.getId(), requestingUser.getDepartment());
            license = moderator.updateLicenseFromModerationRequest(license,
                    licenseAdditions,
                    licenseDeletions,
                    requestingUser.getDepartment());
            return updateLicense(license, user, requestingUser);
        } catch (SW360Exception e) {
            log.error("Could not get original license when updating from moderation request.");
            return RequestStatus.FAILURE;
        }
    }

    public List<License> getDetailedLicenseSummaryForExport(String organisation, List<String> identifiers) {
        final List<License> licenses = CommonUtils.nullToEmptyList(licenseRepository.searchByShortName(identifiers));
        List<Obligation> obligations = getTodosFromLicenses(licenses);
        final List<LicenseType> licenseTypes = getLicenseTypesFromLicenses(licenses);
        return filterTodoWhiteListAndFillTodosRisksAndLicenseTypeInLicense(organisation, licenses, obligations, licenseTypes);
    }

    private List<Obligation> getTodosFromLicenses(List<License> licenses) {
        List<Obligation> obligations;
        final Set<String> obligIds = new HashSet<>();
        for (License license : licenses) {
            obligIds.addAll(CommonUtils.nullToEmptySet(license.getObligationDatabaseIds()));
        }

        if (obligIds.isEmpty()) {
            obligations = Collections.emptyList();
        } else {
            obligations = CommonUtils.nullToEmptyList(getObligationsByIds(obligIds));
        }
        return obligations;
    }

    private List<LicenseType> getLicenseTypesFromLicenses(List<License> licenses) {
        List<LicenseType> licenseTypes;
        final Set<String> licenseTypeIds = new HashSet<>();
        for (License license : licenses) {
            if (license.isSetLicenseTypeDatabaseId()) {
                licenseTypeIds.add(license.getLicenseTypeDatabaseId());
            }
        }

        if (licenseTypeIds.isEmpty()) {
            licenseTypes = Collections.emptyList();
        } else {
            licenseTypes = CommonUtils.nullToEmptyList(getLicenseTypesByIds(licenseTypeIds));
        }
        return licenseTypes;
    }

    public List<LicenseType> addLicenseTypes(List<LicenseType> licenseTypes, User user) {
        if (!PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user)){
            return null;
        }
        List<Response> documentOperationResults = licenseTypeRepository.executeBulk(licenseTypes);
        documentOperationResults = documentOperationResults.stream()
                .filter(res -> res.getError() != null || res.getStatusCode() != HttpStatus.SC_CREATED).collect(Collectors.toList());
        if (documentOperationResults.isEmpty()) {
            return licenseTypes;
        } else return null;
    }

    public List<License> addOrOverwriteLicenses(List<License> licenses, User user, boolean allowOverwriting) throws SW360Exception {
        final List<License> knownLicenses = licenseRepository.getAll();
        for (License license : licenses) {
            if(! makePermission(license, user).isActionAllowed(RequestedAction.CLEARING)){
                license.setChecked(false);
            }

            if(! license.isSetId()){
                validateNewLicense(license);
            } else {
                if(allowOverwriting) {
                    knownLicenses.stream()
                            .filter(kl -> license.getId().equals(kl.getId()))
                            .findFirst()
                            .map(License::getRevision)
                            .ifPresent(license::setRevision);
                } else {
                    license.unsetRevision();
                }
                validateExistingLicense(license);
            }
            prepareLicense(license);
        }

        List<Response> documentOperationResults = licenseRepository.executeBulk(licenses);
        documentOperationResults = documentOperationResults.stream()
                .filter(res -> res.getError() != null || res.getStatusCode() != HttpStatus.SC_CREATED).collect(Collectors.toList());
        if (documentOperationResults.isEmpty()) {
            return licenses;
        } else {
            documentOperationResults.forEach(dor ->
                    log.error("Adding license=[" + dor.getId() + "] produced an [" + dor.getError() + "] due to: " + dor.getReason()));
            return null;
        }
    }

    public List<Obligation> addListOfObligations(List<Obligation> listOfObligations, User user) throws SW360Exception {
        if (!PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user)){
            return null;
        }
        for (Obligation Oblig : listOfObligations) {
            prepareTodo(Oblig);
        }

        List<Response> documentOperationResults = obligRepository.executeBulk(listOfObligations);
        documentOperationResults = documentOperationResults.stream()
                .filter(res -> res.getError() != null || res.getStatusCode() != HttpStatus.SC_CREATED).collect(Collectors.toList());
        if (documentOperationResults.isEmpty()) {
            return listOfObligations;
        } else return null;
    }

    public List<License> getLicenses() {
        final List<License> licenses = licenseRepository.getAll();
        if (licenses == null) {
            return Collections.emptyList();
        }
        final List<Obligation> obligations = getTodosFromLicenses(licenses);
        final List<LicenseType> licenseTypes = getLicenseTypesFromLicenses(licenses);
        fillTodosRisksAndLicenseTypes(licenses, obligations, licenseTypes);
        return licenses;
    }

    private void fillTodosRisksAndLicenseTypes(List<License> licenses, List<Obligation> obligations, List<LicenseType> licenseTypes) {
        putTodosInLicenses(licenses, obligations);
        putLicenseTypesInLicenses(licenses, licenseTypes);
    }

    public List<LicenseType> getLicenseTypes() {
        return licenseTypeRepository.getAll();
    }


    public List<Obligation> getObligations() {
        final List<Obligation> obligations = obligRepository.getAll();
        return obligations;
    }

    public List<ObligationNode> getObligationNodes() {
        final List<ObligationNode> obligationNodes = obligationNodeRepository.getAll();
        return obligationNodes;
    }


    public List<ObligationElement> getObligationElements() {
        final List<ObligationElement> obligationElements = obligationElementRepository.getAll();
        return obligationElements;
    }

    public List<LicenseType> getLicenseTypesByIds(Collection<String> ids) {
        return licenseTypeRepository.get(ids);
    }

    public List<Obligation> getObligationsByIds(Collection<String> ids) {
        final List<Obligation> obligations = obligRepository.get(ids);
        for (Obligation oblig : obligations) {
            if(! oblig.isSetWhitelist()){
                oblig.setWhitelist(Collections.emptySet());
            }
        }
        obligations.stream().forEach(obl -> {
            obl.setDevelopmentString(obl.isDevelopment() ? "True" : "False");
            obl.setDistributionString(obl.isDistribution() ? "True" : "False");
        });
        return obligations;
    }

    public LicenseType getLicenseTypeById(String id) {
        return licenseTypeRepository.get(id);
    }

    public Obligation getObligationsById(String id) {
        return obligRepository.get(id);
    }

    public ObligationNode getObligationNodeById(String id) {
        return obligationNodeRepository.get(id);
    }

    public ObligationElement getObligationElementById(String id) {
        return obligationElementRepository.get(id);
    }

    private List<ObligationNode> isExistedObligationNode(ObligationNode obligationNode) {
        String nodeType = obligationNode.getNodeType();
        List<ObligationNode> existedObligationElement = new ArrayList<>();

        if (nodeType.equals("Obligation")) {
            String oblElementId = obligationNode.getOblElementId();
            existedObligationElement = obligationNodeRepository.searchByObligationNodeType(nodeType);
            existedObligationElement.retainAll(obligationNodeRepository.searchByObligationNodeOblElementId(oblElementId));
        } else {
            String nodeText = obligationNode.getNodeText();
            existedObligationElement = obligationNodeRepository.searchByObligationNodeType(nodeType);
            existedObligationElement.retainAll(obligationNodeRepository.searchByObligationNodeText(nodeText));
        }
        return existedObligationElement;
    }

    private List<ObligationElement> isExistedObligationElement(ObligationElement obligationElement) {
        String lang = obligationElement.getLangElement();
        String action = obligationElement.getAction();
        String object = obligationElement.getObject();
        List<ObligationElement> existedObligationElement = new ArrayList<>();

        existedObligationElement = obligationElementRepository.searchByObligationLang(lang);
        existedObligationElement.retainAll(obligationElementRepository.searchByObligationAction(action));
        existedObligationElement.retainAll(obligationElementRepository.searchByObligationObject(object));

        if (existedObligationElement.isEmpty()) {
            return Collections.emptyList();
        }
        return existedObligationElement;
    }

    public RequestStatus deleteLicense(String id, User user) throws SW360Exception {
        License license = licenseRepository.get(id);
        assertNotNull(license);

        if (checkIfInUse(id)) {
            return RequestStatus.IN_USE;
        }

        // Remove the license if the user is allowed to do it by himself
        if (makePermission(license, user).isActionAllowed(RequestedAction.DELETE)) {
            licenseRepository.remove(license);
            moderator.notifyModeratorOnDelete(license.getId());
            return RequestStatus.SUCCESS;
        } else {
            log.error(user + " does not have the permission to delete the license.");
            return RequestStatus.FAILURE;
        }
    }

    public boolean checkIfInUse(String licenseId) {
        ReleaseRepository releaseRepository = new ReleaseRepository(db,new VendorRepository(db));
        final List<Release> usingReleases = releaseRepository.searchReleasesByUsingLicenseId(licenseId);
        return !usingReleases.isEmpty();
    }

    public List<CustomProperties> getCustomProperties(String documentType){
        return customPropertiesRepository.getCustomProperties(documentType);
    }

    public RequestStatus addOrUpdateCustomProperties(CustomProperties customProperties){
        if(customProperties.isSetId()){
            customPropertiesRepository.update(customProperties);
        } else {
            customPropertiesRepository.add(customProperties);
        }
        return RequestStatus.SUCCESS;
    }

    public RequestSummary deleteAllLicenseInformation() {
        RequestSummary result = new RequestSummary()
                .setRequestStatus(RequestStatus.SUCCESS)
                .setTotalElements(0)
                .setTotalAffectedElements(0);
        for(DatabaseRepositoryCloudantClient repository : repositories) {
            result = addRequestSummaries(result, deleteAllDocuments(repository));
        }
        return result;
    }

    private RequestSummary deleteAllDocuments(DatabaseRepositoryCloudantClient repository) {
        Set<String> allIds = repository.getAllIds();
        List<DocumentOperationResult> operationResults = repository.deleteIds(allIds);
        return getRequestSummary(allIds.size(), operationResults.size());
    }

    public RequestSummary importAllSpdxLicenses(User user) {
        RequestSummary requestSummary = new RequestSummary()
                .setTotalAffectedElements(0)
                .setMessage("");
        List<String> spdxIds = SpdxConnector.getAllSpdxLicenseIds();
        Map<String,License> sw360Licenses = ThriftUtils.getIdMap(getLicenses());

        List<License> newLicenses = new ArrayList<>();
        List<String> mismatchedLicenses = new ArrayList<>();

        for(String spdxId : spdxIds){
            License sw360license = sw360Licenses.get(spdxId);

            if(sw360license == null) {

                final Optional<License> spdxLicenseAsSW360License = SpdxConnector.getSpdxLicenseAsSW360License(spdxId);
                if(spdxLicenseAsSW360License.isPresent()){
                    newLicenses.add(spdxLicenseAsSW360License.get());
                }else{
                    log.error("Failed to find SpdxListedLicense with id=" + spdxId);
                }
            }else{
                boolean matches = SpdxConnector.matchesSpdxLicenseText(sw360license,spdxId);
                if (matches) {
                    log.info("The SPDX license with id=" + spdxId + " is already in the DB");
                }else {
                    log.warn("There is a license with id=" + spdxId + " which does not match the SPDX license");
                    mismatchedLicenses.add(spdxId);
                }
            }
        }

        try {
            addOrOverwriteLicenses(newLicenses,user, false);

            if (mismatchedLicenses.size() > 0){
                requestSummary.setMessage("The following licenses did not match their SPDX equivalent: " + COMMA_JOINER.join(mismatchedLicenses));
            }
            requestSummary.setTotalAffectedElements(newLicenses.size());
        } catch (SW360Exception e) {
            String msg = "Failed to import all SPDX licenses";
            requestSummary.setMessage(msg);
            log.error(msg, e);
        }

        return requestSummary
                .setTotalElements(spdxIds.size())
                .setRequestStatus(RequestStatus.SUCCESS);
    }

    public RequestSummary importAllOSADLLicenses(User user) {
        RequestSummary requestSummary = new RequestSummary().setTotalAffectedElements(0).setMessage("");
        if (IMPORT_STATUS) {
            return requestSummary.setRequestStatus(RequestStatus.PROCESSING);
        }

        IMPORT_STATUS = true;
        final List<License> sw360Licenses = licenseRepository.getAll();
        final List<Obligation> sw360Obligations = obligRepository.getAll();
        JSONObject licensesMissing = new JSONObject();
        JSONObject licensesSuccess = new JSONObject();
        OSADLObligationConnector osadlConnector = new OSADLObligationConnector();

        try {
            for (License sw360License : sw360Licenses) {
                String licenseId = sw360License.getId();
                final Optional<Obligation> obligationOptional = OSADLObligationConnector.get(licenseId, user);
                if (obligationOptional.isPresent()) {
                    Obligation oblig = obligationOptional.get();
                    String obligNode = addNodes(osadlConnector.parseText(oblig.getText()).toJSONString(), user);
                    String obligText = buildObligationText(obligNode, 0);
                    boolean OSADLexists = false;
                    for (Obligation sw360Obligation : sw360Obligations) {
                        if (sw360Obligation.getExternalIds() != null && sw360Obligation.getExternalIds().get(OSADLObligationConnector.EXTERNAL_ID_OSADL).equals(licenseId)) {
                            sw360Obligation.setText(obligText);
                            sw360Obligation.setNode(obligNode);
                            sw360Obligation.addToWhitelist(user.getDepartment());
                            obligRepository.update(sw360Obligation);
                            if (!sw360License.getObligationDatabaseIds().contains(sw360Obligation.getId())) {
                                sw360License.addToObligationDatabaseIds(sw360Obligation.getId());
                                sw360License.setObligations(getObligationsByIds(sw360License.obligationDatabaseIds));
                                licenseRepository.update(sw360License);
                            }
                            licensesSuccess.put(licenseId, sw360License.getFullname());
                            OSADLexists = true;
                            break;
                        }
                    }
                    if (!OSADLexists) {
                        if (oblig.isSetId()) {
                            oblig.unsetId();
                        }
                        oblig.setText(obligText);
                        oblig.setNode(obligNode);
                        String obligId = addObligations(oblig, user);
                        sw360License.addToObligationDatabaseIds(obligId);
                        sw360License.setObligations(getObligationsByIds(sw360License.obligationDatabaseIds));
                        licenseRepository.update(sw360License);
                    }
                    licensesSuccess.put(licenseId, sw360License.getFullname());
                } else {
                    licensesMissing.put(licenseId, sw360License.getFullname());
                }
            }
            requestSummary.setMessage("{\"licensesSuccess\":" + licensesSuccess.toString()
                                        + ",\"licensesMissing\":" + licensesMissing.toString()+"}");
            requestSummary.setTotalAffectedElements(licensesSuccess.size());
            requestSummary.setTotalElements(sw360Licenses.size());
            requestSummary.setRequestStatus(RequestStatus.SUCCESS);
            IMPORT_STATUS = false;
        } catch (SW360Exception e) {
            IMPORT_STATUS = false;
            String msg = "Failed to import all OSADL license obligations";
            log.error(msg, e);
            requestSummary.setMessage(msg);
            requestSummary.setRequestStatus(RequestStatus.FAILURE);
        }

        return requestSummary;
    }

    public RequestStatus deleteObligations(String id, User user) throws SW360Exception {
        Obligation oblig = obligRepository.get(id);
        assertNotNull(oblig);

        // Remove the license if the user is allowed to do it by himself
        if (PermissionUtils.isUserAtLeast(UserGroup.SW360_ADMIN, user)) {
            obligRepository.remove(oblig);
            return RequestStatus.SUCCESS;
        } else {
            log.error(user + " does not have the permission to delete oblig.");
            return RequestStatus.ACCESS_DENIED;
        }
    }

    // Read nodes from input and save
    public String addNodes(String jsonString, User user) throws SW360Exception {
        try {
            com.liferay.portal.kernel.json.JSONObject jsonObject = JSONFactoryUtil.createJSONObject(jsonString);
            return addNodes(jsonObject, user);
        }
        catch (Exception e){
            //TODO: handle exception
        }
        return null;
    }

    private String addNodes(com.liferay.portal.kernel.json.JSONObject jsonObject, User user) throws SW360Exception {
        try {
            JSONArray jsonArray = jsonObject.getJSONArray("val");
            jsonObject.put("id", addNodeElement(jsonArray, user));
            jsonObject.remove("val");
            for (int i = 0; i < jsonObject.getJSONArray("children").length(); i++) {
                com.liferay.portal.kernel.json.JSONObject contactObject = jsonObject.getJSONArray("children").getJSONObject(i);
                addNodes(contactObject, user);
            }
            return jsonObject.toJSONString();
        }
        catch (Exception e){
            //TODO: handle exception
        }
        return null;
    }

    // Save node element
    private String addNodeElement(JSONArray jsonArray, User user) throws SW360Exception {
        if ( jsonArray.length() == 1 ) {
            ObligationNode obligationNode = new ObligationNode();
            obligationNode.setNodeType("ROOT");
            obligationNode.setNodeText("");
            return addObligationNodes(obligationNode, user);
        }
        if(jsonArray.getString(0).equals("Obligation")) {
            ObligationElement obligationElement = new ObligationElement();
            obligationElement.setLangElement(jsonArray.getString(1));
            obligationElement.setAction(jsonArray.getString(2));
            obligationElement.setObject(jsonArray.getString(3));
            if (jsonArray.getString(4).equals(ObligationElementStatus.UNDEFINED.toString())) {
                obligationElement.setStatus(ObligationElementStatus.UNDEFINED);
            } else {
                obligationElement.setStatus(ObligationElementStatus.DEFINED);
            }

            ObligationNode obligationNode = new ObligationNode();
            obligationNode.setNodeType("Obligation");
            obligationNode.setOblElementId(addObligationElements(obligationElement, user));
            return addObligationNodes(obligationNode, user);
        } else {
            ObligationNode obligationNode = new ObligationNode();
            obligationNode.setNodeType(jsonArray.getString(0));
            obligationNode.setNodeText(jsonArray.getString(1));
            return addObligationNodes(obligationNode, user);
        }
    }

    // Build obligation text from nodes
    public String buildObligationText(String jsonString, int level) throws SW360Exception {
        try {
            com.liferay.portal.kernel.json.JSONObject jsonObject = JSONFactoryUtil.createJSONObject(jsonString);
            obligationText = "";
            return buildObligationText(jsonObject, level);
        }
        catch (Exception e){
            //TODO: handle exception
        }
        return null;
    }

    private String buildObligationText(com.liferay.portal.kernel.json.JSONObject jsonObject, int level) {
        StringBuilder prefix = new StringBuilder("");
        for(int j = 1; j < level ; j++) {
            prefix = prefix.append("\t");
		}
        try {
            ObligationNode obligationNode = getObligationNodeById(jsonObject.get("id").toString());
            String obligationTextNode = "";
            if (!obligationNode.getNodeType().equals("ROOT")) {
                if (obligationNode.getNodeType().equals("Obligation")) {
                    ObligationElement obligationElement = getObligationElementById(obligationNode.getOblElementId());
                    obligationTextNode = prefix.toString() + obligationElement.getLangElement() + " " + obligationElement.getAction() + " " + obligationElement.getObject();
                } else {
                    obligationTextNode = prefix.toString() + obligationNode.getNodeType() +" "+ obligationNode.getNodeText();
                }
                obligationText = obligationText + "\n" + obligationTextNode;
            }
        } catch (Exception e) {
            //TODO: handle exception
        }
        if (jsonObject.getJSONArray("children").length() != 0 ) {
            for (int i = 0; i < jsonObject.getJSONArray("children").length(); i++) {
                com.liferay.portal.kernel.json.JSONObject contactObject = jsonObject.getJSONArray("children").getJSONObject(i);
                buildObligationText(contactObject, level+1);
            }
        }
        return obligationText.replaceFirst("\n","");
    }
}