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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

        try (InputStream is = file.getInputStream();
             DicomInputStream dis = new DicomInputStream(is)) {

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

                DicomTagResponse response = new DicomTagResponse(tagId, tagName, value);
                tags.add(response);

                // Print out the information as requested
                log.info("DICOM Tag -> ID: {}, Name: {}, Value: {}", tagId, tagName, value);
            }

        } catch (IOException e) {
            log.error("Failed to parse DICOM file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse DICOM file. Please ensure it is a valid .dcm file.");
        }

        return tags;
    }
}
