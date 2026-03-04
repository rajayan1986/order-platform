package com.platform.processing.saga;

public record SagaStepResult(boolean success, boolean compensationRequired, String newStatus) {

    public static SagaStepResult success(String newStatus) {
        return new SagaStepResult(true, false, newStatus);
    }

    public static SagaStepResult failure(String newStatus, boolean compensationRequired) {
        return new SagaStepResult(false, compensationRequired, newStatus);
    }
}
