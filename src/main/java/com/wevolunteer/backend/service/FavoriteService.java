package com.wevolunteer.backend.service;

import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Favorite;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.FavoriteRepository;
import com.wevolunteer.backend.repository.OpportunityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final OpportunityRepository opportunityRepository;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            OpportunityRepository opportunityRepository) {

        this.favoriteRepository = favoriteRepository;
        this.opportunityRepository = opportunityRepository;
    }

    public List<Favorite> getFavoritesByUserId(String userId) {
        return favoriteRepository.findByUserId(userId);
    }

    public Favorite addFavorite(String userId, String opportunityId) {
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        Favorite favorite = new Favorite(
                userId,
                opportunity.opportunityId(),
                opportunity.title(),
                opportunity.date(),
                opportunity.location(),
                opportunity.organizationId(),
                opportunity.organizationName(),
                LocalDateTime.now().toString()
        );

        favoriteRepository.save(favorite);

        return favorite;
    }

    public void removeFavorite(String userId, String opportunityId) {
        favoriteRepository.deleteByUserIdAndOpportunityId(userId, opportunityId);
    }
}
