package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Registration;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.List;
import java.util.Map;

@Repository
public class DynamoDbRegistrationRepository implements RegistrationRepository {

    private static final String TABLE_NAME = "WeVolunteer";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbRegistrationRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public List<Registration> findByUserId(String userId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("USER#" + userId),
                        ":skPrefix", AttributeValue.fromS("REGISTRATION#")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToRegistration)
                .toList();
    }

    private Registration mapToRegistration(Map<String, AttributeValue> item) {
        return new Registration(
                item.get("userId").s(),
                item.get("opportunityId").s(),
                item.get("title").s(),
                item.get("date").s(),
                item.get("location").s(),
                item.get("organizationId").s(),
                item.get("organizationName").s(),
                item.get("registrationStatus").s()
        );
    }
}