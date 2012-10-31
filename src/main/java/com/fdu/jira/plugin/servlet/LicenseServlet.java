package com.fdu.jira.plugin.servlet;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.upm.api.license.entity.PluginLicense;
import com.atlassian.upm.api.util.Option;
import com.atlassian.upm.license.storage.lib.AtlassianMarketplaceUriFactory;
import com.atlassian.upm.license.storage.lib.PluginLicenseStoragePluginUnresolvedException;
import com.atlassian.upm.license.storage.lib.ThirdPartyPluginLicenseStorageManager;

import org.apache.commons.lang.StringUtils;

/**
 * A license administration servlet that uses {@link ThirdPartyPluginLicenseStorageManager} to:
 *  - get the current plugin license,
 *  - update the plugin license,
 *  - remove the plugin license,
 *  - buy, try, upgrade, and renew your license directly from My Atlassian,
 *  - check for a licensing-aware UPM,
 *  - and properly handle if a licensing-aware UPM is detected.
 *
 * This servlet can be reached at http://localhost:2990/jira/plugins/servlet/com.fdu.jira.plugin.jira-timesheet-plugin/license
 */
public class LicenseServlet extends HttpServlet
{
    private static final String TEMPLATE = "license-admin.vm";

    private final ThirdPartyPluginLicenseStorageManager licenseManager;
    private final AtlassianMarketplaceUriFactory uriFactory;
    private final ApplicationProperties applicationProperties;
    private final TemplateRenderer renderer;
    private final LoginUriProvider loginUriProvider;
    private final UserManager userManager;
    private final I18nResolver i18nResolver;

    public LicenseServlet(ThirdPartyPluginLicenseStorageManager licenseManager,
                              AtlassianMarketplaceUriFactory uriFactory,
                              ApplicationProperties applicationProperties,
                              TemplateRenderer renderer,
                              LoginUriProvider loginUriProvider,
                              UserManager userManager,
                              I18nResolver i18nResolver)
    {
        this.licenseManager = licenseManager;
        this.uriFactory = uriFactory;
        this.applicationProperties = applicationProperties;
        this.renderer = renderer;
        this.loginUriProvider = loginUriProvider;
        this.userManager = userManager;
        this.i18nResolver = i18nResolver;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        if (userManager.getRemoteUsername() == null)
        {
            redirectToLogin(req, resp);
            return;
        } else if (!hasAdminPermission())
        {
            handleUnpermittedUser(req, resp);
            return;
        }

        final Map<String, Object> context = initVelocityContext(resp);
        addEligibleMarketplaceButtons(context);
        renderer.render(TEMPLATE, context, resp.getWriter());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        if (userManager.getRemoteUsername() == null)
        {
            redirectToLogin(req, resp);
            return;
        } else if (!hasAdminPermission())
        {
            handleUnpermittedUser(req, resp);
            return;
        }

        final Map<String, Object> context = initVelocityContext(resp);

        if (!context.containsKey("errorMessage"))
        {
            try
            {
                if (!licenseManager.isUpmLicensingAware())
                {
                    String license = req.getParameter("license");
                    Option<PluginLicense> validatedLicense = licenseManager.validateLicense(license);

                    //we have an empty/null license parameter - let's remove the stored license.
                    if (StringUtils.isEmpty(license))
                    {
                        licenseManager.removeRawLicense();
                        context.put("successMessage", i18nResolver.getText("plugin.license.storage.admin.license.remove"));
                        context.put("license", licenseManager.getLicense());
                    }
                    //we have a non-empty license parameter - let's update the license if it is valid.
                    else if (validatedLicense.isDefined())
                    {
                        licenseManager.setRawLicense(license);
                        if (validatedLicense.get().getError().isDefined())
                        {
                            context.put("warningMessage", i18nResolver.getText("plugin.license.storage.admin.license.update.invalid"));
                        }
                        else
                        {
                            context.put("successMessage", i18nResolver.getText("plugin.license.storage.admin.license.update"));
                        }
                        context.put("license", licenseManager.getLicense());
                    }
                    //we have an invalid license - do nothing.
                    else
                    {
                        context.put("errorMessage", i18nResolver.getText("plugin.license.storage.admin.license.invalid"));
                    }
                }
            }
            catch (PluginLicenseStoragePluginUnresolvedException e)
            {
                context.put("errorMessage", i18nResolver.getText("plugin.license.storage.admin.plugin.unavailable"));
                context.put("displayLicenseAdminUi", false);
            }
        }

        addEligibleMarketplaceButtons(context); //must be invoked *after* the license update has occurred.
        renderer.render(TEMPLATE, context, resp.getWriter());
    }

    private Map<String, Object> initVelocityContext(HttpServletResponse resp)
    {
        resp.setContentType("text/html;charset=utf-8");
        URI servletUri = URI.create(applicationProperties.getBaseUrl() + "/plugins/servlet/com.fdu.jira.plugin.jira-timesheet-plugin/license");

        final Map<String, Object> context = new HashMap<String, Object>();

        resp.setContentType("text/html;charset=utf-8");
        context.put("servletUri", servletUri);
        context.put("displayLicenseAdminUi", true);

        try
        {
            context.put("license", licenseManager.getLicense());
            context.put("upmLicensingAware", licenseManager.isUpmLicensingAware());
            context.put("pluginKey", licenseManager.getPluginKey());
            if (licenseManager.isUpmLicensingAware())
            {
                context.put("warningMessage", i18nResolver.getText("plugin.license.storage.admin.upm.licensing.aware",
                                                                   licenseManager.getPluginManagementUri()));
            }
        }
        catch (PluginLicenseStoragePluginUnresolvedException e)
        {
            context.put("errorMessage", i18nResolver.getText("plugin.license.storage.admin.plugin.unavailable"));
            context.put("displayLicenseAdminUi", false);
        }

        return context;
    }

    private void addEligibleMarketplaceButtons(Map<String, Object> context)
    {
        URI servletUri = URI.create(applicationProperties.getBaseUrl() + "/plugins/servlet/com.fdu.jira.plugin.jira-timesheet-plugin/license");

        try
        {
            boolean eligibleButtons = false;

            if (uriFactory.isPluginBuyable())
            {
                context.put("buyPluginUri", uriFactory.getBuyPluginUri(servletUri));
                eligibleButtons = true;
            }
            if (uriFactory.isPluginTryable())
            {
                context.put("tryPluginUri", uriFactory.getTryPluginUri(servletUri));
                eligibleButtons = true;
            }
            if (uriFactory.isPluginRenewable())
            {
                context.put("renewPluginUri", uriFactory.getRenewPluginUri(servletUri));
                eligibleButtons = true;
            }
            if (uriFactory.isPluginUpgradable())
            {
                context.put("upgradePluginUri", uriFactory.getUpgradePluginUri(servletUri));
                eligibleButtons = true;
            }

            context.put("eligibleButtons", eligibleButtons);
        }
        catch (PluginLicenseStoragePluginUnresolvedException e)
        {
            context.put("errorMessage", i18nResolver.getText("plugin.license.storage.admin.plugin.unavailable"));
            context.put("displayLicenseAdminUi", false);
        }
    }

    private boolean hasAdminPermission()
    {
        String user = userManager.getRemoteUsername();
        try
        {
            return user != null && (userManager.isAdmin(user) || userManager.isSystemAdmin(user));
        }
        catch(NoSuchMethodError e)
        {
            // userManager.isAdmin(String) was not added until SAL 2.1.
            // We need this check to ensure backwards compatibility with older product versions.
            return user != null && userManager.isSystemAdmin(user);
        }
    }

    private void redirectToLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        resp.sendRedirect(loginUriProvider.getLoginUri(URI.create(req.getRequestURL().toString())).toASCIIString());
    }


    private void handleUnpermittedUser(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        final Map<String, Object> context = new HashMap<String, Object>();
        context.put("errorMessage", i18nResolver.getText("plugin.license.storage.admin.unpermitted"));
        context.put("displayLicenseAdminUi", false);
        renderer.render(TEMPLATE, context, resp.getWriter());
    }
}
