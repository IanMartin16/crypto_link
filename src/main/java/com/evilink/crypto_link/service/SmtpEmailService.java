package com.evilink.crypto_link.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService {

  private final JavaMailSender mail;

  @Value("${mail.from}")
  private String from;

  public SmtpEmailService(JavaMailSender mail) {
    this.mail = mail;
  }

  public void sendApiKey(String to, String plan, String apiKey) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(to);
    msg.setFrom(from);
    msg.setSubject("Your CryptoLink API key (" + plan + ")");
    msg.setText("""
      Thanks for your purchase!

      Plan: %s
      API Key: %s

      Use it like:
      curl "https://cryptolink-production.up.railway.app/v1/prices?symbols=BTC,ETH&fiat=USD" -H "x-api-key: %s"

      (Keep this key private.)
      """.formatted(plan, apiKey, apiKey));

    mail.send(msg);
  }
}
