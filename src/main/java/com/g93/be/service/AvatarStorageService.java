package com.g93.be.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AvatarStorageService {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final Path avatarRoot;
    private final String publicPrefix;

    public AvatarStorageService(
            @Value("${app.avatar.storage-dir}") String avatarStorageDir,
            @Value("${server.servlet.context-path:/api/v1}") String contextPath) {
        this.avatarRoot = Path.of(avatarStorageDir).toAbsolutePath().normalize();
        String normalizedContextPath = contextPath == null || contextPath.isBlank() ? "" : contextPath;
        this.publicPrefix = normalizedContextPath + "/files/avatars/";
    }

    public StoredAvatar store(Long doctorId, MultipartFile file) {
        validateFile(file);
        try {
            byte[] header = readHeader(file);
            String extension = detectExtension(header);
            if (extension == null) {
                throw new IllegalArgumentException("Avatar must be a PNG, JPEG, or WEBP image");
            }

            Path doctorDirectory = avatarRoot.resolve(String.valueOf(doctorId)).normalize();
            ensureWithinAvatarRoot(doctorDirectory);
            Files.createDirectories(doctorDirectory);

            String fileName = UUID.randomUUID() + "." + extension;
            Path destination = doctorDirectory.resolve(fileName).normalize();
            ensureWithinAvatarRoot(destination);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            String relativePath = avatarRoot.relativize(destination).toString().replace('\\', '/');
            return new StoredAvatar(publicPrefix + relativePath, extension);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store avatar", exception);
        }
    }

    public void delete(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(publicPrefix)) return;
        Path target = avatarRoot.resolve(publicUrl.substring(publicPrefix.length())).normalize();
        ensureWithinAvatarRoot(target);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete previous avatar", exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Avatar file must not exceed 5 MB");
        }
    }

    private byte[] readHeader(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(12);
        }
    }

    private String detectExtension(byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "png";
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private void ensureWithinAvatarRoot(Path path) {
        if (!path.startsWith(avatarRoot)) {
            throw new IllegalArgumentException("Invalid avatar storage path");
        }
    }

    public record StoredAvatar(String publicUrl, String extension) {
    }
}
