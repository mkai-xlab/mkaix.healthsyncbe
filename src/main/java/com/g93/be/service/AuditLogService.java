package com.g93.be.service;

import com.g93.be.dto.AuditLogResponse;
import com.g93.be.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {
    void saveLogAsync(String username, String title, String description, String ipAddress, String userAgent);
    PageResponse<AuditLogResponse> getAuditLogs(Pageable pageable);
}
