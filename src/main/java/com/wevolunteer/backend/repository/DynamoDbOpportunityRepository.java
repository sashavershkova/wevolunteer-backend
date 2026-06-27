package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Opportunity;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DynamoDbOpportunityRepository implements OpportunityRepository {

    private static final String TABLE_NAME = "WeVolunteer";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbOpportunityRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Optional<Opportunity> findById(String opportunityId) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                "SK", AttributeValue.fromS("DETAILS")
        );

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

        Map<String, AttributeValue> item = dynamoDbClient.getItem(request).item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapToOpportunity(item));
    }

    @Override
    public List<Opportunity> findOpenOpportunities() {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI1_OpenOpportunities")
                .keyConditionExpression("GSI1PK = :gsi1pk")
                .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.fromS("OPPORTUNITIES#OPEN")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToOpportunity)
                .toList();
    }

    @Override
    public List<Opportunity> findByCategory(String category) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI2_Category")
                .keyConditionExpression("GSI2PK = :category")
                .filterExpression("#status = :openStatus")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":category", AttributeValue.fromS("CATEGORY#" + category),
                        ":openStatus", AttributeValue.fromS("OPEN")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToOpportunity)
                .toList();
    }

    @Override
    public List<Opportunity> findByLocation(String location) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI3_Location")
                .keyConditionExpression("GSI3PK = :location")
                .filterExpression("#status = :openStatus")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":location", AttributeValue.fromS("LOCATION#" + location),
                        ":openStatus", AttributeValue.fromS("OPEN")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToOpportunity)
                .toList();
    }

    @Override
    public List<Opportunity> findByOrganizationId(String organizationId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI4_Organization")
                .keyConditionExpression("GSI4PK = :organizationId")
                .filterExpression("#status = :openStatus")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":organizationId", AttributeValue.fromS("ORG#" + organizationId),
                        ":openStatus", AttributeValue.fromS("OPEN")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToOpportunity)
                .toList();
    }

    private Opportunity mapToOpportunity(Map<String, AttributeValue> item) {
        int capacity = Integer.parseInt(item.get("capacity").n());
        int registeredCount = Integer.parseInt(item.get("registeredCount").n());

        return new Opportunity(
                item.get("opportunityId").s(),
                item.get("title").s(),
                item.get("description").s(),
                item.get("category").s(),
                item.get("location").s(),
                item.get("date").s(),
                item.get("status").s(),
                item.get("organizationId").s(),
                item.get("organizationName").s(),
                capacity,
                registeredCount,
                capacity - registeredCount
        );
    }

    @Override
    public List<Opportunity> findOpenOpportunitiesByDateRange(String startDate, String endDate) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI1_OpenOpportunities")
                .keyConditionExpression("GSI1PK = :gsi1pk AND GSI1SK BETWEEN :startDate AND :endDate")
                .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.fromS("OPPORTUNITIES#OPEN"),
                        ":startDate", AttributeValue.fromS("DATE#" + startDate),
                        ":endDate", AttributeValue.fromS("DATE#" + endDate + "~")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToOpportunity)
                .toList();
    }

    @Override
    public List<Opportunity> findAllByOrganizationId(String organizationId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI4_Organization")
                .keyConditionExpression("GSI4PK = :organizationId")
                .expressionAttributeValues(Map.of(
                        ":organizationId", AttributeValue.fromS("ORG#" + organizationId)
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToOpportunity)
                .toList();
    }
}