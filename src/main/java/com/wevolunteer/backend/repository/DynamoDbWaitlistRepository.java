package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.model.Waitlist;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dual-writes a waitlist entry under both {@code USER#<userId>} (so "what am I waitlisted for"
 * is a fast query, sorted by event date like Registration) and {@code OPPORTUNITY#<opportunityId>}
 * (so "who's waiting for this opportunity" is a fast query, sorted by join time - the
 * OPPORTUNITY-side sort key is {@code WAITLIST#<joinedAt>#<userId>} rather than
 * {@code WAITLIST#<userId>}, so a query naturally returns waitlisted volunteers in first-joined,
 * first-served order).
 *
 * <p>Unlike registration, joining or leaving the waitlist never touches the opportunity's
 * {@code registeredCount} - that counter only reflects actual registrations.
 */
@Repository
public class DynamoDbWaitlistRepository implements WaitlistRepository {

    private static final String TABLE_NAME = "WeVolunteer";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbWaitlistRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public List<Waitlist> findByUserId(String userId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("USER#" + userId),
                        ":skPrefix", AttributeValue.fromS("WAITLIST#")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToWaitlist)
                .toList();
    }

    @Override
    public List<Waitlist> findByOpportunityId(String opportunityId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                        ":skPrefix", AttributeValue.fromS("WAITLIST#")
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToWaitlist)
                .toList();
    }

    @Override
    public void joinWaitlist(
            String userId,
            String userName,
            String userEmail,
            String opportunityId,
            String opportunityTitle,
            String opportunityDate,
            String opportunityLocation,
            String organizationId,
            String organizationName) {

        String joinedAt = LocalDateTime.now().toString();

        Map<String, AttributeValue> userWaitlistItem = new HashMap<>();
        userWaitlistItem.put("PK", AttributeValue.fromS("USER#" + userId));
        userWaitlistItem.put("SK", AttributeValue.fromS("WAITLIST#" + opportunityDate + "#" + opportunityId));
        userWaitlistItem.put("entityType", AttributeValue.fromS("WAITLIST"));
        userWaitlistItem.put("userId", AttributeValue.fromS(userId));
        userWaitlistItem.put("opportunityId", AttributeValue.fromS(opportunityId));
        userWaitlistItem.put("title", AttributeValue.fromS(opportunityTitle));
        userWaitlistItem.put("date", AttributeValue.fromS(opportunityDate));
        userWaitlistItem.put("location", AttributeValue.fromS(opportunityLocation));
        userWaitlistItem.put("organizationId", AttributeValue.fromS(organizationId));
        userWaitlistItem.put("organizationName", AttributeValue.fromS(organizationName));
        userWaitlistItem.put("joinedAt", AttributeValue.fromS(joinedAt));

        Map<String, AttributeValue> opportunityWaitlistItem = new HashMap<>();
        opportunityWaitlistItem.put("PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId));
        opportunityWaitlistItem.put("SK", AttributeValue.fromS("WAITLIST#" + joinedAt + "#" + userId));
        opportunityWaitlistItem.put("entityType", AttributeValue.fromS("OPPORTUNITY_WAITLIST"));
        opportunityWaitlistItem.put("userId", AttributeValue.fromS(userId));
        opportunityWaitlistItem.put("opportunityId", AttributeValue.fromS(opportunityId));
        opportunityWaitlistItem.put("volunteerName", AttributeValue.fromS(userName));
        opportunityWaitlistItem.put("email", AttributeValue.fromS(userEmail));
        opportunityWaitlistItem.put("joinedAt", AttributeValue.fromS(joinedAt));

        Put createUserWaitlistEntry = Put.builder()
                .tableName(TABLE_NAME)
                .item(userWaitlistItem)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build();

        Put createOpportunityWaitlistEntry = Put.builder()
                .tableName(TABLE_NAME)
                .item(opportunityWaitlistItem)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build();

        TransactWriteItemsRequest transaction = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().put(createUserWaitlistEntry).build(),
                        TransactWriteItem.builder().put(createOpportunityWaitlistEntry).build()
                )
                .build();

        try {
            dynamoDbClient.transactWriteItems(transaction);
        } catch (TransactionCanceledException e) {
            throw new ConflictException(
                    "User '" + userId + "' is already on the waitlist for opportunity '" + opportunityId + "'.");
        }
    }

    @Override
    public void leaveWaitlist(String userId, String opportunityId, String opportunityDate) {
        QueryRequest findOpportunityWaitlistEntry = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .filterExpression("userId = :userId")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                        ":skPrefix", AttributeValue.fromS("WAITLIST#"),
                        ":userId", AttributeValue.fromS(userId)
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(findOpportunityWaitlistEntry);

        String opportunityWaitlistSortKey = response.items().isEmpty()
                ? null
                : response.items().get(0).get("SK").s();

        Delete deleteUserWaitlistEntry = Delete.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + userId),
                        "SK", AttributeValue.fromS("WAITLIST#" + opportunityDate + "#" + opportunityId)
                ))
                .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                .build();

        if (opportunityWaitlistSortKey == null) {
            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(TransactWriteItem.builder().delete(deleteUserWaitlistEntry).build())
                    .build());
            return;
        }

        Delete deleteOpportunityWaitlistEntry = Delete.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS("OPPORTUNITY#" + opportunityId),
                        "SK", AttributeValue.fromS(opportunityWaitlistSortKey)
                ))
                .build();

        dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().delete(deleteUserWaitlistEntry).build(),
                        TransactWriteItem.builder().delete(deleteOpportunityWaitlistEntry).build()
                )
                .build());
    }

    private Waitlist mapToWaitlist(Map<String, AttributeValue> item) {
        return new Waitlist(
                getStringOrNull(item, "userId"),
                getStringOrNull(item, "opportunityId"),
                getStringOrNull(item, "title"),
                getStringOrNull(item, "date"),
                getStringOrNull(item, "location"),
                getStringOrNull(item, "organizationId"),
                getStringOrNull(item, "organizationName"),
                getStringOrNull(item, "volunteerName"),
                getStringOrNull(item, "email"),
                getStringOrNull(item, "joinedAt")
        );
    }

    private String getStringOrNull(Map<String, AttributeValue> item, String key) {
        return item.containsKey(key) ? item.get(key).s() : null;
    }
}
