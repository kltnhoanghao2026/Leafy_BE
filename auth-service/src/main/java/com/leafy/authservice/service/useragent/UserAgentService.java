package com.leafy.authservice.service.useragent;

import com.leafy.authservice.enums.DeviceType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Service;

/**
 * User Agent Parser Service
 * Uses yauaa library to parse user agent strings
 */
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAgentService {
    
    UserAgentAnalyzer userAgentAnalyzer;
    
    public UserAgentService() {
        this.userAgentAnalyzer = UserAgentAnalyzer
                .newBuilder()
                .hideMatcherLoadStats()
                .withCache(10000)
                .build();
    }
    
    /**
     * Parse user agent string and extract device information
     */
    public ParsedUserAgent parse(String userAgentString) {
        if (userAgentString == null || userAgentString.isBlank()) {
            return ParsedUserAgent.unknown();
        }
        
        try {
            UserAgent userAgent = userAgentAnalyzer.parse(userAgentString);
            
            String deviceClass = userAgent.getValue(UserAgent.DEVICE_CLASS);
            String deviceBrand = userAgent.getValue(UserAgent.DEVICE_BRAND);
            String deviceName = userAgent.getValue(UserAgent.DEVICE_NAME);
            String osName = userAgent.getValue(UserAgent.OPERATING_SYSTEM_NAME);
            String osVersion = userAgent.getValue(UserAgent.OPERATING_SYSTEM_VERSION);
            String browserName = userAgent.getValue(UserAgent.AGENT_NAME);
            String browserVersion = userAgent.getValue(UserAgent.AGENT_VERSION);
            
            DeviceType deviceType = mapDeviceType(deviceClass);
            
            String fullDeviceName = buildDeviceName(deviceBrand, deviceName, browserName, osName);
            
            return ParsedUserAgent.builder()
                    .deviceType(deviceType)
                    .deviceName(fullDeviceName)
                    .platform(osName)
                    .osVersion(osVersion)
                    .browser(browserName)
                    .browserVersion(browserVersion)
                    .deviceClass(deviceClass)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse user agent: {}", userAgentString, e);
            return ParsedUserAgent.unknown();
        }
    }
    
    /**
     * Map device class to DeviceType enum
     */
    private DeviceType mapDeviceType(String deviceClass) {
        if (deviceClass == null) {
            return DeviceType.WEB;
        }
        
        return switch (deviceClass.toUpperCase()) {
            case "PHONE" -> DeviceType.MOBILE;
            case "TABLET" -> DeviceType.TABLET;
            case "DESKTOP" -> DeviceType.DESKTOP;
            case "MOBILE", "SMARTPHONE" -> DeviceType.MOBILE;
            default -> DeviceType.WEB;
        };
    }
    
    /**
     * Build human-readable device name
     */
    private String buildDeviceName(String brand, String name, String browser, String os) {
        StringBuilder deviceName = new StringBuilder();
        
        if (brand != null && !"Unknown".equals(brand)) {
            deviceName.append(brand).append(" ");
        }
        
        if (name != null && !"Unknown".equals(name) && !name.equals(brand)) {
            deviceName.append(name);
        } else if (browser != null && !"Unknown".equals(browser)) {
            deviceName.append(browser);
            if (os != null && !"Unknown".equals(os)) {
                deviceName.append(" on ").append(os);
            }
        } else if (os != null && !"Unknown".equals(os)) {
            deviceName.append(os);
        }
        
        String result = deviceName.toString().trim();
        return result.isEmpty() ? "Unknown Device" : result;
    }
    
    /**
     * Parsed user agent information
     */
    @Getter
    public static class ParsedUserAgent {
        private final DeviceType deviceType;
        private final String deviceName;
        private final String platform;
        private final String osVersion;
        private final String browser;
        private final String browserVersion;
        private final String deviceClass;
        
        private ParsedUserAgent(DeviceType deviceType, String deviceName, String platform, 
                               String osVersion, String browser, String browserVersion, String deviceClass) {
            this.deviceType = deviceType;
            this.deviceName = deviceName;
            this.platform = platform;
            this.osVersion = osVersion;
            this.browser = browser;
            this.browserVersion = browserVersion;
            this.deviceClass = deviceClass;
        }
        
        public static ParsedUserAgent unknown() {
            return new ParsedUserAgent(DeviceType.WEB, "Unknown Device", "Unknown", 
                    "Unknown", "Unknown", "Unknown", "Unknown");
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private DeviceType deviceType;
            private String deviceName;
            private String platform;
            private String osVersion;
            private String browser;
            private String browserVersion;
            private String deviceClass;
            
            public Builder deviceType(DeviceType deviceType) {
                this.deviceType = deviceType;
                return this;
            }
            
            public Builder deviceName(String deviceName) {
                this.deviceName = deviceName;
                return this;
            }
            
            public Builder platform(String platform) {
                this.platform = platform;
                return this;
            }
            
            public Builder osVersion(String osVersion) {
                this.osVersion = osVersion;
                return this;
            }
            
            public Builder browser(String browser) {
                this.browser = browser;
                return this;
            }
            
            public Builder browserVersion(String browserVersion) {
                this.browserVersion = browserVersion;
                return this;
            }
            
            public Builder deviceClass(String deviceClass) {
                this.deviceClass = deviceClass;
                return this;
            }
            
            public ParsedUserAgent build() {
                return new ParsedUserAgent(deviceType, deviceName, platform, osVersion, 
                        browser, browserVersion, deviceClass);
            }
        }
    }
}
