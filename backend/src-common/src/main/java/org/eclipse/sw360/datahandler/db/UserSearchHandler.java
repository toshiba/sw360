/*
 * Copyright Siemens AG, 2017. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.db;

import org.eclipse.sw360.datahandler.couchdb.DatabaseConnector;
import org.eclipse.sw360.datahandler.couchdb.lucene.LuceneAwareDatabaseConnector;
import org.eclipse.sw360.datahandler.couchdb.lucene.LuceneSearchView;
import org.eclipse.sw360.datahandler.thrift.users.User;

import java.io.IOException;
import java.util.List;

import static org.eclipse.sw360.datahandler.couchdb.lucene.LuceneAwareDatabaseConnector.prepareFuzzyQuery;

public class UserSearchHandler {

    private static final LuceneSearchView luceneSearchView
            = new LuceneSearchView("lucene", "users",
            "function(doc) {" +
                    "  if(doc.type == 'user') { " +
                    "      var ret = new Document();" +
                    "      ret.add(doc.givenname);  " +
                    "      ret.add(doc.lastname);  " +
                    "      ret.add(doc.email);  " +
                    "      return ret;" +
                    "  }" +
                    "}");

    private final LuceneAwareDatabaseConnector connector;

    public UserSearchHandler(DatabaseConnector databaseConnector) throws IOException {
        // Creates the database connector and adds the lucene search view
        connector = new LuceneAwareDatabaseConnector(databaseConnector);
        connector.addView(luceneSearchView);
    }

    private String cleanUp(String searchText) {
        // Lucene seems to split email addresses at an '@' when indexing
        // so in this case we only search for the user name in front of the '@'
        return searchText.split("@")[0];
    }

    public List<User> searchByNameAndEmail(String searchText) {
        // Query the search view for the provided text
        if(searchText == null) {
            searchText = "";
        }
        String queryString = prepareFuzzyQuery(cleanUp(searchText));
        return connector.searchView(User.class, luceneSearchView, queryString);
    }
}
