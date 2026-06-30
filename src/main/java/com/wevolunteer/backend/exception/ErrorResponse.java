package com.wevolunteer.backend.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors) {

    public ErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, null);
    }

    public ErrorResponse(int status, String error, String message, String path, List<FieldErrorDetail> fieldErrors) {
        this(Instant.now(), status, error, message, path, fieldErrors);
    }

    public record FieldErrorDetail(String field, String message) {
    }
}