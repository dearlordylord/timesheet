/*
 * Copyright (c) 2002-2004
 * All rights reserved.
 */
package com.fdu.jira.plugin.report.pivot;

import com.atlassian.core.ofbiz.CoreFactory;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.filter.SearchRequestService;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.comparator.IssueKeyComparator;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverter;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverterImpl;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.plugin.report.impl.AbstractReport;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.util.DateFieldFormat;
import com.atlassian.jira.util.DateFieldFormatImpl;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.jira.web.FieldVisibilityManager;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.upm.license.storage.lib.ThirdPartyPluginLicenseStorageManager;
import com.fdu.jira.util.LicenseUtil;
import com.fdu.jira.util.MyFullNameComparator;
import com.fdu.jira.util.MyUser;
import com.fdu.jira.util.TextUtil;
import com.fdu.jira.util.WorklogUtil;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.EntityExpr;
import org.ofbiz.core.entity.EntityOperator;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.ofbiz.core.util.UtilMisc;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generate a summary of worked hours in a specified period. The time period is
 * divided by the specified value for display.
 */
public class Pivot extends AbstractReport {
    private static final Logger log = Logger.getLogger(Pivot.class);

    private final PermissionManager permissionManager;
    private WorklogManager worklogManager;
    private IssueManager issueManager;

    private final JiraAuthenticationContext authenticationContext;

    private Map<Issue, List<Worklog>> allWorkLogs =
        new Hashtable<Issue, List<Worklog>>();

    public Map<Project, Map<MyUser, Long>> workedProjects =
        new TreeMap<Project, Map<MyUser, Long>>(new Comparator<Project>()
        {
            public int compare(Project p1, Project p2)
                {
                        return p1.getKey().compareTo(p2.getKey());
                }
        }
    );

    public Map<Issue, Map<MyUser, Long>> workedIssues =
        new TreeMap<Issue, Map<MyUser, Long>>(new IssueKeyComparator());

    public Map<MyUser, Long> workedUsers =
        new TreeMap<MyUser, Long>(new MyFullNameComparator<MyUser>());
    
    public SearchRequest filter = null;

    private SearchProvider searchProvider;

    private FieldVisibilityManager fieldVisibilityManager;

    private UserUtil userUtil;

    private SearchRequestService searchRequestService;

    private DateTimeFormatterFactory dateTimeFormatterFactory;

    private TimeZoneManager timeZoneManager;

    private final ThirdPartyPluginLicenseStorageManager licenseManager;

    private int maxPeriod = 62; // 2 months in days

    public Pivot(JiraAuthenticationContext authenticationContext,
            DateTimeFormatterFactory dateTimeFormatterFactory,
            PermissionManager permissionManager,
            WorklogManager worklogManager,
            IssueManager issueManager,
            SearchProvider searchProvider,
            FieldVisibilityManager fieldVisibilityManager,
            UserUtil userUtil,
            SearchRequestService searchRequestService,
            TimeZoneManager timeZoneManager,
            ThirdPartyPluginLicenseStorageManager licenseManager) {
        this.authenticationContext = authenticationContext;
        this.dateTimeFormatterFactory = dateTimeFormatterFactory;
        this.permissionManager = permissionManager;
        this.worklogManager = worklogManager;
        this.issueManager = issueManager;
        this.searchProvider = searchProvider;
        this.fieldVisibilityManager = fieldVisibilityManager;
        this.userUtil = userUtil;
        this.searchRequestService = searchRequestService;
        this.timeZoneManager = timeZoneManager;
        this.licenseManager = licenseManager;

        log.warn("creating pivot...");


        // maxPeriod limit
        ApplicationProperties ap = ComponentManager.getComponentInstanceOfType(ApplicationProperties.class);
        String maxPeriodInDays = ap.getDefaultBackedString("jira.timesheet.plugin.maxPeriodInDays");
        if (maxPeriodInDays != null) {
            try {
                maxPeriod = Integer.valueOf(maxPeriodInDays);
            } catch (NumberFormatException e) {
                log.warn("Invalid maxPeriodInDays number: " + maxPeriodInDays);
            }
        }
    }

    public boolean isExcelViewSupported() {
        return true;
    }

    // Retrieve time user has spent for period
    public void getTimeSpents(User user2, Date startDate, Date endDate,
        Long projectId, Long filterId, String targetGroup, boolean excelView,
        boolean showIssues) throws SearchException,
        GenericEntityException {

        log.warn("get time spents : " + Arrays.toString(new String[] {targetGroup}));
    	
        JiraServiceContext jiraServiceContext = new JiraServiceContextImpl(user2);

        Set<Long> filteredIssues = new TreeSet<Long>();
        if (filterId != null) {
            log.info("Using filter: " + filterId);
            filter = searchRequestService.getFilter(jiraServiceContext, filterId);
            if (filter != null) { // not logged in
                SearchResults issues = searchProvider.search(filter.getQuery(), user2, PagerFilter.getUnlimitedFilter());
                for (Iterator<Issue> i = issues.getIssues().iterator(); i.hasNext();) {
	                Issue value = i.next();
	                filteredIssues.add(value.getId());
                }
            } else {
                return;
            }
        }
    	
        EntityExpr startExpr = new EntityExpr("startdate",
                EntityOperator.GREATER_THAN_EQUAL_TO, new Timestamp(
                        startDate.getTime()));
        EntityExpr endExpr = new EntityExpr("startdate",
                EntityOperator.LESS_THAN, new Timestamp(endDate.getTime()));
        List<EntityExpr> exprs = UtilMisc.toList(startExpr, endExpr);
        Set<String> assigneeIds = null;
        if (targetGroup != null && targetGroup.length() > 0) {
            Set<User> users = userUtil.getAllUsersInGroupNames(
                    Arrays.asList(targetGroup));
            if (assigneeIds == null) assigneeIds = new TreeSet<String>();
            for (User user : users) {
                assigneeIds.add(user.getName());
            }
            log.info("Searching worklogs created since '" + startDate
                    + "', till '" + endDate + "', by group '" + targetGroup + "'");
        } else {
            log.info("Searching worklogs created since '" + startDate
                    + "', till '" + endDate + "'");
        }

        List<GenericValue> worklogs = CoreFactory.getGenericDelegator().findByAnd(
                "Worklog", exprs);

        log.info("Query returned : " + worklogs.size() + " worklogs");
        for (Iterator<GenericValue> worklogsIterator = worklogs.iterator(); worklogsIterator
                .hasNext();) {
            GenericValue genericWorklog =  worklogsIterator.next();
            //Worklog worklog = new Worklog(genericWorklog, remoteUser);
            Worklog worklog = WorklogUtil.convertToWorklog(genericWorklog, worklogManager, issueManager);
            Issue issue = issueManager.getIssueObject(
                    genericWorklog.getLong("issue"));

            if (issue != null 
            		&& (projectId == null || projectId.equals(issue.getLong("project")))
            		&& (filterId == null || filteredIssues.contains(issue.getId()))
            		&& (assigneeIds == null || assigneeIds.contains(worklog.getAuthor()))) {
                if (permissionManager.hasPermission(Permissions.BROWSE,
                        issue, user2)) {
                    if (excelView && showIssues) {
                        // excel view shows complete work log
                        List<Worklog> issueWorklogs = allWorkLogs.get(issue);
                        if (issueWorklogs == null) {
                            issueWorklogs = new ArrayList<Worklog>();
                            allWorkLogs.put(issue, issueWorklogs);
                        }
                        issueWorklogs.add(worklog);
                    } else {
                        Map<MyUser, Long> userWorkLog;

                        if (showIssues) {
                            // shows summary hours per issue for each user
                            userWorkLog = workedIssues.get(issue);
                            if (userWorkLog == null) {
                                userWorkLog = new Hashtable<MyUser, Long>();
                                workedIssues.put(issue, userWorkLog);
                            }
                        } else {
                            // shows summary hours per project for each user
                            Project project = issue.getProjectObject();
                            userWorkLog = workedProjects.get(project);
                            if (userWorkLog == null) {
                                userWorkLog = new Hashtable<MyUser, Long>();
                                workedProjects.put(project, userWorkLog);
                            }
                        }

                        // user per issue
                        MyUser user;
                        if (worklog.getAuthor() != null) {
                            User osuser = userUtil.getUserObject(worklog
                                .getAuthor());
                            if (osuser != null) {
                            	user = new MyUser(osuser.getName(), osuser.getDisplayName());
                            } else {
                            	// TIME-221: user may have been deleted
                            	user = new MyUser("deleted", "deleted");
                            }
                        } else {
                            user = new MyUser("anonymous", "anonymous");
                        }
                        long timespent = worklog.getTimeSpent();
                        
                        Long worked = userWorkLog.get(user);
                        if (worked != null) {
                            timespent += worked;
                        }

                        userWorkLog.put(user, timespent);

                        // user total
                        timespent = worklog.getTimeSpent();
                        worked = workedUsers.get(user);
                        if (worked != null) {
                            timespent += worked;
                        }
                        workedUsers.put(user, timespent);
                    }
                }
            }
        }
    }

    // Generate the report
    public String generateReport(ProjectActionSupport action, Map params,
        boolean excelView) throws Exception {
        log.warn("params : " + params);
        User remoteUser = authenticationContext.getLoggedInUser();
        I18nBean i18nBean = new I18nBean(remoteUser);
        TimeZone timezone = timeZoneManager.getLoggedInUserTimeZone();

        // Retrieve the project parameter
        Long projectId = ParameterUtils.getLongParam(params, "projectid");
        //      Retrieve the filter parameter
        Long filterId = ParameterUtils.getLongParam(params, "filterid");
        // Retrieve the start and end dates and the time interval specified by
        // the user
        Date endDate = getEndDate(params, i18nBean, timezone);
        Date startDate = getStartDate(params, i18nBean, endDate, timezone);
        String targetGroup = ParameterUtils.getStringParam(params, "targetGroup");
        Boolean showIssues = ParameterUtils.getBooleanParam(params, "showIssues");

        // get time spents
        getTimeSpents(remoteUser, startDate, endDate, projectId, filterId, targetGroup, excelView, showIssues);

        // Pass the issues to the velocity template
        DateFieldFormat  dateFieldFormat = new DateFieldFormatImpl(dateTimeFormatterFactory);
        DatePickerConverter dpc = new DatePickerConverterImpl(authenticationContext, dateFieldFormat);
        Map<String, Object> velocityParams = new HashMap<String, Object>();
        velocityParams.put("startDate", dpc.getString(startDate));
        Calendar calendarDate = Calendar.getInstance();
        calendarDate.setTime(endDate);
        // timesheet report will add 1 day
        calendarDate.add(Calendar.DAY_OF_YEAR, -1); 
        velocityParams.put("endDate", dpc.getString(calendarDate.getTime()));
        velocityParams.put("outlookDate", dateTimeFormatterFactory.
                formatter().withStyle(DateTimeStyle.DATE).forLoggedInUser());
        velocityParams.put("fieldVisibility", fieldVisibilityManager);
        velocityParams.put("textUtil", new TextUtil(i18nBean, timezone));
        velocityParams.put("license", LicenseUtil.getStatus(licenseManager));
        if (excelView && showIssues) {
            velocityParams.put("allWorkLogs", allWorkLogs);
        } else {
            velocityParams.put("showIssues", showIssues);
            velocityParams.put("workedIssues", showIssues ? workedIssues : workedProjects);
            velocityParams.put("workedUsers", workedUsers);
        }
        velocityParams.put("projectId", projectId);
        velocityParams.put("filterId", filterId);
        velocityParams.put("targetGroup", targetGroup);

        return descriptor.getHtml(excelView && showIssues ? "excel" : "view", velocityParams);
    }

    // Generate html report
    public String generateReportHtml(ProjectActionSupport action, Map params)
        throws Exception {
        return generateReport(action, params, false);
    }

    // Generate excel, report
    public String generateReportExcel(ProjectActionSupport action, Map params)
        throws Exception {
        return generateReport(action, params, true);
    }

    // Validate the parameters set by the user.
    public void validate(ProjectActionSupport action, Map params) {
        User remoteUser = authenticationContext.getLoggedInUser();
        I18nHelper i18nBean = new I18nBean(remoteUser);

        Date startDate = ParameterUtils.getDateParam(params, "startDate",
                i18nBean.getLocale());
        Date endDate = ParameterUtils.getDateParam(params, "endDate",
                i18nBean.getLocale());

        if (startDate == null || endDate == null) {
            return; // nothing to validate
        }

        // The end date must be after the start date
        if (endDate.before(startDate)) {
            action.addError("endDate", action
                    .getText("report.pivot.before.startdate"));
        }

        // maxPeriod

        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        c.add(Calendar.DAY_OF_MONTH, maxPeriod);

        // The end date must be after the start date
        if (c.getTime().before(endDate)) {
            action.addError("endDate", action
                    .getText("report.pivot.maxperiod"));
        }
    }

    public static Date getEndDate(Map params, I18nBean i18nBean, TimeZone timezone) {
        Date endDate = ParameterUtils.getDateParam(params, "endDate",
                i18nBean.getLocale());
        // set endDate rigth after the date user has specified
        Calendar calendarDate = Calendar.getInstance(timezone);
        if (endDate != null) {
            // include the specified date
            calendarDate.setTime(endDate);
            calendarDate.add(Calendar.DAY_OF_YEAR, 1);
        }
        // round to midnight, do not include today
        calendarDate.set(Calendar.HOUR_OF_DAY, 0);
        calendarDate.set(Calendar.MINUTE, 0);
        calendarDate.set(Calendar.SECOND, 0);
        calendarDate.set(Calendar.MILLISECOND, 0);
        return calendarDate.getTime();
    }

    public static Date getStartDate(Map params, I18nBean i18nBean,
        Date endDate, TimeZone timezone) {
        Date startDate = ParameterUtils.getDateParam(params, "startDate",
                i18nBean.getLocale());
        Calendar calendarDate = Calendar.getInstance(timezone);
        // set startDate a wee before
        if (startDate == null) {
            calendarDate.setTime(endDate);
            calendarDate.add(Calendar.WEEK_OF_YEAR, -1);
        } else {
            calendarDate.setTime(startDate);
        }
        // round to midnight, do not include today
        calendarDate.set(Calendar.HOUR_OF_DAY, 0);
        calendarDate.set(Calendar.MINUTE, 0);
        calendarDate.set(Calendar.SECOND, 0);
        calendarDate.set(Calendar.MILLISECOND, 0);
        return calendarDate.getTime();
    }
}
