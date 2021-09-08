/*
 * Copyright Toshiba corporation, 2021. Part of the SW360 Portal Project.
 * Copyright Toshiba Software Development (Vietnam) Co., Ltd., 2021. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.entitlement;

import org.eclipse.sw360.datahandler.common.Moderator;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.spdx.annotations.Annotations;
import org.eclipse.sw360.datahandler.thrift.spdx.documentcreationinformation.CheckSum;
import org.eclipse.sw360.datahandler.thrift.spdx.spdxpackageinfo.ExternalReference;
import org.eclipse.sw360.datahandler.thrift.spdx.spdxpackageinfo.PackageInformation;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationService;
import org.eclipse.sw360.datahandler.thrift.users.User;

import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.thrift.TException;

/**
 * Moderation for the SPDX Package service
 *
 * @author hieu1.phamvan@toshiba.co.jp
 */

public class SpdxPackageInfoModerator extends Moderator<PackageInformation._Fields, PackageInformation> {

    private static final Logger log = LogManager.getLogger(SpdxPackageInfoModerator.class);


    public SpdxPackageInfoModerator(ThriftClients thriftClients) {
        super(thriftClients);
    }

    public SpdxPackageInfoModerator() {
        super(new ThriftClients());
    }

    public RequestStatus updateSpdxPackageInfo(PackageInformation packageInfo, User user) {

        try {
            ModerationService.Iface client = thriftClients.makeModerationClient();
            client.createSpdxPackageInfoRequest(packageInfo, user);
            return RequestStatus.SENT_TO_MODERATOR;
        } catch (TException e) {
            log.error("Could not moderate SPDX Package Info " + packageInfo.getId() + " for User " + user.getEmail(), e);
            return RequestStatus.FAILURE;
        }
    }

    public RequestStatus deleteSpdxPackageInfo(PackageInformation packageInfo, User user) {
        try {
            ModerationService.Iface client = thriftClients.makeModerationClient();
            client.createSpdxPackageInfoDeleteRequest(packageInfo, user);
            return RequestStatus.SENT_TO_MODERATOR;
        } catch (TException e) {
            log.error("Could not moderate delete SPDX document " + packageInfo.getId() + " for User " + user.getEmail(), e);
            return RequestStatus.FAILURE;
        }
    }

    public PackageInformation updateSpdxPackageInfoFromModerationRequest(PackageInformation packageInfo,
                                                      PackageInformation packageInfoAdditions,
                                                      PackageInformation packageInfoDeletions) {
        for (PackageInformation._Fields field : PackageInformation._Fields.values()) {
            if(packageInfoAdditions.getFieldValue(field) == null && packageInfoDeletions.getFieldValue(field) == null){
                continue;
            }
            switch (field) {
                case ID:
                case REVISION:
                case TYPE:
                    break;
                case CHECKSUMS:
                    packageInfo = updateCheckSums(packageInfo, packageInfoAdditions, packageInfoDeletions);
                    break;
                case EXTERNAL_REFS:
                    packageInfo = updateExternalReference(packageInfo, packageInfoAdditions, packageInfoDeletions);
                    break;
                case ANNOTATIONS:
                    packageInfo = updateAnnotaions(packageInfo, packageInfoAdditions, packageInfoDeletions);
                    break;
                default:
                    packageInfo = updateBasicField(field, PackageInformation.metaDataMap.get(field), packageInfo, packageInfoAdditions, packageInfoDeletions);
            }

        }
        return packageInfo;
    }

    private PackageInformation updateAnnotaions(PackageInformation packageInfo, PackageInformation packageInfoAdditions, PackageInformation packageInfoDeletions) {
        Set<Annotations> actuals = packageInfo.getAnnotations();
        Iterator<Annotations> additionsIterator = packageInfoAdditions.getAnnotationsIterator();
        Iterator<Annotations> deletionsIterator = packageInfoDeletions.getAnnotationsIterator();
        if (additionsIterator == null && deletionsIterator == null) {
            return packageInfo;
        }
        if (actuals == null) {
            actuals = new HashSet<>();
        }
        while (additionsIterator.hasNext()) {
            Annotations additions = additionsIterator.next();
            Annotations actual = new Annotations();
            for (Annotations._Fields field : Annotations._Fields.values()) {
                if (additions.isSet(field)) {
                    actual.setFieldValue(field, additions.getFieldValue(field));
                }
            }
        }
        while (deletionsIterator.hasNext()) {
            Annotations deletions = deletionsIterator.next();
            actuals.remove(deletions);
        }
        packageInfo.setAnnotations(actuals);
        return packageInfo;
    }

    private PackageInformation updateCheckSums(PackageInformation packageInfo, PackageInformation packageInfoAdditions, PackageInformation packageInfoDeletions) {
        Set<CheckSum> actuals = packageInfo.getChecksums();
        Iterator<CheckSum> additionsIterator = packageInfoAdditions.getChecksumsIterator();
        Iterator<CheckSum> deletionsIterator = packageInfoDeletions.getChecksumsIterator();
        if (additionsIterator == null && deletionsIterator == null) {
            return packageInfo;
        }
        if (actuals == null) {
            actuals = new HashSet<>();
        }
        while (additionsIterator.hasNext()) {
            CheckSum additions = additionsIterator.next();
            CheckSum actual = new CheckSum();
            for (CheckSum._Fields field : CheckSum._Fields.values()) {
                if (additions.isSet(field)) {
                    actual.setFieldValue(field, additions.getFieldValue(field));
                }
            }
        }
        while (deletionsIterator.hasNext()) {
            CheckSum deletions = deletionsIterator.next();
            actuals.remove(deletions);
        }
        packageInfo.setChecksums(actuals);
        return packageInfo;
    }

    private PackageInformation updateExternalReference(PackageInformation packageInfo, PackageInformation packageInfoAdditions, PackageInformation packageInfoDeletions) {
        Set<ExternalReference> actuals = packageInfo.getExternalRefs();
        Iterator<ExternalReference> additionsIterator = packageInfoAdditions.getExternalRefsIterator();
        Iterator<ExternalReference> deletionsIterator = packageInfoDeletions.getExternalRefsIterator();
        if (additionsIterator == null && deletionsIterator == null) {
            return packageInfo;
        }
        if (actuals == null) {
            actuals = new HashSet<>();
        }
        while (additionsIterator.hasNext()) {
            ExternalReference additions = additionsIterator.next();
            ExternalReference actual = new ExternalReference();
            for (ExternalReference._Fields field : ExternalReference._Fields.values()) {
                if (additions.isSet(field)) {
                    actual.setFieldValue(field, additions.getFieldValue(field));
                }
            }
        }
        while (deletionsIterator.hasNext()) {
            ExternalReference deletions = deletionsIterator.next();
            actuals.remove(deletions);
        }
        packageInfo.setExternalRefs(actuals);
        return packageInfo;
    }


}

