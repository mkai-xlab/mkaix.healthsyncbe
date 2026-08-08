package com.g93.be.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Canonical permission metadata and security classifications.
 */
public final class PermissionCatalog {

    public static final Map<String, String> VIETNAMESE_NAMES = vietnameseNames();

    public static final Set<String> CLINICAL_PERMISSION_CODES = Set.of(
            "READ_PATIENT_LIST",
            "CREATE_PATIENT_EXAM",
            "VIEW_PATIENT_DETAIL",
            "VIEW_IMAGE_LIST",
            "UPLOAD_DICOM_IMAGE",
            "TRIGGER_AI_ANALYSIS",
            "VIEW_AI_RESULT",
            "COMPARE_XAI_SIDE_BY_SIDE",
            "VIEW_ANALYTIC_HISTORY",
            "VIEW_PENDING_DIAGNOSIS",
            "ADD_CLINICAL_COMMENT",
            "OVERRIDE_AI_GRADE",
            "CONFIRM_CONCLUSION",
            "GENERATE_PDF_REPORT",
            "EXPORT_DOWNLOAD_PDF",
            "VIEW_DOCTOR_DASHBOARD");

    public static final Set<String> ADMIN_DEFAULT_PERMISSION_CODES = Set.of(
            "READ_OWN_PROFILE",
            "REQUEST_PASSWORD_RESET",
            "VIEW_USER_LIST",
            "MANAGE_USER_ROLE",
            "VIEW_ADMIN_DASHBOARD",
            "GENERATE_OPERATIONAL_REP",
            "USE_AI_CHAT",
            "MANAGE_MEDICAL_KNOWLEDGE");

    private PermissionCatalog() {
    }

    public static boolean isClinical(String permissionCode) {
        return CLINICAL_PERMISSION_CODES.contains(permissionCode);
    }

    private static Map<String, String> vietnameseNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("READ_OWN_PROFILE", "Xem hồ sơ cá nhân");
        names.put("REQUEST_PASSWORD_RESET", "Yêu cầu đặt lại mật khẩu");
        names.put("VIEW_USER_LIST", "Xem danh sách người dùng");
        names.put("READ_PATIENT_LIST", "Xem danh sách bệnh nhân");
        names.put("CREATE_PATIENT_EXAM", "Tạo bệnh nhân và ca khám");
        names.put("VIEW_IMAGE_LIST", "Xem danh sách hình ảnh");
        names.put("UPLOAD_DICOM_IMAGE", "Tải lên tệp DICOM");
        names.put("TRIGGER_AI_ANALYSIS", "Khởi chạy phân tích AI");
        names.put("VIEW_AI_RESULT", "Xem kết quả AI");
        names.put("VIEW_ANALYTIC_HISTORY", "Xem lịch sử phân tích");
        names.put("VIEW_PENDING_DIAGNOSIS", "Xem chẩn đoán chờ xác nhận");
        names.put("GENERATE_PDF_REPORT", "Tạo báo cáo PDF");
        names.put("VIEW_DOCTOR_DASHBOARD", "Xem trang tổng quan bác sĩ");
        names.put("VIEW_ADMIN_DASHBOARD", "Xem trang tổng quan quản trị");
        names.put("MANAGE_USER_ROLE", "Quản lý vai trò người dùng");
        names.put("VIEW_PATIENT_DETAIL", "Xem chi tiết bệnh nhân");
        names.put("COMPARE_XAI_SIDE_BY_SIDE", "So sánh XAI song song");
        names.put("ADD_CLINICAL_COMMENT", "Thêm nhận xét lâm sàng");
        names.put("OVERRIDE_AI_GRADE", "Điều chỉnh phân độ KL của AI");
        names.put("CONFIRM_CONCLUSION", "Xác nhận kết luận");
        names.put("EXPORT_DOWNLOAD_PDF", "Xuất và tải xuống PDF");
        names.put("GENERATE_OPERATIONAL_REP", "Tạo báo cáo vận hành");
        names.put("USE_AI_CHAT", "Su dung tro ly AI");
        names.put("MANAGE_MEDICAL_KNOWLEDGE", "Quan ly kho tri thuc y khoa");
        return Map.copyOf(names);
    }
}
