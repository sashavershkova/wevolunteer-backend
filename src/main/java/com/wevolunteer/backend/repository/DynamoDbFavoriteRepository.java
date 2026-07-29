package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Favorite;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DynamoDbFavoriteRepository implements FavoriteRepository {

    private static final String TABLE_NAME = "WeVolunteer";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbFavoriteRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public List<Favorite> findByUserId(String userId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("USER#" + userId),
                        ":skPrefix", AttributeValue.fromS("FAVORITE#")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToFavorite)
                .toList();
    }

    @Override
    public void save(Favorite favorite) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.fromS("USER#" + favorite.userId()));
        item.put("SK", AttributeValue.fromS("FAVORITE#" + favorite.opportunityId()));
        item.put("entityType", AttributeValue.fromS("FAVORITE"));
        item.put("userId", AttributeValue.fromS(favorite.userId()));
        item.put("opportunityId", AttributeValue.fromS(favorite.opportunityId()));
        item.put("title", AttributeValue.fromS(favorite.title()));
        item.put("date", AttributeValue.fromS(favorite.date()));
        item.put("location", AttributeValue.fromS(favorite.location()));
        item.put("organizationId", AttributeValue.fromS(favorite.organizationId()));
        item.put("organizationName", AttributeValue.fromS(favorite.organizationName()));
        item.put("favoritedAt", AttributeValue.fromS(favorite.favoritedAt()));

        // No conditionExpression on purpose: favoriting an opportunity that's already
        // favorited just refreshes the stored snapshot instead of erroring - a save
        // button should be idempotent, not a one-time registration.
        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }

    @Override
    public void deleteByUserIdAndOpportunityId(String userId, String opportunityId) {
        // No conditionExpression on purpose: un-favoriting something that isn't
        // favorited is a no-op, not an error - matches toggle-button UX.
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + userId),
                        "SK", AttributeValue.fromS("FAVORITE#" + opportunityId)
                ))
                .build();

        dynamoDbClient.deleteItem(request);
    }

    private Favorite mapToFavorite(Map<String, AttributeValue> item) {
        return new Favorite(
                item.get("userId").s(),
                item.get("opportunityId").s(),
                item.get("title").s(),
                item.get("date").s(),
                item.get("location").s(),
                item.get("organizationId").s(),
                item.get("organizationName").s(),
                item.get("favoritedAt").s()
        );
    }
}
