package com.leafy.profileservice.mapper;

import com.leafy.profileservice.dto.request.profile.ProfileCreateRequest;
import com.leafy.profileservice.dto.request.profile.ProfileUpdateRequest;
import com.leafy.profileservice.dto.response.profile.ProfileDetailsResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.model.Profile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper interface for Profile entity and DTOs
 * Uses MapStruct for automatic mapping generation
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProfileMapper {

    /**
     * Map ProfileCreateRequest to Profile entity
     *
     * @param request the create request
     * @return the profile entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isVerified", ignore = true)
    Profile toEntity(ProfileCreateRequest request);

    /**
     * Map Profile entity to ProfileResponse
     *
     * @param profile the profile entity
     * @return the profile response
     */
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "certificates", ignore = true)
    ProfileResponse toResponse(Profile profile);

    /**
     * Map Profile entity to ProfileDetailsResponse
     *
     * @param profile the profile entity
     * @return the profile details response
     */
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "certificates", ignore = true)
    ProfileDetailsResponse toDetailsResponse(Profile profile);

    /**
     * Map list of Profile entities to list of ProfileResponse
     *
     * @param profiles the list of profile entities
     * @return the list of profile responses
     */
    List<ProfileResponse> toResponseList(List<Profile> profiles);

    /**
     * Update existing Profile entity from ProfileUpdateRequest
     *
     * @param request the update request
     * @param profile the profile entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "fullName", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "isVerified", ignore = true)
    void updateEntityFromRequest(ProfileUpdateRequest request, @MappingTarget Profile profile);
}
