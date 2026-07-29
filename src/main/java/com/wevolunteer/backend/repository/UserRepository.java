package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String userId);

    User save(User user);

    User update(User user);

    /**
     * Updates only the profile image key, leaving every other profile attribute untouched.
     *
     * @return the profile as stored after the update
     */
    User updateProfileImageKey(String userId, String profileImageKey);

    void deleteById(String userId);
}
