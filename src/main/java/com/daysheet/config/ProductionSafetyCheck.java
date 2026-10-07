package com.daysheet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in production with settings that would be unsafe,
 * and warns about features that are switched off.
 */
@Component
@org.springframework.core.annotation.Order(100)
public class ProductionSafetyCheck implements ApplicationRunner {

    private final com.daysheet.service.CompanySettingsService company;

    public ProductionSafetyCheck(com.daysheet.service.CompanySettingsService company) {
        this.company = company;
    }

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);

    @Value("${app.env}") private String env;
    @Value("${app.jwt.secret}") private String jwtSecret;
    @Value("${app.cors.allowed-origins}") private String corsOrigins;
    @Value("${app.public-url}") private String publicUrl;
    @Value("${app.razorpay.key-id}") private String razorpayKey;
    @Value("${app.razorpay.webhook-secret}") private String webhookSecret;
    @Value("${spring.mail.host:}") private String smtpHost;
    @Value("${app.mail.resend-api-key:}") private String resendKey;
    @Value("${spring.datasource.password}") private String dbPassword;


    @Override
    public void run(ApplicationArguments args) {
        boolean production = "production".equalsIgnoreCase(env);
        if (!production) {
            log.info("Running in {} mode. Use --spring.profiles.active=prod on the server.", env);
            return;
        }
        if (jwtSecret.contains("dev-only") || jwtSecret.length() < 32) {
            throw new IllegalStateException("Set JWT_SECRET to a random string of at least 32 characters before going live.");
        }
        if ("postgres".equals(dbPassword)) {
            throw new IllegalStateException("Set DB_PASSWORD to a strong password before going live.");
        }
        if (corsOrigins.contains("localhost") || publicUrl.contains("localhost")) {
            log.warn("CORS_ORIGINS or PUBLIC_URL still points at localhost.");
        }
        if (!publicUrl.startsWith("https://")) log.warn("PUBLIC_URL should start with https://");
        if (razorpayKey.isBlank()) log.warn("Razorpay keys are not set: online payments are switched off.");
        else if (razorpayKey.startsWith("rzp_test_")) log.warn("Razorpay is in TEST mode. Use live keys to take real payments.");
        if (!razorpayKey.isBlank() && webhookSecret.isBlank()) {
            log.warn("RAZORPAY_WEBHOOK_SECRET is not set: payments still work, but closed-tab payments won't be picked up automatically.");
        }
        var company = this.company.current();
        if (company.businessName() == null || company.businessName().startsWith("Your registered")) {
            log.warn("Company details are placeholders. Fill them in at /admin → Company & GST.");
        }
        if (company.businessAddress() == null || !company.businessAddress().matches(".*\\b\\d{6}\\b.*")) {
            log.warn("Business address has no PIN code. Add the full address from your GST certificate at /admin → Company & GST.");
        }
        if (company.gstin() == null) log.warn("No GSTIN set: payments get plain receipts without GST. Set it at /admin → Company & GST.");
        if (resendKey.isBlank() && smtpHost.isBlank()) {
            throw new IllegalStateException("Set RESEND_API_KEY: signups need a verification email and none can be sent.");
        }

    }
}
