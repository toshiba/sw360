/*
 * Copyright Siemens AG, 2014-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.couchdb;

import org.ektorp.http.HttpClient;
import org.ektorp.http.StdHttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import java.net.MalformedURLException;

/**
 * Class for connecting to a given CouchDB instance
 */
public class DatabaseInstance extends StdCouchDbInstance {

    /**
     * Builds a CouchDB instance using ektorp
     *
     * @param httpClient HttpClient with authentication of the CouchDB instance
     * @throws MalformedURLException
     */
    public DatabaseInstance(HttpClient httpClient) throws MalformedURLException {
        super(httpClient);
        DatabaseInstanceTracker.track(this);
    }

    @Override
    public void createDatabase(String dbName) {
        if (!checkIfDbExists(dbName)) {
            super.createDatabase(dbName);
        }
    }

    public void destroy() {
        getConnection().shutdown();
    }
}
