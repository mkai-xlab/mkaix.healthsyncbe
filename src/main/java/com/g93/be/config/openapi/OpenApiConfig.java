package com.g93.be.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    @Bean
    ApiDocumentationRegistry apiDocumentationRegistry() {
        return new ApiDocumentationRegistry();
    }

    @Bean
    public OpenAPI healthSyncOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Nhập access token nhận từ API đăng nhập. Không nhập tiền tố Bearer."))
                .addSchemas("ErrorResponse", errorSchema())
                .addSchemas("FirstTimeLoginError", firstTimeLoginSchema())
                .addResponses("BadRequest", badRequestResponse())
                .addResponses("Unauthorized", errorResponse(
                        "Chưa đăng nhập, access token thiếu, sai hoặc hết hạn",
                        Map.of(
                                "status", 401,
                                "error", "Unauthorized",
                                "message", "Invalid or expired token",
                                "timestamp", "2026-08-02T10:15:30")))
                .addResponses("Forbidden", errorResponse(
                        "Tài khoản đã đăng nhập nhưng không có role, permission hoặc quyền sở hữu dữ liệu",
                        Map.of(
                                "status", 403,
                                "error", "Forbidden",
                                "message", "Bạn không có quyền truy cập tính năng này (Access Denied).",
                                "timestamp", "2026-08-02T10:15:30")))
                .addResponses("FirstTimeLoginRequired", firstTimeLoginResponse())
                .addResponses("NotFound", new ApiResponse()
                        .description("Không tìm thấy tài nguyên hoặc tệp; response không có body"))
                .addResponses("UnsupportedMediaType", errorResponse(
                        "Content-Type không được endpoint hỗ trợ",
                        Map.of(
                                "status", 415,
                                "error", "Unsupported Media Type",
                                "message", "Content-Type 'application/json' is not supported",
                                "timestamp", "2026-08-02T10:15:30")))
                .addResponses("InternalServerError", errorResponse(
                        "Lỗi ngoài dự kiến khi xử lý yêu cầu",
                        Map.of(
                                "status", 500,
                                "error", "Internal Server Error",
                                "message", "An unexpected error occurred",
                                "timestamp", "2026-08-02T10:15:30")));

        return new OpenAPI()
                .info(new Info()
                        .title("HealthSync API")
                        .version("v1")
                        .description("API quản lý ca khám, DICOM, kết quả AI và báo cáo y khoa."))
                .components(components)
                .tags(List.of(
                        tag("Access management", "Manage roles and access assignments."),
                        tag("Medical knowledge", "Manage medical knowledge sources and indexing."),
                        tag("AI chat", "Ask business and medical questions through AI chat."),
                        tag("Xác thực", "Đăng nhập, đăng xuất và quản lý mật khẩu."),
                        tag("Bác sĩ", "Quản lý tài khoản và hồ sơ bác sĩ."),
                        tag("Người dùng", "Quản lý tài khoản người dùng."),
                        tag("Bệnh nhân", "Quản lý hồ sơ và danh sách bệnh nhân."),
                        tag("Ca khám", "Tra cứu, lọc và thống kê ca khám."),
                        tag("DICOM", "Tải lên, theo dõi và đọc dữ liệu DICOM."),
                        tag("Xác nhận DICOM", "Xác nhận phiên tải lên và khởi chạy phân tích AI."),
                        tag("Phân tích AI", "Chạy dự đoán và đọc ảnh kết quả AI."),
                        tag("Xác nhận chẩn đoán", "Xác nhận hoặc điều chỉnh KL grade."),
                        tag("Báo cáo PDF", "Tạo, xem trước và tải báo cáo ca khám."),
                        tag("Thông báo", "Đọc và gửi thông báo người dùng."),
                        tag("Phân quyền", "Quản lý permission theo role."),
                        tag("Tính năng", "Quản lý nhóm tính năng của permission."),
                        tag("Nhật ký hệ thống", "Tra cứu audit log."),
                        tag("Tệp", "Tải tệp dùng trong hồ sơ."),
                        tag("Kiểm thử email", "Kiểm tra mail server của môi trường."),
                        tag("Kiểm thử lưu trữ", "Kiểm tra kết nối storage.")));
    }

    @Bean
    public OperationCustomizer documentedOperationCustomizer(ApiDocumentationRegistry registry) {
        return (operation, handlerMethod) -> {
            ApiDocumentationRegistry.EndpointDoc document = registry.find(handlerMethod)
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing OpenAPI documentation for "
                                    + handlerMethod.getBeanType().getSimpleName() + "#"
                                    + handlerMethod.getMethod().getName()));

            String endpointKey = handlerMethod.getBeanType().getSimpleName()
                    + "#" + handlerMethod.getMethod().getName();
            operation.setOperationId(handlerMethod.getBeanType().getSimpleName()
                    + "_" + handlerMethod.getMethod().getName());
            operation.setTags(List.of(document.tag()));
            operation.setSummary(document.summary());
            operation.setDescription(document.description());
            operation.setSecurity(document.publicEndpoint()
                    ? List.of()
                    : List.of(new SecurityRequirement().addList(BEARER_AUTH)));

            customizeSuccessResponse(operation, document);
            customizeRequestExample(operation, document.requestExampleKey());
            customizeParameters(operation.getParameters(), endpointKey);
            addErrorResponses(operation, document, endpointKey);
            return operation;
        };
    }

    private void customizeSuccessResponse(
            io.swagger.v3.oas.models.Operation operation,
            ApiDocumentationRegistry.EndpointDoc document) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        ApiResponse success = responses.get(document.successCode());
        if (success == null && !"200".equals(document.successCode())) {
            success = responses.remove("200");
        }
        if (success == null) {
            success = new ApiResponse();
        }
        success.setDescription(document.successDescription());
        applySuccessContent(success, document);
        responses.addApiResponse(document.successCode(), success);
    }

    private void applySuccessContent(
            ApiResponse response,
            ApiDocumentationRegistry.EndpointDoc document) {
        if (document.responseKind() == ApiDocumentationRegistry.ResponseKind.VOID) {
            response.setContent(null);
            return;
        }

        if (document.responseKind() == ApiDocumentationRegistry.ResponseKind.JSON) {
            Content content = response.getContent() == null ? new Content() : response.getContent();
            MediaType mediaType = content.get("application/json");
            if (mediaType == null) {
                mediaType = new MediaType().schema(new ObjectSchema());
                content.addMediaType("application/json", mediaType);
            }
            mediaType.setExample(ApiExamples.get(document.responseExampleKey()));
            response.setContent(content);
            return;
        }

        String mediaTypeName;
        Schema<?> schema;
        switch (document.responseKind()) {
            case TEXT -> {
                mediaTypeName = "text/plain";
                schema = new StringSchema();
            }
            case PDF -> {
                mediaTypeName = "application/pdf";
                schema = new StringSchema().format("binary");
            }
            case PNG -> {
                mediaTypeName = "image/png";
                schema = new StringSchema().format("binary");
            }
            case DICOM -> {
                mediaTypeName = "application/dicom";
                schema = new StringSchema().format("binary");
            }
            default -> throw new IllegalStateException("Unsupported response kind: " + document.responseKind());
        }
        MediaType mediaType = new MediaType().schema(schema);
        if (document.responseExampleKey() != null) {
            mediaType.setExample(ApiExamples.get(document.responseExampleKey()));
        }
        response.setContent(new Content().addMediaType(mediaTypeName, mediaType));
    }

    private void customizeRequestExample(
            io.swagger.v3.oas.models.Operation operation,
            String exampleKey) {
        if (exampleKey == null || operation.getRequestBody() == null
                || operation.getRequestBody().getContent() == null) {
            return;
        }
        MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
        if (mediaType != null) {
            mediaType.setExample(ApiExamples.get(exampleKey));
        }
    }

    private void addErrorResponses(
            io.swagger.v3.oas.models.Operation operation,
            ApiDocumentationRegistry.EndpointDoc document,
            String endpointKey) {
        ApiResponses responses = operation.getResponses();
        if ("DicomController#uploadZipBatch".equals(endpointKey)) {
            responses.addApiResponse("400", zipUploadBadRequest());
        } else {
            responses.addApiResponse("400", reference("BadRequest"));
        }

        if (!document.publicEndpoint()) {
            responses.addApiResponse("401", reference("Unauthorized"));
            responses.addApiResponse("403", reference("Forbidden"));
        }
        for (String status : document.extraErrorResponses()) {
            String component = "403".equals(status)
                    ? "FirstTimeLoginRequired"
                    : "401".equals(status) ? "Unauthorized" : "BadRequest";
            responses.addApiResponse(status, reference(component));
        }
        if (document.notFoundResponse()) {
            responses.addApiResponse("404", reference("NotFound"));
        }
        if (document.unsupportedMediaType()) {
            responses.addApiResponse("415", reference("UnsupportedMediaType"));
        }
        responses.addApiResponse("500", "MailTestController#sendTestEmail".equals(endpointKey)
                ? plainTextMailError()
                : reference("InternalServerError"));
    }

    private void customizeParameters(List<Parameter> parameters, String endpointKey) {
        if (parameters == null) {
            return;
        }
        if ("AuthController#logout".equals(endpointKey)) {
            parameters.removeIf(parameter -> "Authorization".equalsIgnoreCase(parameter.getName()));
        }
        for (Parameter parameter : parameters) {
            if ("patientId".equals(parameter.getName())
                    && "PatientController#getPatientDetailsWithImages".equals(endpointKey)) {
                parameter.setDescription("Mã bệnh nhân");
                parameter.setExample("PAT_A12B34CD");
                continue;
            }
            ParameterDoc documentation = PARAMETER_DOCUMENTATION.get(parameter.getName());
            if (documentation != null) {
                parameter.setDescription(documentation.description());
                parameter.setExample(documentation.example());
            }
        }
    }

    private static final Map<String, ParameterDoc> PARAMETER_DOCUMENTATION = Map.ofEntries(
            Map.entry("id", new ParameterDoc("ID của tài nguyên", 42)),
            Map.entry("examinationId", new ParameterDoc("ID ca khám", 42)),
            Map.entry("aiResultId", new ParameterDoc("ID kết quả AI của một DICOM instance", 19)),
            Map.entry("imageId", new ParameterDoc("ID ảnh lâm sàng", 105)),
            Map.entry("doctorId", new ParameterDoc("ID bác sĩ", 7)),
            Map.entry("patientId", new ParameterDoc("ID bệnh nhân", 12)),
            Map.entry("roleName", new ParameterDoc("Mã role", "DOCTOR")),
            Map.entry("sessionId", new ParameterDoc("UUID phiên tải DICOM", "550e8400-e29b-41d4-a716-446655440000")),
            Map.entry("date", new ParameterDoc("Ngày theo định dạng yyyy-MM-dd", "2026-08-02")),
            Map.entry("year", new ParameterDoc("Năm cần lọc", 2026)),
            Map.entry("month", new ParameterDoc("Tháng từ 1 đến 12", 8)),
            Map.entry("direction", new ParameterDoc("Chiều sắp xếp: asc hoặc desc", "desc")),
            Map.entry("status", new ParameterDoc("Trạng thái ca khám", "NEED_VERIFY")),
            Map.entry("grade", new ParameterDoc("KL grade từ 0 đến 4", 3)),
            Map.entry("startDate", new ParameterDoc("Ngày bắt đầu yyyy-MM-dd", "2026-08-01")),
            Map.entry("endDate", new ParameterDoc("Ngày kết thúc yyyy-MM-dd", "2026-08-31")),
            Map.entry("userId", new ParameterDoc("ID người dùng", 7)),
            Map.entry("page", new ParameterDoc("Số trang, bắt đầu từ 0", 0)),
            Map.entry("size", new ParameterDoc("Số bản ghi mỗi trang", 10)),
            Map.entry("sort", new ParameterDoc("Thuộc tính và chiều sắp xếp", "createdAt,desc")),
            Map.entry("keyword", new ParameterDoc("Từ khóa tìm kiếm", "Nguyễn")),
            Map.entry("specialization", new ParameterDoc("Chuyên môn bác sĩ", "Cơ xương khớp")),
            Map.entry("folderName", new ParameterDoc("Thư mục đích trên storage", "test")),
            Map.entry("fileName", new ParameterDoc("Tên tệp muốn lưu", "knee.png")),
            Map.entry("to", new ParameterDoc("Địa chỉ email nhận", "doctor01@healthsync.vn")),
            Map.entry("title", new ParameterDoc("Tiêu đề email", "HealthSync SMTP test")),
            Map.entry("message", new ParameterDoc("Nội dung email", "Mail server hoạt động bình thường")));

    private static ApiResponse badRequestResponse() {
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                .addExamples("validation", new Example()
                        .summary("Sai validation")
                        .value(Map.of(
                                "status", 400,
                                "error", "Validation Error",
                                "message", "email: must be a well-formed email address",
                                "timestamp", "2026-08-02T10:15:30")))
                .addExamples("invalidParameter", new Example()
                        .summary("Sai kiểu tham số")
                        .value(Map.of(
                                "status", 400,
                                "error", "Bad Request",
                                "message", "Invalid parameter type for: examinationId",
                                "timestamp", "2026-08-02T10:15:30")))
                .addExamples("businessRule", new Example()
                        .summary("Không thỏa điều kiện nghiệp vụ")
                        .value(Map.of(
                                "status", 400,
                                "error", "Bad Request",
                                "message", "Examination must be verified before generating its report",
                                "timestamp", "2026-08-02T10:15:30")));
        return new ApiResponse()
                .description("Dữ liệu đầu vào hoặc điều kiện nghiệp vụ không hợp lệ")
                .content(new Content().addMediaType("application/json", mediaType));
    }

    private static ApiResponse errorResponse(String description, Object example) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                        .example(example)));
    }

    private static ApiResponse firstTimeLoginResponse() {
        return new ApiResponse()
                .description("Tài khoản đăng nhập lần đầu phải đổi mật khẩu")
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/FirstTimeLoginError"))
                        .example(Map.of(
                                "error", "FIRST_TIME_LOGIN_REQUIRED",
                                "message", "Please change your password before continuing"))));
    }

    private static ApiResponse zipUploadBadRequest() {
        return new ApiResponse()
                .description("ZIP rỗng, sai định dạng hoặc không chứa DICOM hợp lệ")
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new ObjectSchema()
                                .addProperty("error", new StringSchema())
                                .addProperty("status", new StringSchema()))
                        .example(Map.of(
                                "error", "ZIP file does not contain valid DICOM files",
                                "status", "FAILED"))));
    }

    private static ApiResponse plainTextMailError() {
        return new ApiResponse()
                .description("Mail server từ chối hoặc không thể gửi email")
                .content(new Content().addMediaType("text/plain", new MediaType()
                        .schema(new StringSchema())
                        .example("Gửi email thất bại. Lỗi: Authentication failed")));
    }

    private static ApiResponse reference(String componentName) {
        return new ApiResponse().$ref("#/components/responses/" + componentName);
    }

    private static Schema<?> errorSchema() {
        return new ObjectSchema()
                .addProperty("status", new IntegerSchema().format("int32").example(400))
                .addProperty("error", new StringSchema().example("Bad Request"))
                .addProperty("message", new StringSchema().example("Invalid request"))
                .addProperty("timestamp", new StringSchema().format("date-time").example("2026-08-02T10:15:30"));
    }

    private static Schema<?> firstTimeLoginSchema() {
        return new ObjectSchema()
                .addProperty("error", new StringSchema().example("FIRST_TIME_LOGIN_REQUIRED"))
                .addProperty("message", new StringSchema().example("Please change your password before continuing"));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private record ParameterDoc(String description, Object example) {
    }
}
