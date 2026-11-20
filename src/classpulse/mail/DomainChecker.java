package classpulse.mail;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/**
 * Checks that the domain of an email address can receive email, before any email is sent.
 *
 * It asks DNS for the domain's MX (mail exchanger) records, using Java's built-in JNDI
 * DNS provider. "gmial.com" style typos and made-up domains have no mail server, so the
 * account is refused straight away. The final proof that the mailbox exists is the
 * 6-digit code: only someone who can read that inbox can type it in.
 */
public class DomainChecker {
    public enum Status {
        /** The domain has a mail server. */
        ACCEPTS_MAIL,
        /** The domain does not exist, or says it never accepts email. */
        NO_MAIL_SERVER,
        /** DNS could not be reached (for example, no internet). The code check still protects us. */
        UNKNOWN
    }

    private final Map<String, Status> cache = new ConcurrentHashMap<>();

    public Status check(String emailOrDomain) {
        String domain = emailOrDomain.substring(emailOrDomain.lastIndexOf('@') + 1).toLowerCase(java.util.Locale.ROOT);
        Status cached = cache.get(domain);
        if (cached != null) {
            return cached;
        }
        Status status = lookup(domain);
        if (status != Status.UNKNOWN) {
            cache.put(domain, status); // Only remember real answers, not network failures.
        }
        return status;
    }

    /** Protected so tests can replace the network lookup. */
    protected Status lookup(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put(Context.PROVIDER_URL, "dns:");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext dns = null;
        try {
            dns = new InitialDirContext(env);
            Attributes mx = dns.getAttributes(domain, new String[]{"MX"});
            Attribute records = mx.get("MX");
            if (records != null && records.size() > 0) {
                // RFC 7505 "null MX" (0 .) means the domain explicitly accepts no email.
                boolean nullMx = records.size() == 1 && records.get().toString().trim().matches("0\\s+\\.?");
                return nullMx ? Status.NO_MAIL_SERVER : Status.ACCEPTS_MAIL;
            }
            // No MX record: mail falls back to the domain's own address (A record), if it has one.
            Attribute a = dns.getAttributes(domain, new String[]{"A"}).get("A");
            return a != null && a.size() > 0 ? Status.ACCEPTS_MAIL : Status.NO_MAIL_SERVER;
        } catch (NameNotFoundException e) {
            return Status.NO_MAIL_SERVER; // The domain does not exist at all.
        } catch (NamingException e) {
            return Status.UNKNOWN; // Timeout or no network.
        } finally {
            if (dns != null) {
                try {
                    dns.close();
                } catch (NamingException ignored) {
                    // Nothing else to do.
                }
            }
        }
    }
}
