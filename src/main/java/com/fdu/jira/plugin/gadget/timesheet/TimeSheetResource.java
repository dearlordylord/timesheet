package com.fdu.jira.plugin.gadget.timesheet;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.bc.filter.SearchRequestService;
import com.atlassian.jira.bc.issue.util.VisibilityValidator;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.worklog.WorklogManager;
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
import com.fdu.jira.util.CalendarUtil;
import com.fdu.jira.util.LicenseUtil;
import com.fdu.jira.util.ServletUtil;
import com.fdu.jira.util.TextUtil;
import com.opensymphony.util.TextUtils;
import org.apache.velocity.exception.VelocityException;

import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/timesheet")
public class TimeSheetResource {

    // References to managers required for this portlet
    private WorklogManager worklogManager;
    private IssueManager issueManager;
    private VisibilityValidator visibilityValidator;
    private JiraAuthenticationContext authenticationContext;
    private PermissionManager permissionManager;
    private ApplicationProperties applicationProperties;
    private SearchProvider searchProvider;
    private FieldVisibilityManager fieldVisibilityManager;
    private SearchRequestService searchRequestService;
    private UserUtil userUtil;
    private DateTimeFormatterFactory dateTimeFormatterFactory;
    private TimeZoneManager timeZoneManager;
    private ThirdPartyPluginLicenseStorageManager licenseManager;

    public TimeSheetResource(JiraAuthenticationContext authenticationContext,
            PermissionManager permissionManager,
            ApplicationProperties applicationProperties,
            DateTimeFormatterFactory dateTimeFormatterFactory,
            WorklogManager worklogManager,
            IssueManager issueManager,
            SearchProvider searchProvider,
            VisibilityValidator visibilityValidator,
            FieldVisibilityManager fieldVisibilityManager,
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
        this.visibilityValidator = visibilityValidator;
        this.searchProvider = searchProvider;
        this.fieldVisibilityManager = fieldVisibilityManager;
        this.userUtil = userUtil;
        this.searchRequestService = searchRequestService;
        this.timeZoneManager = timeZoneManager;
        this.licenseManager = licenseManager;
    }

    @GET
    @AnonymousAllowed
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getTimesheet(@Context HttpServletRequest request, @QueryParam("targetUser") String targetUserName)
    {
        // Retrieve the number of minute to add for a Low Worklog
        int numOfWeeks = ServletUtil.getIntParam(request, "numOfWeeks", 1);
        // Retrieve the week day that is to be a first day
        int reportingDay = ServletUtil.getIntParam(request, "reportingDay", Calendar.MONDAY);

        User targetUser = authenticationContext.getLoggedInUser();
        if (targetUserName != null && targetUserName.length() != 0) {
             targetUser = userUtil.getUserObject(targetUserName);
        }

        VelocityManager vm = ComponentManager.getInstance().getVelocityManager();
        try
        {
            return Response.ok(new TimeSheetRepresentation(vm.getBody("templates/timesheetportlet/", "timesheet-portlet.vm",
                    getVelocityParams(request, numOfWeeks, reportingDay, targetUser)),
                    /* projectOrFilterName */ null)).cacheControl(getNoCacheControl()).build();
        }
        catch (VelocityException e)
        {
            e.printStackTrace();
            return Response.serverError().build();
        }
    }

    private Map<String, Object> getVelocityParams(HttpServletRequest request, int numOfWeeks, int reportingDay, User targetUser) {
        Map<String, Object> params = getVelocityParams(numOfWeeks, reportingDay, targetUser);
        params.put("i18n", authenticationContext.getI18nHelper());
        params.put("textutils", new TextUtils());
        params.put("req", request);
        final VelocityRequestContext velocityRequestContext = new DefaultVelocityRequestContextFactory(applicationProperties).getJiraVelocityRequestContext();
        params.put("baseurl", velocityRequestContext.getBaseUrl());
        params.put("requestContext", velocityRequestContext);
        return params;
    }

    // Pass the data required for the portlet display to the view template
    private Map<String, Object> getVelocityParams(int numOfWeeks, int reportingDay, User targetUser) {
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
            params.put("targetUser", targetUser);

            // get time spents
            com.fdu.jira.plugin.report.timesheet.TimeSheet ts = new com.fdu.jira.plugin.report.timesheet.TimeSheet(
                dateTimeFormatterFactory,
                applicationProperties,
                permissionManager,
                worklogManager,
                issueManager,
                searchProvider,
                visibilityValidator,
                fieldVisibilityManager,
                userUtil,
                searchRequestService,
                authenticationContext,
                timeZoneManager,
                licenseManager);

            ts.getTimeSpents(user, startDate.getTime(), endDate.getTime(),
                    targetUser.getName(), false, null, new String[] {}, null, null, null, true, null);

            // pass parameters
            params.put("weekDays", ts.getWeekDays());
            params.put("weekWorkLog", ts.getWeekWorkLogShort());
            params.put("detailedWeekWorkLog", ts.getWeekWorkLog());
            params.put("weekTotalTimeSpents", ts.getWeekTotalTimeSpents());
            params.put("fieldVisibility", fieldVisibilityManager);
            String dpStartDate = CalendarUtil.toDatePickerString(startDate, dateTimeFormatterFactory, authenticationContext);
            params.put("startDate", dpStartDate);
            endDate.add(Calendar.DAY_OF_YEAR, -1); // timeshet report will add 1 day
            String dpEndDate = CalendarUtil.toDatePickerString(endDate, dateTimeFormatterFactory, authenticationContext);
            params.put("endDate", dpEndDate);
            params.put("textUtil", new TextUtil(i18nBean, timezone));
            params.put("outlookDate", dateTimeFormatterFactory.
                    formatter().withStyle(DateTimeStyle.DATE).forLoggedInUser().withZone(timezone));
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
