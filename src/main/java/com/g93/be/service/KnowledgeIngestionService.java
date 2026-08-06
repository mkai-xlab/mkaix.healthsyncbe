package com.g93.be.service;

import com.g93.be.config.ChatProperties;
import com.g93.be.chat.KnowledgeIndexRequestedEvent;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.dto.KnowledgeUrlRequest;
import com.g93.be.entity.KnowledgeAccessScope;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeDocumentStatus;
import com.g93.be.entity.KnowledgeSourceType;
import com.g93.be.entity.User;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.repository.KnowledgeDocumentRepository;
import com.g93.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class KnowledgeIngestionService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");

    private final KnowledgeDocumentRepository repository;
    private final UserRepository userRepository;
    private final VectorStore vectorStore;
    private final RestClient knowledgeRestClient;
    private final ChatProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public KnowledgeDocumentResponse upload(
            MultipartFile file, String title, KnowledgeAccessScope scope, String username) {
        validateFile(file);
        byte[] bytes = readBytes(file);
        String checksum = sha256(bytes);
        if (repository.existsBySourceKey("file:" + checksum)) {
            throw new IllegalArgumentException("This document has already been uploaded");
        }
        User user = findUser(username);
        Path storagePath = storeFile(bytes, file.getOriginalFilename());

        KnowledgeDocument document = new KnowledgeDocument();
        document.setSourceKey("file:" + checksum);
        document.setTitle(normalizeTitle(title, file.getOriginalFilename()));
        document.setSourceType(KnowledgeSourceType.FILE);
        document.setOriginalName(safeFileName(file.getOriginalFilename()));
        document.setContentType(file.getContentType());
        document.setStoragePath(storagePath.toString());
        document.setChecksum(checksum);
        document.setAccessScope(scope == null ? KnowledgeAccessScope.ALL : scope);
        document.setUploadedBy(user);
        document = repository.save(document);
        requestIndex(document.getId());
        return toResponse(document);
    }

    @Transactional
    public KnowledgeDocumentResponse addUrl(KnowledgeUrlRequest request, String username) {
        URI uri = validatePublicUrl(request.url());
        String sourceKey = "url:" + sha256(uri.normalize().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (repository.existsBySourceKey(sourceKey)) {
            throw new IllegalArgumentException("This URL has already been added");
        }
        byte[] bytes = download(uri);
        Path storagePath = storeFile(bytes, "source.html");

        KnowledgeDocument document = new KnowledgeDocument();
        document.setSourceKey(sourceKey);
        document.setTitle(request.title().trim());
        document.setSourceType(KnowledgeSourceType.URL);
        document.setSourceUrl(uri.toString());
        document.setOriginalName("source.html");
        document.setContentType("text/html");
        document.setStoragePath(storagePath.toString());
        document.setChecksum(sha256(bytes));
        document.setAccessScope(request.accessScope() == null ? KnowledgeAccessScope.ALL : request.accessScope());
        document.setUploadedBy(findUser(username));
        document = repository.save(document);
        requestIndex(document.getId());
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> getAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public KnowledgeDocumentResponse reindex(Long id) {
        KnowledgeDocument document = findDocument(id);
        document.setStatus(KnowledgeDocumentStatus.PENDING);
        document.setErrorMessage(null);
        repository.save(document);
        requestIndex(id);
        return toResponse(document);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeDocument document = findDocument(id);
        vectorStore.delete(sourceFilter(document.getSourceKey()));
        repository.delete(document);
        if (document.getStoragePath() != null) {
            try {
                Files.deleteIfExists(Path.of(document.getStoragePath()));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not delete stored knowledge document", exception);
            }
        }
    }

    private void requestIndex(Long documentId) {
        eventPublisher.publishEvent(new KnowledgeIndexRequestedEvent(documentId));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file is required");
        }
        if (file.getSize() > properties.maxDocumentBytes()) {
            throw new IllegalArgumentException("Document exceeds the configured size limit");
        }
        String extension = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only PDF, DOC, DOCX, and TXT documents are supported");
        }
    }

    private byte[] download(URI uri) {
        byte[] bytes = knowledgeRestClient.get().uri(uri).exchange((request, response) -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("URL returned HTTP " + response.getStatusCode().value());
            }
            int readLimit = (int) Math.min(Integer.MAX_VALUE - 1L, properties.maxUrlBytes() + 1L);
            try (java.io.InputStream input = response.getBody()) {
                return input.readNBytes(readLimit);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read URL content", exception);
            }
        });
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("The URL returned no content");
        }
        if (bytes.length > properties.maxUrlBytes()) {
            throw new IllegalArgumentException("URL content exceeds the configured size limit");
        }
        return bytes;
    }

    private URI validatePublicUrl(String rawUrl) {
        URI uri = URI.create(rawUrl.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Only absolute HTTP or HTTPS URLs are supported");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Private or local URLs are not allowed");
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("URL host could not be resolved", exception);
        }
        return uri;
    }

    private Path storeFile(byte[] bytes, String originalName) {
        try {
            Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            String extension = extension(originalName);
            Path path = root.resolve(UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension)).normalize();
            if (!path.startsWith(root)) {
                throw new IllegalStateException("Invalid knowledge storage path");
            }
            Files.write(path, bytes);
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store knowledge document", exception);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read uploaded document", exception);
        }
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private KnowledgeDocument findDocument(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(document.getId(), document.getTitle(), document.getSourceType().name(),
                document.getSourceUrl(), document.getOriginalName(), document.getAccessScope().name(),
                document.getStatus().name(), document.getChunkCount(), document.getErrorMessage(),
                document.getCreatedAt(), document.getIndexedAt());
    }

    private String normalizeTitle(String title, String fileName) {
        String value = title == null || title.isBlank() ? safeFileName(fileName) : title.trim();
        if (value == null || value.isBlank()) {
            return "Medical document";
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private String safeFileName(String name) {
        return name == null ? null : Path.of(name).getFileName().toString();
    }

    private String extension(String name) {
        String safe = safeFileName(name);
        int index = safe == null ? -1 : safe.lastIndexOf('.');
        return index < 0 ? "" : safe.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private org.springframework.ai.vectorstore.filter.Filter.Expression sourceFilter(String sourceKey) {
        return new FilterExpressionBuilder().eq("sourceKey", sourceKey).build();
    }

}
