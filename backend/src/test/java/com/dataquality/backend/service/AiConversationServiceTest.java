package com.dataquality.backend.service;

import com.dataquality.backend.dto.ai.AiChatMessageDto;
import com.dataquality.backend.dto.ai.AiChatRequest;
import com.dataquality.backend.dto.ai.AiChatResponse;
import com.dataquality.backend.dto.ai.AiConversationDetail;
import com.dataquality.backend.dto.ai.AiConversationReply;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.model.AiConversation;
import com.dataquality.backend.model.AiMessage;
import com.dataquality.backend.repository.AiConversationRepository;
import com.dataquality.backend.repository.AiMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CONVERSATION_ID = 42L;

    @Mock private AiConversationRepository conversationRepository;
    @Mock private AiMessageRepository messageRepository;
    @Mock private AiAssistantService aiAssistantService;

    @InjectMocks
    private AiConversationService service;

    private AiConversation conversation(String title) {
        AiConversation conversation = new AiConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        conversation.setTitle(title);
        return conversation;
    }

    private AiMessage message(String role, String content) {
        AiMessage message = new AiMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    @Test
    void anotherUsersConversationIsReportedAsNotFound() {
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L, CONVERSATION_ID)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.rename(99L, CONVERSATION_ID, "x")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L, CONVERSATION_ID)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.sendMessage(99L, CONVERSATION_ID, "hi")).isInstanceOf(ResourceNotFoundException.class);
        verify(conversationRepository, never()).delete(any());
    }

    @Test
    void getReturnsStoredMessagesInOrder() {
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation("Score")));
        when(messageRepository.findByConversationIdOrderByIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(message("user", "Q"), message("assistant", "A")));

        AiConversationDetail detail = service.get(USER_ID, CONVERSATION_ID);

        assertThat(detail.title()).isEqualTo("Score");
        assertThat(detail.messages()).containsExactly(new AiChatMessageDto("user", "Q"), new AiChatMessageDto("assistant", "A"));
    }

    @Test
    void firstMessageNamesTheConversationAndBothTurnsArePersisted() {
        AiConversation conversation = conversation(null);
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByIdAsc(CONVERSATION_ID)).thenReturn(List.of());
        when(aiAssistantService.chat(new AiChatRequest("Quel est mon score ?", List.of()))).thenReturn(new AiChatResponse("99 %"));

        AiConversationReply reply = service.sendMessage(USER_ID, CONVERSATION_ID, "Quel est mon score ?");

        assertThat(reply.reply()).isEqualTo("99 %");
        assertThat(reply.title()).isEqualTo("Quel est mon score ?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<AiMessage>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(messageRepository).saveAll(saved.capture());
        List<AiMessage> savedMessages = new ArrayList<>();
        saved.getValue().forEach(savedMessages::add);
        assertThat(savedMessages).extracting(AiMessage::getRole).containsExactly("user", "assistant");
        assertThat(savedMessages).extracting(AiMessage::getContent).containsExactly("Quel est mon score ?", "99 %");
        verify(conversationRepository).save(conversation);
    }

    @Test
    void existingTitleIsKeptAndPriorMessagesAreSentAsHistory() {
        AiConversation conversation = conversation("Mon titre");
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(message("user", "Q1"), message("assistant", "A1")));
        when(aiAssistantService.chat(new AiChatRequest("Q2", List.of(
                new AiChatMessageDto("user", "Q1"), new AiChatMessageDto("assistant", "A1")))))
                .thenReturn(new AiChatResponse("A2"));

        AiConversationReply reply = service.sendMessage(USER_ID, CONVERSATION_ID, "Q2");

        assertThat(reply.title()).isEqualTo("Mon titre");
    }

    @Test
    void providerFailureStoresNothing() {
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation(null)));
        when(messageRepository.findByConversationIdOrderByIdAsc(CONVERSATION_ID)).thenReturn(List.of());
        when(aiAssistantService.chat(any())).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.sendMessage(USER_ID, CONVERSATION_ID, "hi")).isInstanceOf(IllegalStateException.class);

        verify(messageRepository, never()).saveAll(any());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void longFirstMessageProducesATruncatedSingleLineTitle() {
        AiConversation conversation = conversation(null);
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByIdAsc(CONVERSATION_ID)).thenReturn(List.of());
        when(aiAssistantService.chat(any())).thenReturn(new AiChatResponse("ok"));

        String longMessage = "mot ".repeat(40) + "\nfin";
        AiConversationReply reply = service.sendMessage(USER_ID, CONVERSATION_ID, longMessage);

        assertThat(reply.title()).hasSizeLessThanOrEqualTo(60).endsWith("…").doesNotContain("\n");
    }

    @Test
    void renameStripsWhitespace() {
        AiConversation conversation = conversation("Ancien");
        when(conversationRepository.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(conversation)).thenReturn(conversation);

        assertThat(service.rename(USER_ID, CONVERSATION_ID, "  Nouveau  ").title()).isEqualTo("Nouveau");
    }
}
