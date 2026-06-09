package com.leafy.profileservice.service.seeder;

import com.leafy.profileservice.client.FileServiceClient;
import com.leafy.profileservice.client.FileServiceClient.UploadedFileRef;
import com.leafy.profileservice.dto.response.seeder.CertificateSeedResult;
import com.leafy.profileservice.model.ApprovalRequest;
import com.leafy.profileservice.model.Certificate;
import com.leafy.profileservice.model.enums.CertificateStatus;
import com.leafy.profileservice.repository.ApprovalRequestRepository;
import com.leafy.profileservice.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class CertificateSeederServiceImpl implements CertificateSeederService {

    private static final int DEFAULT_REQUEST_COUNT = 20;
    private static final int DEFAULT_CERTS_PER_REQUEST = 2;

    private final ProfileRepository profileRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final FileServiceClient fileServiceClient;

    // ── Seed data pools ──────────────────────────────────────────────────────

    private static final String[] CERT_TITLES = {
        "Chứng chỉ Nông nghiệp Hữu cơ",
        "Bằng Kỹ thuật Canh tác Bền vững",
        "Chứng nhận VietGAP",
        "Chứng chỉ GlobalG.A.P.",
        "Bằng Quản lý Dịch hại Tổng hợp (IPM)",
        "Chứng chỉ Nuôi trồng Thủy sản Sinh thái",
        "Chứng nhận Hệ thống Tưới tiết kiệm nước",
        "Chứng chỉ Phân tích Đất và Dinh dưỡng cây trồng",
        "Bằng Trồng rau Thủy canh chuyên nghiệp",
        "Chứng chỉ Phòng chống Dịch bệnh cây trồng",
        "Chứng nhận Sản xuất Cà phê Đặc sản",
        "Bằng Kỹ thuật Ghép cây Nâng cao",
        "Chứng chỉ Quản lý Trang trại Thông minh",
        "Chứng nhận An toàn Thực phẩm (HACCP)",
        "Bằng Chuyên gia Bệnh học Thực vật",
    };

    private static final String[] ISSUERS = {
        "Bộ Nông nghiệp và Phát triển Nông thôn",
        "Trung tâm Khuyến nông Quốc gia",
        "Đại học Nông nghiệp Hà Nội",
        "Đại học Nông Lâm TP. Hồ Chí Minh",
        "Viện Khoa học Nông nghiệp Việt Nam",
        "Tổ chức FAO Việt Nam",
        "Trung tâm Kiểm định Chất lượng Nông sản",
        "Hiệp hội Nông nghiệp Hữu cơ Việt Nam",
        "GlobalG.A.P. Certification Body",
        "VIETCERT – Tổ chức Chứng nhận Việt Nam",
    };

    private static final String[] SPECIALTIES = {
        "Bệnh học thực vật",
        "Kỹ thuật canh tác lúa",
        "Nông nghiệp hữu cơ",
        "Quản lý dịch hại tổng hợp (IPM)",
        "Dinh dưỡng và cải tạo đất",
        "Trồng rau thủy canh",
        "Cây ăn quả nhiệt đới",
        "Sản xuất cà phê và cây công nghiệp",
        "Nuôi trồng thủy sản sinh thái",
        "Kỹ thuật tưới tiết kiệm nước",
        "Chế biến và bảo quản nông sản",
        "Nông nghiệp thông minh và công nghệ cao",
    };

    // ── Implementation ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public CertificateSeedResult reseed(Integer requestCount, Integer certsPerRequest) {
        int effectiveRequestCount = requestCount != null ? requestCount : DEFAULT_REQUEST_COUNT;
        int effectiveCertsPerRequest = certsPerRequest != null ? certsPerRequest : DEFAULT_CERTS_PER_REQUEST;

        // 0. Upload sample proof files to file-service
        UploadedFileRef[] proofRefs = uploadSampleProofFiles();

        // 1. Delete all existing PENDING approval requests
        List<ApprovalRequest> pendingRequests = approvalRequestRepository.findByStatus(CertificateStatus.PENDING);
        int deletedCount = pendingRequests.size();
        approvalRequestRepository.deleteAll(pendingRequests);
        log.info("Certificate seeder: deleted {} existing PENDING approval requests", deletedCount);

        // 2. Load all profile IDs
        List<String> allProfileIds = profileRepository.findAll()
                .stream()
                .map(p -> p.getId())
                .toList();

        if (allProfileIds.isEmpty()) {
            log.warn("Certificate seeder: no profiles found — nothing to seed");
            return CertificateSeedResult.builder()
                    .deletedPendingCount(deletedCount)
                    .seededRequestCount(0)
                    .seededCertificateCount(0)
                    .sourceProfileCount(0)
                    .build();
        }

        // 3. Distribute requests across profiles (cycling if requestCount > profiles)
        Random rng = new Random();
        List<String> shuffledIds = new ArrayList<>(allProfileIds);
        Collections.shuffle(shuffledIds, rng);

        int actualRequests = Math.min(effectiveRequestCount, allProfileIds.size() * 3); // cap at 3 per profile
        List<ApprovalRequest> toSave = new ArrayList<>(actualRequests);
        int totalCerts = 0;

        for (int i = 0; i < actualRequests; i++) {
            String profileId = shuffledIds.get(i % shuffledIds.size());
            List<Certificate> certs = buildCertificates(effectiveCertsPerRequest, rng, proofRefs);
            totalCerts += certs.size();

            toSave.add(ApprovalRequest.builder()
                    .profileId(profileId)
                    .certificates(certs)
                    .status(CertificateStatus.PENDING)
                    .proposedSpecialty(SPECIALTIES[rng.nextInt(SPECIALTIES.length)])
                    .build());
        }

        approvalRequestRepository.saveAll(toSave);

        // Distinct profiles used
        long distinctProfiles = toSave.stream()
                .map(ApprovalRequest::getProfileId)
                .distinct()
                .count();

        log.info("Certificate seeder: created {} approval requests ({} certs total) across {} profiles",
                toSave.size(), totalCerts, distinctProfiles);

        return CertificateSeedResult.builder()
                .deletedPendingCount(deletedCount)
                .seededRequestCount(toSave.size())
                .seededCertificateCount(totalCerts)
                .sourceProfileCount((int) distinctProfiles)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Uploads the two bundled sample proof files to file-service.
     * Falls back to a null ref if file-service is unavailable (so seeding still works
     * without file-service running).
     *
     * @return array of two UploadedFileRef: [0]=PDF, [1]=DOCX
     */
    private UploadedFileRef[] uploadSampleProofFiles() {
        UploadedFileRef pdfRef = uploadClasspathFile(
                "sample-proof/sample-proof.pdf", "sample-certificate.pdf", "application/pdf");
        UploadedFileRef docRef = uploadClasspathFile(
                "sample-proof/sample-proof.docx", "sample-certificate.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return new UploadedFileRef[]{ pdfRef, docRef };
    }

    private UploadedFileRef uploadClasspathFile(String classpathPath, String filename, String mimeType) {
        try {
            byte[] bytes = new ClassPathResource(classpathPath).getInputStream().readAllBytes();
            return fileServiceClient.uploadInternal(filename, bytes, mimeType);
        } catch (Exception e) {
            log.warn("Certificate seeder: could not upload '{}' to file-service ({}). " +
                    "Seeding will continue with proofFileId=null.", classpathPath, e.getMessage());
            return null;
        }
    }

    private List<Certificate> buildCertificates(int count, Random rng, UploadedFileRef[] proofRefs) {
        List<Certificate> certs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String title = CERT_TITLES[rng.nextInt(CERT_TITLES.length)];
            String issuer = ISSUERS[rng.nextInt(ISSUERS.length)];

            // Alternate between PDF and DOCX proof files
            UploadedFileRef ref = (proofRefs != null && proofRefs.length > 0)
                    ? proofRefs[i % proofRefs.length]
                    : null;

            String proofFileId = ref != null ? ref.fileId() : null;
            String fileType    = ref != null ? ref.fileType() : null;
            String proofUrl    = ref != null ? "/api/files/download/" + ref.fileId() : null;

            // Random issue date between 2020-01-01 and 2025-12-31
            LocalDate issueDate = LocalDate.of(2020, 1, 1)
                    .plusDays(rng.nextInt(365 * 5 + 365));

            certs.add(Certificate.builder()
                    .title(title)
                    .issuedBy(issuer)
                    .proofUrl(proofUrl)
                    .proofFileId(proofFileId)
                    .fileType(fileType)
                    .issueDate(issueDate)
                    .expired(false)
                    .build());
        }
        return certs;
    }
}
