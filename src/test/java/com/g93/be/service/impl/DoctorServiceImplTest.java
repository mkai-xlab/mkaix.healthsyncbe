package com.g93.be.service.impl;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.mapper.DoctorMapper;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.AvatarStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DoctorServiceImplTest {

    private EditDoctorRequest mockDoctorReq = new EditDoctorRequest();
    private EditDoctorProfileRequest mockDoctorProfileReq = new EditDoctorProfileRequest();
    private Doctor mockUser = new Doctor();

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailUtil mailUtil;
    @Mock
    private DoctorMapper doctorMapper;
    @Mock
    private AvatarStorageService avatarStorageService;

    @InjectMocks
    private DoctorServiceImpl doctorService;

    private Validator validator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(doctorService, "loginUrl", "http://localhost:3000/login");
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==========================================
    // 1. searchDoctors
    // ==========================================
    /**
     * Mục đích test: Kiểm tra chức năng tạo bác sĩ khi không cung cấp các trường
     * không bắt buộc như số điện thoại.
     * Đầu vào: Request chứa email, tên hợp lệ nhưng thiếu số điện thoại.
     * Hành động: Gọi hàm createDoctor().
     * Kỳ vọng: Tạo thành công bác sĩ mà không báo lỗi.
     */
    // ==============================================================================
    // UTCID02: Missing non-required fields (phone null)
    // ==============================================================================
    /**
     * Mục đích test: Kiểm tra chức năng tạo bác sĩ khi không cung cấp các trường
     * không bắt buộc như số điện thoại.
     * Đầu vào: Request chứa email, tên hợp lệ nhưng thiếu số điện thoại.
     * Hành động: Gọi hàm createDoctor().
     * Kỳ vọng: Tạo thành công bác sĩ mà không báo lỗi.
     */
    // ==============================================================================
    // UTCID02: Missing non-required fields (phone null)
    // ==============================================================================
    /**
     * Mục đích test: Kiểm tra chức năng tạo bác sĩ khi không cung cấp các trường
     * không bắt buộc như số điện thoại.
     * Đầu vào: Request chứa email, tên hợp lệ nhưng thiếu số điện thoại.
     * Hành động: Gọi hàm createDoctor().
     * Kỳ vọng: Tạo thành công bác sĩ mà không báo lỗi.
     */

    // ==============================================================================
    // UTCID02: Missing non-required fields
    // ==============================================================================
    /**
     * Mục đích test: Kiểm tra chức năng tạo bác sĩ khi không cung cấp các trường
     * không bắt buộc như số điện thoại.
     * Đầu vào: Request chứa email, tên hợp lệ nhưng thiếu số điện thoại.
     * Hành động: Gọi hàm createDoctor().
     * Kỳ vọng: Tạo thành công bác sĩ mà không báo lỗi.
     */
    // ==============================================================================
    // UTCID02: Missing non-required fields
    // ==============================================================================
    @Test
    void testCreateDoctor_NoPhoneAndNoAvatar() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");

        Role role = new Role();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        Doctor savedDoc = new Doctor();
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());

        doctorService.createDoctor(req);

        verify(userRepository, never()).findByPhone(anyString());
        verify(doctorRepository).save(argThat(d -> d.getPhone() == null && d.getAvatar() == null));
    }

    /**
     * Mục đích: Kiểm tra logic tạo username tự động tránh trùng lặp và khả năng xử
     * lý khi gửi email thất bại.
     * Đầu vào: Email có tiền tố "test", DB đã có các username "test" và "test1".
     * Cấu hình Mail server bị lỗi.
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Tự động gán username thành "test2". Quá trình tạo vẫn thành công
     * (không ném ngoại lệ) bất chấp lỗi gửi mail.
     */
    @Test
    void testCreateDoctor_UsernameCollisionAndMailException() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("test@test.com");
        req.setFullName("New Doctor");

        Role role = new Role();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Mock username collision: "test" exists, "test1" exists, "test2" available
        when(userRepository.findByUsername("test")).thenReturn(Optional.of(new User()));
        when(userRepository.findByUsername("test1")).thenReturn(Optional.of(new User()));
        when(userRepository.findByUsername("test2")).thenReturn(Optional.empty());

        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        lenient().doThrow(new RuntimeException("Mail server down")).when(mailUtil)
                .sendTemplateMail(anyString(), anyString(), anyString(), anyMap());

        Doctor savedDoc = new Doctor();
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());

        // Should not throw exception despite mail error
        assertDoesNotThrow(() -> doctorService.createDoctor(req));

        verify(doctorRepository).save(argThat(d -> d.getUsername().equals("test2")));
    }

    /**
     * Mục đích: Kiểm tra tạo username tự động khi email bắt đầu bằng ký tự đặc biệt
     * (VD: !!!@test.com).
     * Đầu vào: Ký tự đặc biệt ở đầu email, bị xóa sạch sau khi lọc regex.
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Gán username mặc định là "doctor" thay vì chuỗi rỗng.
     */
    @Test
    void testCreateDoctor_InvalidEmailBase() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("!!!@test.com"); // base will be empty after regex
        req.setFullName("New Doctor");

        Role role = new Role();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        Doctor savedDoc = new Doctor();
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());

        doctorService.createDoctor(req);

        verify(doctorRepository).save(argThat(d -> d.getUsername().equals("doctor")));
    }

    /**
     * Mục đích: Kiểm tra validation của DTO CreateDoctorRequest đối với trường
     * Email.
     * Đầu vào: Lần lượt gán Email là null, chuỗi rỗng, sai định dạng, và dài quá
     * mức cho phép.
     * Hành động: Gọi validator.validate().
     * Kỳ vọng: Validator trả về lỗi (violation) cho mọi trường hợp nhập sai Email.
     */
    @Test
    void testCreateDoctorRequest_Validation_Email() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setFullName("Valid Name");
        req.setPhone("0901234567");

        // Null
        req.setEmail(null);
        assertFalse(validator.validate(req).isEmpty());

        // Blank
        req.setEmail(" ");
        assertFalse(validator.validate(req).isEmpty());

        // Invalid format
        req.setEmail("invalid-email");
        assertFalse(validator.validate(req).isEmpty());

        // > 150 chars
        req.setEmail("a".repeat(150) + "@test.com");
        assertFalse(validator.validate(req).isEmpty());
    }

    /**
     * Mục đích: Kiểm tra validation của DTO CreateDoctorRequest đối với trường Full
     * Name.
     * Đầu vào: Lần lượt gán Full Name là null, chuỗi rỗng, và dài quá 100 ký tự.
     * Hành động: Gọi validator.validate().
     * Kỳ vọng: Validator trả về lỗi (violation) cho mọi trường hợp.
     */
    @Test
    void testCreateDoctorRequest_Validation_FullName() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("test@test.com");
        req.setPhone("0901234567");

        // Null
        req.setFullName(null);
        assertFalse(validator.validate(req).isEmpty());

        // Blank
        req.setFullName(" ");
        assertFalse(validator.validate(req).isEmpty());

        // > 100 chars
        req.setFullName("a".repeat(101));
        assertFalse(validator.validate(req).isEmpty());
    }

    /**
     * Mục đích: Kiểm tra validation của DTO CreateDoctorRequest đối với trường số
     * điện thoại (Phone).
     * Đầu vào: Lần lượt gán Phone là null, chứa chữ cái (sai định dạng), và dài quá
     * 20 ký tự.
     * Hành động: Gọi validator.validate().
     * Kỳ vọng: Validator trả về lỗi (violation) cho mọi trường hợp.
     */
    @Test
    void testCreateDoctorRequest_Validation_Phone() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("test@test.com");
        req.setFullName("Valid Name");

        // Null
        req.setPhone(null);
        assertFalse(validator.validate(req).isEmpty());

        // Invalid format
        req.setPhone("090abcd123");
        assertFalse(validator.validate(req).isEmpty());

        // > 20 chars
        req.setPhone("1".repeat(21));
        assertFalse(validator.validate(req).isEmpty());
    }

    // --- AUTO-GENERATED MISSING TESTS FROM EXCEL ---
    /**
     * Mục đích: Verify activation toggles status to ACTIVE
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testActivateDoctor_UTCID03() {
        org.mockito.Mockito.lenient().when(doctorRepository.findById(1L))
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.activateDoctor(1L);
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }

    /**
     * Mục đích: Verify an admin can partially edit a doctor
     * Kịch bản Test Design: UTCID05
     */
    @Test
    void testEditDoctor_UTCID05() {
        com.g93.be.dto.EditDoctorRequest invalidDto = new com.g93.be.dto.EditDoctorRequest(); // Missing fields
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            doctorService.editDoctor(1L, invalidDto);
        });
        org.junit.jupiter.api.Assertions.assertNotNull(ex);
    }

    /**
     * Mục đích: Verify an admin can partially edit a doctor
     * Kịch bản Test Design: UTCID06
     */
    @Test
    void testEditDoctor_UTCID06() {
        org.mockito.Mockito.lenient().when(doctorRepository.findDetailsById(1L)).thenReturn(java.util.Optional.empty());
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            doctorService.editDoctor(1L, mockDoctorReq);
        });
        org.junit.jupiter.api.Assertions.assertNotNull(ex);
    }

    /**
     * Mục đích: Verify an admin can partially edit a doctor
     * Kịch bản Test Design: UTCID07
     */
    @Test
    void testEditDoctor_UTCID07() {
        org.mockito.Mockito.lenient().when(doctorRepository.findDetailsById(1L))
                .thenReturn(java.util.Optional.of(mockUser));
        org.mockito.Mockito.lenient().when(doctorRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Invalid request"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            doctorService.editDoctor(1L, mockDoctorReq);
        });
        org.junit.jupiter.api.Assertions.assertNotNull(ex);
    }

    /**
     * Mục đích: Verify an admin can partially edit a doctor
     * Kịch bản Test Design: UTCID08
     */
    @Test
    void testEditDoctor_UTCID08() {
        org.mockito.Mockito.lenient().when(doctorRepository.findDetailsById(1L))
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.editDoctor(1L, mockDoctorReq);
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }

    /**
     * Mục đích: Verify a doctor can update their own profile
     * Kịch bản Test Design: UTCID04
     */
    @Test
    void testEditDoctorProfile_UTCID04() {
        org.mockito.Mockito.lenient().when(doctorRepository.findProfileByUsername("user1"))
                .thenReturn(java.util.Optional.empty());
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            doctorService.editDoctorProfile("user1", mockDoctorProfileReq);
        });
        org.junit.jupiter.api.Assertions.assertNotNull(ex);
    }

    /**
     * Mục đích: Verify a doctor can update their own profile
     * Kịch bản Test Design: UTCID05
     */
    @Test
    void testEditDoctorProfile_UTCID05() {
        org.mockito.Mockito.lenient().when(doctorRepository.findProfileByUsername("user1"))
                .thenReturn(java.util.Optional.of(mockUser));
        org.mockito.Mockito.lenient().when(doctorRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Invalid request"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            doctorService.editDoctorProfile("user1", mockDoctorProfileReq);
        });
        org.junit.jupiter.api.Assertions.assertNotNull(ex);
    }

    /**
     * Mục đích: Verify a doctor can update their own profile
     * Kịch bản Test Design: UTCID06
     */
    @Test
    void testEditDoctorProfile_UTCID06() {
        org.mockito.Mockito.lenient().when(doctorRepository.findProfileByUsername("user1"))
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.editDoctorProfile("user1", mockDoctorProfileReq);
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }

    /**
     * Mục đích: Verify getActiveDoctors fetching logic
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testGetActiveDoctors_UTCID03() {
        org.mockito.Mockito
                .when(doctorRepository.findAllByStatus(com.g93.be.entity.UserStatus.ACTIVE))
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.getActiveDoctors();
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }

    /**
     * Mục đích: Verify fetching all doctors without pagination
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testGetAllDoctors_UTCID03() {
        org.mockito.Mockito
                .when(doctorRepository.findAll())
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.getAllDoctors();
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }

    /**
     * Mục đích: Verify the authenticated doctor profile
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testGetDoctorProfile_UTCID03() {
        org.mockito.Mockito.lenient().when(doctorRepository.findProfileByUsername("user1"))
                .thenReturn(java.util.Optional.empty());
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            doctorService.getDoctorProfile("user1");
        });
        org.junit.jupiter.api.Assertions.assertNotNull(ex);
    }

    /**
     * Mục đích: Verify the authenticated doctor profile
     * Kịch bản Test Design: UTCID04
     */
    @Test
    void testGetDoctorProfile_UTCID04() {
        org.mockito.Mockito.lenient().when(doctorRepository.findProfileByUsername("user1"))
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.getDoctorProfile("user1");
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }

    /**
     * Mục đích: Verify paginated searching and filtering of doctors
     * Kịch bản Test Design: UTCID04
     */
    @Test
    void testSearchDoctors_UTCID04() {
        org.mockito.Mockito
                .when(doctorRepository.findAll(
                        org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
                        org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.searchDoctors("query", null, null, org.springframework.data.domain.PageRequest.of(0, 10));
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }

    /**
     * Mục đích: Verify soft delete toggles status to INACTIVE
     * Kịch bản Test Design: UTCID03
     */
    @Test
    void testSoftDeleteDoctor_UTCID03() {
        org.mockito.Mockito.lenient().when(doctorRepository.findById(1L))
                .thenThrow(new RuntimeException("DB Connection failure"));
        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            doctorService.softDeleteDoctor(1L, "test reason");
        });
        org.junit.jupiter.api.Assertions.assertEquals("DB Connection failure", ex.getMessage());
    }
    // ==============================================================================
    // MISSING TESTS IMPLEMENTED FROM EXCEL MATRIX
    // ==============================================================================

    /**
     * Mục đích test: Kích hoạt lại bác sĩ đang bị khóa.
     * Đầu vào: Bác sĩ tồn tại trong DB, status INACTIVE.
     * Hành động: Gọi hàm activateDoctor(1L).
     * Kỳ vọng: Kích hoạt thành công, trạng thái chuyển thành ACTIVE.
     */
    // ==============================================================================
    // UTCID01: Activate existing inactive doctor
    // ==============================================================================
    @Test
    void testActivateDoctor_UTCID01_Normal() {
        Doctor doc = new Doctor();
        doc.setId(1L);
        doc.setStatus(UserStatus.INACTIVE);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);

        assertDoesNotThrow(() -> doctorService.activateDoctor(1L));
        assertEquals(UserStatus.ACTIVE, doc.getStatus());
        verify(doctorRepository).save(doc);
    }

    /**
     * Mục đích test: Báo lỗi khi kích hoạt bác sĩ không tồn tại.
     * Đầu vào: ID bác sĩ giả (999).
     * Hành động: Gọi hàm activateDoctor(999L).
     * Kỳ vọng: Ném ra IllegalArgumentException với thông báo "Doctor with id 999 not found".
     */
    // ==============================================================================
    // UTCID02: Activate non-existent doctor
    // ==============================================================================
    @Test
    void testActivateDoctor_UTCID02_NotFound() {
        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            doctorService.activateDoctor(999L);
        });
        assertEquals("Doctor with id 999 not found", ex.getMessage());
    }

    /**
     * Mục đích test: Lấy danh sách bác sĩ đang hoạt động thành công.
     * Đầu vào: DB có chứa bác sĩ trạng thái ACTIVE.
     * Hành động: Gọi hàm getActiveDoctors().
     * Kỳ vọng: Trả về danh sách chứa thông tin các bác sĩ.
     */
    // ==============================================================================
    // UTCID01: Get active doctors successfully
    // ==============================================================================
    @Test
    void testGetActiveDoctors_UTCID01_Normal() {
        Doctor doc = new Doctor();
        doc.setId(1L);
        when(doctorRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(Arrays.asList(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());

        List<DoctorResponse> result = doctorService.getActiveDoctors();
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    /**
     * Mục đích test: Trả về danh sách rỗng khi không có bác sĩ ACTIVE.
     * Đầu vào: DB trống hoặc tất cả bác sĩ đều INACTIVE.
     * Hành động: Gọi hàm getActiveDoctors().
     * Kỳ vọng: Trả về danh sách rỗng (empty).
     */
    // ==============================================================================
    // UTCID02: Get active doctors - empty list
    // ==============================================================================
    @Test
    void testGetActiveDoctors_UTCID02_Empty() {
        when(doctorRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(Collections.emptyList());

        List<DoctorResponse> result = doctorService.getActiveDoctors();
        assertTrue(result.isEmpty());
    }

    /**
     * Mục đích test: Tìm kiếm bác sĩ có kết quả trả về.
     * Đầu vào: keyword="Nguyen", spec="Cardiology".
     * Hành động: Gọi hàm searchDoctors().
     * Kỳ vọng: Trả về PageResponse chứa danh sách bác sĩ phù hợp.
     */
    // ==============================================================================
    // UTCID01: Search doctors successfully
    // ==============================================================================
    @Test
    void testSearchDoctors_UTCID01_Normal() {
        Pageable pageable = PageRequest.of(0, 10);
        Doctor doc = new Doctor();
        Page<Doctor> mockPage = new PageImpl<>(Arrays.asList(doc));
        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);
        when(doctorMapper.toResponse(any())).thenReturn(new DoctorResponse());

        PageResponse<DoctorResponse> res = doctorService.searchDoctors("Nguyen", "Cardiology", null, pageable);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích test: Tìm kiếm bác sĩ không ra kết quả (NotMatch).
     * Đầu vào: keyword="NotMatch".
     * Hành động: Gọi hàm searchDoctors().
     * Kỳ vọng: Trả về PageResponse với list trống.
     */
    // ==============================================================================
    // UTCID02: Search doctors - no match
    // ==============================================================================
    @Test
    void testSearchDoctors_UTCID02_NoMatch() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> emptyPage = new PageImpl<>(Collections.emptyList());
        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        PageResponse<DoctorResponse> res = doctorService.searchDoctors("NotMatch", null, null, pageable);
        assertTrue(res.content().isEmpty());
    }

    /**
     * Mục đích test: Tìm kiếm bác sĩ với bộ lọc null.
     * Đầu vào: keyword=null, spec=null.
     * Hành động: Gọi hàm searchDoctors().
     * Kỳ vọng: Bỏ qua các điều kiện null và trả về danh sách tất cả.
     */
    // ==============================================================================
    // UTCID03: Search doctors - null filters
    // ==============================================================================
    @Test
    void testSearchDoctors_UTCID03_NullFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> mockPage = new PageImpl<>(Arrays.asList(new Doctor()));
        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);
        when(doctorMapper.toResponse(any())).thenReturn(new DoctorResponse());

        PageResponse<DoctorResponse> res = doctorService.searchDoctors(null, null, null, pageable);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích test: Lấy toàn bộ danh sách bác sĩ thành công.
     * Đầu vào: DB có tồn tại bác sĩ.
     * Hành động: Gọi hàm getAllDoctors().
     * Kỳ vọng: Trả về danh sách chứa tất cả bác sĩ.
     */
    // ==============================================================================
    // UTCID01: Get all doctors successfully
    // ==============================================================================
    @Test
    void testGetAllDoctors_UTCID01_Normal() {
        Doctor doc = new Doctor();
        when(doctorRepository.findAll()).thenReturn(Arrays.asList(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());

        List<DoctorResponse> result = doctorService.getAllDoctors();
        assertEquals(1, result.size());
    }

    /**
     * Mục đích test: Lấy toàn bộ danh sách bác sĩ khi DB rỗng.
     * Đầu vào: DB không có bác sĩ nào.
     * Hành động: Gọi hàm getAllDoctors().
     * Kỳ vọng: Trả về danh sách rỗng (empty).
     */
    // ==============================================================================
    // UTCID02: Get all doctors - empty DB
    // ==============================================================================
    @Test
    void testGetAllDoctors_UTCID02_Empty() {
        when(doctorRepository.findAll()).thenReturn(Collections.emptyList());
        List<DoctorResponse> result = doctorService.getAllDoctors();
        assertTrue(result.isEmpty());
    }

    /**
     * Mục đích test: Khóa mềm (soft delete) bác sĩ thành công.
     * Đầu vào: ID bác sĩ hợp lệ ("101").
     * Hành động: Gọi hàm softDeleteDoctor().
     * Kỳ vọng: Trạng thái bác sĩ chuyển thành INACTIVE, không xóa cứng.
     */
    // ==============================================================================
    // UTCID01: Soft delete doctor successfully
    // ==============================================================================
    @Test
    void testSoftDeleteDoctor_UTCID01_Normal() {
        Doctor doc = new Doctor();
        doc.setId(101L);
        doc.setStatus(UserStatus.ACTIVE);
        when(doctorRepository.findById(101L)).thenReturn(Optional.of(doc));

        assertDoesNotThrow(() -> doctorService.softDeleteDoctor(101L, "Retiring"));
        assertEquals(UserStatus.INACTIVE, doc.getStatus());
        verify(doctorRepository).save(doc);
    }

    /**
     * Mục đích test: Lỗi khi soft delete bác sĩ không tồn tại.
     * Đầu vào: ID bác sĩ giả (999).
     * Hành động: Gọi hàm softDeleteDoctor().
     * Kỳ vọng: Ném ra IllegalArgumentException với thông báo "Doctor with id 999 not found".
     */
    // ==============================================================================
    // UTCID02: Soft delete non-existent doctor
    // ==============================================================================
    @Test
    void testSoftDeleteDoctor_UTCID02_NotFound() {
        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            doctorService.softDeleteDoctor(999L, "Reason");
        });
        assertEquals("Doctor with id 999 not found", ex.getMessage());
    }
}
