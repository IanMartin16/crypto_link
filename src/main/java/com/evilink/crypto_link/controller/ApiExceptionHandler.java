package com.evilink.crypto_link.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, Object> responseStatus(ResponseStatusException e) {
        // Respeta el status real (401, 404, etc.)
        return Map.of("ok", false, "error", e.getReason() == null ? "Error" : e.getReason());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("ok", false, "error", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(UpstreamException.class)
    public Map<String, Object> upstream(UpstreamException e) {
        return Map.of("ok", false, "error", "Upstream provider error");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Map<String, Object> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return Map.of("ok", false, "error", "Internal Server Error");
    }
}
