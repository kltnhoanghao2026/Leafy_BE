package com.leafy.profileservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.profileservice.model.enums.CertificateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.ArrayList;
import java.util.List;

/**
 * Approval Request model
 * A batch submission of certificates awaiting admin review
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "approval_requests")
public class ApprovalRequest extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    private String profileId;

    @Builder.Default
    private List<Certificate> certificates = new ArrayList<>();

    @Builder.Default
    private CertificateStatus status = CertificateStatus.PENDING;

    private String rejectionReason;
}
