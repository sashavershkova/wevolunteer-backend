package com.wevolunteer.backend.service;

import com.wevolunteer.backend.config.S3ProfileImageProperties;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlResponse;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.OpportunityRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Issues short-lived pre-signed URLs for uploading opportunity images straight to S3.
 *
 * <p>The browser uploads directly to S3; image bytes never pass through this application. The
 * object key is always derived from the authenticated organization's Cognito subject, never from
 * the request, so an organization can only write inside its own prefix.
 */
@Service
public class OpportunityImageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3ProfileImageProperties properties;
    private final OpportunityService opportunityService;
    private final OpportunityRepository opportunityRepository;

    public OpportunityImageService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            S3ProfileImageProperties properties,
            OpportunityService opportunityService,
            OpportunityRepository opportunityRepository) {

        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.properties = properties;
        this.opportunityService = opportunityService;
        this.opportunityRepository = opportunityRepository;
    }

    public OpportunityImageUploadUrlResponse createUploadUrl(
            String organizationId,
            String contentType) {

        String extension = ImageContentTypes.extensionForUpload(contentType);
        String normalizedContentType = ImageContentTypes.normalize(contentType);

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
     * Records an already-uploaded object as an opportunity's image.
     *
     * <p>Runs four checks before touching DynamoDB: the caller owns the opportunity, the key lies
     * inside the caller's own prefix, the object actually exists in S3, and its metadata is within
     * the accepted type and size. Only then is the opportunity updated.
     *
     * @return the opportunity as stored after the update
     */
    public Opportunity attachImage(
            String organizationId,
            String opportunityId,
            String objectKey) {

        Opportunity opportunity = opportunityService.getById(opportunityId);

        if (!opportunity.organizationId().equals(organizationId)) {
            throw new ForbiddenException(
                    "Only the organization that owns this opportunity can change its image.");
        }

        requireOwnedKey(organizationId, objectKey);

        HeadObjectResponse metadata = requireUploadedObject(objectKey);
        requireAcceptableMetadata(metadata);

        return opportunityRepository.update(withImageKey(opportunity, objectKey));
    }

    /**
     * Rejects any key the caller could not legitimately have been given.
     *
     * <p>Matching the whole key against a strict pattern — rather than only checking the prefix —
     * also rules out traversal segments and unexpected extensions.
     */
    private void requireOwnedKey(String organizationId, String objectKey) {
        String expected = "organizations/" + Pattern.quote(organizationId)
                + "/opportunities/" + ImageContentTypes.UUID_REGEX + "\\.(jpg|png|webp)";

        if (objectKey == null || !objectKey.matches(expected)) {
            throw new ForbiddenException(
                    "That image key does not belong to your organization.");
        }
    }

    private HeadObjectResponse requireUploadedObject(String objectKey) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(properties.profileImagesBucket())
                .key(objectKey)
                .build();

        try {
            return s3Client.headObject(request);
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
    }

    private void requireAcceptableMetadata(HeadObjectResponse metadata) {
        ImageContentTypes.requireAllowedStoredType(metadata.contentType());
        ImageContentTypes.requireWithinSizeLimit(metadata.contentLength());
    }

    private Opportunity withImageKey(Opportunity opportunity, String imageKey) {
        return new Opportunity(
                opportunity.opportunityId(),
                opportunity.title(),
                opportunity.description(),
                opportunity.category(),
                opportunity.location(),
                opportunity.date(),
                opportunity.status(),
                opportunity.organizationId(),
                opportunity.organizationName(),
                opportunity.capacity(),
                opportunity.registeredCount(),
                opportunity.availableSpots(),
                opportunity.time(),
                opportunity.startTime(),
                opportunity.endTime(),
                opportunity.whatYoullDo(),
                opportunity.recurring(),
                imageKey
        );
    }

    /**
     * Generates a temporary link the browser can render directly.
     *
     * <p>The bucket stays private: the URL carries a signature that expires, and nothing is
     * written back to DynamoDB. The stored key remains the durable value.
     *
     * @param imageKey the stored S3 object key, or {@code null} when the record has no image
     * @return a pre-signed URL, or {@code null} when there is no image to link to
     */
    public String resolveImageUrl(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.profileImagesBucket())
                .key(imageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadUrlDuration())
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest =
                s3Presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toString();
    }
}
