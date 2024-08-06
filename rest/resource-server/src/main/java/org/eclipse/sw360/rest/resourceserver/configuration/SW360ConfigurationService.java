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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.ConfigContainer;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.configuration.SW360ConfigService;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.InvalidPropertiesFormatException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SW360ConfigurationService {
    private SW360ConfigService.Iface getThriftConfigClient() {
        return new ThriftClients().makeSW360ConfigClient();
    }

    public RequestStatus createSW360Config(ConfigContainer configContainer) throws TException {
        SW360ConfigService.Iface configService = getThriftConfigClient();
        return configService.createSW360Configs(configContainer);
    }

    public Map<String, String> getSW360Config() throws TException {
        SW360ConfigService.Iface configService = getThriftConfigClient();
        return configService.getSW360Configs();
    }

    public String getSW360ConfigByKey(String key) throws TException {
        SW360ConfigService.Iface configService = getThriftConfigClient();
        return configService.getConfigByKey(key);
    }

    public RequestStatus updateSW360Config(Map<String, String> updatedConfig, User user) throws TException, InvalidPropertiesFormatException {
        try {
            SW360ConfigService.Iface configService = getThriftConfigClient();
            return configService.updateSW360Configs(updatedConfig, user);
        } catch (SW360Exception sw360Exception) {
            throw new InvalidPropertiesFormatException(sw360Exception.getWhy());
        }
    }
}
