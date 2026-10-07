package com.daysheet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends email through Resend (https://resend.com) when RESEND_API_KEY is set.
 * Falls back to SMTP if configured, and otherwise writes emails to the log (handy in development:
 * verification codes appear in the console).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> smtp;
    private final String from;
    private final String replyTo;
    private final String smtpHost;
    private final String resendKey;
    private final RestClient resend;

    public MailService(ObjectProvider<JavaMailSender> smtp,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.mail.support}") String replyTo,
                       @Value("${spring.mail.host:}") String smtpHost,
                       @Value("${app.mail.resend-api-key:}") String resendKey) {
        this.smtp = smtp;
        this.from = from;
        this.replyTo = replyTo;
        this.smtpHost = smtpHost;
        this.resendKey = resendKey == null ? "" : resendKey.trim();
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(10_000);
        timeouts.setReadTimeout(15_000);
        this.resend = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(timeouts)
                .defaultHeader("Authorization", "Bearer " + this.resendKey)
                .build();
    }

    /** True when real emails go out (Resend or SMTP), false when they're only logged. */
    public boolean configured() { return !resendKey.isBlank() || !smtpHost.isBlank(); }

    public String provider() { return !resendKey.isBlank() ? "Resend" : !smtpHost.isBlank() ? "SMTP" : "log only"; }

    /** Plain-text email. Never throws: a failed email must not break the user's action. */
    public boolean send(String to, String subject, String text) {
        return send(to, subject, text, null);
    }

    /** Email with an HTML version (plain text is always included for clients that block HTML). */
    public boolean send(String to, String subject, String text, String html) {
        if (!resendKey.isBlank()) return viaResend(to, subject, text, html);
        if (!smtpHost.isBlank()) return viaSmtp(to, subject, text);
        log.info("Email (no provider configured) to={} subject={}\n{}", to, subject, text);
        return true;
    }

    private boolean viaResend(String to, String subject, String text, String html) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("text", text);
        if (html != null) body.put("html", html);
        if (replyTo != null && !replyTo.isBlank()) body.put("reply_to", replyTo);
        try {
            resend.post().uri("/emails").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            log.error("Resend rejected email to {} ({}): {}", to, e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Could not reach Resend for {}: {}", to, e.getMessage());
        }
        return false;
    }

    private boolean viaSmtp(String to, String subject, String text) {
        JavaMailSender mail = smtp.getIfAvailable();
        if (mail == null) return false;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            if (replyTo != null && !replyTo.isBlank()) message.setReplyTo(replyTo);
            message.setSubject(subject);
            message.setText(text);
            mail.send(message);
            return true;
        } catch (Exception e) {
            log.error("Could not send email to {}: {}", to, e.getMessage());
            return false;
        }
    }
}
