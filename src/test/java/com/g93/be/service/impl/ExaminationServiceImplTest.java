package com.g93.be.service.impl;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.Examination;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.ExaminationStatus;
import com.g93.be.exception.UnauthorizedAccessException;
import com.g93.be.mapper.ExaminationMapper;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExaminationServiceImplTest {

    @Mock
    private ExaminationRepository examinationRepository;

    @Mock
    private DicomInstanceRepository dicomInstanceRepository;

    @Mock
    private ExaminationMapper examinationMapper;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExaminationServiceImpl examinationService;

    private User mockUser;
    private Role mockRole;
    private Pageable pageable;
    private Examination mockExam;
    private ExaminationDto mockExamDto;
    private Page<Examination> mockPage;
    private LocalDate testDate;
    private LocalDateTime startOfDay;
    private LocalDateTime endOfDay;

    @BeforeEach
    void setUp() {
        mockRole = new Role();
        mockRole.setCode("DOCTOR");

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("user1");
        mockUser.setRole(mockRole);

        pageable = PageRequest.of(0, 10);

        mockExam = new Examination();
        mockExam.setId(100L);

        mockExamDto = new ExaminationDto();
        mockExamDto.setExaminationId(100L);

        mockPage = new PageImpl<>(List.of(mockExam));

        testDate = LocalDate.now();
        startOfDay = testDate.atStartOfDay();
        endOfDay = testDate.atTime(23, 59, 59);
    }

    private Pageable getCustomSortPageable(Pageable page) {
        Sort sort = Sort.by(
                Sort.Order.desc("maxPredictedGrade").nullsLast(),
                Sort.Order.desc("createdAt"));
        return PageRequest.of(page.getPageNumber(), page.getPageSize(), sort);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc05
     */
    @Test
    void testGetAllExaminations_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorId(1L, getCustomSortPageable(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getAllExaminations(pageable, "user1", isPersonal);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorId(1L, getCustomSortPageable(pageable));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc04
     */
    @Test
    void testGetAllExaminations_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorId(1L, getCustomSortPageable(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getAllExaminations(pageable, "user1", isPersonal);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorId(1L, getCustomSortPageable(pageable));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetAllExaminations_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findAll(getCustomSortPageable(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getAllExaminations(pageable, "user1", isPersonal);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findAll(getCustomSortPageable(pageable));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc02
     */
    @Test
    void testGetAllExaminations_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findAll(getCustomSortPageable(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getAllExaminations(pageable, "user1", isPersonal);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findAll(getCustomSortPageable(pageable));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc01
     */
    @Test
    void testGetAllExaminations_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getAllExaminations(pageable, "user1", isPersonal);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc07
     */
    @Test
    void testGetAllExaminations_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getAllExaminations(pageable, "user1", isPersonal);
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc06
     */
    @Test
    void testGetAllExaminations_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getAllExaminations(pageable, "user1", isPersonal);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetAllExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetAllExaminations_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getAllExaminations(pageable, "user1", isPersonal);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStatus(eq(1L), eq(ExaminationStatus.VERIFIED), any(Pageable.class)))
                .thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED,
                "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndStatus(eq(1L), eq(ExaminationStatus.VERIFIED),
                any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStatus(eq(1L), eq(ExaminationStatus.VERIFIED), any(Pageable.class)))
                .thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED,
                "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndStatus(eq(1L), eq(ExaminationStatus.VERIFIED),
                any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStatus(eq(ExaminationStatus.VERIFIED), any(Pageable.class)))
                .thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED,
                "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByStatus(eq(ExaminationStatus.VERIFIED), any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStatus(eq(ExaminationStatus.VERIFIED), any(Pageable.class)))
                .thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED,
                "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByStatus(eq(ExaminationStatus.VERIFIED), any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED,
                "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED,
                "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByStatus.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED,
                "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndMaxPredictedGrade(1L, 2, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndMaxPredictedGrade(1L, 2, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndMaxPredictedGrade(1L, 2, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndMaxPredictedGrade(1L, 2, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByMaxPredictedGrade(2, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByMaxPredictedGrade(2, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByMaxPredictedGrade(2, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByMaxPredictedGrade(2, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID02).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc02
     */
    @Test
    void testGetExaminationsByGrade_UTCID02() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByMaxPredictedGrade(eq(99), any()))
                .thenThrow(new IllegalArgumentException("Invalid Input"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsByGrade(99, "user1", isPersonal, pageable);
        });

        assertTrue(ex.getMessage().contains("Invalid Input"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID03).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc03
     */
    @Test
    void testGetExaminationsByGrade_UTCID03() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = null;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByMaxPredictedGrade(eq(0), any())).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(0, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByMaxPredictedGrade(eq(0), any());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByGrade.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID08).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc08
     */
    @Test
    void testGetExaminationsByGrade_UTCID08() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByMaxPredictedGrade(eq(2), any()))
                .thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);
        });

        assertTrue(ex.getMessage().contains("DB Connection failure"));
    }

    // getTotalUnverifiedExaminations Tests
    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndStatus(1L, ExaminationStatus.NEED_VERIFY)).thenReturn(5L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndStatus(1L, ExaminationStatus.NEED_VERIFY);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndStatus(1L, ExaminationStatus.NEED_VERIFY)).thenReturn(5L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndStatus(1L, ExaminationStatus.NEED_VERIFY);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByStatus(ExaminationStatus.NEED_VERIFY)).thenReturn(10L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(10L, res);
        verify(examinationRepository).countByStatus(ExaminationStatus.NEED_VERIFY);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByStatus(ExaminationStatus.NEED_VERIFY)).thenReturn(15L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(15L, res);
        verify(examinationRepository).countByStatus(ExaminationStatus.NEED_VERIFY);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getTotalUnverifiedExaminations(1L, isPersonal);
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetTotalUnverifiedExaminations.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    // Group 3 Tests

    // getExaminationById
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID01
     */
    @Test
    void testGetExaminationById_Normal_Admin() {
        mockRole.setCode("ADMIN");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(100L)).thenReturn(Optional.of(mockExam));
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationById(100L, "user1");

        assertNotNull(res);
        assertEquals(100L, res.getExaminationId());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testGetExaminationById_Normal_DoctorOwnExam() {
        mockRole.setCode("DOCTOR");
        Doctor mockDoctor = new Doctor();
        mockDoctor.setId(mockUser.getId());
        mockExam.setDoctor(mockDoctor);
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(100L)).thenReturn(Optional.of(mockExam));
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationById(100L, "user1");

        assertNotNull(res);
        assertEquals(100L, res.getExaminationId());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID04
     */
    @Test
    void testGetExaminationById_Abnormal_DoctorOtherExam() {
        mockRole.setCode("DOCTOR");
        Doctor otherDoc = new Doctor();
        otherDoc.setId(2L);
        mockExam.setDoctor(otherDoc);

        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(105L)).thenReturn(Optional.of(mockExam));

        UnauthorizedAccessException ex = assertThrows(UnauthorizedAccessException.class, () -> {
            examinationService.getExaminationById(105L, "user1");
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testGetExaminationById_Abnormal_DoctorNoExamDoctor() {
        mockRole.setCode("DOCTOR");
        mockExam.setDoctor(null);

        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(100L)).thenReturn(Optional.of(mockExam));

        assertThrows(UnauthorizedAccessException.class, () -> {
            examinationService.getExaminationById(100L, "user1");
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID02
     */
    @Test
    void testGetExaminationById_Abnormal_ExamNotFound() {
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(-1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationById(-1L, "user1");
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID06
     */
    @Test
    void testGetExaminationById_Abnormal_UserNotFound() {
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationById(100L, "user1");
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID05
     */
    @Test
    void testGetExaminationById_Abnormal_IdNull() {
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(null))
                .thenThrow(new IllegalArgumentException("The given id must not be null!"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationById(null, "user1");
        });
        assertTrue(ex.getMessage().contains("must not be null"));
    }

    // getExaminationsByDoctorId
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID01
     */
    @Test
    void testGetExaminationsByDoctorId_Normal() {
        when(examinationRepository.findByDoctorId(eq(10L), any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsByDoctorId(10L, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    // getExaminationsByPatientId
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByPatientId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * 
     * Kịch bản Test Design: UTCID01
     */
    @Test
    void testGetExaminationsByPatientId_Normal() {
        when(examinationRepository.findByPatientId(eq(5L), any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsByPatientId(5L, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByPatientId.
     * Đầu vào: Kịch bản: Luồng lỗi với patientId không tồn tại (-1).
     * Hành động: Gọi phương thức GetExaminationsByPatientId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả rỗng.
     * 
     * Kịch bản Test Design: UTCID02
     */
    @Test
    void testGetExaminationsByPatientId_UTCID02_InvalidId() {
        Page<Examination> emptyPage = new PageImpl<>(List.of());
        when(examinationRepository.findByPatientId(eq(-1L), any(Pageable.class))).thenReturn(emptyPage);

        var res = examinationService.getExaminationsByPatientId(-1L, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    // getExaminationsByPatientIdAndStudyMonth
    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByPatientIdAndStudyMonth.
     * Đầu vào: Kịch bản: Luồng chuẩn (Normal).
     * Hành động: Gọi phương thức GetExaminationsByPatientIdAndStudyMonth().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     * utc01
     */
    @Test
    void testGetExaminationsByPatientIdAndStudyMonth_Normal() {
        when(examinationRepository.findByPatientIdAndStudyDateBetween(eq(5L), any(LocalDate.class),
                any(LocalDate.class), eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsByPatientIdAndStudyMonth(5L, 2023, 10, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByPatientIdAndStudyMonth.
     * Đầu vào: Kịch bản: Lỗi kết nối DB.
     * Hành động: Gọi phương thức GetExaminationsByPatientIdAndStudyMonth().
     * Kỳ vọng: Ném ra RuntimeException.
     * 
     * Kịch bản Test Design: UTCID02
     */
    @Test
    void testGetExaminationsByPatientIdAndStudyMonth_UTCID02_DBFailure() {
        when(examinationRepository.findByPatientIdAndStudyDateBetween(eq(5L), any(LocalDate.class),
                any(LocalDate.class), eq(pageable))).thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getExaminationsByPatientIdAndStudyMonth(5L, 2023, 10, pageable);
        });
        assertTrue(ex.getMessage().contains("DB Connection failure"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByPatientIdAndStudyMonth.
     * Đầu vào: Kịch bản: Giá trị biên của năm (year = -1).
     * Hành động: Gọi phương thức GetExaminationsByPatientIdAndStudyMonth().
     * Kỳ vọng: Ném ra IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testGetExaminationsByPatientIdAndStudyMonth_UTCID03_InvalidYear() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsByPatientIdAndStudyMonth(5L, -1, 10, pageable);
        });
        assertTrue(ex.getMessage().contains("Invalid year"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByPatientIdAndStudyMonth.
     * Đầu vào: Kịch bản: Tháng không hợp lệ (month = 13).
     * Hành động: Gọi phương thức GetExaminationsByPatientIdAndStudyMonth().
     * Kỳ vọng: Ném ra lỗi.
     * 
     * Kịch bản Test Design: UTCID04
     */
    @Test
    void testGetExaminationsByPatientIdAndStudyMonth_UTCID04_InvalidMonth() {
        Exception ex = assertThrows(Exception.class, () -> {
            examinationService.getExaminationsByPatientIdAndStudyMonth(5L, 2023, 13, pageable);
        });
        assertTrue(ex.getMessage().contains("Invalid value for MonthOfYear"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByPatientIdAndStudyMonth.
     * Đầu vào: Kịch bản: Thời gian ở tương lai.
     * Hành động: Gọi phương thức GetExaminationsByPatientIdAndStudyMonth().
     * Kỳ vọng: Ném ra IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID05
     */
    @Test
    void testGetExaminationsByPatientIdAndStudyMonth_UTCID05_FutureDate() {
        LocalDate futureDate = LocalDate.now().plusMonths(1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsByPatientIdAndStudyMonth(5L, futureDate.getYear(),
                    futureDate.getMonthValue(), pageable);
        });
        assertTrue(ex.getMessage().contains("future"));
    }

    // getExaminationsFilteredByStudyDate
    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByStudyDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role ADMIN.
     * Hành động: Gọi phương thức getExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về PageResponse rỗng.
     * 
     * Kịch bản Test Design: UTCID01
     */
    @Test
    void testGetExaminationsFilteredByStudyDate_UTCID01_AdminNormal() {
        mockRole.setCode("ADMIN");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStudyDate(eq(LocalDate.of(2024, 1, 1)), eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByStudyDate(LocalDate.of(2024, 1, 1), "user1", false,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByStudyDate.
     * Đầu vào: Kịch bản: Lỗi kết nối DB.
     * Hành động: Gọi phương thức getExaminationsFilteredByStudyDate().
     * Kỳ vọng: Ném ra RuntimeException.
     * 
     * Kịch bản Test Design: UTCID07
     */
    @Test
    void testGetExaminationsFilteredByStudyDate_UTCID02_DBFailure() {
        mockRole.setCode("ADMIN");
        when(userRepository.findByUsernameOrEmail("user1", "user1"))
                .thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getExaminationsFilteredByStudyDate(LocalDate.of(2024, 1, 1), "user1", false, pageable);
        });
        assertTrue(ex.getMessage().contains("DB Connection failure"));
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByStudyDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role HEAD_OF_DEPARTMENT, isPersonal = false.
     * Hành động: Gọi phương thức getExaminationsFilteredByStudyDate().
     * Kỳ vọng: Trả về danh sách dựa trên ngày.
     * 
     * Kịch bản Test Design: UTCID04
     */
    @Test
    void testGetExaminationsFilteredByStudyDate_UTCID03_HeadNormal_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStudyDate(eq(LocalDate.of(2024, 1, 1)), eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByStudyDate(LocalDate.of(2024, 1, 1), "user1", false,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByStudyDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role HEAD_OF_DEPARTMENT, isPersonal = true.
     * Hành động: Gọi phương thức getExaminationsFilteredByStudyDate().
     * Kỳ vọng: Trả về danh sách dựa trên bác sĩ và ngày.
     * 
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testGetExaminationsFilteredByStudyDate_UTCID04_HeadNormal_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStudyDate(eq(1L), eq(LocalDate.of(2024, 1, 1)), eq(pageable)))
                .thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByStudyDate(LocalDate.of(2024, 1, 1), "user1", true,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByStudyDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role DOCTOR, isPersonal = any.
     * Hành động: Gọi phương thức getExaminationsFilteredByStudyDate().
     * Kỳ vọng: Trả về danh sách dựa trên bác sĩ và ngày.
     * 
     * Kịch bản Test Design: UTCID05
     */
    @Test
    void testGetExaminationsFilteredByStudyDate_UTCID05_DoctorNormal() {
        mockRole.setCode("DOCTOR");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStudyDate(eq(1L), eq(LocalDate.of(2024, 1, 1)), eq(pageable)))
                .thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByStudyDate(LocalDate.of(2024, 1, 1), "user1", false,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByStudyDate.
     * Đầu vào: Kịch bản: User không tồn tại.
     * Hành động: Gọi phương thức getExaminationsFilteredByStudyDate().
     * Kỳ vọng: Ném ra IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID06
     */
    @Test
    void testGetExaminationsFilteredByStudyDate_UTCID06_UserNotFound() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByStudyDate(LocalDate.of(2024, 1, 1), "ghost", false, pageable);
        });
        assertTrue(ex.getMessage().contains("User with username/email ghost not found"));
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByStudyDate.
     * Đầu vào: Kịch bản: Ngày truyền vào ở tương lai.
     * Hành động: Gọi phương thức getExaminationsFilteredByStudyDate().
     * Kỳ vọng: Ném ra IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID02
     */
    @Test
    void testGetExaminationsFilteredByStudyDate_UTCID07_FutureDate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByStudyDate(LocalDate.of(2100, 1, 1), "user1", false, pageable);
        });
        assertTrue(ex.getMessage().contains("future"));
    }

    // getExaminationsFilteredByUploadDate
    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role ADMIN.
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về System Data.
     * 
     * Kịch bản Test Design: UTCID01
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID01_AdminNormal() {
        mockRole.setCode("ADMIN");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2024, 1, 1), "user1", false,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: Lỗi kết nối DB.
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Ném ra RuntimeException.
     * 
     * Kịch bản Test Design: UTCID08
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID08_DBFailure() {
        mockRole.setCode("ADMIN");
        when(userRepository.findByUsernameOrEmail("user1", "user1"))
                .thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2024, 1, 1), "user1", false, pageable);
        });
        assertTrue(ex.getMessage().contains("DB Connection failure"));
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: Giá trị biên ngày nhuận (2024-02-29).
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Trả về System Data.
     * 
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID03_AdminBoundary() {
        mockRole.setCode("ADMIN");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2024, 2, 29), "user1", null,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role HEAD_OF_DEPARTMENT, isPersonal = false.
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Trả về System Data.
     * 
     * Kịch bản Test Design: UTCID04
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID04_HeadNormal_SystemData() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2024, 1, 1), "user1", false,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role HEAD_OF_DEPARTMENT, isPersonal = true.
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Trả về Personal Data.
     * 
     * Kịch bản Test Design: UTCID05
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID05_HeadNormal_PersonalData() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndCreatedAtBetween(eq(1L), any(LocalDateTime.class),
                any(LocalDateTime.class), eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2024, 1, 1), "user1", true,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: Luồng chuẩn, role DOCTOR, isPersonal = any.
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Trả về Personal Data.
     * 
     * Kịch bản Test Design: UTCID06
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID06_DoctorNormal() {
        mockRole.setCode("DOCTOR");
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndCreatedAtBetween(eq(1L), any(LocalDateTime.class),
                any(LocalDateTime.class), eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2024, 1, 1), "user1", false,
                pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: User không tồn tại.
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Ném ra IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID07
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID07_UserNotFound() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2024, 1, 1), "ghost", false, pageable);
        });
        assertTrue(ex.getMessage().contains("User with username/email ghost not found"));
    }

    /**
     * Mục đích: Kiểm tra chức năng getExaminationsFilteredByUploadDate.
     * Đầu vào: Kịch bản: Ngày truyền vào ở tương lai.
     * Hành động: Gọi phương thức getExaminationsFilteredByUploadDate().
     * Kỳ vọng: Ném ra IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID02
     */
    @Test
    void testGetExaminationsFilteredByUploadDate_UTCID02_FutureDate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByUploadDate(LocalDate.of(2100, 1, 1), "user1", false, pageable);
        });
        assertTrue(ex.getMessage().contains("future"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationById.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID07).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationById_UTCID07() {
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenThrow(new RuntimeException("DB Error"));
        assertThrows(RuntimeException.class,
                () -> examinationService.getExaminationById(1L, "user1"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByDoctorId.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID02).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByDoctorId_UTCID02() {
        Page<Examination> page = new PageImpl<>(List.of());
        when(examinationRepository.findByDoctorId(eq(-1L), any())).thenReturn(page);
        var res = examinationService.getExaminationsByDoctorId(-1L, PageRequest.of(0, 10));
        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByDoctorId.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID03).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByDoctorId_UTCID03() {
        Page<Examination> page = new PageImpl<>(List.of());
        when(examinationRepository.findByDoctorId(eq(Long.MAX_VALUE), any())).thenReturn(page);
        var res = examinationService.getExaminationsByDoctorId(Long.MAX_VALUE, PageRequest.of(0, 10));
        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByDoctorId.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID04).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByDoctorId_UTCID04() {
        when(examinationRepository.findByDoctorId(eq(10L), any()))
                .thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getExaminationsByDoctorId(10L, PageRequest.of(0, 10));
        });

        assertTrue(ex.getMessage().contains("DB Connection failure"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByDoctorId.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID05).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByDoctorId_UTCID05() {
        Page<Examination> page = new PageImpl<>(
                List.of());
        when(examinationRepository.findByDoctorId(eq(1L),
                any())).thenReturn(page);
        var res = examinationService.getExaminationsByDoctorId(1L,
                PageRequest.of(0, 10));
        assertNotNull(res);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByDoctorId.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID06).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByDoctorId_UTCID06() {
        Page<Examination> page = new PageImpl<>(
                List.of());
        when(examinationRepository.findByDoctorId(eq(1L),
                any())).thenReturn(page);
        var res = examinationService.getExaminationsByDoctorId(1L,
                PageRequest.of(0, 10));
        assertNotNull(res);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByDoctorId.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID07).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByDoctorId_UTCID07() {
        Page<Examination> page = new PageImpl<>(
                List.of());
        when(examinationRepository.findByDoctorId(eq(1L),
                any())).thenReturn(page);
        var res = examinationService.getExaminationsByDoctorId(1L,
                PageRequest.of(0, 10));
        assertNotNull(res);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetExaminationsByDoctorId.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID08).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByDoctorId_UTCID08() {
        Page<Examination> page = new PageImpl<>(
                List.of());
        when(examinationRepository.findByDoctorId(eq(1L),
                any())).thenReturn(page);
        var res = examinationService.getExaminationsByDoctorId(1L,
                PageRequest.of(0, 10));
        assertNotNull(res);
    }
    // -------------------------------------------------------------------------
    // Tests for markAsViewed
    // -------------------------------------------------------------------------

    @Test
    void testMarkAsViewed_UTCID01() {
        mockExam.setId(1L);
        mockExam.setIsViewed(0);
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExam));

        examinationService.markAsViewed(1L);

        org.mockito.Mockito.verify(examinationRepository, org.mockito.Mockito.times(1)).save(mockExam);
        assertEquals(1, mockExam.getIsViewed());
    }

    @Test
    void testMarkAsViewed_UTCID02() {
        mockExam.setId(1L);
        mockExam.setIsViewed(1);
        when(examinationRepository.findById(1L)).thenReturn(Optional.of(mockExam));

        examinationService.markAsViewed(1L);

        org.mockito.Mockito.verify(examinationRepository, org.mockito.Mockito.times(1)).save(mockExam);
        assertEquals(1, mockExam.getIsViewed());
    }

    @Test
    void testMarkAsViewed_UTCID03() {
        when(examinationRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.markAsViewed(999L);
                });
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testMarkAsViewed_UTCID04() {
        when(examinationRepository.findById(null))
                .thenThrow(new IllegalArgumentException("The given id must not be null!"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.markAsViewed(null);
                });
        assertTrue(ex.getMessage().contains("must not be null"));
    }

    @Test
    void testMarkAsViewed_UTCID05() {
        when(examinationRepository.findById(-1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.markAsViewed(-1L);
                });
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testMarkAsViewed_UTCID06() {
        when(examinationRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.markAsViewed(Long.MAX_VALUE);
                });
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testMarkAsViewed_UTCID07() {
        when(examinationRepository.findById(1L)).thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.markAsViewed(1L);
        });
        assertEquals("DB Connection failure", ex.getMessage());
    }
    // -------------------------------------------------------------------------
    // Tests for countSevereExaminations (getTotalSevereExaminations)
    // -------------------------------------------------------------------------

    @Test
    void testCountSevereExaminations_UTCID01() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByMaxPredictedGradeIn(List.of(3, 4))).thenReturn(10L);

        long res = examinationService.getTotalSevereExaminations(1L, false);

        assertEquals(10L, res);
    }

    @Test
    void testCountSevereExaminations_UTCID03() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByMaxPredictedGradeIn(List.of(3, 4))).thenReturn(15L);

        long res = examinationService.getTotalSevereExaminations(1L, null);

        assertEquals(15L, res);
    }

    @Test
    void testCountSevereExaminations_UTCID02() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalSevereExaminations(1L, true);

        assertEquals(0L, res);
    }

    @Test
    void testCountSevereExaminations_UTCID04() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByMaxPredictedGradeIn(List.of(3, 4))).thenReturn(20L);

        long res = examinationService.getTotalSevereExaminations(1L, false);

        assertEquals(20L, res);
    }

    @Test
    void testCountSevereExaminations_UTCID05() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(1L, List.of(3, 4))).thenReturn(2L);

        long res = examinationService.getTotalSevereExaminations(1L, true);

        assertEquals(2L, res);
    }

    @Test
    void testCountSevereExaminations_UTCID06() {
        mockRole.setCode("DOCTOR");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(1L, List.of(3, 4))).thenReturn(7L);

        long res = examinationService.getTotalSevereExaminations(1L, null);

        assertEquals(7L, res);
    }

    @Test
    void testCountSevereExaminations_UTCID07() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.getTotalSevereExaminations(999L, false);
                });
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testCountSevereExaminations_UTCID08() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByMaxPredictedGradeIn(List.of(3, 4)))
                .thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getTotalSevereExaminations(1L, false);
        });
        assertEquals("DB Connection failure", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // Tests for countTotalExaminations (getTotalExaminations)
    // -------------------------------------------------------------------------

    @Test
    void testCountTotalExaminations_UTCID01_AdminNormal() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.count()).thenReturn(100L);

        long res = examinationService.getTotalExaminations(1L, false);
        assertEquals(100L, res);
    }

    @Test
    void testCountTotalExaminations_UTCID08_DBFailure() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getTotalExaminations(1L, false);
        });
        assertTrue(ex.getMessage().contains("DB Connection failure"));
    }

    @Test
    void testCountTotalExaminations_UTCID03_AdminBoundary() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.count()).thenReturn(100L);

        long res = examinationService.getTotalExaminations(1L, null);
        assertEquals(100L, res);
    }

    @Test
    void testCountTotalExaminations_UTCID04_HeadNormal_SystemData() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.count()).thenReturn(100L);

        long res = examinationService.getTotalExaminations(1L, false);
        assertEquals(100L, res);
    }

    @Test
    void testCountTotalExaminations_UTCID05_HeadNormal_PersonalData() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorId(1L)).thenReturn(50L);

        long res = examinationService.getTotalExaminations(1L, true);
        assertEquals(50L, res);
    }

    @Test
    void testCountTotalExaminations_UTCID06_DoctorNormal() {
        mockRole.setCode("DOCTOR");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorId(1L)).thenReturn(50L);

        long res = examinationService.getTotalExaminations(1L, false);
        assertEquals(50L, res);
    }

    @Test
    void testCountTotalExaminations_UTCID07_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getTotalExaminations(999L, false);
        });
        assertTrue(ex.getMessage().contains("User with id 999 not found"));
    }

    @Test
    void testCountTotalExaminations_UTCID02_InvalidInput() {
        when(userRepository.findById(-1L)).thenThrow(new IllegalArgumentException("Invalid Input"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getTotalExaminations(-1L, false);
        });
        assertTrue(ex.getMessage().contains("Invalid Input"));
    }

    // -------------------------------------------------------------------------
    // Tests for countTotalExaminationsLast7Days (getTotalExaminationsInLast7Days)
    // -------------------------------------------------------------------------

    @Test
    void testCountTotalExaminationsLast7Days_UTCID01() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository
                .countByCreatedAtAfter(any(java.time.LocalDateTime.class)))
                .thenReturn(10L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, false);

        assertEquals(10L, res);
    }

    @Test
    void testCountTotalExaminationsLast7Days_UTCID03() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository
                .countByCreatedAtAfter(any(java.time.LocalDateTime.class)))
                .thenReturn(15L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, null);

        assertEquals(15L, res);
    }

    @Test
    void testCountTotalExaminationsLast7Days_UTCID02() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalExaminationsInLast7Days(1L, true);

        assertEquals(0L, res);
    }

    @Test
    void testCountTotalExaminationsLast7Days_UTCID04() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository
                .countByCreatedAtAfter(any(java.time.LocalDateTime.class)))
                .thenReturn(20L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, false);

        assertEquals(20L, res);
    }

    @Test
    void testCountTotalExaminationsLast7Days_UTCID05() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndCreatedAtAfter(eq(1L),
                any(java.time.LocalDateTime.class))).thenReturn(2L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, true);

        assertEquals(2L, res);
    }

    @Test
    void testCountTotalExaminationsLast7Days_UTCID06() {
        mockRole.setCode("DOCTOR");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndCreatedAtAfter(eq(1L),
                any(java.time.LocalDateTime.class))).thenReturn(7L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, null);

        assertEquals(7L, res);
    }

    @Test
    void testCountTotalExaminationsLast7Days_UTCID07() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.getTotalExaminationsInLast7Days(999L, false);
                });
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testCountTotalExaminationsLast7Days_UTCID08() {
        mockRole.setCode("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository
                .countByCreatedAtAfter(any(java.time.LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getTotalExaminationsInLast7Days(1L, false);
        });
        assertEquals("DB Connection failure", ex.getMessage());
    }
    // -------------------------------------------------------------------------
    // Tests for getPatientGradeStatistics
    // -------------------------------------------------------------------------

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID01).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID01() {
        mockRole.setCode("ADMIN");
        mockUser.setId(10L);
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        var res = examinationService.getPatientGradeStatistics("user1", false);

        assertNotNull(res);
        // Admin doesn't have personal exams if true, but for false it should return
        // System Data.
        // Wait, looking at implementation, ADMIN with isPersonal=false returns
        // countPatientsByLatestGrade()
        org.mockito.Mockito.verify(examinationRepository).countPatientsByLatestGrade();
    }

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID02).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID02() {
        when(userRepository.findByUsernameOrEmail(null, null)).thenThrow(new IllegalArgumentException("Invalid Input"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.getPatientGradeStatistics(null, false);
                });
        assertTrue(ex.getMessage().contains("Invalid Input"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID03).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID03() {
        mockRole.setCode("ADMIN");
        mockUser.setId(10L);
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        var res = examinationService.getPatientGradeStatistics("user1", null);

        assertNotNull(res);
        org.mockito.Mockito.verify(examinationRepository).countPatientsByLatestGrade();
    }

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID04).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID04() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        mockUser.setId(10L);
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        var res = examinationService.getPatientGradeStatistics("user1", false);

        assertNotNull(res);
        org.mockito.Mockito.verify(examinationRepository).countPatientsByLatestGrade();
    }

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID05).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID05() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        mockUser.setId(10L);
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        var res = examinationService.getPatientGradeStatistics("user1", true);

        assertNotNull(res);
        org.mockito.Mockito.verify(examinationRepository).countPatientsByLatestGradeForDoctor(10L);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID06).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID06() {
        mockRole.setCode("DOCTOR");
        mockUser.setId(10L);
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        var res = examinationService.getPatientGradeStatistics("user1", true);

        assertNotNull(res);
        org.mockito.Mockito.verify(examinationRepository).countPatientsByLatestGradeForDoctor(10L);
    }

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID07).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID07() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    examinationService.getPatientGradeStatistics("ghost", false);
                });
        assertTrue(ex.getMessage().contains("not found"));
    }

    /**
     * Mục đích: Kiểm tra chức năng GetPatientGradeStatistics.
     * Đầu vào: Kịch bản: Phụ thuộc vào Test Matrix (UTCID08).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetPatientGradeStatistics_UTCID08() {
        mockRole.setCode("ADMIN");
        mockUser.setId(10L);
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countPatientsByLatestGrade())
                .thenThrow(new RuntimeException("DB Connection failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            examinationService.getPatientGradeStatistics("user1", false);
        });
        assertEquals("DB Connection failure", ex.getMessage());
    }
}
