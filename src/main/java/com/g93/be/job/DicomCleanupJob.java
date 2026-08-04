package com.g93.be.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.DicomUploadSessionDTO;
import com.g93.be.dto.PendingDicomUploadDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class DicomCleanupJob {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Run every minute
    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredDicomSessions() {
        log.info("Running DicomCleanupJob to find expired upload sessions...");

        long now = System.currentTimeMillis();
        // Expire sessions older than 10 minutes (600,000 ms)
        long tenMinutesAgo = now - (10 * 60 * 1000);

        // Find all sessions in the ZSET with a score less than 10 minutes ago
        Set<String> expiredSessionIds = stringRedisTemplate.opsForZSet()
                .rangeByScore("uploadSessionTimeouts", 0, tenMinutesAgo);

        if (expiredSessionIds == null || expiredSessionIds.isEmpty()) {
            return;
        }

        for (String sessionId : expiredSessionIds) {
            log.info("Found expired session: {}. Starting cleanup...", sessionId);
            String redisKey = "uploadSession:" + sessionId;
            String sessionJson = stringRedisTemplate.opsForValue().get(redisKey);

            if (sessionJson != null) {
                try {
                    DicomUploadSessionDTO sessionDTO = objectMapper.readValue(sessionJson, DicomUploadSessionDTO.class);
                    // Delete physical files for all patients in the session
                    if (sessionDTO.getPatients() != null) {
                        for (PendingDicomUploadDTO pending : sessionDTO.getPatients().values()) {
                            deletePhysicalFiles(pending);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to parse expired session data for cleanup: " + sessionId, e);
                }
            }

            // Remove from cache and ZSET
            stringRedisTemplate.delete(redisKey);
            stringRedisTemplate.opsForZSet().remove("uploadSessionTimeouts", sessionId);
            log.info("Cleanup completed for session: {}", sessionId);
        }
    }

    private void deletePhysicalFiles(PendingDicomUploadDTO pending) {
        if (pending.getPhysicalFilePaths() != null) {
            for (String absolutePath : pending.getPhysicalFilePaths().values()) {
                try {
                    Path path = Paths.get(absolutePath);
                    Files.deleteIfExists(path);
                    log.info("Deleted expired file: {}", absolutePath);
                } catch (IOException e) {
                    log.error("Failed to delete physical file {}", absolutePath, e);
                }
            }
        }
    }
}
