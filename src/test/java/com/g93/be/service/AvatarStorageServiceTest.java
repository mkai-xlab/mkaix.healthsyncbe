package com.g93.be.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesValidatedImageUnderDoctorDirectoryAndDeletesIt() {
        Path avatarDirectory = tempDirectory.resolve("avatars");
        AvatarStorageService service = new AvatarStorageService(avatarDirectory.toString(), "/api/v1");
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
        };
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", png);

        AvatarStorageService.StoredAvatar stored = service.store(7L, file);

        assertEquals("png", stored.extension());
        assertTrue(stored.publicUrl().startsWith("/api/v1/files/avatars/7/"));
        Path storedFile = avatarDirectory.resolve(
                stored.publicUrl().substring("/api/v1/files/avatars/".length()));
        assertTrue(Files.exists(storedFile));

        service.delete(stored.publicUrl());

        assertFalse(Files.exists(storedFile));
    }

    @Test
    void rejectsFileWhoseContentIsNotASupportedImage() {
        AvatarStorageService service = new AvatarStorageService(
                tempDirectory.resolve("avatars").toString(), "/api/v1");
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-an-image".getBytes());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.store(7L, file));

        assertEquals("Avatar must be a PNG, JPEG, or WEBP image", exception.getMessage());
    }

    @Test
    void rejectsEmptyFile() {
        AvatarStorageService service = new AvatarStorageService(
                tempDirectory.resolve("avatars").toString(), "/api/v1");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[0]);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.store(7L, file));

        assertEquals("Avatar file is required", exception.getMessage());
    }
}
