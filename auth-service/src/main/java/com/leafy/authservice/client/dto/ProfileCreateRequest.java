package com.leafy.authservice.client.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCreateRequest {
    private String userId;
    private String fullName;
    private String profilePicture;
    private String avatar;
    private String role;
    private String specialty;
    private String bio;
    private String addressLine;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private Double latitude;
    private Double longitude;
}
