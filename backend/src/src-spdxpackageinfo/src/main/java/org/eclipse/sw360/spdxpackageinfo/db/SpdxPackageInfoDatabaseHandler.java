
package org.eclipse.sw360.spdxpackageinfo.db;

import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.model.Response;
import com.google.common.collect.*;

import org.eclipse.sw360.common.utils.BackendUtils;
import org.eclipse.sw360.components.summary.SummaryType;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.common.Duration;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.common.ThriftEnumUtils;
import org.eclipse.sw360.datahandler.couchdb.AttachmentConnector;
import org.eclipse.sw360.datahandler.couchdb.AttachmentStreamConnector;
import org.eclipse.sw360.datahandler.entitlement.ComponentModerator;
import org.eclipse.sw360.datahandler.entitlement.ProjectModerator;
import org.eclipse.sw360.datahandler.entitlement.ReleaseModerator;
import org.eclipse.sw360.datahandler.permissions.DocumentPermissions;
import org.eclipse.sw360.datahandler.permissions.PermissionUtils;
import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangeLogs;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangedFields;
import org.eclipse.sw360.datahandler.thrift.changelogs.Operation;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationRequest;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationService;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectService;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.ProjectVulnerabilityRating;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.ReleaseVulnerabilityRelation;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityCheckStatus;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityService;
import org.eclipse.sw360.datahandler.thrift.spdxdocument.*;
import org.eclipse.sw360.datahandler.thrift.spdxdocument.SPDXDocument;
import org.eclipse.sw360.datahandler.thrift.spdxdocument.SPDXDocumentService;
import org.eclipse.sw360.datahandler.thrift.spdxpackageinfo.*;
import org.eclipse.sw360.mail.MailConstants;
import org.eclipse.sw360.mail.MailUtil;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.thrift.TException;
import org.eclipse.sw360.spdx.SpdxBOMImporter;
import org.eclipse.sw360.spdx.SpdxBOMImporterSink;
import org.eclipse.sw360.spdxdocument.db.SpdxDocumentRepository;
import org.jetbrains.annotations.NotNull;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.*;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Sets.newHashSet;
import static org.eclipse.sw360.datahandler.common.CommonUtils.*;
import static org.eclipse.sw360.datahandler.common.Duration.durationOf;
import static org.eclipse.sw360.datahandler.common.SW360Assert.assertNotNull;
import static org.eclipse.sw360.datahandler.common.SW360Assert.fail;
import static org.eclipse.sw360.datahandler.permissions.PermissionUtils.makePermission;
import static org.eclipse.sw360.datahandler.thrift.ThriftUtils.copyFields;
import static org.eclipse.sw360.datahandler.thrift.ThriftValidate.ensureEccInformationIsSet;
import static org.eclipse.sw360.datahandler.thrift.ThriftValidate.prepareComponents;
import static org.eclipse.sw360.datahandler.thrift.ThriftValidate.prepareReleases;
import static org.eclipse.sw360.spdxdocument.db.SpdxDocumentRepository.*;

public class SpdxPackageInfoDatabaseHandler {

    private static final Logger log = LogManager.getLogger(SpdxPackageInfoDatabaseHandler.class);

    /**
     * Connection to the couchDB database
     */
    private final DatabaseConnectorCloudant db;

    private final SpdxPackageInfoRepository PackageInfoRepository;
    private final SpdxDocumentRepository SPDXDocumentRepository;

    public SpdxPackageInfoDatabaseHandler(Supplier<CloudantClient> httpClient, String dbName) throws MalformedURLException {
        db = new DatabaseConnectorCloudant(httpClient, dbName);

        log.info("Create the repositories ");
        // Create the repositories
        PackageInfoRepository = new SpdxPackageInfoRepository(db);
        SPDXDocumentRepository = new SpdxDocumentRepository(db);

        // Create the moderator
    }

    public List<PackageInformation> getPackageInformationsShort(Set<String> ids, User user) {
        // insert code here
        return null;
    }

    public List<PackageInformation> getPackageInformationSummary(User user) {
        List<PackageInformation> packageInfos = PackageInfoRepository.getPackageInformationSummary();
        return packageInfos;
    }

    public PackageInformation getPackageInformationById(String id, User user) throws SW360Exception {
        PackageInformation packageInfo = PackageInfoRepository.get(id);
        return packageInfo;
    }

    public AddDocumentRequestSummary addPackageInformation(PackageInformation packageInformation, User user) throws SW360Exception {
        AddDocumentRequestSummary requestSummary = new AddDocumentRequestSummary();
        PackageInfoRepository.add(packageInformation);
        String packageInformationId = packageInformation.getId();
        String spdxDocumentId = packageInformation.getSpdxDocumentId();
        SPDXDocument spdxDocument = SPDXDocumentRepository.get(spdxDocumentId);
        Set<String> spdxPackageInfoIds = spdxDocument.getSpdxPackageInfoIds();
        spdxPackageInfoIds.add(packageInformationId);
        spdxDocument.setSpdxPackageInfoIds(spdxPackageInfoIds);
        SPDXDocumentRepository.update(spdxDocument);
        return requestSummary.setRequestStatus(AddDocumentRequestStatus.SUCCESS)
                            .setId(packageInformationId);
    }

    public AddDocumentRequestSummary addPackageInformations(Set<PackageInformation> packageInformations, User user) throws SW360Exception {
        // insert code here
        return null;
    }

    public RequestStatus updatePackageInformation(PackageInformation packageInformation, User user) throws SW360Exception {
        PackageInformation actual = PackageInfoRepository.get(packageInformation.getId());
        assertNotNull(actual, "Could not find SPDX Document Creation Information to update!");
        PackageInfoRepository.update(packageInformation);
        return RequestStatus.SUCCESS;
    }

    public RequestSummary updatePackageInformations(Set<PackageInformation> packageInformations, User user) throws SW360Exception {
        // insert code here
        return null;
    }

    public RequestStatus deletePackageInformation(String id, User user) throws SW360Exception {
        PackageInformation packageInfo = PackageInfoRepository.get(id);
        PackageInfoRepository.remove(packageInfo);
        String spdxDocumentId = packageInfo.getSpdxDocumentId();
        SPDXDocument spdxDocument = SPDXDocumentRepository.get(spdxDocumentId);
        Set<String> packageInfoIds = spdxDocument.getSpdxPackageInfoIds();
        packageInfoIds.remove(id);
        spdxDocument.setSpdxPackageInfoIds(packageInfoIds);
        SPDXDocumentRepository.update(spdxDocument);
        return RequestStatus.SUCCESS;
    }

}