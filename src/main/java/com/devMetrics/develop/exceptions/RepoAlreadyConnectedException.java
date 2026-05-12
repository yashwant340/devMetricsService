package com.devMetrics.develop.exceptions;

public class RepoAlreadyConnectedException extends RuntimeException {
    public RepoAlreadyConnectedException(String message) { super(message); }
}