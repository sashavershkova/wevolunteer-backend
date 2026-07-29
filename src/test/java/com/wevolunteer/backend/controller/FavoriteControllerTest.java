package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Favorite;
import com.wevolunteer.backend.service.FavoriteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteController")
class FavoriteControllerTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";

    @Mock
    private FavoriteService favoriteService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private FavoriteController favoriteController;

    @Test
    @DisplayName("getMyFavorites resolves the user from the JWT subject and delegates to the service")
    void getMyFavoritesResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        List<Favorite> expected = List.of(favorite());
        when(favoriteService.getFavoritesByUserId(USER_ID)).thenReturn(expected);

        List<Favorite> result = favoriteController.getMyFavorites(jwt);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("addMyFavorite resolves the user from the JWT subject and never accepts a client-supplied user id")
    void addMyFavoriteResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        Favorite expected = favorite();
        when(favoriteService.addFavorite(USER_ID, OPPORTUNITY_ID)).thenReturn(expected);

        Favorite result = favoriteController.addMyFavorite(jwt, OPPORTUNITY_ID);

        assertThat(result).isSameAs(expected);
        verify(favoriteService).addFavorite(USER_ID, OPPORTUNITY_ID);
        verifyNoMoreInteractions(favoriteService);
    }

    @Test
    @DisplayName("removeMyFavorite resolves the user from the JWT subject and delegates to the service")
    void removeMyFavoriteResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);

        favoriteController.removeMyFavorite(jwt, OPPORTUNITY_ID);

        verify(favoriteService).removeFavorite(USER_ID, OPPORTUNITY_ID);
        verifyNoMoreInteractions(favoriteService);
    }

    private static Favorite favorite() {
        return new Favorite(
                USER_ID,
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                "org-1",
                "Green Earth",
                "2026-07-29T10:00:00");
    }
}
