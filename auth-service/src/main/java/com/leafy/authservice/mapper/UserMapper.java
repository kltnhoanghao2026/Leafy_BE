package com.leafy.authservice.mapper;

import com.leafy.authservice.dto.request.UserCreateRequest;
import com.leafy.authservice.dto.request.UserUpdateRequest;
import com.leafy.authservice.dto.response.UserDetailsResponse;
import com.leafy.authservice.dto.response.UserResponse;
import com.leafy.authservice.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    /**
     * Map UserCreateRequest to User entity
     *
     * @param request the create request
     * @return the user entity
     */
    User toEntity(UserCreateRequest request);

    /**
     * Map User entity to UserResponse
     *
     * @param user the user entity
     * @return the user response
     */
    UserResponse toResponse(User user);

    /**
     * Map User entity to UserDetailsResponse
     *
     * @param user the user entity
     * @return the user details response
     */
    UserDetailsResponse toDetailsResponse(User user);

    /**
     * Map list of User entities to list of UserResponse
     *
     * @param users the list of user entities
     * @return the list of user responses
     */
    List<UserResponse> toResponseList(List<User> users);

    /**
     * Update existing User entity from UserUpdateRequest
     *
     * @param request the update request
     * @param user    the user entity to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updateEntityFromRequest(UserUpdateRequest request, @MappingTarget User user);
}
