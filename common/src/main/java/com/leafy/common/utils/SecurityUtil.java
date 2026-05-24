package com.leafy.common.utils;

import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

    public String getCurrentUserId() {
        return ServiceSecurityUtils.getCurrentUserId();
    }

    public String getCurrentProfileId() {
        return ServiceSecurityUtils.getCurrentProfileId();
    }

    public String getCurrentUserPhoneNumber() {
        return ""; // Not strictly required if not available
    }
}
