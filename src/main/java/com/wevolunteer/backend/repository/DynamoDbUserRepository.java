package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.User;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

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
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.fromS("USER#" + userId),
                "SK", AttributeValue.fromS("PROFILE")
        );

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
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
                item.get("role").s()
        );
    }

    @Override
    public User save(User user) {
        Map<String, AttributeValue> item = Map.of(
                "PK", AttributeValue.fromS("USER#" + user.userId()),
                "SK", AttributeValue.fromS("PROFILE"),
                "entityType", AttributeValue.fromS("USER"),
                "userId", AttributeValue.fromS(user.userId()),
                "name", AttributeValue.fromS(user.name()),
                "email", AttributeValue.fromS(user.email()),
                "role", AttributeValue.fromS(user.role())
        );

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build();

        dynamoDbClient.putItem(request);

        return user;
    }
}