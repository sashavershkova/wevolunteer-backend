package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Opportunity;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.ArrayList;
import java.util.HashMap;
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

        return queryAndMap(request);
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

        return queryAndMap(request);
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

        return queryAndMap(request);
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

        return queryAndMap(request);
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

        return queryAndMap(request);
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

        return queryAndMap(request);
    }

    @Override
    public List<Opportunity> findOpenOpportunitiesWithFilters(
            String category,
            String location,
            String organizationId,
            String startDate,
            String endDate) {

        Map<String, AttributeValue> values = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        List<String> filters = new ArrayList<>();

        QueryRequest.Builder requestBuilder = QueryRequest.builder()
                .tableName(TABLE_NAME);

        boolean locationIsMain = hasText(location);
        boolean categoryIsMain = !locationIsMain && hasText(category);
        boolean organizationIsMain = !locationIsMain && !categoryIsMain && hasText(organizationId);
        boolean dateIsMain = !locationIsMain && !categoryIsMain && !organizationIsMain
                && hasText(startDate) && hasText(endDate);

        if (locationIsMain) {
            requestBuilder
                    .indexName("GSI3_Location")
                    .keyConditionExpression("GSI3PK = :locationKey");
            values.put(":locationKey", AttributeValue.fromS("LOCATION#" + location));
            addOpenStatusFilter(filters, names, values);

        } else if (categoryIsMain) {
            requestBuilder
                    .indexName("GSI2_Category")
                    .keyConditionExpression("GSI2PK = :categoryKey");
            values.put(":categoryKey", AttributeValue.fromS("CATEGORY#" + category));
            addOpenStatusFilter(filters, names, values);

        } else if (organizationIsMain) {
            requestBuilder
                    .indexName("GSI4_Organization")
                    .keyConditionExpression("GSI4PK = :organizationKey");
            values.put(":organizationKey", AttributeValue.fromS("ORG#" + organizationId));
            addOpenStatusFilter(filters, names, values);

        } else if (dateIsMain) {
            requestBuilder
                    .indexName("GSI1_OpenOpportunities")
                    .keyConditionExpression("GSI1PK = :openKey AND GSI1SK BETWEEN :startDateKey AND :endDateKey");
            values.put(":openKey", AttributeValue.fromS("OPPORTUNITIES#OPEN"));
            values.put(":startDateKey", AttributeValue.fromS("DATE#" + startDate));
            values.put(":endDateKey", AttributeValue.fromS("DATE#" + endDate + "~"));

        } else {
            requestBuilder
                    .indexName("GSI1_OpenOpportunities")
                    .keyConditionExpression("GSI1PK = :openKey");
            values.put(":openKey", AttributeValue.fromS("OPPORTUNITIES#OPEN"));
        }

        if (hasText(category) && !categoryIsMain) {
            names.put("#category", "category");
            values.put(":categoryFilter", AttributeValue.fromS(category));
            filters.add("#category = :categoryFilter");
        }

        if (hasText(location) && !locationIsMain) {
            names.put("#location", "location");
            values.put(":locationFilter", AttributeValue.fromS(location + ", WA"));
            filters.add("#location = :locationFilter");
        }

        if (hasText(organizationId) && !organizationIsMain) {
            names.put("#organizationId", "organizationId");
            values.put(":organizationFilter", AttributeValue.fromS(organizationId));
            filters.add("#organizationId = :organizationFilter");
        }

        if (hasText(startDate) && hasText(endDate) && !dateIsMain) {
            names.put("#date", "date");
            values.put(":startDateFilter", AttributeValue.fromS(startDate));
            values.put(":endDateFilter", AttributeValue.fromS(endDate));
            filters.add("#date BETWEEN :startDateFilter AND :endDateFilter");
        }

        requestBuilder.expressionAttributeValues(values);

        if (!names.isEmpty()) {
            requestBuilder.expressionAttributeNames(names);
        }

        if (!filters.isEmpty()) {
            requestBuilder.filterExpression(String.join(" AND ", filters));
        }

        return queryAndMap(requestBuilder.build());
    }

    private void addOpenStatusFilter(
            List<String> filters,
            Map<String, String> names,
            Map<String, AttributeValue> values) {

        names.put("#status", "status");
        values.put(":openStatus", AttributeValue.fromS("OPEN"));
        filters.add("#status = :openStatus");
    }

    private List<Opportunity> queryAndMap(QueryRequest request) {
        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToOpportunity)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, AttributeValue> buildOpportunityItem(Opportunity opportunity) {
        String opportunityId = opportunity.opportunityId();
        String dateSortKey = "DATE#" + opportunity.date() + "#OPPORTUNITY#" + opportunityId;

        Map<String, AttributeValue> item = new java.util.HashMap<>();

        item.put("PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId));
        item.put("SK", AttributeValue.fromS("DETAILS"));
        item.put("entityType", AttributeValue.fromS("OPPORTUNITY"));

        item.put("opportunityId", AttributeValue.fromS(opportunityId));
        item.put("title", AttributeValue.fromS(opportunity.title()));
        item.put("description", AttributeValue.fromS(opportunity.description()));
        item.put("category", AttributeValue.fromS(opportunity.category()));
        item.put("location", AttributeValue.fromS(opportunity.location()));
        item.put("date", AttributeValue.fromS(opportunity.date()));
        item.put("status", AttributeValue.fromS(opportunity.status()));
        item.put("organizationId", AttributeValue.fromS(opportunity.organizationId()));
        item.put("organizationName", AttributeValue.fromS(opportunity.organizationName()));
        item.put("capacity", AttributeValue.fromN(String.valueOf(opportunity.capacity())));
        item.put("registeredCount", AttributeValue.fromN(String.valueOf(opportunity.registeredCount())));

        if ("OPEN".equals(opportunity.status())) {
                item.put("GSI1PK", AttributeValue.fromS("OPPORTUNITIES#OPEN"));
                item.put("GSI1SK", AttributeValue.fromS(dateSortKey));
        }

        item.put("GSI2PK", AttributeValue.fromS("CATEGORY#" + opportunity.category()));
        item.put("GSI2SK", AttributeValue.fromS(dateSortKey));

        item.put("GSI3PK", AttributeValue.fromS("LOCATION#" + opportunity.location().replace(", WA", "")));
        item.put("GSI3SK", AttributeValue.fromS(dateSortKey));

        item.put("GSI4PK", AttributeValue.fromS("ORG#" + opportunity.organizationId()));
        item.put("GSI4SK", AttributeValue.fromS(dateSortKey));

        return item;
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
        public List<Opportunity> findByOrganizationIdAndStatus(
                String organizationId,
                String status) {

        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("GSI4_Organization")
                .keyConditionExpression("GSI4PK = :organizationId")
                .filterExpression("#status = :status")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":organizationId", AttributeValue.fromS("ORG#" + organizationId),
                        ":status", AttributeValue.fromS(status)
                ))
                .build();

        return queryAndMap(request);
        }

        @Override
        public void deleteById(String opportunityId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                        "SK", AttributeValue.fromS("DETAILS")
                ))
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .build();

        dynamoDbClient.deleteItem(request);
        }

        @Override
        public Opportunity save(Opportunity opportunity) {
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(buildOpportunityItem(opportunity))
                    .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                    .build();

            dynamoDbClient.putItem(request);

            return opportunity;
        }
}