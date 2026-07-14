package com.healthsync.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DicomProcessorHandler implements RequestHandler<S3Event, String> {

    private static final Logger log = LoggerFactory.getLogger(DicomProcessorHandler.class);
    private final S3Client s3Client = S3Client.create();
    private final SqsClient sqsClient = SqsClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Environment variables configured in Terraform
    private final String destDicomBucket = System.getenv("DICOM_BUCKET");
    private final String destPngBucket = System.getenv("PNG_BUCKET");
    private final String sqsQueueUrl = System.getenv("SQS_QUEUE_URL");

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        log.info("Received S3 event with {} records.", s3Event.getRecords().size());
        for (S3EventNotification.S3EventNotificationRecord record : s3Event.getRecords()) {
            processRecord(record);
        }
        return "Completed processing all S3 events.";
    }

    private void processRecord(S3EventNotification.S3EventNotificationRecord record) {
        String srcBucket = record.getS3().getBucket().getName();
        String srcKey = record.getS3().getObject().getUrlDecodedKey();

        log.info("Processing raw upload: bucket={}, key={}", srcBucket, srcKey);

        // 1. Basic validation - only process files uploaded directly to "dicom/" folder
        if (!srcKey.startsWith("dicom/") || !srcKey.toLowerCase().endsWith(".dcm")) {
            log.info("Skipping record as it does not match 'dicom/*.dcm': {}", srcKey);
            return;
        }

        File tempDcmFile = null;
        File tempPngFile = null;

        try {
            // 2. Download the uploaded raw DICOM file to /tmp
            tempDcmFile = File.createTempFile("dicom_in_", ".dcm");
            downloadS3Object(srcBucket, srcKey, tempDcmFile);

            // 3. Extract metadata tags while skipping the heavy PixelData tag
            Map<String, String> metadata = extractDicomMetadata(tempDcmFile);

            // 4. Extract image to PNG
            tempPngFile = File.createTempFile("image_out_", ".png");
            boolean imageExtracted = convertDicomToPng(tempDcmFile, tempPngFile);

            // 5. Build the target S3 path structure
            // Path format: <patient_id>/<study_instance_uid>/<body_part_position>_<epoch>.<ext>
            String patientId = metadata.getOrDefault("PatientID", "unknown_patient")
                    .replaceAll("[^a-zA-Z0-9.-]", "_");
            String studyUid = metadata.getOrDefault("StudyInstanceUID", "unknown_study")
                    .replaceAll("[^a-zA-Z0-9.-]", "_");
            String bodyPart = metadata.getOrDefault("BodyPartExamined", "image")
                    .replaceAll("[^a-zA-Z0-9.-]", "_");

            // Suffix with epoch timestamp in seconds to handle multiple images per session/position
            long epochSecond = Instant.now().getEpochSecond();
            String baseFileName = bodyPart + "_" + epochSecond;
            String relativePath = patientId + "/" + studyUid + "/" + baseFileName;

            String targetDicomKey = relativePath + ".dcm";
            String targetPngKey = relativePath + ".png";

            String finalDicomBucket = (destDicomBucket != null && !destDicomBucket.isEmpty()) ? destDicomBucket : srcBucket;
            String finalPngBucket = (destPngBucket != null && !destPngBucket.isEmpty()) ? destPngBucket : srcBucket;

            // 6. Upload DICOM file to the structured path
            uploadFileToS3(finalDicomBucket, targetDicomKey, "application/dicom", tempDcmFile);

            // 7. Upload PNG file to the structured path (if extraction succeeded)
            if (imageExtracted) {
                uploadFileToS3(finalPngBucket, targetPngKey, "image/png", tempPngFile);
            } else {
                log.warn("Skipping PNG S3 upload because extraction failed.");
                targetPngKey = null;
            }

            // 8. Delete the original raw file from the "dicom/" folder to avoid duplicate triggers
            deleteS3Object(srcBucket, srcKey);

            // 9. Send success message to SQS containing the S3 paths and metadata
            if (sqsQueueUrl != null && !sqsQueueUrl.isEmpty()) {
                publishToSqs(sqsQueueUrl, finalDicomBucket, targetDicomKey, finalPngBucket, targetPngKey, metadata);
            } else {
                log.error("SQS_QUEUE_URL environment variable is missing. Message not sent.");
            }

        } catch (Exception e) {
            log.error("Failed to process DICOM event record for key: {}", srcKey, e);
        } finally {
            // Clean up local temp files to prevent /tmp disk exhaustion
            cleanupTempFile(tempDcmFile);
            cleanupTempFile(tempPngFile);
        }
    }

    private void downloadS3Object(String bucket, String key, File destination) {
        log.info("Downloading file from S3 s3://{}/{} to temp location: {}", bucket, key, destination.getAbsolutePath());
        s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build(),
                destination.toPath()
        );
    }

    private Map<String, String> extractDicomMetadata(File dicomFile) {
        Map<String, String> metadata = new HashMap<>();
        try (DicomInputStream dis = new DicomInputStream(dicomFile)) {
            Attributes attrs = dis.readDataset();
            for (int tag : attrs.tags()) {
                // Explicitly skip PixelData (0x7FE00010) to keep JSON metadata size low
                if (tag == Tag.PixelData) {
                    continue;
                }

                String tagName = ElementDictionary.getStandardElementDictionary().keywordOf(tag);
                if (tagName != null && !tagName.isEmpty()) {
                    VR vr = attrs.getVR(tag);
                    if (vr != null && !vr.isInlineBinary()) {
                        String value = attrs.getString(tag, "");
                        if (value != null && !value.isEmpty()) {
                            metadata.put(tagName, value.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse DICOM metadata for file {}", dicomFile.getName(), e);
        }
        log.info("Extracted {} DICOM metadata fields.", metadata.size());
        return metadata;
    }

    private boolean convertDicomToPng(File dicomFile, File destinationPngFile) {
        ImageIO.scanForPlugins();
        try (ImageInputStream iis = ImageIO.createImageInputStream(dicomFile)) {
            Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
            if (iter.hasNext()) {
                ImageReader reader = iter.next();
                reader.setInput(iis, false);
                BufferedImage bi = reader.read(0);
                if (bi != null) {
                    ImageIO.write(bi, "png", destinationPngFile);
                    log.info("Successfully converted DICOM image to PNG: {}", destinationPngFile.getAbsolutePath());
                    return true;
                } else {
                    log.warn("DICOM image read returned null for file: {}", dicomFile.getName());
                }
            } else {
                log.error("No DICOM ImageReader plugin found in ClassPath.");
            }
        } catch (Exception e) {
            log.error("Error converting DICOM to PNG for file {}", dicomFile.getName(), e);
        }
        return false;
    }

    private void uploadFileToS3(String bucket, String key, String contentType, File file) {
        log.info("Uploading file to S3: bucket={}, key={}", bucket, key);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file)
        );
    }

    private void deleteS3Object(String bucket, String key) {
        log.info("Deleting object from S3: bucket={}, key={}", bucket, key);
        s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        );
    }

    private void publishToSqs(String queueUrl, String dicomBucket, String dicomKey, String pngBucket, String pngKey, Map<String, String> metadata) throws Exception {
        Map<String, Object> dicomFileMap = new HashMap<>();
        dicomFileMap.put("bucket", dicomBucket);
        dicomFileMap.put("key", dicomKey);

        Map<String, Object> pngFileMap = new HashMap<>();
        pngFileMap.put("bucket", pngBucket);
        pngFileMap.put("key", pngKey);

        Map<String, Object> sqsPayload = new HashMap<>();
        sqsPayload.put("dicom_file", dicomFileMap);
        sqsPayload.put("png_file", pngFileMap);
        sqsPayload.put("metadata", metadata);

        String messageBody = objectMapper.writeValueAsString(sqsPayload);
        log.info("Publishing JSON payload to SQS: {}", queueUrl);
        sqsClient.sendMessage(
                SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(messageBody)
                        .build()
        );
    }

    private void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath());
                log.info("Deleted temporary file: {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", file.getAbsolutePath(), e);
            }
        }
    }
}
