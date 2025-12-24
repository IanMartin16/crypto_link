package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.exception.UpstreamException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.evilink.crypto_link.controller")
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private String requestId(HttpServletRequest req) {
    Object rid = req.getAttribute("requestId"); // si tu RequestIdFilter lo setea
    if (rid != null) return rid.toString();
    String h = req.getHeader("X-Request-Id");
    return h != null ? h : "";
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> status(ResponseStatusException e, HttpServletRequest req) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of(
      "ok", false,
      "error", e.getReason() == null ? "Error" : e.getReason(),
      "requestId", requestId(req)
    ));
  }

  @ExceptionHandler(UpstreamException.class)
  public ResponseEntity<Map<String, Object>> upstream(UpstreamException e, HttpServletRequest req) {
    log.warn("Upstream error requestId={} msg={}", requestId(req), e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
      "ok", false,
      "error", "Upstream provider error",
      "requestId", requestId(req)
    ));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<Map<String, Object>> db(DataAccessException e, HttpServletRequest req) {
    log.error("DB error requestId={}", requestId(req), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
      "ok", false,
      "error", "Database error",
      "requestId", requestId(req)
    ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> any(Exception e, HttpServletRequest req) {
    log.error("Unhandled error requestId={}", requestId(req), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
      "ok", false,
      "error", "Internal Server Error",
      "requestId", requestId(req)
    ));
  }
}
