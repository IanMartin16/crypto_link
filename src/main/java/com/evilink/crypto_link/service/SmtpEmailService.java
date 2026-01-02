package com.evilink.crypto_link.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService {

  private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

  private final Resend resend;
  private final String from;

  public SmtpEmailService(
      @Value("${cryptolink.resend.api-key:}") String apiKey,
      @Value("${cryptolink.resend.from:}") String from
  ) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("Missing cryptolink.resend.api-key (expected re_...)");
    }
    if (!apiKey.startsWith("re_")) {
      throw new IllegalStateException("cryptolink.resend.api-key must start with re_...");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalStateException("Missing cryptolink.resend.from (Name <email@domain>)");
    }
    this.resend = new Resend(apiKey);
    this.from = from.trim();
  }

  public void sendApiKey(String to, String plan, String apiKey) throws ResendException {
    if (to == null || to.isBlank()) throw new IllegalArgumentException("Missing 'to' email");
    if (plan == null || plan.isBlank()) throw new IllegalArgumentException("Missing plan");
    if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Missing apiKey");

    String email = to.trim().toLowerCase();
    String p = plan.trim().toUpperCase();

    String subject = "CryptoLink: tu API Key (" + p + ")";

    String text = """
        ¡Listo!
        
        Tu plan: %s
        Tu API Key: %s
        
        Guárdala en un lugar seguro. Si crees que se filtró, genera otra.
        """.formatted(p, apiKey);

    String html = """
        <div style="font-family:Arial,sans-serif;line-height:1.5">
          <h2>CryptoLink</h2>
          <p>Tu suscripción quedó activa.</p>
          <p><b>Plan:</b> %s</p>
          <p><b>Tu API Key:</b></p>
          <pre style="background:#f4f4f4;padding:12px;border-radius:8px;overflow:auto">%s</pre>
          <p>Guárdala en un lugar seguro. Si crees que se filtró, genera otra.</p>
          <hr/>
          <p style="font-size:12px;color:#666">Este correo fue enviado automáticamente.</p>
        </div>
        """.formatted(escapeHtml(p), escapeHtml(apiKey));

    CreateEmailOptions params = CreateEmailOptions.builder()
        .from(this.from)
        .to(email)
        .subject(subject)
        .text(text)
        .html(html)
        .build();

    ResendException last = null;

    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        CreateEmailResponse resp = resend.emails().send(params);
        log.info("Resend: email queued id={} to={} plan={}", resp.getId(), email, p);
        return;
      } catch (ResendException e) {
        last = e;
        int sc = e.getStatusCode();
        log.warn("Resend: send failed attempt={} statusCode={} name={} message={}",
            attempt, sc, e.getErrorName(), e.getMessage());

        boolean retryable = (sc == 429) || (sc >= 500 && sc <= 599);
        if (!retryable || attempt == 2) throw e;

        try { Thread.sleep(600L * attempt); } catch (InterruptedException ignored) {}
      }
    }

    throw last; // debería no llegar
  }

  private static String escapeHtml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }
}
