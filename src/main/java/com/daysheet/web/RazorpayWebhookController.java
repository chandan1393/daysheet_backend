package com.daysheet.web;

import com.daysheet.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Set this URL in Razorpay Dashboard → Webhooks: https://your-domain/api/public/razorpay/webhook
 * Events: payment.captured and order.paid. Secret: same value as RAZORPAY_WEBHOOK_SECRET.
 */
@RestController
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final BillingService billing;

    @PostMapping("/api/public/razorpay/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        billing.handleWebhook(body, signature);
        return ResponseEntity.ok().build();
    }
}
