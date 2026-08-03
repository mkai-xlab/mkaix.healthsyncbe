package com.g93.be;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.Examination;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.mapper.ExaminationMapper;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.impl.ExaminationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminationServiceFilterSortTest {

    @Mock
    private ExaminationRepository examinationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DicomInstanceRepository dicomInstanceRepository;

    @Mock
    private ExaminationMapper examinationMapper;

    @InjectMocks
    private ExaminationServiceImpl examinationService;

    private User doctor;
    private User headOfDepartment;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        Role doctorRole = new Role();
        doctorRole.setCode("DOCTOR");
        doctor = new User();
        doctor.setId(1L);
        doctor.setUsername("doctor1");
        doctor.setRole(doctorRole);

        Role headRole = new Role();
        headRole.setCode("DEPARTMENT_HEAD");
        headOfDepartment = new User();
        headOfDepartment.setId(2L);
        headOfDepartment.setUsername("head_dept");
        headOfDepartment.setRole(headRole);

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void testGetExaminationsSortedByStudyDate_Doctor() {
        when(userRepository.findByUsernameOrEmail("doctor1", "doctor1")).thenReturn(Optional.of(doctor));
        Page<Examination> page = new PageImpl<>(List.of(new Examination()));
        Sort sort = Sort.by("studyDate").descending();
        Pageable sortedPageable = PageRequest.of(0, 10, sort);
        when(examinationRepository.findByDoctorId(1L, sortedPageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsSortedByStudyDate("desc", "doctor1", false, pageable);

        assertEquals(1, response.content().size());
        verify(examinationRepository).findByDoctorId(1L, sortedPageable);
    }

    @Test
    void testGetExaminationsSortedByStudyDate_HeadOfDepartment() {
        when(userRepository.findByUsernameOrEmail("head_dept", "head_dept")).thenReturn(Optional.of(headOfDepartment));
        Page<Examination> page = new PageImpl<>(List.of(new Examination(), new Examination()));
        Sort sort = Sort.by("studyDate").ascending();
        Pageable sortedPageable = PageRequest.of(0, 10, sort);
        when(examinationRepository.findAll(sortedPageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsSortedByStudyDate("asc", "head_dept", false, pageable);

        assertEquals(2, response.content().size());
        verify(examinationRepository).findAll(sortedPageable);
    }

    @Test
    void testGetExaminationsSortedByUploadDate_Doctor() {
        when(userRepository.findByUsernameOrEmail("doctor1", "doctor1")).thenReturn(Optional.of(doctor));
        Page<Examination> page = new PageImpl<>(List.of(new Examination()));
        Sort sort = Sort.by("createdAt").descending();
        Pageable sortedPageable = PageRequest.of(0, 10, sort);
        when(examinationRepository.findByDoctorId(1L, sortedPageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsSortedByUploadDate("desc", "doctor1", false, pageable);

        assertEquals(1, response.content().size());
        verify(examinationRepository).findByDoctorId(1L, sortedPageable);
    }

    @Test
    void testGetExaminationsFilteredByStudyDate_Doctor() {
        when(userRepository.findByUsernameOrEmail("doctor1", "doctor1")).thenReturn(Optional.of(doctor));
        Page<Examination> page = new PageImpl<>(List.of(new Examination()));
        LocalDate date = LocalDate.of(2026, 7, 22);
        when(examinationRepository.findByDoctorIdAndStudyDate(1L, date, pageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsFilteredByStudyDate(date, "doctor1", false, pageable);

        assertEquals(1, response.content().size());
        verify(examinationRepository).findByDoctorIdAndStudyDate(1L, date, pageable);
    }

    @Test
    void testGetExaminationsFilteredByStudyDate_HeadOfDepartment() {
        when(userRepository.findByUsernameOrEmail("head_dept", "head_dept")).thenReturn(Optional.of(headOfDepartment));
        Page<Examination> page = new PageImpl<>(List.of(new Examination()));
        LocalDate date = LocalDate.of(2026, 7, 22);
        when(examinationRepository.findByStudyDate(date, pageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsFilteredByStudyDate(date, "head_dept", false, pageable);

        assertEquals(1, response.content().size());
        verify(examinationRepository).findByStudyDate(date, pageable);
    }

    @Test
    void testGetExaminationsFilteredByUploadDate_Doctor() {
        when(userRepository.findByUsernameOrEmail("doctor1", "doctor1")).thenReturn(Optional.of(doctor));
        Page<Examination> page = new PageImpl<>(List.of(new Examination()));
        LocalDate date = LocalDate.of(2026, 7, 22);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);
        when(examinationRepository.findByDoctorIdAndCreatedAtBetween(1L, start, end, pageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsFilteredByUploadDate(date, "doctor1", false, pageable);

        assertEquals(1, response.content().size());
        verify(examinationRepository).findByDoctorIdAndCreatedAtBetween(1L, start, end, pageable);
    }

    @Test
    void testGetExaminationsFilteredByUploadDate_HeadOfDepartment() {
        when(userRepository.findByUsernameOrEmail("head_dept", "head_dept")).thenReturn(Optional.of(headOfDepartment));
        Page<Examination> page = new PageImpl<>(List.of(new Examination()));
        LocalDate date = LocalDate.of(2026, 7, 22);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);
        when(examinationRepository.findByCreatedAtBetween(start, end, pageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsFilteredByUploadDate(date, "head_dept", false, pageable);

        assertEquals(1, response.content().size());
        verify(examinationRepository).findByCreatedAtBetween(start, end, pageable);
    }

    @Test
    void testGetExaminationsByPatientIdAndStudyMonth() {
        Page<Examination> page = new PageImpl<>(List.of(new Examination()));
        int year = 2026;
        int month = 7;
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        when(examinationRepository.findByPatientIdAndStudyDateBetween(1L, startDate, endDate, pageable)).thenReturn(page);
        when(examinationMapper.toDto(any(), any())).thenReturn(new ExaminationDto());

        PageResponse<ExaminationDto> response = examinationService.getExaminationsByPatientIdAndStudyMonth(1L, year, month, pageable);

        assertEquals(1, response.content().size());
        verify(examinationRepository).findByPatientIdAndStudyDateBetween(1L, startDate, endDate, pageable);
    }
}
