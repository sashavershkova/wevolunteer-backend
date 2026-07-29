package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.model.User;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class DynamoDbUserRepository implements UserRepository {

    private static final String TABLE_NAME = "WeVolunteer";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbUserRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Optional<User> findById(String userId) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(profileKey(userId))
                .build();

        Map<String, AttributeValue> item = dynamoDbClient.getItem(request).item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapToUser(item));
    }

    private User mapToUser(Map<String, AttributeValue> item) {
        return new User(
                item.get("userId").s(),
                item.get("name").s(),
                item.get("email").s(),
                item.get("role").s(),
                getStringOrNull(item, "profileImageKey")
        );
    }

    /**
     * Reads an optional attribute. Profile items created before profile images existed have no
     * profileImageKey at all, so a direct get(...).s() would throw NullPointerException on them.
     */
    private String getStringOrNull(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);

        return value == null ? null : value.s();
    }

    @Override
    public User save(User user) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", AttributeValue.fromS("USER#" + user.userId()));
        item.put("SK", AttributeValue.fromS("PROFILE"));
        item.put("entityType", AttributeValue.fromS("USER"));
        item.put("userId", AttributeValue.fromS(user.userId()));
        item.put("name", AttributeValue.fromS(user.name()));
        item.put("email", AttributeValue.fromS(user.email()));
        item.put("role", AttributeValue.fromS(user.role()));

        if (user.profileImageKey() != null) {
            item.put("profileImageKey", AttributeValue.fromS(user.profileImageKey()));
        }

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build();

        try {
            dynamoDbClient.putItem(request);
        } catch (ConditionalCheckFailedException e) {
            throw new ConflictException("User '" + user.userId() + "' already exists.");
        }

        return user;
    }

    @Override
    public User update(User user) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(profileKey(user.userId()))
                .updateExpression("SET #name = :name, email = :email, #role = :role")
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .expressionAttributeNames(Map.of(
                        "#name", "name",
                        "#role", "role"
                ))
                .expressionAttributeValues(Map.of(
                        ":name", AttributeValue.fromS(user.name()),
                        ":email", AttributeValue.fromS(user.email()),
                        ":role", AttributeValue.fromS(user.role())
                ))
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        return mapToUser(dynamoDbClient.updateItem(request).attributes());
    }

    @Override
    public User updateProfileImageKey(String userId, String profileImageKey) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(profileKey(userId))
                .updateExpression("SET profileImageKey = :profileImageKey")
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .expressionAttributeValues(Map.of(
                        ":profileImageKey", AttributeValue.fromS(profileImageKey)
                ))
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        return mapToUser(dynamoDbClient.updateItem(request).attributes());
    }

    @Override
    public void deleteById(String userId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(profileKey(userId))
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .build();

        dynamoDbClient.deleteItem(request);
    }

    private Map<String, AttributeValue> profileKey(String userId) {
        return Map.of(
                "PK", AttributeValue.fromS("USER#" + userId),
                "SK", AttributeValue.fromS("PROFILE")
        );
    }
}
