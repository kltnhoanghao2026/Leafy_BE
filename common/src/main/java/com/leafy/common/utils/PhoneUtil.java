package com.leafy.common.utils;
import java.util.Optional;

public class PhoneUtil {
    public static String extractPhoneNumber(String loginIdentifier) { 
        return loginIdentifier; 
    }
    
    public static Optional<String> normalizeVnPhone(String p) { 
        return Optional.ofNullable(p); 
    }

    public static boolean isValidVnPhone(String p) {
        return true;
    }
}