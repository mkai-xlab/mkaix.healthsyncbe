package com.g93.be.service;

import com.g93.be.dto.KnowledgeBatchUploadItemResponse;
import com.g93.be.dto.KnowledgeBatchUploadResponse;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.entity.KnowledgeAccessScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeBatchIngestionService {

    static final int MAX_BATCH_FILES = 10;

    private final KnowledgeIngestionService ingestionService;

    public KnowledgeBatchUploadResponse upload(
            List<MultipartFile> files, KnowledgeAccessScope scope, String username) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one document file is required");
        }
        if (files.size() > MAX_BATCH_FILES) {
            throw new IllegalArgumentException("A batch can contain at most " + MAX_BATCH_FILES + " documents");
        }

        List<KnowledgeBatchUploadItemResponse> items = new ArrayList<>(files.size());
        int accepted = 0;
        for (MultipartFile file : files) {
            String originalName = file == null ? null : file.getOriginalFilename();
            try {
                KnowledgeDocumentResponse document = ingestionService.upload(
                        file, null, scope, username);
                items.add(new KnowledgeBatchUploadItemResponse(originalName, true, document, null));
                accepted++;
            } catch (IllegalArgumentException exception) {
                items.add(new KnowledgeBatchUploadItemResponse(
                        originalName, false, null, exception.getMessage()));
            } catch (RuntimeException exception) {
                log.error("Could not accept knowledge document {} from batch", originalName, exception);
                items.add(new KnowledgeBatchUploadItemResponse(
                        originalName, false, null, "Could not accept document"));
            }
        }

        return new KnowledgeBatchUploadResponse(
                files.size(), accepted, files.size() - accepted, List.copyOf(items));
    }
}
