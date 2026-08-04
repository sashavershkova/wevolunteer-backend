package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.model.Waitlist;
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
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbWaitlistRepository")
class DynamoDbWaitlistRepositoryTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private DynamoDbWaitlistRepository repository;

    @Nested
    @DisplayName("joinWaitlist")
    class JoinWaitlist {

        @Test
        @DisplayName("writes both the user-side and opportunity-side waitlist entries in one transaction")
        void writesBothSides() {
            repository.joinWaitlist(
                    USER_ID, "Chelsea Pham", "chelsea@example.com",
                    OPPORTUNITY_ID, "Beach Cleanup", "2026-08-01", "Seattle, WA",
                    "org-1", "Green Earth");

            ArgumentCaptor<TransactWriteItemsRequest> captor =
                    ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
            verify(dynamoDbClient).transactWriteItems(captor.capture());

            List<Map<String, AttributeValue>> items = captor.getValue().transactItems().stream()
                    .map(item -> item.put().item())
                    .toList();

            assertThat(items).hasSize(2);
            assertThat(items.get(0).get("PK").s()).isEqualTo("USER#" + USER_ID);
            assertThat(items.get(0).get("SK").s()).isEqualTo("WAITLIST#2026-08-01#" + OPPORTUNITY_ID);
            assertThat(items.get(1).get("PK").s()).isEqualTo("OPPORTUNITY#" + OPPORTUNITY_ID);
            assertThat(items.get(1).get("SK").s()).startsWith("WAITLIST#");
            assertThat(items.get(1).get("SK").s()).endsWith("#" + USER_ID);
        }

        @Test
        @DisplayName("wraps a transaction cancellation in a ConflictException naming the duplicate join")
        void wrapsCancellationAsConflict() {
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenThrow(TransactionCanceledException.builder().build());

            assertThatThrownBy(() -> repository.joinWaitlist(
                    USER_ID, "Chelsea Pham", "chelsea@example.com",
                    OPPORTUNITY_ID, "Beach Cleanup", "2026-08-01", "Seattle, WA",
                    "org-1", "Green Earth"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already on the waitlist");
        }
    }

    @Nested
    @DisplayName("leaveWaitlist")
    class LeaveWaitlist {

        @Test
        @DisplayName("looks up the opportunity-side sort key and deletes both entries")
        void deletesBothSidesWhenFound() {
            Map<String, AttributeValue> opportunitySideItem = new HashMap<>();
            opportunitySideItem.put("SK", AttributeValue.fromS("WAITLIST#2026-07-01T10:00:00#" + USER_ID));
            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of(opportunitySideItem)).build());

            repository.leaveWaitlist(USER_ID, OPPORTUNITY_ID, "2026-08-01");

            ArgumentCaptor<TransactWriteItemsRequest> captor =
                    ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
            verify(dynamoDbClient).transactWriteItems(captor.capture());

            assertThat(captor.getValue().transactItems()).hasSize(2);
        }

        @Test
        @DisplayName("still deletes the user-side entry when no matching opportunity-side entry is found")
        void deletesUserSideOnlyWhenOpportunitySideMissing() {
            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of()).build());

            repository.leaveWaitlist(USER_ID, OPPORTUNITY_ID, "2026-08-01");

            ArgumentCaptor<TransactWriteItemsRequest> captor =
                    ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
            verify(dynamoDbClient, times(1)).transactWriteItems(captor.capture());
            assertThat(captor.getValue().transactItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("maps queried items into Waitlist records")
        void mapsItems() {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("userId", AttributeValue.fromS(USER_ID));
            item.put("opportunityId", AttributeValue.fromS(OPPORTUNITY_ID));
            item.put("title", AttributeValue.fromS("Beach Cleanup"));
            item.put("date", AttributeValue.fromS("2026-08-01"));
            item.put("location", AttributeValue.fromS("Seattle, WA"));
            item.put("organizationId", AttributeValue.fromS("org-1"));
            item.put("organizationName", AttributeValue.fromS("Green Earth"));
            item.put("joinedAt", AttributeValue.fromS("2026-07-01T10:00:00"));
            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of(item)).build());

            List<Waitlist> result = repository.findByUserId(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(result.get(0).title()).isEqualTo("Beach Cleanup");
            assertThat(result.get(0).joinedAt()).isEqualTo("2026-07-01T10:00:00");
        }
    }
}
