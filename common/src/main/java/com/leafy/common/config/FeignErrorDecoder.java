package com.leafy.common.config;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            // Check if response body is null
            if (response.body() == null) {
                log.error("Feign error: {} - Response body is null, status: {}", methodKey, response.status());
                return new AppException(ErrorCode.SYS_UNCATEGORIZED);
            }
            
            try (InputStream bodyIs = response.body().asInputStream()) {
                ApiResponse<?> apiResponse = objectMapper.readValue(bodyIs, ApiResponse.class);
                ErrorCode errorCode = ErrorCode.fromCode(apiResponse.code());
                log.error("Feign error: {} - {}", errorCode.getCode(), errorCode.getMessageKey());
                return new AppException(errorCode);
            }
        } catch (IOException e) {
            log.error("Error decoding feign response", e);
            return new AppException(ErrorCode.SYS_UNCATEGORIZED);
        } catch (Exception e) {
            log.error("Unknown error decoding feign response", e);
            return new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }
}
