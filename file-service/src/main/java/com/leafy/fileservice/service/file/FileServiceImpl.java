package com.leafy.fileservice.service.file;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.fileservice.dto.request.FileUpdateRequest;
import com.leafy.fileservice.dto.request.FileUploadRequest;
import com.leafy.fileservice.dto.response.FileDetailsResponse;
import com.leafy.fileservice.dto.response.FileResponse;
import com.leafy.fileservice.mapper.FileMapper;
import com.leafy.fileservice.model.File;
import com.leafy.fileservice.repository.FileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import com.leafy.common.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Implementation of FileService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileServiceImpl implements FileService {

    FileRepository fileRepository;
    FileMapper fileMapper;

    @Override
    public Mono<FileResponse> createFile(FileUploadRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> {
                    if (auth.getPrincipal() instanceof UserPrincipal) {
                        return ((UserPrincipal) auth.getPrincipal()).getId();
                    }
                    return auth.getName();
                })
                .defaultIfEmpty("unknown")
                .flatMap(userId -> {
                    request.setUploadedBy(userId);
                    return Mono.fromCallable(() -> {
                        log.info("Creating file metadata with S3 key: {}", request.getS3Key());
                        File file = fileMapper.toEntity(request);
                        File savedFile = fileRepository.save(file);
                        log.info("File metadata created successfully with ID: {}", savedFile.getId());
                        return fileMapper.toResponse(savedFile);
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @Override
    public Mono<FileResponse> updateFile(String fileId, FileUpdateRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Updating file metadata with ID: {}", fileId);
            File file = findFileByIdBlocking(fileId);
            fileMapper.updateEntityFromRequest(request, file);
            File updatedFile = fileRepository.save(file);
            log.info("File metadata updated successfully with ID: {}", updatedFile.getId());
            return fileMapper.toResponse(updatedFile);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<FileResponse> getFileById(String fileId) {
        return Mono.fromCallable(() -> {
            log.info("Getting file by ID: {}", fileId);
            File file = findFileByIdBlocking(fileId);
            return fileMapper.toResponse(file);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<FileDetailsResponse> getFileDetailsById(String fileId) {
        return Mono.fromCallable(() -> {
            log.info("Getting file details by ID: {}", fileId);
            File file = findFileByIdBlocking(fileId);
            return fileMapper.toDetailsResponse(file);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<File> getFileEntityById(String fileId) {
        return Mono.fromCallable(() -> findFileByIdBlocking(fileId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<FileResponse> getFileByS3Key(String s3Key) {
        return Mono.fromCallable(() -> {
            log.info("Getting file by S3 key: {}", s3Key);
            File file = fileRepository.findByS3Key(s3Key)
                    .orElseThrow(() -> {
                        log.error("File not found with S3 key: {}", s3Key);
                        return new AppException(ErrorCode.FILE_NOT_FOUND);
                    });
            return fileMapper.toResponse(file);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Page<FileResponse>> getAllFiles(Pageable pageable) {
        return Mono.fromCallable(() -> {
            log.info("Getting all files with pagination");
            Page<File> files = fileRepository.findAll(pageable);
            return files.map(fileMapper::toResponse);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Page<FileResponse>> getActiveFiles(Pageable pageable) {
        return Mono.fromCallable(() -> {
            log.info("Getting all active files with pagination");
            Page<File> files = fileRepository.findByActiveTrue(pageable);
            return files.map(fileMapper::toResponse);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Page<FileResponse>> getFilesByUploadedBy(String uploadedBy, Pageable pageable) {
        return Mono.fromCallable(() -> {
            log.info("Getting files by uploader: {} with pagination", uploadedBy);
            Page<File> files = fileRepository.findByUploadedBy(uploadedBy, pageable);
            return files.map(fileMapper::toResponse);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Page<FileResponse>> searchFiles(String searchTerm, Pageable pageable) {
        return Mono.fromCallable(() -> {
            log.info("Searching files with term: {}", searchTerm);
            Page<File> files = fileRepository.searchByOriginalFileName(searchTerm, pageable);
            return files.map(fileMapper::toResponse);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteFile(String fileId) {
        return Mono.fromRunnable(() -> {
            log.info("Deleting (deactivating) file with ID: {}", fileId);
            File file = findFileByIdBlocking(fileId);
            file.setActive(false);
            fileRepository.save(file);
            log.info("File deactivated successfully with ID: {}", fileId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<FileResponse> activateFile(String fileId) {
        return Mono.fromCallable(() -> {
            log.info("Activating file with ID: {}", fileId);
            File file = findFileByIdBlocking(fileId);
            file.setActive(true);
            File activatedFile = fileRepository.save(file);
            log.info("File activated successfully with ID: {}", fileId);
            return fileMapper.toResponse(activatedFile);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<FileResponse> deactivateFile(String fileId) {
        return Mono.fromCallable(() -> {
            log.info("Deactivating file with ID: {}", fileId);
            File file = findFileByIdBlocking(fileId);
            file.setActive(false);
            File deactivatedFile = fileRepository.save(file);
            log.info("File deactivated successfully with ID: {}", fileId);
            return fileMapper.toResponse(deactivatedFile);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> existsByS3Key(String s3Key) {
        return Mono.fromCallable(() -> fileRepository.existsByS3Key(s3Key))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private File findFileByIdBlocking(String fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.error("File not found with ID: {}", fileId);
                    return new AppException(ErrorCode.FILE_NOT_FOUND);
                });
    }
}
