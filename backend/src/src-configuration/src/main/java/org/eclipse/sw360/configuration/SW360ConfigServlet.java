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

import org.apache.thrift.protocol.TCompactProtocol;
import org.eclipse.sw360.datahandler.thrift.configuration.SW360ConfigService;
import org.eclipse.sw360.projects.Sw360ThriftServlet;

public class SW360ConfigServlet extends Sw360ThriftServlet {
    public SW360ConfigServlet() {
        super(new SW360ConfigService.Processor<>(new SW360ConfigHandler()), new TCompactProtocol.Factory());
    }
}
