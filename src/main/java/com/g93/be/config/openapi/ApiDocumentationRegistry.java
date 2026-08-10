package com.g93.be.config.openapi;

import org.springframework.web.method.HandlerMethod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ApiDocumentationRegistry {

        enum ResponseKind {
                JSON,
                TEXT,
                VOID,
                PDF,
                PNG,
                DICOM
        }

        record EndpointDoc(
                        String tag,
                        String summary,
                        String description,
                        String successCode,
                        String successDescription,
                        String responseExampleKey,
                        String requestExampleKey,
                        ResponseKind responseKind,
                        boolean publicEndpoint,
                        boolean notFoundResponse,
                        boolean unsupportedMediaType,
                        Set<String> extraErrorResponses) {
        }

        private final Map<String, EndpointDoc> documents = new LinkedHashMap<>();

        ApiDocumentationRegistry() {
                registerAuth();
                registerDoctors();
                registerUsers();
                registerPatients();
                registerExaminations();
                registerDicom();
                registerAiAndReviews();
                registerReports();
                registerNotifications();
                registerAccessManagement();
                registerRag();
                registerSystemAndFiles();
        }

        Optional<EndpointDoc> find(HandlerMethod handlerMethod) {
                return find(handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName());
        }

        Optional<EndpointDoc> find(String controllerName, String methodName) {
                return Optional.ofNullable(documents.get(key(controllerName, methodName)));
        }

        Map<String, EndpointDoc> all() {
                return Map.copyOf(documents);
        }

        private void registerAuth() {
                addPublic("AuthController", "login", "Xác thực", "Đăng nhập",
                                "Xác thực tài khoản và trả access token, refresh token, role cùng permission.",
                                "200", "Đăng nhập thành công", "LOGIN", "LOGIN_REQUEST", ResponseKind.JSON,
                                Set.of("401", "403"));
                add("AuthController", "logout", "Xác thực", "Đăng xuất",
                                "Thu hồi access token và refresh token của phiên hiện tại.",
                                "204", "Đăng xuất thành công, không có response body", null, "LOGOUT_REQUEST",
                                ResponseKind.VOID);
                addPublic("AuthController", "changePassword", "Xác thực", "Đổi mật khẩu",
                                "Đổi mật khẩu khi username và mật khẩu hiện tại hợp lệ.",
                                "200", "Mật khẩu đã được thay đổi", "STRING_SUCCESS", "CHANGE_PASSWORD_REQUEST",
                                ResponseKind.TEXT, Set.of());
                addPublic("AuthController", "forgotPassword", "Xác thực", "Yêu cầu đặt lại mật khẩu",
                                "Gửi OTP đặt lại mật khẩu; response không tiết lộ email có tồn tại hay không.",
                                "200", "Đã tiếp nhận yêu cầu", "STRING_SUCCESS", "FORGOT_PASSWORD_REQUEST",
                                ResponseKind.TEXT, Set.of());
                addPublic("AuthController", "resetPassword", "Xác thực", "Đặt lại mật khẩu",
                                "Đặt mật khẩu mới bằng email và OTP sáu chữ số còn hiệu lực.",
                                "200", "Mật khẩu đã được đặt lại", "STRING_SUCCESS", "RESET_PASSWORD_REQUEST",
                                ResponseKind.TEXT, Set.of());
        }

        private void registerDoctors() {
                add("DoctorController", "createDoctor", "Bác sĩ", "Tạo tài khoản bác sĩ",
                                "Admin tạo bác sĩ; username và mật khẩu ban đầu được hệ thống sinh.",
                                "201", "Tạo bác sĩ thành công", "DOCTOR", "CREATE_DOCTOR_REQUEST", ResponseKind.JSON);
                add("DoctorController", "editDoctor", "Bác sĩ", "Cập nhật bác sĩ",
                                "Admin cập nhật thông tin tài khoản và chuyên môn của bác sĩ theo ID.",
                                "200", "Thông tin bác sĩ sau cập nhật", "DOCTOR", "EDIT_DOCTOR_REQUEST",
                                ResponseKind.JSON);
                add("DoctorController", "getProfile", "Bác sĩ", "Xem hồ sơ của tôi",
                                "Trả hồ sơ của bác sĩ hoặc trưởng khoa đang đăng nhập.",
                                "200", "Hồ sơ hiện tại", "DOCTOR", null, ResponseKind.JSON);
                add("DoctorController", "editProfile", "Bác sĩ", "Cập nhật hồ sơ của tôi",
                                "Cập nhật các trường hồ sơ được gửi lên; trường bỏ trống được giữ nguyên.",
                                "200", "Hồ sơ sau cập nhật", "DOCTOR", "EDIT_PROFILE_REQUEST", ResponseKind.JSON);
                addMultipart("DoctorController", "updateProfileAvatar", "Bác sĩ", "Cập nhật ảnh đại diện",
                                "Tải ảnh từ máy và thay ảnh đại diện của tài khoản đang đăng nhập.",
                                "200", "Hồ sơ kèm avatarUrl mới", "DOCTOR", ResponseKind.JSON);
                add("DoctorController", "getDoctors", "Bác sĩ", "Tìm kiếm danh sách bác sĩ",
                                "Phân trang và lọc bác sĩ theo từ khóa, chuyên môn hoặc trạng thái.",
                                "200", "Trang kết quả bác sĩ", "DOCTOR_PAGE", null, ResponseKind.JSON);
                add("DoctorController", "getActiveDoctors", "Bác sĩ", "Lấy bác sĩ đang hoạt động",
                                "Trả danh sách bác sĩ có trạng thái ACTIVE.",
                                "200", "Danh sách bác sĩ hoạt động", "DOCTOR_LIST", null, ResponseKind.JSON);
                add("DoctorController", "activateDoctor", "Bác sĩ", "Kích hoạt bác sĩ",
                                "Admin chuyển tài khoản bác sĩ sang trạng thái hoạt động.",
                                "200", "Kích hoạt thành công, không có response body", null, null, ResponseKind.VOID);
                add("DoctorController", "deactivateDoctorPost", "Bác sĩ", "Vô hiệu hóa bác sĩ",
                                "Admin vô hiệu hóa mềm tài khoản bác sĩ bằng POST.",
                                "200", "Vô hiệu hóa thành công, không có response body", null, null, ResponseKind.VOID);
                add("DoctorController", "deactivateDoctor", "Bác sĩ", "Vô hiệu hóa bác sĩ bằng DELETE",
                                "Admin vô hiệu hóa mềm tài khoản; dữ liệu lịch sử không bị xóa vật lý.",
                                "200", "Vô hiệu hóa thành công, không có response body", null, null, ResponseKind.VOID);
        }

        private void registerUsers() {
                add("UserController", "createUser", "Người dùng", "Tạo người dùng",
                                "Admin tạo tài khoản theo role ID; không cho phép tạo thêm ADMIN qua endpoint này.",
                                "201", "Tạo người dùng thành công", "USER", "CREATE_USER_REQUEST", ResponseKind.JSON);
                add("UserController", "getStaffList", "Người dùng", "Lấy danh sách nhân sự y tế",
                                "Trả bác sĩ và trưởng khoa phục vụ phân công ca khám.",
                                "200", "Danh sách nhân sự y tế", "USER_LIST", null, ResponseKind.JSON);
                add("UserController", "countDoctors", "Người dùng", "Đếm số lượng bác sĩ",
                                "Trả về tổng số tài khoản bác sĩ trên hệ thống. Chỉ Trưởng khoa hoặc Admin mới có quyền truy cập.",
                                "200", "Tổng số bác sĩ", "LONG", null, ResponseKind.JSON);
                add("UserController", "countHeads", "Người dùng", "Đếm số lượng trưởng khoa",
                                "Trả về tổng số tài khoản trưởng khoa (và phó khoa) trên hệ thống. Chỉ Admin mới có quyền truy cập.",
                                "200", "Tổng số trưởng khoa", "LONG", null, ResponseKind.JSON);
        }

        private void registerPatients() {
                add("PatientController", "createPatient", "Bệnh nhân", "Tạo bệnh nhân",
                                "Tạo hồ sơ bệnh nhân mới trước khi tiếp nhận ca khám.",
                                "201", "Hồ sơ bệnh nhân đã tạo", "PATIENT", "CREATE_PATIENT_REQUEST",
                                ResponseKind.JSON);
                add("PatientController", "getAllPatients", "Bệnh nhân", "Tìm kiếm bệnh nhân",
                                "Phân trang và lọc bệnh nhân theo mã, tên, ngày sinh, giới tính hoặc liên hệ.",
                                "200", "Trang kết quả bệnh nhân", "PATIENT_PAGE", null, ResponseKind.JSON);
                add("PatientController", "editPatient", "Bệnh nhân", "Cập nhật bệnh nhân",
                                "Cập nhật thông tin hành chính và liên hệ của bệnh nhân theo ID.",
                                "200", "Hồ sơ bệnh nhân sau cập nhật", "PATIENT", "EDIT_PATIENT_REQUEST",
                                ResponseKind.JSON);
                add("PatientController", "deletePatient", "Bệnh nhân", "Xóa bệnh nhân",
                                "Trưởng khoa xóa bệnh nhân theo ID khi quy tắc dữ liệu cho phép.",
                                "200", "Xóa thành công, không có response body", null, null, ResponseKind.VOID);
                add("PatientController", "getPatientDetailsWithImages", "Bệnh nhân", "Xem chi tiết bệnh nhân",
                                "Trả hồ sơ bệnh nhân cùng các ca khám gần đây và URL hình ảnh liên quan.",
                                "200", "Chi tiết bệnh nhân", "PATIENT_DETAILS", null, ResponseKind.JSON);
                add("PatientController", "getPatientsByUploadDate", "Bệnh nhân", "Lọc bệnh nhân theo ngày tải lên",
                                "Trả bệnh nhân có dữ liệu được tải lên trong ngày; bác sĩ nhận dữ liệu theo phạm vi của mình.",
                                "200", "Trang bệnh nhân theo ngày", "PATIENT_PAGE", null, ResponseKind.JSON);
        }

        private void registerExaminations() {
                add("ExaminationController", "getAllExaminations", "Ca khám", "Lấy toàn bộ ca khám",
                                "Trưởng khoa xem danh sách ca khám toàn khoa có phân trang.", "200", "Trang ca khám",
                                "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsSortedByStudyDate", "Ca khám", "Sắp xếp theo ngày chụp",
                                "Sắp xếp ca khám trong phạm vi được phép theo studyDate tăng hoặc giảm.", "200",
                                "Trang ca khám đã sắp xếp", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsSortedByUploadDate", "Ca khám",
                                "Sắp xếp theo ngày tải lên",
                                "Sắp xếp ca khám trong phạm vi được phép theo thời điểm tạo.", "200",
                                "Trang ca khám đã sắp xếp", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsFilteredByStudyDate", "Ca khám", "Lọc theo ngày chụp",
                                "Lọc ca khám theo ngày studyDate định dạng yyyy-MM-dd.", "200",
                                "Trang ca khám theo ngày chụp", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsFilteredByUploadDate", "Ca khám", "Lọc theo ngày tải lên",
                                "Lọc ca khám theo ngày dữ liệu được tạo trên hệ thống.", "200",
                                "Trang ca khám theo ngày tải", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationById", "Ca khám", "Xem chi tiết ca khám",
                                "Trả patient, DICOM instance và AI result của ca khám nếu người dùng có quyền sở hữu.",
                                "200", "Chi tiết ca khám", "EXAMINATION", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsByDoctorId", "Ca khám", "Lấy ca khám theo bác sĩ",
                                "Bác sĩ chỉ xem danh sách của mình; trưởng khoa có thể xem theo bác sĩ bất kỳ.", "200",
                                "Trang ca khám của bác sĩ", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsByPatientId", "Ca khám", "Lấy ca khám theo bệnh nhân",
                                "Trưởng khoa lấy lịch sử ca khám theo patient ID.", "200",
                                "Trang ca khám của bệnh nhân", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsByPatientIdAndStudyMonth", "Ca khám",
                                "Lọc lịch sử khám theo tháng",
                                "Lọc ca khám của bệnh nhân theo tháng chụp yyyy-MM.", "200",
                                "Trang ca khám trong tháng", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsByStatus", "Ca khám", "Lọc ca khám theo trạng thái",
                                "Lọc theo trạng thái xử lý như NEED_VERIFY, VERIFIED hoặc REPORT_GENERATED.", "200",
                                "Trang ca khám theo trạng thái", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getExaminationsByGrade", "Ca khám", "Lọc ca khám theo KL grade",
                                "Lọc theo maxPredictedGrade từ 0 đến 4 trong phạm vi được phép.", "200",
                                "Trang ca khám theo KL grade", "EXAMINATION_PAGE", null, ResponseKind.JSON);
                add("ExaminationController", "getPatientGradeStatistics", "Ca khám", "Thống kê bệnh nhân theo KL grade",
                                "Đếm bệnh nhân theo kết quả KL mới nhất, hỗ trợ lọc khoảng ngày.", "200",
                                "Số bệnh nhân theo từng grade", "GRADE_STATS", null, ResponseKind.JSON);
                add("ExaminationController", "markAsViewed", "Ca khám", "Đánh dấu đã xem",
                                "Đánh dấu ca khám thuộc phạm vi người dùng là đã được mở xem.", "200",
                                "Cập nhật thành công, không có response body", null, null, ResponseKind.VOID);
                add("ExaminationController", "getTotalExaminations", "Ca khám", "Đếm tổng ca khám theo người dùng",
                                "Đếm ca khám theo userId; bác sĩ chỉ được hỏi dữ liệu của mình.", "200",
                                "Tổng số ca khám", "LONG", null, ResponseKind.JSON);
                add("ExaminationController", "getTotalSevereExaminations", "Ca khám", "Đếm ca KL nặng",
                                "Đếm ca có KL grade 3 hoặc 4 theo userId.", "200", "Số ca KL nặng", "LONG", null,
                                ResponseKind.JSON);
                add("ExaminationController", "getTotalVerifiedExaminations", "Ca khám", "Đếm ca đã xác nhận",
                                "Đếm ca có trạng thái VERIFIED theo userId.", "200", "Số ca đã xác nhận", "LONG", null,
                                ResponseKind.JSON);
                add("ExaminationController", "getTotalUnverifiedExaminations", "Ca khám", "Đếm ca chưa xác nhận",
                                "Đếm ca chưa ở trạng thái VERIFIED theo userId.", "200", "Số ca chưa xác nhận", "LONG",
                                null, ResponseKind.JSON);
                add("ExaminationController", "getMyTotalExaminations", "Ca khám", "Đếm tổng ca của tôi",
                                "Đếm toàn bộ ca khám của người dùng đang đăng nhập.", "200", "Tổng ca khám của tôi",
                                "LONG", null, ResponseKind.JSON);
                add("ExaminationController", "getMyTotalLast7Days", "Ca khám", "Đếm tổng ca 7 ngày của tôi",
                                "Đếm ca khám trong 7 ngày gần nhất của người dùng đang đăng nhập.", "200",
                                "Tổng ca khám 7 ngày của tôi", "LONG", null, ResponseKind.JSON);
                add("ExaminationController", "getMyTotalSevereExaminations", "Ca khám", "Đếm ca nặng của tôi",
                                "Đếm ca KL 3-4 của người dùng đang đăng nhập.", "200", "Số ca nặng của tôi", "LONG",
                                null, ResponseKind.JSON);
                add("ExaminationController", "getMyTotalVerifiedExaminations", "Ca khám", "Đếm ca đã xác nhận của tôi",
                                "Đếm ca VERIFIED của người dùng đang đăng nhập.", "200", "Số ca đã xác nhận của tôi",
                                "LONG", null, ResponseKind.JSON);
                add("ExaminationController", "getMyTotalUnverifiedExaminations", "Ca khám",
                                "Đếm ca chưa xác nhận của tôi",
                                "Đếm ca chưa VERIFIED của người dùng đang đăng nhập.", "200",
                                "Số ca chưa xác nhận của tôi", "LONG", null, ResponseKind.JSON);
        }

        private void registerDicom() {
                addMultipart("DicomController", "uploadDicomFile", "DICOM", "Đọc metadata DICOM",
                                "Tải một tệp DICOM để kiểm tra và trả các tag metadata; chưa tạo ca khám.", "200",
                                "Danh sách tag DICOM", "DICOM_TAGS", ResponseKind.JSON);
                addMultipart("DicomController", "uploadBatch", "DICOM", "Tải nhiều tệp DICOM",
                                "Kiểm tra nhiều tệp và tạo uploadSessionId để bác sĩ xác nhận trước khi lưu.", "200",
                                "Kết quả tiếp nhận batch", "BATCH_UPLOAD", ResponseKind.JSON);
                addMultipart("DicomController", "uploadZipBatch", "DICOM", "Tải gói ZIP DICOM",
                                "Giải nén, kiểm tra các tệp DICOM và tạo phiên xác nhận.", "200",
                                "Kết quả tiếp nhận ZIP", "BATCH_UPLOAD", ResponseKind.JSON);
                add("DicomController", "getTotalStudies", "DICOM", "Đếm study DICOM",
                                "Trả số StudyInstanceUID duy nhất trong phạm vi thống kê lâm sàng.", "200",
                                "Tổng số study", "LONG", null, ResponseKind.JSON);
                addNotFound("DicomController", "getUploadSession", "DICOM", "Xem phiên tải DICOM",
                                "Trả dữ liệu tạm của phiên upload nếu là người tải hoặc trưởng khoa.", "200",
                                "Nội dung upload session", "UPLOAD_SESSION", null, ResponseKind.JSON);
                addNotFound("DicomController", "getInstanceImage", "DICOM", "Xem ảnh PNG của DICOM",
                                "Trả ảnh PNG đã chuyển đổi của DICOM instance thuộc ca khám được phép.", "200",
                                "Dữ liệu ảnh PNG", null, null, ResponseKind.PNG);
                addNotFound("DicomController", "getInstanceRaw", "DICOM", "Tải tệp DICOM gốc",
                                "Trả tệp application/dicom gốc của instance thuộc ca khám được phép.", "200",
                                "Dữ liệu tệp DICOM", null, null, ResponseKind.DICOM);
                add("DicomVerifyController", "verifyUploadSession", "Xác nhận DICOM", "Xác nhận phiên DICOM",
                                "Lưu các bệnh nhân được chấp nhận và khởi chạy xử lý AI bất đồng bộ.", "200",
                                "Trạng thái tiếp nhận xử lý", "VERIFY_MESSAGE", "DICOM_VERIFY_REQUEST",
                                ResponseKind.JSON);
        }

        private void registerAiAndReviews() {
                add("AiController", "predictBatch", "Phân tích AI", "Chạy AI cho DICOM instances",
                                "Gửi các DICOM instance đang chờ sang AI và trả kết quả theo ca khám.", "200",
                                "Danh sách ca khám kèm AI result", "EXAMINATION_LIST", "AI_PREDICT_REQUEST",
                                ResponseKind.JSON);
                addNotFound("AiController", "getHeatmapImage", "Phân tích AI", "Xem ảnh Grad-CAM",
                                "Trả heatmap của AI result nếu người dùng có quyền với ca khám.", "200",
                                "Dữ liệu ảnh heatmap", null, null, ResponseKind.PNG);
                addNotFound("AiController", "getImage", "Phân tích AI", "Xem ảnh lâm sàng",
                                "Trả ảnh ROI, annotated hoặc ảnh liên quan theo image ID được phép.", "200",
                                "Dữ liệu ảnh", null, null, ResponseKind.PNG);
                add("DiagnosisReviewController", "confirmAiGrade", "Xác nhận chẩn đoán", "Xác nhận kết quả AI",
                                "Chọn kết quả AI làm KL grade cuối cùng cho một AI result.", "200",
                                "Quyết định xác nhận", "DIAGNOSIS_REVIEW", null, ResponseKind.JSON);
                add("DiagnosisReviewController", "adjustKlGrade", "Xác nhận chẩn đoán", "Điều chỉnh KL grade",
                                "Ghi KL grade bác sĩ chọn cùng ghi chú làm kết quả cuối cùng.", "200",
                                "Quyết định điều chỉnh", "DIAGNOSIS_REVIEW", "ADJUST_KL_REQUEST", ResponseKind.JSON);
        }

        private void registerReports() {
                add("ReportController", "generatePdfReport", "Báo cáo PDF", "Tạo báo cáo PDF",
                                "Tạo và lưu PDF từ kết quả cuối cùng của ca đã VERIFIED.", "200",
                                "Metadata và URL báo cáo", "REPORT", null, ResponseKind.JSON);
                add("ReportController", "previewReport", "Báo cáo PDF", "Xem trước báo cáo PDF",
                                "Trả PDF dạng inline để frontend hiển thị mà không ép tải xuống.", "200",
                                "Nội dung PDF inline", null, null, ResponseKind.PDF);
                add("ReportController", "downloadReport", "Báo cáo PDF", "Tải báo cáo PDF",
                                "Trả PDF dạng attachment để trình duyệt tải tệp về máy.", "200",
                                "Nội dung PDF tải xuống", null, null, ResponseKind.PDF);
        }

        private void registerNotifications() {
                add("NotificationController", "getAllNotifications", "Thông báo", "Lấy toàn bộ thông báo của tôi",
                                "Trả cả thông báo đã đọc và chưa đọc của tài khoản hiện tại, mới nhất trước.", "200",
                                "Danh sách thông báo", "NOTIFICATION_LIST", null, ResponseKind.JSON);
                add("NotificationController", "getUnreadNotifications", "Thông báo", "Lấy thông báo chưa đọc",
                                "Chỉ trả các thông báo chưa đọc của tài khoản hiện tại.", "200",
                                "Danh sách thông báo chưa đọc", "NOTIFICATION_LIST", null, ResponseKind.JSON);
                add("NotificationController", "markAsRead", "Thông báo", "Đánh dấu thông báo đã đọc",
                                "Đánh dấu một thông báo thuộc tài khoản hiện tại là đã đọc.", "200", "Xác nhận đã đọc",
                                "STRING_SUCCESS", null, ResponseKind.TEXT);
                add("NotificationController", "markAllAsRead", "Thông báo", "Đánh dấu tất cả thông báo đã đọc",
                                "Đánh dấu toàn bộ thông báo chưa đọc thuộc tài khoản hiện tại là đã đọc và trả số bản ghi được cập nhật.",
                                "200", "Số thông báo đã được cập nhật", "MARK_ALL_NOTIFICATIONS_READ", null,
                                ResponseKind.JSON);
                add("NotificationController", "sendTestNotification", "Thông báo", "Gửi thông báo kiểm thử",
                                "Admin lưu và phát một thông báo WebSocket đến đúng người dùng đích.", "200",
                                "Xác nhận gửi thông báo", "STRING_SUCCESS", "SEND_NOTIFICATION_REQUEST",
                                ResponseKind.TEXT);
        }

        private void registerAccessManagement() {
                add("RoleController", "getAllRoles", "Access management", "List roles",
                                "Return all roles for administrator role assignment and management.", "200",
                                "Role list", "ROLE_LIST", null, ResponseKind.JSON);
                add("PermissionController", "getPermissionTree", "Phân quyền", "Lấy cây permission",
                                "Trả feature và permission theo cấu trúc dùng cho màn hình phân quyền.", "200",
                                "Cây feature/permission", "FEATURE_LIST", null, ResponseKind.JSON);
                add("PermissionController", "getRolePermissions", "Phân quyền", "Lấy permission của role",
                                "Trả danh sách permission ID đang được gán cho role code.", "200",
                                "Danh sách permission ID", "PERMISSION_IDS", null, ResponseKind.JSON);
                add("PermissionController", "updateRolePermissions", "Phân quyền", "Cập nhật permission của role",
                                "Thay toàn bộ permission của role; không cho gán permission y tế cho ADMIN.", "200",
                                "Cập nhật thành công, không có response body", null, "ROLE_PERMISSIONS_REQUEST",
                                ResponseKind.VOID);
                add("PermissionController", "createPermission", "Phân quyền", "Tạo permission",
                                "Tạo permission mới trong một feature, có thể phụ thuộc permission khác.", "201",
                                "Permission đã tạo", "PERMISSION", "CREATE_PERMISSION_REQUEST", ResponseKind.JSON);
                add("PermissionController", "updatePermission", "Phân quyền", "Cập nhật permission",
                                "Cập nhật code, tên, thứ tự, presentation và dependency của permission.", "200",
                                "Permission sau cập nhật", "PERMISSION", "UPDATE_PERMISSION_REQUEST",
                                ResponseKind.JSON);
                add("PermissionController", "deletePermission", "Phân quyền", "Xóa permission",
                                "Gỡ quan hệ role/dependency rồi xóa permission theo ID.", "204",
                                "Xóa thành công, không có response body", null, null, ResponseKind.VOID);
                add("FeatureController", "createFeature", "Tính năng", "Tạo feature",
                                "Tạo nhóm chức năng dùng để tổ chức permission.", "201", "Feature đã tạo", "FEATURE",
                                "FEATURE_REQUEST", ResponseKind.JSON);
                add("FeatureController", "updateFeature", "Tính năng", "Cập nhật feature",
                                "Cập nhật tên và mô tả của nhóm chức năng.", "200", "Feature sau cập nhật", "FEATURE",
                                "FEATURE_REQUEST", ResponseKind.JSON);
                add("FeatureController", "deleteFeature", "Tính năng", "Xóa feature",
                                "Xóa feature cùng các permission và quan hệ phụ thuộc liên quan.", "204",
                                "Xóa thành công, không có response body", null, null, ResponseKind.VOID);
        }

        private void registerRag() {
                addMultipart("KnowledgeController", "upload", "Medical knowledge", "Upload medical knowledge",
                                "Upload one medical knowledge file for asynchronous indexing.", "202",
                                "Knowledge document accepted for indexing", "KNOWLEDGE_DOCUMENT", ResponseKind.JSON);
                addMultipart("KnowledgeController", "uploadBatch", "Medical knowledge", "Upload medical knowledge batch",
                                "Upload multiple medical knowledge files for asynchronous indexing.", "202",
                                "Knowledge document batch accepted for indexing", "KNOWLEDGE_BATCH", ResponseKind.JSON);
                add("KnowledgeController", "addUrl", "Medical knowledge", "Add knowledge URL",
                                "Register a knowledge source URL for asynchronous indexing.", "202",
                                "Knowledge URL accepted for indexing", "KNOWLEDGE_DOCUMENT", null, ResponseKind.JSON);
                add("KnowledgeController", "getAll", "Medical knowledge", "List knowledge documents",
                                "List uploaded and registered knowledge documents with their indexing status.", "200",
                                "Knowledge documents", "KNOWLEDGE_LIST", null, ResponseKind.JSON);
                add("KnowledgeController", "reindex", "Medical knowledge", "Reindex a knowledge document",
                                "Queue an existing knowledge document for indexing again.", "202",
                                "Knowledge document accepted for reindexing", "KNOWLEDGE_DOCUMENT", null, ResponseKind.JSON);
                add("KnowledgeController", "syncReport", "Medical knowledge", "Sync an approved report",
                                "Queue an approved examination report as a knowledge source.", "202",
                                "Report accepted for indexing", "KNOWLEDGE_DOCUMENT", null, ResponseKind.JSON);
                add("KnowledgeController", "delete", "Medical knowledge", "Delete a knowledge document",
                                "Delete a knowledge document and its indexed content.", "204",
                                "Knowledge document deleted", null, null, ResponseKind.VOID);
                add("ChatController", "ask", "AI chat", "Ask a medical question",
                                "Route a question inside a persisted conversation and return the generated answer.", "200",
                                "Chat answer", "CHAT_ANSWER", "CHAT_QUESTION", ResponseKind.JSON);
                add("ChatController", "createSession", "AI chat", "Create chat session",
                                "Create an owned conversation, optionally linked to an accessible examination.", "201",
                                "Created chat session", "CHAT_SESSION", "CREATE_CHAT_SESSION", ResponseKind.JSON);
                add("ChatController", "getSessions", "AI chat", "List chat sessions",
                                "List the current user's conversations by most recent activity.", "200",
                                "Chat session page", "CHAT_SESSION_PAGE", null, ResponseKind.JSON);
                add("ChatController", "getMessages", "AI chat", "Get chat messages",
                                "Read the ordered message history of an owned conversation.", "200",
                                "Chat message page", "CHAT_MESSAGE_PAGE", null, ResponseKind.JSON);
                add("ChatController", "updateSession", "AI chat", "Update chat session",
                                "Rename, close, or reopen an owned conversation.", "200",
                                "Updated chat session", "CHAT_SESSION", "UPDATE_CHAT_SESSION", ResponseKind.JSON);
        }

        private void registerSystemAndFiles() {
                add("AuditLogController", "getAuditLogs", "Nhật ký hệ thống", "Lấy audit log",
                                "Admin xem nhật ký thao tác có phân trang, mới nhất trước.", "200", "Trang audit log",
                                "AUDIT_PAGE", null, ResponseKind.JSON);
                addMultipart("FileUploadController", "uploadAvatar", "Tệp", "Tải tệp avatar",
                                "Lưu ảnh avatar và trả URL nội bộ; endpoint không tự gán ảnh vào profile.", "200",
                                "URL tệp đã lưu", "FILE_URL", ResponseKind.JSON);
                add("MailTestController", "sendTestEmail", "Kiểm thử email", "Gửi email kiểm thử",
                                "Gửi email văn bản bằng mail server đang cấu hình; chỉ dùng để kiểm tra môi trường.",
                                "200", "Kết quả gửi email", "STRING_SUCCESS", null, ResponseKind.TEXT);
                addMultipart("TestS3Controller", "testUpload", "Kiểm thử lưu trữ", "Kiểm thử upload S3",
                                "Admin tải một tệp lên S3 để kiểm tra cấu hình storage.", "200",
                                "URL do storage trả về", "S3_URL", ResponseKind.TEXT);
        }

        private void add(
                        String controller,
                        String method,
                        String tag,
                        String summary,
                        String description,
                        String successCode,
                        String successDescription,
                        String responseExample,
                        String requestExample,
                        ResponseKind responseKind) {
                put(controller, method, new EndpointDoc(
                                tag, summary, description, successCode, successDescription,
                                responseExample, requestExample, responseKind,
                                false, false, false, Set.of()));
        }

        private void addPublic(
                        String controller,
                        String method,
                        String tag,
                        String summary,
                        String description,
                        String successCode,
                        String successDescription,
                        String responseExample,
                        String requestExample,
                        ResponseKind responseKind,
                        Set<String> extraErrors) {
                put(controller, method, new EndpointDoc(
                                tag, summary, description, successCode, successDescription,
                                responseExample, requestExample, responseKind,
                                true, false, false, extraErrors));
        }

        private void addNotFound(
                        String controller,
                        String method,
                        String tag,
                        String summary,
                        String description,
                        String successCode,
                        String successDescription,
                        String responseExample,
                        String requestExample,
                        ResponseKind responseKind) {
                put(controller, method, new EndpointDoc(
                                tag, summary, description, successCode, successDescription,
                                responseExample, requestExample, responseKind,
                                false, true, false, Set.of()));
        }

        private void addMultipart(
                        String controller,
                        String method,
                        String tag,
                        String summary,
                        String description,
                        String successCode,
                        String successDescription,
                        String responseExample,
                        ResponseKind responseKind) {
                put(controller, method, new EndpointDoc(
                                tag, summary, description, successCode, successDescription,
                                responseExample, null, responseKind,
                                false, false, true, Set.of()));
        }

        private void put(String controller, String method, EndpointDoc document) {
                String key = key(controller, method);
                if (documents.putIfAbsent(key, document) != null) {
                        throw new IllegalStateException("Duplicate OpenAPI documentation: " + key);
                }
        }

        private String key(String controller, String method) {
                return controller + "#" + method;
        }
}
