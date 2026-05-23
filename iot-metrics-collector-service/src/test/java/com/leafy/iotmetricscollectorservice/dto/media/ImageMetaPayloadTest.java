package com.leafy.iotmetricscollectorservice.dto.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ImageMetaPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesLegacyFirmwarePayloadWithDeviceUidAndBlankTimestamps() throws Exception {
        String json = """
            {
              "deviceUid": "leafy-prototype-001",
              "timestamp": "",
              "status": "SUCCESS",
              "requestId": "request-1",
              "triggerType": "MANUAL",
              "success": true,
              "ts": "",
              "fileId": "file-1",
              "contentType": "image/jpeg",
              "sizeBytes": 8857,
              "width": 640,
              "height": 480,
              "error": null
            }
            """;

        ImageMetaPayload payload = objectMapper.readValue(json, ImageMetaPayload.class);

        assertThat(payload.getRequestId()).isEqualTo("request-1");
        assertThat(payload.getSuccess()).isTrue();
        assertThat(payload.getTimestamp()).isNull();
        assertThat(payload.getTs()).isNull();
        assertThat(payload.getFileId()).isEqualTo("file-1");
        assertThat(payload.getSizeBytes()).isEqualTo(8857L);
    }

    @Test
    void deserializesIso8601TimestampsNormally() throws Exception {
        String json = """
            {
              "timestamp": "2026-05-21T04:18:52Z",
              "ts": "2026-05-21T04:18:52Z",
              "status": "SUCCESS",
              "requestId": "request-1"
            }
            """;

        ImageMetaPayload payload = objectMapper.readValue(json, ImageMetaPayload.class);

        assertThat(payload.getTimestamp()).isEqualTo(Instant.parse("2026-05-21T04:18:52Z"));
        assertThat(payload.getTs()).isEqualTo(Instant.parse("2026-05-21T04:18:52Z"));
    }
}
