package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.ConversationResponse;
import com.wevolunteer.backend.dto.MessageResponse;
import com.wevolunteer.backend.dto.SendMessageRequest;
import com.wevolunteer.backend.dto.StartConversationRequest;
import com.wevolunteer.backend.model.ParticipantType;
import com.wevolunteer.backend.service.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController")
class MessageControllerTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ORG_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CONVERSATION_ID = USER_ID + "_" + ORG_ID;

    @Mock
    private MessageService messageService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private MessageController messageController;

    @Test
    @DisplayName("getMyConversations resolves the caller from the JWT subject")
    void getMyConversationsResolvesCallerFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        List<ConversationResponse> expected = List.of(conversationResponse());
        when(messageService.getMyConversations(USER_ID)).thenReturn(expected);

        assertThat(messageController.getMyConversations(jwt)).isSameAs(expected);
    }

    @Test
    @DisplayName("startConversation takes the sender from the JWT and never from the request body")
    void startConversationTakesSenderFromJwt() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        StartConversationRequest request =
                new StartConversationRequest(ORG_ID, "Is parking available?");
        ConversationResponse expected = conversationResponse();
        when(messageService.startConversation(USER_ID, request)).thenReturn(expected);

        assertThat(messageController.startConversation(jwt, request)).isSameAs(expected);
        verify(messageService).startConversation(USER_ID, request);
        verifyNoMoreInteractions(messageService);
    }

    @Test
    @DisplayName("getMessages passes the JWT subject through for the participant check")
    void getMessagesPassesJwtSubject() {
        when(jwt.getSubject()).thenReturn(ORG_ID);
        List<MessageResponse> expected = List.of(messageResponse());
        when(messageService.getMessages(ORG_ID, CONVERSATION_ID)).thenReturn(expected);

        assertThat(messageController.getMessages(jwt, CONVERSATION_ID)).isSameAs(expected);
    }

    @Test
    @DisplayName("sendMessage passes the JWT subject through as the sender")
    void sendMessagePassesJwtSubject() {
        when(jwt.getSubject()).thenReturn(ORG_ID);
        SendMessageRequest request = new SendMessageRequest("See you Saturday.");
        MessageResponse expected = messageResponse();
        when(messageService.sendMessage(ORG_ID, CONVERSATION_ID, request)).thenReturn(expected);

        assertThat(messageController.sendMessage(jwt, CONVERSATION_ID, request))
                .isSameAs(expected);
        verify(messageService).sendMessage(ORG_ID, CONVERSATION_ID, request);
        verifyNoMoreInteractions(messageService);
    }

    @Test
    @DisplayName("markRead clears the calling subject's own unread count")
    void markReadPassesJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);

        messageController.markRead(jwt, CONVERSATION_ID);

        verify(messageService).markRead(USER_ID, CONVERSATION_ID);
        verifyNoMoreInteractions(messageService);
    }

    private static ConversationResponse conversationResponse() {
        return new ConversationResponse(
                CONVERSATION_ID,
                ORG_ID,
                "Seattle Food Bank",
                "Is parking available?",
                "2026-08-04T17:05:09.250Z",
                1);
    }

    private static MessageResponse messageResponse() {
        return new MessageResponse(
                "msg-1",
                CONVERSATION_ID,
                ORG_ID,
                ParticipantType.ORGANIZATION,
                "See you Saturday.",
                "2026-08-04T17:05:09.250Z");
    }
}
