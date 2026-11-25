package com.dataquality.backend.service;

import com.dataquality.backend.dto.ai.AiChatMessageDto;
import com.dataquality.backend.dto.ai.AiChatRequest;
import com.dataquality.backend.dto.ai.AiConversationDetail;
import com.dataquality.backend.dto.ai.AiConversationReply;
import com.dataquality.backend.dto.ai.AiConversationSummary;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.model.AiConversation;
import com.dataquality.backend.model.AiMessage;
import com.dataquality.backend.repository.AiConversationRepository;
import com.dataquality.backend.repository.AiMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persists AI assistant conversations per user. Every lookup is scoped by the
 * caller's user id, so another user's conversation is indistinguishable from a
 * missing one (404) rather than leaking its existence.
 */
@Service
@RequiredArgsConstructor
public class AiConversationService {

    private static final int TITLE_MAX_LENGTH = 60;
    // Bounds the prompt sent to the (small, local) model on very long conversations.
    private static final int HISTORY_LIMIT = 20;

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiAssistantService aiAssistantService;

    public List<AiConversationSummary> list(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(AiConversationSummary::from)
                .toList();
    }

    public AiConversationSummary create(Long userId) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        return AiConversationSummary.from(conversationRepository.save(conversation));
    }

    public AiConversationDetail get(Long userId, Long conversationId) {
        AiConversation conversation = findOwned(userId, conversationId);
        List<AiChatMessageDto> messages = messageRepository.findByConversationIdOrderByIdAsc(conversationId).stream()
                .map(m -> new AiChatMessageDto(m.getRole(), m.getContent()))
                .toList();
        return new AiConversationDetail(conversation.getId(), conversation.getTitle(), messages);
    }

    public AiConversationSummary rename(Long userId, Long conversationId, String title) {
        AiConversation conversation = findOwned(userId, conversationId);
        conversation.setTitle(title.strip());
        return AiConversationSummary.from(conversationRepository.save(conversation));
    }

    @Transactional
    public void delete(Long userId, Long conversationId) {
        conversationRepository.delete(findOwned(userId, conversationId));
    }

    /**
     * The LLM call runs before anything is written, so a provider failure leaves
     * the conversation exactly as it was instead of storing a question with no answer.
     */
    public AiConversationReply sendMessage(Long userId, Long conversationId, String message) {
        AiConversation conversation = findOwned(userId, conversationId);
        List<AiMessage> stored = messageRepository.findByConversationIdOrderByIdAsc(conversationId);

        List<AiChatMessageDto> history = stored.stream()
                .skip(Math.max(0, stored.size() - HISTORY_LIMIT))
                .map(m -> new AiChatMessageDto(m.getRole(), m.getContent()))
                .toList();

        String reply = aiAssistantService.chat(new AiChatRequest(message, history)).reply();

        messageRepository.saveAll(List.of(
                newMessage(conversationId, "user", message),
                newMessage(conversationId, "assistant", reply)));

        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(titleFrom(message));
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return new AiConversationReply(reply, conversation.getTitle());
    }

    private AiConversation findOwned(Long userId, Long conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation introuvable: " + conversationId));
    }

    private AiMessage newMessage(Long conversationId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String titleFrom(String message) {
        String singleLine = message.strip().replaceAll("\\s+", " ");
        return singleLine.length() <= TITLE_MAX_LENGTH
                ? singleLine
                : singleLine.substring(0, TITLE_MAX_LENGTH - 1).stripTrailing() + "…";
    }
}
