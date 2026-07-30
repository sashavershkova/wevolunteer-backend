package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.CreateUserRequest;
import com.wevolunteer.backend.dto.UpdateUserRequest;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final String USER_ID = "user-1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns the user when the repository finds it")
        void returnsUserWhenFound() {
            User user = user(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            User result = userService.getById(USER_ID);

            assertThat(result).isSameAs(user);
            verify(userRepository).findById(USER_ID);
        }

        @Test
        @DisplayName("throws NotFoundException with the id in the message when absent")
        void throwsWhenMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: " + USER_ID);
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("maps the request onto a User carrying the supplied id and saves it")
        void mapsRequestAndSaves() {
            CreateUserRequest request =
                    new CreateUserRequest("Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
            when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            User result = userService.createUser(USER_ID, request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            assertThat(captor.getValue()).isEqualTo(
                    new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER"));
            assertThat(result).isEqualTo(captor.getValue());
        }

        @Test
        @DisplayName("returns whatever the repository returns rather than the local instance")
        void returnsRepositoryResult() {
            CreateUserRequest request =
                    new CreateUserRequest("Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
            User persisted = new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
            when(userRepository.save(any(User.class))).thenReturn(persisted);

            assertThat(userService.createUser(USER_ID, request)).isSameAs(persisted);
        }

        @Test
        @DisplayName("does not consult the repository for an existing user first")
        void doesNotCheckForExistingUser() {
            CreateUserRequest request =
                    new CreateUserRequest("Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
            when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.createUser(USER_ID, request);

            verify(userRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("maps the request onto a User and delegates to update")
        void mapsRequestAndUpdates() {
            UpdateUserRequest request =
                    new UpdateUserRequest("New Name", "new@example.com", "ORGANIZATION");
            when(userRepository.update(any(User.class))).thenAnswer(call -> call.getArgument(0));

            User result = userService.updateUser(USER_ID, request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).update(captor.capture());

            assertThat(captor.getValue()).isEqualTo(
                    new User(USER_ID, "New Name", "new@example.com", "ORGANIZATION"));
            assertThat(result).isEqualTo(captor.getValue());
        }

        @Test
        @DisplayName("does not verify the user exists before updating")
        void doesNotCheckExistenceFirst() {
            UpdateUserRequest request =
                    new UpdateUserRequest("New Name", "new@example.com", "ORGANIZATION");
            when(userRepository.update(any(User.class))).thenAnswer(call -> call.getArgument(0));

            userService.updateUser(USER_ID, request);

            verify(userRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("cancels every registration before deleting the user")
        void cancelsRegistrationsThenDeletes() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
            when(registrationService.getRegistrationsByUserId(USER_ID)).thenReturn(List.of(
                    registration(USER_ID, "opp-1"),
                    registration(USER_ID, "opp-2")));

            userService.deleteUser(USER_ID);

            InOrder inOrder = inOrder(userRepository, registrationService);
            inOrder.verify(userRepository).findById(USER_ID);
            inOrder.verify(registrationService).getRegistrationsByUserId(USER_ID);
            inOrder.verify(registrationService).cancelRegistration(USER_ID, "opp-1");
            inOrder.verify(registrationService).cancelRegistration(USER_ID, "opp-2");
            inOrder.verify(userRepository).deleteById(USER_ID);
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("deletes the user directly when there are no registrations")
        void deletesWhenNoRegistrations() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
            when(registrationService.getRegistrationsByUserId(USER_ID)).thenReturn(List.of());

            userService.deleteUser(USER_ID);

            verify(registrationService, never()).cancelRegistration(any(), any());
            verify(userRepository).deleteById(USER_ID);
        }

        @Test
        @DisplayName("throws NotFoundException and touches nothing when the user is absent")
        void throwsAndDeletesNothingWhenMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: " + USER_ID);

            verifyNoInteractions(registrationService);
            verify(userRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("propagates a cancellation failure without deleting the user")
        void propagatesCancellationFailure() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
            when(registrationService.getRegistrationsByUserId(USER_ID))
                    .thenReturn(List.of(registration(USER_ID, "opp-1")));
            org.mockito.Mockito.doThrow(new NotFoundException("Opportunity not found: opp-1"))
                    .when(registrationService).cancelRegistration(USER_ID, "opp-1");

            assertThatThrownBy(() -> userService.deleteUser(USER_ID))
                    .isInstanceOf(NotFoundException.class);

            verify(userRepository, never()).deleteById(any());
        }
    }

    private static User user(String userId) {
        return new User(userId, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
    }

    private static Registration registration(String userId, String opportunityId) {
        return new Registration(
                userId,
                opportunityId,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                "org-1",
                "Green Earth",
                "ACTIVE",
                "Chelsea Pham",
                "chelsea@example.com",
                "2026-07-24T10:00:00",
                null, null, null);
    }
}
