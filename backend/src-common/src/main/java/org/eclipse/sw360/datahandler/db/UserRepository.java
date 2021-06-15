/*
 * Copyright Siemens AG, 2013-2018. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.db;

import org.eclipse.sw360.components.summary.UserSummary;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.couchdb.SummaryAwareRepository;
import org.eclipse.sw360.datahandler.thrift.users.User;

import com.cloudant.client.api.model.DesignDocument.MapReduce;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD access for the User class
 *
 * @author cedric.bodet@tngtech.com
 * @author Johannes.Najjar@tngtech.com
 * @author thomas.maier@evosoft.com
 */

public class UserRepository extends SummaryAwareRepository<User> {
    private static final String ALL = "function(doc) { if (doc.type == 'user') emit(null, doc._id) }";
    private static final String BYEXTERNALID = "function(doc) { if (doc.type == 'user' && doc.externalid) emit(doc.externalid.toLowerCase(), doc._id) }";
    private static final String BYAPITOKEN = "function(doc) { if (doc.type == 'user') " +
            "  for (var i in doc.restApiTokens) {" +
            "    emit(doc.restApiTokens[i].token, doc._id)" +
            "  }" +
            "}";
    private static final String BYEMAIL = "function(doc) { " +
            "  if (doc.type == 'user') {" +
            "    emit(doc.email, doc._id); " +
            "    if (doc.formerEmailAddresses && Array.isArray(doc.formerEmailAddresses)) {" +
            "      var arr = doc.formerEmailAddresses;" +
            "      for (var i = 0; i < arr.length; i++){" +
            "        emit(arr[i], doc._id);" +
            "      }" +
            "    }" +
            "  }" +
            "}";

    public UserRepository(DatabaseConnectorCloudant databaseConnector) {
        super(User.class, databaseConnector, new UserSummary());
        Map<String, MapReduce> views = new HashMap<String, MapReduce>();
        views.put("all", createMapReduce(ALL, null));
        views.put("byExternalId", createMapReduce(BYEXTERNALID, null));
        views.put("byApiToken", createMapReduce(BYAPITOKEN, null));
        views.put("byEmail", createMapReduce(BYEMAIL, null));
        initStandardDesignDocument(views, databaseConnector);
    }

    @Override
    public List<User> get(Collection<String> ids) {
        return getConnector().get(User.class, ids, true);
    }

    public User getByExternalId(String externalId) {
        if(externalId == null || "".equals(externalId)) {
            // liferay contains the setup user with externalId=="" and we do not want to match him or any other one with empty externalID
            return null;
        }
        final Set<String> userIds = queryForIdsAsValue("byExternalId", externalId.toLowerCase());
        return getUserFromIds(userIds);
    }

    public User getByEmail(String email) {
        final Set<String> userIds = queryForIdsAsValue("byEmail", email);
        return getUserFromIds(userIds);
    }

    public User getByApiToken(String token) {
        final Set<String> userIds = queryForIdsAsValue("byApiToken", token);
        return getUserFromIds(userIds);
    }

    private User getUserFromIds(Set<String> userIds) {
        if (userIds != null && !userIds.isEmpty()) {
            return get(CommonUtils.getFirst(userIds));
        } else {
            return null;
        }
    }
}
