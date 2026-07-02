package com.g93.be.controller;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.service.ExaminationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/examinations")
@RequiredArgsConstructor
@Slf4j
public class ExaminationController {

    private final ExaminationService examinationService;

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
}
