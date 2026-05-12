package com.devMetrics.develop.controller;

import com.devMetrics.develop.exceptions.GitHubApiException;
import com.devMetrics.develop.exceptions.RepoAlreadyConnectedException;
import com.devMetrics.develop.exceptions.RepoNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RepoNotFoundException.class)
    public ResponseEntity<?> handleNotFound(RepoNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RepoAlreadyConnectedException.class)
    public ResponseEntity<?> handleAlreadyConnected(
            RepoAlreadyConnectedException ex) {
        return ResponseEntity.status(409)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<?> handleGitHubError(GitHubApiException ex) {
        return ResponseEntity.status(502)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(403)
                .body(Map.of("error", ex.getMessage()));
    }
}