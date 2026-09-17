package com.spendly.exception;

import com.spendly.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.query.sqm.UnknownPathException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    /** Account temporarily locked by {@link com.spendly.security.LoginAttemptService}. */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(TooManyAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiError.of(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "Validation failed", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            // "monthly.month" -> "month"
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fields.put(field, violation.getMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "Validation failed", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "Malformed request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'"));
    }

    /**
     * An unknown property in the {@code sort} query parameter.
     *
     * <p>Spring Data resolves sort properties against the entity and throws this
     * when the name does not exist. It is a client mistake, but without a handler
     * it reached the catch-all below and was answered with a 500 plus a stack
     * trace in the logs — so {@code ?sort=whatever} was an easy way to fill the
     * error log with noise.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownSortProperty(PropertyReferenceException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST,
                        "Cannot sort by '" + ex.getPropertyName() + "': no such field"));
    }

    /**
     * The same mistake as above, seen from the other side.
     *
     * <p>These repositories carry explicit {@code @Query} JPQL, so Spring Data
     * does not resolve the sort property itself — Hibernate does, while parsing
     * the query it just appended {@code order by e.nosuchfield} to, and the
     * result arrives wrapped in a generic data-access exception. Anything else
     * wrapped the same way really is a server fault and is left to the catch-all.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiError> handleInvalidDataAccessUsage(InvalidDataAccessApiUsageException ex) {
        if (hasCause(ex, UnknownPathException.class) || hasCause(ex, PropertyReferenceException.class)) {
            return ResponseEntity.badRequest()
                    .body(ApiError.of(HttpStatus.BAD_REQUEST, "Cannot sort or filter by that field"));
        }
        return handleGenericCause(ex);
    }

    /**
     * Two clients edited the same row; the second write lost.
     *
     * @see com.spendly.domain.Expense#getVersion()
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT,
                        "This item was changed somewhere else. Reload it and try again."));
    }

    /**
     * No controller and no static file for the path.
     *
     * <p>{@code NoResourceFoundException} became reachable once the SPA fallback
     * stopped answering {@code /api/**} with index.html. Both of these are plain
     * 404s; left to the catch-all they would be reported as server errors and
     * logged with a stack trace on every bot that probes for {@code /wp-login.php}.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, "No endpoint for this path"));
    }

    /**
     * Wrong HTTP verb on a real path.
     *
     * <p>Without this the exception falls through to the catch-all below and a
     * client mistake is reported as a 500 — and logged at ERROR with a stack
     * trace, so every bot probing endpoints with the wrong method buries the
     * failures that actually matter.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            // RFC 9110: a 405 response must say which methods are allowed.
            headers.setAllow(supported);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(ApiError.of(HttpStatus.METHOD_NOT_ALLOWED,
                        ex.getMethod() + " is not supported for this endpoint"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        String received = ex.getContentType() == null ? "none" : ex.getContentType().toString();
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Content-Type " + received + " is not supported; use application/json"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Unique constraint races (e.g. two concurrent creates of the same category)
        // surface here instead of through the service-level existence checks.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "Conflicts with existing data"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        // The request id also goes back to the caller in the body, so a reported
        // error leads straight to this line.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
    }

    /** The interesting cause is not always the deepest one, so check every link. */
    private static boolean hasCause(Throwable thrown, Class<? extends Throwable> type) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private ResponseEntity<ApiError> handleGenericCause(Exception ex) {
        log.error("Unhandled data access failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            Map<String, String> fields,
            String requestId
    ) {
        static ApiError of(HttpStatus status, String message) {
            return of(status, message, null);
        }

        static ApiError of(HttpStatus status, String message, Map<String, String> fields) {
            return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fields,
                    RequestIdFilter.current());
        }
    }
}
