package com.daysheet.billing;

import com.daysheet.config.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Talks to Razorpay's Orders API and checks Razorpay signatures. */
@Component
public class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(String id, long amount, String currency, String status) {}

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestClient http;

    public RazorpayClient(@Value("${app.razorpay.key-id}") String keyId,
                          @Value("${app.razorpay.key-secret}") String keySecret,
                          @Value("${app.razorpay.webhook-secret}") String webhookSecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(10_000);
        timeouts.setReadTimeout(20_000);
        this.http = RestClient.builder()
                .baseUrl("https://api.razorpay.com")
                .requestFactory(timeouts)
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret))
                .build();
    }

    public boolean enabled() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }

    public String keyId() { return keyId; }

    /** Creates an order for the exact amount; the amount can't be changed by the browser afterwards. */
    public Order createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        try {
            Order order = http.post()
                    .uri("/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes))
                    .retrieve()
                    .body(Order.class);
            if (order == null || order.id() == null) throw new IllegalStateException("Empty order response");
            return order;
        } catch (RestClientException | IllegalStateException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Payments are not reachable right now. Try again in a minute or email us.");
        }
    }

    /** Signature Razorpay Checkout returns: HMAC-SHA256(order_id|payment_id, key_secret). */
    public boolean isValidPaymentSignature(String orderId, String paymentId, String signature) {
        return matches(hmac(keySecret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)), signature);
    }

    /** Signature on webhook calls: HMAC-SHA256(raw body, webhook secret). */
    public boolean isValidWebhookSignature(byte[] body, String signature) {
        if (webhookSecret.isBlank()) return false;
        return matches(hmac(webhookSecret, body), signature);
    }

    private static String hmac(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    /** Constant-time comparison so the signature can't be guessed byte by byte. */
    private static boolean matches(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.trim().getBytes(StandardCharsets.UTF_8));
    }
}
