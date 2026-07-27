package com.g93.be.controller;
import com.g93.be.dto.PatientGradeStatsDto;


import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.service.ExaminationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.repository.UserRepository;
import com.g93.be.entity.User;

@RestController
@RequestMapping("/examinations")
@RequiredArgsConstructor
@Slf4j
public class ExaminationController {

    private final ExaminationService examinationService;
    private final UserRepository userRepository;

    /**
     * Retrieves examinations with pagination.
     *
     * @param pageable The pagination parameters (default 10 per page).
     * @return A paginated list of examinations.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ExaminationDto>> getAllExaminations(
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get all examinations");
        return ResponseEntity.ok(examinationService.getAllExaminations(pageable));
    }

    @GetMapping("/sort/study-date")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsSortedByStudyDate(
            @RequestParam(defaultValue = "desc") String direction,
            java.security.Principal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to sort examinations by study date ({}) for user: {}", direction, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsSortedByStudyDate(direction, principal.getName(), pageable));
    }

    @GetMapping("/sort/upload-date")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsSortedByUploadDate(
            @RequestParam(defaultValue = "desc") String direction,
            java.security.Principal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to sort examinations by upload date ({}) for user: {}", direction, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsSortedByUploadDate(direction, principal.getName(), pageable));
    }

    @GetMapping("/filter/study-date")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsFilteredByStudyDate(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            java.security.Principal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to filter examinations by study date ({}) for user: {}", date, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsFilteredByStudyDate(date, principal.getName(), pageable));
    }

    @GetMapping("/filter/upload-date")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsFilteredByUploadDate(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            java.security.Principal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to filter examinations by upload date ({}) for user: {}", date, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsFilteredByUploadDate(date, principal.getName(), pageable));
    }

    /**
     * Retrieves an examination by ID.
     *
     * @param id The ID of the examination to retrieve.
     * @return The examination details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExaminationDto> getExaminationById(@PathVariable Long id) {
        log.info("Received request to get examination with id: {}", id);
        return ResponseEntity.ok(examinationService.getExaminationById(id));
    }

    /**
     * Retrieves examinations by doctor ID with pagination.
     *
     * @param doctorId The doctor ID.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations for the doctor.
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByDoctorId(
            @PathVariable Long doctorId,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by doctor id: {}", doctorId);
        return ResponseEntity.ok(examinationService.getExaminationsByDoctorId(doctorId, pageable));
    }

    /**
     * Retrieves examinations by patient ID with pagination.
     *
     * @param patientId The patient ID.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations for the patient.
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByPatientId(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by patient id: {}", patientId);
        return ResponseEntity.ok(examinationService.getExaminationsByPatientId(patientId, pageable));
    }

    /**
     * Retrieves examinations by patient ID filtered by study month.
     *
     * @param patientId The patient ID.
     * @param year The year to filter by.
     * @param month The month to filter by.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations for the patient in the given month.
     */
    @GetMapping("/patient/{patientId}/filter/study-month")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByPatientIdAndStudyMonth(
            @PathVariable Long patientId,
            @RequestParam int year,
            @RequestParam int month,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations for patient id: {} in {}/{}", patientId, month, year);
        return ResponseEntity.ok(examinationService.getExaminationsByPatientIdAndStudyMonth(patientId, year, month, pageable));
    }

    /**
     * Retrieves examinations by status based on user role.
     *
     * @param status The status to filter by.
     * @param userDetails The authenticated user details.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations matching the status.
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByStatus(
            @RequestParam ExaminationStatus status,
            java.security.Principal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by status: {} for user: {}", status, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsByStatus(status, principal.getName(), pageable));
    }

    /**
     * Retrieves examinations by max predicted grade based on user role.
     *
     * @param grade The max predicted grade to filter by.
     * @param principal The authenticated user's principal.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations matching the grade.
     */
    @GetMapping("/grade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByGrade(
            @RequestParam Integer grade,
            java.security.Principal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by grade: {} for user: {}", grade, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsByGrade(grade, principal.getName(), pageable));
    }

    /**
     * Retrieves patient statistics grouped by max predicted grade based on user role.
     *
     * @param principal The authenticated user's principal.
     * @return A list of patient grade statistics.
     */
    @GetMapping("/statistics/patients-by-grade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<PatientGradeStatsDto>> getPatientGradeStatistics(
            java.security.Principal principal) {
        log.info("Received request to get patient grade statistics for user: {}", principal.getName());
        return ResponseEntity.ok(examinationService.getPatientGradeStatistics(principal.getName()));
    }

    /**
     * Marks an examination as viewed.
     *
     * @param id The ID of the examination to mark as viewed.
     * @return 200 OK.
     */
    @PutMapping("/{id}/view")
    public ResponseEntity<Void> markAsViewed(@PathVariable Long id) {
        log.info("Received request to mark examination {} as viewed", id);
        examinationService.markAsViewed(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves total examinations based on user role.
     *
     * @param userId The ID of the user requesting the total.
     * @return The total number of examinations.
     */
    @GetMapping("/total")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getTotalExaminations(@RequestParam Long userId) {
        log.info("Received request to get total examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalExaminations(userId));
    }

    /**
     * Retrieves total severe examinations (KL3, KL4) based on user role.
     *
     * @param userId The ID of the user requesting the total.
     * @return The total number of severe examinations.
     */
    @GetMapping("/total-severe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getTotalSevereExaminations(@RequestParam Long userId) {
        log.info("Received request to get total severe examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalSevereExaminations(userId));
    }

    /**
     * Retrieves total verified examinations based on user role.
     *
     * @param userId The ID of the user requesting the total.
     * @return The total number of verified examinations.
     */
    @GetMapping("/total-verified")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getTotalVerifiedExaminations(@RequestParam Long userId) {
        log.info("Received request to get total verified examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalVerifiedExaminations(userId));
    }

    /**
     * Retrieves total unverified examinations based on user role.
     *
     * @param userId The ID of the user requesting the total.
     * @return The total number of unverified examinations.
     */
    @GetMapping("/total-unverified")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getTotalUnverifiedExaminations(@RequestParam Long userId) {
        log.info("Received request to get total unverified examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalUnverifiedExaminations(userId));
    }

    /**
     * Retrieves total examinations based on user role (from access token).
     */
    @GetMapping("/my-total")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getMyTotalExaminations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalExaminations(userId));
    }

    /**
     * Retrieves total severe examinations based on user role (from access token).
     */
    @GetMapping("/my-total-severe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getMyTotalSevereExaminations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total severe examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalSevereExaminations(userId));
    }

    /**
     * Retrieves total verified examinations based on user role (from access token).
     */
    @GetMapping("/my-total-verified")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getMyTotalVerifiedExaminations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total verified examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalVerifiedExaminations(userId));
    }

    /**
     * Retrieves total unverified examinations based on user role (from access token).
     */
    @GetMapping("/my-total-unverified")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getMyTotalUnverifiedExaminations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total unverified examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalUnverifiedExaminations(userId));
    }
}

