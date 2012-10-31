package com.fdu.jira.plugin.report.timesheet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.atlassian.configurable.ValuesGenerator;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.util.UserManager;
import com.fdu.jira.util.TextUtil;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.crowd.embedded.api.User;

public class GroupValuesGenerator implements ValuesGenerator {

    public Map<String, String> getValues(Map params) {
        User u = (User) params.get("User");
        Map<String, String> values = new TreeMap<String, String>();
        values.put("", "");
        PermissionManager permissionManager = 
            (PermissionManager) ComponentManager.getComponentInstanceOfType(PermissionManager.class);

        if (permissionManager.
                hasPermission(Permissions.USER_PICKER, u)) {
            UserManager userManager = 
                (UserManager) ComponentManager.getComponentInstanceOfType(UserManager.class);
            Collection<Group> groups = userManager.getGroups();
            for (Iterator<Group> i = groups.iterator(); i.hasNext();) {
                Group group = i.next();
                values.put(TextUtil.getUnquotedString(group.getName()),
                        TextUtil.getUnquotedString(group.getName()));
            }
        }
        return values;
    }

}
