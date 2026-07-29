package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Favorite;

import java.util.List;

public interface FavoriteRepository {

    List<Favorite> findByUserId(String userId);

    void save(Favorite favorite);

    void deleteByUserIdAndOpportunityId(String userId, String opportunityId);
}
