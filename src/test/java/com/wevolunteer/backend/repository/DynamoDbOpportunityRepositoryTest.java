package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Opportunity;
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
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbOpportunityRepository - imageKey")
class DynamoDbOpportunityRepositoryTest {

    private static final String OPPORTUNITY_ID = "opp-1";
    private static final String ORG_ID = "org-1";
    private static final String IMAGE_KEY =
            "organizations/org-1/opportunities/550e8400-e29b-41d4-a716-446655440000.jpg";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private DynamoDbOpportunityRepository repository;

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("reads an opportunity created before images existed")
        void readsOpportunityWithoutImageKey() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(item(null)).build());

            Optional<Opportunity> result = repository.findById(OPPORTUNITY_ID);

            assertThat(result).isPresent();
            assertThat(result.get().imageKey()).isNull();
            assertThat(result.get().title()).isEqualTo("Beach Cleanup");
        }

        @Test
        @DisplayName("reads an opportunity that has an image")
        void readsOpportunityWithImageKey() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(item(IMAGE_KEY)).build());

            Optional<Opportunity> result = repository.findById(OPPORTUNITY_ID);

            assertThat(result).isPresent();
            assertThat(result.get().imageKey()).isEqualTo(IMAGE_KEY);
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("omits the attribute entirely when there is no image")
        void omitsImageKeyWhenAbsent() {
            repository.save(opportunity(null));

            assertThat(capturedItem()).doesNotContainKey("imageKey");
        }

        @Test
        @DisplayName("writes the key when an image is present")
        void writesImageKeyWhenPresent() {
            repository.save(opportunity(IMAGE_KEY));

            assertThat(capturedItem())
                    .containsEntry("imageKey", AttributeValue.fromS(IMAGE_KEY));
        }

        private Map<String, AttributeValue> capturedItem() {
            ArgumentCaptor<PutItemRequest> captor =
                    ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            return captor.getValue().item();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("keeps the image key when the model still carries it")
        void keepsImageKey() {
            repository.update(opportunity(IMAGE_KEY));

            ArgumentCaptor<PutItemRequest> captor =
                    ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            assertThat(captor.getValue().item())
                    .containsEntry("imageKey", AttributeValue.fromS(IMAGE_KEY));
        }

        @Test
        @DisplayName("drops the attribute when the model has no key, which is why the service must carry it forward")
        void dropsImageKeyWhenModelHasNone() {
            repository.update(opportunity(null));

            ArgumentCaptor<PutItemRequest> captor =
                    ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            assertThat(captor.getValue().item()).doesNotContainKey("imageKey");
        }
    }

    @Nested
    @DisplayName("startTime and endTime")
    class StartAndEndTime {

        @Test
        @DisplayName("save writes both startTime and endTime attributes")
        void writesStartAndEndTimeOnSave() {
            repository.save(opportunity(null));

            ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            assertThat(captor.getValue().item())
                    .containsEntry("startTime", AttributeValue.fromS("09:00"))
                    .containsEntry("endTime", AttributeValue.fromS("12:00"));
        }

        @Test
        @DisplayName("update writes both startTime and endTime attributes")
        void writesStartAndEndTimeOnUpdate() {
            repository.update(opportunity(null));

            ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            assertThat(captor.getValue().item())
                    .containsEntry("startTime", AttributeValue.fromS("09:00"))
                    .containsEntry("endTime", AttributeValue.fromS("12:00"));
        }

        @Test
        @DisplayName("findById reads back startTime and endTime when present")
        void readsStartAndEndTimeWhenPresent() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(item(null, "09:00", "12:00")).build());

            Optional<Opportunity> result = repository.findById(OPPORTUNITY_ID);

            assertThat(result).isPresent();
            assertThat(result.get().startTime()).isEqualTo("09:00");
            assertThat(result.get().endTime()).isEqualTo("12:00");
        }

        @Test
        @DisplayName("findById safely loads an older record that predates startTime/endTime, as null rather than throwing")
        void readsOlderRecordMissingStartAndEndTime() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(item(null, null, null)).build());

            Optional<Opportunity> result = repository.findById(OPPORTUNITY_ID);

            assertThat(result).isPresent();
            assertThat(result.get().startTime()).isNull();
            assertThat(result.get().endTime()).isNull();
            assertThat(result.get().title()).isEqualTo("Beach Cleanup");
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("sets status to CLOSED and resets registeredCount to 0 in the same update")
        void setsClosedStatusAndZeroesRegisteredCount() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(itemWithRegisteredCount(2)).build());

            repository.close(OPPORTUNITY_ID);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().updateExpression())
                    .isEqualTo("SET #status = :closedStatus, registeredCount = :zero REMOVE GSI1PK, GSI1SK");
            assertThat(captor.getValue().expressionAttributeValues())
                    .containsEntry(":closedStatus", AttributeValue.fromS("CLOSED"))
                    .containsEntry(":zero", AttributeValue.fromN("0"));
        }

        @Test
        @DisplayName("returns an opportunity with status CLOSED, registeredCount 0 and availableSpots equal to capacity")
        void returnsZeroedOpportunity() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(itemWithRegisteredCount(2)).build());

            Opportunity result = repository.close(OPPORTUNITY_ID);

            assertThat(result.status()).isEqualTo("CLOSED");
            assertThat(result.registeredCount()).isZero();
            assertThat(result.availableSpots()).isEqualTo(result.capacity());
        }
    }

    @Nested
    @DisplayName("reopen")
    class Reopen {

        @Test
        @DisplayName("sets status to OPEN, resets registeredCount to 0 and restores GSI1PK/GSI1SK in the same update")
        void setsOpenStatusAndRestoresGsi1() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(itemClosed(null)).build());

            repository.reopen(OPPORTUNITY_ID);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().updateExpression())
                    .isEqualTo("SET #status = :openStatus, registeredCount = :zero, "
                            + "GSI1PK = :gsi1pk, GSI1SK = :gsi1sk");
            assertThat(captor.getValue().expressionAttributeValues())
                    .containsEntry(":openStatus", AttributeValue.fromS("OPEN"))
                    .containsEntry(":zero", AttributeValue.fromN("0"))
                    .containsEntry(":gsi1pk", AttributeValue.fromS("OPPORTUNITIES#OPEN"))
                    .containsEntry(":gsi1sk",
                            AttributeValue.fromS("DATE#2026-08-01#OPPORTUNITY#" + OPPORTUNITY_ID));
        }

        @Test
        @DisplayName("requires the item to exist and be currently CLOSED")
        void conditionRequiresExistenceAndClosedStatus() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(itemClosed(null)).build());

            repository.reopen(OPPORTUNITY_ID);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().conditionExpression())
                    .isEqualTo("attribute_exists(PK) AND attribute_exists(SK) AND #status = :closedStatus");
            assertThat(captor.getValue().expressionAttributeValues())
                    .containsEntry(":closedStatus", AttributeValue.fromS("CLOSED"));
        }

        @Test
        @DisplayName("returns an opportunity with status OPEN, registeredCount 0 and availableSpots equal to capacity")
        void returnsReopenedOpportunity() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(itemClosed(null)).build());

            Opportunity result = repository.reopen(OPPORTUNITY_ID);

            assertThat(result.status()).isEqualTo("OPEN");
            assertThat(result.registeredCount()).isZero();
            assertThat(result.availableSpots()).isEqualTo(result.capacity());
        }

        @Test
        @DisplayName("preserves the image key from the stored record")
        void preservesImageKey() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(itemClosed(IMAGE_KEY)).build());

            Opportunity result = repository.reopen(OPPORTUNITY_ID);

            assertThat(result.imageKey()).isEqualTo(IMAGE_KEY);
        }

        @Test
        @DisplayName("preserves capacity and all other non-status fields")
        void preservesRemainingFields() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().item(itemClosed(null)).build());

            Opportunity result = repository.reopen(OPPORTUNITY_ID);

            assertThat(result.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(result.title()).isEqualTo("Beach Cleanup");
            assertThat(result.description()).isEqualTo("Pick up litter");
            assertThat(result.category()).isEqualTo("ENVIRONMENT");
            assertThat(result.location()).isEqualTo("Seattle, WA");
            assertThat(result.date()).isEqualTo("2026-08-01");
            assertThat(result.organizationId()).isEqualTo(ORG_ID);
            assertThat(result.organizationName()).isEqualTo("Green Earth");
            assertThat(result.capacity()).isEqualTo(10);
        }
    }

    private static Map<String, AttributeValue> itemWithRegisteredCount(int registeredCount) {
        Map<String, AttributeValue> withCount = new HashMap<>(item(null));
        withCount.put("registeredCount", AttributeValue.fromN(String.valueOf(registeredCount)));
        return withCount;
    }

    private static Map<String, AttributeValue> itemClosed(String imageKey) {
        Map<String, AttributeValue> closed = new HashMap<>(item(imageKey));
        closed.put("status", AttributeValue.fromS("CLOSED"));
        return closed;
    }

    private static Opportunity opportunity(String imageKey) {
        return new Opportunity(
                OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                "Seattle, WA", "2026-08-01", "OPEN", ORG_ID, "Green Earth",
                10, 0, 10, null, "09:00", "12:00", List.of("Sort donations"), false, imageKey);
    }

    private static Map<String, AttributeValue> item(String imageKey) {
        return item(imageKey, null, null);
    }

    private static Map<String, AttributeValue> item(String imageKey, String startTime, String endTime) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", AttributeValue.fromS("OPPORTUNITY#" + OPPORTUNITY_ID));
        item.put("SK", AttributeValue.fromS("DETAILS"));
        item.put("opportunityId", AttributeValue.fromS(OPPORTUNITY_ID));
        item.put("title", AttributeValue.fromS("Beach Cleanup"));
        item.put("description", AttributeValue.fromS("Pick up litter"));
        item.put("category", AttributeValue.fromS("ENVIRONMENT"));
        item.put("location", AttributeValue.fromS("Seattle, WA"));
        item.put("date", AttributeValue.fromS("2026-08-01"));
        item.put("status", AttributeValue.fromS("OPEN"));
        item.put("organizationId", AttributeValue.fromS(ORG_ID));
        item.put("organizationName", AttributeValue.fromS("Green Earth"));
        item.put("capacity", AttributeValue.fromN("10"));
        item.put("registeredCount", AttributeValue.fromN("0"));

        if (imageKey != null) {
            item.put("imageKey", AttributeValue.fromS(imageKey));
        }

        if (startTime != null) {
            item.put("startTime", AttributeValue.fromS(startTime));
        }

        if (endTime != null) {
            item.put("endTime", AttributeValue.fromS(endTime));
        }

        return item;
    }
}
