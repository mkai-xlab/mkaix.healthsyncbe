package com.g93.be.controller;


import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.PatientGradeStatsDto;
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
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.repository.UserRepository;
import com.g93.be.entity.User;

import java.util.List;

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
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<PageResponse<ExaminationDto>> getAllExaminations(
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get all examinations");
        return ResponseEntity.ok(examinationService.getAllExaminations(pageable, principal != null ? principal.getName() : null, isPersonal));
    }

    @GetMapping("/sort/study-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsSortedByStudyDate(
            @RequestParam(defaultValue = "desc") String direction,
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to sort examinations by study date ({}) for user: {}", direction,
                principal.getName());
        return ResponseEntity
                .ok(examinationService.getExaminationsSortedByStudyDate(direction, principal.getName(), isPersonal, pageable));
    }

    @GetMapping("/sort/upload-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsSortedByUploadDate(
            @RequestParam(defaultValue = "desc") String direction,
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to sort examinations by upload date ({}) for user: {}", direction,
                principal.getName());
        return ResponseEntity
                .ok(examinationService.getExaminationsSortedByUploadDate(direction, principal.getName(), isPersonal, pageable));
    }

    @GetMapping("/filter/study-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsFilteredByStudyDate(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to filter examinations by study date ({}) for user: {}", date, principal.getName());
        return ResponseEntity
                .ok(examinationService.getExaminationsFilteredByStudyDate(date, principal.getName(), isPersonal, pageable));
    }

    @GetMapping("/filter/upload-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsFilteredByUploadDate(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to filter examinations by upload date ({}) for user: {}", date, principal.getName());
        return ResponseEntity
                .ok(examinationService.getExaminationsFilteredByUploadDate(date, principal.getName(), isPersonal, pageable));
    }

    /**
     * Retrieves an examination by ID.
     *
     * @param id The ID of the examination to retrieve.
     * @return The examination details.
     */
    @GetMapping("/{id}")
    @PreAuthorize("(hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
            + "or (hasRole('DOCTOR') and hasAuthority('VIEW_PENDING_DIAGNOSIS'))) "
            + "and @accessControl.canAccessExamination(#p0, authentication)")
    public ResponseEntity<ExaminationDto> getExaminationById(
            @PathVariable Long id,
            java.security.Principal principal) {
        log.info("Received request to get examination with id: {} by user: {}",
                id, principal.getName());
        return ResponseEntity.ok(
                examinationService.getExaminationById(id, principal.getName()));
    }

    /**
     * Retrieves examinations by doctor ID with pagination.
     *
     * @param doctorId The doctor ID.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations for the doctor.
     */
    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("@accessControl.canAccessDoctor(#p0, authentication)")
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
     * @param pageable  The pagination parameters.
     * @return A paginated list of examinations for the patient.
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
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
     * @param year      The year to filter by.
     * @param month     The month to filter by.
     * @param pageable  The pagination parameters.
     * @return A paginated list of examinations for the patient in the given month.
     */
    @GetMapping("/patient/{patientId}/filter/study-month")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByPatientIdAndStudyMonth(
            @PathVariable Long patientId,
            @RequestParam int year,
            @RequestParam int month,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations for patient id: {} in {}/{}", patientId, month, year);
        return ResponseEntity
                .ok(examinationService.getExaminationsByPatientIdAndStudyMonth(patientId, year, month, pageable));
    }

    /**
     * Retrieves examinations by status based on user role.
     *
     * @param status      The status to filter by.
     * @param userDetails The authenticated user details.
     * @param pageable    The pagination parameters.
     * @return A paginated list of examinations matching the status.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('VIEW_PENDING_DIAGNOSIS'))")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByStatus(
            @RequestParam ExaminationStatus status,
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by status: {} for user: {}", status, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsByStatus(status, principal.getName(), isPersonal, pageable));
    }

    /**
     * Retrieves examinations by max predicted grade based on user role.
     *
     * @param grade     The max predicted grade to filter by.
     * @param principal The authenticated user's principal.
     * @param pageable  The pagination parameters.
     * @return A paginated list of examinations matching the grade.
     */
    @GetMapping("/grade")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('VIEW_PENDING_DIAGNOSIS'))")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByGrade(
            @RequestParam Integer grade,
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by grade: {} for user: {}", grade, principal.getName());
        return ResponseEntity.ok(examinationService.getExaminationsByGrade(grade, principal.getName(), isPersonal, pageable));
    }

    /**
     * Retrieves patient statistics grouped by max predicted grade based on user
     * role.
     *
     * @param principal The authenticated user's principal.
     * @return A list of patient grade statistics.
     */
    @GetMapping("/statistics/patients-by-grade")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
                    + "or (hasRole('DOCTOR') and hasAuthority('VIEW_ANALYTIC_HISTORY'))")
    public ResponseEntity<List<PatientGradeStatsDto>> getPatientGradeStatistics(
            java.security.Principal principal,
            @RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        log.info("Received request to get patient grade statistics for user: {}",
                principal.getName());
        return ResponseEntity.ok(
                examinationService.getPatientGradeStatistics(principal.getName(), isPersonal));
    }

    /**
     * Marks an examination as viewed.
     *
     * @param id The ID of the examination to mark as viewed.
     * @return 200 OK.
     */
    @PutMapping("/{id}/view")
    @PreAuthorize("@accessControl.canAccessExamination(#p0, authentication)")
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
    @PreAuthorize("@accessControl.canAccessUser(#p0, authentication)")
    public ResponseEntity<Long> getTotalExaminations(@RequestParam Long userId, @RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        log.info("Received request to get total examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalExaminations(userId, isPersonal));
    }

    /**
     * Retrieves total severe examinations (KL3, KL4) based on user role.
     *
     * @param userId The ID of the user requesting the total.
     * @return The total number of severe examinations.
     */
    @GetMapping("/total-severe")
    @PreAuthorize("@accessControl.canAccessUser(#p0, authentication)")
    public ResponseEntity<Long> getTotalSevereExaminations(@RequestParam Long userId, @RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        log.info("Received request to get total severe examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalSevereExaminations(userId, isPersonal));
    }

    /**
     * Retrieves total verified examinations based on user role.
     *
     * @param userId The ID of the user requesting the total.
     * @return The total number of verified examinations.
     */
    @GetMapping("/total-verified")
    @PreAuthorize("@accessControl.canAccessUser(#p0, authentication)")
    public ResponseEntity<Long> getTotalVerifiedExaminations(@RequestParam Long userId, @RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        log.info("Received request to get total verified examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalVerifiedExaminations(userId, isPersonal));
    }

    /**
     * Retrieves total unverified examinations based on user role.
     *
     * @param userId The ID of the user requesting the total.
     * @return The total number of unverified examinations.
     */
    @GetMapping("/total-unverified")
    @PreAuthorize("@accessControl.canAccessUser(#p0, authentication)")
    public ResponseEntity<Long> getTotalUnverifiedExaminations(@RequestParam Long userId, @RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        log.info("Received request to get total unverified examinations for user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalUnverifiedExaminations(userId, isPersonal));
    }

    /**
     * Retrieves total examinations based on user role (from access token).
     */
    @GetMapping("/my-total")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<Long> getMyTotalExaminations(@RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalExaminations(userId, isPersonal));
    }

    /**
     * Retrieves total examinations in the last 7 days based on user role (from access token).
     */
    @GetMapping("/my-total-last-7-days")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<Long> getMyTotalLast7Days(@RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total examinations in the last 7 days for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalExaminationsInLast7Days(userId, isPersonal));
    }

    /**
     * Retrieves total severe examinations based on user role (from access token).
     */
    @GetMapping("/my-total-severe")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<Long> getMyTotalSevereExaminations(@RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total severe examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalSevereExaminations(userId, isPersonal));
    }

    /**
     * Retrieves total verified examinations based on user role (from access token).
     */
    @GetMapping("/my-total-verified")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<Long> getMyTotalVerifiedExaminations(@RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total verified examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalVerifiedExaminations(userId, isPersonal));
    }

    /**
     * Retrieves total unverified examinations based on user role (from access
     * token).
     */
    @GetMapping("/my-total-unverified")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
    public ResponseEntity<Long> getMyTotalUnverifiedExaminations(@RequestParam(defaultValue = "false", required = false) Boolean isPersonal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (String) authentication.getPrincipal();
        User user = userRepository.findByUsername(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();
        log.info("Received request to get total unverified examinations for my token, user id: {}", userId);
        return ResponseEntity.ok(examinationService.getTotalUnverifiedExaminations(userId, isPersonal));
    }
}
