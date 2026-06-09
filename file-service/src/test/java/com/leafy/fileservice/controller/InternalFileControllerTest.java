package com.leafy.fileservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.leafy.fileservice.dto.response.FileResponse;
import com.leafy.fileservice.service.file.FileService;
import com.leafy.fileservice.service.s3.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class InternalFileControllerTest {

    @Mock
    private FileService fileService;

    @Mock
    private S3Service s3Service;

    @Test
    void downloadFileByS3KeyInternal_proxiesS3BytesWithMetadataHeaders() {
        InternalFileController controller = new InternalFileController(fileService, s3Service);
        FileResponse file = FileResponse.builder()
                .s3Key("folder/leaf.jpg")
                .originalFileName("leaf.jpg")
                .contentType("image/jpeg")
                .build();
        when(fileService.getFileByS3Key("folder/leaf.jpg")).thenReturn(Mono.just(file));
        when(s3Service.downloadFile("folder/leaf.jpg"))
                .thenReturn(Flux.just(new DefaultDataBufferFactory().wrap(new byte[] {1, 2, 3})));

        ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>> response =
                controller.downloadFileByS3KeyInternal("folder/leaf.jpg").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("image/jpeg");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment; filename=\"leaf.jpg\"");
        assertThat(response.getBody()).isNotNull();
    }
}
