package org.project.security;

public interface CaptchaVerifier {
    void verify(String token, String expectedAction, String remoteIp);
}
