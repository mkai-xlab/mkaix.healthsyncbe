package com.g93.be.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the configurable letterhead used when rendering PDF reports.
 */
@Configuration
@EnableConfigurationProperties(XrayReportProperties.class)
public class ReportConfiguration {
}
