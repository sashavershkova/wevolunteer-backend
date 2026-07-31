package com.wevolunteer.backend.service;

import com.wevolunteer.backend.config.S3ProfileImageProperties;
import com.wevolunteer.backend.dto.ProfileImageUploadUrlResponse;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.OrganizationRepository;
import com.wevolunteer.backend.repository.UserRepository;
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
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProfileImageService")
class ProfileImageServiceTest {

    private static final String USER_ID = "44989448-5061-7036-08e2-bb7cee6dfaf4";
    private static final String ORG_ID = "f4b884a8-f001-70b0-2d0c-b6239835c350";
    private static final String BUCKET = "wevolunteer-files-images-test";
    private static final Duration UPLOAD_DURATION = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_DURATION = Duration.ofMinutes(60);

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final String USER_KEY =
            "users/" + USER_ID + "/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg";
    private static final String ORG_KEY =
            "organizations/" + ORG_ID + "/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.png";

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private PresignedPutObjectRequest presignedPutRequest;

    @Mock
    private PresignedGetObjectRequest presignedGetRequest;

    @Mock
    private UserService userService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private ProfileImageService service;

    @BeforeEach
    void setUp() throws Exception {
        when(presignedPutRequest.url())
                .thenReturn(URI.create("https://example-bucket.s3.amazonaws.com/upload").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutRequest);

        service = new ProfileImageService(
                s3Presigner,
                s3Client,
                new S3ProfileImageProperties(BUCKET, UPLOAD_DURATION, DOWNLOAD_DURATION),
                userService,
                organizationService,
                userRepository,
                organizationRepository);
    }

    @Nested
    @DisplayName("upload URLs")
    class UploadUrls {

        @Test
        @DisplayName("volunteer keys live under users/<sub>/profile/")
        void userKeyUsesUserPrefix() {
            ProfileImageUploadUrlResponse response =
                    service.createUserUploadUrl(USER_ID, "image/jpeg");

            assertThat(response.objectKey())
                    .matches("users/" + USER_ID + "/profile/" + UUID_PATTERN + "\\.jpg");
        }

        @Test
        @DisplayName("organization keys live under organizations/<sub>/profile/")
        void organizationKeyUsesOrganizationPrefix() {
            ProfileImageUploadUrlResponse response =
                    service.createOrganizationUploadUrl(ORG_ID, "image/png");

            assertThat(response.objectKey())
                    .matches("organizations/" + ORG_ID + "/profile/" + UUID_PATTERN + "\\.png");
        }

        @Test
        @DisplayName("a volunteer key can never collide with an organization key")
        void prefixesDoNotOverlap() {
            String userKey = service.createUserUploadUrl(USER_ID, "image/jpeg").objectKey();
            String orgKey = service.createOrganizationUploadUrl(USER_ID, "image/jpeg").objectKey();

            assertThat(userKey).startsWith("users/");
            assertThat(orgKey).startsWith("organizations/");
        }

        @ParameterizedTest(name = "{0} produces a .{1} key")
        @CsvSource({"image/jpeg, jpg", "image/png, png", "image/webp, webp"})
        @DisplayName("extension comes from the content type, not the client")
        void extensionFromContentType(String contentType, String expectedExtension) {
            assertThat(service.createUserUploadUrl(USER_ID, contentType).objectKey())
                    .endsWith("." + expectedExtension);
        }

        @ParameterizedTest
        @ValueSource(strings = {"image/gif", "image/svg+xml", "application/pdf"})
        @DisplayName("rejects unsupported types without signing anything")
        void rejectsUnsupportedTypes(String contentType) {
            assertThatThrownBy(() -> service.createUserUploadUrl(USER_ID, contentType))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported image type");

            verifyNoInteractions(s3Presigner);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("rejects a missing content type")
        void rejectsMissingContentType(String contentType) {
            assertThatThrownBy(() -> service.createUserUploadUrl(USER_ID, contentType))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pins bucket, key and content type into the signature")
        void pinsRequestDetails() {
            ProfileImageUploadUrlResponse response =
                    service.createUserUploadUrl(USER_ID, "image/jpeg");

            ArgumentCaptor<PutObjectPresignRequest> captor =
                    ArgumentCaptor.forClass(PutObjectPresignRequest.class);
            verify(s3Presigner).presignPutObject(captor.capture());

            assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().putObjectRequest().key()).isEqualTo(response.objectKey());
            assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/jpeg");
            assertThat(captor.getValue().signatureDuration()).isEqualTo(UPLOAD_DURATION);
            assertThat(response.expiresInSeconds()).isEqualTo(900L);
        }
    }

    @Nested
    @DisplayName("attachUserImage")
    class AttachUserImage {

        @Test
        @DisplayName("stores the key against the authenticated volunteer")
        void storesKey() {
            when(userService.getById(USER_ID)).thenReturn(user(null));
            stubHeadObject("image/jpeg", 1_000L);
            when(userRepository.updateProfileImageKey(USER_ID, USER_KEY))
                    .thenReturn(user(USER_KEY));

            User result = service.attachUserImage(USER_ID, USER_KEY);

            assertThat(result.profileImageKey()).isEqualTo(USER_KEY);
            verify(userRepository).updateProfileImageKey(USER_ID, USER_KEY);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "users/someone-else/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg",
                "organizations/44989448-5061-7036-08e2-bb7cee6dfaf4/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg",
                "users/44989448-5061-7036-08e2-bb7cee6dfaf4/profile/../../secret.jpg",
                "users/44989448-5061-7036-08e2-bb7cee6dfaf4/profile/not-a-uuid.jpg",
                "users/44989448-5061-7036-08e2-bb7cee6dfaf4/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.svg",
                "users/44989448-5061-7036-08e2-bb7cee6dfaf4/opportunities/b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg"
        })
        @DisplayName("rejects keys outside the caller's own profile prefix")
        void rejectsForeignKeys(String objectKey) {
            when(userService.getById(USER_ID)).thenReturn(user(null));

            assertThatThrownBy(() -> service.attachUserImage(USER_ID, objectKey))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(s3Client, userRepository);
        }

        @Test
        @DisplayName("rejects a key whose object was never uploaded")
        void rejectsMissingObject() {
            when(userService.getById(USER_ID)).thenReturn(user(null));
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("missing").build());

            assertThatThrownBy(() -> service.attachUserImage(USER_ID, USER_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No uploaded image was found");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("rejects an object larger than 5 MB")
        void rejectsOversizedObject() {
            when(userService.getById(USER_ID)).thenReturn(user(null));
            stubHeadObject("image/jpeg", 5L * 1024 * 1024 + 1);

            assertThatThrownBy(() -> service.attachUserImage(USER_ID, USER_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("5 MB");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("rejects an object whose stored type is not an image")
        void rejectsBadStoredType() {
            when(userService.getById(USER_ID)).thenReturn(user(null));
            stubHeadObject("application/pdf", 1_000L);

            assertThatThrownBy(() -> service.attachUserImage(USER_ID, USER_KEY))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("replaces an existing image")
        void replacesExistingImage() {
            when(userService.getById(USER_ID)).thenReturn(user("users/" + USER_ID + "/profile/old.jpg"));
            stubHeadObject("image/jpeg", 1_000L);
            when(userRepository.updateProfileImageKey(USER_ID, USER_KEY))
                    .thenReturn(user(USER_KEY));

            assertThat(service.attachUserImage(USER_ID, USER_KEY).profileImageKey())
                    .isEqualTo(USER_KEY);
        }

        @Test
        @DisplayName("propagates not-found for a profile that does not exist")
        void propagatesNotFound() {
            when(userService.getById(USER_ID))
                    .thenThrow(new NotFoundException("User not found: " + USER_ID));

            assertThatThrownBy(() -> service.attachUserImage(USER_ID, USER_KEY))
                    .isInstanceOf(NotFoundException.class);

            verifyNoInteractions(s3Client, userRepository);
        }
    }

    @Nested
    @DisplayName("attachOrganizationImage")
    class AttachOrganizationImage {

        @Test
        @DisplayName("stores the key against the authenticated organization")
        void storesKey() {
            when(organizationService.getById(ORG_ID)).thenReturn(organization(null));
            stubHeadObject("image/png", 1_000L);
            when(organizationRepository.updateProfileImageKey(ORG_ID, ORG_KEY))
                    .thenReturn(organization(ORG_KEY));

            Organization result = service.attachOrganizationImage(ORG_ID, ORG_KEY);

            assertThat(result.profileImageKey()).isEqualTo(ORG_KEY);
        }

        @Test
        @DisplayName("rejects a volunteer-prefixed key")
        void rejectsUserPrefixedKey() {
            when(organizationService.getById(ORG_ID)).thenReturn(organization(null));

            assertThatThrownBy(() -> service.attachOrganizationImage(
                    ORG_ID, "users/" + ORG_ID + "/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.png"))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(organizationRepository);
        }

        @Test
        @DisplayName("rejects another organization's key")
        void rejectsOtherOrganizationKey() {
            when(organizationService.getById(ORG_ID)).thenReturn(organization(null));

            assertThatThrownBy(() -> service.attachOrganizationImage(
                    ORG_ID, "organizations/other/profile/b616fc40-7856-4c19-993b-dd6a2ee2466a.png"))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(organizationRepository);
        }
    }

    @Nested
    @DisplayName("resolveImageUrl")
    class ResolveImageUrl {

        @Test
        @DisplayName("returns null for a profile with no image, signing nothing")
        void returnsNullWhenNoKey() {
            assertThat(service.resolveImageUrl(null)).isNull();
            assertThat(service.resolveImageUrl("  ")).isNull();

            verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
        }

        @Test
        @DisplayName("signs a display URL with the longer download expiry")
        void signsDisplayUrl() throws Exception {
            when(presignedGetRequest.url())
                    .thenReturn(URI.create(
                            "https://example-bucket.s3.amazonaws.com/display").toURL());
            when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                    .thenReturn(presignedGetRequest);

            String url = service.resolveImageUrl(USER_KEY);

            assertThat(url).isEqualTo("https://example-bucket.s3.amazonaws.com/display");

            ArgumentCaptor<GetObjectPresignRequest> captor =
                    ArgumentCaptor.forClass(GetObjectPresignRequest.class);
            verify(s3Presigner).presignGetObject(captor.capture());

            assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().getObjectRequest().key()).isEqualTo(USER_KEY);
            assertThat(captor.getValue().signatureDuration()).isEqualTo(DOWNLOAD_DURATION);
            assertThat(captor.getValue().signatureDuration()).isNotEqualTo(UPLOAD_DURATION);
        }
    }

    private void stubHeadObject(String contentType, long contentLength) {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build());
    }

    private static User user(String profileImageKey) {
        return new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER",
                profileImageKey);
    }

    private static Organization organization(String profileImageKey) {
        return new Organization(ORG_ID, "Green Bean", "We grow trees",
                "info@greenbean.org", "https://greenbean.org", profileImageKey);
    }
}
