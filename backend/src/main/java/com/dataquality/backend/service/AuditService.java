package com.dataquality.backend.service;

import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.AuditLog;
import com.dataquality.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(AuditAction action, String actorEmail, String targetType, String targetId, String details, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setActorEmail(actorEmail);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        entry.setIpAddress(ipAddress);
        auditLogRepository.save(entry);
    }
}
