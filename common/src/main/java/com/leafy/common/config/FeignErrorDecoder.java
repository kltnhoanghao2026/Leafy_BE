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
                // Preserve the raw downstream message as the detail so callers see the real error
                String detail = apiResponse.message();
                log.error("Feign error [{}] from {}: {}", errorCode.getCode(), methodKey,
                        detail != null ? detail : errorCode.getMessageKey());
                return new AppException(errorCode, detail);
            }
        } catch (IOException e) {
            log.error("Error decoding feign response from {}: {}", methodKey, e.getMessage());
            return new AppException(ErrorCode.SYS_UNCATEGORIZED);
        } catch (Exception e) {
            log.error("Unknown error decoding feign response from {}: {}", methodKey, e.getMessage());
            return new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }
}
