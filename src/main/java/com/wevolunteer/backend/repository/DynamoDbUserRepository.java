package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.model.User;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;

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
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + user.userId()),
                        "SK", AttributeValue.fromS("PROFILE")
                ))
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
                .build();

        dynamoDbClient.updateItem(request);

        return user;
    }

    @Override
        public void deleteById(String userId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + userId),
                        "SK", AttributeValue.fromS("PROFILE")
                ))
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .build();

        dynamoDbClient.deleteItem(request);
        }
}