/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.datahandler.test;

import org.eclipse.sw360.testthrift.TestObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestServiceHandlerTest {

    private static final String TEST_ID = "abcdef";
    private static final String TEST_REV = "123456";
    private static final String TEST_NAME = "Super License 3.2";

    TestObject object;
    TestServiceHandler handler;

    @Before
    public void setUp() throws Exception {
        // Prepare object
        object = new TestObject();
        object.setId(TEST_ID);
        object.setRevision(TEST_REV);
        object.setName(TEST_NAME);
        // Prepare handler
        handler = new TestServiceHandler();
    }

    @Test
    public void testTest() throws Exception {
        TestObject returnValue = handler.test(object);
        assertNull(object.getText());
        assertEquals(returnValue.getText(), TestServiceHandler.testText);
    }
}