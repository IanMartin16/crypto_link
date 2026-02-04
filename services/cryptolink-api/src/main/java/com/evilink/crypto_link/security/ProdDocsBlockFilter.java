package com.evilink.crypto_link.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("prod")
public class ProdDocsBlockFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    String p = req.getRequestURI();

    boolean isDocs =
        p.equals("/v3/api-docs") || p.startsWith("/v3/api-docs/") ||
        p.equals("/api-docs")    || p.startsWith("/api-docs/") ||
        p.equals("/docs")        || p.startsWith("/docs/") ||
        p.startsWith("/swagger-ui");

    if (isDocs) {
      res.setStatus(404);
      return;
    }

    chain.doFilter(req, res);
  }
}
