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
import java.io.File;
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
        
        String baseDir = "d:\\Capstone\\source\\mkaix.healthsyncbe\\src\\main\\java\\com\\g93\\be\\datatemp";
        String folderName = "Patient_" + System.currentTimeMillis();
        Path folderPath = Paths.get(baseDir, folderName);
        File dcmFile = folderPath.resolve("upload.dcm").toFile();
        File infoFile = folderPath.resolve("info.txt").toFile();
        File imageFile = folderPath.resolve("image.png").toFile();

        try {
            Files.createDirectories(folderPath);
            file.transferTo(dcmFile);

            String patientName = "Unknown";
            
            // 1. Extract Metadata
            try (DicomInputStream dis = new DicomInputStream(dcmFile)) {
                Attributes attrs = dis.readDataset();
                
                StringBuilder infoTxtContent = new StringBuilder();
                infoTxtContent.append("DICOM Patient Information:\n");
                infoTxtContent.append("==========================\n");

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
                }

                // Write metadata to text file
                Files.writeString(infoFile.toPath(), infoTxtContent.toString());
            }

            // Rename folder to include the real patient name if found
            if (!"Unknown".equals(patientName)) {
                String safePatientName = patientName.replaceAll("[^a-zA-Z0-9.-]", "_");
                Path newFolderPath = Paths.get(baseDir, safePatientName + "_" + System.currentTimeMillis());
                Files.move(folderPath, newFolderPath);
                folderPath = newFolderPath;
                dcmFile = folderPath.resolve("upload.dcm").toFile();
                imageFile = folderPath.resolve("image.png").toFile();
            }

            // 2. Extract Image
            ImageIO.scanForPlugins();
            try (ImageInputStream iis = ImageIO.createImageInputStream(dcmFile)) {
                Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
                if (iter.hasNext()) {
                    ImageReader reader = iter.next();
                    reader.setInput(iis, false);
                    BufferedImage bi = reader.read(0);
                    if (bi != null) {
                        ImageIO.write(bi, "png", imageFile);
                        log.info("Successfully extracted PNG image to {}", imageFile.getAbsolutePath());
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
            log.error("Failed to parse DICOM file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse DICOM file. Please ensure it is a valid .dcm file.");
        }

        return tags;
    }
}
