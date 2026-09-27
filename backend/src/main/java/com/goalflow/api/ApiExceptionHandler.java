package com.goalflow.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiProblem> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiProblem(
                exception.getCode(),
                exception.getMessage(),
                exception.getCurrentVersion(),
                Map.of(),
                Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiProblem(
                "VALIDATION_FAILED",
                "请检查填写内容。",
                null,
                fieldErrors,
                Instant.now()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiProblem> handleOptimisticLock() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiProblem(
                "VERSION_CONFLICT",
                "内容已在其他位置更新，请刷新后重试。",
                null,
                Map.of(),
                Instant.now()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiProblem> handleDataIntegrity() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiProblem(
                "DATA_CONFLICT",
                "数据与现有记录冲突，请刷新后重试。",
                null,
                Map.of(),
                Instant.now()));
    }

    public record ApiProblem(
            String code,
            String message,
            Integer currentVersion,
            Map<String, String> fieldErrors,
            Instant timestamp) {
    }
}
