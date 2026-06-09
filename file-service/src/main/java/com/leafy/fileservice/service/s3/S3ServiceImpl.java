package com.leafy.fileservice.service.s3;

import com.leafy.fileservice.dto.response.S3UploadResponse;
import com.leafy.fileservice.dto.response.PresignedUploadResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements S3Service {

        S3AsyncClient s3AsyncClient;

        @NonFinal
        @Value("${aws.s3.bucket}")
        String bucketName;

        @NonFinal
        @Value("${aws.region}")
        String region;

        @Override
        public Mono<S3UploadResponse> uploadFile(FilePart filePart) {
                String filename = filePart.filename();
                String key = UUID.randomUUID() + "-" + filename;

                return DataBufferUtils.join(filePart.content())
                                .flatMap(dataBuffer -> {
                                        long contentLength = dataBuffer.readableByteCount();
                                        String contentType = "application/octet-stream";
                                        if (filePart.headers().getContentType() != null) {
                                                contentType = filePart.headers().getContentType().toString();
                                        }

                                        Map<String, String> metadata = Map.of("filename", filename);

                                        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                                        .contentType(contentType)
                                                        .contentLength(contentLength)
                                                        .metadata(metadata)
                                                        .bucket(bucketName)
                                                        .key(key)
                                                        .build();

                                        log.info("Uploading file to S3: bucket={}, key={}, size={}", bucketName, key,
                                                        contentLength);

                                        return Mono.fromFuture(
                                                        s3AsyncClient.putObject(
                                                                        putObjectRequest,
                                                                        AsyncRequestBody.fromByteBuffer(
                                                                                        dataBuffer.toByteBuffer())))
                                                        .map(response -> {
                                                                log.info("File uploaded successfully to S3: key={}, size={}",
                                                                                key, contentLength);
                                                                return S3UploadResponse.builder()
                                                                                .s3Key(key)
                                                                                .fileSize(contentLength)
                                                                                .build();
                                                        });
                                });
        }

        @Override
        public Flux<DataBuffer> downloadFile(String s3Key) {
                log.info("Downloading file from S3: bucket={}, key={}", bucketName, s3Key);

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Key)
                                .build();

                return Flux.from(
                                Mono.fromFuture(
                                                s3AsyncClient.getObject(
                                                                getObjectRequest,
                                                                AsyncResponseTransformer.toPublisher()))
                                                .flatMapMany(response -> {
                                                        log.info("File download started from S3: key={}", s3Key);
                                                        return Flux.from(response)
                                                                        .map(byteBuffer -> {
                                                                                byte[] bytes = new byte[byteBuffer
                                                                                                .remaining()];
                                                                                byteBuffer.get(bytes);
                                                                                return new DefaultDataBufferFactory()
                                                                                                .wrap(bytes);
                                                                        });
                                                }));
        }

        @Override
        public Mono<Void> deleteFile(String s3Key) {
                log.info("Deleting file from S3: bucket={}, key={}", bucketName, s3Key);

                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Key)
                                .build();

                return Mono.fromFuture(s3AsyncClient.deleteObject(deleteObjectRequest))
                                .doOnSuccess(response -> log.info("File deleted successfully from S3: key={}", s3Key))
                                .then();
        }

        @Override
        public Mono<String> generatePresignedUrl(String s3Key, int expirationMinutes) {
                log.info("Generating presigned URL for S3 key: {}, expiration: {} minutes", s3Key, expirationMinutes);

                return Mono.fromCallable(() -> {
                        try (S3Presigner presigner = createPresigner()) {
                                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                                .bucket(bucketName)
                                                .key(s3Key)
                                                .build();

                                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                                                .getObjectRequest(getObjectRequest)
                                                .build();

                                PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
                                String url = presignedRequest.url().toString();

                                log.info("Presigned URL generated successfully for key: {}", s3Key);
                                return url;
                        }
                });
        }

        @Override
        public Mono<PresignedUploadResponse> generatePresignedUploadUrl(String filename, String contentType, int expirationMinutes) {
                String key = UUID.randomUUID() + "-" + filename;
                log.info("Generating presigned URL for upload to S3 key: {}, expiration: {} minutes", key, expirationMinutes);

                return Mono.fromCallable(() -> {
                        try (S3Presigner presigner = createPresigner()) {
                                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                                .bucket(bucketName)
                                                .key(key)
                                                .contentType(contentType)
                                                .build();

                                PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                                                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                                                .putObjectRequest(putObjectRequest)
                                                .build();

                                PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
                                String url = presignedRequest.url().toString();

                                log.info("Presigned upload URL generated successfully for key: {}", key);
                                return PresignedUploadResponse.builder()
                                                .s3Key(key)
                                                .presignedUrl(url)
                                                .build();
                        }
                });
        }

        private S3Presigner createPresigner() {
                return S3Presigner.builder()
                                .region(Region.of(region))
                                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                                .build();
        }
}
