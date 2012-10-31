package com.fdu.jira.util;

import com.atlassian.jira.portal.SearchRequestValuesGenerator;

import java.util.Map;

public class OptionalSearchRequestValuesGenerator extends
		SearchRequestValuesGenerator {

	public Map<String, String> getValues(Map arg0) {
		Map<String, String> values = super.getValues(arg0);
		values.put("", "");
		return values;
	}

}
