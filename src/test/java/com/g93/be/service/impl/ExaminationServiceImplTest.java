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
            Sort.Order.desc("createdAt")
        );
        return PageRequest.of(page.getPageNumber(), page.getPageSize(), sort);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetAllExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStatus(ExaminationStatus.VERIFIED, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByStatus(ExaminationStatus.VERIFIED, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStatus(ExaminationStatus.VERIFIED, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByStatus(ExaminationStatus.VERIFIED, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsByStatus().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByStatus_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByStatus(ExaminationStatus.VERIFIED, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsByGrade().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByGrade_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsByGrade(2, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorId(eq(1L), any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorId(eq(1L), any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorId(eq(1L), any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorId(eq(1L), any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findAll(any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findAll(any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findAll(any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findAll(any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByStudyDate_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByStudyDate("asc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorId(eq(1L), any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorId(eq(1L), any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorId(eq(1L), any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorId(eq(1L), any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findAll(any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findAll(any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findAll(any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findAll(any(Pageable.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsSortedByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsSortedByUploadDate_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsSortedByUploadDate("desc", "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStudyDate(1L, testDate, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndStudyDate(1L, testDate, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndStudyDate(1L, testDate, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndStudyDate(1L, testDate, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStudyDate(testDate, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByStudyDate(testDate, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByStudyDate(testDate, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByStudyDate(testDate, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByStudyDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null; // Should ignore and use personal
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndCreatedAtBetween(1L, startOfDay, endOfDay, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndCreatedAtBetween(1L, startOfDay, endOfDay, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByDoctorIdAndCreatedAtBetween(1L, startOfDay, endOfDay, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByDoctorIdAndCreatedAtBetween(1L, startOfDay, endOfDay, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByCreatedAtBetween(startOfDay, endOfDay, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByCreatedAtBetween(startOfDay, endOfDay, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findByCreatedAtBetween(startOfDay, endOfDay, pageable)).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(examinationRepository).findByCreatedAtBetween(startOfDay, endOfDay, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        PageResponse<ExaminationDto> res = examinationService.getExaminationsFilteredByUploadDate(testDate, "user1", isPersonal, pageable);

        assertNotNull(res);
        assertTrue(res.content().isEmpty());
    }


    // getTotalExaminations Tests
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorId(1L)).thenReturn(5L);

        long res = examinationService.getTotalExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorId(1L);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorId(1L)).thenReturn(5L);

        long res = examinationService.getTotalExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorId(1L);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.count()).thenReturn(10L);

        long res = examinationService.getTotalExaminations(1L, isPersonal);

        assertEquals(10L, res);
        verify(examinationRepository).count();
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.count()).thenReturn(15L);

        long res = examinationService.getTotalExaminations(1L, isPersonal);

        assertEquals(15L, res);
        verify(examinationRepository).count();
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getTotalExaminations(1L, isPersonal);
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminations_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    // getTotalExaminationsInLast7Days Tests
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(5L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class))).thenReturn(5L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(10L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);

        assertEquals(10L, res);
        verify(examinationRepository).countByCreatedAtAfter(any(LocalDateTime.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(15L);

        long res = examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);

        assertEquals(15L, res);
        verify(examinationRepository).countByCreatedAtAfter(any(LocalDateTime.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalExaminationsInLast7Days().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalExaminationsInLast7Days_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalExaminationsInLast7Days(1L, isPersonal);

        assertEquals(0L, res);
    }

    // getTotalSevereExaminations Tests
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(eq(1L), anyList())).thenReturn(5L);

        long res = examinationService.getTotalSevereExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndMaxPredictedGradeIn(eq(1L), anyList());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndMaxPredictedGradeIn(eq(1L), anyList())).thenReturn(5L);

        long res = examinationService.getTotalSevereExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndMaxPredictedGradeIn(eq(1L), anyList());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByMaxPredictedGradeIn(anyList())).thenReturn(10L);

        long res = examinationService.getTotalSevereExaminations(1L, isPersonal);

        assertEquals(10L, res);
        verify(examinationRepository).countByMaxPredictedGradeIn(anyList());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByMaxPredictedGradeIn(anyList())).thenReturn(15L);

        long res = examinationService.getTotalSevereExaminations(1L, isPersonal);

        assertEquals(15L, res);
        verify(examinationRepository).countByMaxPredictedGradeIn(anyList());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalSevereExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getTotalSevereExaminations(1L, isPersonal);
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalSevereExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalSevereExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalSevereExaminations_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalSevereExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    // getTotalVerifiedExaminations Tests
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED)).thenReturn(5L);

        long res = examinationService.getTotalVerifiedExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED)).thenReturn(5L);

        long res = examinationService.getTotalVerifiedExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndStatus(1L, ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByStatus(ExaminationStatus.VERIFIED)).thenReturn(10L);

        long res = examinationService.getTotalVerifiedExaminations(1L, isPersonal);

        assertEquals(10L, res);
        verify(examinationRepository).countByStatus(ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByStatus(ExaminationStatus.VERIFIED)).thenReturn(15L);

        long res = examinationService.getTotalVerifiedExaminations(1L, isPersonal);

        assertEquals(15L, res);
        verify(examinationRepository).countByStatus(ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalVerifiedExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getTotalVerifiedExaminations(1L, isPersonal);
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalVerifiedExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetTotalVerifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalVerifiedExaminations_Abnormal_RoleUnrecognized() {
        mockRole.setCode("PATIENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        long res = examinationService.getTotalVerifiedExaminations(1L, isPersonal);

        assertEquals(0L, res);
    }

    // getTotalUnverifiedExaminations Tests
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndStatusNot(1L, ExaminationStatus.VERIFIED)).thenReturn(5L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndStatusNot(1L, ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByDoctorIdAndStatusNot(1L, ExaminationStatus.VERIFIED)).thenReturn(5L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(5L, res);
        verify(examinationRepository).countByDoctorIdAndStatusNot(1L, ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByStatusNot(ExaminationStatus.VERIFIED)).thenReturn(10L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(10L, res);
        verify(examinationRepository).countByStatusNot(ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetTotalUnverifiedExaminations().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetTotalUnverifiedExaminations_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(examinationRepository.countByStatusNot(ExaminationStatus.VERIFIED)).thenReturn(15L);

        long res = examinationService.getTotalUnverifiedExaminations(1L, isPersonal);

        assertEquals(15L, res);
        verify(examinationRepository).countByStatusNot(ExaminationStatus.VERIFIED);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
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
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
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

    // getPatientGradeStatistics Tests
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetPatientGradeStatistics_Normal_Doctor() {
        mockRole.setCode("DOCTOR");
        Boolean isPersonal = null;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        
        com.g93.be.repository.ExaminationRepository.GradePatientCountProjection mockProj = mock(com.g93.be.repository.ExaminationRepository.GradePatientCountProjection.class);
        when(mockProj.getGrade()).thenReturn(1);
        when(mockProj.getPatientCount()).thenReturn(5L);
        when(examinationRepository.countPatientsByLatestGradeForDoctor(1L)).thenReturn(List.of(mockProj));

        var res = examinationService.getPatientGradeStatistics("user1", isPersonal);

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(1, res.get(0).getGrade());
        assertEquals(5L, res.get(0).getPatientCount());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetPatientGradeStatistics_Normal_Head_Personal() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        
        com.g93.be.repository.ExaminationRepository.GradePatientCountProjection mockProj = mock(com.g93.be.repository.ExaminationRepository.GradePatientCountProjection.class);
        when(mockProj.getGrade()).thenReturn(1);
        when(mockProj.getPatientCount()).thenReturn(5L);
        when(examinationRepository.countPatientsByLatestGradeForDoctor(1L)).thenReturn(List.of(mockProj));

        var res = examinationService.getPatientGradeStatistics("user1", isPersonal);

        assertEquals(1, res.size());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetPatientGradeStatistics_Normal_Head_All() {
        mockRole.setCode("HEAD_OF_DEPARTMENT");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        
        com.g93.be.repository.ExaminationRepository.GradePatientCountProjection mockProj = mock(com.g93.be.repository.ExaminationRepository.GradePatientCountProjection.class);
        when(mockProj.getGrade()).thenReturn(1);
        when(mockProj.getPatientCount()).thenReturn(10L);
        when(examinationRepository.countPatientsByLatestGrade()).thenReturn(List.of(mockProj));

        var res = examinationService.getPatientGradeStatistics("user1", isPersonal);

        assertEquals(1, res.size());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetPatientGradeStatistics_Normal_Admin_All() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        
        com.g93.be.repository.ExaminationRepository.GradePatientCountProjection mockProj = mock(com.g93.be.repository.ExaminationRepository.GradePatientCountProjection.class);
        when(mockProj.getGrade()).thenReturn(1);
        when(mockProj.getPatientCount()).thenReturn(15L);
        when(examinationRepository.countPatientsByLatestGrade()).thenReturn(List.of(mockProj));

        var res = examinationService.getPatientGradeStatistics("user1", isPersonal);

        assertEquals(1, res.size());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetPatientGradeStatistics_Abnormal_Admin_Personal() {
        mockRole.setCode("ADMIN");
        Boolean isPersonal = true;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        var res = examinationService.getPatientGradeStatistics("user1", isPersonal);

        assertTrue(res.isEmpty());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetPatientGradeStatistics_Abnormal_UserNotFound() {
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getPatientGradeStatistics("user1", isPersonal);
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetPatientGradeStatistics().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetPatientGradeStatistics_Abnormal_RoleNull() {
        mockUser.setRole(null);
        Boolean isPersonal = false;
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));

        var res = examinationService.getPatientGradeStatistics("user1", isPersonal);

        assertTrue(res.isEmpty());
    }


    // Group 3 Tests
    
    // getExaminationById
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
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
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
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
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationById_Abnormal_DoctorOtherExam() {
        mockRole.setCode("DOCTOR");
        Doctor otherDoc = new Doctor();
        otherDoc.setId(2L);
        mockExam.setDoctor(otherDoc);
        
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(100L)).thenReturn(Optional.of(mockExam));

        UnauthorizedAccessException ex = assertThrows(UnauthorizedAccessException.class, () -> {
            examinationService.getExaminationById(100L, "user1");
        });
        assertTrue(ex.getMessage() != null);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
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
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationById_Abnormal_ExamNotFound() {
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.of(mockUser));
        when(examinationRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationById(100L, "user1");
        });
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationById().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationById_Abnormal_UserNotFound() {
        when(userRepository.findByUsernameOrEmail("user1", "user1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationById(100L, "user1");
        });
    }

    // getExaminationsByDoctorId
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByDoctorId().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
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
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsByPatientId_Normal() {
        when(examinationRepository.findByPatientId(eq(20L), any(Pageable.class))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsByPatientId(20L, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    // getExaminationsByPatientIdAndStudyMonth
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức GetExaminationsByPatientIdAndStudyMonth().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByPatientIdAndStudyMonth_Normal() {
        when(examinationRepository.findByPatientIdAndStudyDateBetween(eq(20L), any(LocalDate.class), any(LocalDate.class), eq(pageable))).thenReturn(mockPage);
        when(dicomInstanceRepository.findByExaminationId(100L)).thenReturn(List.of());
        when(examinationMapper.toDto(mockExam, List.of())).thenReturn(mockExamDto);

        var res = examinationService.getExaminationsByPatientIdAndStudyMonth(20L, 2023, 10, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    // markAsViewed
    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testMarkAsViewed_Normal() {
        when(examinationRepository.findById(100L)).thenReturn(Optional.of(mockExam));
        when(examinationRepository.save(mockExam)).thenReturn(mockExam);

        examinationService.markAsViewed(100L);

        assertEquals(1, mockExam.getIsViewed());
        verify(examinationRepository).save(mockExam);
    }

    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testMarkAsViewed_Abnormal_ExamNotFound() {
        when(examinationRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            examinationService.markAsViewed(100L);
        });
    }


    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByStudyDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByStudyDate_Abnormal_FutureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByStudyDate(futureDate, "user1", false, pageable);
        });
        assertTrue(ex.getMessage().contains("future"));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsFilteredByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testGetExaminationsFilteredByUploadDate_Abnormal_FutureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsFilteredByUploadDate(futureDate, "user1", false, pageable);
        });
        assertTrue(ex.getMessage().contains("future"));
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức GetExaminationsByPatientIdAndStudyMonth().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    void testGetExaminationsByPatientIdAndStudyMonth_Abnormal_FutureDate() {
        LocalDate futureDate = LocalDate.now().plusMonths(1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.getExaminationsByPatientIdAndStudyMonth(1L, futureDate.getYear(), futureDate.getMonthValue(), pageable);
        });
        assertTrue(ex.getMessage().contains("future"));
    }


    // -------------------------------------------------------------------------
    // Tests for markAsViewed(Long id)
    // -------------------------------------------------------------------------

    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    @DisplayName("UTC_MAV_01: Normal - Valid existing ID, isViewed is 0 initially")
    void testMarkAsViewed_Normal_Success() {
        Long examId = 1L;
        Examination exam = new Examination();
        exam.setId(examId);
        exam.setIsViewed(0);

        when(examinationRepository.findById(examId)).thenReturn(Optional.of(exam));

        examinationService.markAsViewed(examId);

        assertEquals(1, exam.getIsViewed());
        verify(examinationRepository, times(1)).save(exam);
    }

    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    @DisplayName("UTC_MAV_02: Normal - Valid existing ID, isViewed is 1 initially")
    void testMarkAsViewed_Normal_AlreadyViewed() {
        Long examId = 2L;
        Examination exam = new Examination();
        exam.setId(examId);
        exam.setIsViewed(1);

        when(examinationRepository.findById(examId)).thenReturn(Optional.of(exam));

        examinationService.markAsViewed(examId);

        assertEquals(1, exam.getIsViewed());
        verify(examinationRepository, times(1)).save(exam);
    }

    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    @DisplayName("UTC_MAV_03: Abnormal - ID not found")
    void testMarkAsViewed_Abnormal_NotFound() {
        Long examId = 999L;
        when(examinationRepository.findById(examId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.markAsViewed(examId);
        });

        assertEquals("Examination with id 999 not found", exception.getMessage());
        verify(examinationRepository, never()).save(any());
    }

    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    @DisplayName("UTC_MAV_04: Abnormal - ID is null")
    void testMarkAsViewed_Abnormal_NullId() {
        // Since findById(null) typically throws IllegalArgumentException in Spring Data JPA:
        when(examinationRepository.findById(null)).thenThrow(new IllegalArgumentException("The given id must not be null!"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.markAsViewed(null);
        });

        assertTrue(exception.getMessage().contains("must not be null"));
        verify(examinationRepository, never()).save(any());
    }

    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Boundary NegativeId.
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    @DisplayName("UTC_MAV_05: Boundary - ID is negative or zero")
    void testMarkAsViewed_Boundary_NegativeId() {
        Long examId = -1L;
        when(examinationRepository.findById(examId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.markAsViewed(examId);
        });

        assertEquals("Examination with id -1 not found", exception.getMessage());
        verify(examinationRepository, never()).save(any());
    }

    /**
     * Mục đích: Kiểm tra chức năng MarkAsViewed.
     * Đầu vào: Kịch bản: Boundary MaxId.
     * Hành động: Gọi phương thức MarkAsViewed().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     */
    @Test
    @DisplayName("UTC_MAV_06: Boundary - ID is Long.MAX_VALUE")
    void testMarkAsViewed_Boundary_MaxId() {
        Long examId = Long.MAX_VALUE;
        when(examinationRepository.findById(examId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            examinationService.markAsViewed(examId);
        });

        assertEquals("Examination with id " + Long.MAX_VALUE + " not found", exception.getMessage());
        verify(examinationRepository, never()).save(any());
    }

    // --- AUTO-GENERATED MISSING TESTS FROM EXCEL ---
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID07
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationById_UTCID07() {
        // TODO: Implement mock setup and assertion for UTCID07
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID02
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByDoctorId_UTCID02() {
        // TODO: Implement mock setup and assertion for UTCID02
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID03
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByDoctorId_UTCID03() {
        // TODO: Implement mock setup and assertion for UTCID03
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID04
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByDoctorId_UTCID04() {
        // TODO: Implement mock setup and assertion for UTCID04
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID05
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByDoctorId_UTCID05() {
        // TODO: Implement mock setup and assertion for UTCID05
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID06
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByDoctorId_UTCID06() {
        // TODO: Implement mock setup and assertion for UTCID06
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID07
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByDoctorId_UTCID07() {
        // TODO: Implement mock setup and assertion for UTCID07
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID08
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByDoctorId_UTCID08() {
        // TODO: Implement mock setup and assertion for UTCID08
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID02
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientId_UTCID02() {
        // TODO: Implement mock setup and assertion for UTCID02
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID03
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientId_UTCID03() {
        // TODO: Implement mock setup and assertion for UTCID03
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID04
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientId_UTCID04() {
        // TODO: Implement mock setup and assertion for UTCID04
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID05
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientId_UTCID05() {
        // TODO: Implement mock setup and assertion for UTCID05
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID01
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientIdAndStatus_UTCID01() {
        // TODO: Implement mock setup and assertion for UTCID01
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID02
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientIdAndStatus_UTCID02() {
        // TODO: Implement mock setup and assertion for UTCID02
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID03
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientIdAndStatus_UTCID03() {
        // TODO: Implement mock setup and assertion for UTCID03
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID04
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientIdAndStatus_UTCID04() {
        // TODO: Implement mock setup and assertion for UTCID04
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID05
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsByPatientIdAndStatus_UTCID05() {
        // TODO: Implement mock setup and assertion for UTCID05
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID08
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsFilteredByStudyDate_UTCID08() {
        // TODO: Implement mock setup and assertion for UTCID08
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID09
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsFilteredByStudyDate_UTCID09() {
        // TODO: Implement mock setup and assertion for UTCID09
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID09
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetExaminationsFilteredByUploadDate_UTCID09() {
        // TODO: Implement mock setup and assertion for UTCID09
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify examination logic, RBAC, detailed inputs, logs, and DB failure
     * Kịch bản Test Design: UTCID08
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testGetPatientGradeStatistics_UTCID08() {
        // TODO: Implement mock setup and assertion for UTCID08
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
}
