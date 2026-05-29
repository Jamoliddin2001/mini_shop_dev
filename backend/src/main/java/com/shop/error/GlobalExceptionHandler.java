package com.shop.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Centralised exception handling (design pattern: <b>Global Handler</b> via
 * {@code @RestControllerAdvice}). Produces a single {@link ApiError} shape for every error and
 * never leaks stack traces to the client.
 *
 * <p>Security failures raised inside the filter chain (missing/invalid token, route-level role
 * denial) do NOT pass through here — they are rendered by the security entry-point /
 * access-denied handler, which reuse this same {@link ApiError} format. Failures raised during
 * controller dispatch (e.g. bad credentials on login, method-level {@code @PreAuthorize}) are
 * handled here.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation failure on a {@code @Valid @RequestBody} DTO -> 400 with field details. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("Validation failed on {} {}: {} field violation(s)",
                request.getMethod(), request.getRequestURI(), violations.size());
        log.debug("Field violations: {}", violations);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    /** Bean Validation failure on {@code @Validated} request params/path variables -> 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        log.warn("Constraint violation on {} {}: {} violation(s)",
                request.getMethod(), request.getRequestURI(), violations.size());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    /** Malformed / unreadable JSON body -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        log.warn("Malformed request body on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request, "Request body is missing or malformed");
    }

    /** Duplicate email on registration -> 409. */
    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailInUse(EmailAlreadyUsedException ex,
                                                     HttpServletRequest request) {
        log.warn("Registration conflict on {} {}: email already registered",
                request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "Conflict", request, ex.getMessage());
    }

    /** Semantically invalid request rejected by a controller/service rule -> 400. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex,
                                                     HttpServletRequest request) {
        log.warn("Bad request on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", request, ex.getMessage());
    }

    /** Requested/referenced resource does not exist (product or category) -> 404. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
                                                   HttpServletRequest request) {
        log.warn("Not found on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", request, ex.getMessage());
    }

    /**
     * Query/path parameter of the wrong type -> 400 (e.g. {@code ?minPrice=abc}, {@code ?categoryId=xyz}).
     * Without this, such trivial bad input would fall through to the 500 handler.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        log.warn("Type mismatch on {} {}: parameter '{}'",
                request.getMethod(), request.getRequestURI(), ex.getName());
        FieldViolation violation = new FieldViolation(ex.getName(), "Invalid value for parameter");
        return build(HttpStatus.BAD_REQUEST, "Bad Request", request, List.of(violation));
    }

    /**
     * Unsupported {@code sort} property (e.g. {@code ?sort=bogus}) -> 400. Spring Data raises this
     * when resolving a {@code Sort} against an unknown entity attribute; without this handler it
     * would surface as a 500. The controller also whitelists sortable fields as the primary guard.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidSort(PropertyReferenceException ex,
                                                      HttpServletRequest request) {
        log.warn("Invalid sort/property on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getPropertyName());
        FieldViolation violation = new FieldViolation("sort", "Unknown sort property: " + ex.getPropertyName());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", request, List.of(violation));
    }

    /** Authentication failure raised during dispatch (e.g. bad credentials on login) -> 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest request) {
        log.warn("Authentication failed on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", request, "Invalid email or password");
    }

    /** Authenticated but not allowed (method-level @PreAuthorize) -> 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        log.warn("Access denied on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Forbidden", request, "Access is denied");
    }

    /**
     * Database integrity constraint violation surfaced to the web layer -> 409. The common case is
     * deleting/modifying a resource still referenced by others (e.g. deleting a product that is part
     * of an existing order — {@code order_items.product_id} is {@code ON DELETE RESTRICT}). Without
     * this handler such a foreseeable conflict would fall through to the 500 handler. The raw DB
     * message can leak schema details, so it is logged server-side only; the client gets a safe,
     * generic message.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity conflict on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", request,
                "The resource is referenced by other records and cannot be modified or deleted");
    }

    /** Anything unexpected -> 500. Stack trace goes to the log only, never to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", request,
                "An unexpected error occurred");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, status.getReasonPhrase(), request, message, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, HttpServletRequest request,
                                           String message) {
        return build(status, error, request, message, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, HttpServletRequest request,
                                           List<FieldViolation> violations) {
        return build(status, error, request, "Request validation failed", violations);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, HttpServletRequest request,
                                           String message, List<FieldViolation> violations) {
        ApiError body = ApiError.of(status.value(), error, message, request.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }
}
