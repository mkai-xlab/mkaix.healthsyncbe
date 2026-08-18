package com.g93.be.service;

import com.g93.be.entity.KnowledgeDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
public class KnowledgeDocumentReader {

    public List<Document> read(KnowledgeDocument knowledge) {
        return read(new FileSystemResource(knowledge.getStoragePath()),
                knowledge.getOriginalName(), knowledge.getContentType());
    }

    public List<Document> read(byte[] bytes, String originalName, String contentType) {
        Resource resource = new NamedByteArrayResource(bytes, originalName);
        return read(resource, originalName, contentType);
    }

    private List<Document> read(Resource resource, String originalName, String contentType) {
        if (isType(originalName, contentType, "pdf", "application/pdf")) {
            return readPdf(resource);
        }
        if (isType(originalName, contentType, "txt", "text/plain")) {
            TextReader reader = new TextReader(resource);
            reader.setCharset(StandardCharsets.UTF_8);
            return reader.get();
        }
        return new TikaDocumentReader(resource).get();
    }

    private List<Document> readPdf(Resource resource) {
        try (PDDocument pdf = PDDocument.load(resource.getInputStream())) {
            return List.of(new Document(new PDFTextStripper().getText(pdf)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read PDF document", exception);
        }
    }

    private boolean isType(String originalName, String contentType, String extension, String mediaType) {
        if (mediaType.equalsIgnoreCase(contentType)) {
            return true;
        }
        return originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
