package pl.trackidea.service;

import org.springframework.http.HttpStatus;

/** Błąd reguł biznesowych – zwracany do frontendu jako czytelny komunikat. */
public class DomainException extends RuntimeException {

    private final HttpStatus status;

    public DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static DomainException badRequest(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, message);
    }

    public static DomainException forbidden(String message) {
        return new DomainException(HttpStatus.FORBIDDEN, message);
    }

    public static DomainException notFound(String message) {
        return new DomainException(HttpStatus.NOT_FOUND, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
