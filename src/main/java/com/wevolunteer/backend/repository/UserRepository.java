package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String userId);

    User save(User user);

    User update(User user);

    void deleteById(String userId);
}