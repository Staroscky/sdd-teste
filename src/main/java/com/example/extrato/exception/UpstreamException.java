package com.example.extrato.exception;

public class UpstreamException extends RuntimeException {

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
