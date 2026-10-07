package com.daysheet.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Profession pack modules. Each practice gets sensible defaults for its profession and can switch any on or off. */
public enum PackModule {
    PRESCRIPTIONS, PACKAGES, CASES, DEADLINES;

    public static Set<PackModule> defaultsFor(Profession p) {
        return switch (p) {
            case DOCTOR, DENTIST -> Set.of(PRESCRIPTIONS);
            case PHYSIOTHERAPIST, PSYCHOLOGIST, CONSULTANT, SALON, TUTOR, OTHER -> Set.of(PACKAGES);
            case LAWYER -> Set.of(CASES);
            case ACCOUNTANT -> Set.of(DEADLINES);
        };
    }

    /** Stored as "PRESCRIPTIONS,PACKAGES"; null means "use the profession's defaults". */
    public static Set<PackModule> parse(String stored, Profession profession) {
        if (stored == null) return defaultsFor(profession);
        if (stored.isBlank()) return Set.of();
        return Arrays.stream(stored.split(",")).map(String::trim)
                .filter(s -> Arrays.stream(values()).anyMatch(m -> m.name().equals(s)))
                .map(PackModule::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String format(List<String> modules) {
        return modules.stream().map(String::trim)
                .filter(s -> Arrays.stream(values()).anyMatch(m -> m.name().equals(s)))
                .distinct().collect(Collectors.joining(","));
    }
}
