package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.model.Organization;
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

    @Override
    public Organization save(Organization organization) {
        Map<String, AttributeValue> item = new java.util.HashMap<>();

        item.put("PK", AttributeValue.fromS("ORG#" + organization.organizationId()));
        item.put("SK", AttributeValue.fromS("PROFILE"));
        item.put("entityType", AttributeValue.fromS("ORGANIZATION"));
        item.put("organizationId", AttributeValue.fromS(organization.organizationId()));
        item.put("name", AttributeValue.fromS(organization.name()));
        item.put("email", AttributeValue.fromS(organization.email()));

        if (organization.description() != null) {
            item.put("description", AttributeValue.fromS(organization.description()));
        }

        if (organization.website() != null) {
            item.put("website", AttributeValue.fromS(organization.website()));
        }

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build();

        try {
            dynamoDbClient.putItem(request);
        } catch (ConditionalCheckFailedException e) {
            throw new ConflictException(
                    "An organization with ID '" + organization.organizationId() + "' already exists.");
        }

        return organization;
    }

    @Override
    public Organization update(Organization organization) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("ORG#" + organization.organizationId()),
                        "SK", AttributeValue.fromS("PROFILE")
                ))
                .updateExpression("SET #name = :name, description = :description, email = :email, website = :website")
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .expressionAttributeNames(Map.of(
                        "#name", "name"
                ))
                .expressionAttributeValues(Map.of(
                        ":name", AttributeValue.fromS(organization.name()),
                        ":description", AttributeValue.fromS(organization.description()),
                        ":email", AttributeValue.fromS(organization.email()),
                        ":website", AttributeValue.fromS(organization.website())
                ))
                .build();

        dynamoDbClient.updateItem(request);

        return organization;
    }

    @Override
    public void deleteById(String organizationId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("ORG#" + organizationId),
                        "SK", AttributeValue.fromS("PROFILE")
                ))
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .build();

        dynamoDbClient.deleteItem(request);
    }
}