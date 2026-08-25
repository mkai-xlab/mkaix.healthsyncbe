package com.g93.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Letterhead and default wording printed on the X-ray report form. These values belong to the
 * hospital that deploys the system rather than to any single examination, so they are configured
 * per environment instead of being stored per record.
 */
@ConfigurationProperties(prefix = "app.report")
public record XrayReportProperties(
        String ministryName,
        String hospitalName,
        String departmentName,
        String formCode,
        String clinicalDepartment,
        String signaturePlace) {
}
