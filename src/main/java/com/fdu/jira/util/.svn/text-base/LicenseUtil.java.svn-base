package com.fdu.jira.util;

import org.apache.log4j.Logger;

import com.atlassian.upm.api.license.entity.PluginLicense;
import com.atlassian.upm.license.storage.lib.PluginLicenseStoragePluginUnresolvedException;
import com.atlassian.upm.license.storage.lib.ThirdPartyPluginLicenseStorageManager;

public final class LicenseUtil {
    public static String getStatus(ThirdPartyPluginLicenseStorageManager licenseManager) {
        try {
            // Check and see if a license is currently stored.
            // This accessor method can be used whether or not a licensing-aware
            // UPM is present.
            if (licenseManager.getLicense().isDefined()) {
                PluginLicense pluginLicense = licenseManager.getLicense().get();
                // Check and see if the stored license has an error. If not, it
                // is currently valid.
                if (pluginLicense.getError().isDefined()) {
                    return "license." + pluginLicense.getError().get().name();
                } else {
                    return "license.valid";
                }
            } else {
                return "license.nolicense";
            }
        } catch (PluginLicenseStoragePluginUnresolvedException e) {
            log.error(e);
            return "license.error";
        }
    }

    private static final Logger log = Logger.getLogger(LicenseUtil.class);
}
