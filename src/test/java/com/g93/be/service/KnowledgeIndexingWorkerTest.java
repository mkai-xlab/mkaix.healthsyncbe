package com.g93.be.service;

import com.g93.be.entity.KnowledgeDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    void readsMultiPagePdfAsOneDocumentPerPageWithPageMetadata() throws Exception {
        Path file = tempDir.resolve("guideline.pdf");
        writeTwoPagePdf(file, "Page one: KL grade overview", "Page two: KL grade 4 criteria");

        KnowledgeDocument knowledge = new KnowledgeDocument();
        knowledge.setStoragePath(file.toString());
        knowledge.setOriginalName(file.getFileName().toString());
        knowledge.setContentType("application/pdf");

        KnowledgeDocumentReader reader = new KnowledgeDocumentReader();
        List<Document> pages = reader.read(knowledge);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).getMetadata()).containsEntry("page", 1);
        assertThat(pages.get(0).getText()).contains("Page one");
        assertThat(pages.get(1).getMetadata()).containsEntry("page", 2);
        assertThat(pages.get(1).getText()).contains("Page two");
    }

    private void writeTwoPagePdf(Path file, String page1Text, String page2Text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (String text : List.of(page1Text, page2Text)) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(file.toFile());
        }
    }
}
