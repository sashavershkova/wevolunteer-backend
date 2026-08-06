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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService")
class MessageServiceTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ORG_ID = "22222222-2222-2222-2222-222222222222";
    private static final String OTHER_ORG_ID = "33333333-3333-3333-3333-333333333333";
    private static final String CONVERSATION_ID = USER_ID + "_" + ORG_ID;

    private static final String USER_NAME = "Renata Murzina";
    private static final String ORG_NAME = "Seattle Food Bank";

    private static final Instant FIXED_NOW = Instant.parse("2026-08-04T17:05:09.250Z");
    private static final String FIXED_SENT_AT = "2026-08-04T17:05:09.250Z";

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository,
                userRepository,
                organizationRepository,
                registrationRepository,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("getMyConversations")
    class GetMyConversations {

        @Test
        @DisplayName("reads the volunteer's own partition and reports the organization as counterpart")
        void volunteerSeesOrganizationAsCounterpart() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(messageRepository.findConversationsByParticipant(USER_ID, ParticipantType.USER))
                    .thenReturn(List.of(conversation(ORG_NAME, 2)));

            List<ConversationResponse> result = messageService.getMyConversations(USER_ID);

            assertThat(result).singleElement().satisfies(response -> {
                assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
                assertThat(response.counterpartId()).isEqualTo(ORG_ID);
                assertThat(response.counterpartName()).isEqualTo(ORG_NAME);
                assertThat(response.unreadCount()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName("falls back to the organization lookup when the subject is not a volunteer")
        void organizationSeesVolunteerAsCounterpart() {
            when(userRepository.findById(ORG_ID)).thenReturn(Optional.empty());
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));
            when(messageRepository.findConversationsByParticipant(
                    ORG_ID, ParticipantType.ORGANIZATION))
                    .thenReturn(List.of(conversation(USER_NAME, 0)));

            List<ConversationResponse> result = messageService.getMyConversations(ORG_ID);

            assertThat(result).singleElement().satisfies(response -> {
                assertThat(response.counterpartId()).isEqualTo(USER_ID);
                assertThat(response.counterpartName()).isEqualTo(USER_NAME);
            });
        }

        @Test
        @DisplayName("rejects a subject that is neither a volunteer nor an organization")
        void rejectsUnknownSubject() {
            when(userRepository.findById("ghost")).thenReturn(Optional.empty());
            when(organizationRepository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.getMyConversations("ghost"))
                    .isInstanceOf(NotFoundException.class);

            verifyNoInteractions(messageRepository);
        }

        @Test
        @DisplayName("collapses whitespace and truncates the preview without altering what was stored")
        void truncatesPreview() {
            String longBody = "word ".repeat(80).strip();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(messageRepository.findConversationsByParticipant(USER_ID, ParticipantType.USER))
                    .thenReturn(List.of(new Conversation(
                            CONVERSATION_ID, USER_ID, ORG_ID, ORG_NAME,
                            "line one\n\n   line two", FIXED_SENT_AT, ORG_ID, 0)));

            assertThat(messageService.getMyConversations(USER_ID).getFirst().previewLine())
                    .isEqualTo("line one line two");

            when(messageRepository.findConversationsByParticipant(USER_ID, ParticipantType.USER))
                    .thenReturn(List.of(new Conversation(
                            CONVERSATION_ID, USER_ID, ORG_ID, ORG_NAME,
                            longBody, FIXED_SENT_AT, ORG_ID, 0)));

            assertThat(messageService.getMyConversations(USER_ID).getFirst().previewLine())
                    .hasSizeLessThanOrEqualTo(140)
                    .endsWith("…");
        }
    }

    @Nested
    @DisplayName("getMessages")
    class GetMessages {

        @Test
        @DisplayName("authorizes from the conversation id alone, with no profile lookup")
        void authorizesWithoutProfileLookup() {
            when(messageRepository.findMessages(new ConversationId(USER_ID, ORG_ID)))
                    .thenReturn(List.of(message()));

            List<MessageResponse> result = messageService.getMessages(USER_ID, CONVERSATION_ID);

            assertThat(result).singleElement().satisfies(response -> {
                assertThat(response.senderId()).isEqualTo(USER_ID);
                assertThat(response.senderType()).isEqualTo(ParticipantType.USER);
                assertThat(response.body()).isEqualTo("Is parking available?");
            });

            verifyNoInteractions(userRepository, organizationRepository);
        }

        @Test
        @DisplayName("refuses a subject that is not named in the conversation id")
        void refusesOutsider() {
            assertThatThrownBy(() -> messageService.getMessages("outsider", CONVERSATION_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not a participant");

            verifyNoInteractions(messageRepository);
        }

        @Test
        @DisplayName("rejects a malformed conversation id as a bad request, not a 403")
        void rejectsMalformedId() {
            assertThatThrownBy(() -> messageService.getMessages(USER_ID, "not-a-conversation"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("startConversation")
    class StartConversation {

        @Test
        @DisplayName("lets a volunteer message any organization without requiring a registration")
        void volunteerMayMessageAnyOrganization() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));
            when(messageRepository.findConversation(
                    new ConversationId(USER_ID, ORG_ID), ParticipantType.USER))
                    .thenReturn(Optional.of(conversation(ORG_NAME, 0)));

            ConversationResponse result = messageService.startConversation(
                    USER_ID, new StartConversationRequest(ORG_ID, "Is parking available?"));

            assertThat(result.conversationId()).isEqualTo(CONVERSATION_ID);
            verifyNoInteractions(registrationRepository);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).saveMessage(captor.capture(), any());
            assertThat(captor.getValue().senderType()).isEqualTo(ParticipantType.USER);
            assertThat(captor.getValue().sentAt()).isEqualTo(FIXED_SENT_AT);
        }

        @Test
        @DisplayName("lets an organization message a volunteer registered for one of its opportunities")
        void organizationMayMessageItsOwnVolunteer() {
            when(userRepository.findById(ORG_ID)).thenReturn(Optional.empty());
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(registrationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(registrationWith(ORG_ID)));
            when(messageRepository.findConversation(
                    new ConversationId(USER_ID, ORG_ID), ParticipantType.ORGANIZATION))
                    .thenReturn(Optional.of(conversation(USER_NAME, 0)));

            messageService.startConversation(
                    ORG_ID, new StartConversationRequest(USER_ID, "Your shift is confirmed."));

            ArgumentCaptor<ConversationParticipants> captor =
                    ArgumentCaptor.forClass(ConversationParticipants.class);
            verify(messageRepository).saveMessage(any(), captor.capture());

            ConversationParticipants participants = captor.getValue();
            assertThat(participants.userId()).isEqualTo(USER_ID);
            assertThat(participants.userName()).isEqualTo(USER_NAME);
            assertThat(participants.organizationId()).isEqualTo(ORG_ID);
            assertThat(participants.organizationName()).isEqualTo(ORG_NAME);
        }

        @Test
        @DisplayName("stops an organization messaging a volunteer who never registered with it")
        void organizationMayNotColdMessage() {
            when(userRepository.findById(ORG_ID)).thenReturn(Optional.empty());
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(registrationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(registrationWith(OTHER_ORG_ID)));

            assertThatThrownBy(() -> messageService.startConversation(
                    ORG_ID, new StartConversationRequest(USER_ID, "Come volunteer with us!")))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("registered for one of its opportunities");

            verify(messageRepository, never()).saveMessage(any(), any());
        }

        @Test
        @DisplayName("resolves the recipient against the opposite table, so a lied-about role cannot cross wires")
        void recipientIsResolvedAgainstTheOppositeTable() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(organizationRepository.findById("not-an-org")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.startConversation(
                    USER_ID, new StartConversationRequest("not-an-org", "Hello")))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Organization not found");
        }

        @Test
        @DisplayName("refuses a conversation with yourself")
        void refusesSelfConversation() {
            assertThatThrownBy(() -> messageService.startConversation(
                    USER_ID, new StartConversationRequest(USER_ID, "Hello")))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(messageRepository);
        }

        @Test
        @DisplayName("refuses a body that is only whitespace")
        void refusesBlankBody() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));

            assertThatThrownBy(() -> messageService.startConversation(
                    USER_ID, new StartConversationRequest(ORG_ID, "   \n  ")))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(messageRepository, never()).saveMessage(any(), any());
        }
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("does not re-apply the creation gate when replying to an existing thread")
        void replyIsNotReGated() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));

            MessageResponse result = messageService.sendMessage(
                    ORG_ID, CONVERSATION_ID, new SendMessageRequest("See you Saturday."));

            assertThat(result.senderId()).isEqualTo(ORG_ID);
            assertThat(result.senderType()).isEqualTo(ParticipantType.ORGANIZATION);
            assertThat(result.sentAt()).isEqualTo(FIXED_SENT_AT);
            verifyNoInteractions(registrationRepository);
        }

        @Test
        @DisplayName("strips the stored body so a trailing newline never becomes part of the message")
        void stripsBody() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));

            MessageResponse result = messageService.sendMessage(
                    USER_ID, CONVERSATION_ID, new SendMessageRequest("  Thanks!\n"));

            assertThat(result.body()).isEqualTo("Thanks!");
        }

        @Test
        @DisplayName("refuses a subject that is not a participant before touching any repository")
        void refusesOutsider() {
            assertThatThrownBy(() -> messageService.sendMessage(
                    "outsider", CONVERSATION_ID, new SendMessageRequest("Hi")))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(messageRepository, userRepository, organizationRepository);
        }
    }

    @Nested
    @DisplayName("markRead")
    class MarkRead {

        @Test
        @DisplayName("clears only the caller's own side of the conversation")
        void clearsCallersSide() {
            messageService.markRead(ORG_ID, CONVERSATION_ID);

            verify(messageRepository).markRead(
                    new ConversationId(USER_ID, ORG_ID), ParticipantType.ORGANIZATION);
        }

        @Test
        @DisplayName("refuses an outsider")
        void refusesOutsider() {
            assertThatThrownBy(() -> messageService.markRead("outsider", CONVERSATION_ID))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(messageRepository);
        }
    }

    private static User user() {
        return new User(USER_ID, USER_NAME, "renata@example.com", "VOLUNTEER");
    }

    private static Organization organization() {
        return new Organization(
                ORG_ID, ORG_NAME, "Feeding Seattle", "hello@sfb.org", "https://sfb.org");
    }

    private static Conversation conversation(String counterpartName, int unreadCount) {
        return new Conversation(
                CONVERSATION_ID,
                USER_ID,
                ORG_ID,
                counterpartName,
                "Is parking available?",
                FIXED_SENT_AT,
                USER_ID,
                unreadCount);
    }

    private static Message message() {
        return new Message(
                CONVERSATION_ID,
                "msg-1",
                USER_ID,
                ParticipantType.USER,
                "Is parking available?",
                FIXED_SENT_AT);
    }

    private static Registration registrationWith(String organizationId) {
        return new Registration(
                USER_ID, "opp-1", "Beach Cleanup", "2026-08-01", "Seattle, WA",
                organizationId, ORG_NAME, "ACTIVE", USER_NAME, "renata@example.com",
                "2026-07-29T10:00:00", null, null, null);
    }
}
