package com.fdu.jira.plugin.report.timesheet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.SearchableField;
import com.fdu.jira.util.TextUtil;

public class GroupByFieldValuesGenerator implements ValuesGenerator {
    private ApplicationProperties applicationProperties;

    public GroupByFieldValuesGenerator() {
        this.applicationProperties = ComponentManager.getInstance().getApplicationProperties();
    }

    public Map<String, String> getValues(Map arg0) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("", "");

        FieldManager fieldManager = 
            (FieldManager) ComponentManager.getComponentInstanceOfType(FieldManager.class);
        
        Set<SearchableField> fields = fieldManager.getAllSearchableFields();

        Set<Field> sortedFields = new TreeSet<Field>(new Comparator<Field>() {
            public int compare(Field o, Field other) {
                return o.getName().compareTo(other.getName());
            }
        });
        sortedFields.addAll(fields);


        String groupByFieldsP = applicationProperties.
                getDefaultString("jira.plugin.timesheet.groupbyfields");
        Collection<String> groupByFields = null;
        if (groupByFieldsP != null) {
            groupByFields = Arrays.asList(groupByFieldsP.split(","));
        }

        for (Iterator<Field> i = sortedFields.iterator(); i.hasNext();) {
            Field field = i.next();
            if (groupByFields == null ||
                    groupByFields.contains(field.getId()) ||
                    groupByFields.contains(field.getName())) {
                values.put(field.getId(), TextUtil.getUnquotedString(field.getName()));
            }
        }

        return values;
    }

   

}
