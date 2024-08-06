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

package org.eclipse.sw360.configuration;

import com.cloudant.client.api.CloudantClient;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.thrift.ConfigContainer;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.configuration.SW360ConfigService;
import org.eclipse.sw360.datahandler.thrift.users.User;
import java.util.Map;
import java.util.function.Supplier;

public class SW360ConfigHandler implements SW360ConfigService.Iface {
    private final SW360ConfigDatabaseHandler sw360ConfigDatabaseHandler;

    public SW360ConfigHandler() {
        sw360ConfigDatabaseHandler = new SW360ConfigDatabaseHandler(DatabaseSettings.getConfiguredClient(), DatabaseSettings.COUCH_DB_CONFIG);
    }

    public SW360ConfigHandler(Supplier<CloudantClient> httpClient, String dbName) {
        sw360ConfigDatabaseHandler = new SW360ConfigDatabaseHandler(httpClient, dbName);
    }

    @Override
    public RequestStatus createSW360Configs(ConfigContainer newConfig) {
        return RequestStatus.SUCCESS;
    }

    @Override
    public RequestStatus updateSW360Configs(Map<String, String> updatedConfigs, User user) throws SW360Exception {
        return sw360ConfigDatabaseHandler.updateSW360Configs(updatedConfigs, user);
    }

    @Override
    public Map<String, String> getSW360Configs() {
        return sw360ConfigDatabaseHandler.getSW360Configs();
    }

    @Override
    public String getConfigByKey(String key) {
        return sw360ConfigDatabaseHandler.getConfigByKey(key);
    }
}
