package org.project.service.ex;

public class RateLimitExceededException extends ServiceException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
