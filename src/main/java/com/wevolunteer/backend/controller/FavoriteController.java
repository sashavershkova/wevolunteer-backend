package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Favorite;
import com.wevolunteer.backend.service.FavoriteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/favorites/me")
    public List<Favorite> getMyFavorites(@AuthenticationPrincipal Jwt jwt) {
        return favoriteService.getFavoritesByUserId(jwt.getSubject());
    }

    @PostMapping("/favorites/me/{opportunityId}")
    public Favorite addMyFavorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        return favoriteService.addFavorite(jwt.getSubject(), opportunityId);
    }

    @DeleteMapping("/favorites/me/{opportunityId}")
    public void removeMyFavorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        favoriteService.removeFavorite(jwt.getSubject(), opportunityId);
    }
}
