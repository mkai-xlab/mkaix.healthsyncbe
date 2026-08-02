package com.g93.be;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class DatabaseCleanupTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void cleanupDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs;");
        jdbcTemplate.execute("TRUNCATE TABLE notifications;");
        jdbcTemplate.execute("TRUNCATE TABLE dicom_instances;");
        jdbcTemplate.execute("TRUNCATE TABLE examinations;");
        jdbcTemplate.execute("TRUNCATE TABLE patients;");
        jdbcTemplate.execute("TRUNCATE TABLE doctors;");
        jdbcTemplate.execute("TRUNCATE TABLE admins;");
        jdbcTemplate.execute("TRUNCATE TABLE users;");
        jdbcTemplate.execute("TRUNCATE TABLE permissions;");
        jdbcTemplate.execute("TRUNCATE TABLE roles;");
        jdbcTemplate.execute("TRUNCATE TABLE features;");
        jdbcTemplate.execute("TRUNCATE TABLE role_permissions;");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");
        System.out.println("DATABASE CLEANED SUCCESSFULLY!");
    }
}
