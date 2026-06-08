package com.g93.be.service.impl;

import com.g93.be.dto.DicomTagResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DicomServiceImplTest {

    private DicomServiceImpl dicomService;
    private final Path tempFolder = Paths.get("data", "temp");

    @BeforeEach
    void setUp() {
        dicomService = new DicomServiceImpl();
        // Clean up temp folder before each test to have a clean slate
        cleanTempFolder();
    }

    @AfterEach
    void tearDown() {
        // Leave the generated files for manual inspection or clean them up.
        // Let's clean up to avoid polluting the workspace, but we can verify in the test first.
    }

    private void cleanTempFolder() {
        if (Files.exists(tempFolder)) {
            try {
                Files.walk(tempFolder)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore cleanup failure
                            }
                        });
            } catch (IOException e) {
                // Ignore cleanup failure
            }
        }
    }

    @Test
    void testExtractMetadataAndSaveFiles() throws IOException {
        // Path to the sample DICOM file in Bruno folder
        Path sampleDcmPath = Paths.get("bruno", "dicom", "sample.dcm");
        assertTrue(Files.exists(sampleDcmPath), "Sample DICOM file must exist at " + sampleDcmPath.toAbsolutePath());

        byte[] content = Files.readAllBytes(sampleDcmPath);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "sample.dcm",
                "application/dicom",
                content
        );

        // Execute service call
        List<DicomTagResponse> tags = dicomService.extractMetadata(multipartFile);

        // Verify tags returned
        assertNotNull(tags);
        assertFalse(tags.isEmpty(), "Extracted tags list should not be empty");

        // Check if the temporary folder was created under data/temp
        assertTrue(Files.exists(tempFolder), "data/temp folder should be created");

        // Verify the subdirectory is created for the patient
        List<Path> subdirectories = Files.list(tempFolder)
                .filter(Files::isDirectory)
                .toList();
        assertEquals(1, subdirectories.size(), "Should have exactly one patient subdirectory");

        Path patientDir = subdirectories.getFirst();
        
        // Verify that the files were created inside the subdirectory
        Path savedDcm = patientDir.resolve("upload.dcm");
        Path savedTxt = patientDir.resolve("info.txt");
        Path savedPng = patientDir.resolve("image.png");

        assertTrue(Files.exists(savedDcm), "upload.dcm should exist in patient folder");
        assertTrue(Files.exists(savedTxt), "info.txt should exist in patient folder");
        assertTrue(Files.exists(savedPng), "image.png should exist in patient folder");

        // Read and verify text file content
        String txtContent = Files.readString(savedTxt);
        assertTrue(txtContent.contains("DICOM Patient Information:"), "info.txt should contain patient information header");
        assertTrue(txtContent.contains("PatientName"), "info.txt should contain PatientName tag");

        // Check if the image has content
        assertTrue(Files.size(savedPng) > 0, "extracted image.png should not be empty");
    }
}
