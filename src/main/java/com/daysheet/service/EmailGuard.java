package com.daysheet.service;

import com.daysheet.config.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;

/**
 * Stops obviously fake signups before an account is created: throwaway inbox services,
 * and domains that cannot receive email at all (no MX or A record).
 */
@Component
public class EmailGuard {

    private static final Logger log = LoggerFactory.getLogger(EmailGuard.class);

    /** Well-known disposable inbox domains. Add more here if you see them in your signups. */
    private static final Set<String> DISPOSABLE = Set.of(
            "mailinator.com", "guerrillamail.com", "guerrillamail.net", "sharklasers.com", "grr.la", "10minutemail.com",
            "10minutemail.net", "temp-mail.org", "tempmail.com", "tempmail.net", "tempmailo.com", "yopmail.com", "yopmail.fr",
            "getnada.com", "nada.email", "trashmail.com", "trashmail.de", "dispostable.com", "maildrop.cc", "mailnesia.com",
            "mintemail.com", "throwawaymail.com", "fakeinbox.com", "emailondeck.com", "mohmal.com", "mailcatch.com",
            "spamgourmet.com", "tempinbox.com", "getairmail.com", "moakt.com", "burnermail.io", "inboxkitten.com",
            "33mail.com", "mytemp.email", "tmpmail.org", "tmail.ws", "minuteinbox.com", "emailfake.com", "fakemail.net",
            "crazymailing.com", "mailpoof.com", "dropmail.me", "1secmail.com", "1secmail.net", "1secmail.org", "linshiyouxiang.net");

    private final boolean checkMx;

    public EmailGuard(@Value("${app.email.check-mx:true}") boolean checkMx) {
        this.checkMx = checkMx;
    }

    public void check(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT).trim();
        if (domain.isEmpty() || !domain.contains(".")) throw ApiException.badRequest("Enter a valid email address.");
        if (DISPOSABLE.contains(domain) || DISPOSABLE.stream().anyMatch(d -> domain.endsWith("." + d))) {
            throw ApiException.badRequest("Please use your regular email. Temporary inboxes can't receive our messages later.");
        }
        if (checkMx && !canReceiveMail(domain)) {
            throw ApiException.badRequest("The domain " + domain + " can't receive email. Check the address for typos.");
        }
    }

    /** True unless DNS says the domain doesn't exist or has neither MX nor A records. DNS errors never block a signup. */
    private boolean canReceiveMail(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            if (has(ctx, domain, "MX")) return true;
            return has(ctx, domain, "A");
        } catch (NameNotFoundException e) {
            return false;
        } catch (NamingException e) {
            log.debug("DNS check for {} skipped: {}", domain, e.getMessage());
            return true;
        } finally {
            if (ctx != null) try { ctx.close(); } catch (NamingException ignored) { }
        }
    }

    private static boolean has(DirContext ctx, String domain, String type) throws NamingException {
        Attributes attrs = ctx.getAttributes(domain, new String[]{type});
        Attribute a = attrs.get(type);
        return a != null && a.size() > 0;
    }
}
