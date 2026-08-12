package com.bbc.sms.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Repairs the common restored-database state (schools present, no report-card
 * rows) after Flyway has completed.  Each tenant is handled independently so
 * one malformed legacy tenant does not prevent the application from starting;
 * the Settings install endpoint remains available for that tenant.
 */
@Component
@Order(Integer.MAX_VALUE)
public class StandardReportTemplateProvisioningRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StandardReportTemplateProvisioningRunner.class);
    private final StandardReportTemplateProvisioningService provisioning;

    public StandardReportTemplateProvisioningRunner(StandardReportTemplateProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int inserted = provisioning.provisionAllSchools();
            if (inserted > 0) log.info("Provisioned {} standard report-card template version(s)", inserted);
        } catch (RuntimeException ex) {
            log.error("Standard report-card provisioning could not complete; use Settings to retry", ex);
        }
    }
}
