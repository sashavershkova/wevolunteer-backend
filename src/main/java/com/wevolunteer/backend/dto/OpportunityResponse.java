package com.wevolunteer.backend.dto;

import com.wevolunteer.backend.model.Opportunity;

import java.util.List;

/**
 * API representation of an opportunity.
 *
 * <p>Carries {@code imageUrl}, a temporary pre-signed link the browser can put straight into an
 * img element, and deliberately omits {@code imageKey}. The key is the durable value stored in
 * DynamoDB; the URL is generated per response and expires. Exposing the key would leak the bucket
 * layout and invite clients to send keys back to the server.
 *
 * <p>{@code imageUrl} is {@code null} when the opportunity has no image, and the frontend shows
 * its placeholder.
 */
public record OpportunityResponse(
        String opportunityId,
        String title,
        String description,
        String category,
        String location,
        String date,
        String status,
        String organizationId,
        String organizationName,
        int capacity,
        int registeredCount,
        int availableSpots,
        String time,
        String startTime,
        String endTime,
        List<String> whatYoullDo,
        boolean recurring,
        String imageUrl
) {

    public static OpportunityResponse from(Opportunity opportunity, String imageUrl) {
        return new OpportunityResponse(
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
                imageUrl
        );
    }
}
