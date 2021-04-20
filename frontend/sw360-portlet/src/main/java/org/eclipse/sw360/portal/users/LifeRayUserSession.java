/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.users;

import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;

import javax.portlet.PortletRequest;

/**
 * Class to manage user sessions using Liferay's logged-in user
 *
 * @author cedric.bodet@tngtech.com
 */
public class LifeRayUserSession {
    /**
     * Get the email of the currently logged-in user
     *
     * @param request Java portlet render request
     */
    public static String getEmailFromRequest(PortletRequest request) {
        String email = null;

        // Logged-in user can be fetched from Liferay's ThemeDisplay
        ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
        if (themeDisplay.isSignedIn()) {
            User user = themeDisplay.getUser();

            // Get email address from user
            if (user != null) {
                email = user.getEmailAddress();
            }
        }
        return email;
    }
}
