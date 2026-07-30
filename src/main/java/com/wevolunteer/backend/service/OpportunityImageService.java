package com.wevolunteer.backend.service;

import com.wevolunteer.backend.config.S3ProfileImageProperties;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlResponse;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Issues short-lived pre-signed URLs for uploading opportunity images straight to S3.
 *
 * <p>The browser uploads directly to S3; image bytes never pass through this application. The
 * object key is always derived from the authenticated organization's Cognito subject, never from
 * the request, so an organization can only write inside its own prefix.
 */
@Service
public class OpportunityImageService {

    /** Content types accepted for upload, mapped to the file extension used in the object key. */
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    /** Listed explicitly so the error message has a stable order. */
    private static final String ALLOWED_CONTENT_TYPES_MESSAGE =
            "image/jpeg, image/png, image/webp";

    private final S3Presigner s3Presigner;
    private final S3ProfileImageProperties properties;

    public OpportunityImageService(
            S3Presigner s3Presigner,
            S3ProfileImageProperties properties) {

        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public OpportunityImageUploadUrlResponse createUploadUrl(
            String organizationId,
            String contentType) {

        String normalizedContentType = normalize(contentType);
        String extension = ALLOWED_CONTENT_TYPES.get(normalizedContentType);

        if (extension == null) {
            throw new IllegalArgumentException(
                    "Unsupported image type. Allowed types are: "
                            + ALLOWED_CONTENT_TYPES_MESSAGE + ".");
        }

        String objectKey = buildObjectKey(organizationId, extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.profileImagesBucket())
                .key(objectKey)
                .contentType(normalizedContentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlDuration())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new OpportunityImageUploadUrlResponse(
                objectKey,
                presignedRequest.url().toString(),
                properties.uploadUrlDuration().toSeconds()
        );
    }

    /**
     * Builds {@code organizations/<cognito-sub>/opportunities/<uuid>.<ext>}.
     *
     * <p>A random UUID rather than the uploaded file name: two uploads called "photo.jpg" would
     * otherwise overwrite one another, and user-supplied names may contain characters that are
     * unsafe in an object key.
     */
    private String buildObjectKey(String organizationId, String extension) {
        return "organizations/" + organizationId
                + "/opportunities/" + UUID.randomUUID()
                + "." + extension;
    }

    /**
     * Lower-cases the media type and drops any parameters, so that "IMAGE/JPEG" and
     * "image/jpeg; charset=utf-8" are both recognised.
     */
    private String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }

        int parameterStart = contentType.indexOf(';');
        String mediaType = parameterStart >= 0
                ? contentType.substring(0, parameterStart)
                : contentType;

        return mediaType.trim().toLowerCase(Locale.ROOT);
    }
}
