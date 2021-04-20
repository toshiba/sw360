/*
 * Copyright (c) Bosch Software Innovations GmbH 2016.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.exporter.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.eclipse.sw360.datahandler.common.ImportCSV;
import org.eclipse.sw360.datahandler.thrift.licenses.Obligation;
import org.apache.commons.csv.CSVRecord;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.eclipse.sw360.exporter.utils.ConvertRecord.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author: birgit.heydenreich@tngtech.com
 */

public class ConvertRecordTest {

    private List<CSVRecord> emptyRecord;
    private List<CSVRecord> tooSmallRecord;
    private List<CSVRecord> smallestPossibleRecord;
    private List<CSVRecord> fullRecord;
    private List<CSVRecord> emptyPropRecord;
    private List<CSVRecord> whitePropRecord;
    private List<CSVRecord> emptyValRecord;
    private List<CSVRecord> whiteValRecord;
    private List<Obligation> obligations;
    private List<CSVRecord> fullTodoRecord;

    @Before
    public void prepare() {
        emptyRecord = readToRecord(" ");

        String tooSmall = "header\n 1, prop";
        tooSmallRecord = readToRecord(tooSmall);

        String emptyProp = "header\n 1,,val";
        emptyPropRecord = readToRecord(emptyProp);

        String whiteProp = "header\n 1,  ,val";
        whitePropRecord = readToRecord(whiteProp);

        String emptyValue = "header\n 1,prop,";
        emptyValRecord = readToRecord(emptyValue);

        String whiteValue = "header\n 1,prop, ";
        whiteValRecord = readToRecord(whiteValue);

        String smallestPossible = "headers\n 1,prop1,val1";
        smallestPossibleRecord = readToRecord(smallestPossible);

        String full = "headers\n 1,A,A1 \n 2,A,A2 \n 3,A,A3 \n 4,B,B1 \n 5,B,B2";
        fullRecord = readToRecord(full);

        String fullTodo = "header \n 'title1','text1','true','true' \n 'title2','text2','false','false', '{\"key\": \"value\"}'";
        fullTodoRecord = readToRecord(fullTodo);

        Map<String, String> obligCustomProperties1 = new HashMap<>();
        obligCustomProperties1.put("A", "A1");
        Obligation oblig1 = new Obligation().setId("1").setCustomPropertyToValue(obligCustomProperties1);
        Map<String, String> obligCustomProperties2 = new HashMap<>();
        obligCustomProperties2.put("A", "A2");
        obligCustomProperties2.put("C", "C2");
        Obligation oblig2 = new Obligation().setId("2").setCustomPropertyToValue(obligCustomProperties2);
        obligations = Arrays.asList(oblig1, oblig2);
    }

    public List<CSVRecord> readToRecord(String string) {
        ByteArrayInputStream stream = new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
        return ImportCSV.readAsCSVRecords(stream);
    }

    @Test
    public void testConvertCustomPropertiesEmpty() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(emptyRecord);
        assertThat(properties.keySet().size(), is(0));
        assertThat(properties.values().size(), is(0));
    }

    @Test
    public void testConvertCustomPropertiesEmptyProp() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(emptyPropRecord);
        assertThat(properties.keySet().size(), is(0));
        assertThat(properties.values().size(), is(0));
    }

    @Test
    public void testConvertCustomPropertiesWhitespaceProp() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(whitePropRecord);
        assertThat(properties.keySet().size(), is(0));
        assertThat(properties.values().size(), is(0));
    }

    @Test
    public void testConvertCustomPropertiesEmptyValue() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(emptyValRecord);
        assertThat(properties.keySet().size(), is(1));
        assertThat(properties.values().size(), is(1));
    }

    @Test
    public void testConvertCustomPropertiesWhiteValue() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(whiteValRecord);
        assertThat(properties.keySet().size(), is(1));
        assertThat(properties.values().size(), is(1));
    }

    @Test
    public void testConvertCustomPropertiesTooSmall() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(tooSmallRecord);
        assertThat(properties.keySet().size(), is(0));
        assertThat(properties.values().size(), is(0));
    }

    @Test
    public void testConvertCustomPropertiesSmallestPossible() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(smallestPossibleRecord);
        assertThat(properties.keySet(), Matchers.containsInAnyOrder("prop1"));
        assertThat(properties.get("prop1"), Matchers.containsInAnyOrder("val1"));
    }

    @Test
    public void testConvertCustomPropertiesFull() throws Exception {
        Map<String, Set<String>> properties = convertCustomProperties(fullRecord);
        assertThat(properties.keySet(), Matchers.containsInAnyOrder("A", "B"));
        assertThat(properties.get("A"), Matchers.containsInAnyOrder("A1", "A2", "A3"));
        assertThat(properties.get("B"), Matchers.containsInAnyOrder("B1", "B2"));
    }

    @Test
    public void testConvertCustomPropertiesByEmpty() throws Exception {
        Map<Integer, ConvertRecord.PropertyWithValue> properties = convertCustomPropertiesById(emptyRecord);
        assertThat(properties.keySet().size(), is(0));
        assertThat(properties.values().size(), is(0));
    }

    @Test
    public void testConvertCustomPropertiesByIdSmallestPossible() throws Exception {
        Map<Integer, ConvertRecord.PropertyWithValue> properties = convertCustomPropertiesById(smallestPossibleRecord);
        assertThat(properties.keySet(), Matchers.containsInAnyOrder(1));
        assertThat(properties.get(1).getProperty(), is("prop1"));
        assertThat(properties.get(1).getValue(), is("val1"));
    }

    @Test
    public void testConvertCustomPropertiesByIdFull() throws Exception {
        Map<Integer, ConvertRecord.PropertyWithValue> properties = convertCustomPropertiesById(fullRecord);
        assertThat(properties.keySet(), Matchers.containsInAnyOrder(1, 2, 3, 4, 5));
        assertThat(properties.get(1).getProperty(), is("A"));
        assertThat(properties.get(1).getValue(), is("A1"));
        assertThat(properties.get(2).getProperty(), is("A"));
        assertThat(properties.get(2).getValue(), is("A2"));
        assertThat(properties.get(3).getProperty(), is("A"));
        assertThat(properties.get(3).getValue(), is("A3"));
        assertThat(properties.get(4).getProperty(), is("B"));
        assertThat(properties.get(4).getValue(), is("B1"));
        assertThat(properties.get(5).getProperty(), is("B"));
        assertThat(properties.get(5).getValue(), is("B2"));
    }

    @Test
    public void testFillTodoCustomPropertyInfoEmpty() throws Exception {
        List<ConvertRecord.PropertyWithValueAndId> customProperties = new ArrayList<>();
        SetMultimap<String, Integer> obligCustomPropertyMap = HashMultimap.create();
        fillTodoCustomPropertyInfo(Collections.EMPTY_LIST, customProperties, obligCustomPropertyMap);
        assertThat(customProperties.size(), is(0));
        assertThat(obligCustomPropertyMap.size(), is(0));
    }

    @Test
    public void testFillTodoCustomPropertyInfoFull() throws Exception {
        List<ConvertRecord.PropertyWithValueAndId> customProperties = new ArrayList<>();
        SetMultimap<String, Integer> obligCustomPropertyMap = HashMultimap.create();
        fillTodoCustomPropertyInfo(obligations, customProperties, obligCustomPropertyMap);
        assertThat(customProperties.size(), is(3));
        assertThat(obligCustomPropertyMap.size(), is(3));
        int id = obligCustomPropertyMap.get("1").stream().findFirst().get();
        assertThat(customProperties.get(id).getProperty(), is("A"));
        assertThat(customProperties.get(id).getValue(), is("A1"));
    }

    @Test
    public void testConvertTodosEmpty() throws Exception {
        List<Obligation> obligations = convertTodos(emptyRecord);
        assertThat(obligations.size(), is(0));
    }

    @Test
    public void testConvertTodosFull() throws Exception {
        List<Obligation> obligations = convertTodos(fullTodoRecord);
        assertThat(obligations.size(), is(2));
        assertThat(obligations.get(0).getText(), is("text1"));
        assertThat(obligations.get(0).isDevelopment(), is(true));
        assertThat(obligations.get(0).isDistribution(), is(true));
        assertThat(obligations.get(1).getText(), is("text2"));
        assertThat(obligations.get(1).isDevelopment(), is(false));
        assertThat(obligations.get(1).isDistribution(), is(false));
        assertThat(obligations.get(1).getExternalIds().size(), is(1));
        assertThat(obligations.get(1).getExternalIds().get("key"), is("value"));
    }


}
