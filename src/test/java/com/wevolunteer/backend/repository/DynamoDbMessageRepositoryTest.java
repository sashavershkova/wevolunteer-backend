package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Conversation;
import com.wevolunteer.backend.model.ConversationId;
import com.wevolunteer.backend.model.ConversationParticipants;
import com.wevolunteer.backend.model.Message;
import com.wevolunteer.backend.model.ParticipantType;
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
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbMessageRepository")
class DynamoDbMessageRepositoryTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ORG_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CONVERSATION_ID = USER_ID + "_" + ORG_ID;
    private static final String SENT_AT = "2026-08-04T17:05:09.250Z";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private DynamoDbMessageRepository repository;

    @Nested
    @DisplayName("saveMessage")
    class SaveMessage {

        @Test
        @DisplayName("writes the message and both inbox snapshots in one transaction")
        void writesAllThreeItems() {
            repository.saveMessage(message(ParticipantType.USER), participants());

            TransactWriteItemsRequest request = captureTransaction();

            assertThat(request.transactItems()).hasSize(3);

            Map<String, AttributeValue> messageItem =
                    request.transactItems().getFirst().put().item();
            assertThat(messageItem.get("PK").s()).isEqualTo("CONVERSATION#" + CONVERSATION_ID);
            assertThat(messageItem.get("SK").s())
                    .isEqualTo("MESSAGE#" + SENT_AT + "#msg-1");
            assertThat(messageItem.get("entityType").s()).isEqualTo("MESSAGE");
            assertThat(messageItem.get("senderType").s()).isEqualTo("USER");

            List<Update> updates = request.transactItems().stream()
                    .map(TransactWriteItem::update)
                    .filter(update -> update != null)
                    .toList();

            assertThat(updates)
                    .extracting(update -> update.key().get("PK").s())
                    .containsExactlyInAnyOrder("USER#" + USER_ID, "ORG#" + ORG_ID);

            assertThat(updates)
                    .allSatisfy(update -> assertThat(update.key().get("SK").s())
                            .isEqualTo("CONVERSATION#" + CONVERSATION_ID));
        }

        @Test
        @DisplayName("increments unread only on the recipient's side when a volunteer sends")
        void volunteerSendIncrementsOrganizationOnly() {
            repository.saveMessage(message(ParticipantType.USER), participants());

            assertThat(unreadIncrementFor("USER#" + USER_ID)).isEqualTo("0");
            assertThat(unreadIncrementFor("ORG#" + ORG_ID)).isEqualTo("1");
        }

        @Test
        @DisplayName("increments unread only on the recipient's side when an organization sends")
        void organizationSendIncrementsVolunteerOnly() {
            repository.saveMessage(message(ParticipantType.ORGANIZATION), participants());

            assertThat(unreadIncrementFor("USER#" + USER_ID)).isEqualTo("1");
            assertThat(unreadIncrementFor("ORG#" + ORG_ID)).isEqualTo("0");
        }

        @Test
        @DisplayName("stores each side's counterpart name, not its own")
        void storesCounterpartNames() {
            repository.saveMessage(message(ParticipantType.USER), participants());

            assertThat(counterpartNameFor("USER#" + USER_ID)).isEqualTo("Seattle Food Bank");
            assertThat(counterpartNameFor("ORG#" + ORG_ID)).isEqualTo("Renata Murzina");
        }

        @Test
        @DisplayName("upserts the inbox rows so the first message of a thread creates them")
        void upsertsInboxRows() {
            repository.saveMessage(message(ParticipantType.USER), participants());

            assertThat(captureTransaction().transactItems())
                    .filteredOn(item -> item.update() != null)
                    .allSatisfy(item -> {
                        assertThat(item.update().updateExpression())
                                .startsWith("SET ")
                                .contains("ADD unreadCount :unreadIncrement");
                        assertThat(item.update().conditionExpression()).isNull();
                    });
        }
    }

    @Nested
    @DisplayName("findConversationsByParticipant")
    class FindConversationsByParticipant {

        @Test
        @DisplayName("reads the participant's own partition with the conversation prefix")
        void readsOwnPartition() {
            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of()).build());

            repository.findConversationsByParticipant(ORG_ID, ParticipantType.ORGANIZATION);

            ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(captor.capture());

            assertThat(captor.getValue().expressionAttributeValues().get(":pk").s())
                    .isEqualTo("ORG#" + ORG_ID);
            assertThat(captor.getValue().expressionAttributeValues().get(":skPrefix").s())
                    .isEqualTo("CONVERSATION#");
        }

        @Test
        @DisplayName("orders most recently active first, since the sort key cannot")
        void ordersByRecency() {
            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(
                    QueryResponse.builder().items(List.of(
                            conversationItem("older", "2026-08-01T09:00:00.000Z"),
                            conversationItem("newest", "2026-08-04T17:05:09.250Z"),
                            conversationItem("middle", "2026-08-03T12:00:00.000Z")
                    )).build());

            List<Conversation> result =
                    repository.findConversationsByParticipant(USER_ID, ParticipantType.USER);

            assertThat(result).extracting(Conversation::conversationId)
                    .containsExactly("newest", "middle", "older");
        }

        @Test
        @DisplayName("defaults a missing unread count to zero rather than failing")
        void defaultsMissingUnreadCount() {
            Map<String, AttributeValue> item = Map.of(
                    "conversationId", AttributeValue.fromS(CONVERSATION_ID),
                    "lastMessageAt", AttributeValue.fromS(SENT_AT));

            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of(item)).build());

            assertThat(repository.findConversationsByParticipant(USER_ID, ParticipantType.USER))
                    .singleElement()
                    .extracting(Conversation::unreadCount)
                    .isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findMessages")
    class FindMessages {

        @Test
        @DisplayName("reads the conversation partition oldest first")
        void readsOldestFirst() {
            when(dynamoDbClient.query(any(QueryRequest.class)))
                    .thenReturn(QueryResponse.builder().items(List.of()).build());

            repository.findMessages(new ConversationId(USER_ID, ORG_ID));

            ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(captor.capture());

            assertThat(captor.getValue().expressionAttributeValues().get(":pk").s())
                    .isEqualTo("CONVERSATION#" + CONVERSATION_ID);
            assertThat(captor.getValue().scanIndexForward()).isTrue();
        }
    }

    @Nested
    @DisplayName("findConversation")
    class FindConversation {

        @Test
        @DisplayName("returns empty when the participant has no such conversation")
        void returnsEmptyWhenMissing() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                    .thenReturn(GetItemResponse.builder().build());

            assertThat(repository.findConversation(
                    new ConversationId(USER_ID, ORG_ID), ParticipantType.USER))
                    .isEmpty();
        }

        @Test
        @DisplayName("reads the viewer's own copy of the conversation")
        void readsViewersCopy() {
            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(
                    GetItemResponse.builder()
                            .item(conversationItem(CONVERSATION_ID, SENT_AT))
                            .build());

            Optional<Conversation> result = repository.findConversation(
                    new ConversationId(USER_ID, ORG_ID), ParticipantType.ORGANIZATION);

            assertThat(result).isPresent();

            ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
            verify(dynamoDbClient).getItem(captor.capture());
            assertThat(captor.getValue().key().get("PK").s()).isEqualTo("ORG#" + ORG_ID);
        }
    }

    @Nested
    @DisplayName("markRead")
    class MarkRead {

        @Test
        @DisplayName("zeroes only the reader's own copy")
        void zeroesReadersCopy() {
            repository.markRead(new ConversationId(USER_ID, ORG_ID), ParticipantType.USER);

            ArgumentCaptor<UpdateItemRequest> captor =
                    ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().key().get("PK").s()).isEqualTo("USER#" + USER_ID);
            assertThat(captor.getValue().updateExpression()).isEqualTo("SET unreadCount = :zero");
        }

        @Test
        @DisplayName("treats a conversation that was never started as a no-op, not an error")
        void missingConversationIsNoOp() {
            when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                    .thenThrow(ConditionalCheckFailedException.builder().build());

            assertThatCode(() -> repository.markRead(
                    new ConversationId(USER_ID, ORG_ID), ParticipantType.USER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("guards against conjuring a stub inbox row")
        void guardsAgainstStubRow() {
            repository.markRead(new ConversationId(USER_ID, ORG_ID), ParticipantType.USER);

            ArgumentCaptor<UpdateItemRequest> captor =
                    ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().conditionExpression()).isEqualTo("attribute_exists(PK)");
        }
    }

    private TransactWriteItemsRequest captureTransaction() {
        ArgumentCaptor<TransactWriteItemsRequest> captor =
                ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        return captor.getValue();
    }

    private String unreadIncrementFor(String partitionKey) {
        return updateFor(partitionKey)
                .expressionAttributeValues().get(":unreadIncrement").n();
    }

    private String counterpartNameFor(String partitionKey) {
        return updateFor(partitionKey)
                .expressionAttributeValues().get(":counterpartName").s();
    }

    private Update updateFor(String partitionKey) {
        return captureTransaction().transactItems().stream()
                .map(TransactWriteItem::update)
                .filter(update -> update != null)
                .filter(update -> partitionKey.equals(update.key().get("PK").s()))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("No update written for " + partitionKey));
    }

    private static Message message(ParticipantType senderType) {
        return new Message(
                CONVERSATION_ID,
                "msg-1",
                senderType == ParticipantType.USER ? USER_ID : ORG_ID,
                senderType,
                "Is parking available?",
                SENT_AT);
    }

    private static ConversationParticipants participants() {
        return new ConversationParticipants(
                USER_ID, "Renata Murzina", ORG_ID, "Seattle Food Bank");
    }

    private static Map<String, AttributeValue> conversationItem(
            String conversationId, String lastMessageAt) {

        return Map.of(
                "conversationId", AttributeValue.fromS(conversationId),
                "userId", AttributeValue.fromS(USER_ID),
                "organizationId", AttributeValue.fromS(ORG_ID),
                "counterpartName", AttributeValue.fromS("Seattle Food Bank"),
                "lastMessagePreview", AttributeValue.fromS("Is parking available?"),
                "lastMessageAt", AttributeValue.fromS(lastMessageAt),
                "lastMessageSenderId", AttributeValue.fromS(USER_ID),
                "unreadCount", AttributeValue.fromN("1"));
    }
}
