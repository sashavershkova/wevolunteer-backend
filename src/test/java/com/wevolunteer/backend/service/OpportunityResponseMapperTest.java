package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.OpportunityResponse;
import com.wevolunteer.backend.model.Opportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityResponseMapper")
class OpportunityResponseMapperTest {

    private static final String IMAGE_KEY =
            "organizations/org-1/opportunities/b616fc40-7856-4c19-993b-dd6a2ee2466a.jpg";
    private static final String SIGNED_URL = "https://bucket.s3.amazonaws.com/signed";

    @Mock
    private OpportunityImageService opportunityImageService;

    @InjectMocks
    private OpportunityResponseMapper mapper;

    @Test
    @DisplayName("resolves a display URL for an opportunity that has an image")
    void resolvesUrlWhenImagePresent() {
        when(opportunityImageService.resolveImageUrl(IMAGE_KEY)).thenReturn(SIGNED_URL);

        OpportunityResponse response = mapper.toResponse(opportunity(IMAGE_KEY));

        assertThat(response.imageUrl()).isEqualTo(SIGNED_URL);
    }

    @Test
    @DisplayName("returns a null URL for an opportunity with no image, so the placeholder shows")
    void returnsNullUrlWhenNoImage() {
        when(opportunityImageService.resolveImageUrl(null)).thenReturn(null);

        OpportunityResponse response = mapper.toResponse(opportunity(null));

        assertThat(response.imageUrl()).isNull();
    }

    @Test
    @DisplayName("copies every other opportunity field unchanged")
    void copiesOtherFields() {
        when(opportunityImageService.resolveImageUrl(IMAGE_KEY)).thenReturn(SIGNED_URL);
        Opportunity opportunity = opportunity(IMAGE_KEY);

        OpportunityResponse response = mapper.toResponse(opportunity);

        assertThat(response.opportunityId()).isEqualTo(opportunity.opportunityId());
        assertThat(response.title()).isEqualTo(opportunity.title());
        assertThat(response.capacity()).isEqualTo(opportunity.capacity());
        assertThat(response.registeredCount()).isEqualTo(opportunity.registeredCount());
        assertThat(response.availableSpots()).isEqualTo(opportunity.availableSpots());
        assertThat(response.startTime()).isEqualTo(opportunity.startTime());
        assertThat(response.endTime()).isEqualTo(opportunity.endTime());
        assertThat(response.whatYoullDo()).isEqualTo(opportunity.whatYoullDo());
        assertThat(response.recurring()).isEqualTo(opportunity.recurring());
    }

    @Test
    @DisplayName("maps a list, signing one URL per opportunity")
    void mapsList() {
        when(opportunityImageService.resolveImageUrl(IMAGE_KEY)).thenReturn(SIGNED_URL);
        when(opportunityImageService.resolveImageUrl(null)).thenReturn(null);

        List<OpportunityResponse> responses = mapper.toResponses(
                List.of(opportunity(IMAGE_KEY), opportunity(null)));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).imageUrl()).isEqualTo(SIGNED_URL);
        assertThat(responses.get(1).imageUrl()).isNull();
    }

    @Test
    @DisplayName("maps an empty list without touching the presigner")
    void mapsEmptyList() {
        assertThat(mapper.toResponses(List.of())).isEmpty();

        verifyNoInteractions(opportunityImageService);
    }

    private static Opportunity opportunity(String imageKey) {
        return new Opportunity(
                "opp-1", "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                "Seattle, WA", "2026-08-01", "OPEN", "org-1", "Green Earth",
                10, 3, 7, null, "09:00", "13:00",
                List.of("Sort donations"), false, imageKey);
    }
}
