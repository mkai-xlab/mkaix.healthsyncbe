package com.g93.be.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterReturning(pointcut = "@annotation(logAction)", returning = "result")
    public void logAfter(JoinPoint joinPoint, LogAction logAction, Object result) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                return;
            }
            String username = authentication.getName();

            // Extract HTTP Request info
            String ipAddress = null;
            String userAgent = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ipAddress = request.getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isBlank()) {
                    ipAddress = request.getRemoteAddr();
                }
                userAgent = request.getHeader("User-Agent");
            }

            // Extract method arguments (safely serialize to JSON string)
            Object[] args = joinPoint.getArgs();
            List<Object> serializableArgs = new ArrayList<>();
            for (Object arg : args) {
                // Filter out non-serializable objects like HttpServletRequest, MultipartFile, etc.
                if (arg instanceof HttpServletRequest || arg instanceof org.springframework.web.multipart.MultipartFile) {
                    serializableArgs.add(arg.getClass().getSimpleName());
                } else {
                    serializableArgs.add(arg);
                }
            }

            String description = "";
            try {
                description = objectMapper.writeValueAsString(serializableArgs);
            } catch (Exception e) {
                description = "Failed to serialize arguments: " + e.getMessage();
            }

            auditLogService.saveLogAsync(username, logAction.value(), description, ipAddress, userAgent);

        } catch (Exception e) {
            log.error("Error in AuditLogAspect", e);
        }
    }
}
