package com.g93.be.service.impl;

import com.g93.be.dto.DicomTagResponse;
import com.g93.be.service.DicomService;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of DicomService using dcm4che library.
 */
@Service
@Slf4j
public class DicomServiceImpl implements DicomService {

    @Override
    public List<DicomTagResponse> extractMetadata(MultipartFile file) {
        List<DicomTagResponse> tags = new ArrayList<>();

        Path tempFile;
        try {
            tempFile = Files.createTempFile("upload_", ".dcm");
            file.transferTo(tempFile.toFile());
        } catch (IOException e) {
            log.error("Failed to save uploaded file to temporary location", e);
            throw new RuntimeException("Failed to upload and save file.");
        }

        String patientName = "Unknown";
        StringBuilder infoTxtContent = new StringBuilder();
        infoTxtContent.append("DICOM Patient Information:\n");
        infoTxtContent.append("==========================\n");

        // 1. Read dataset and extract tags
        try (DicomInputStream dis = new DicomInputStream(tempFile.toFile())) {
            Attributes attrs = dis.readDataset();

            // Iterate over all tags in the dataset
            for (int tag : attrs.tags()) {
                String tagId = TagUtils.toString(tag);

                String tagName = ElementDictionary.getStandardElementDictionary().keywordOf(tag);
                if (tagName == null || tagName.isEmpty()) {
                    tagName = "Unknown/Private Tag";
                }

                VR vr = attrs.getVR(tag);
                String value = "";
                if (vr != null) {
                    try {
                        // Extract string representation, avoiding huge binary chunks
                        if (!vr.isInlineBinary()) {
                            value = attrs.getString(tag, "");
                        } else {
                            value = "[Binary Data]";
                        }
                    } catch (Exception e) {
                        value = "[Unsupported Value Format]";
                    }
                }

                if ("PatientName".equals(tagName) && value != null && !value.isEmpty()) {
                    patientName = value.replace("^", " ").trim();
                }

                DicomTagResponse response = new DicomTagResponse(tagId, tagName, value);
                tags.add(response);

                infoTxtContent.append(String.format("ID: %s | Name: %s | Value: %s\n", tagId, tagName, value));
                log.info("DICOM Tag -> ID: {}, Name: {}, Value: {}", tagId, tagName, value);
            }
        } catch (IOException e) {
            log.error("Failed to parse DICOM file: {}", e.getMessage(), e);
            // Clean up temp file
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ex) {
                log.warn("Failed to clean up temporary file: {}", tempFile, ex);
            }
            throw new RuntimeException("Failed to parse DICOM file. Please ensure it is a valid .dcm file.");
        }

        // 2. Perform file operations (move, write txt, extract image) after the stream is closed
        try {
            // Create target folder path
            Path baseDir = Paths.get("data", "temp");
            String folderName = "Patient_" + System.currentTimeMillis();
            if (!"Unknown".equals(patientName) && !patientName.trim().isEmpty()) {
                String safePatientName = patientName.replaceAll("[^a-zA-Z0-9.-]", "_");
                folderName = safePatientName + "_" + System.currentTimeMillis();
            }
            Path folderPath = baseDir.resolve(folderName);
            Files.createDirectories(folderPath);

            // Move temp file to target folder as upload.dcm
            Path targetDcmPath = folderPath.resolve("upload.dcm");
            Files.move(tempFile, targetDcmPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Write metadata to text file
            Path infoFilePath = folderPath.resolve("info.txt");
            Files.writeString(infoFilePath, infoTxtContent.toString());

            // 3. Extract Image
            Path imageFilePath = folderPath.resolve("image.png");
            ImageIO.scanForPlugins();
            try (ImageInputStream iis = ImageIO.createImageInputStream(targetDcmPath.toFile())) {
                Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
                if (iter.hasNext()) {
                    ImageReader reader = iter.next();
                    reader.setInput(iis, false);
                    BufferedImage bi = reader.read(0);
                    if (bi != null) {
                        ImageIO.write(bi, "png", imageFilePath.toFile());
                        log.info("Successfully extracted PNG image to {}", imageFilePath.toAbsolutePath());
                    } else {
                        log.warn("DICOM image read returned null.");
                    }
                } else {
                    log.warn("No DICOM ImageReader plugin found. Could not extract PNG.");
                }
            } catch (Exception e) {
                log.error("Error extracting image from DICOM: {}", e.getMessage(), e);
            }
        } catch (IOException e) {
            log.error("Failed to save DICOM outputs: {}", e.getMessage(), e);
            // Clean up temp file if still exists
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ex) {
                log.warn("Failed to clean up temporary file: {}", tempFile, ex);
            }
            throw new RuntimeException("Failed to save DICOM output files.");
        }

        return tags;
    }
}
