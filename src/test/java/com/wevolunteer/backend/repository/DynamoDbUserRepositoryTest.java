package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbUserRepository")
class DynamoDbUserRepositoryTest {

    private static final String USER_ID = "user-1";
    private static final String IMAGE_KEY = "users/user-1/profile/abc.jpg";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private DynamoDbUserRepository repository;

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("reads a legacy profile that has no profileImageKey attribute")
        void readsProfileWithoutImageKey() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(profileItem(null)).build());

            Optional<User> result = repository.findById(USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().profileImageKey()).isNull();
            assertThat(result.get().name()).isEqualTo("Chelsea Pham");
            assertThat(result.get().email()).isEqualTo("chelsea@example.com");
            assertThat(result.get().role()).isEqualTo("VOLUNTEER");
        }

        @Test
        @DisplayName("reads a profile that has a profileImageKey")
        void readsProfileWithImageKey() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(profileItem(IMAGE_KEY)).build());

            Optional<User> result = repository.findById(USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().profileImageKey()).isEqualTo(IMAGE_KEY);
        }

        @Test
        @DisplayName("returns empty when the item does not exist")
        void returnsEmptyWhenMissing() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(Map.of()).build());

            assertThat(repository.findById(USER_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("omits profileImageKey entirely for a user with no image")
        void omitsImageKeyWhenAbsent() {
            User user = new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");

            repository.save(user);

            ArgumentCaptor<PutItemRequest> captor =
                    ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            assertThat(captor.getValue().item()).doesNotContainKey("profileImageKey");
        }

        @Test
        @DisplayName("writes profileImageKey when the user has one")
        void writesImageKeyWhenPresent() {
            User user = new User(
                    USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER", IMAGE_KEY);

            repository.save(user);

            ArgumentCaptor<PutItemRequest> captor =
                    ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            assertThat(captor.getValue().item())
                    .containsEntry("profileImageKey", AttributeValue.fromS(IMAGE_KEY));
        }
    }

    @Nested
    @DisplayName("updateProfileImageKey")
    class UpdateProfileImageKey {

        @Test
        @DisplayName("sets only profileImageKey, leaving other attributes untouched")
        void updatesOnlyTheImageKey() {
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                    .thenReturn(UpdateItemResponse.builder()
                            .attributes(profileItem(IMAGE_KEY))
                            .build());

            repository.updateProfileImageKey(USER_ID, IMAGE_KEY);

            ArgumentCaptor<UpdateItemRequest> captor =
                    ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            UpdateItemRequest request = captor.getValue();
            assertThat(request.updateExpression())
                    .isEqualTo("SET profileImageKey = :profileImageKey");
            assertThat(request.updateExpression()).doesNotContain("name");
            assertThat(request.updateExpression()).doesNotContain("email");
            assertThat(request.updateExpression()).doesNotContain("role");
            assertThat(request.expressionAttributeValues())
                    .containsEntry(":profileImageKey", AttributeValue.fromS(IMAGE_KEY));
        }

        @Test
        @DisplayName("requires the profile to already exist")
        void requiresExistingProfile() {
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                    .thenReturn(UpdateItemResponse.builder()
                            .attributes(profileItem(IMAGE_KEY))
                            .build());

            repository.updateProfileImageKey(USER_ID, IMAGE_KEY);

            ArgumentCaptor<UpdateItemRequest> captor =
                    ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().conditionExpression())
                    .isEqualTo("attribute_exists(PK) AND attribute_exists(SK)");
        }

        @Test
        @DisplayName("returns the stored profile including the new key")
        void returnsUpdatedProfile() {
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                    .thenReturn(UpdateItemResponse.builder()
                            .attributes(profileItem(IMAGE_KEY))
                            .build());

            User result = repository.updateProfileImageKey(USER_ID, IMAGE_KEY);

            assertThat(result.profileImageKey()).isEqualTo(IMAGE_KEY);
            assertThat(result.name()).isEqualTo("Chelsea Pham");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("preserves an existing profileImageKey through a profile edit")
        void preservesImageKey() {
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                    .thenReturn(UpdateItemResponse.builder()
                            .attributes(profileItem(IMAGE_KEY))
                            .build());

            User result = repository.update(
                    new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER"));

            ArgumentCaptor<UpdateItemRequest> captor =
                    ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().updateExpression())
                    .doesNotContain("profileImageKey");
            assertThat(captor.getValue().returnValues()).isEqualTo(ReturnValue.ALL_NEW);
            assertThat(result.profileImageKey()).isEqualTo(IMAGE_KEY);
        }
    }

    private static Map<String, AttributeValue> profileItem(String profileImageKey) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", AttributeValue.fromS("USER#" + USER_ID));
        item.put("SK", AttributeValue.fromS("PROFILE"));
        item.put("entityType", AttributeValue.fromS("USER"));
        item.put("userId", AttributeValue.fromS(USER_ID));
        item.put("name", AttributeValue.fromS("Chelsea Pham"));
        item.put("email", AttributeValue.fromS("chelsea@example.com"));
        item.put("role", AttributeValue.fromS("VOLUNTEER"));

        if (profileImageKey != null) {
            item.put("profileImageKey", AttributeValue.fromS(profileImageKey));
        }

        return item;
    }
}
