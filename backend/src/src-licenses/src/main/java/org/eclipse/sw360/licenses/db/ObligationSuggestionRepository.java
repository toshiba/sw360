/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.licenses.db;

import java.util.HashMap;
import java.util.Map;
import java.util.*;

import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseRepositoryCloudantClient;
import org.eclipse.sw360.datahandler.thrift.licenses.ObligationSuggestion;

import com.cloudant.client.api.model.DesignDocument.MapReduce;

/**
 * CRUD access for the Obligation Suggestion class
 */

public class ObligationSuggestionRepository extends DatabaseRepositoryCloudantClient<ObligationSuggestion> {

    private static final String ALL = "function(doc) { if (doc.type == 'obligationSuggestion') emit(null, doc._id) }";

    public ObligationSuggestionRepository(DatabaseConnectorCloudant db) {
        super(db, ObligationSuggestion.class);
        Map<String, MapReduce> views = new HashMap<String, MapReduce>();
        views.put("all", createMapReduce(ALL, null));
        initStandardDesignDocument(views, db);
    }
}
