package com.daysheet.billing;

import com.daysheet.domain.PricePlan;
import com.daysheet.repository.PricePlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the two starting plans the first time the app runs. Change prices later in the database. */
@Component
@RequiredArgsConstructor
public class PlanSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanSeeder.class);
    private final PricePlanRepository plans;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (plans.count() > 0) return;
        plans.save(plan("MONTHLY", "Monthly", "Pay month by month. Stop any time.", 79900, 30, "month", null, false, 1,
                "Your own booking page\nCalendar, visit history and notes\nPrescriptions and documents on every visit\nInvoices with tax and printing"));
        plans.save(plan("YEARLY", "Yearly", "One payment for the whole year.", 799000, 365, "year", "2 months free", true, 2,
                "Everything in Monthly\nHelp moving your existing client list\nPriority support on WhatsApp\nPrice locked for 12 months"));
        log.info("Created default price plans (Monthly ₹799, Yearly ₹7,990).");
    }

    private static PricePlan plan(String code, String name, String description, long paise, int days, String interval,
                                  String badge, boolean highlighted, int order, String features) {
        PricePlan p = new PricePlan();
        p.setCode(code);
        p.setName(name);
        p.setDescription(description);
        p.setAmountPaise(paise);
        p.setDurationDays(days);
        p.setIntervalLabel(interval);
        p.setBadge(badge);
        p.setHighlighted(highlighted);
        p.setSortOrder(order);
        p.setFeatures(features);
        return p;
    }
}
