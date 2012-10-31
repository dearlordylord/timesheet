package com.fdu.jira.util;

import java.util.Calendar;
import java.util.TimeZone;

import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverter;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverterImpl;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.DateFieldFormat;
import com.atlassian.jira.util.DateFieldFormatImpl;

public final class CalendarUtil
{
    public static Calendar[] getDatesRange(int reportingDay, int numOfWeeks, TimeZone timezone) {
        // Calculate the start and end dates
        Calendar currentDate = Calendar.getInstance(timezone);
        Calendar endDate = Calendar.getInstance(timezone);

        if (reportingDay != 0 /* today */) {
            endDate.set(Calendar.DAY_OF_WEEK, reportingDay);
        } else {
            endDate.add(Calendar.DAY_OF_MONTH, 1); // include today
        }

        endDate.set(Calendar.HOUR_OF_DAY, 0);
        endDate.set(Calendar.MINUTE, 0);
        endDate.set(Calendar.SECOND, 0);
        endDate.set(Calendar.MILLISECOND, 0);

        if (endDate.before(currentDate)) {
            endDate.add(Calendar.WEEK_OF_MONTH, 1);
        }

        Calendar startDate = Calendar.getInstance(timezone);
        startDate.setTime(endDate.getTime());
        startDate.add(Calendar.WEEK_OF_YEAR, -numOfWeeks);

        return new Calendar[] { startDate, endDate };
    }

    public static String toDatePickerString(Calendar date,
            DateTimeFormatterFactory dateTimeFormatterFactory,
            JiraAuthenticationContext authenticationContext) {
        DateFieldFormat  dateFieldFormat = new DateFieldFormatImpl(dateTimeFormatterFactory);
        DatePickerConverter dpc = new DatePickerConverterImpl(authenticationContext, dateFieldFormat);
        Calendar calendarDate = Calendar.getInstance();  // defualt TZ!!!
        calendarDate.set(Calendar.YEAR, date.get(Calendar.YEAR));
        calendarDate.set(Calendar.MONTH, date.get(Calendar.MONTH));
        calendarDate.set(Calendar.DATE, date.get(Calendar.DATE));
        calendarDate.set(Calendar.HOUR_OF_DAY, 0);
        calendarDate.set(Calendar.MINUTE, 0);
        calendarDate.set(Calendar.SECOND, 0);
        calendarDate.set(Calendar.MILLISECOND, 0);
        return dpc.getString(calendarDate.getTime());
    }
    
}
