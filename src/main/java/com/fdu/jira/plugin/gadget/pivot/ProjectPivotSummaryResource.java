package com.fdu.jira.plugin.gadget.pivot;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.apache.velocity.exception.VelocityException;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.filter.SearchRequestService;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.util.velocity.DefaultVelocityRequestContextFactory;
import com.atlassian.jira.util.velocity.VelocityRequestContext;
import com.atlassian.jira.web.FieldVisibilityManager;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.upm.license.storage.lib.ThirdPartyPluginLicenseStorageManager;
import com.atlassian.velocity.VelocityManager;
import com.fdu.jira.plugin.gadget.timesheet.TimeSheetRepresentation;
import com.fdu.jira.util.CalendarUtil;
import com.fdu.jira.util.LicenseUtil;
import com.fdu.jira.util.TextUtil;
import com.opensymphony.util.TextUtils;

/**
 * Generate a summary of worked hours for all or specified project.
 */
@Path ("/project-pivot-summary")
public class ProjectPivotSummaryResource {
    private static final Logger log = Logger.getLogger(ProjectPivotSummaryResource.class);

    // References to managers required for this gadget
    private WorklogManager worklogManager;
    private IssueManager issueManager;
    private JiraAuthenticationContext authenticationContext;
    private PermissionManager permissionManager;
    private SearchProvider searchProvider;
    private FieldVisibilityManager fieldVisibilityManager;
    private ApplicationProperties applicationProperties;
    private ProjectManager projectManager;
    private UserUtil userUtil;
    private SearchRequestService searchRequestService;
    private DateTimeFormatterFactory dateTimeFormatterFactory;
    private TimeZoneManager timeZoneManager;

    private final ThirdPartyPluginLicenseStorageManager licenseManager;

    public ProjectPivotSummaryResource(JiraAuthenticationContext authenticationContext,
            PermissionManager permissionManager,
            ApplicationProperties applicationProperties,
            DateTimeFormatterFactory dateTimeFormatterFactory,
            WorklogManager worklogManager,
            IssueManager issueManager,
            SearchProvider searchProvider,
            FieldVisibilityManager fieldVisibilityManager,
            ProjectManager projectManager,
            UserUtil userUtil,
            SearchRequestService searchRequestService,
            TimeZoneManager timeZoneManager,
            ThirdPartyPluginLicenseStorageManager licenseManager) {
        this.authenticationContext = authenticationContext;
        this.permissionManager = permissionManager;
        this.applicationProperties = applicationProperties;
        this.dateTimeFormatterFactory = dateTimeFormatterFactory;
        this.worklogManager = worklogManager;
        this.issueManager = issueManager;
        this.searchProvider = searchProvider;
        this.fieldVisibilityManager = fieldVisibilityManager;
        this.projectManager = projectManager;
        this.userUtil = userUtil;
        this.searchRequestService = searchRequestService;
        this.timeZoneManager = timeZoneManager;
        this.licenseManager = licenseManager;
    }

    @GET
    @AnonymousAllowed
    @Produces ({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getSummary(@Context HttpServletRequest request,
            @QueryParam("numOfWeeks") int numOfWeeks,
            @QueryParam ("reportingDay") int reportingDay, @QueryParam ("projectOrFilter") String projectOrFilter,
            @QueryParam ("targetGroup") String targetGroupName)
    {
        VelocityManager vm = ComponentManager.getInstance().getVelocityManager();
        Long projectId = null, filterId = null;
        String projectOrFilterName = null;
        if (projectOrFilter != null && projectOrFilter.indexOf("-")  != -1) {
            int i = projectOrFilter.indexOf("-");
            String type = projectOrFilter.substring(0, i);
            Long id = Long.parseLong(projectOrFilter.substring(i + 1));
            if ("project".equals(type)) {
                projectId = id;
                Project project = projectManager.getProjectObj(projectId); 
                projectOrFilterName = project.getName();
            } else if ("filter".equals(type)) {
                filterId = id;
                User user = authenticationContext.getLoggedInUser();
                JiraServiceContext jiraServiceContext = new JiraServiceContextImpl(user);
                SearchRequest filter = searchRequestService.getFilter(jiraServiceContext, filterId);
                projectOrFilterName = filter.getName();
            } else {
                log.error("Unsupported type of projectOrFilter argument: " + type);
            }
        }
        try
        {
            
            return Response.ok(new TimeSheetRepresentation(vm.getBody("templates/pivotgadget/",
                    "project-pivot-summary.vm", getVelocityParams(request, numOfWeeks,
                            reportingDay, projectId, filterId,
                            targetGroupName)),
                    projectOrFilterName)).cacheControl(getNoCacheControl()).build();
        }
        catch (VelocityException e)
        {
            e.printStackTrace();
            return Response.serverError().build();
        }
    }


    // Pass the data required for the portlet display to the view template
    protected Map<String,Object> getVelocityParams(HttpServletRequest request, int numOfWeeks,
            int reportingDay, Long projectId, Long filterId, String targetGroup) {
        Map<String, Object> params = getVelocityParams(numOfWeeks, reportingDay, projectId, filterId, targetGroup);
        params.put("i18n", authenticationContext.getI18nHelper());
        params.put("textutils", new TextUtils());
        params.put("req", request);
        final VelocityRequestContext velocityRequestContext =
            new DefaultVelocityRequestContextFactory(applicationProperties).getJiraVelocityRequestContext();
        params.put("baseurl", velocityRequestContext.getBaseUrl());
        params.put("requestContext", velocityRequestContext);
        return params;
    }

    protected Map<String, Object> getVelocityParams(int numOfWeeks, int reportingDay, Long projectId,
            Long filterId, String targetGroup) {
        Map<String, Object> params = new HashMap<String, Object>();
        final User user = authenticationContext.getLoggedInUser();
        TimeZone timezone = timeZoneManager.getLoggedInUserTimeZone();

        params.put("loggedin", user != null);
        params.put("license", LicenseUtil.getStatus(licenseManager));

        if (user == null) /* anonymous access */ {
            return params;
        }
        
        final I18nBean i18nBean = new I18nBean(user);

        final Calendar[] dates = CalendarUtil.getDatesRange(reportingDay, numOfWeeks, timezone);
        final Calendar startDate = dates[0], endDate = dates[1];

        try {
            // get time spents
            com.fdu.jira.plugin.report.pivot.Pivot pivot = new com.fdu.jira.plugin.report.pivot.Pivot(
                    authenticationContext,
                    dateTimeFormatterFactory,
                    permissionManager,
                    worklogManager,
                    issueManager,
                    searchProvider,
                    fieldVisibilityManager,
                    userUtil,
                    searchRequestService,
                    timeZoneManager,
                    licenseManager);

            pivot.getTimeSpents(user, startDate.getTime(), endDate.getTime(),
                    projectId, filterId,
                            targetGroup, /* excelView */ false, /* showIssues */ true);

            // Pass the issues to the velocity template
            params.put("filter", pivot.filter);
            if (projectId != null) {
                Project project = projectManager.getProjectObj(projectId);
                params.put("project", project);
            }
            String dpStartDate = CalendarUtil.toDatePickerString(startDate, dateTimeFormatterFactory, authenticationContext);
            params.put("startDate", dpStartDate);
            endDate.add(Calendar.DAY_OF_YEAR, -1); // timeshet report will add 1 day
            String dpEndDate = CalendarUtil.toDatePickerString(endDate, dateTimeFormatterFactory, authenticationContext);
            params.put("endDate", dpEndDate);
            params.put("outlookDate", dateTimeFormatterFactory.
                    formatter().withStyle(DateTimeStyle.DATE).forLoggedInUser());
            params.put("fieldVisibility", fieldVisibilityManager);
            params.put("textUtil", new TextUtil(i18nBean, timezone));
            params.put("showIssues", true);
            params.put("workedIssues", pivot.workedIssues);
            params.put("workedUsers", pivot.workedUsers);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return params;

    }

    private CacheControl getNoCacheControl() {
        CacheControl noCache = new CacheControl();
        noCache.setNoCache(true);
        return noCache;
    }
}
