package com.daysheet.billing;

import java.util.LinkedHashMap;
import java.util.Map;

/** Indian states and union territories with their GST state codes (the first two digits of a GSTIN). */
public final class GstStates {
    private GstStates() {}

    public static final Map<String, String> BY_CODE;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("35", "Andaman and Nicobar Islands"); m.put("37", "Andhra Pradesh"); m.put("12", "Arunachal Pradesh");
        m.put("18", "Assam"); m.put("10", "Bihar"); m.put("04", "Chandigarh"); m.put("22", "Chhattisgarh");
        m.put("26", "Dadra and Nagar Haveli and Daman and Diu"); m.put("07", "Delhi"); m.put("30", "Goa");
        m.put("24", "Gujarat"); m.put("06", "Haryana"); m.put("02", "Himachal Pradesh"); m.put("01", "Jammu and Kashmir");
        m.put("20", "Jharkhand"); m.put("29", "Karnataka"); m.put("32", "Kerala"); m.put("38", "Ladakh");
        m.put("31", "Lakshadweep"); m.put("23", "Madhya Pradesh"); m.put("27", "Maharashtra"); m.put("14", "Manipur");
        m.put("17", "Meghalaya"); m.put("15", "Mizoram"); m.put("13", "Nagaland"); m.put("21", "Odisha");
        m.put("34", "Puducherry"); m.put("03", "Punjab"); m.put("08", "Rajasthan"); m.put("11", "Sikkim");
        m.put("33", "Tamil Nadu"); m.put("36", "Telangana"); m.put("16", "Tripura"); m.put("09", "Uttar Pradesh");
        m.put("05", "Uttarakhand"); m.put("19", "West Bengal");
        BY_CODE = java.util.Collections.unmodifiableMap(m);
    }

    public static boolean isValid(String code) { return code != null && BY_CODE.containsKey(code); }

    public static String name(String code) { return code == null ? "" : BY_CODE.getOrDefault(code, code); }

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** Format plus the official mod-36 check digit, so typos are caught before they reach an invoice. */
    public static boolean isValidGstin(String gstin) {
        if (gstin == null || !gstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")) return false;
        if (!isValid(gstin.substring(0, 2))) return false;
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int product = CHARS.indexOf(gstin.charAt(i)) * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHARS.charAt((36 - sum % 36) % 36) == gstin.charAt(14);
    }
}
