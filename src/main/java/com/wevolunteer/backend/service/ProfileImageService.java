package com.wevolunteer.backend.service;

import com.wevolunteer.backend.config.S3ProfileImageProperties;
import com.wevolunteer.backend.dto.ProfileImageUploadUrlResponse;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.OrganizationRepository;
import com.wevolunteer.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Profile images for volunteers and organizations.
 *
 * <p>Keys are always derived from the authenticated Cognito subject, never from the request, so a
 * caller can only ever write inside their own prefix:
 *
 * <pre>
 * users/&lt;sub&gt;/profile/&lt;uuid&gt;.jpg
 * organizations/&lt;sub&gt;/profile/&lt;uuid&gt;.png
 * </pre>
 *
 * <p>Both prefixes are already covered by the ECS task role's S3 policy.
 */
@Service
public class ProfileImageService {

    private static final String USER_PREFIX = "users";
    private static final String ORGANIZATION_PREFIX = "organizations";

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3ProfileImageProperties properties;
    private final UserService userService;
    private final OrganizationService organizationService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public ProfileImageService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            S3ProfileImageProperties properties,
            UserService userService,
            OrganizationService organizationService,
            UserRepository userRepository,
            OrganizationRepository organizationRepository) {

        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.properties = properties;
        this.userService = userService;
        this.organizationService = organizationService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
    }

    public ProfileImageUploadUrlResponse createUserUploadUrl(String userId, String contentType) {
        return createUploadUrl(USER_PREFIX, userId, contentType);
    }

    public ProfileImageUploadUrlResponse createOrganizationUploadUrl(
            String organizationId, String contentType) {

        return createUploadUrl(ORGANIZATION_PREFIX, organizationId, contentType);
    }

    /**
     * Records an already-uploaded object as the volunteer's profile image.
     *
     * <p>The previous image is left in S3. Deleting it safely means doing so only after the
     * DynamoDB write succeeds; that is tracked as separate cleanup work.
     */
    public User attachUserImage(String userId, String objectKey) {
        userService.getById(userId);

        requireOwnedKey(USER_PREFIX, userId, objectKey);
        requireUploadedImage(objectKey);

        return userRepository.updateProfileImageKey(userId, objectKey);
    }

    /** Records an already-uploaded object as the organization's profile image. */
    public Organization attachOrganizationImage(String organizationId, String objectKey) {
        organizationService.getById(organizationId);

        requireOwnedKey(ORGANIZATION_PREFIX, organizationId, objectKey);
        requireUploadedImage(objectKey);

        return organizationRepository.updateProfileImageKey(organizationId, objectKey);
    }

    /**
     * Generates a temporary link the browser can render directly.
     *
     * <p>The bucket stays private and nothing is written back to DynamoDB: the stored key remains
     * the durable value, and this URL expires.
     *
     * @return a pre-signed URL, or {@code null} when the profile has no image
     */
    public String resolveImageUrl(String profileImageKey) {
        if (profileImageKey == null || profileImageKey.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.profileImagesBucket())
                .key(profileImageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadUrlDuration())
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest =
                s3Presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toString();
    }

    private ProfileImageUploadUrlResponse createUploadUrl(
            String ownerPrefix, String ownerId, String contentType) {

        String extension = ImageContentTypes.extensionForUpload(contentType);
        String normalizedContentType = ImageContentTypes.normalize(contentType);

        String objectKey = ownerPrefix + "/" + ownerId + "/profile/"
                + UUID.randomUUID() + "." + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.profileImagesBucket())
                .key(objectKey)
                .contentType(normalizedContentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlDuration())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        return new ProfileImageUploadUrlResponse(
                objectKey,
                presignedRequest.url().toString(),
                properties.uploadUrlDuration().toSeconds()
        );
    }

    /**
     * Rejects any key the caller could not legitimately have been given.
     *
     * <p>Matching the whole key against a strict pattern — rather than only checking the prefix —
     * also rules out traversal segments and unexpected extensions.
     */
    private void requireOwnedKey(String ownerPrefix, String ownerId, String objectKey) {
        String expected = ownerPrefix + "/" + Pattern.quote(ownerId) + "/profile/"
                + ImageContentTypes.UUID_REGEX + "\\.(jpg|png|webp)";

        if (objectKey == null || !objectKey.matches(expected)) {
            throw new ForbiddenException("That image key does not belong to your profile.");
        }
    }

    private void requireUploadedImage(String objectKey) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(properties.profileImagesBucket())
                .key(objectKey)
                .build();

        HeadObjectResponse metadata;

        try {
            metadata = s3Client.headObject(request);
        } catch (NoSuchKeyException e) {
            throw new IllegalArgumentException(
                    "No uploaded image was found for that key. Upload the file before "
                            + "attaching it.");
        } catch (S3Exception e) {
            // A HEAD response carries no body, so the SDK cannot always decode the error into
            // NoSuchKeyException; a bare 404 means the same thing.
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException(
                        "No uploaded image was found for that key. Upload the file before "
                                + "attaching it.");
            }

            throw e;
        }

        ImageContentTypes.requireAllowedStoredType(metadata.contentType());
        ImageContentTypes.requireWithinSizeLimit(metadata.contentLength());
    }
}
