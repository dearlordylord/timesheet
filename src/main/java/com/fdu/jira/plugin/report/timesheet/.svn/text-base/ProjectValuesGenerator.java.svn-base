package com.fdu.jira.plugin.report.timesheet;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.fdu.jira.util.TextUtil;

public class ProjectValuesGenerator implements ValuesGenerator {

    public Map<String, String> getValues(Map arg0) {
        ProjectManager projectManager = 
            ComponentManager.getComponentInstanceOfType(ProjectManager.class);
        Collection<Project> projects = projectManager.getProjectObjects();
        PermissionManager permissionManager = 
            ComponentManager.getComponentInstanceOfType(PermissionManager.class);
        JiraAuthenticationContext authenticationContext =
            ComponentManager.getComponentInstanceOfType(JiraAuthenticationContext.class);
        User remoteUser = authenticationContext.getLoggedInUser();
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("", "All Projects");
        for (Project project : projects) {
            if (permissionManager.hasPermission(Permissions.BROWSE, project,
                    remoteUser)) {
                result.put(project.getId().toString(),  TextUtil.getUnquotedString(project.getName()));
            }
        }
        return result;
    }
}

