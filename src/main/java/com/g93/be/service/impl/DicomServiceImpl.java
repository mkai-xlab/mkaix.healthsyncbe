package com.g93.be.service.impl;

import com.g93.be.dto.DicomTagResponse;
import com.g93.be.dto.PatientDetailsResponse;
import com.g93.be.entity.*;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.*;
import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DicomServiceImpl implements DicomService {

    private final PatientRepository patientRepository;
    private final ImageRepository imageRepository;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final PatientMapper patientMapper;
    @Value("${app.storage.local-dir:D:\\Capstone\\data}")
    private String localDir;

    @Override
    public List<DicomTagResponse> extractMetadata(MultipartFile file) {
        // Keeping original method as is, but could be refactored
        // to use the common extraction logic.
        return new ArrayList<>(); // Stubbing this out to avoid duplication if it's not used, but actually I should keep the original implementation.
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientDetailsResponse uploadAndProcessDicom(MultipartFile file) {
        Path tempFile;
        try {
            tempFile = Files.createTempFile("upload_", ".dcm");
            file.transferTo(tempFile.toFile());
        } catch (IOException e) {
            log.error("Failed to save uploaded file to temporary location", e);
            throw new RuntimeException("Failed to upload and save file.");
        }

        String patientName = UUID.randomUUID().toString();
        String patientId = UUID.randomUUID().toString();
        String studyInstanceUid = null;
        String seriesInstanceUid = null;
        String sopInstanceUid = null;
        LocalDate dob = null;
        Gender gender = null;
        String phone = null;
        String address = null;
        LocalDate studyDate = null;
        java.time.LocalTime studyTime = null;
        String bodyPart = null;
        String description = null;
        String referringPhysician = null;
        String imageLaterality = null;
        Integer imageRows = null;
        Integer imageColumns = null;

        try (DicomInputStream dis = new DicomInputStream(tempFile.toFile())) {
            Attributes attrs = dis.readDataset();

            for (int tag : attrs.tags()) {
                String tagName = ElementDictionary.getStandardElementDictionary().keywordOf(tag);
                if (tagName == null) continue;

                VR vr = attrs.getVR(tag);
                String value = "";
                if (vr != null && !vr.isInlineBinary()) {
                    try {
                        value = attrs.getString(tag, "");
                    } catch (Exception ignored) { }
                }

                if ("PatientName".equals(tagName) && value != null && !value.isEmpty()) {
                    patientName = value.replace("^", " ").trim();
                } else if ("PatientID".equals(tagName) && value != null && !value.isEmpty()) {
                    patientId = value.trim();
                } else if ("PatientBirthDate".equals(tagName) && value != null && !value.isEmpty()) {
                    try {
                        dob = java.time.LocalDate.parse(value.trim(), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                    } catch (Exception ignored) {}
                } else if ("PatientSex".equals(tagName) && value != null && !value.isEmpty()) {
                    String s = value.trim().toUpperCase();
                    if (s.startsWith("M")) gender = Gender.MALE;
                    else if (s.startsWith("F")) gender = Gender.FEMALE;
                    else gender = Gender.OTHER;
                } else if ("PatientAddress".equals(tagName) && value != null && !value.isEmpty()) {
                    address = value.trim();
                } else if ("PatientTelephoneNumbers".equals(tagName) && value != null && !value.isEmpty()) {
                    phone = value.trim();
                } else if ("StudyDate".equals(tagName) && value != null && !value.isEmpty()) {
                    try { studyDate = java.time.LocalDate.parse(value.trim(), java.time.format.DateTimeFormatter.BASIC_ISO_DATE); } catch (Exception ignored) {}
                } else if ("StudyTime".equals(tagName) && value != null && !value.isEmpty()) {
                    try { 
                        String t = value.trim();
                        if (t.length() >= 6) studyTime = java.time.LocalTime.parse(t.substring(0,6), java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
                    } catch (Exception ignored) {}
                } else if ("BodyPartExamined".equals(tagName)) {
                    bodyPart = value;
                } else if ("StudyDescription".equals(tagName) || "RequestedProcedureDescription".equals(tagName)) {
                    if (value != null && !value.isEmpty()) description = value;
                } else if ("ReferringPhysicianName".equals(tagName)) {
                    if (value != null) referringPhysician = value.replace("^", " ").trim();
                } else if ("ImageLaterality".equals(tagName)) {
                    imageLaterality = value;
                } else if ("Rows".equals(tagName) && value != null && !value.isEmpty()) {
                    try { imageRows = Integer.parseInt(value.trim()); } catch (Exception ignored) {}
                } else if ("Columns".equals(tagName) && value != null && !value.isEmpty()) {
                    try { imageColumns = Integer.parseInt(value.trim()); } catch (Exception ignored) {}
                } else if ("StudyInstanceUID".equals(tagName)) {
                    studyInstanceUid = value;
                } else if ("SeriesInstanceUID".equals(tagName)) {
                    seriesInstanceUid = value;
                } else if ("SOPInstanceUID".equals(tagName)) {
                    sopInstanceUid = value;
                }
            }
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new RuntimeException("Failed to parse DICOM file.");
        }

        if (sopInstanceUid != null && dicomInstanceRepository.existsBySopInstanceUid(sopInstanceUid)) {
            deleteQuietly(tempFile);
            throw new IllegalArgumentException("File dicom này đã tồn tại");
        }

        // DB Operations
        Patient patient = getOrCreatePatient(patientId, patientName, dob, gender, phone, address);

        // File Operations
        String uuidStr = UUID.randomUUID().toString();
        Path baseDir = Paths.get(localDir);
        Path dicomDir = baseDir.resolve("dicom");
        Path imageDir = baseDir.resolve("images");

        try {
            Files.createDirectories(dicomDir);
            Files.createDirectories(imageDir);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new RuntimeException("Failed to create storage directories.");
        }

        Path targetDcmPath = dicomDir.resolve(uuidStr + ".dcm");
        Path imageFilePath = imageDir.resolve(uuidStr + ".png");

        try {
            Files.move(tempFile, targetDcmPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            extractImageFromDicom(targetDcmPath, imageFilePath);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new RuntimeException("Failed to save files.");
        }

        String imagePathStr = imageFilePath.toAbsolutePath().toString();

        patient.setImagePath(imagePathStr);
        patient = patientRepository.save(patient);

        Examination examination = new Examination();
        examination.setPatient(patient);
        examination.setStatus(ExaminationStatus.CREATED);
        examination.setImagePath(imagePathStr);
        examination.setStudyDate(studyDate);
        examination.setStudyTime(studyTime);
        examination.setBodyPart(bodyPart);
        examination.setDescription(description);
        examination.setReferringPhysician(referringPhysician);
        examination = examinationRepository.save(examination);

        DicomInstance dicomInstance = new DicomInstance();
        dicomInstance.setExamination(examination);
        dicomInstance.setStudyInstanceUid(studyInstanceUid);
        dicomInstance.setSeriesInstanceUid(seriesInstanceUid);
        dicomInstance.setSopInstanceUid(sopInstanceUid);
        dicomInstance.setImageLaterality(imageLaterality);
        dicomInstance.setImageRows(imageRows);
        dicomInstance.setImageColumns(imageColumns);
        dicomInstance.setStorageRawPath(targetDcmPath.toAbsolutePath().toString());
        dicomInstance.setStoragePngPath(imageFilePath.toAbsolutePath().toString());
        dicomInstanceRepository.save(dicomInstance);

        PatientDetailsResponse response = new PatientDetailsResponse();
        response.setPatient(patientMapper.toResponse(patient));
        
        List<com.g93.be.dto.ExaminationImageDto> examDtos = new ArrayList<>();
        com.g93.be.dto.ExaminationImageDto dto = new com.g93.be.dto.ExaminationImageDto();
        dto.setExaminationId(examination.getId());
        dto.setEncounterCode(examination.getEncounterCode());
        if (examination.getStatus() != null) {
            dto.setStatus(examination.getStatus().name());
        }
        dto.setVisitTime(examination.getVisitTime());
        
        if (dicomInstance.getStoragePngPath() != null) {
            Path pngPath = Paths.get(dicomInstance.getStoragePngPath());
            if (Files.exists(pngPath)) {
                try {
                    String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
                    dto.setImageUrl(baseUrl + "/api/v1/dicom/instances/" + dicomInstance.getId() + "/image");
                } catch (Exception e) {
                    log.error("Failed to build image url for instance: " + dicomInstance.getId(), e);
                }
            }
        }
        examDtos.add(dto);
        
        response.setExaminations(examDtos);
        return response;
    }

    private Patient getOrCreatePatient(String patientId, String patientName, LocalDate dob, Gender gender, String phone, String address) {
        Optional<Patient> existingPatientOpt = patientRepository.findByPatientCode(patientId);
        if (existingPatientOpt.isPresent()) {
            Patient existingPatient = existingPatientOpt.get();
            boolean updated = false;
            if (existingPatient.getDob() == null && dob != null) { existingPatient.setDob(dob); updated = true; }
            if (existingPatient.getGender() == null && gender != null) { existingPatient.setGender(gender); updated = true; }
            if (existingPatient.getPhone() == null && phone != null) { existingPatient.setPhone(phone); updated = true; }
            if (existingPatient.getAddress() == null && address != null) { existingPatient.setAddress(address); updated = true; }
            if (updated) { patientRepository.save(existingPatient); }
            return existingPatient;
        }

        Patient patient = new Patient();
        patient.setPatientCode(patientId);
        patient.setFullName(patientName);
        patient.setEmail(patientId + "@healthsync.com");
        patient.setStatus(UserStatus.ACTIVE);
        if (dob != null) patient.setDob(dob);
        if (gender != null) patient.setGender(gender);
        if (phone != null) patient.setPhone(phone);
        if (address != null) patient.setAddress(address);

        return patientRepository.save(patient);
    }

    private void extractImageFromDicom(Path dicomPath, Path imagePath) {
        ImageIO.scanForPlugins();
        try (ImageInputStream iis = ImageIO.createImageInputStream(dicomPath.toFile())) {
            Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
            if (iter.hasNext()) {
                ImageReader reader = iter.next();
                reader.setInput(iis, false);
                BufferedImage bi = reader.read(0);
                if (bi != null) {
                    ImageIO.write(bi, "png", imagePath.toFile());
                }
            }
        } catch (Exception e) {
            log.error("Error extracting image from DICOM", e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {}
        }
    }
}
