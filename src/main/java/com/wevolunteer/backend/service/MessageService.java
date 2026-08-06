package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.ConversationResponse;
import com.wevolunteer.backend.dto.MessageResponse;
import com.wevolunteer.backend.dto.SendMessageRequest;
import com.wevolunteer.backend.dto.StartConversationRequest;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Conversation;
import com.wevolunteer.backend.model.ConversationId;
import com.wevolunteer.backend.model.ConversationParticipants;
import com.wevolunteer.backend.model.Message;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.ParticipantType;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.MessageRepository;
import com.wevolunteer.backend.repository.OrganizationRepository;
import com.wevolunteer.backend.repository.RegistrationRepository;
import com.wevolunteer.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Direct messaging between volunteers and organizations.
 *
 * <p><strong>Who may start a thread.</strong> A volunteer may message any organization —
 * asking a question before registering is the main reason messaging exists. An organization may
 * only message volunteers who have registered for one of its opportunities, which keeps the
 * inbox from becoming an outbound channel for organizations that have no relationship with the
 * volunteer. Once a thread exists either side may reply freely; the gate is on creation only.
 *
 * <p><strong>Who is who.</strong> Cognito issues one subject per account and the access token
 * carries no role claim, so the sender's type is resolved by looking the subject up as a user
 * and then as an organization. That is one extra read per send. A {@code custom:role} claim
 * would remove it, and this class is the only place that would change.
 */
@Service
public class MessageService {

    private static final int PREVIEW_MAX_LENGTH = 140;

    /**
     * Fixed-width UTC, deliberately not {@code Instant#toString} or {@code LocalDateTime#now}.
     *
     * <p>Both of those vary in width — {@code Instant} omits trailing zeros in the fraction, so
     * {@code ...T10:00:00Z} and {@code ...T10:00:00.500Z} compare in the wrong order (at the
     * fraction position, 'Z' sorts after '.'). Since the timestamp leads a DynamoDB sort key,
     * that would silently misorder threads. A fixed three-digit fraction sorts correctly as a
     * string.
     */
    private static final DateTimeFormatter SENT_AT_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RegistrationRepository registrationRepository;
    private final Clock clock;

    // Explicit because this class has two constructors. Spring only infers constructor
    // injection when there is exactly one; with two and no annotation it falls back to a
    // no-arg constructor that does not exist, and the context fails to start. Every other
    // service in this codebase has a single constructor, which is why none of them need this.
    @Autowired
    public MessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            RegistrationRepository registrationRepository) {

        this(messageRepository, userRepository, organizationRepository, registrationRepository,
                Clock.systemUTC());
    }

    /** Test seam: lets a test pin {@code sentAt} without touching the Spring context. */
    MessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            RegistrationRepository registrationRepository,
            Clock clock) {

        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.registrationRepository = registrationRepository;
        this.clock = clock;
    }

    public List<ConversationResponse> getMyConversations(String participantId) {
        ParticipantType participantType = resolveParticipantType(participantId);

        return messageRepository
                .findConversationsByParticipant(participantId, participantType)
                .stream()
                .map(conversation -> toResponse(conversation, participantType))
                .toList();
    }

    /**
     * Every message in a thread, oldest first.
     *
     * <p>Authorization is a string check: the conversation id encodes both participants, so
     * being named in it <em>is</em> being a participant. No profile lookup, no read of the
     * conversation row.
     */
    public List<MessageResponse> getMessages(String participantId, String conversationIdValue) {
        ConversationId conversationId = requireParticipant(participantId, conversationIdValue);

        return messageRepository.findMessages(conversationId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ConversationResponse startConversation(
            String senderId, StartConversationRequest request) {

        ConversationParticipants participants =
                resolveParticipantsForNewConversation(senderId, request.recipientId());

        ParticipantType senderType = senderType(participants, senderId);

        append(participants, senderId, senderType, request.body());

        return toResponse(
                messageRepository
                        .findConversation(participants.conversationId(), senderType)
                        .orElseThrow(() -> new IllegalStateException(
                                "Conversation snapshot missing immediately after write: "
                                        + participants.conversationId().value())),
                senderType);
    }

    public MessageResponse sendMessage(
            String senderId, String conversationIdValue, SendMessageRequest request) {

        ConversationId conversationId = requireParticipant(senderId, conversationIdValue);
        ParticipantType senderType = conversationId.participantType(senderId);

        // Both profiles are read because the write refreshes both inbox snapshots, and a name
        // may have changed since the thread started. Replying is not re-gated: the registration
        // check guards thread creation, and an organization that was allowed to open a thread
        // stays allowed to continue it even if the volunteer later cancels.
        ConversationParticipants participants = loadParticipants(conversationId);

        Message message = append(participants, senderId, senderType, request.body());

        return toResponse(message);
    }

    public void markRead(String participantId, String conversationIdValue) {
        ConversationId conversationId = requireParticipant(participantId, conversationIdValue);

        messageRepository.markRead(conversationId, conversationId.participantType(participantId));
    }

    private Message append(
            ConversationParticipants participants,
            String senderId,
            ParticipantType senderType,
            String body) {

        String trimmedBody = body.strip();

        if (trimmedBody.isEmpty()) {
            throw new IllegalArgumentException("A message must contain some text.");
        }

        Message message = new Message(
                participants.conversationId().value(),
                UUID.randomUUID().toString(),
                senderId,
                senderType,
                trimmedBody,
                SENT_AT_FORMAT.format(clock.instant()));

        messageRepository.saveMessage(message, participants);

        return message;
    }

    /**
     * Resolves both sides of a not-yet-existing conversation and applies the creation gate.
     *
     * <p>The sender's type is discovered rather than trusted, which is what makes
     * {@code recipientId} safe to accept from the client: a volunteer's recipient is always
     * looked up as an organization and vice versa, so a caller cannot address the wrong table
     * by lying about who they are.
     */
    private ConversationParticipants resolveParticipantsForNewConversation(
            String senderId, String recipientId) {

        if (senderId.equals(recipientId)) {
            throw new IllegalArgumentException("You cannot start a conversation with yourself.");
        }

        Optional<User> senderAsUser = userRepository.findById(senderId);

        if (senderAsUser.isPresent()) {
            Organization recipient = organizationRepository.findById(recipientId)
                    .orElseThrow(() ->
                            new NotFoundException("Organization not found: " + recipientId));

            return new ConversationParticipants(
                    senderAsUser.get().userId(),
                    senderAsUser.get().name(),
                    recipient.organizationId(),
                    recipient.name());
        }

        Organization senderAsOrganization = organizationRepository.findById(senderId)
                .orElseThrow(() -> new NotFoundException(
                        "No volunteer or organization profile found for: " + senderId));

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new NotFoundException("Volunteer not found: " + recipientId));

        requireRegistrationWith(recipient.userId(), senderAsOrganization.organizationId());

        return new ConversationParticipants(
                recipient.userId(),
                recipient.name(),
                senderAsOrganization.organizationId(),
                senderAsOrganization.name());
    }

    /**
     * One query on the volunteer's own partition, reused rather than reading each of the
     * organization's opportunities and their registration lists.
     */
    private void requireRegistrationWith(String userId, String organizationId) {
        boolean hasRegistered = registrationRepository.findByUserId(userId).stream()
                .map(Registration::organizationId)
                .anyMatch(organizationId::equals);

        if (!hasRegistered) {
            throw new ForbiddenException(
                    "An organization can only message volunteers who have registered for one of "
                            + "its opportunities.");
        }
    }

    private ConversationParticipants loadParticipants(ConversationId conversationId) {
        User user = userRepository.findById(conversationId.userId())
                .orElseThrow(() -> new NotFoundException(
                        "Volunteer not found: " + conversationId.userId()));

        Organization organization = organizationRepository
                .findById(conversationId.organizationId())
                .orElseThrow(() -> new NotFoundException(
                        "Organization not found: " + conversationId.organizationId()));

        return new ConversationParticipants(
                user.userId(), user.name(), organization.organizationId(), organization.name());
    }

    private ParticipantType resolveParticipantType(String participantId) {
        if (userRepository.findById(participantId).isPresent()) {
            return ParticipantType.USER;
        }

        if (organizationRepository.findById(participantId).isPresent()) {
            return ParticipantType.ORGANIZATION;
        }

        throw new NotFoundException(
                "No volunteer or organization profile found for: " + participantId);
    }

    private ConversationId requireParticipant(String participantId, String conversationIdValue) {
        ConversationId conversationId = ConversationId.parse(conversationIdValue);

        if (!conversationId.includes(participantId)) {
            throw new ForbiddenException("You are not a participant in this conversation.");
        }

        return conversationId;
    }

    private static ParticipantType senderType(
            ConversationParticipants participants, String senderId) {

        return participants.userId().equals(senderId)
                ? ParticipantType.USER
                : ParticipantType.ORGANIZATION;
    }

    private ConversationResponse toResponse(
            Conversation conversation, ParticipantType viewerType) {

        String counterpartId = viewerType == ParticipantType.USER
                ? conversation.organizationId()
                : conversation.userId();

        return new ConversationResponse(
                conversation.conversationId(),
                counterpartId,
                conversation.counterpartName(),
                toPreview(conversation.lastMessagePreview()),
                conversation.lastMessageAt(),
                conversation.unreadCount());
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.messageId(),
                message.conversationId(),
                message.senderId(),
                message.senderType(),
                message.body(),
                message.sentAt());
    }

    /**
     * Truncated at read time, not write time, so the stored snapshot stays the real message and
     * the preview length can change without a backfill.
     */
    private static String toPreview(String body) {
        if (body == null) {
            return null;
        }

        String collapsed = body.strip().replaceAll("\\s+", " ");

        return collapsed.length() <= PREVIEW_MAX_LENGTH
                ? collapsed
                : collapsed.substring(0, PREVIEW_MAX_LENGTH - 1).stripTrailing() + "…";
    }
}
