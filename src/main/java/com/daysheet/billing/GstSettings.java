package com.daysheet.billing;

import com.daysheet.service.CompanySettingsService;
import com.daysheet.service.CompanySettingsService.Company;
import org.springframework.stereotype.Component;

/** GST rules on top of the company settings (which an admin can edit). */
@Component
public class GstSettings {

    /** Price split into the parts a GST invoice shows. All amounts in paise. */
    public record Breakdown(long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise, long totalPaise) {
        public long taxPaise() { return cgstPaise + sgstPaise + igstPaise; }
    }

    private final CompanySettingsService company;

    public GstSettings(CompanySettingsService company) {
        this.company = company;
    }

    private Company c() { return company.current(); }

    /** GST is charged only when a valid GSTIN is set. Without one, payments get plain receipts with no tax. */
    public boolean registered() { return GstStates.isValidGstin(c().gstin()); }

    public String gstin() { return registered() ? c().gstin() : null; }
    public String supplierStateCode() { return registered() ? c().gstin().substring(0, 2) : null; }
    public String businessName() { return c().businessName(); }
    public String businessAddress() { return c().businessAddress(); }
    public String businessPhone() { return c().businessPhone(); }
    public String supportEmail() { return c().supportEmail(); }
    public String jurisdictionCity() { return c().jurisdictionCity(); }
    /** Named grievance officer, or null to show only the designation. Never falls back to the legal name. */
    public String grievanceOfficer() { return c().grievanceOfficer(); }
    public String legalName() { return c().legalName(); }
    public int rate() { return registered() ? c().gstRate() : 0; }
    public boolean pricesIncludeTax() { return c().pricesIncludeTax(); }
    public String sac() { return c().sac(); }
    public String invoicePrefix() { return c().invoicePrefix(); }

    /**
     * Splits a plan price for a buyer in the given state. Same state as you: CGST + SGST.
     * Another state (or unknown): IGST. Prices are treated as tax-inclusive or tax-exclusive per settings.
     */
    public Breakdown breakdown(long pricePaise, String buyerStateCode) {
        int r = rate();
        if (r <= 0) return new Breakdown(pricePaise, 0, 0, 0, pricePaise);
        if (pricesIncludeTax()) return fromTotal(pricePaise, buyerStateCode);
        long tax = Math.round(pricePaise * r / 100.0);
        return split(pricePaise, tax, buyerStateCode);
    }

    /** For an amount already received (e.g. a bank transfer): treat it as GST-inclusive. */
    public Breakdown fromTotal(long totalPaise, String buyerStateCode) {
        int r = rate();
        if (r <= 0) return new Breakdown(totalPaise, 0, 0, 0, totalPaise);
        long taxable = Math.round(totalPaise * 100.0 / (100 + r));
        return split(taxable, totalPaise - taxable, buyerStateCode);
    }

    private Breakdown split(long taxable, long tax, String buyerStateCode) {
        boolean intraState = buyerStateCode != null && buyerStateCode.equals(supplierStateCode());
        long cgst = intraState ? tax / 2 : 0;
        long sgst = intraState ? tax - cgst : 0;
        long igst = intraState ? 0 : tax;
        return new Breakdown(taxable, cgst, sgst, igst, taxable + tax);
    }
}
