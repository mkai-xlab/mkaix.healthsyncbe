package com.g93.be.config.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

final class ApiExamples {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, Object> EXAMPLES = Map.ofEntries(
            Map.entry("LOGIN_REQUEST", json("""
                    {"username":"doctor01","password":"Doctor@123"}
                    """)),
            Map.entry("LOGIN", json("""
                    {
                      "accessToken":"eyJhbGciOiJIUzI1NiJ9.<access-token>",
                      "refreshToken":"eyJhbGciOiJIUzI1NiJ9.<refresh-token>",
                      "role":"DOCTOR",
                      "username":"doctor01",
                      "fullName":"BS. Nguyễn Minh An",
                      "permissions":[
                        {"id":9,"code":"VIEW_AI_RESULT","name":"Xem kết quả AI","priority":9,"presentation":null,"requiresPermissionId":null}
                      ]
                    }
                    """)),
            Map.entry("LOGOUT_REQUEST", json("""
                    {"refreshToken":"eyJhbGciOiJIUzI1NiJ9.<refresh-token>"}
                    """)),
            Map.entry("CHANGE_PASSWORD_REQUEST", json("""
                    {"username":"doctor01","oldPassword":"Doctor@123","newPassword":"Doctor@456"}
                    """)),
            Map.entry("FORGOT_PASSWORD_REQUEST", json("""
                    {"email":"doctor01@healthsync.vn"}
                    """)),
            Map.entry("RESET_PASSWORD_REQUEST", json("""
                    {"email":"doctor01@healthsync.vn","token":"482913","newPassword":"Doctor@456"}
                    """)),
            Map.entry("CREATE_DOCTOR_REQUEST", json("""
                    {
                      "fullName":"BS. Trần Thu Hà",
                      "email":"doctor02@healthsync.vn","phone":"0912345678",
                      "yearsOfExperience":8,"degree":"Bác sĩ chuyên khoa I"
                    }
                    """)),
            Map.entry("EDIT_DOCTOR_REQUEST", json("""
                    {
                      "fullName":"BS. Trần Thu Hà","email":"doctor02@healthsync.vn",
                      "phone":"0912345678","yearsOfExperience":9,
                      "degree":"Bác sĩ chuyên khoa II","biography":"Chuyên khoa Cơ xương khớp"
                    }
                    """)),
            Map.entry("EDIT_PROFILE_REQUEST", json("""
                    {
                      "fullName":"BS. Nguyễn Minh An","email":"doctor01@healthsync.vn",
                      "phone":"0901234567","yearsOfExperience":12,
                      "degree":"Bác sĩ chuyên khoa II","biography":"Chuyên khoa Chẩn đoán hình ảnh"
                    }
                    """)),
            Map.entry("DOCTOR", json("""
                    {
                      "id":7,"username":"doctor01","fullName":"BS. Nguyễn Minh An",
                      "email":"doctor01@healthsync.vn","phone":"0901234567",
                      "avatarUrl":"/api/v1/files/avatar/7","role":"DOCTOR","status":"ACTIVE",
                      "yearsOfExperience":12,"degree":"Bác sĩ chuyên khoa II",
                      "biography":"Chuyên khoa Chẩn đoán hình ảnh",
                      "createdAt":"2026-07-20T08:30:00","updatedAt":"2026-08-02T09:15:00"
                    }
                    """)),
            Map.entry("DOCTOR_LIST", json("""
                    [{"id":7,"username":"doctor01","fullName":"BS. Nguyễn Minh An","email":"doctor01@healthsync.vn","role":"DOCTOR","status":"ACTIVE","yearsOfExperience":12,"degree":"Bác sĩ chuyên khoa II"}]
                    """)),
            Map.entry("DOCTOR_PAGE", json("""
                    {
                      "content":[{"id":7,"username":"doctor01","fullName":"BS. Nguyễn Minh An","email":"doctor01@healthsync.vn","role":"DOCTOR","status":"ACTIVE","yearsOfExperience":12,"degree":"Bác sĩ chuyên khoa II"}],
                      "pageNumber":0,"pageSize":10,"totalElements":1,"totalPages":1,"isLast":true
                    }
                    """)),
            Map.entry("CREATE_USER_REQUEST", json("""
                    {"fullName":"Nguyễn Hải Yến","email":"staff01@healthsync.vn","phone":"0987654321","roleId":2}
                    """)),
            Map.entry("USER", json("""
                    {"id":12,"username":"staff01","fullName":"Nguyễn Hải Yến","email":"staff01@healthsync.vn","phone":"0987654321","role":{"id":2,"code":"DOCTOR","name":"Medical Doctor"},"status":"ACTIVE","userType":"DOCTOR","createdAt":"2026-08-02T08:00:00","updatedAt":null}
                    """)),
            Map.entry("USER_LIST", json("""
                    [{"id":7,"username":"doctor01","fullName":"BS. Nguyễn Minh An","email":"doctor01@healthsync.vn","role":{"id":2,"code":"DOCTOR","name":"Medical Doctor"},"status":"ACTIVE","userType":"DOCTOR","createdAt":"2026-07-20T08:30:00"}]
                    """)),
            Map.entry("CREATE_PATIENT_REQUEST", json("""
                    {
                      "patientCode":"PAT_A12B34CD","fullName":"Lê Văn Bình","dateOfBirth":"1968-04-15","gender":"MALE",
                      "phone":"0934567890","email":"binh.le@example.com","address":"Hà Nội",
                      "emergencyContactName":"Lê Thu Trang","emergencyContactPhone":"0977001122"
                    }
                    """)),
            Map.entry("EDIT_PATIENT_REQUEST", json("""
                    {"fullName":"Lê Văn Bình","phone":"0934567891","email":"binh.le@example.com","address":"Ba Đình, Hà Nội"}
                    """)),
            Map.entry("PATIENT", json("""
                    {
                      "id":21,"patientCode":"PAT_A12B34CD","patient_id":"PAT_A12B34CD",
                      "fullName":"Lê Văn Bình","dateOfBirth":"1968-04-15","gender":"MALE",
                      "phone":"0934567890","email":"binh.le@example.com","address":"Hà Nội",
                      "emergencyContactName":"Lê Thu Trang","emergencyContactPhone":"0977001122",
                      "createdAt":"2026-08-01T08:00:00","updatedAt":"2026-08-02T09:00:00"
                    }
                    """)),
            Map.entry("PATIENT_PAGE", json("""
                    {
                      "content":[{"id":21,"patientCode":"PAT_A12B34CD","fullName":"Lê Văn Bình","dateOfBirth":"1968-04-15","gender":"MALE","phone":"0934567890"}],
                      "pageNumber":0,"pageSize":10,"totalElements":24,"totalPages":3,"isLast":false
                    }
                    """)),
            Map.entry("EXAMINATION", json("""
                    {
                      "examinationId":42,"encounterCode":"ENC-2026-0042","status":"NEED_VERIFY",
                      "studyDate":"2026-08-01","studyTime":"09:30:00","visitTime":"2026-08-01T09:25:00",
                      "thumbnailUrl":"/api/v1/dicom/instances/101/image","referringPhysician":"BS. Phạm Quốc Huy",
                      "chiefComplaint":"Đau khớp gối phải","clinicalNotes":"Đau tăng khi vận động",
                      "priority":"NORMAL","finalDiagnosis":null,"patient":{"id":21,"patientCode":"PAT_A12B34CD","fullName":"Lê Văn Bình","gender":"MALE"},
                      "doctorId":7,"isViewed":0,"maxPredictedGrade":3,
                      "images":[{"dicomInstanceId":101,"imageUrl":"/api/v1/dicom/instances/101/image","aiResults":[{"aiResultId":19,"predictedGrade":3,"effectiveGrade":3,"confidence":0.94,"kneeSide":"RIGHT"}]}]
                    }
                    """)),
            Map.entry("EXAMINATION_PAGE", json("""
                    {
                      "content":[{"examinationId":42,"encounterCode":"ENC-2026-0042","status":"NEED_VERIFY","studyDate":"2026-08-01","doctorId":7,"maxPredictedGrade":3}],
                      "pageNumber":0,"pageSize":10,"totalElements":18,"totalPages":2,"isLast":false
                    }
                    """)),
            Map.entry("EXAMINATION_LIST", json("""
                    [{"examinationId":42,"encounterCode":"ENC-2026-0042","status":"NEED_VERIFY","studyDate":"2026-08-01","doctorId":7,"maxPredictedGrade":3}]
                    """)),
            Map.entry("PATIENT_DETAILS", json("""
                    {
                      "patient":{"id":21,"patientCode":"PAT_A12B34CD","fullName":"Lê Văn Bình","dateOfBirth":"1968-04-15","gender":"MALE"},
                      "recentExaminations":[{"examinationId":42,"encounterCode":"ENC-2026-0042","status":"NEED_VERIFY","studyDate":"2026-08-01","doctorId":7}]
                    }
                    """)),
            Map.entry("AI_PREDICT_REQUEST", json("""
                    {"dicomInstanceIds":[101,102]}
                    """)),
            Map.entry("DICOM_TAGS", json("""
                    [{"tagId":"0010,0020","tagName":"Patient ID","value":"PAT_A12B34CD"},{"tagId":"0008,0060","tagName":"Modality","value":"DX"}]
                    """)),
            Map.entry("BATCH_UPLOAD", json("""
                    {"message":"Đã tiếp nhận 2 tệp DICOM","uploadSessionId":"d9f4c260-3d24-4a21-a720-3fcbbf6e1c12","errors":[],"successfulPatients":[]}
                    """)),
            Map.entry("DICOM_VERIFY_REQUEST", json("""
                    {"uploadSessionId":"d9f4c260-3d24-4a21-a720-3fcbbf6e1c12","acceptedPatientCodes":["PAT_A12B34CD"]}
                    """)),
            Map.entry("VERIFY_MESSAGE", json("""
                    {"message":"Xác nhận thành công, hệ thống đang xử lý AI"}
                    """)),
            Map.entry("UPLOAD_SESSION", json("""
                    {"uploadSessionId":"d9f4c260-3d24-4a21-a720-3fcbbf6e1c12","uploaderUserId":7,"patients":{"PAT_A12B34CD":{"patientCode":"PAT_A12B34CD","files":["knee-right.dcm"]}}}
                    """)),
            Map.entry("ADJUST_KL_REQUEST", json("""
                    {"confirmedKlGrade":2,"reviewNote":"Hẹp khe khớp mức độ nhẹ, điều chỉnh KL từ 3 xuống 2"}
                    """)),
            Map.entry("DIAGNOSIS_REVIEW", json("""
                    {"reviewId":5,"aiResultId":19,"examinationId":42,"predictedKlGrade":3,"confirmedKlGrade":2,"decision":"DOCTOR_ADJUSTED","reviewNote":"Hẹp khe khớp mức độ nhẹ","reviewedByDoctorId":7,"reviewedAt":"2026-08-02T10:15:00"}
                    """)),
            Map.entry("REPORT", json("""
                    {"reportId":9,"examinationId":42,"fileName":"report_ENC-2026-0042_a1b2c3d4.pdf","fileSize":38832,"contentType":"application/pdf","generatedAt":"2026-08-02T10:30:00","previewUrl":"/api/v1/reports/42/preview","downloadUrl":"/api/v1/reports/42/download"}
                    """)),
            Map.entry("NOTIFICATION_LIST", json("""
                    [{"id":31,"title":"Kết quả AI đã sẵn sàng","message":"Ca khám ENC-2026-0042 cần được xác nhận","type":"AI_RESULT","isRead":false,"createdAt":"2026-08-02T10:00:00","data":{"examinationId":42}}]
                    """)),
            Map.entry("MARK_ALL_NOTIFICATIONS_READ", json("""
                    {"updatedCount":3}
                    """)),
            Map.entry("SEND_NOTIFICATION_REQUEST", json("""
                    {"userId":7,"title":"Kiểm thử thông báo","message":"Thông báo WebSocket hoạt động bình thường","type":"SYSTEM","data":{"source":"swagger"}}
                    """)),
            Map.entry("FEATURE_REQUEST", json("""
                    {"name":"Quản lý báo cáo","description":"Các quyền tạo, xem và tải báo cáo"}
                    """)),
            Map.entry("FEATURE", json("""
                    {"id":7,"name":"Reporting & Export","description":"Lập báo cáo kết quả","permissions":[{"id":12,"code":"GENERATE_PDF_REPORT","name":"Tạo báo cáo PDF","priority":12,"requiresPermissionId":null}]}
                    """)),
            Map.entry("FEATURE_LIST", json("""
                    [{"id":7,"name":"Reporting & Export","description":"Lập báo cáo kết quả","permissions":[{"id":12,"code":"GENERATE_PDF_REPORT","name":"Tạo báo cáo PDF","priority":12,"requiresPermissionId":null}]}]
                    """)),
            Map.entry("ROLE_PERMISSIONS_REQUEST", json("""
                    {"permissionIds":[1,2,3,14,15,22]}
                    """)),
            Map.entry("PERMISSION_IDS", json("""
                    [1,2,3,14,15,22]
                    """)),
            Map.entry("CREATE_PERMISSION_REQUEST", json("""
                    {"code":"VIEW_REPORT_ARCHIVE","name":"Xem kho báo cáo","priority":23,"presentation":"report_archive_page","featureId":7,"requiresPermissionId":12}
                    """)),
            Map.entry("UPDATE_PERMISSION_REQUEST", json("""
                    {"code":"VIEW_REPORT_ARCHIVE","name":"Xem kho báo cáo","priority":23,"presentation":"report_archive_page","requiresPermissionId":12}
                    """)),
            Map.entry("PERMISSION", json("""
                    {"id":23,"code":"VIEW_REPORT_ARCHIVE","name":"Xem kho báo cáo","priority":23,"presentation":"report_archive_page","requiresPermissionId":12}
                    """)),
            Map.entry("AUDIT_PAGE", json("""
                    {"content":[{"id":81,"username":"admin","title":"UPDATE_ROLE_PERMISSIONS","description":"Cập nhật quyền cho role DOCTOR","ipAddress":"127.0.0.1","userAgent":"Swagger UI","timeStamp":"2026-08-02T11:00:00"}],"pageNumber":0,"pageSize":20,"totalElements":1,"totalPages":1,"isLast":true}
                    """)),
            Map.entry("GRADE_STATS", json("""
                    [{"grade":0,"patientCount":8},{"grade":1,"patientCount":5},{"grade":2,"patientCount":4},{"grade":3,"patientCount":3},{"grade":4,"patientCount":1}]
                    """)),
            Map.entry("FILE_URL", json("""
                    {"url":"/api/v1/files/avatar/7"}
                    """)),
            Map.entry("STRING_SUCCESS", "Thao tác thành công"),
            Map.entry("LONG", 18),
            Map.entry("S3_URL", "https://storage.example.com/test/550e8400-e29b-41d4-a716-446655440000-knee.png")
    );

    private ApiExamples() {
    }

    static Object get(String key) {
        if (key == null) {
            return null;
        }
        Object example = EXAMPLES.get(key);
        if (example == null) {
            throw new IllegalArgumentException("Unknown OpenAPI example key: " + key);
        }
        return example;
    }

    private static Object json(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
