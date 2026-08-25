# Hướng Dẫn Code RBAC (Role-Based Access Control)

## 0. Giải Thích Tiếng Việt: Đọc Phần Này Trước

### 0.1 RBAC ở đây gồm những khối nào?

4 bảng dữ liệu tạo thành mô hình phân quyền động (không hard-code role trong code):

```
Role (vd: ADMIN, DOCTOR, HEAD_OF_DEPARTMENT)
  │
  │  role_permissions (bảng nối N-N)
  │
  ▼
Permission (vd: GENERATE_PDF_REPORT, VIEW_PATIENT_DETAIL)
  │
  │  Permission.feature (N-1)          Permission.requiresPermission (tự tham chiếu, N-1)
  ▼                                     ▼
Feature (nhóm hiển thị, vd "Reporting & Export")   Permission cha (permission phụ thuộc)
```

Không có bảng nào chứa "role → được gọi API nào" trực tiếp - việc đó nằm ở tầng code
(`@PreAuthorize` trên từng method). 4 bảng trên chỉ trả lời "role này có Permission gì", còn
"Permission này bảo vệ API nào" là do dev tự gắn `@PreAuthorize("hasAuthority('CODE')")` lên
đúng method - **không có cơ chế tự động nào ép Permission phải khớp với API**, đây là điều cần
nhớ khi thêm endpoint mới (xem 0.6).

### 0.2 Vì sao Permission có `requiresPermission` (tự tham chiếu)?

```java
// entity/Permission.java:39-42
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "requires_permission_id")
@OnDelete(action = OnDeleteAction.CASCADE)      // xoá permission cha -> permission con bị xoá theo, ở tầng DB
private Permission requiresPermission;
```

Đây là quan hệ **phân cấp hiển thị trên UI quản lý phân quyền** (màn hình cây permission,
`GET /permissions/tree`), không phải ràng buộc runtime kiểu "phải có permission cha mới check
được permission con". Ví dụ thật trong seed data (`DataInitializer`):

```java
Permission pPat02 = new Permission(..., "VIEW_PATIENT_DETAIL", ..., fPatient, pPat01);
//                                                                            ^^^^^ requiresPermission = pPat01 (READ_PATIENT_LIST)
```

nghĩa là trên UI, "Xem chi tiết bệnh nhân" được vẽ lồng dưới "Xem danh sách bệnh nhân" cho dễ
hiểu, nhưng ở tầng `@PreAuthorize`, 2 permission này được kiểm tra **độc lập nhau** - có
`VIEW_PATIENT_DETAIL` không tự động có `READ_PATIENT_LIST`.

### 0.3 Permission "clinical" vs "system-admin" - phân tách cứng cho ADMIN

`PermissionCatalog.java` định nghĩa 2 tập hợp mã Permission cố định (hằng số Java, không phải
dữ liệu trong DB):

```java
CLINICAL_PERMISSION_CODES = {READ_PATIENT_LIST, CREATE_PATIENT_EXAM, VIEW_PATIENT_DETAIL,
    VIEW_IMAGE_LIST, UPLOAD_DICOM_IMAGE, TRIGGER_AI_ANALYSIS, VIEW_AI_RESULT,
    COMPARE_XAI_SIDE_BY_SIDE, VIEW_ANALYTIC_HISTORY, VIEW_PENDING_DIAGNOSIS,
    ADD_CLINICAL_COMMENT, OVERRIDE_AI_GRADE, CONFIRM_CONCLUSION, GENERATE_PDF_REPORT,
    EXPORT_DOWNLOAD_PDF, VIEW_DOCTOR_DASHBOARD}                       // 16 permission

ADMIN_DEFAULT_PERMISSION_CODES = {READ_OWN_PROFILE, REQUEST_PASSWORD_RESET, VIEW_USER_LIST,
    MANAGE_USER_ROLE, VIEW_ADMIN_DASHBOARD, GENERATE_OPERATIONAL_REP, USE_AI_CHAT,
    MANAGE_MEDICAL_KNOWLEDGE}                                          // 8 permission, KHÔNG giao nhau với tập trên
```

Chủ đích thiết kế: **ADMIN chỉ quản trị hệ thống (tài khoản, phân quyền, thống kê vận hành),
không được đọc dữ liệu lâm sàng của bệnh nhân** - tách bạch vai trò quản trị khỏi vai trò khám
chữa bệnh. Quy tắc này được thực thi ở **3 lớp độc lập**, lớp nào cũng tự đứng vững:

1. **Lúc gán quyền qua UI** - `PermissionServiceImpl.updateRolePermissions()` ném lỗi ngay nếu
   admin cố gán permission "clinical" cho role ADMIN.
2. **Lúc app khởi động** - `DataInitializer.synchronizeAdminPermissions()` quét lại toàn bộ
   `role_permissions` của ADMIN mỗi lần boot, tự xoá permission "clinical" nào lỡ lọt vào (dù bằng
   cách nào: thao tác tay trong DB, migration cũ, bug...), rồi bù lại permission mặc định nào
   đang thiếu.
3. **Lúc gọi API nghiệp vụ lâm sàng qua route business-data của chatbot** -
   `BusinessDataQueryService.execute()` chặn ADMIN trước khi chạy SQL nếu intent thuộc nhóm
   clinical (xem `docs/rag-chatbox-code-guide.md` mục 2, câu hỏi Q11).

### 0.4 `DataInitializer` - seed dữ liệu lần đầu VÀ tự sửa dữ liệu cũ mỗi lần boot

Đây là điểm dễ hiểu lầm nhất: `DataInitializer.run()` **không chỉ chạy 1 lần trên DB rỗng** - nó
chạy **mỗi lần app khởi động**, chia làm 2 loại thao tác:

```
run()
  │
  ├─ [1] Khối "if (roleRepository.findByCode("ADMIN").isEmpty())"
  │       CHỈ chạy khi DB thực sự rỗng (chưa từng seed): tạo 3 Role, 9 Feature, 22 Permission
  │       gốc, gán role_permissions ban đầu. Chạy ĐÚNG 1 LẦN trong vòng đời 1 database.
  │
  └─ [2] 4 method "synchronize*"/"ensure*" - LUÔN LUÔN chạy, mọi lần boot, kể cả DB đã có sẵn
          dữ liệu từ trước:
          synchronizeChatPermissions()   - đảm bảo Feature/Permission cho tính năng chat tồn tại
                                            VÀ có description/tên đúng (tự sửa nếu bị sai/cũ)
          synchronizePermissionNames()   - so từng Permission.name hiện có với
                                            PermissionCatalog.VIETNAMESE_NAMES, khác thì cập nhật
          synchronizeRoleNames()         - tương tự, cho Role.name
          synchronizeAdminPermissions()  - xem 0.3, mục [3]
          ensureHeadOfDepartmentRole()   - tạo role HEAD_OF_DEPARTMENT nếu thiếu, copy quyền từ
                                            DOCTOR nếu role đó chưa có permission nào
```

Ý nghĩa thực tế: nếu bạn sửa 1 dòng text tiếng Việt trong `PermissionCatalog.VIETNAMESE_NAMES`
rồi deploy, **không cần chạy migration SQL tay, không cần xoá dữ liệu cũ** - chỉ cần app khởi
động lại 1 lần là toàn bộ Permission cũ trong DB tự cập nhật đúng text mới. Đây chính là cơ chế
đã dùng thật để sửa lỗi chính tả tiếng Việt của `USE_AI_CHAT`/`MANAGE_MEDICAL_KNOWLEDGE` và tên
3 Role trong dự án này (xem lịch sử commit nhánh `hotfix/data-init`).

### 0.5 `@PreAuthorize` - 3 kiểu SpEL dùng trong dự án

```
hasRole('DOCTOR')                     → true nếu authorities có "ROLE_DOCTOR"
hasAnyRole('ADMIN', 'DOCTOR')         → true nếu có 1 trong các ROLE_* liệt kê
hasAuthority('GENERATE_PDF_REPORT')   → true nếu authorities có đúng permission code đó (không tiền tố)
@accessControl.canAccessUser(#p0, authentication)   → gọi bean AccessControlService, tự viết logic
```

3 kiểu đầu chỉ cần **role/permission trong JWT claim** (xem `docs/authentication-authorization-code-guide.md`
mục 0.2), không cần query DB - rất rẻ. Kiểu thứ 4 (SpEL gọi bean `@accessControl`) dùng khi quyền
không thể diễn tả bằng "có permission X hay không" mà cần **so sánh dữ liệu**: "bác sĩ này có
đúng là người phụ trách ca khám này không", "user đang thao tác có phải chính mình hay cấp trên
của mình không" - loại quyền này BẮT BUỘC phải query DB (`AccessControlService` tự
`userRepository`/`examinationRepository`...) nên đắt hơn, chỉ dùng đúng chỗ cần.

Ví dụ thật kết hợp cả 2 kiểu trong cùng 1 endpoint (`PatientController.createPatient`):
```java
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        + "or (hasRole('DOCTOR') and hasAuthority('CREATE_PATIENT_EXAM'))")
```
đọc là: "Trưởng khoa luôn được tạo bệnh nhân (không cần permission riêng), HOẶC là Bác sĩ **và**
có permission `CREATE_PATIENT_EXAM` được gán riêng qua màn quản lý phân quyền".

### 0.6 Thêm 1 API mới cần phân quyền thì làm những bước nào?

Không có bước nào tự động - phải làm thủ công đủ 4 chỗ nếu muốn permission mới xuất hiện trên UI
quản lý phân quyền và được JWT mang theo đúng:

1. Thêm hằng `code` mới vào `PermissionCatalog.VIETNAMESE_NAMES` (và `CLINICAL_PERMISSION_CODES`
   hoặc `ADMIN_DEFAULT_PERMISSION_CODES` nếu áp dụng, xem 0.3) - có test
   `permissionCatalogClassifiesEverySeedPermission` (`DataInitializerTest`) ép buộc MỌI permission
   trong `VIETNAMESE_NAMES` phải thuộc đúng 1 trong 2 tập này, không được bỏ sót.
2. Thêm `Permission` mới vào khối seed trong `DataInitializer` (nếu muốn có sẵn từ đầu) HOẶC để
   admin tự tạo qua `POST /permissions` (`PermissionController.createPermission`).
3. Gán permission đó cho role cần thiết - qua UI (`PUT /permissions/role/{roleName}`) hoặc thêm
   vào khối gán mặc định trong `DataInitializer`.
4. Gắn `@PreAuthorize("hasAuthority('CODE_MOI')")` (hoặc kết hợp `hasRole`) lên đúng method
   controller cần bảo vệ.

Thiếu bước 4 = permission tồn tại trong DB/JWT nhưng **không chặn được gì cả** - endpoint vẫn mở
cho bất kỳ ai đã đăng nhập (nếu không có `@PreAuthorize` nào khác) hoặc theo đúng rule
`@PreAuthorize` đã có sẵn (nếu quên xoá rule cũ).

### 0.7 Đọc thêm

- Toàn bộ luồng JWT/login/logout: `docs/authentication-authorization-code-guide.md`.
- Cách route business-data của chatbot áp thêm 1 lớp phân quyền riêng (SQL predicate theo
  `doctor_id`) độc lập với RBAC ở đây: `docs/rag-chatbox-code-guide.md` mục 0.5.

## 1. Danh sách file theo trách nhiệm

| File | Vai trò |
| --- | --- |
| `entity/Role.java`, `Feature.java`, `Permission.java`, `RolePermission.java` | 4 bảng mô hình RBAC |
| `config/DataInitializer.java` | Seed dữ liệu lần đầu + tự đồng bộ mỗi lần boot |
| `config/PermissionCatalog.java` | Nguồn sự thật duy nhất cho tên tiếng Việt + phân loại clinical/admin |
| `controller/PermissionController.java` | API quản lý cây permission, gán quyền cho role (admin dùng) |
| `controller/RoleController.java` | API liệt kê role (admin dùng) |
| `service/impl/PermissionServiceImpl.java` | Business logic đứng sau 2 controller trên |
| `security/AccessControlService.java` (bean `@accessControl`) | Rule phân quyền theo ownership, dùng trong `@PreAuthorize` SpEL |
| `repository/RolePermissionRepository.java` | Truy vấn permission theo role (dùng cả lúc login lẫn lúc check quyền) |

## 2. Entity - toàn văn có chú thích

```java
// entity/Role.java
@Column(name = "code", length = 50, nullable = false, unique = true)
private String code;         // "ADMIN"/"DOCTOR"/"HEAD_OF_DEPARTMENT" - dùng để build "ROLE_" + code
                              // thành GrantedAuthority (xem CustomUserDetails.getAuthorities())

@Column(name = "name", columnDefinition = "TEXT")
private String name;         // tên hiển thị tiếng Việt, ví dụ "Bác sĩ" - KHÔNG dùng để so sánh logic
```

```java
// entity/Permission.java:23-42
@Column(name = "code", length = 100, nullable = false, unique = true)
private String code;                     // "GENERATE_PDF_REPORT" - đúng chuỗi dùng trong hasAuthority()

@Column(name = "priority")
private Integer priority = 1;            // chỉ để sắp xếp thứ tự hiển thị trên UI cây permission

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "feature_id", nullable = false)
private Feature feature;                 // nhóm hiển thị (bắt buộc, mọi permission phải thuộc 1 feature)

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "requires_permission_id")
@OnDelete(action = OnDeleteAction.CASCADE)   // permission cha bị xoá -> permission con xoá theo (DB-level)
private Permission requiresPermission;       // chỉ để phân cấp hiển thị UI, xem 0.2 - KHÔNG ràng buộc runtime
```

```java
// entity/RolePermission.java - bảng nối N-N thuần tuý, không có field nào khác ngoài 2 khoá ngoại
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "role_id", nullable = false)
private Role role;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "permission_id", nullable = false)
private Permission permission;
```

## 3. `DataInitializer.java` - các method tự đồng bộ, annotated

```java
// config/DataInitializer.java:179-191
private void synchronizeRoleNames() {
    List<Role> changedRoles = new ArrayList<>();
    for (Role role : roleRepository.findAll()) {                          // quét MỌI role đang có trong DB
        String vietnameseName = ROLE_VIETNAMESE_NAMES.get(role.getCode()); // tra theo code, không theo name cũ
        if (vietnameseName != null && !Objects.equals(vietnameseName, role.getName())) {
            role.setName(vietnameseName);        // chỉ ghi đè khi thực sự khác - tránh UPDATE thừa
            changedRoles.add(role);
        }
    }
    if (!changedRoles.isEmpty()) {
        roleRepository.saveAll(changedRoles);    // 1 lần save duy nhất cho tất cả role đổi, không phải N lần
    }
}
```

```java
// config/DataInitializer.java:193-196
private void synchronizeChatPermissions() {
    Feature feature = nullable(featureRepository.findByName(CHAT_FEATURE_NAME))
            .map(existing -> withDescription(existing, CHAT_FEATURE_DESCRIPTION))  // ĐÃ tồn tại -> vẫn kiểm tra
            .orElseGet(() -> saveFeature(new Feature(null, CHAT_FEATURE_NAME, CHAT_FEATURE_DESCRIPTION)));
            //                CHƯA tồn tại -> tạo mới
```

Điểm hay dễ bỏ sót: nếu chỉ dùng `orElseGet` (tạo mới khi thiếu) mà KHÔNG có nhánh `.map(...)`
sửa description khi đã tồn tại, thì 1 Feature seed sai text từ trước sẽ **không bao giờ tự sửa
được** (vì "đã tồn tại" nên nhánh tạo mới không bao giờ chạy) - đây đúng là lỗi đã xảy ra thật
với Feature "AI Chatbox & Medical Knowledge" trước khi được vá bằng `withDescription(...)`.

```java
// config/DataInitializer.java:245-273
private void synchronizeAdminPermissions() {
    Role adminRole = roleRepository.findByCode("ADMIN").orElse(null);
    if (adminRole == null) return;

    List<RolePermission> currentAssignments = rolePermissionRepository.findByRoleId(adminRole.getId());
    List<RolePermission> clinicalAssignments = currentAssignments.stream()
            .filter(rp -> PermissionCatalog.isClinical(rp.getPermission().getCode()))
            .toList();
    if (!clinicalAssignments.isEmpty()) {
        rolePermissionRepository.deleteAll(clinicalAssignments);   // ép ADMIN không bao giờ giữ quyền clinical
    }

    Set<String> retainedCodes = ...;   // các permission KHÔNG-clinical mà ADMIN đang có, giữ nguyên
    List<RolePermission> missingDefaults = permissionRepository.findAll().stream()
            .filter(p -> PermissionCatalog.ADMIN_DEFAULT_PERMISSION_CODES.contains(p.getCode()))
            .filter(p -> !retainedCodes.contains(p.getCode()))       // chỉ bù cái đang THIẾU
            .map(p -> new RolePermission(null, adminRole, p))
            .toList();
    if (!missingDefaults.isEmpty()) {
        rolePermissionRepository.saveAll(missingDefaults);
    }
}
```

## 4. `PermissionServiceImpl.java` - chặn gán quyền clinical cho ADMIN ngay lúc ghi

```java
// service/impl/PermissionServiceImpl.java:71-88
@Override
@Transactional
public void updateRolePermissions(String roleCode, UpdateRolePermissionsRequest request) {
    Role role = roleRepository.findByCode(roleCode)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));

    List<RolePermission> newPermissions = request.permissionIds().stream().map(permissionId -> {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found with ID: " + permissionId));
        if ("ADMIN".equalsIgnoreCase(roleCode) && PermissionCatalog.isClinical(permission.getCode())) {
            throw new IllegalArgumentException(
                    "Clinical permission cannot be assigned to ADMIN: " + permission.getCode());
        }
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        return rp;
    }).collect(Collectors.toList());

    // Thay THẾ TOÀN BỘ danh sách quyền của role, không phải thêm/bớt từng cái - client (FE) phải
    // gửi lên đủ danh sách permissionIds mong muốn CUỐI CÙNG, không phải danh sách "thêm mới".
    rolePermissionRepository.deleteByRoleId(role.getId());
    rolePermissionRepository.saveAll(newPermissions);
}
```

Đây là 2 lớp bảo vệ (0.3) độc lập với nhau: lớp này chặn **ngay lúc ghi qua UI** (fail sớm, báo
lỗi rõ ràng cho admin biết vì sao bị từ chối); `synchronizeAdminPermissions()` (mục 3) là lớp
**dọn dẹp bị động**, đứng sau phòng khi có đường ghi khác (migration tay, seed cũ, bug) lọt qua
được lớp đầu.

## 5. `AccessControlService.java` - phân quyền theo dữ liệu, không theo permission tĩnh

```java
// security/AccessControlService.java:99-105
private User currentUser(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        return null;
    }
    // authentication.getName() - AN TOÀN với mọi loại principal, xem
    // docs/authentication-authorization-code-guide.md mục 0.4. Truy vấn lại DB ở đây là CHỦ Ý:
    // permission trong JWT là snapshot lúc đăng nhập, còn rule ownership dưới đây cần role/trạng
    // thái MỚI NHẤT của user, không tin tưởng JWT cũ.
    return userRepository.findByUsernameOrEmail(authentication.getName(), authentication.getName())
            .orElse(null);
}
```

```java
// security/AccessControlService.java:70-80
public boolean canAccessUser(Long userId, Authentication authentication) {
    User user = currentUser(authentication);
    return user != null && (isAdmin(user) || (isClinicalUser(user)
            && (isClinicalSupervisor(user) || user.getId().equals(userId))));
}
```

đọc là: được phép thao tác lên `userId` nếu **là ADMIN** (toàn quyền quản trị tài khoản), HOẶC
**vừa là nhân sự lâm sàng (DOCTOR/HEAD_OF_DEPARTMENT) vừa (là Trưởng khoa HOẶC chính là bản thân
user đó)** - tức bác sĩ thường chỉ tự thao tác lên chính mình, Trưởng khoa thao tác lên bất kỳ ai
trong nhóm lâm sàng.

`canAccessExamination`/`canAccessDicomInstance`/`canAccessAiResult`/`canAccessClinicalImage` đều
theo cùng 1 khuôn: thử đường "bác sĩ được gán trực tiếp" trước (rẻ, 1 query lấy thẳng
`assignedDoctorId`), chỉ khi không khớp mới lần theo quan hệ tới `Patient` rồi kiểm tra
`examinationRepository.existsByPatientIdAndDoctorId(...)` (đắt hơn, cần join sâu hơn) - tối ưu
để trường hợp phổ biến nhất (đúng bác sĩ phụ trách) trả lời nhanh nhất.

## 6. Test bảo vệ hành vi này

- `DataInitializerTest` - đảm bảo mọi permission trong `PermissionCatalog.VIETNAMESE_NAMES` được
  phân loại đúng 1 trong 2 tập clinical/admin-default, đảm bảo `synchronizeAdminPermissions` xoá
  đúng quyền clinical và bù đúng quyền thiếu, đảm bảo `HEAD_OF_DEPARTMENT` luôn được seed.
- `PermissionServiceImplTest`/`PermissionControllerTest` - chặn gán clinical cho ADMIN, luồng
  tạo/sửa/xoá Feature và Permission.
- `ControllerRbacTest`, `DiagnosisReviewControllerRbacTest`, `ChatControllerRbacTest`,
  `ClinicalAdminControllerRbacTest` - test theo kiểu "gọi API với từng role, kỳ vọng đúng
  200/403" cho hầu hết controller trong hệ thống, là bộ test giữ cho `@PreAuthorize` không bị sửa
  nhầm khi refactor.
