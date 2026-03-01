package com.leafy.fileservice.mapper;

import com.leafy.fileservice.dto.request.FileUpdateRequest;
import com.leafy.fileservice.dto.request.FileUploadRequest;
import com.leafy.fileservice.dto.response.FileDetailsResponse;
import com.leafy.fileservice.dto.response.FileResponse;
import com.leafy.fileservice.model.File;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FileMapper {

    /**
     * Map FileUploadRequest to File entity
     *
     * @param request the upload request
     * @return the file entity
     */
    @Mapping(target = "id", ignore = true)
    File toEntity(FileUploadRequest request);

    /**
     * Map File entity to FileResponse
     *
     * @param file the file entity
     * @return the file response
     */
    FileResponse toResponse(File file);

    /**
     * Map File entity to FileDetailsResponse
     *
     * @param file the file entity
     * @return the file details response
     */
    FileDetailsResponse toDetailsResponse(File file);

    /**
     * Map list of File entities to list of FileResponse
     *
     * @param files the list of file entities
     * @return the list of file responses
     */
    List<FileResponse> toResponseList(List<File> files);

    /**
     * Update existing File entity from FileUpdateRequest
     *
     * @param request the update request
     * @param file    the file entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "s3Key", ignore = true)
    @Mapping(target = "contentType", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "uploadedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntityFromRequest(FileUpdateRequest request, @MappingTarget File file);
}
