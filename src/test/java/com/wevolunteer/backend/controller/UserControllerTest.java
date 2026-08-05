package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.CreateUserRequest;
import com.wevolunteer.backend.dto.UpdateUserRequest;
import com.wevolunteer.backend.dto.UserProfileResponse;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.service.ProfileResponseMapper;
import com.wevolunteer.backend.service.RegistrationService;
import com.wevolunteer.backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    private static final String USER_ID = "user-1";

    @Mock
    private UserService userService;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private ProfileResponseMapper profileResponseMapper;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("updateCurrentUser resolves the user from the JWT subject and delegates to the service")
    void updateCurrentUserResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        UpdateUserRequest request =
                new UpdateUserRequest("New Name", "new@example.com", "ORGANIZATION");
        User updated = new User(USER_ID, "New Name", "new@example.com", "VOLUNTEER");
        when(userService.updateUser(USER_ID, request)).thenReturn(updated);
        UserProfileResponse expected =
                UserProfileResponse.from(updated, null);
        when(profileResponseMapper.toResponse(updated)).thenReturn(expected);

        UserProfileResponse result = userController.updateCurrentUser(jwt, request);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("updateCurrentUser passes the JWT subject and request through exactly once, "
            + "with no other service interaction")
    void updateCurrentUserPassesArgumentsThroughOnce() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        UpdateUserRequest request =
                new UpdateUserRequest("New Name", "new@example.com", "ORGANIZATION");
        User updated = new User(USER_ID, "New Name", "new@example.com", "VOLUNTEER");
        when(userService.updateUser(USER_ID, request)).thenReturn(updated);

        userController.updateCurrentUser(jwt, request);

        verify(userService).updateUser(USER_ID, request);
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("updateCurrentUser is mapped to PATCH /users/me")
    void updateCurrentUserIsMappedToMeRoute() throws NoSuchMethodException {
        Method method = UserController.class.getMethod(
                "updateCurrentUser", Jwt.class, UpdateUserRequest.class);
        org.springframework.web.bind.annotation.PatchMapping mapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class);

        assertThat(mapping.value()).containsExactly("/users/me");
    }

    @Test
    @DisplayName("no longer exposes a path-variable PATCH /users/{userId} endpoint")
    void noLongerHasPathVariableUpdateEndpoint() {
        assertThatThrownBy(() -> UserController.class.getMethod(
                "updateUser", String.class, UpdateUserRequest.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("getCurrentUser resolves the user from the JWT subject and delegates to the service")
    void getCurrentUserResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        User user = new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
        when(userService.getById(USER_ID)).thenReturn(user);
        UserProfileResponse expected = UserProfileResponse.from(user, null);
        when(profileResponseMapper.toResponse(user)).thenReturn(expected);

        UserProfileResponse result = userController.getCurrentUser(jwt);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("createUser resolves the user id from the JWT subject and delegates to the service")
    void createUserResolvesUserIdFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        CreateUserRequest request =
                new CreateUserRequest("Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
        User created = new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
        when(userService.createUser(USER_ID, request)).thenReturn(created);
        UserProfileResponse expected = UserProfileResponse.from(created, null);
        when(profileResponseMapper.toResponse(created)).thenReturn(expected);

        UserProfileResponse result = userController.createUser(jwt, request);

        assertThat(result).isSameAs(expected);
    }
}