package org.project.workflow.exception;

import org.springframework.http.HttpStatus;

/** 对外错误仅携带稳定错误码和可公开摘要，不返回内部堆栈。 */
public class WorkflowApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public WorkflowApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
