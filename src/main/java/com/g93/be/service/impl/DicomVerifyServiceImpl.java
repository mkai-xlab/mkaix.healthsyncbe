package com.g93.be.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.DicomUploadSessionDTO;
import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.dto.PendingDicomUploadDTO;
import com.g93.be.dto.VerifySessionResultDto;
import com.g93.be.dto.PatientUploadErrorDto;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.service.DicomVerifyService;
import com.g93.be.service.AiService;
import com.g93.be.service.NotificationService;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PatientGradeStatsDto;
import com.g93.be.dto.SendNotificationRequest;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Objects;
import org.springframework.scheduling.annotation.Async;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DicomVerifyServiceImpl implements DicomVerifyService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PatientRepository patientRepository;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final ImageRepository imageRepository;
    private final DoctorRepository doctorRepository;
    private final DicomRawRepository dicomRawRepository;
    private final AiService aiService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Xác nhận (Verify) danh sách bệnh nhân và ca chụp (Examination) được tải lên
     * từ phiên tải DICOM.
     * Hàm này được gọi khi Bác sĩ ấn nút "Lưu (Verify)" trên giao diện danh sách
     * Review.
     * Những bệnh nhân được bác sĩ tích chọn (nằm trong acceptedCodes) sẽ được lưu
     * vào DB.
     * Những bệnh nhân không được chọn sẽ bị bỏ qua và xóa file vật lý tương ứng.
     *
     * @param request          Bao gồm Session ID và danh sách PatientCode được chấp
     *                         nhận.
     * @param requestingUserId ID Bác sĩ thực hiện xác nhận.
     * @param privilegedUser   Cờ cho biết Bác sĩ này có quyền đặc biệt (ví dụ
     *                         Admin) để xác nhận session của người khác hay không.
     * @return VerifySessionResultDto chứa ID các ảnh DICOM đã lưu thành công và
     *         danh sách lỗi.
     */
    @Override
    @Transactional
    public VerifySessionResultDto verifySession(
            DicomVerifyRequest request,
            Long requestingUserId,
            boolean privilegedUser) {
        String sessionId = request.getUploadSessionId();
        String redisKey = "uploadSession:" + sessionId;

        // Bước 1: Lấy dữ liệu phiên tải lên đang được lưu tạm trong Redis
        String sessionJson = stringRedisTemplate.opsForValue().get(redisKey);
        if (sessionJson == null) {
            throw new RuntimeException("Upload session not found or expired: " + sessionId);
        }

        DicomUploadSessionDTO sessionDTO;
        try {
            // Chuyển đổi chuỗi JSON lấy từ Redis về Object DTO
            sessionDTO = objectMapper.readValue(sessionJson, DicomUploadSessionDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse session data", e);
            throw new RuntimeException("Failed to parse session data", e);
        }

        // Bước 2: Kiểm tra quyền sở hữu phiên tải lên (Người nào upload thì người đó
        // mới có quyền verify, trừ phi là Admin)
        if (!privilegedUser && !Objects.equals(sessionDTO.getUploaderUserId(), requestingUserId)) {
            throw new AccessDeniedException(
                    "You are not allowed to verify this upload session");
        }

        // Danh sách mã bệnh nhân (PatientCode) được bác sĩ tích chọn trên giao diện
        List<String> acceptedCodes = request.getAcceptedPatientCodes() != null ? request.getAcceptedPatientCodes()
                : List.of();
        List<Long> savedInstanceIds = new ArrayList<>();
        List<PatientUploadErrorDto> failedPatients = new ArrayList<>();

        // Bước 3: Duyệt qua từng nhóm bệnh nhân đã được bóc tách từ trước (lưu trong
        // Session)
        for (PendingDicomUploadDTO pending : sessionDTO.getPatients().values()) {
            if (acceptedCodes.contains(pending.getPatientCode())) {
                try {
                    // Nếu bệnh nhân được chọn -> Lưu thông tin Bệnh nhân, Ca chụp, Ảnh DICOM vào
                    // CSDL
                    savedInstanceIds.addAll(savePatientData(pending, sessionDTO.getUploaderUserId()));
                } catch (Exception e) {
                    log.error("Error saving patient data for {}: {}", pending.getPatientCode(), e.getMessage());
                    failedPatients.add(new PatientUploadErrorDto(pending.getPatientCode(), pending.getPatientName(),
                            e.getMessage()));
                }
            } else {
                // Nếu bệnh nhân KHÔNG được chọn -> Tiến hành xóa sạch các file vật lý đã lưu
                // tạm trên ổ cứng
                deletePhysicalFiles(pending);
            }
        }

        // Bước 4: Sau khi xử lý xong, dọn dẹp Session khỏi Redis để giải phóng bộ nhớ
        stringRedisTemplate.delete(redisKey);
        stringRedisTemplate.opsForZSet().remove("uploadSessionTimeouts", sessionId);
        log.info("Session {} verified and cleaned from Redis.", sessionId);

        return new VerifySessionResultDto(savedInstanceIds, failedPatients);
    }

    /**
     * Logic nghiệp vụ lưu thông tin chi tiết của một bệnh nhân, tạo Examination (Ca
     * chụp)
     * và chèn các file DICOM, file ảnh PNG tương ứng vào cơ sở dữ liệu.
     *
     * @param pending        Dữ liệu ảnh và metadata đã bóc tách của một bệnh nhân
     *                       từ phiên tải lên.
     * @param uploaderUserId ID Bác sĩ tải ảnh lên.
     * @return Danh sách ID của các đối tượng DicomInstance vừa được lưu.
     */
    private List<Long> savePatientData(PendingDicomUploadDTO pending, Long uploaderUserId) {
        List<Long> instanceIds = new ArrayList<>();

        // Chuẩn hóa Study Instance UID (Nếu không có thẻ này, tạo một UID giả định để
        // nhóm file lại với nhau)
        final String finalStudyUid = (pending.getStudyInstanceUid() != null && !pending.getStudyInstanceUid().isEmpty())
                ? pending.getStudyInstanceUid()
                : "UNKNOWN_STUDY_" + System.currentTimeMillis();

        // Chuẩn hóa ngày chụp (Nếu thiếu, lấy ngày hiện tại làm ngày chụp)
        LocalDate studyDateForGrouping = (pending.getStudyDate() != null)
                ? pending.getStudyDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();

        // Kiểm tra xem bệnh nhân này đã có ca chụp nào trong cùng một ngày đó hay chưa
        // Mục đích là để thêm ảnh mới vào ca chụp cũ trong ngày, tránh tạo quá nhiều
        // Examination rác.
        Optional<Examination> existingExamOpt = examinationRepository
                .findFirstByPatientPatientCodeAndStudyDateOrderByCreatedAtDesc(pending.getPatientCode(),
                        studyDateForGrouping);
        Examination examination;

        if (existingExamOpt.isPresent()) {
            examination = existingExamOpt.get();
            // Cấm tải thêm ảnh vào một ca chụp đã chốt kết quả báo cáo (REPORT_GENERATED)
            if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
                deletePhysicalFiles(pending);
                throw new RuntimeException(
                        "Cannot upload new dicoms to an examination that is already REPORT_GENERATED for patient "
                                + pending.getPatientCode() + " on " + studyDateForGrouping);
            }
            // Nếu trạng thái đang là Chờ Xác Nhận hoặc Đã Xác Nhận, chuyển về Đang Xử Lý AI
            // để hệ thống phân tích lại
            if (examination.getStatus() == ExaminationStatus.NEED_VERIFY
                    || examination.getStatus() == ExaminationStatus.VERIFIED) {
                examination.setStatus(ExaminationStatus.AI_PROCESSING);
                examinationRepository.save(examination);
            }
        } else {
            // Trường hợp chưa có ca chụp trong ngày -> Tiến hành tạo mới Patient (nếu chưa
            // có) và Examination.
            // 1. Get or Create Patient (Lấy bệnh nhân cũ hoặc tạo mới nếu chưa tồn tại)
            final String finalPatientId = pending.getPatientCode();
            final String finalPatientName = (pending.getPatientName() != null && !pending.getPatientName().isEmpty())
                    ? pending.getPatientName()
                    : "Unknown";

            Patient patient = patientRepository.findByPatientCode(finalPatientId).orElse(null);
            if (patient != null) {
                // Nếu Bệnh nhân đã tồn tại -> Cập nhật lại thông tin cá nhân theo file DICOM
                // mới nhất
                patient.setFullName(finalPatientName.replace("^", " ").trim()); // Thay thế ký tự ^ thường gặp trong
                                                                                // DICOM bằng dấu cách
                if (pending.getPatientBirthDate() != null) {
                    patient.setDob(
                            pending.getPatientBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                }
                if ("F".equalsIgnoreCase(pending.getPatientSex())) {
                    patient.setGender(Gender.FEMALE);
                } else if ("M".equalsIgnoreCase(pending.getPatientSex())) {
                    patient.setGender(Gender.MALE);
                } else {
                    patient.setGender(Gender.OTHER);
                }
                patient = patientRepository.save(patient);
            } else {
                // Nếu chưa tồn tại -> Tạo mới hoàn toàn
                Patient p = new Patient();
                p.setPatientCode(finalPatientId);
                // Tạo một email giả tạm thời vì trường email trong entity Patient bắt buộc Not
                // Null và Unique
                p.setEmail(finalPatientId + "_" + UUID.randomUUID().toString().substring(0, 8) + "@temp.com");
                p.setFullName(finalPatientName.replace("^", " ").trim());
                if (pending.getPatientBirthDate() != null) {
                    p.setDob(pending.getPatientBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                }
                if ("F".equalsIgnoreCase(pending.getPatientSex())) {
                    p.setGender(Gender.FEMALE);
                } else if ("M".equalsIgnoreCase(pending.getPatientSex())) {
                    p.setGender(Gender.MALE);
                } else {
                    p.setGender(Gender.OTHER);
                }
                patient = patientRepository.save(p);
            }

            // 2. Tạo một Examination (Ca chụp) mới liên kết với Patient vừa lấy
            examination = new Examination();
            examination.setPatient(patient);

            // Gán bác sĩ phụ trách ca chụp là người đã upload file DICOM này
            Doctor doctor = null;
            if (uploaderUserId != null) {
                doctor = doctorRepository.findById(uploaderUserId).orElse(null);
            }
            if (doctor == null) {
                log.warn(
                        "Uploader with ID {} is not found in doctors table. Setting doctor_id to null for examination.",
                        uploaderUserId);
            }
            examination.setDoctor(doctor); // need fix don't have doctor --> not save examination

            examination.setEncounterCode(finalStudyUid);
            examination.setStatus(ExaminationStatus.AI_PROCESSING); // Bắt đầu ở trạng thái xử lý AI
            examination.setVisitTime(LocalDateTime.now());
            examination.setStudyDate(studyDateForGrouping);
            if (pending.getStudyTime() != null) {
                examination
                        .setStudyTime(pending.getStudyTime().toInstant().atZone(ZoneId.systemDefault()).toLocalTime());
            }
            examination.setDescription(pending.getDescription());
            examination.setReferringPhysician(pending.getReferringPhysician());
            examination = examinationRepository.save(examination);
        }

        // 3. Xử lý lưu các Tệp Ảnh (Images and Instances) vào Cơ sở dữ liệu
        String firstPngPath = null;

        // Cache Map dùng để liên kết giữa SOPInstanceUID (từng tấm ảnh DICOM) với đối
        // tượng Image/DicomRaw trong Database
        Map<String, Image> pngMap = new HashMap<>();
        Map<String, DicomRaw> rawMap = new HashMap<>();

        // Lưu thông tin đường dẫn file (Metadata) vào bảng Image (ảnh xem trước) và
        // bảng DicomRaw (ảnh gốc)
        for (PendingDicomUploadDTO.ImageCacheDTO imageCache : pending.getParsedImages()) {
            if ("image/png".equals(imageCache.getMimeType())) {
                Image image = new Image();
                image.setFilePath(imageCache.getStoredFilePath());
                image.setExtension("png");
                image = imageRepository.save(image);
                pngMap.put(imageCache.getSopInstanceUid(), image);
                if (firstPngPath == null) {
                    firstPngPath = imageCache.getStoredFilePath();
                }
            } else {
                DicomRaw raw = new DicomRaw();
                raw.setFilePath(imageCache.getStoredFilePath());
                raw.setExtension("dcm");
                raw = dicomRawRepository.save(raw);
                rawMap.put(imageCache.getSopInstanceUid(), raw);
            }
        }

        // Lưu từng thực thể DicomInstance (đại diện cho một slice/hình X-Quang đơn lẻ
        // trong Ca chụp)
        for (PendingDicomUploadDTO.InstanceCacheDTO instCache : pending.getParsedInstances()) {
            DicomInstance instance = new DicomInstance();
            instance.setExamination(examination);
            instance.setSopInstanceUid(instCache.getSopInstanceUid());
            instance.setStudyInstanceUid(finalStudyUid);
            instance.setBodyPart(instCache.getBodyPart());
            instance.setImageLaterality(instCache.getImageLaterality());
            
            LocalDateTime instanceStudyDate = studyDateForGrouping.atStartOfDay();
            if (pending.getStudyTime() != null) {
                instanceStudyDate = LocalDateTime.of(studyDateForGrouping, pending.getStudyTime().toInstant().atZone(ZoneId.systemDefault()).toLocalTime());
            }
            instance.setStudyDate(instanceStudyDate);
            
            instance.setCreatedAt(LocalDateTime.now());
            instance.setStatus(DicomInstanceStatus.AI_SENDING); // Đánh dấu sẵn sàng gửi qua Model AI

            // Map ảnh xem trước (PNG)
            Image matchedImage = pngMap.get(instCache.getSopInstanceUid());
            if (matchedImage == null && !pngMap.isEmpty()) {
                matchedImage = pngMap.values().iterator().next();
            }
            instance.setImage(matchedImage);

            // Map ảnh gốc (DCM)
            DicomRaw matchedRaw = rawMap.get(instCache.getSopInstanceUid());
            if (matchedRaw == null && !rawMap.isEmpty()) {
                matchedRaw = rawMap.values().iterator().next();
            }
            instance.setDicomRaw(matchedRaw);

            instance = dicomInstanceRepository.save(instance);
            instanceIds.add(instance.getId());
        }

        return instanceIds;
    }

    /**
     * Tiện ích dọn dẹp (Cleanup): Xóa các file vật lý (DCM, PNG) đã lưu tạm trên ổ
     * cứng
     * đối với những bệnh nhân không được chọn trong bước Xác nhận.
     *
     * @param pending Đối tượng chứa metadata và đường dẫn vật lý của các file đã bị
     *                từ chối.
     */
    private void deletePhysicalFiles(PendingDicomUploadDTO pending) {
        if (pending.getPhysicalFilePaths() != null) {
            for (String absolutePath : pending.getPhysicalFilePaths().values()) {
                try {
                    Path path = Paths.get(absolutePath);
                    Files.deleteIfExists(path);
                    log.info("Deleted rejected file: {}", absolutePath);
                } catch (IOException e) {
                    log.error("Failed to delete physical file {}", absolutePath, e);
                }
            }
        }
    }

    /**
     * Xử lý bất đồng bộ (Async) luồng gọi Model AI sau khi đã Verify thành công.
     * Hệ thống sẽ tự động tổng hợp danh sách ID ảnh, gửi sang AI phân tích,
     * sau đó nhận kết quả, thống kê (thường/bệnh) và thông báo lại cho Frontend.
     *
     * @param savedInstanceIds Danh sách ID ảnh DICOM đã được lưu thành công.
     * @param username         Tên đăng nhập của Bác sĩ thao tác.
     */
    @Override
    @Async
    public void processVerifiedSessionAsync(List<Long> savedInstanceIds, String username) {
        if (savedInstanceIds == null || savedInstanceIds.isEmpty()) {
            return;
        }

        Long notificationUserId = null;
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                notificationUserId = user.getId();
            }
        } // need check username == null
        if (notificationUserId == null) {
            log.warn("Skipping requester notification because no user could be resolved for username: {}", username);
        }
        final Long finalNotificationUserId = notificationUserId;

        try {
            // Bước 1: Gửi lệnh yêu cầu Model AI phân tích hàng loạt (Batch Prediction)
            AiPredictionRequest aiRequest = new AiPredictionRequest(savedInstanceIds);
            List<ExaminationDto> aiResultsList = aiService.predictBatch(aiRequest);

            // Bước 2: Thống kê số lượng bệnh nhân theo Mức độ nghiêm trọng cao nhất
            // (max_predicted_grade)
            Map<Long, Integer> patientToMaxGrade = new HashMap<>();
            for (ExaminationDto exam : aiResultsList) {
                if (exam.getPatient() != null && exam.getMaxPredictedGrade() != null) {
                    Long patId = exam.getPatient().getId();
                    Integer currentMax = patientToMaxGrade.getOrDefault(patId, -1);
                    if (exam.getMaxPredictedGrade() > currentMax) {
                        patientToMaxGrade.put(patId, exam.getMaxPredictedGrade());
                    }
                }
            }

            // Đếm số lượng ca bệnh theo từng mức độ (0: Bình thường, 1-3: Mức độ bệnh)
            Map<Integer, Long> gradeCountMap = new HashMap<>();
            for (Integer grade : patientToMaxGrade.values()) {
                gradeCountMap.put(grade, gradeCountMap.getOrDefault(grade, 0L) + 1);
            }

            List<PatientGradeStatsDto> statsList = gradeCountMap.entrySet().stream()
                    .map(entry -> new PatientGradeStatsDto(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // Bước 3: Gửi thông báo STOMP Websocket về cho người dùng (Báo cáo AI đã sẵn
            // sàng)
            if (finalNotificationUserId != null) {
                SendNotificationRequest notifReq = new SendNotificationRequest(
                        finalNotificationUserId,
                        "Phân tích AI hoàn tất (Chờ xác nhận)",
                        "Hệ thống đã phân tích thành công hình ảnh X-Quang từ phiên xác nhận. Vui lòng kiểm tra và chốt kết quả chẩn đoán.",
                        "AI_RESULT",
                        statsList);
                notificationService.sendNotification(notifReq);
            }

        } catch (Exception e) {
            log.error("Error during background AI processing", e);
            // Gửi thông báo lỗi nếu Model AI sập hoặc có lỗi trong luồng phân tích
            try {
                if (finalNotificationUserId != null) {
                    SendNotificationRequest errReq = new SendNotificationRequest(
                            finalNotificationUserId,
                            "Lỗi phân tích AI",
                            "Đã có lỗi xảy ra trong quá trình phân tích AI.",
                            "ERROR",
                            null);
                    notificationService.sendNotification(errReq);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
