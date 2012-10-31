package com.fdu.jira.plugin.report.timesheet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.priority.Priority;

public class PrioritiesValuesGenerator implements ValuesGenerator {

    public Map<String, String> getValues(Map arg0) {
        Map<String, String> values = new TreeMap<String, String>();
        values.put("", "");
        ConstantsManager constantsManager =
            ComponentManager.getComponentInstanceOfType(ConstantsManager.class);
        Collection<Priority> priorities = constantsManager.getPriorityObjects();
        
        for (Iterator<Priority> i = priorities.iterator(); i.hasNext();) {
            Priority priority = i.next();
            values.put(priority.getId(), priority.getName());
        }
        return values;
    }

}
