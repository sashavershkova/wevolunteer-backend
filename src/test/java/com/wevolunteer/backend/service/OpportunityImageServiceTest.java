package com.wevolunteer.backend.service;

import com.wevolunteer.backend.config.S3ProfileImageProperties;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlResponse;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.OpportunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpportunityImageService")
class OpportunityImageServiceTest {

    private static final String ORG_ID = "9ea34132-aaaa-bbbb-cccc-000000000001";
    private static final String BUCKET = "wevolunteer-files-images-test";
    private static final Duration UPLOAD_DURATION = Duration.ofMinutes(15);

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedRequest;

    @Mock
    private S3Client s3Client;

    @Mock
    private OpportunityService opportunityService;

    @Mock
    private OpportunityRepository opportunityRepository;

    private OpportunityImageService service;

    @BeforeEach
    void setUp() throws Exception {
        when(presignedRequest.url())
                .thenReturn(URI.create("https://example-bucket.s3.amazonaws.com/signed").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        service = new OpportunityImageService(
                s3Presigner,
                s3Client,
                new S3ProfileImageProperties(BUCKET, UPLOAD_DURATION, Duration.ofMinutes(60)),
                opportunityService,
                opportunityRepository);
    }

    @Nested
    @DisplayName("object key")
    class ObjectKey {

        @Test
        @DisplayName("is scoped to the authenticated organization's own prefix")
        void isScopedToOrganizationPrefix() {
            OpportunityImageUploadUrlResponse response =
                    service.createUploadUrl(ORG_ID, "image/jpeg");

            assertThat(response.objectKey())
                    .startsWith("organizations/" + ORG_ID + "/opportunities/");
        }

        @Test
        @DisplayName("uses a random UUID rather than any client-supplied name")
        void usesRandomUuid() {
            OpportunityImageUploadUrlResponse response =
                    service.createUploadUrl(ORG_ID, "image/png");

            assertThat(response.objectKey()).matches(
                    "organizations/" + ORG_ID + "/opportunities/" + UUID_PATTERN + "\\.png");
        }

        @Test
        @DisplayName("differs between calls so two uploads cannot overwrite each other")
        void isUniquePerCall() {
            String first = service.createUploadUrl(ORG_ID, "image/jpeg").objectKey();
            String second = service.createUploadUrl(ORG_ID, "image/jpeg").objectKey();

            assertThat(first).isNotEqualTo(second);
        }

        @ParameterizedTest(name = "{0} produces a .{1} key")
        @CsvSource({
                "image/jpeg, jpg",
                "image/png,  png",
                "image/webp, webp"
        })
        @DisplayName("extension is derived from the content type, not supplied by the client")
        void extensionMatchesContentType(String contentType, String expectedExtension) {
            OpportunityImageUploadUrlResponse response =
                    service.createUploadUrl(ORG_ID, contentType);

            assertThat(response.objectKey()).endsWith("." + expectedExtension);
        }
    }

    @Nested
    @DisplayName("content type validation")
    class ContentTypeValidation {

        @ParameterizedTest
        @ValueSource(strings = {"image/gif", "image/svg+xml", "application/pdf", "text/html", "image"})
        @DisplayName("rejects types outside the allow list")
        void rejectsUnsupportedTypes(String contentType) {
            assertThatThrownBy(() -> service.createUploadUrl(ORG_ID, contentType))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported image type");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("rejects a missing content type")
        void rejectsMissingContentType(String contentType) {
            assertThatThrownBy(() -> service.createUploadUrl(ORG_ID, contentType))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("never contacts S3 when the type is rejected")
        void doesNotPresignWhenRejected() {
            assertThatThrownBy(() -> service.createUploadUrl(ORG_ID, "image/gif"))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(s3Presigner);
        }

        @ParameterizedTest
        @ValueSource(strings = {"IMAGE/JPEG", "Image/Jpeg", "image/jpeg; charset=utf-8", "  image/jpeg  "})
        @DisplayName("accepts case and parameter variations of an allowed type")
        void normalizesContentType(String contentType) {
            OpportunityImageUploadUrlResponse response =
                    service.createUploadUrl(ORG_ID, contentType);

            assertThat(response.objectKey()).endsWith(".jpg");
        }
    }

    @Nested
    @DisplayName("pre-signed request")
    class PreSignedRequest {

        @Test
        @DisplayName("targets the configured bucket and the generated key")
        void targetsConfiguredBucketAndKey() {
            OpportunityImageUploadUrlResponse response =
                    service.createUploadUrl(ORG_ID, "image/jpeg");

            PutObjectPresignRequest presignRequest = capturePresignRequest();

            assertThat(presignRequest.putObjectRequest().bucket()).isEqualTo(BUCKET);
            assertThat(presignRequest.putObjectRequest().key()).isEqualTo(response.objectKey());
        }

        @Test
        @DisplayName("pins the content type, so the upload must match what was signed")
        void pinsContentType() {
            service.createUploadUrl(ORG_ID, "image/png");

            assertThat(capturePresignRequest().putObjectRequest().contentType())
                    .isEqualTo("image/png");
        }

        @Test
        @DisplayName("uses the configured upload expiry")
        void usesConfiguredExpiry() {
            service.createUploadUrl(ORG_ID, "image/jpeg");

            assertThat(capturePresignRequest().signatureDuration()).isEqualTo(UPLOAD_DURATION);
        }

        @Test
        @DisplayName("reports the expiry to the caller in seconds")
        void reportsExpiryInSeconds() {
            OpportunityImageUploadUrlResponse response =
                    service.createUploadUrl(ORG_ID, "image/jpeg");

            assertThat(response.expiresInSeconds()).isEqualTo(900L);
        }

        @Test
        @DisplayName("returns the signed URL and no AWS credentials")
        void returnsSignedUrlOnly() {
            OpportunityImageUploadUrlResponse response =
                    service.createUploadUrl(ORG_ID, "image/jpeg");

            assertThat(response.uploadUrl())
                    .isEqualTo("https://example-bucket.s3.amazonaws.com/signed");
            assertThat(response.uploadUrl()).doesNotContain("AKIA");
        }

        private PutObjectPresignRequest capturePresignRequest() {
            ArgumentCaptor<PutObjectPresignRequest> captor =
                    ArgumentCaptor.forClass(PutObjectPresignRequest.class);
            verify(s3Presigner).presignPutObject(captor.capture());

            return captor.getValue();
        }
    }

    @Nested
    @DisplayName("attachImage")
    class AttachImage {

        private static final String OPPORTUNITY_ID = "opp-1";
        private static final String OWNED_KEY =
                "organizations/9ea34132-aaaa-bbbb-cccc-000000000001/opportunities/"
                        + "b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg";

        @Test
        @DisplayName("stores the key and returns the updated opportunity")
        void storesKey() {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));
            stubHeadObject("image/jpeg", 1_000L);
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result = service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY);

            assertThat(result.imageKey()).isEqualTo(OWNED_KEY);
        }

        @Test
        @DisplayName("leaves every other field of the opportunity untouched")
        void leavesOtherFieldsUntouched() {
            Opportunity existing = opportunity(null);
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(existing);
            stubHeadObject("image/jpeg", 1_000L);
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY);

            ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
            verify(opportunityRepository).update(captor.capture());
            Opportunity saved = captor.getValue();

            assertThat(saved.title()).isEqualTo(existing.title());
            assertThat(saved.capacity()).isEqualTo(existing.capacity());
            assertThat(saved.registeredCount()).isEqualTo(existing.registeredCount());
            assertThat(saved.startTime()).isEqualTo(existing.startTime());
            assertThat(saved.endTime()).isEqualTo(existing.endTime());
            assertThat(saved.status()).isEqualTo(existing.status());
        }

        @Test
        @DisplayName("replaces an image that was already set")
        void replacesExistingImage() {
            when(opportunityService.getById(OPPORTUNITY_ID))
                    .thenReturn(opportunity("organizations/" + ORG_ID + "/opportunities/"
                            + "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.png"));
            stubHeadObject("image/jpeg", 1_000L);
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result = service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY);

            assertThat(result.imageKey()).isEqualTo(OWNED_KEY);
        }

        @Test
        @DisplayName("rejects an opportunity belonging to another organization")
        void rejectsOtherOrganizationsOpportunity() {
            when(opportunityService.getById(OPPORTUNITY_ID))
                    .thenReturn(opportunityOwnedBy("someone-else"));

            assertThatThrownBy(() -> service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(opportunityRepository);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "organizations/someone-else/opportunities/b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg",
                "organizations/9ea34132-aaaa-bbbb-cccc-000000000001/opportunities/../../secret.jpg",
                "organizations/9ea34132-aaaa-bbbb-cccc-000000000001/opportunities/not-a-uuid.jpg",
                "organizations/9ea34132-aaaa-bbbb-cccc-000000000001/opportunities/b616fc40-7856-4c19-993b-dd6a2ee2466a.svg",
                "users/9ea34132-aaaa-bbbb-cccc-000000000001/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg",
                "b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg"
        })
        @DisplayName("rejects keys outside the caller's own image prefix")
        void rejectsForeignKeys(String objectKey) {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));

            assertThatThrownBy(() -> service.attachImage(ORG_ID, OPPORTUNITY_ID, objectKey))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(opportunityRepository);
        }

        @Test
        @DisplayName("does not check S3 for a key it has already rejected")
        void doesNotCheckS3ForRejectedKey() {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));

            assertThatThrownBy(() -> service.attachImage(
                    ORG_ID, OPPORTUNITY_ID, "organizations/someone-else/opportunities/x.jpg"))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(s3Client);
        }

        @Test
        @DisplayName("rejects a key whose object was never uploaded")
        void rejectsMissingObject() {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("missing").build());

            assertThatThrownBy(() -> service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No uploaded image was found");

            verifyNoInteractions(opportunityRepository);
        }

        @Test
        @DisplayName("rejects an object whose stored content type is not an allowed image")
        void rejectsBadStoredContentType() {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));
            stubHeadObject("application/pdf", 1_000L);

            assertThatThrownBy(() -> service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a supported image");

            verifyNoInteractions(opportunityRepository);
        }

        @Test
        @DisplayName("rejects an object larger than 5 MB, which the pre-signed PUT could not prevent")
        void rejectsOversizedObject() {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));
            stubHeadObject("image/jpeg", 5L * 1024 * 1024 + 1);

            assertThatThrownBy(() -> service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("5 MB");

            verifyNoInteractions(opportunityRepository);
        }

        @Test
        @DisplayName("accepts an object exactly at the 5 MB limit")
        void acceptsObjectAtLimit() {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));
            stubHeadObject("image/jpeg", 5L * 1024 * 1024);
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            assertThat(service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY).imageKey())
                    .isEqualTo(OWNED_KEY);
        }

        @Test
        @DisplayName("propagates not-found for an opportunity that does not exist")
        void propagatesNotFound() {
            when(opportunityService.getById(OPPORTUNITY_ID))
                    .thenThrow(new NotFoundException("Opportunity not found: " + OPPORTUNITY_ID));

            assertThatThrownBy(() -> service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY))
                    .isInstanceOf(NotFoundException.class);

            verifyNoInteractions(s3Client, opportunityRepository);
        }

        @Test
        @DisplayName("checks the configured bucket when confirming the upload")
        void checksConfiguredBucket() {
            when(opportunityService.getById(OPPORTUNITY_ID)).thenReturn(opportunity(null));
            stubHeadObject("image/jpeg", 1_000L);
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            service.attachImage(ORG_ID, OPPORTUNITY_ID, OWNED_KEY);

            ArgumentCaptor<HeadObjectRequest> captor =
                    ArgumentCaptor.forClass(HeadObjectRequest.class);
            verify(s3Client).headObject(captor.capture());

            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo(OWNED_KEY);
        }

        private void stubHeadObject(String contentType, long contentLength) {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder()
                            .contentType(contentType)
                            .contentLength(contentLength)
                            .build());
        }

        private Opportunity opportunity(String imageKey) {
            return new Opportunity(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", "OPEN", ORG_ID, "Green Earth",
                    10, 3, 7, null, "09:00", "13:00",
                    List.of("Sort donations"), false, imageKey);
        }

        private Opportunity opportunityOwnedBy(String organizationId) {
            return new Opportunity(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", "OPEN", organizationId, "Other Org",
                    10, 3, 7, null, "09:00", "13:00",
                    List.of("Sort donations"), false, null);
        }
    }
}
