CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_key VARCHAR(160) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_url VARCHAR(2048) NULL,
    original_name VARCHAR(255) NULL,
    content_type VARCHAR(150) NULL,
    storage_path VARCHAR(512) NULL,
    checksum VARCHAR(64) NULL,
    access_scope VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    uploaded_by_user_id BIGINT NULL,
    chunk_count INT NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    indexed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_documents_source_key (source_key),
    KEY idx_knowledge_documents_status (status),
    KEY idx_knowledge_documents_uploader (uploaded_by_user_id),
    CONSTRAINT fk_knowledge_documents_uploader
        FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id)
        ON DELETE SET NULL
);

INSERT INTO features (name, description)
SELECT 'AI Chatbox & Medical Knowledge', 'Tro ly AI va kho tri thuc y khoa'
WHERE NOT EXISTS (SELECT 1 FROM features WHERE name = 'AI Chatbox & Medical Knowledge');

INSERT INTO permissions (code, name, priority, feature_id)
SELECT 'USE_AI_CHAT', 'Su dung tro ly AI', 23, f.id
FROM features f
WHERE f.name = 'AI Chatbox & Medical Knowledge'
  AND NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USE_AI_CHAT');

INSERT INTO permissions (code, name, priority, feature_id, requires_permission_id)
SELECT 'MANAGE_MEDICAL_KNOWLEDGE', 'Quan ly kho tri thuc y khoa', 24, f.id, p.id
FROM features f
JOIN permissions p ON p.code = 'USE_AI_CHAT'
WHERE f.name = 'AI Chatbox & Medical Knowledge'
  AND NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'MANAGE_MEDICAL_KNOWLEDGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('USE_AI_CHAT', 'MANAGE_MEDICAL_KNOWLEDGE')
WHERE r.code IN ('ADMIN', 'DOCTOR', 'HEAD_OF_DEPARTMENT', 'DEPARTMENT_HEAD')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'MANAGE_MEDICAL_KNOWLEDGE'
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
