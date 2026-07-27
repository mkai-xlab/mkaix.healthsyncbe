package com.g93.be.service.impl;

import com.g93.be.entity.AuditLog;
import com.g93.be.entity.User;
import com.g93.be.repository.AuditLogRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.AuditLogService;
import com.g93.be.dto.AuditLogResponse;
import com.g93.be.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    @Override
    public void saveLogAsync(String username, String title, String description, String ipAddress, String userAgent) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("Cannot save audit log: User not found for username {}", username);
                return;
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setUser(userOpt.get());
            auditLog.setTitle(title);

            // Limit description length if it's too long for TEXT column (though TEXT is
            // usually 65KB)
            if (description != null && description.length() > 50000) {
                description = description.substring(0, 50000) + "...";
            }
            auditLog.setDescription(description);

            if (ipAddress != null && ipAddress.length() > 100) {
                ipAddress = ipAddress.substring(0, 100);
            }
            auditLog.setIpAddress(ipAddress);

            if (userAgent != null && userAgent.length() > 255) {
                userAgent = userAgent.substring(0, 255);
            }
            auditLog.setUserAgent(userAgent);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log async", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Pageable pageable) {
        Page<AuditLog> auditLogs = auditLogRepository.findAll(pageable);
        Page<AuditLogResponse> responsePage = auditLogs.map(log -> AuditLogResponse.builder()
                .id(log.getId())
                .username(log.getUser() != null ? log.getUser().getUsername() : "system")
                .title(log.getTitle())
                .description(log.getDescription())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .timeStamp(log.getTimeStamp())
                .build());
        return PageResponse.of(responsePage);
    }
}
