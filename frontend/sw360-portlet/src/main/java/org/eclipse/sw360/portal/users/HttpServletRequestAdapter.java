/*
 * Copyright Siemens AG, 2018. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.users;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.servlet.SessionErrors;

import java.util.Optional;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;

class HttpServletRequestAdapter implements RequestAdapter {
    // this constant is supposed to be defined in WebKeys according to docs found online, but it's not
    private static final String COMPANY_ID = "COMPANY_ID";

    private HttpServletRequest request;

    HttpServletRequestAdapter(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public long getCompanyId() {
        return (long) request.getAttribute(COMPANY_ID);
    }

    @Override
    public Consumer<String> getErrorMessagesConsumer() {
        return msg -> SessionErrors.add(request, msg);
    }

    @Override
    public Optional<ServiceContext> getServiceContext() {
        try {
            return Optional.of(ServiceContextFactory.getInstance(request));
        } catch (PortalException e ) {
            return Optional.empty();
        }
    }
}
