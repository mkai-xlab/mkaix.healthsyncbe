-- Script đồng bộ Database nhánh dev với nhánh update/redis
-- Các thay đổi này dựa trên những cập nhật entity ở nhánh update/redis

-- 1. Thêm các cột bị thiếu vào bảng examinations
-- (Sử dụng IF NOT EXISTS nếu MySQL phiên bản >= 8.0.16, nếu bản cũ hơn bạn có thể bỏ IF NOT EXISTS)
ALTER TABLE examinations ADD COLUMN IF NOT EXISTS is_viewed TINYINT DEFAULT 0;
ALTER TABLE examinations ADD COLUMN IF NOT EXISTS max_predicted_grade INT;

-- 2. Đảm bảo bảng ai_result_confidence_score tồn tại (Trong trường hợp db dev chưa chạy migration cho entity này)
CREATE TABLE IF NOT EXISTS ai_result_confidence_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ai_result_id BIGINT NOT NULL,
    c0_confidence DOUBLE,
    c1_confidence DOUBLE,
    c2_confidence DOUBLE,
    c3_confidence DOUBLE,
    c4_confidence DOUBLE,
    CONSTRAINT fk_ai_result_confidence_score_ai_result FOREIGN KEY (ai_result_id) REFERENCES ai_result(id)
);
