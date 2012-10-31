/**
 * Copyright (c) 2010, Andriy Zhdanov,
 * azhdanov@gmail.com
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the Outsourcing Factory Inc. nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */
package com.fdu.jira.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.config.properties.ApplicationProperties;

/**
 * Load holidays from jira-config.properties,
 * where they must be specified basing on relevant government site manually, e.g.
 * E.g. jira/home/jira-config.properties:
 * <code>
 * jira.timesheet.plugin.holidays=\
 * 1/May/2012 - Labor Day,\
 * 9/May/2012 - Victory Day
 * 
 * or just
 * 
 * jira.timesheet.plugin.holidays=1/May/2012, 9/May/2012
 * 
 * for jira default locale and long date format, which are usually en_US and dd/MMM/YYYY.
 * 
 * </code>
 * Things to consider in future:
 * 0) Holidays for user viewing the report or holidays for user reported worked log?
 * 1) No holidays calendar in java - http://www.javaworld.com/javaworld/javatips/jw-javatip44.html
 * 2) holidaywebservice.com - for .net, or www.bank-holidays.com - no service api?
 * 3) http://www.codeproject.com/Articles/18261/Working-with-date-time-patterns
 * 4) http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/scheduling/support/CronSequenceGenerator.html
 */
public class Holidays {
    private static final Logger log = Logger.getLogger(Holidays.class);

    private static Map<Date, String> holidays;
    public static Map<Date, String> getHolidays() {
        if (holidays == null) {
            synchronized (Holidays.class) {
                if (holidays == null) {
                    holidays = new TreeMap<Date, String>();
                    ApplicationProperties ap = ComponentManager.getComponentInstanceOfType(ApplicationProperties.class);
                    String p = ap.getDefaultBackedString("jira.timesheet.plugin.holidays");
                    log.warn("There is no 'jira.timesheet.plugin.holidays' application property set");
                    if (p == null) {
                        return holidays;
                    }

                    Locale locale = Locale.getDefault();
                    String localeStr = ap.getDefaultBackedString("jira.i18n.default.locale");
                    if (localeStr != null) {
                        locale = new Locale(localeStr);
                    }
                    log.info("Using " + locale.getDisplayName() + " locale for parsing holidays");
                    
                    String formatStr = ap.getDefaultBackedString("jira.lf.date.dmy");
                    log.info("Using " + formatStr + " holidays date format");
                    DateFormat df = new SimpleDateFormat(formatStr, locale);
                    

                    String[] s = p.split(",");
                    for (int i = 0; i < s.length; i++) {
                        String[] t = s[i].split("-");
                        try {
                            Date date = df.parse(t[0].trim());
                            String name = t.length == 2 ? t[1].trim() : "Bank holiday";
                            holidays.put(date, name);
                        } catch (Exception e) {
                            log.error("Can't parse holiday: " + s[i], e);
                            continue;
                        }
                    }
                }
            }
        }
        return holidays;
    }

}
