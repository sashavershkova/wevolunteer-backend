package com.wevolunteer.backend.service;

import com.wevolunteer.backend.config.S3ProfileImageProperties;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlResponse;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

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

    private OpportunityImageService service;

    @BeforeEach
    void setUp() throws Exception {
        when(presignedRequest.url())
                .thenReturn(URI.create("https://example-bucket.s3.amazonaws.com/signed").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        service = new OpportunityImageService(
                s3Presigner,
                new S3ProfileImageProperties(BUCKET, UPLOAD_DURATION, Duration.ofMinutes(60)));
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
}
