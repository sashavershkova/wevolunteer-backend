package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Organization;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.Map;
import java.util.Optional;

@Repository
public class DynamoDbOrganizationRepository implements OrganizationRepository {

    private static final String TABLE_NAME = "WeVolunteer";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbOrganizationRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Optional<Organization> findById(String organizationId) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.fromS("ORG#" + organizationId),
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

        return Optional.of(mapToOrganization(item));
    }

    private Organization mapToOrganization(Map<String, AttributeValue> item) {
        return new Organization(
                item.get("organizationId").s(),
                item.get("name").s(),
                getStringOrNull(item, "description"),
                item.get("email").s(),
                getStringOrNull(item, "website")
        );
    }

    private String getStringOrNull(Map<String, AttributeValue> item, String key) {
        return item.containsKey(key) ? item.get(key).s() : null;
    }
}