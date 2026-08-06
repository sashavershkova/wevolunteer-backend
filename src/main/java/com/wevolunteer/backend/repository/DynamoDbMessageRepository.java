package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Conversation;
import com.wevolunteer.backend.model.ConversationId;
import com.wevolunteer.backend.model.ConversationParticipants;
import com.wevolunteer.backend.model.Message;
import com.wevolunteer.backend.model.ParticipantType;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Messaging on the shared single table, following the dual-write shape
 * {@link DynamoDbRegistrationRepository} already uses.
 *
 * <p>Three item kinds:
 *
 * <pre>
 *   message         PK = CONVERSATION#&lt;convId&gt;  SK = MESSAGE#&lt;sentAt&gt;#&lt;messageId&gt;
 *   volunteer inbox PK = USER#&lt;userId&gt;          SK = CONVERSATION#&lt;convId&gt;
 *   org inbox       PK = ORG#&lt;organizationId&gt;   SK = CONVERSATION#&lt;convId&gt;
 * </pre>
 *
 * <p>A thread read is one query on the conversation partition; an inbox read is one query on the
 * participant's own partition. Neither needs a new GSI, which matters because the table's
 * GSI1-GSI4 are provisioned outside this repository and adding GSI5 would be a manual step.
 *
 * <p>The cost of avoiding that index is that {@code CONVERSATION#<convId>} does not sort by
 * recency, so {@link #findConversationsByParticipant} sorts in memory. Inboxes are small and the
 * codebase already sorts collections of this size application-side (see {@code sortRegistrations}
 * on the frontend). If inboxes ever grow past a few hundred rows, the fix is a sparse GSI keyed
 * on {@code lastMessageAt}, not pagination of an unsorted query.
 */
@Repository
public class DynamoDbMessageRepository implements MessageRepository {

    private static final String TABLE_NAME = "WeVolunteer";

    private static final String CONVERSATION_SK_PREFIX = "CONVERSATION#";
    private static final String MESSAGE_SK_PREFIX = "MESSAGE#";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbMessageRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public List<Conversation> findConversationsByParticipant(
            String participantId, ParticipantType participantType) {

        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS(partitionKey(participantId, participantType)),
                        ":skPrefix", AttributeValue.fromS(CONVERSATION_SK_PREFIX)
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToConversation)
                .sorted(Comparator.comparing(
                        Conversation::lastMessageAt,
                        Comparator.nullsLast(Comparator.<String>reverseOrder())))
                .toList();
    }

    @Override
    public Optional<Conversation> findConversation(
            ConversationId conversationId, ParticipantType viewerType) {

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS(partitionKey(conversationId, viewerType)),
                        "SK", AttributeValue.fromS(CONVERSATION_SK_PREFIX + conversationId.value())
                ))
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapToConversation(response.item()));
    }

    @Override
    public List<Message> findMessages(ConversationId conversationId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("CONVERSATION#" + conversationId.value()),
                        ":skPrefix", AttributeValue.fromS(MESSAGE_SK_PREFIX)
                ))
                // Oldest first: sentAt leads the sort key and is fixed-width, so ascending key
                // order is chronological order. The thread renders top-to-bottom with no sort.
                .scanIndexForward(true)
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        return response.items().stream()
                .map(this::mapToMessage)
                .toList();
    }

    @Override
    public void saveMessage(Message message, ConversationParticipants participants) {
        ConversationId conversationId = participants.conversationId();

        Map<String, AttributeValue> messageItem = new HashMap<>();
        messageItem.put("PK", AttributeValue.fromS("CONVERSATION#" + conversationId.value()));
        messageItem.put("SK", AttributeValue.fromS(
                MESSAGE_SK_PREFIX + message.sentAt() + "#" + message.messageId()));
        messageItem.put("entityType", AttributeValue.fromS("MESSAGE"));
        messageItem.put("conversationId", AttributeValue.fromS(conversationId.value()));
        messageItem.put("messageId", AttributeValue.fromS(message.messageId()));
        messageItem.put("senderId", AttributeValue.fromS(message.senderId()));
        messageItem.put("senderType", AttributeValue.fromS(message.senderType().name()));
        messageItem.put("body", AttributeValue.fromS(message.body()));
        messageItem.put("sentAt", AttributeValue.fromS(message.sentAt()));

        // messageId is a fresh UUID, so this Put can never collide and needs no condition
        // expression -- unlike a registration, sending the same message twice is a legitimate
        // thing for a person to do.
        Put putMessage = Put.builder()
                .tableName(TABLE_NAME)
                .item(messageItem)
                .build();

        boolean senderIsUser = message.senderType() == ParticipantType.USER;

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().put(putMessage).build(),
                        TransactWriteItem.builder()
                                .update(inboxUpdate(
                                        participants, ParticipantType.USER, message, !senderIsUser))
                                .build(),
                        TransactWriteItem.builder()
                                .update(inboxUpdate(
                                        participants, ParticipantType.ORGANIZATION, message, senderIsUser))
                                .build())
                .build();

        dynamoDbClient.transactWriteItems(request);
    }

    /**
     * Upserts one side's inbox snapshot.
     *
     * <p>{@code Update} rather than {@code Put} so the first message of a conversation creates
     * the row and every later one refreshes it, without the service having to know which case it
     * is in. {@code ADD unreadCount} treats a missing attribute as zero, so the same expression
     * both initializes and increments -- and adding zero on the sender's own side keeps the two
     * branches identical instead of needing a separate expression per side.
     */
    private Update inboxUpdate(
            ConversationParticipants participants,
            ParticipantType side,
            Message message,
            boolean isRecipient) {

        ConversationId conversationId = participants.conversationId();

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":entityType", AttributeValue.fromS("CONVERSATION"));
        values.put(":conversationId", AttributeValue.fromS(conversationId.value()));
        values.put(":userId", AttributeValue.fromS(participants.userId()));
        values.put(":organizationId", AttributeValue.fromS(participants.organizationId()));
        values.put(":counterpartName",
                AttributeValue.fromS(participants.counterpartNameFor(side)));
        values.put(":lastMessagePreview", AttributeValue.fromS(message.body()));
        values.put(":lastMessageAt", AttributeValue.fromS(message.sentAt()));
        values.put(":lastMessageSenderId", AttributeValue.fromS(message.senderId()));
        values.put(":unreadIncrement", AttributeValue.fromN(isRecipient ? "1" : "0"));

        return Update.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS(partitionKey(conversationId, side)),
                        "SK", AttributeValue.fromS(CONVERSATION_SK_PREFIX + conversationId.value())
                ))
                .updateExpression(
                        "SET entityType = :entityType"
                                + ", conversationId = :conversationId"
                                + ", userId = :userId"
                                + ", organizationId = :organizationId"
                                + ", counterpartName = :counterpartName"
                                + ", lastMessagePreview = :lastMessagePreview"
                                + ", lastMessageAt = :lastMessageAt"
                                + ", lastMessageSenderId = :lastMessageSenderId"
                                + " ADD unreadCount :unreadIncrement")
                .expressionAttributeValues(values)
                .build();
    }

    @Override
    public void markRead(ConversationId conversationId, ParticipantType readerType) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.fromS(partitionKey(conversationId, readerType)),
                        "SK", AttributeValue.fromS(CONVERSATION_SK_PREFIX + conversationId.value())
                ))
                .updateExpression("SET unreadCount = :zero")
                // attribute_exists keeps an Update from conjuring a stub inbox row for a
                // conversation that was never started. Marking a nonexistent thread read is a
                // no-op rather than an error, matching the toggle semantics DynamoDbFavorite-
                // Repository documents.
                .conditionExpression("attribute_exists(PK)")
                .expressionAttributeValues(Map.of(":zero", AttributeValue.fromN("0")))
                .build();

        try {
            dynamoDbClient.updateItem(request);
        } catch (ConditionalCheckFailedException ignored) {
            // Nothing to mark read.
        }
    }

    private String partitionKey(ConversationId conversationId, ParticipantType side) {
        return side == ParticipantType.USER
                ? "USER#" + conversationId.userId()
                : "ORG#" + conversationId.organizationId();
    }

    private String partitionKey(String participantId, ParticipantType participantType) {
        return participantType == ParticipantType.USER
                ? "USER#" + participantId
                : "ORG#" + participantId;
    }

    private Conversation mapToConversation(Map<String, AttributeValue> item) {
        return new Conversation(
                stringOrNull(item, "conversationId"),
                stringOrNull(item, "userId"),
                stringOrNull(item, "organizationId"),
                stringOrNull(item, "counterpartName"),
                stringOrNull(item, "lastMessagePreview"),
                stringOrNull(item, "lastMessageAt"),
                stringOrNull(item, "lastMessageSenderId"),
                intOrZero(item, "unreadCount")
        );
    }

    private Message mapToMessage(Map<String, AttributeValue> item) {
        return new Message(
                stringOrNull(item, "conversationId"),
                stringOrNull(item, "messageId"),
                stringOrNull(item, "senderId"),
                ParticipantType.valueOf(item.get("senderType").s()),
                stringOrNull(item, "body"),
                stringOrNull(item, "sentAt")
        );
    }

    private static String stringOrNull(Map<String, AttributeValue> item, String attribute) {
        AttributeValue value = item.get(attribute);
        return value == null ? null : value.s();
    }

    private static int intOrZero(Map<String, AttributeValue> item, String attribute) {
        AttributeValue value = item.get(attribute);
        return value == null || value.n() == null ? 0 : Integer.parseInt(value.n());
    }
}
