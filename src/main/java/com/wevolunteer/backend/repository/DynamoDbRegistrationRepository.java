package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.model.Registration;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.Delete;

import java.time.LocalDateTime;
import java.util.HashMap;
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

    @Override
    public List<Registration> findByOpportunityId(String opportunityId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                        ":skPrefix", AttributeValue.fromS("REGISTRATION#")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToRegistration)
                .toList();
    }

    @Override
        public void registerUserForOpportunity(
                String userId,
                String userName,
                String userEmail,
                String opportunityId,
                String opportunityTitle,
                String opportunityDate,
                String opportunityLocation,
                String organizationId,
                String organizationName) {

                String registeredAt = LocalDateTime.now().toString();

                Map<String, AttributeValue> userRegistrationItem = new HashMap<>();
                userRegistrationItem.put("PK", AttributeValue.fromS("USER#" + userId));
                userRegistrationItem.put("SK", AttributeValue.fromS("REGISTRATION#" + opportunityDate + "#" + opportunityId));
                userRegistrationItem.put("entityType", AttributeValue.fromS("REGISTRATION"));
                userRegistrationItem.put("userId", AttributeValue.fromS(userId));
                userRegistrationItem.put("opportunityId", AttributeValue.fromS(opportunityId));
                userRegistrationItem.put("title", AttributeValue.fromS(opportunityTitle));
                userRegistrationItem.put("date", AttributeValue.fromS(opportunityDate));
                userRegistrationItem.put("location", AttributeValue.fromS(opportunityLocation));
                userRegistrationItem.put("organizationId", AttributeValue.fromS(organizationId));
                userRegistrationItem.put("organizationName", AttributeValue.fromS(organizationName));
                userRegistrationItem.put("registrationStatus", AttributeValue.fromS("ACTIVE"));
                userRegistrationItem.put("registeredAt", AttributeValue.fromS(registeredAt));

                Map<String, AttributeValue> opportunityRegistrationItem = new HashMap<>();
                opportunityRegistrationItem.put("PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId));
                opportunityRegistrationItem.put("SK", AttributeValue.fromS("REGISTRATION#" + userId));
                opportunityRegistrationItem.put("entityType", AttributeValue.fromS("OPPORTUNITY_REGISTRATION"));
                opportunityRegistrationItem.put("userId", AttributeValue.fromS(userId));
                opportunityRegistrationItem.put("opportunityId", AttributeValue.fromS(opportunityId));
                opportunityRegistrationItem.put("volunteerName", AttributeValue.fromS(userName));
                opportunityRegistrationItem.put("email", AttributeValue.fromS(userEmail));
                opportunityRegistrationItem.put("registrationStatus", AttributeValue.fromS("ACTIVE"));
                opportunityRegistrationItem.put("registeredAt", AttributeValue.fromS(registeredAt));

                Update incrementRegisteredCount = Update.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                                "PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                                "SK", AttributeValue.fromS("DETAILS")
                        ))
                        .updateExpression("SET registeredCount = registeredCount + :one")
                        .conditionExpression("#status = :openStatus AND registeredCount < #capacity")
                        .expressionAttributeNames(Map.of(
                                "#status", "status",
                                "#capacity", "capacity"
                        ))
                        .expressionAttributeValues(Map.of(
                                ":one", AttributeValue.fromN("1"),
                                ":openStatus", AttributeValue.fromS("OPEN")
                        ))
                        .build();

                Put createUserRegistration = Put.builder()
                        .tableName(TABLE_NAME)
                        .item(userRegistrationItem)
                        .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                        .build();

                Put createOpportunityRegistration = Put.builder()
                        .tableName(TABLE_NAME)
                        .item(opportunityRegistrationItem)
                        .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                        .build();

                TransactWriteItemsRequest transaction = TransactWriteItemsRequest.builder()
                        .transactItems(
                                TransactWriteItem.builder().update(incrementRegisteredCount).build(),
                                TransactWriteItem.builder().put(createUserRegistration).build(),
                                TransactWriteItem.builder().put(createOpportunityRegistration).build()
                        )
                        .build();

                try {
                        dynamoDbClient.transactWriteItems(transaction);
                } catch (TransactionCanceledException e) {
                        List<CancellationReason> reasons = e.cancellationReasons();

                        if (isConditionalCheckFailed(reasons, 1) || isConditionalCheckFailed(reasons, 2)) {
                                throw new ConflictException(
                                        "User '" + userId + "' is already registered for opportunity '" + opportunityId + "'.");
                        }

                        if (isConditionalCheckFailed(reasons, 0)) {
                                throw new ConflictException(
                                        "Opportunity '" + opportunityId + "' is no longer open or has reached capacity.");
                        }

                        throw e;
                }
        }

        private boolean isConditionalCheckFailed(List<CancellationReason> reasons, int index) {
                return reasons != null
                        && index < reasons.size()
                        && "ConditionalCheckFailed".equals(reasons.get(index).code());
        }

        private Registration mapToRegistration(Map<String, AttributeValue> item) {
                return new Registration(
                        getStringOrNull(item, "userId"),
                        getStringOrNull(item, "opportunityId"),
                        getStringOrNull(item, "title"),
                        getStringOrNull(item, "date"),
                        getStringOrNull(item, "location"),
                        getStringOrNull(item, "organizationId"),
                        getStringOrNull(item, "organizationName"),
                        getStringOrNull(item, "registrationStatus"),
                        getStringOrNull(item, "volunteerName"),
                        getStringOrNull(item, "email"),
                        getStringOrNull(item, "registeredAt")
                );
        }

        private String getStringOrNull(Map<String, AttributeValue> item, String key) {
                return item.containsKey(key) ? item.get(key).s() : null;
        }

    @Override
        public void cancelRegistration(
                String userId,
                String opportunityId,
                String opportunityDate) {

        Update decrementRegisteredCount = Update.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                        "SK", AttributeValue.fromS("DETAILS")
                ))
                .updateExpression("SET registeredCount = registeredCount - :one")
                .conditionExpression("registeredCount > :zero")
                .expressionAttributeValues(Map.of(
                        ":one", AttributeValue.fromN("1"),
                        ":zero", AttributeValue.fromN("0")
                ))
                .build();

        Delete deleteUserRegistration = Delete.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + userId),
                        "SK", AttributeValue.fromS("REGISTRATION#" + opportunityDate + "#" + opportunityId)
                ))
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .build();

        Delete deleteOpportunityRegistration = Delete.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                        "SK", AttributeValue.fromS("REGISTRATION#" + userId)
                ))
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .build();

        TransactWriteItemsRequest transaction = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(decrementRegisteredCount).build(),
                        TransactWriteItem.builder().delete(deleteUserRegistration).build(),
                        TransactWriteItem.builder().delete(deleteOpportunityRegistration).build()
                )
                .build();

        dynamoDbClient.transactWriteItems(transaction);
        }
}