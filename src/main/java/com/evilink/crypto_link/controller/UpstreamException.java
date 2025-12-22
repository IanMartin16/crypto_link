package com.evilink.crypto_link.controller;

public class UpstreamException extends RuntimeException {
    public UpstreamException(String msg, Throwable cause) { super(msg, cause); }
    public UpstreamException(String msg) { super(msg); }
}
