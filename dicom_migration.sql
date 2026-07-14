-- ==============================================================================
-- RUNBOOK: Migrate DICOM schema (Chạy lệnh này TRƯỚC khi khởi động lại Spring)
-- ==============================================================================

-- 1. Tạo bảng dicom_raws mới nếu chưa tồn tại
CREATE TABLE IF NOT EXISTS dicom_raws (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    extension VARCHAR(20),
    file_path VARCHAR(500),
    created_at DATETIME
);

-- 2. Di chuyển dữ liệu file '.dcm' từ bảng images sang bảng dicom_raws mới
INSERT INTO dicom_raws (extension, file_path, created_at)
SELECT extension, file_path, created_at
FROM images
WHERE extension = 'dcm';

-- 3. Xóa các bản ghi '.dcm' khỏi bảng images để tránh dư thừa dữ liệu
DELETE FROM images WHERE extension = 'dcm';

-- 4. Bổ sung các cột khóa ngoại vào bảng dicom_instances (Dùng để map OneToOne)
ALTER TABLE dicom_instances ADD COLUMN image_id BIGINT;
ALTER TABLE dicom_instances ADD COLUMN dicom_raw_id BIGINT;

-- 5. Cập nhật dữ liệu từ cột cũ (storage_png_path) sang khóa ngoại image_id
UPDATE dicom_instances d
JOIN images i ON d.storage_png_path = i.file_path
SET d.image_id = i.id
WHERE d.storage_png_path IS NOT NULL;

-- 6. Cập nhật dữ liệu từ cột cũ (storage_raw_path) sang khóa ngoại dicom_raw_id
UPDATE dicom_instances d
JOIN dicom_raws r ON d.storage_raw_path = r.file_path
SET d.dicom_raw_id = r.id
WHERE d.storage_raw_path IS NOT NULL;

-- 7. Thêm Ràng buộc khóa ngoại (Foreign Key Constraints)
ALTER TABLE dicom_instances
ADD CONSTRAINT fk_dicom_instances_image
FOREIGN KEY (image_id) REFERENCES images(id);

ALTER TABLE dicom_instances
ADD CONSTRAINT fk_dicom_instances_raw
FOREIGN KEY (dicom_raw_id) REFERENCES dicom_raws(id);

-- 8. Thêm cột body_part vào bảng dicom_instances
ALTER TABLE dicom_instances ADD COLUMN body_part VARCHAR(100);

-- 9. Cập nhật dữ liệu từ cột cũ (body_part) của examinations sang dicom_instances
UPDATE dicom_instances d
JOIN examinations e ON d.examination_id = e.id
SET d.body_part = e.body_part;

-- 10. Xóa các cột cũ không còn sử dụng để hoàn thiện schema
ALTER TABLE dicom_instances DROP COLUMN storage_png_path;
ALTER TABLE dicom_instances DROP COLUMN storage_raw_path;
ALTER TABLE examinations DROP COLUMN body_part;
ALTER TABLE examinations DROP COLUMN image_path;

-- ==============================================================================
-- HOÀN TẤT: Bây giờ bạn có thể khởi động lại ứng dụng Spring Boot an toàn!
-- ==============================================================================
