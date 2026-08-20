package com.g93.be.service;

import com.g93.be.entity.KnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIndexingWorkerTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPlainTextAsUtf8() throws Exception {
        Path file = tempDir.resolve("kien_thuc_y_khoa.txt");
        String content = "Hệ tuần hoàn: Tim, mạch máu, máu. Chức năng vận chuyển oxy và dinh dưỡng.";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        KnowledgeDocument knowledge = new KnowledgeDocument();
        knowledge.setStoragePath(file.toString());
        knowledge.setOriginalName(file.getFileName().toString());
        knowledge.setContentType("text/plain");

        KnowledgeDocumentReader reader = new KnowledgeDocumentReader();

        assertThat(reader.read(knowledge))
                .singleElement()
                .satisfies(document -> assertThat(document.getText()).isEqualTo(content));
    }
}
