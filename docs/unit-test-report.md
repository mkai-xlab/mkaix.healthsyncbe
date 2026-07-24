# UNIT TEST REPORT - HEALTHSYNC BACKEND

## 1. Thông tin thực thi

| Thuộc tính | Giá trị |
|---|---|
| Created By | Codex |
| Executed By | Codex |
| Executed Date | 24/07/2026 |
| Framework | JUnit Jupiter, Mockito, Spring Security Test |
| Java | 21.0.10 |
| Test result source | `target/surefire-reports/TEST-*.xml` |
| Phạm vi | Chỉ unit test; không bao gồm integration test |

Lệnh kiểm thử:

```powershell
mvn "-Dtest=AuthServiceTest,DoctorServiceTest,PermissionServiceTest,PdfExportServiceTest,JwtTokenProviderTest,ControllerRbacTest" test
```

| Passed | Failed | Untested | Normal (N) | Abnormal (A) | Boundary (B) | Total Test Cases | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 56 | 0 | 0 | 29 | 24 | 3 | 56 | 100% |

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
| UTC-AUTH-LOGIN-01 | `login_Success` | AuthenticationManager trả user `test_user`, role `DOCTOR`, tài khoản đã kích hoạt | Username `test_user`; password `password123` | `LoginResponse(access_token, refresh_token, DOCTOR, test_user)` | Không | N | Trả đúng hai token, username và role | Tất cả giá trị trả về đúng expected | P | 24/07/2026 | - |
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

---

## 6. Role-Based Access Control

| Thuộc tính | Giá trị |
|---|---|
| Code Module | Spring Method Security / Controllers |
| Method | `@PreAuthorize` rules |
| Test Class | `ControllerRbacTest` |
| Test Requirement | Cho phép/từ chối thao tác theo role và authority thực tế qua Spring method-security proxy. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 7 | 0 | 0 | 4 | 3 | 0 | 7 | 100% |

| UTCID | Test case | Condition / Precondition | Input Role/Authority | Confirm Return / Interaction | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-RBAC-01 | `adminCanCreateFeature` | Method security enabled | `ROLE_ADMIN` | Service createFeature được gọi | Không | N | ADMIN được phép tạo feature | Trả đúng response; service được gọi | P | 24/07/2026 | - |
| UTC-RBAC-02 | `doctorCannotCreateFeature` | Method security enabled | `ROLE_DOCTOR` | Service không được thực thi | `AccessDeniedException` | A | DOCTOR bị chặn | Đúng exception | P | 24/07/2026 | - |
| UTC-RBAC-03 | `adminCanActivateDoctor` | Method security enabled | `ROLE_ADMIN`; doctor id 7 | `activateDoctor(7)` được gọi | Không | N | ADMIN được activate doctor | Service được gọi đúng id | P | 24/07/2026 | - |
| UTC-RBAC-04 | `doctorCannotActivateDoctor` | Method security enabled | `ROLE_DOCTOR`; doctor id 7 | Service không được thực thi | `AccessDeniedException` | A | DOCTOR bị chặn | Đúng exception | P | 24/07/2026 | - |
| UTC-RBAC-05 | `doctorCanSearchDoctors` | Method security enabled | `ROLE_DOCTOR` | Trả `PageResponse` | Không | N | DOCTOR được search doctor | Không bị chặn; response đúng | P | 24/07/2026 | - |
| UTC-RBAC-06 | `userWithPdfAuthorityCanGenerateReport` | Method security enabled | `GENERATE_PDF_REPORT` | Message path `report.pdf` | Không | N | Authority hợp lệ được export | Trả đúng message | P | 24/07/2026 | - |
| UTC-RBAC-07 | `roleWithoutPdfAuthorityCannotGenerateReport` | Method security enabled | Chỉ `ROLE_ADMIN`, không có PDF authority | Service không được thực thi | `AccessDeniedException` | A | Bị chặn khi thiếu authority | Đúng exception | P | 24/07/2026 | - |

---

## 7. Export PDF Report

| Thuộc tính | Giá trị |
|---|---|
| Code Module | `PdfExportService` |
| Method | `generateAndSavePdfReport` |
| Test Class | `PdfExportServiceTest` |
| Test Requirement | Lấy examination, render HTML, tạo PDF trong thư mục tạm và xử lý lỗi dữ liệu/template. |

| Passed | Failed | Untested | N | A | B | Total | Success Rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 3 | 0 | 0 | 1 | 2 | 0 | 3 | 100% |

| UTCID | Test case | Condition / Precondition | Input | Confirm Return / File | Exception | Type | Expected Output | Actual Output | Result | Date | Defect ID |
|---|---|---|---|---|---|:---:|---|---|:---:|---|---|
| UTC-PDF-01 | `generateAndSavePdfReport_Success` | Examination id 1 tồn tại; HTML hợp lệ; JUnit temp directory | Examination id `1`, encounter `ENC-123` | Path `report_ENC-123_<uuid>.pdf`; file tồn tại và size > 0 | Không | N | PDF hợp lệ được tạo trong thư mục tạm | File đúng tên, tồn tại và có dữ liệu | P | 24/07/2026 | - |
| UTC-PDF-02 | `generateAndSavePdfReport_ExaminationNotFound_ThrowsException` | Examination id 1 không tồn tại | Examination id `1` | Không render template, không tạo file | `IllegalArgumentException` | A | Message `Examination not found with id: 1` | Đúng message; template không được gọi | P | 24/07/2026 | - |
| UTC-PDF-03 | `generateAndSavePdfReport_TemplateEngineError_ThrowsException` | Examination tồn tại; template engine lỗi | Examination id `1` | Không tạo PDF thành công | `RuntimeException` | A | Message chứa `Template processing failed` | Đúng exception và message | P | 24/07/2026 | - |

---

## 8. Kết luận Unit Test

| Test Class | Passed | Failed | Errors | Skipped | Total | Success Rate |
|---|---:|---:|---:|---:|---:|---:|
| `AuthServiceTest` | 13 | 0 | 0 | 0 | 13 | 100% |
| `JwtTokenProviderTest` | 10 | 0 | 0 | 0 | 10 | 100% |
| `DoctorServiceTest` | 11 | 0 | 0 | 0 | 11 | 100% |
| `PermissionServiceTest` | 12 | 0 | 0 | 0 | 12 | 100% |
| `ControllerRbacTest` | 7 | 0 | 0 | 0 | 7 | 100% |
| `PdfExportServiceTest` | 3 | 0 | 0 | 0 | 3 | 100% |
| **TOTAL** | **56** | **0** | **0** | **0** | **56** | **100%** |

Kết quả được xác nhận từ sáu XML Surefire tương ứng: `56 tests`, `0 failures`, `0 errors`, `0 skipped`.
