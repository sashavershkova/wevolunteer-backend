package com.wevolunteer.backend.service;

import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Favorite;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.FavoriteRepository;
import com.wevolunteer.backend.repository.OpportunityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteService")
class FavoriteServiceTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";
    private static final String ORG_ID = "org-1";
    private static final String ORG_NAME = "Green Earth";

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private OpportunityRepository opportunityRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    @Nested
    @DisplayName("getFavoritesByUserId")
    class GetFavoritesByUserId {

        @Test
        @DisplayName("delegates to the repository")
        void delegatesToRepository() {
            List<Favorite> expected = List.of(favorite());
            when(favoriteRepository.findByUserId(USER_ID)).thenReturn(expected);

            assertThat(favoriteService.getFavoritesByUserId(USER_ID)).isSameAs(expected);
        }

        @Test
        @DisplayName("returns the empty list the repository returns without substituting null")
        void returnsEmptyList() {
            when(favoriteRepository.findByUserId(USER_ID)).thenReturn(List.of());

            assertThat(favoriteService.getFavoritesByUserId(USER_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("addFavorite")
    class AddFavorite {

        @Test
        @DisplayName("snapshots the current opportunity details onto the saved favorite")
        void savesSnapshotOfOpportunity() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity()));

            Favorite result = favoriteService.addFavorite(USER_ID, OPPORTUNITY_ID);

            ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
            verify(favoriteRepository).save(captor.capture());

            assertThat(captor.getValue()).isEqualTo(result);
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(result.title()).isEqualTo("Beach Cleanup");
            assertThat(result.date()).isEqualTo("2026-08-01");
            assertThat(result.location()).isEqualTo("Seattle, WA");
            assertThat(result.organizationId()).isEqualTo(ORG_ID);
            assertThat(result.organizationName()).isEqualTo(ORG_NAME);
            assertThat(result.favoritedAt()).isNotBlank();
        }

        @Test
        @DisplayName("throws NotFoundException and never saves when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.addFavorite(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verify(favoriteRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("removeFavorite")
    class RemoveFavorite {

        @Test
        @DisplayName("delegates to the repository without checking existence first")
        void delegatesToRepository() {
            favoriteService.removeFavorite(USER_ID, OPPORTUNITY_ID);

            verify(favoriteRepository).deleteByUserIdAndOpportunityId(USER_ID, OPPORTUNITY_ID);
        }
    }

    private static Opportunity opportunity() {
        return new Opportunity(
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "Pick up litter",
                "ENVIRONMENT",
                "Seattle, WA",
                "2026-08-01",
                "OPEN",
                ORG_ID,
                ORG_NAME,
                10,
                3,
                7,
                null,
                "09:00",
                "12:00",
                List.of("Sort and organize donations", "Help set up the distribution area"),
                false);
    }

    private static Favorite favorite() {
        return new Favorite(
                USER_ID,
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                ORG_ID,
                ORG_NAME,
                "2026-07-29T10:00:00");
    }
}
