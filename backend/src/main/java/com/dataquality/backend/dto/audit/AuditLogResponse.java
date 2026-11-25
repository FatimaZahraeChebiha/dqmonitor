package com.dataquality.backend.dto.audit;

import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String actorEmail,
        AuditAction action,
        String targetType,
        String targetId,
        String details,
        String ipAddress,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getActorEmail(), log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt()
        );
    }
}
