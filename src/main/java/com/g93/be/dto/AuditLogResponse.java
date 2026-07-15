package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {
    private Long id;
    private String username;
    private String title;
    private String description;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timeStamp;
}
