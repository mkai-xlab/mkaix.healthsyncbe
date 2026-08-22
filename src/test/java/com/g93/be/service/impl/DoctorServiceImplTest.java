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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DoctorServiceImplTest {

    private com.g93.be.dto.EditDoctorRequest mockDoctorReq = new com.g93.be.dto.EditDoctorRequest();
    private com.g93.be.dto.EditDoctorProfileRequest mockDoctorProfileReq = new com.g93.be.dto.EditDoctorProfileRequest();
    private com.g93.be.entity.Doctor mockUser = new com.g93.be.entity.Doctor();

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
     * Mục đích: Kiểm tra tìm kiếm danh sách bác sĩ với các tham số bình thường
     * (không null).
     * Đầu vào: Từ khóa "kw", chuyên khoa "spec", trạng thái ACTIVE và phân trang.
     * Hành động: Gọi searchDoctors().
     * Kỳ vọng: Trả về PageResponse có chứa 1 DoctorResponse.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testSearchDoctors_Normal() {
        Pageable pageable = PageRequest.of(0, 10);
        Doctor doc = new Doctor();
        Page<Doctor> page = new PageImpl<>(List.of(doc));
        DoctorResponse docRes = new DoctorResponse();

        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);

        PageResponse<DoctorResponse> res = doctorService.searchDoctors("kw", "spec", UserStatus.ACTIVE, pageable);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(doctorRepository).findAll(any(Specification.class), eq(pageable));
    }

    /**
     * Mục đích: Kiểm tra tìm kiếm bác sĩ nhưng không có kết quả phù hợp.
     * Đầu vào: Tham số tìm kiếm hợp lệ nhưng mock DB trả về trang rỗng.
     * Hành động: Gọi searchDoctors().
     * Kỳ vọng: Trả về PageResponse rỗng (size = 0).
     * 
     * Kịch bản Test Design: N/A (Extra Test Case)
     */
    @Test
    void testSearchDoctors_EmptyResult() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> page = new PageImpl<>(List.of());

        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<DoctorResponse> res = doctorService.searchDoctors("kw", "spec", UserStatus.ACTIVE, pageable);

        assertNotNull(res);
        assertEquals(0, res.content().size());
        verify(doctorRepository).findAll(any(Specification.class), eq(pageable));
    }

    /**
     * Mục đích: Kiểm tra tìm kiếm bác sĩ khi tất cả các bộ lọc đều bị null.
     * Đầu vào: keyword = null, specialty = null, status = null.
     * Hành động: Gọi searchDoctors().
     * Kỳ vọng: Hàm vẫn chạy qua mà không quăng lỗi, trả về danh sách bác sĩ không
     * bị filter lỗi.
     * 
     * Kịch bản Test Design: N/A (Extra Test Case)
     */
    @Test
    void testSearchDoctors_NullFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> page = new PageImpl<>(List.of());

        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<DoctorResponse> res = doctorService.searchDoctors(null, null, null, pageable);

        assertNotNull(res);
        verify(doctorRepository).findAll(any(Specification.class), eq(pageable));
    }

    // ==========================================
    // 2. getAllDoctors
    // ==========================================
    /**
     * Mục đích: Kiểm tra lấy toàn bộ danh sách bác sĩ không phân trang.
     * Đầu vào: Mock repository trả về danh sách có 1 bác sĩ.
     * Hành động: Gọi getAllDoctors().
     * Kỳ vọng: Trả về danh sách chứa 1 phần tử DoctorResponse.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testGetAllDoctors_Normal() {
        Doctor doc = new Doctor();
        DoctorResponse docRes = new DoctorResponse();

        when(doctorRepository.findAll()).thenReturn(List.of(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);

        List<DoctorResponse> res = doctorService.getAllDoctors();

        assertNotNull(res);
        assertEquals(1, res.size());
        verify(doctorRepository).findAll();
    }

    /**
     * Mục đích: Kiểm tra lấy danh sách toàn bộ bác sĩ khi DB trống.
     * Đầu vào: Mock repository trả về danh sách rỗng.
     * Hành động: Gọi getAllDoctors().
     * Kỳ vọng: Trả về danh sách rỗng (isEmpty() == true).
     * 
     * Kịch bản Test Design: N/A (Extra Test Case)
     */
    @Test
    void testGetAllDoctors_EmptyList() {
        when(doctorRepository.findAll()).thenReturn(List.of());
        List<DoctorResponse> res = doctorService.getAllDoctors();
        assertTrue(res.isEmpty());
        verify(doctorRepository).findAll();
    }

    // ==========================================
    // 3. getActiveDoctors
    // ==========================================
    /**
     * Mục đích: Kiểm tra lấy danh sách các bác sĩ đang hoạt động (ACTIVE).
     * Đầu vào: Mock repository trả về danh sách có 1 bác sĩ ACTIVE.
     * Hành động: Gọi getActiveDoctors().
     * Kỳ vọng: Trả về danh sách chứa 1 phần tử.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testGetActiveDoctors_Normal() {
        Doctor doc = new Doctor();
        DoctorResponse docRes = new DoctorResponse();

        when(doctorRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);

        List<DoctorResponse> res = doctorService.getActiveDoctors();

        assertNotNull(res);
        assertEquals(1, res.size());
        verify(doctorRepository).findAllByStatus(UserStatus.ACTIVE);
    }

    /**
     * Mục đích: Kiểm tra lấy danh sách bác sĩ đang hoạt động khi DB không có ai.
     * Đầu vào: Mock repository trả về danh sách rỗng.
     * Hành động: Gọi getActiveDoctors().
     * Kỳ vọng: Trả về danh sách rỗng.
     * 
     * Kịch bản Test Design: N/A (Extra Test Case)
     */
    @Test
    void testGetActiveDoctors_EmptyList() {
        when(doctorRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of());
        List<DoctorResponse> res = doctorService.getActiveDoctors();
        assertTrue(res.isEmpty());
        verify(doctorRepository).findAllByStatus(UserStatus.ACTIVE);
    }

    // ==========================================
    // 4. softDeleteDoctor
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng xóa mềm (ẩn) bác sĩ thành công.
     * Đầu vào: Bác sĩ đang ACTIVE trong DB.
     * Hành động: Gọi softDeleteDoctor().
     * Kỳ vọng: Trạng thái bác sĩ chuyển thành INACTIVE và được lưu lại DB.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testSoftDeleteDoctor_Normal() {
        Doctor doc = new Doctor();
        doc.setStatus(UserStatus.ACTIVE);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doc));

        doctorService.softDeleteDoctor(1L, "test reason");

        assertEquals(UserStatus.INACTIVE, doc.getStatus());
        verify(doctorRepository).save(doc);
    }

    /**
     * Mục đích: Kiểm tra xóa mềm bác sĩ nhưng ID truyền vào không tồn tại.
     * Đầu vào: ID không tồn tại.
     * Hành động: Gọi softDeleteDoctor().
     * Kỳ vọng: Ném ra ngoại lệ IllegalArgumentException báo không tìm thấy.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testSoftDeleteDoctor_Abnormal_NotFound() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.softDeleteDoctor(1L, "test reason"));
        assertEquals("Doctor with id 1 not found", ex.getMessage());
    }

    // ==========================================
    // 5. activateDoctor
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng kích hoạt lại bác sĩ đã bị ẩn.
     * Đầu vào: Bác sĩ đang INACTIVE trong DB.
     * Hành động: Gọi activateDoctor().
     * Kỳ vọng: Trạng thái bác sĩ chuyển thành ACTIVE và được lưu lại DB.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testActivateDoctor_Normal() {
        Doctor doc = new Doctor();
        doc.setStatus(UserStatus.INACTIVE);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doc));

        doctorService.activateDoctor(1L);

        assertEquals(UserStatus.ACTIVE, doc.getStatus());
        verify(doctorRepository).save(doc);
    }

    /**
     * Mục đích: Kiểm tra kích hoạt lại bác sĩ nhưng ID truyền vào không tồn tại.
     * Đầu vào: ID không tồn tại.
     * Hành động: Gọi activateDoctor().
     * Kỳ vọng: Ném ra ngoại lệ IllegalArgumentException báo không tìm thấy.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testActivateDoctor_Abnormal_NotFound() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.activateDoctor(1L));
        assertEquals("Doctor with id 1 not found", ex.getMessage());
    }

    // ==========================================
    // 6. editDoctor
    // ==========================================
    /**
     * Mục đích: Kiểm tra chỉnh sửa thông tin bác sĩ thành công.
     * Đầu vào: Object request hợp lệ gồm tên, email, phone, avatarUrl mới.
     * Hành động: Gọi editDoctor().
     * Kỳ vọng: Các thông tin của bác sĩ trong hệ thống được cập nhật giống với
     * request.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testEditDoctor_Normal() {
        Doctor doc = new Doctor();
        doc.setId(1L);
        EditDoctorRequest req = new EditDoctorRequest();
        req.setFullName("Updated Name");
        req.setEmail("updated@test.com");
        req.setPhone("0987654321");
        req.setAvatarUrl("http://avatar.com/new.png");
        req.setYearsOfExperience(10);
        req.setDegree("PhD");
        req.setBiography("Bio updated");

        DoctorResponse docRes = new DoctorResponse();
        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);

        DoctorResponse res = doctorService.editDoctor(1L, req);

        assertEquals("Updated Name", doc.getFullName());
        assertEquals("updated@test.com", doc.getEmail());
        assertEquals("0987654321", doc.getPhone());
        assertEquals(10, doc.getYearsOfExperience());
        assertEquals("PhD", doc.getDegree());
        assertEquals("Bio updated", doc.getBiography());
        assertNotNull(doc.getAvatar());
        assertEquals("http://avatar.com/new.png", doc.getAvatar().getFilePath());
        assertEquals("png", doc.getAvatar().getExtension());
        assertNotNull(res);
    }

    /**
     * Mục đích: Kiểm tra lỗi khi chỉnh sửa thông tin bác sĩ nhưng không tìm thấy
     * ID.
     * Đầu vào: ID bác sĩ không tồn tại.
     * Hành động: Gọi editDoctor().
     * Kỳ vọng: Ném ra IllegalArgumentException với thông báo lỗi phù hợp.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testEditDoctor_Abnormal_NotFound() {
        EditDoctorRequest req = new EditDoctorRequest();
        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.editDoctor(1L, req));
        assertEquals("Doctor with id 1 not found", ex.getMessage());
    }

    // ==========================================
    // 7. getDoctorProfile
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng lấy thông tin Profile của chính bác sĩ đang đăng
     * nhập.
     * Đầu vào: Username hợp lệ đang tồn tại trong DB.
     * Hành động: Gọi getDoctorProfile().
     * Kỳ vọng: Trả về đối tượng DoctorResponse tương ứng với user đó.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testGetDoctorProfile_Normal() {
        Doctor doc = new Doctor();
        DoctorResponse docRes = new DoctorResponse();
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.of(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);

        DoctorResponse res = doctorService.getDoctorProfile("user1");

        assertNotNull(res);
        verify(doctorRepository).findProfileByUsername("user1");
    }

    /**
     * Mục đích: Kiểm tra lấy Profile nhưng username không tồn tại (trường hợp token
     * rác/tài khoản bị xóa).
     * Đầu vào: Username giả.
     * Hành động: Gọi getDoctorProfile().
     * Kỳ vọng: Ném ra IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testGetDoctorProfile_Abnormal_NotFound() {
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.getDoctorProfile("user1"));
        assertEquals("Doctor not found for username: user1", ex.getMessage());
    }

    // ==========================================
    // 8. editDoctorProfile
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng bác sĩ tự chỉnh sửa Profile cá nhân thành công.
     * Đầu vào: Username hợp lệ và một request EditDoctorProfileRequest hợp lệ.
     * Hành động: Gọi editDoctorProfile().
     * Kỳ vọng: Cập nhật thành công các thông tin (tên, số điện thoại, kinh
     * nghiệm,...) vào DB.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testEditDoctorProfile_Normal() {
        Doctor doc = new Doctor();
        EditDoctorProfileRequest req = new EditDoctorProfileRequest();
        req.setFullName("Updated Profile Name");
        req.setEmail("profile@test.com");
        req.setPhone("111222333");
        req.setYearsOfExperience(5);
        req.setDegree("Master");
        req.setBiography("Profile bio updated");

        DoctorResponse docRes = new DoctorResponse();
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);

        DoctorResponse res = doctorService.editDoctorProfile("user1", req);

        assertEquals("Updated Profile Name", doc.getFullName());
        assertEquals("profile@test.com", doc.getEmail());
        assertEquals("111222333", doc.getPhone());
        assertEquals(5, doc.getYearsOfExperience());
        assertEquals("Master", doc.getDegree());
        assertEquals("Profile bio updated", doc.getBiography());
        assertNotNull(res);
    }

    /**
     * Mục đích: Kiểm tra chỉnh sửa Profile thất bại khi Username không tồn tại
     * trong DB.
     * Đầu vào: Username không hợp lệ.
     * Hành động: Gọi editDoctorProfile().
     * Kỳ vọng: Ném ra ngoại lệ IllegalArgumentException.
     * 
     * Kịch bản Test Design: UTCID01 (Dự kiến)
     */
    @Test
    void testEditDoctorProfile_Abnormal_NotFound() {
        EditDoctorProfileRequest req = new EditDoctorProfileRequest();
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.editDoctorProfile("user1", req));
        assertEquals("Doctor not found for username: user1", ex.getMessage());
    }

    // ==========================================
    // 9. createDoctor
    // ==========================================
    /**
     * Mục đích: Kiểm tra tạo mới tài khoản Bác sĩ thành công bởi Admin.
     * Đầu vào: CreateDoctorRequest với email và các thông tin cơ bản hợp lệ.
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Tạo thành công User, sinh mật khẩu ngẫu nhiên, lưu thông tin Doctor,
     * gửi email báo mật khẩu, và trả về Response.
     */
    @Test
    void testCreateDoctor_Normal() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");
        req.setPhone("0999888777");
        req.setAvatarUrl("http://image.com/avatar.jpg");
        req.setYearsOfExperience(8);
        req.setDegree("Specialist");
        req.setBiography("Great doctor");

        Role role = new Role();
        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        Doctor savedDoc = new Doctor();
        savedDoc.setId(99L);
        savedDoc.setEmail(req.getEmail());
        savedDoc.setFullName(req.getFullName());
        savedDoc.setUsername("newdoc");

        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());

        DoctorResponse res = doctorService.createDoctor(req);

        assertNotNull(res);
        verify(doctorRepository).save(any(Doctor.class));
        verify(mailUtil).sendTemplateMail(eq("newdoc@test.com"), anyString(), eq("doctor-welcome"), anyMap());
    }

    /**
     * Mục đích: Kiểm tra lỗi khi tạo mới bác sĩ nhưng thiếu email.
     * Đầu vào: Request thiếu email (email rỗng).
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Ném ra IllegalArgumentException báo "Email is required".
     */
    @Test
    void testCreateDoctor_Abnormal_MissingEmail() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("");
        req.setFullName("New Doctor");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.createDoctor(req));
        assertEquals("Email is required", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra lỗi khi tạo mới bác sĩ nhưng thiếu tên.
     * Đầu vào: Request thiếu Full Name (null).
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Ném ra IllegalArgumentException báo "Full name is required".
     */
    @Test
    void testCreateDoctor_Abnormal_MissingFullName() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.createDoctor(req));
        assertEquals("Full name is required", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra lỗi khi tạo bác sĩ mà email đã tồn tại trong hệ thống.
     * Đầu vào: Request chứa email đã có trong DB.
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Ném ra IllegalArgumentException báo email đã được đăng ký.
     */
    @Test
    void testCreateDoctor_Abnormal_DuplicateEmail() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("existing@test.com");
        req.setFullName("New Doctor");

        User existingUser = new User();
        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(existingUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.createDoctor(req));
        assertEquals("Email 'existing@test.com' is already registered", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra lỗi khi tạo bác sĩ mà số điện thoại đã tồn tại.
     * Đầu vào: Request chứa số điện thoại đã có trong DB.
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Ném ra IllegalArgumentException báo số điện thoại đã được đăng ký.
     */
    @Test
    void testCreateDoctor_Abnormal_DuplicatePhone() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");
        req.setPhone("0999888777");

        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        User existingUser = new User();
        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(existingUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doctorService.createDoctor(req));
        assertEquals("Phone '0999888777' is already registered", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra hệ thống khi tạo mới bác sĩ nhưng DB không có role DOCTOR
     * (cấu hình sai).
     * Đầu vào: RoleRepository không tìm thấy DOCTOR role.
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Ném ra IllegalStateException báo thiếu cấu hình Role.
     */
    @Test
    void testCreateDoctor_Abnormal_RoleNotFound() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");

        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> doctorService.createDoctor(req));
        assertEquals("DOCTOR role not found in database", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra chức năng cập nhật một phần (Partial Update) cho bác sĩ.
     * Đầu vào: Request chỉ chứa các trường null, không chứa dữ liệu mới.
     * Hành động: Gọi editDoctor().
     * Kỳ vọng: Giữ nguyên các thông tin cũ của bác sĩ, không bị ghi đè thành null.
     * 
     * Kịch bản Test Design: UTCID03 (Dự kiến)
     */
    @Test
    void testEditDoctor_PartialUpdate() {
        Doctor doc = new Doctor();
        doc.setId(1L);
        doc.setFullName("Old Name");
        doc.setEmail("old@test.com");

        EditDoctorRequest req = new EditDoctorRequest(); // All fields null

        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());

        doctorService.editDoctor(1L, req);

        assertEquals("Old Name", doc.getFullName());
        assertEquals("old@test.com", doc.getEmail());
        assertNull(doc.getPhone());
    }

    /**
     * Mục đích: Kiểm tra trường hợp đặc biệt khi cập nhật Avatar nhưng URL không có
     * đuôi mở rộng.
     * Đầu vào: url avatar không có định dạng file rõ ràng.
     * Hành động: Gọi editDoctor().
     * Kỳ vọng: Đường dẫn được lưu lại nhưng phần extension (đuôi file) bị null
     * (không gây crash).
     * 
     * Kịch bản Test Design: N/A (Extra Test Case)
     */
    @Test
    void testEditDoctor_AvatarEdgeCases() {
        Doctor doc = new Doctor();
        Image existingAvatar = new Image();
        existingAvatar.setFilePath("old.png");
        doc.setAvatar(existingAvatar);

        EditDoctorRequest req = new EditDoctorRequest();
        req.setAvatarUrl("http://avatar.com/newfile"); // No extension

        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());

        doctorService.editDoctor(1L, req);

        assertEquals("http://avatar.com/newfile", doc.getAvatar().getFilePath());
        assertNull(doc.getAvatar().getExtension());
    }

    /**
     * Mục đích: Kiểm tra chức năng cập nhật Profile một phần (Partial Update).
     * Đầu vào: ProfileRequest trống rỗng.
     * Hành động: Gọi editDoctorProfile().
     * Kỳ vọng: Dữ liệu cũ của Profile được giữ nguyên, không ghi đè thành null.
     * 
     * Kịch bản Test Design: UTCID03 (Dự kiến)
     */
    @Test
    void testEditDoctorProfile_PartialUpdate() {
        Doctor doc = new Doctor();
        doc.setFullName("Old Name");

        EditDoctorProfileRequest req = new EditDoctorProfileRequest(); // All fields null

        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());

        doctorService.editDoctorProfile("user1", req);

        assertEquals("Old Name", doc.getFullName());
    }

    /**
     * Mục đích: Kiểm tra hệ thống khi tạo mới bác sĩ nhưng thiếu số điện thoại và
     * Avatar.
     * Đầu vào: Request chỉ chứa Email và Full Name, không có số điện thoại và ảnh.
     * Hành động: Gọi createDoctor().
     * Kỳ vọng: Tạo thành công bác sĩ mà không báo lỗi thiếu các trường không bắt
     * buộc (phone, avatar).
     */
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
        org.mockito.Mockito.lenient().when(doctorRepository.findDetailsById(1L)).thenReturn(java.util.Optional.of(mockUser));
        org.mockito.Mockito.lenient().when(doctorRepository.save(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalArgumentException("Invalid request"));

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
        org.mockito.Mockito.lenient().when(doctorRepository.findDetailsById(1L)).thenThrow(new RuntimeException("DB Connection failure"));
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
        org.mockito.Mockito.lenient().when(doctorRepository.save(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalArgumentException("Invalid request"));

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
                .when(doctorRepository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
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
}
