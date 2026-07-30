package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.OpportunityResponse;
import com.wevolunteer.backend.model.Opportunity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns opportunities into API responses, resolving a temporary display URL for any that have an
 * image.
 *
 * <p>Kept out of the controllers so the pre-signing rule lives in one place, and out of
 * {@link OpportunityService} so internal reads — closing, updating, attaching an image — do not
 * pay to sign URLs that nobody displays.
 */
@Component
public class OpportunityResponseMapper {

    private final OpportunityImageService opportunityImageService;

    public OpportunityResponseMapper(OpportunityImageService opportunityImageService) {
        this.opportunityImageService = opportunityImageService;
    }

    public OpportunityResponse toResponse(Opportunity opportunity) {
        return OpportunityResponse.from(
                opportunity,
                opportunityImageService.resolveImageUrl(opportunity.imageKey())
        );
    }

    public List<OpportunityResponse> toResponses(List<Opportunity> opportunities) {
        return opportunities.stream()
                .map(this::toResponse)
                .toList();
    }
}
