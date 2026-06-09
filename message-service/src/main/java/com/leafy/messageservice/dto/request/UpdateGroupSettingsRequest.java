package com.leafy.messageservice.dto.request;

public record UpdateGroupSettingsRequest(
        Boolean memberCanChangeInfo,
        Boolean memberCanPinMessages,
        Boolean memberCanSendMessages,
        Boolean membershipApprovalEnabled,
        Boolean joinByLinkEnabled
) {}
