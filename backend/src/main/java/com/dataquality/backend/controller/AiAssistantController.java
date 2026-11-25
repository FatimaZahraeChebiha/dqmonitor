package com.dataquality.backend.controller;

import com.dataquality.backend.dto.ai.AiConversationDetail;
import com.dataquality.backend.dto.ai.AiConversationReply;
import com.dataquality.backend.dto.ai.AiConversationSummary;
import com.dataquality.backend.dto.ai.AiRenameRequest;
import com.dataquality.backend.dto.ai.AiSendMessageRequest;
import com.dataquality.backend.security.AuthPrincipal;
import com.dataquality.backend.service.AiConversationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/conversations")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "Persistent, per-user conversations with the assistant, grounded in the platform's own dashboard, report, and rule data")
public class AiAssistantController {

    private final AiConversationService conversationService;

    @GetMapping
    public ResponseEntity<List<AiConversationSummary>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(conversationService.list(principal.userId()));
    }

    @PostMapping
    public ResponseEntity<AiConversationSummary> create(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.create(principal.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiConversationDetail> get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(conversationService.get(principal.userId(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AiConversationSummary> rename(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id, @Valid @RequestBody AiRenameRequest request) {
        return ResponseEntity.ok(conversationService.rename(principal.userId(), id, request.title()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        conversationService.delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<AiConversationReply> sendMessage(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id, @Valid @RequestBody AiSendMessageRequest request) {
        return ResponseEntity.ok(conversationService.sendMessage(principal.userId(), id, request.message()));
    }
}
