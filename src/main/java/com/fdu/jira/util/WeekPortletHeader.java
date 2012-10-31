package com.fdu.jira.util;

import java.util.Date;
import java.util.Calendar;

import com.fdu.jira.util.Holidays;

public class WeekPortletHeader {
	 private Calendar weekDayDate;   	 
	 private String weekDayCSS;
	 
	 public WeekPortletHeader(Calendar date) {
		 weekDayDate = date;
	 }
	 
	 public Date getWeekDayDate() {
		 return weekDayDate.getTime();
	 }
	 
	 public String getWeekDayCSS() {
		 return weekDayCSS;
	 }
	 
	 public void setWeekDayCSS(String aWeekDayCSS) {
		 this.weekDayCSS = aWeekDayCSS;
	 }
	 
	 public boolean isBusinessDay() {
		 return !isNonBusinessDay();
	 }
 
	 public boolean isNonBusinessDay() {
	     int dayOfWeek = weekDayDate.get(Calendar.DAY_OF_WEEK);
		 return (dayOfWeek == 7 || dayOfWeek == 1);
	 }

	 public boolean isHoliday() {
	     return getHolidayName() !=  null;
	 }
	 
	 public String getHolidayName() {
	     return Holidays.getHolidays().get(weekDayDate.getTime());
	 }

    public boolean equalsToDate(Date other) {
	     Calendar otherDate = Calendar.getInstance(weekDayDate.getTimeZone());
	     otherDate.setTime(other);
             return weekDayDate.get(Calendar.DATE) == otherDate.get(Calendar.DATE) &&
                     weekDayDate.get(Calendar.MONTH) == otherDate.get(Calendar.MONTH) &&
                     weekDayDate.get(Calendar.YEAR) == otherDate.get(Calendar.YEAR);        
	 }

}
	

