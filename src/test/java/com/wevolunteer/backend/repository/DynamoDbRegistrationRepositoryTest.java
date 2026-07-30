package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Registration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbRegistrationRepository")
class DynamoDbRegistrationRepositoryTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private DynamoDbRegistrationRepository repository;

    @Nested
    @DisplayName("findByOpportunityId")
    class FindByOpportunityId {

        @Test
        @DisplayName("maps a real opportunity-side item exactly as production writes it, "
                + "which has no date/title/location/organization attributes")
        void mapsRealOpportunitySideItem() {
            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of(opportunitySideItem())).build());

            List<Registration> result = repository.findByOpportunityId(OPPORTUNITY_ID);

            assertThat(result).hasSize(1);
            Registration registration = result.get(0);
            assertThat(registration.userId()).isEqualTo(USER_ID);
            assertThat(registration.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(registration.volunteerName()).isEqualTo("Chelsea Pham");
            assertThat(registration.email()).isEqualTo("chelsea@example.com");
            // These are absent on the opportunity-side item by design; callers that need them
            // (e.g. bulk close) must not assume they are populated here.
            assertThat(registration.date()).isNull();
            assertThat(registration.title()).isNull();
            assertThat(registration.location()).isNull();
            assertThat(registration.organizationId()).isNull();
            assertThat(registration.organizationName()).isNull();
        }
    }

    @Nested
    @DisplayName("cancelRegistrationForOpportunityClose")
    class CancelRegistrationForOpportunityClose {

        @Test
        @DisplayName("looks up the user-side item's real sort key and deletes both registration "
                + "items plus decrements registeredCount, without relying on any assumed date")
        void cancelsUsingRealUserSideSortKey() {
            String realSortKey = "REGISTRATION#2026-09-15#" + OPPORTUNITY_ID;
            Map<String, AttributeValue> userSideItem = new HashMap<>();
            userSideItem.put("PK", AttributeValue.fromS("USER#" + USER_ID));
            userSideItem.put("SK", AttributeValue.fromS(realSortKey));
            userSideItem.put("opportunityId", AttributeValue.fromS(OPPORTUNITY_ID));

            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of(userSideItem)).build());

            repository.cancelRegistrationForOpportunityClose(USER_ID, OPPORTUNITY_ID);

            ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(queryCaptor.capture());
            assertThat(queryCaptor.getValue().expressionAttributeValues())
                    .containsEntry(":pk", AttributeValue.fromS("USER#" + USER_ID))
                    .containsEntry(":opportunityId", AttributeValue.fromS(OPPORTUNITY_ID));

            ArgumentCaptor<TransactWriteItemsRequest> transactionCaptor =
                    ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
            verify(dynamoDbClient).transactWriteItems(transactionCaptor.capture());

            List<TransactWriteItem> items = transactionCaptor.getValue().transactItems();
            assertThat(items).hasSize(3);

            assertThat(items.get(0).update().updateExpression())
                    .isEqualTo("SET registeredCount = registeredCount - :one");
            assertThat(items.get(0).update().conditionExpression()).isEqualTo("registeredCount > :zero");

            assertThat(items.get(1).delete().key())
                    .containsEntry("PK", AttributeValue.fromS("USER#" + USER_ID))
                    .containsEntry("SK", AttributeValue.fromS(realSortKey));

            assertThat(items.get(2).delete().key())
                    .containsEntry("PK", AttributeValue.fromS("OPPORTUNITY#" + OPPORTUNITY_ID))
                    .containsEntry("SK", AttributeValue.fromS("REGISTRATION#" + USER_ID));
        }

        @Test
        @DisplayName("throws NotFoundException and never starts a transaction when no "
                + "user-side registration exists for the opportunity")
        void throwsWhenUserRegistrationMissing() {
            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of()).build());

            assertThatThrownBy(() ->
                    repository.cancelRegistrationForOpportunityClose(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class);

            verify(dynamoDbClient, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        @DisplayName("wraps a transaction cancellation in a ConflictException naming the user "
                + "and opportunity, instead of leaking a bare SDK exception")
        void wrapsTransactionCancellationWithMeaningfulMessage() {
            Map<String, AttributeValue> userSideItem = new HashMap<>();
            userSideItem.put("PK", AttributeValue.fromS("USER#" + USER_ID));
            userSideItem.put("SK", AttributeValue.fromS("REGISTRATION#2026-08-01#" + OPPORTUNITY_ID));
            userSideItem.put("opportunityId", AttributeValue.fromS(OPPORTUNITY_ID));

            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of(userSideItem)).build());

            CancellationReason failed = CancellationReason.builder().code("ConditionalCheckFailed").build();
            TransactionCanceledException cancellation = TransactionCanceledException.builder()
                    .message("Transaction cancelled")
                    .cancellationReasons(failed, failed, failed)
                    .build();
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenThrow(cancellation);

            assertThatThrownBy(() ->
                    repository.cancelRegistrationForOpportunityClose(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(USER_ID)
                    .hasMessageContaining(OPPORTUNITY_ID)
                    .hasMessageContaining("ConditionalCheckFailed");
        }
    }

    private static Map<String, AttributeValue> opportunitySideItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.fromS("OPPORTUNITY#" + OPPORTUNITY_ID));
        item.put("SK", AttributeValue.fromS("REGISTRATION#" + USER_ID));
        item.put("entityType", AttributeValue.fromS("OPPORTUNITY_REGISTRATION"));
        item.put("userId", AttributeValue.fromS(USER_ID));
        item.put("opportunityId", AttributeValue.fromS(OPPORTUNITY_ID));
        item.put("volunteerName", AttributeValue.fromS("Chelsea Pham"));
        item.put("email", AttributeValue.fromS("chelsea@example.com"));
        item.put("registrationStatus", AttributeValue.fromS("ACTIVE"));
        item.put("registeredAt", AttributeValue.fromS("2026-07-24T10:00:00"));
        return item;
    }
}
