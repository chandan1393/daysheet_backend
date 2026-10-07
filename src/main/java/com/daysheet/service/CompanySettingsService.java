package com.daysheet.service;

import com.daysheet.billing.GstStates;
import com.daysheet.domain.CompanySettings;
import com.daysheet.repository.CompanySettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Company and GST details, stored in the database and cached in memory.
 * The BUSINESS_* and GST_* environment settings are only used to fill the row the first time.
 */
@Service
public class CompanySettingsService {

    private static final Logger log = LoggerFactory.getLogger(CompanySettingsService.class);

    /** Read-only copy used everywhere else. */
    public record Company(String businessName, String businessAddress, String businessPhone, String supportEmail,
                          String gstin, int gstRate, boolean pricesIncludeTax, String sac, String invoicePrefix,
                          String jurisdictionCity, String grievanceOfficer, String legalName) {}

    private final CompanySettingsRepository repository;
    private final AtomicReference<Company> cache = new AtomicReference<>();

    @Value("${app.business.name}") private String envName;
    @Value("${app.business.address}") private String envAddress;
    @Value("${app.business.phone}") private String envPhone;
    @Value("${app.business.legal-name:}") private String envLegalName;
    @Value("${app.business.grievance-officer:}") private String envGrievance;
    @Value("${app.business.jurisdiction:}") private String envJurisdiction;
    @Value("${app.business.gstin}") private String envGstin;
    @Value("${app.mail.support}") private String envSupport;
    @Value("${app.gst.rate}") private int envRate;
    @Value("${app.gst.prices-include-tax}") private boolean envInclusive;
    @Value("${app.gst.sac}") private String envSac;
    @Value("${app.gst.invoice-prefix}") private String envPrefix;

    public CompanySettingsService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    public Company current() {
        Company c = cache.get();
        if (c != null) return c;
        synchronized (this) {
            if (cache.get() == null) cache.set(snapshot(loadOrCreate()));
            return cache.get();
        }
    }

    @Transactional
    public Company update(CompanySettings changes) {
        CompanySettings row = loadOrCreate();
        row.setBusinessName(changes.getBusinessName());
        row.setBusinessAddress(changes.getBusinessAddress());
        row.setBusinessPhone(changes.getBusinessPhone());
        row.setLegalName(changes.getLegalName());
        row.setSupportEmail(changes.getSupportEmail());
        row.setGstin(changes.getGstin());
        row.setGstRate(changes.getGstRate());
        row.setPricesIncludeTax(changes.isPricesIncludeTax());
        row.setSac(changes.getSac());
        row.setInvoicePrefix(changes.getInvoicePrefix());
        row.setJurisdictionCity(changes.getJurisdictionCity());
        row.setGrievanceOfficer(changes.getGrievanceOfficer());
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        Company c = snapshot(row);
        cache.set(c);
        return c;
    }

    @Transactional
    protected CompanySettings loadOrCreate() {
        return repository.findById(CompanySettings.SINGLETON_ID).orElseGet(() -> {
            CompanySettings s = new CompanySettings();
            s.setBusinessName(envName);
            s.setBusinessAddress(envAddress);
            s.setBusinessPhone(blankToNull(envPhone));
            s.setSupportEmail(envSupport);
            String gstin = envGstin == null ? "" : envGstin.trim().toUpperCase(Locale.ROOT);
            if (!gstin.isEmpty() && !GstStates.isValidGstin(gstin)) {
                log.warn("GST_NUMBER '{}' is not a valid GSTIN; leaving it empty. Fix it in the admin panel.", gstin);
                gstin = "";
            }
            s.setGstin(blankToNull(gstin));
            s.setGstRate(envRate);
            s.setPricesIncludeTax(envInclusive);
            s.setSac(envSac);
            s.setInvoicePrefix(envPrefix.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT));
            s.setLegalName(blankToNull(envLegalName));
            s.setGrievanceOfficer(blankToNull(envGrievance));
            s.setJurisdictionCity(envJurisdiction == null || envJurisdiction.isBlank() ? null : envJurisdiction.trim());
            s.setUpdatedAt(Instant.now());
            log.info("Created company settings from environment defaults.");
            return repository.save(s);
        });
    }

    private static Company snapshot(CompanySettings s) {
        return new Company(s.getBusinessName(), s.getBusinessAddress(), s.getBusinessPhone(), s.getSupportEmail(),
                s.getGstin(), s.getGstRate(), s.isPricesIncludeTax(), s.getSac(),
                s.getInvoicePrefix() == null || s.getInvoicePrefix().isBlank() ? "DS" : s.getInvoicePrefix(),
                s.getJurisdictionCity(), s.getGrievanceOfficer(), s.getLegalName());
    }

    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
