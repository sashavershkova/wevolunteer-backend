package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.ConversationResponse;
import com.wevolunteer.backend.dto.MessageResponse;
import com.wevolunteer.backend.dto.SendMessageRequest;
import com.wevolunteer.backend.dto.StartConversationRequest;
import com.wevolunteer.backend.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Messaging endpoints.
 *
 * <p>No endpoint takes a sender id. The caller is always {@code jwt.getSubject()}, matching
 * {@code FavoriteController}: a client can name who it is talking <em>to</em>, never who it is.
 */
@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/conversations/me")
    public List<ConversationResponse> getMyConversations(@AuthenticationPrincipal Jwt jwt) {
        return messageService.getMyConversations(jwt.getSubject());
    }

    @PostMapping("/conversations")
    public ConversationResponse startConversation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartConversationRequest request) {

        return messageService.startConversation(jwt.getSubject(), request);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageResponse> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId) {

        return messageService.getMessages(jwt.getSubject(), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public MessageResponse sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        return messageService.sendMessage(jwt.getSubject(), conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/read")
    public void markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId) {

        messageService.markRead(jwt.getSubject(), conversationId);
    }
}
