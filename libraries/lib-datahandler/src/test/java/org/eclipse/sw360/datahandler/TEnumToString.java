/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler;

import org.eclipse.sw360.datahandler.common.ThriftEnumUtils;
import com.tngtech.jgiven.annotation.Format;
import com.tngtech.jgiven.format.ArgumentFormatter;
import org.apache.thrift.TEnum;

import java.lang.annotation.*;
/**
 * @author daniele.fognini@tngtech.com
 */
@Documented
@Format(value = TEnumToString.EnumFormatter.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface TEnumToString {

    class EnumFormatter implements ArgumentFormatter<TEnum> {

        @Override
        public String format(TEnum o, String... args) {
            return "\"" + ThriftEnumUtils.enumToString(o) + "\"";
        }
    }
}
