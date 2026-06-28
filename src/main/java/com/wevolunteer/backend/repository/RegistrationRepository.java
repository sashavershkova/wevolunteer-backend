package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Registration;

import java.util.List;

public interface RegistrationRepository {

    List<Registration> findByUserId(String userId);
}