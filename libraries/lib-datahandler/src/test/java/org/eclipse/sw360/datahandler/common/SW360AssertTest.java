/*
 * Copyright Siemens AG, 2017. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.datahandler.common;

import com.google.common.collect.Sets;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.junit.Test;

import java.util.Set;

public class SW360AssertTest {

    @Test
    public void testAssertIdsSuccess() throws SW360Exception {
        Set<ObjectWithId> set = Sets.newHashSet(new ObjectWithId(true), new ObjectWithId(true));

        SW360Assert.assertIds(set, o -> o.isSet());
    }

    @Test(expected = SW360Exception.class)
    public void testAssertIdsFails() throws SW360Exception {
        Set<ObjectWithId> set = Sets.newHashSet(new ObjectWithId(true), new ObjectWithId(false), new ObjectWithId(true));

        SW360Assert.assertIdsUnset(set, o -> o.isSet());
    }

    private class ObjectWithId {
        private boolean isSet;

        public ObjectWithId(boolean isSet) {
            this.isSet = isSet;
        }

        public boolean isSet() {
            return isSet;
        }
    }
}
