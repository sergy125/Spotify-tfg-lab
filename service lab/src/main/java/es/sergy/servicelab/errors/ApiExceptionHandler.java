package es.sergy.servicelab.errors;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ProblemDetail> externalApi(ExternalApiException ex) {
        var response = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, ex.getRetryAfter());
        }
        return response.body(ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ProblemDetail> database(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "No se pudo guardar o leer el ranking. Comprueba la ruta y los permisos del archivo SQLite."));
    }
}
