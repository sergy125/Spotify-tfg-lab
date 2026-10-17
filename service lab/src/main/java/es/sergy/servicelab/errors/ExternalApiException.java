package es.sergy.servicelab.errors;

import org.springframework.http.HttpStatus;

public class ExternalApiException extends RuntimeException {
    private final HttpStatus status;
    private final String retryAfter;

    public ExternalApiException(HttpStatus status, String message, String retryAfter) {
        super(message);
        this.status = status;
        this.retryAfter = retryAfter;
    }

    public ExternalApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
