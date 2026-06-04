package com.leafy.common.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class S3UtilV2 {

    @Value("${aws.s3.base-url:https://leafy-media-storage.s3.ap-southeast-1.amazonaws.com/}")
    private String s3BaseUrl;

    public String getS3BaseUrl() {
        return s3BaseUrl;
    }

    public String getFullUrl(String key) { 
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("http")) return key;
        return s3BaseUrl + key; 
    }
}
