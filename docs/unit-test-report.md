# UNIT TEST REPORT - HEALTHSYNC BACKEND

## 1. Thông tin thực thi

| Thuộc tính | Giá trị |
|---|---|
| Created By | Codex |
| Executed By | Codex |
| Executed Date | 25/07/2026 |
| Framework | JUnit Jupiter, Mockito, Spring Security Test |
| Java | 21.0.10 |
| Test result source | `target/surefire-reports/TEST-*.xml` |
| Phạm vi | Chỉ unit test; không bao gồm integration test |

Lệnh kiểm thử:

```powershell
mvn "-Dtest=AuthServiceTest,DoctorServiceTest,NotificationServiceTest,PermissionServiceTest,PdfExportServiceTest,JwtTokenProviderTest,ControllerRbacTest,DiagnosisReviewServiceTest,DiagnosisReviewControllerRbacTest,ExaminationMapperKlReviewTest" test
```

| Passed | Failed | Untested | Normal (N) | Abnormal (A) | Boundary (B) | Total Test Cases | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 85 | 0 | 0 | 44 | 36 | 5 | 85 | 100% |

Quy ước:

- `N - Normal`: luồng hợp lệ thông thường.
- `A - Abnormal`: dữ liệu/quyền/trạng thái không hợp lệ hoặc có exception.
- `B - Boundary`: trạng thái biên, giá trị tùy chọn hoặc giá trị mặc định.
- `P`: Passed. `F`: Failed.
- Success Rate = `Passed / Total Test Cases * 100%`.

---

## 2. Authentication

### 2.1 Login

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `AuthServiceImpl` |
| Method | `login` |
| Test Class | `AuthServiceTest` |
| Test Requirement | Xác thực username/password, xử lý lần đăng nhập đầu tiên và sinh access/refresh token. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-AUTH-LOGIN-01 | `login_Success` | AuthenticationManager trả user `test_user`, full name `Test Doctor`, role `DOCTOR`, tài khoản đã kích hoạt | Username `test_user`; password `password123` | `LoginResponse(access_token, refresh_token, DOCTOR, test_user, Test Doctor)` | Không | N | Trả đúng hai token, username, full name và role | Tất cả giá trị trả về đúng expected | P | 24/07/2026 | - |
| UTC-AUTH-LOGIN-02 | `login_FirstTimeLogin_ThrowsException` | User có `isFirstActivated=true` | Username `test_user`; password `password123` | Không sinh token | `FirstTimeLoginException` | A | Từ chối login và không gọi JWT generator | Đúng exception; access/refresh generator không được gọi | P | 24/07/2026 | - |

### 2.2 Change Password

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `AuthServiceImpl` |
| Method | `changePassword` |
| Test Class | `AuthServiceTest` |
| Test Requirement | Kiểm tra mật khẩu cũ, đổi mật khẩu mới và kích hoạt tài khoản ở lần đầu. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 4 | 0 | 0 | 1 | 2 | 1 | 4 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / State | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-AUTH-CHANGE-01 | `changePassword_Success` | User tồn tại; mật khẩu cũ khớp | `test_user`, `old_password`, `new_password` | Password được encode và user được save | Không | N | Password = `new_encoded_password` | Password và repository interaction đúng expected | P | 24/07/2026 | - |
| UTC-AUTH-CHANGE-02 | `changePassword_FirstTimeLogin_ActivatesAccount` | User tồn tại; `isFirstActivated=true`; mật khẩu tạm khớp | `test_user`, `temporary_password`, `new_password` | Password mới; `isFirstActivated=false`; user được save | Không | B | Đổi mật khẩu đồng thời kích hoạt tài khoản | Password đúng và cờ kích hoạt chuyển thành false | P | 24/07/2026 | - |
| UTC-AUTH-CHANGE-03 | `changePassword_WrongOldPassword_ThrowsException` | User tồn tại; mật khẩu cũ không khớp | `test_user`, `wrong_old_password`, `new_password` | Không save user | `IllegalArgumentException` | A | Message `Invalid username or password` | Đúng message; repository không save | P | 24/07/2026 | - |
| UTC-AUTH-CHANGE-04 | `changePassword_UserNotFound_ThrowsException` | Không tìm thấy username | `unknown_user`, `old_password`, `new_password` | Không đổi dữ liệu | `IllegalArgumentException` | A | Từ chối yêu cầu | Đúng exception | P | 24/07/2026 | - |

### 2.3 Forget Password

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `AuthServiceImpl` |
| Method | `forgotPassword` |
| Test Class | `AuthServiceTest` |
| Test Requirement | Sinh OTP reset password khi email tồn tại và ngăn email enumeration. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / Interaction | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-AUTH-FORGOT-01 | `forgotPassword_UserFound_GeneratesTokenAndSendsEmail` | User tồn tại; chưa có reset token | Email `test@hospital.com` | Save `PasswordResetToken`; gửi template `reset-password` | Không | N | OTP được lưu và email được gửi đúng địa chỉ | Repository save và MailUtil được gọi đúng | P | 24/07/2026 | - |
| UTC-AUTH-FORGOT-02 | `forgotPassword_UserNotFound_ReturnsEarly` | Email không tồn tại | Email `unknown@hospital.com` | Không lưu token; không gửi email | Không | A | Kết thúc im lặng để không lộ email | Không có interaction lưu token/gửi mail | P | 24/07/2026 | - |

### 2.4 Reset Password

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `AuthServiceImpl` |
| Method | `resetPassword` |
| Test Class | `AuthServiceTest` |
| Test Requirement | Xác minh email, OTP, thời hạn token; đổi password và xóa token sau khi dùng. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 5 | 0 | 0 | 1 | 4 | 0 | 5 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / State | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-AUTH-RESET-01 | `resetPassword_Success` | User tồn tại; token `123456` còn hạn | Email `test@hospital.com`; token `123456`; password `new_password` | Password mới; user save; token delete | Không | N | Password = `new_encoded_password`; token bị xóa | State và repository interactions đúng expected | P | 24/07/2026 | - |
| UTC-AUTH-RESET-02 | `resetPassword_UserNotFound_ThrowsException` | Email không tồn tại | Email `unknown@hospital.com`; token `123456` | Không đổi dữ liệu | `IllegalArgumentException` | A | Từ chối reset | Đúng exception | P | 24/07/2026 | - |
| UTC-AUTH-RESET-03 | `resetPassword_TokenNotFound_ThrowsException` | User tồn tại; không có reset token | Email hợp lệ; token `123456` | Không đổi dữ liệu | `IllegalArgumentException` | A | Từ chối reset | Đúng exception | P | 24/07/2026 | - |
| UTC-AUTH-RESET-04 | `resetPassword_TokenMismatch_ThrowsException` | Token lưu là `123456` | Token gửi lên `wrong_token` | Không đổi dữ liệu | `IllegalArgumentException` | A | Từ chối token sai | Đúng exception | P | 24/07/2026 | - |
| UTC-AUTH-RESET-05 | `resetPassword_TokenExpired_ThrowsException` | Token hết hạn 5 phút trước | Token `123456` | Không đổi dữ liệu | `IllegalArgumentException` | A | Message báo token hết hạn | Đúng exception | P | 24/07/2026 | - |

---

## 3. JWT Token

### 3.1 Access Token

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `JwtTokenProvider` |
| Method | Access-token methods |
| Test Class | `JwtTokenProviderTest` |
| Test Requirement | Sinh token, đọc claims và xác thực access token theo user/thời hạn. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 6 | 0 | 0 | 4 | 2 | 0 | 6 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-JWT-ACCESS-01 | `generateAccessToken_and_ExtractUsername_Success` | User `test_user` | `CustomUserDetails` hợp lệ | Token khác null; username `test_user` | Không | N | Sinh và đọc đúng username | Username thực tế `test_user` | P | 24/07/2026 | - |
| UTC-JWT-ACCESS-02 | `extractRoleFromAccessToken_Success` | User role `DOCTOR` | Access token hợp lệ | Role `DOCTOR` | Không | N | Đọc đúng role claim | Role thực tế `DOCTOR` | P | 24/07/2026 | - |
| UTC-JWT-ACCESS-03 | `extractPermissionsFromAccessToken_Success` | User có 2 permissions | `CREATE_PATIENT`, `VIEW_REPORT` | Danh sách 2 permission | Không | N | Có đủ hai permission | Kết quả chứa đúng hai code | P | 24/07/2026 | - |
| UTC-JWT-ACCESS-04 | `isAccessTokenValid_ValidToken_ReturnsTrue` | Token còn hạn; cùng user | Token hợp lệ và `test_user` | `true` | Không | N | Token hợp lệ | `true` | P | 24/07/2026 | - |
| UTC-JWT-ACCESS-05 | `isAccessTokenValid_InvalidUserDetails_ReturnsFalse` | Token của `test_user` | Validate với `wrong_user` | `false` | Không | A | Token không hợp lệ cho user khác | `false` | P | 24/07/2026 | - |
| UTC-JWT-ACCESS-06 | `isAccessTokenValid_ExpiredToken_ReturnsFalse` | TTL 1 ms; chờ 10 ms | Access token hết hạn | `false` | Không | A | Token hết hạn không hợp lệ | `false` | P | 24/07/2026 | - |

### 3.2 Refresh Token

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `JwtTokenProvider` |
| Method | Refresh-token methods |
| Test Class | `JwtTokenProviderTest` |
| Test Requirement | Sinh, đọc username và xác thực refresh token. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 4 | 0 | 0 | 2 | 2 | 0 | 4 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-JWT-REFRESH-01 | `generateRefreshToken_and_ExtractUsername_Success` | User `test_user` | `CustomUserDetails` hợp lệ | Token khác null; username `test_user` | Không | N | Sinh và đọc đúng username | Username thực tế `test_user` | P | 24/07/2026 | - |
| UTC-JWT-REFRESH-02 | `isRefreshTokenValid_ValidToken_ReturnsTrue` | Token còn hạn; cùng user | Refresh token hợp lệ | `true` | Không | N | Token hợp lệ | `true` | P | 24/07/2026 | - |
| UTC-JWT-REFRESH-03 | `isRefreshTokenValid_InvalidUserDetails_ReturnsFalse` | Token của `test_user` | Validate với `wrong_user` | `false` | Không | A | Token không hợp lệ cho user khác | `false` | P | 24/07/2026 | - |
| UTC-JWT-REFRESH-04 | `isRefreshTokenValid_ExpiredToken_ReturnsFalse` | TTL 1 ms; chờ 10 ms | Refresh token hết hạn | `false` | Không | A | Token hết hạn không hợp lệ | `false` | P | 24/07/2026 | - |

---

## 4. Doctor

### 4.1 Search Doctor

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `DoctorServiceImpl` |
| Method | `searchDoctors` |
| Test Class | `DoctorServiceTest` |
| Test Requirement | Tìm kiếm/filter doctor và bảo toàn pagination metadata. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 0 | 1 | 0 | 0 | 1 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-DOCTOR-SEARCH-01 | `searchDoctorsReturnsMappedPageAndPreservesPagination` | Repository trả 1 doctor, tổng 3 records | Keyword `one`; specialization `orthopedics`; status `ACTIVE`; page 1 size 2 | `PageResponse` đã map | Không | N | Page 1, size 2, total 3, total pages 2 | Metadata và content đúng expected | P | 24/07/2026 | - |

### 4.2 View Doctor

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `DoctorServiceImpl` |
| Method | `getAllDoctors`, `getActiveDoctors`, `getDoctorProfile` |
| Test Class | `DoctorServiceTest` |
| Test Requirement | Xem danh sách doctor, danh sách active và profile theo username. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 3 | 0 | 0 | 3 | 0 | 0 | 3 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-DOCTOR-VIEW-01 | `getAllDoctorsMapsEveryDoctor` | Repository trả 2 Doctor | Không | 2 `DoctorResponse` | Không | N | Map đầy đủ 2 records | Nhận đúng 2 response | P | 24/07/2026 | - |
| UTC-DOCTOR-VIEW-02 | `getActiveDoctorsOnlyQueriesActiveStatus` | Repository có doctor ACTIVE | Status `ACTIVE` | Danh sách active doctor | Không | N | Chỉ query ACTIVE và map response | Đúng query ACTIVE và đúng response | P | 24/07/2026 | - |
| UTC-DOCTOR-VIEW-03 | `getDoctorProfileMapsDoctorByUsername` | Username tồn tại | Username `doctor.one` | `DoctorResponse` tương ứng | Không | N | Tìm và map đúng doctor | Trả đúng response mock | P | 24/07/2026 | - |

### 4.3 Create Doctor

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `DoctorServiceImpl` |
| Method | `createDoctor` |
| Test Class | `DoctorServiceTest` |
| Test Requirement | Tạo doctor, sinh credentials, gán role/status và kiểm tra email trùng. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / State | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-DOCTOR-CREATE-01 | `createDoctorGeneratesCredentialsAndPersistsActiveDoctor` | Email/phone chưa tồn tại; role DOCTOR tồn tại | Name `New Doctor`; email `new.doctor@hospital.com`; phone `0900000000`; experience 5 | Username `new.doctor`; encoded password; ACTIVE; welcome email | Không | N | Doctor id 8 được save với đúng fields | State entity, save và mail interaction đúng | P | 24/07/2026 | - |
| UTC-DOCTOR-CREATE-02 | `createDoctorRejectsDuplicateEmailBeforeSaving` | Email đã tồn tại | Email `existing@hospital.com` | Không save doctor | `IllegalArgumentException` | A | Message email đã đăng ký | Đúng message và repository không save | P | 24/07/2026 | - |

### 4.4 Update Doctor

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `DoctorServiceImpl` |
| Method | `editDoctor` |
| Test Class | `DoctorServiceTest` |
| Test Requirement | Cập nhật toàn phần/từng phần doctor, avatar và xử lý doctor không tồn tại. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 3 | 0 | 0 | 2 | 1 | 0 | 3 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / State | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-DOCTOR-UPDATE-01 | `editDoctorUpdatesProvidedFieldsAndCreatesAvatar` | Doctor id 7 tồn tại; chưa có avatar | Name/email/phone/avatar/experience/degree/biography mới | Save doctor; tạo Image extension `png` | Không | N | Tất cả field được cập nhật | Tất cả assertion đúng expected | P | 24/07/2026 | - |
| UTC-DOCTOR-UPDATE-02 | `editDoctorLeavesNullFieldsUnchangedAndUpdatesExistingAvatar` | Doctor tồn tại và đã có avatar | Chỉ avatar `new.png`; field khác null | Giữ field cũ; cập nhật avatar hiện có | Không | N | Full name giữ nguyên; avatar đổi | Full name `Doctor One`; avatar `new.png` | P | 24/07/2026 | - |
| UTC-DOCTOR-UPDATE-03 | `editDoctorRejectsUnknownDoctor` | Doctor id 99 không tồn tại | Id `99` | Không save | `IllegalArgumentException` | A | Message `Doctor with id 99 not found` | Đúng message; không save | P | 24/07/2026 | - |

### 4.5 Active/Deactive Doctor

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `DoctorServiceImpl` |
| Method | `activateDoctor`, `softDeleteDoctor` |
| Test Class | `DoctorServiceTest` |
| Test Requirement | Chuyển trạng thái doctor giữa ACTIVE và INACTIVE. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 2 | 0 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm State | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-DOCTOR-STATUS-01 | `activateDoctorPersistsActiveStatus` | Doctor id 7 đang INACTIVE | Id `7` | Entity save có status ACTIVE | Không | N | Chuyển và lưu ACTIVE | Status thực tế ACTIVE | P | 24/07/2026 | - |
| UTC-DOCTOR-STATUS-02 | `deactivateDoctorPersistsInactiveStatus` | Doctor id 7 đang ACTIVE | Id `7` | Entity save có status INACTIVE | Không | N | Chuyển và lưu INACTIVE | Status thực tế INACTIVE | P | 24/07/2026 | - |

---

## 5. Feature, Permission và Role-Permission

### 5.1 View Feature/Permission Tree

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `getPermissionTree` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Gom permission theo feature, sắp xếp priority và trả dependency. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 0 | 1 | 0 | 0 | 1 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-PERM-TREE-01 | `getPermissionTreeGroupsAndSortsPermissionsByPriority` | Feature Reports; permissions priority 2 và 1 | Feature id 10; permission ids 20, 21 | `FeatureResponse` chứa permissions đã sort | Không | N | Thứ tự `CREATE_REPORT`, `VIEW_REPORT`; dependency id 20 | Thứ tự và dependency đúng expected | P | 24/07/2026 | - |

### 5.2 View Role Permissions

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `getRolePermissions` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Xem permission IDs của role và xử lý role không tồn tại. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-ROLE-PERM-VIEW-01 | `getRolePermissionsReturnsPermissionIds` | Role DOCTOR id 5 có hai permissions | Role code `DOCTOR` | `[20, 22]` | Không | N | Trả đúng permission IDs | Kết quả `[20, 22]` | P | 24/07/2026 | - |
| UTC-ROLE-PERM-VIEW-02 | `getRolePermissionsRejectsUnknownRole` | Role không tồn tại | Role code `UNKNOWN` | Không | `IllegalArgumentException` | A | Message `Role not found: UNKNOWN` | Đúng message | P | 24/07/2026 | - |

### 5.3 Update Role Permissions

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `updateRolePermissions` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Thay thế permission assignments của role và kiểm tra permission không tồn tại. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Interaction | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-ROLE-PERM-UPDATE-01 | `updateRolePermissionsReplacesExistingAssignments` | Role DOCTOR và permissions 20, 22 tồn tại | Role `DOCTOR`; ids `[20,22]` | Delete mapping cũ; save 2 mapping mới | Không | N | `deleteByRoleId(5)` và `saveAll` đúng data | Interactions và mapping IDs đúng expected | P | 24/07/2026 | - |
| UTC-ROLE-PERM-UPDATE-02 | `updateRolePermissionsRejectsMissingPermission` | Permission 999 không tồn tại | Role `DOCTOR`; ids `[999]` | Không save mappings | `IllegalArgumentException` | A | Message `Permission not found with ID: 999` | Đúng message; không saveAll | P | 24/07/2026 | - |

### 5.4 Create Feature

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `createFeature` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Tạo feature và kiểm tra trùng tên. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-FEATURE-CREATE-01 | `createFeaturePersistsAndReturnsFeature` | Tên chưa tồn tại | Name `Reports`; description `Report management` | Feature id 10; permissions rỗng | Không | N | Save và trả feature mới | Id 10, name Reports, permissions `[]` | P | 24/07/2026 | - |
| UTC-FEATURE-CREATE-02 | `createFeatureRejectsDuplicateName` | Tên Reports đã tồn tại | Name `Reports` | Không save | `IllegalArgumentException` | A | Từ chối tên trùng | Đúng exception; repository không save | P | 24/07/2026 | - |

### 5.5 Update Feature

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `updateFeature` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Cập nhật feature và trả các permission hiện có. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 0 | 1 | 0 | 0 | 1 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-FEATURE-UPDATE-01 | `updateFeaturePersistsChangesAndReturnsExistingPermissions` | Feature id 10 tồn tại | Name `Clinical reports`; description `Updated` | Feature mới và permission `VIEW_REPORT` | Không | N | Save fields mới, giữ permission response | Name/description và permission đúng expected | P | 24/07/2026 | - |

### 5.6 Create Permission

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `createPermission` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Tạo permission, priority mặc định, dependency tùy chọn và kiểm tra code trùng. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 0 | 1 | 1 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-PERM-CREATE-01 | `createPermissionUsesDefaultPriorityAndOptionalRequirement` | Feature 10 và required permission 20 tồn tại | Code `CREATE_REPORT`; priority null; requires id 20 | Permission id 21; priority 1; dependency 20 | Không | B | Dùng priority mặc định và gán dependency | Response đúng id, priority, dependency | P | 24/07/2026 | - |
| UTC-PERM-CREATE-02 | `createPermissionRejectsDuplicateCode` | Code VIEW_REPORT đã tồn tại | Code `VIEW_REPORT` | Không save | `IllegalArgumentException` | A | Từ chối code trùng | Đúng exception; repository không save | P | 24/07/2026 | - |

### 5.7 Update Permission

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `updatePermission` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Cập nhật permission, giữ priority khi bỏ trống, xóa dependency và chống self-reference. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 0 | 1 | 1 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / State | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-PERM-UPDATE-01 | `updatePermissionClearsRequirementAndPreservesPriorityWhenOmitted` | Permission id 20 tồn tại, priority hiện tại 2 | Priority null; requiresPermissionId null | Priority vẫn 2; dependency null | Không | B | Giữ priority và xóa dependency | State và response đúng expected | P | 24/07/2026 | - |
| UTC-PERM-UPDATE-02 | `updatePermissionRejectsSelfRequirement` | Permission id 20 tồn tại | Permission 20 yêu cầu chính id 20 | Không save | `IllegalArgumentException` | A | Message `Permission cannot require itself` | Đúng message; repository không save | P | 24/07/2026 | - |

### 5.8 Delete Feature

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `deleteFeature` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Xóa feature sau khi dọn permission, role assignment và dependency; xử lý feature không tồn tại. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Interaction | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-FEATURE-DELETE-01 | `deleteFeatureRemovesPermissionRelationsBeforeFeature` | Feature 10 có permission 20 | Feature id `10` | Clear dependency; delete role mappings, permission và feature | Không | N | Toàn bộ liên kết được dọn trước khi xóa feature | Repository interactions đúng expected | P | 24/07/2026 | - |
| UTC-FEATURE-DELETE-02 | `deleteFeatureRejectsUnknownFeature` | Feature 999 không tồn tại | Feature id `999` | Không delete feature | `IllegalArgumentException` | A | Message `Feature not found with ID: 999` | Đúng message; không gọi delete | P | 24/07/2026 | - |

### 5.9 Delete Permission

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PermissionServiceImpl` |
| Method | `deletePermission` |
| Test Class | `PermissionServiceTest` |
| Test Requirement | Xóa permission sau khi dọn role assignment và dependency; xử lý permission không tồn tại. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Interaction | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-PERM-DELETE-01 | `deletePermissionRemovesRelationsBeforePermission` | Permission 20 tồn tại | Permission id `20` | Clear dependency; delete role mappings và permission | Không | N | Liên kết được dọn trước khi xóa permission | Repository interactions đúng expected | P | 24/07/2026 | - |
| UTC-PERM-DELETE-02 | `deletePermissionRejectsUnknownPermission` | Permission 999 không tồn tại | Permission id `999` | Không delete permission | `IllegalArgumentException` | A | Message `Permission not found with ID: 999` | Đúng message; không gọi delete | P | 24/07/2026 | - |

---

## 6. Notifications

### 6.1 Get All Notifications

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `NotificationServiceImpl` |
| Method | `getAllNotifications` |
| Test Class | `NotificationServiceTest` |
| Test Requirement | Trả cả notification đã đọc/chưa đọc theo thứ tự repository và xử lý user không tồn tại. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 0 | 0 | 1 | 1 | 0 | 2 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-NOTI-ALL-01 | `getAllNotificationsReturnsReadAndUnreadNewestFirst` | User 7 có một notification chưa đọc và một đã đọc | Username `doctor` | Danh sách gồm cả hai trạng thái | Không | N | Giữ thứ tự mới nhất trước từ repository | DTO order và `isRead=[false,true]` đúng expected | P | 24/07/2026 | - |
| UTC-NOTI-ALL-02 | `getAllNotificationsRejectsUnknownUser` | Username không tồn tại | Username `unknown` | Không query notification | `IllegalArgumentException` | A | Message `User not found` | Đúng message; notification repository không được gọi | P | 24/07/2026 | - |

---

## 7. Role-Based Access Control

| Thuộc tính | Giá trị |
|---|---|
| Code Module | Spring Method Security / Controllers |
| Method | `@PreAuthorize` rules |
| Test Class | `ControllerRbacTest` |
| Test Requirement | Cho phép/từ chối thao tác theo role và authority thực tế qua Spring method-security proxy. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 11 | 0 | 0 | 6 | 5 | 0 | 11 | 100% |

| UTCID | Test case | Condition / Precondition | Input Role/Authority | Confirm Return / Interaction | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-RBAC-01 | `adminCanCreateFeature` | Method security enabled | `ROLE_ADMIN` | Service createFeature được gọi | Không | N | ADMIN được phép tạo feature | Trả đúng response; service được gọi | P | 24/07/2026 | - |
| UTC-RBAC-02 | `doctorCannotCreateFeature` | Method security enabled | `ROLE_DOCTOR` | Service không được thực thi | `AccessDeniedException` | A | DOCTOR bị chặn | Đúng exception | P | 24/07/2026 | - |
| UTC-RBAC-03 | `adminCanActivateDoctor` | Method security enabled | `ROLE_ADMIN`; doctor id 7 | `activateDoctor(7)` được gọi | Không | N | ADMIN được activate doctor | Service được gọi đúng id | P | 24/07/2026 | - |
| UTC-RBAC-04 | `doctorCannotActivateDoctor` | Method security enabled | `ROLE_DOCTOR`; doctor id 7 | Service không được thực thi | `AccessDeniedException` | A | DOCTOR bị chặn | Đúng exception | P | 24/07/2026 | - |
| UTC-RBAC-05 | `doctorCanSearchDoctors` | Method security enabled | `ROLE_DOCTOR` | Trả `PageResponse` | Không | N | DOCTOR được search doctor | Không bị chặn; response đúng | P | 24/07/2026 | - |
| UTC-RBAC-06 | `userWithPdfAuthorityCanGenerateReport` | Method security enabled | `GENERATE_PDF_REPORT` | Message path `report.pdf` | Không | N | Authority hợp lệ được export | Trả đúng message | P | 24/07/2026 | - |
| UTC-RBAC-07 | `roleWithoutPdfAuthorityCannotGenerateReport` | Method security enabled | Chỉ `ROLE_ADMIN`, không có PDF authority | Service không được thực thi | `AccessDeniedException` | A | Bị chặn khi thiếu authority | Đúng exception | P | 24/07/2026 | - |
| UTC-RBAC-08 | `adminCanDeleteFeature` | Method security enabled | `ROLE_ADMIN`; feature id 10 | `deleteFeature(10)` được gọi; HTTP 204 | Không | N | ADMIN được xóa feature | Status và service interaction đúng | P | 24/07/2026 | - |
| UTC-RBAC-09 | `doctorCannotDeleteFeature` | Method security enabled | `ROLE_DOCTOR`; feature id 10 | Service không được thực thi | `AccessDeniedException` | A | DOCTOR bị chặn | Đúng exception | P | 24/07/2026 | - |
| UTC-RBAC-10 | `adminCanDeletePermission` | Method security enabled | `ROLE_ADMIN`; permission id 20 | `deletePermission(20)` được gọi; HTTP 204 | Không | N | ADMIN được xóa permission | Status và service interaction đúng | P | 24/07/2026 | - |
| UTC-RBAC-11 | `doctorCannotDeletePermission` | Method security enabled | `ROLE_DOCTOR`; permission id 20 | Service không được thực thi | `AccessDeniedException` | A | DOCTOR bị chặn | Đúng exception | P | 24/07/2026 | - |

---

## 8. Export PDF Report

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PdfExportService` |
| Method | `generateAndSavePdfReport` |
| Test Class | `PdfExportServiceTest`, `ReportControllerTest`, `PdfReportTemplateTest` |
| Test Requirement | Lấy examination, chọn kết quả AI mới nhất đã được xác nhận, xuất KL cuối cùng, render HTML và xử lý lỗi dữ liệu/template. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 17 | 0 | 0 | 8 | 8 | 1 | 17 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / File | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-PDF-01 | `generateAndSavePdfReport_Success` | Examination id 1 tồn tại; HTML hợp lệ; JUnit temp directory | Examination id `1`, encounter `ENC-123` | Path `report_ENC-123_<uuid>.pdf`; file tồn tại và size > 0 | Không | N | PDF hợp lệ được tạo trong thư mục tạm | File đúng tên, tồn tại và có dữ liệu | P | 24/07/2026 | - |
| UTC-PDF-02 | `generateAndSavePdfReport_ExaminationNotFound_ThrowsException` | Examination id 1 không tồn tại | Examination id `1` | Không render template, không tạo file | `IllegalArgumentException` | A | Message `Examination not found with id: 1` | Đúng message; template không được gọi | P | 24/07/2026 | - |
| UTC-PDF-03 | `generateAndSavePdfReport_TemplateEngineError_ThrowsException` | Examination tồn tại; template engine lỗi | Examination id `1` | Không tạo PDF thành công | `RuntimeException` | A | Message chứa `Template processing failed` | Đúng exception và message | P | 24/07/2026 | - |
| UTC-PDF-04 | `generateAndSavePdfReport_UsesAiGradeWhenDoctorConfirmedAi` | Latest AI result KL2 có quyết định `AI_CONFIRMED` | Examination id `1` | KL cuối cùng 2; KL AI 2 | Không | N | PDF dùng đúng kết quả AI đã xác nhận | Dữ liệu template đúng expected | P | 25/07/2026 | - |
| UTC-PDF-05 | `generateAndSavePdfReport_UsesAdjustedGradeWhenDoctorChangedKl` | Latest AI result KL2 được adjust thành KL4 | Examination id `1` | KL cuối cùng 4; KL AI vẫn 2 | Không | N | PDF dùng kết quả bác sĩ đã chỉnh sửa | Dữ liệu template đúng expected | P | 25/07/2026 | - |
| UTC-PDF-06 | `generateAndSavePdfReport_RejectsUnconfirmedAiResult` | Latest AI result chưa có quyết định review | Examination id `1` | Không render template | `IllegalArgumentException` | A | Từ chối xuất kết quả chưa xác nhận | Đúng exception/message | P | 25/07/2026 | - |
| UTC-PDF-07 | `generateAndSavePdfReport_UsesOnlyLatestAiAnalysis` | Một analysis cũ chưa xác nhận và một analysis mới đã xác nhận | Examination id `1` | Chỉ một kết quả KL3 | Không | B | Bỏ analysis cũ, chỉ dùng kết quả mới nhất | Chỉ latest result được đưa vào template | P | 25/07/2026 | - |

---

### Current PDF coverage update (29/07/2026)

The current targeted run contains 17 PDF-specific tests: 14 service tests, 2 controller streaming tests, and 1 real-template/font packaging test. All 17 passed. Coverage added after the original table includes:

- Mapping available collection-form fields while leaving unavailable fields blank.
- Returning an existing stored report without rendering a duplicate.
- Rejecting unassigned doctors and report paths outside the configured export directory.
- Returning the stored PDF for an assigned doctor and a department head.
- Removing an incomplete PDF when database persistence fails.
- Returning `Content-Disposition: inline` for preview and `attachment` for download.
- Rendering the production report template with the packaged Tahoma font through an input stream.

---

## 9. Xác nhận và điều chỉnh KL Grade

### 9.1 Diagnosis Review Service

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `DiagnosisReviewServiceImpl` |
| Method | `confirmAiGrade`, `adjustKlGrade` |
| Test Class | `DiagnosisReviewServiceTest` |
| Test Requirement | Lưu quyết định xác nhận AI hoặc chỉnh KL, giữ nguyên KL AI, cho phép trưởng khoa review, kiểm tra quyền sở hữu examination và audit annotation. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 12 | 0 | 0 | 6 | 5 | 1 | 12 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / State | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-KL-SVC-01 | `adjustKlGradeCreatesReviewAndPreservesAiPrediction` | AI result KL2 thuộc examination của doctor 7; chưa có review | AI result 19; confirmed KL3; review note | Review 23; predicted vẫn KL2; confirmed KL3 | Không | N | Tạo review, trim note và không sửa dự đoán AI | Response/state đúng expected | P | 25/07/2026 | - |
| UTC-KL-SVC-02 | `adjustKlGradeUpdatesExistingReview` | AI result 19 đã có review 23 KL3 | Confirmed KL4; note mới | Giữ review id 23; cập nhật KL/note | Không | B | Upsert review hiện hữu | Entity và repository interaction đúng | P | 25/07/2026 | - |
| UTC-KL-SVC-03 | `confirmAiGradeUsesOriginalPredictionAsFinalGrade` | AI result có predicted KL2 | AI result 19 | Confirmed KL2; decision `AI_CONFIRMED` | Không | N | Chọn xác nhận thì KL cuối cùng bằng KL AI | Response/state đúng expected | P | 25/07/2026 | - |
| UTC-KL-SVC-04 | `departmentHeadCanAdjustExaminationAssignedToAnotherDoctor` | Examination thuộc doctor 7; caller là trưởng khoa 8 | Confirmed KL4 | Review thuộc trưởng khoa; decision `DOCTOR_ADJUSTED` | Không | N | Trưởng khoa được adjust ngoài assignment | Response/state đúng expected | P | 25/07/2026 | - |
| UTC-KL-SVC-05 | `adjustKlGradeRejectsGradeOutsideKlScale` | Không cần query dữ liệu | Confirmed KL5 | Không query/save | `IllegalArgumentException` | A | Chỉ chấp nhận KL0-KL4 | Đúng message; repository không được gọi | P | 25/07/2026 | - |
| UTC-KL-SVC-06 | `adjustKlGradeRejectsUnknownAiResult` | AI result 999 không tồn tại | Confirmed KL3 | Không save review | `IllegalArgumentException` | A | Message `AI result not found with ID: 999` | Đúng message | P | 25/07/2026 | - |
| UTC-KL-SVC-07 | `adjustKlGradeRejectsUnknownDoctor` | AI result tồn tại; username không có doctor | Username `unknown` | Không save review | `IllegalArgumentException` | A | Message `Doctor not found: unknown` | Đúng message | P | 25/07/2026 | - |
| UTC-KL-SVC-08 | `adjustKlGradeRejectsDoctorNotAssignedToExamination` | Examination được giao doctor 7; caller doctor 8 | Confirmed KL3 | Không save review | `AccessDeniedException` | A | Chặn sửa examination của bác sĩ khác | Đúng exception/message | P | 25/07/2026 | - |
| UTC-KL-SVC-09 | `confirmAndAdjustActionsAreAuditLogged` | Hai service method dùng AOP audit | Chữ ký confirm và adjust | `CONFIRM_AI_GRADE`; `OVERRIDE_AI_GRADE` | Không | N | Cả hai method có đúng `@LogAction` | Annotation và action code đúng expected | P | 25/07/2026 | - |

### 9.2 KL Grade RBAC

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `DiagnosisReviewController` |
| Method | `@PreAuthorize` trên `confirmAiGrade`, `adjustKlGrade` |
| Test Class | `DiagnosisReviewControllerRbacTest` |
| Test Requirement | Doctor cần đúng authority để confirm/adjust; trưởng khoa kế thừa cả hai quyền review; role không phù hợp bị chặn. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 6 | 0 | 0 | 3 | 3 | 0 | 6 | 100% |

| UTCID | Test case | Input Authorities | Confirm Interaction | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-KL-RBAC-01 | `doctorWithOverrideAuthorityCanAdjustKlGrade` | `ROLE_DOCTOR`, `OVERRIDE_AI_GRADE` | Service được gọi đúng AI result/request/username | Không | N | Cho phép và trả response | Đúng response/interaction | P | 25/07/2026 | - |
| UTC-KL-RBAC-02 | `doctorWithoutOverrideAuthorityCannotAdjustKlGrade` | Chỉ `ROLE_DOCTOR` | Service không thực thi | `AccessDeniedException` | A | Chặn khi thiếu authority | Đúng exception | P | 25/07/2026 | - |
| UTC-KL-RBAC-03 | `nonDoctorCannotAdjustKlGrade` | `ROLE_ADMIN`, `OVERRIDE_AI_GRADE` | Service không thực thi | `AccessDeniedException` | A | Chặn role không phải DOCTOR | Đúng exception | P | 25/07/2026 | - |
| UTC-KL-RBAC-04 | `departmentHeadCanAdjustKlGradeWithoutExplicitAuthority` | `ROLE_HEAD_OF_DEPARTMENT` | Service adjust được gọi | Không | N | Trưởng khoa được phép adjust | Đúng response/interaction | P | 25/07/2026 | - |
| UTC-KL-RBAC-05 | `doctorWithConfirmAuthorityCanConfirmAiGrade` | `ROLE_DOCTOR`, `CONFIRM_CONCLUSION` | Service confirm được gọi | Không | N | Doctor được xác nhận kết quả AI | Đúng response/interaction | P | 25/07/2026 | - |

### 9.3 Examination Response Mapping

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `ExaminationMapper` |
| Method | `toDto` |
| Test Class | `ExaminationMapperKlReviewTest` |
| Test Requirement | Response giữ KL AI, trả KL xác nhận, quyết định review và dùng KL xác nhận làm `effectiveGrade`. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 0 | 1 | 0 | 0 | 1 | 100% |

| UTCID | Test case | Input | Confirm Return | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-KL-MAP-01 | `mapsPredictedConfirmedAndEffectiveKlGrades` | Predicted KL2; review KL3; doctor 7 | predicted 2; confirmed/effective 3; decision `DOCTOR_ADJUSTED` | Không | N | Mapper phản ánh quyết định, không mất AI grade | Tất cả field đúng expected | P | 25/07/2026 | - |

---

## 10. Kết luận Unit Test

| Test Class | Passed | Failed | Errors | Skipped | Total | Success Rate |
|---|---:|---:|---:|---:|---:|---:|
| `AuthServiceTest` | 13 | 0 | 0 | 0 | 13 | 100% |
| `JwtTokenProviderTest` | 10 | 0 | 0 | 0 | 10 | 100% |
| `DoctorServiceTest` | 11 | 0 | 0 | 0 | 11 | 100% |
| `NotificationServiceTest` | 2 | 0 | 0 | 0 | 2 | 100% |
| `PermissionServiceTest` | 16 | 0 | 0 | 0 | 16 | 100% |
| `ControllerRbacTest` | 11 | 0 | 0 | 0 | 11 | 100% |
| `PdfExportServiceTest` | 8 | 0 | 0 | 0 | 8 | 100% |
| `DiagnosisReviewServiceTest` | 12 | 0 | 0 | 0 | 12 | 100% |
| `DiagnosisReviewControllerRbacTest` | 6 | 0 | 0 | 0 | 6 | 100% |
| `ExaminationMapperKlReviewTest` | 1 | 0 | 0 | 0 | 1 | 100% |
| **TOTAL** | **90** | **0** | **0** | **0** | **90** | **100%** |

### 10.1 Additional KL workflow coverage

| UTCID | Test case | Expected Output | Result | Date |
|---|---|---|:---:|---|
| UTC-KL-SVC-10 | `finalReviewMarksExaminationAsVerified` | All latest AI results reviewed; examination becomes `VERIFIED` | P | 25/07/2026 |
| UTC-KL-SVC-11 | `reviewIsRejectedAfterReportWasGenerated` | Review is rejected after `REPORT_GENERATED` | P | 25/07/2026 |
| UTC-KL-SVC-12 | `departmentHeadCanConfirmExaminationAssignedToAnotherDoctor` | Department head confirms an AI result outside their assignment | P | 25/07/2026 |
| UTC-KL-RBAC-06 | `departmentHeadCanConfirmAiGrade` | Department head can call the confirm endpoint | P | 25/07/2026 |
| UTC-PDF-08 | `generateAndSavePdfReport_RejectsExaminationWithoutAiResults` | PDF export rejects an examination without AI results | P | 25/07/2026 |

The focused KL/RBAC/mapper/PDF run completed with `27 tests`, `0 failures`, `0 errors`, and `0 skipped`. The full project run executed `144 tests` with `6 failures` and `6 errors`; those 12 existing integration failures are caused by shared local database state and foreign-key cleanup failures in `audit_logs` and `examinations`, not by the focused KL workflow tests.

Kết quả được xác nhận từ mười XML Surefire tương ứng: `90 tests`, `0 failures`, `0 errors`, `0 skipped`.

## 11. DoctorControllerTest (Thực thi ngày 30/07/2026)

Lệnh kiểm thử: `mvn test -Dtest=DoctorControllerTest`

| UTCID | API | Trường hợp | Expected Output | Status |
|---|---|---|---|:---:|
| UTC-DOC-01 | `POST /doctors` | `Normal`: Dữ liệu hợp lệ, role ADMIN | 201 Created | P |
| UTC-DOC-02 | `POST /doctors` | `Abnormal`: Thiếu Body Request | 400 Bad Request | P |
| UTC-DOC-03 | `POST /doctors` | `Abnormal`: `fullName` null/blank/>100 ký tự | 400 Bad Request | P |
| UTC-DOC-04 | `POST /doctors` | `Abnormal`: `email` null/blank/sai format/>150 ký tự | 400 Bad Request | P |
| UTC-DOC-05 | `POST /doctors` | `Abnormal`: `phone` null/blank/chứa chữ/>20 ký tự | 400 Bad Request | P |
| UTC-DOC-06 | `POST /doctors` | `Abnormal`: `degree` >100 ký tự | 400 Bad Request | P |
| UTC-DOC-07 | `POST /doctors` | `Abnormal`: Security - role DOCTOR | 403 Forbidden | P |
| UTC-DOC-08 | `PUT /doctors/{id}` | `Normal`: Dữ liệu hợp lệ, role ADMIN | 200 OK | P |
| UTC-DOC-09 | `PUT /doctors/{id}` | `Abnormal`: Thiếu Body Request | 400 Bad Request | P |
| UTC-DOC-10 | `PUT /doctors/{id}` | `Abnormal`: `email` sai format | 400 Bad Request | P |
| UTC-DOC-11 | `PUT /doctors/{id}` | `Abnormal`: `degree` >100 ký tự | 400 Bad Request | P |
| UTC-DOC-12 | `PUT /doctors/{id}` | `Abnormal`: Không tìm thấy ID bác sĩ | 400 Bad Request | P |
| UTC-DOC-13 | `PUT /doctors/{id}` | `Abnormal`: Security - role DOCTOR | 403 Forbidden | P |
| UTC-DOC-14 | `GET /doctors/profile` | `Normal`: Lấy profile của chính mình | 200 OK | P |
| UTC-DOC-15 | `GET /doctors/profile` | `Abnormal`: Không tìm thấy user | 400 Bad Request | P |
| UTC-DOC-16 | `GET /doctors/profile` | `Abnormal`: Security - role PATIENT | 403 Forbidden | P |
| UTC-DOC-17 | `PUT /doctors/profile` | `Normal`: Sửa profile của chính mình hợp lệ | 200 OK | P |
| UTC-DOC-18 | `PUT /doctors/profile` | `Abnormal`: `email` sai format | 400 Bad Request | P |
| UTC-DOC-19 | `PUT /doctors/profile` | `Abnormal`: `degree` >100 ký tự | 400 Bad Request | P |
| UTC-DOC-20 | `PUT /doctors/profile` | `Abnormal`: Security - role PATIENT | 403 Forbidden | P |
| UTC-DOC-21 | `GET /doctors/active` | `Normal`: Lấy danh sách bác sĩ đang active | 200 OK | P |
| UTC-DOC-22 | `GET /doctors/active` | `Abnormal`: Security - role PATIENT | 403 Forbidden | P |
| UTC-DOC-23 | `POST /doctors/{id}/activate` | `Normal`: Kích hoạt bác sĩ theo ID (ADMIN) | 200 OK | P |
| UTC-DOC-24 | `POST /doctors/{id}/activate` | `Abnormal`: ID không tồn tại | 400 Bad Request | P |
| UTC-DOC-25 | `POST /doctors/{id}/activate` | `Abnormal`: Security - role DOCTOR | 403 Forbidden | P |
| UTC-DOC-26 | `POST /doctors/{id}/deactivate` | `Normal`: Hủy kích hoạt POST (ADMIN) | 200 OK | P |
| UTC-DOC-27 | `POST /doctors/{id}/deactivate` | `Abnormal`: Security - role DOCTOR | 403 Forbidden | P |
| UTC-DOC-28 | `DELETE /doctors/{id}` | `Normal`: Hủy kích hoạt DELETE (ADMIN) | 200 OK | P |
| UTC-DOC-29 | `DELETE /doctors/{id}` | `Abnormal`: Security - role DOCTOR | 403 Forbidden | P |

Kết quả: **29/29 tests passed (100% Success Rate).** Các lỗi `400 Bad Request` do validation hoặc không tìm thấy dữ liệu đều đã được GlobalExceptionHandler bắt và xử lý triệt để (không rớt về 500). Toàn bộ RBAC security cũng đã được verify bằng MockMvc + Spring Security.

---

## 11. Staff Management (UserServiceImpl)

| Thu?c t�nh | Gi� tr? |
|---|---|
| Code Module | "UserServiceImpl" |
| Method | "searchStaff", "toggleUserStatus" |
| Test Class | "UserServiceImplTest" |
| Test Requirement | T�m ki?m staff (DOCTOR, HEAD_OF_DEPARTMENT), k�ch ho?t/v� hi?u h�a staff v� g?i email. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 6 | 0 | 0 | 3 | 3 | 0 | 6 | 100% |

| UTCID | Test case | Result | Date |
|---|---|:---:|---|
| UTC-STAFF-SEARCH-01 | "testSearchStaff_Normal" | P | 12/08/2026 |
| UTC-STAFF-TOGGLE-01 | "testToggleUserStatus_Deactivate_Normal" | P | 12/08/2026 |
| UTC-STAFF-TOGGLE-02 | "testToggleUserStatus_Activate_Normal" | P | 12/08/2026 |
| UTC-STAFF-TOGGLE-03 | "testToggleUserStatus_Deactivate_MissingReason_ThrowsException" | P | 12/08/2026 |
| UTC-STAFF-TOGGLE-04 | "testToggleUserStatus_TargetIsAdmin_ThrowsException" | P | 12/08/2026 |
| UTC-STAFF-TOGGLE-05 | "testToggleUserStatus_UserNotFound_ThrowsException" | P | 12/08/2026 |
