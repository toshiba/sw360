/*
 * Copyright Siemens AG, 2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.common;

import java.util.concurrent.TimeUnit;

/**
 * @author daniele.fognini@tngtech.com
 */
public final class Duration {
    private final long duration;
    private final TimeUnit timeUnit;

    private Duration(long duration, TimeUnit timeUnit) {
        this.duration = duration;
        this.timeUnit = timeUnit;
    }

    public static Duration durationOf(long duration, TimeUnit timeUnit) {
        return new Duration(duration, timeUnit);
    }

    public long getDuration() {
        return duration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public long toMillis() {
        return timeUnit.toMillis(duration);
    }
}
