package com.wevolunteer.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wevolunteer.backend.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProfileResponse")
class UserProfileResponseTest {

    private static final String USER_ID = "user-1";
    private static final String IMAGE_KEY = "users/user-1/profile/abc.jpg";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("from")
    class From {

        @Test
        @DisplayName("copies every public profile field")
        void copiesPublicFields() {
            User user = new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");

            UserProfileResponse response = UserProfileResponse.from(user);

            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.name()).isEqualTo("Chelsea Pham");
            assertThat(response.email()).isEqualTo("chelsea@example.com");
            assertThat(response.role()).isEqualTo("VOLUNTEER");
        }

        @Test
        @DisplayName("maps a profile that has no image, so existing records keep working")
        void mapsProfileWithoutImage() {
            User user = new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");

            assertThat(user.profileImageKey()).isNull();
            assertThat(UserProfileResponse.from(user)).isNotNull();
        }

        @Test
        @DisplayName("maps a profile that has an image without failing")
        void mapsProfileWithImage() {
            User user = new User(
                    USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER", IMAGE_KEY);

            UserProfileResponse response = UserProfileResponse.from(user);

            assertThat(response.userId()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("serialization")
    class Serialization {

        @Test
        @DisplayName("never exposes the S3 object key to API clients")
        void doesNotExposeObjectKey() throws Exception {
            User user = new User(
                    USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER", IMAGE_KEY);

            String json = objectMapper.writeValueAsString(UserProfileResponse.from(user));

            assertThat(json).doesNotContain("profileImageKey");
            assertThat(json).doesNotContain(IMAGE_KEY);
            assertThat(json).doesNotContain("users/");
        }

        @Test
        @DisplayName("serializes the fields the frontend expects")
        void serializesExpectedFields() throws Exception {
            User user = new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");

            String json = objectMapper.writeValueAsString(UserProfileResponse.from(user));

            assertThat(json).contains("\"userId\":\"" + USER_ID + "\"");
            assertThat(json).contains("\"name\":\"Chelsea Pham\"");
            assertThat(json).contains("\"email\":\"chelsea@example.com\"");
            assertThat(json).contains("\"role\":\"VOLUNTEER\"");
        }
    }
}
