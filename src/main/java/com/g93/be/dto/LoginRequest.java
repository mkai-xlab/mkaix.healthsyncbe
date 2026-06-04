package com.g93.be.dto;

/**
 * DTO chứa thông tin đăng nhập của người dùng.
 * username ở đây tương ứng với email đăng ký.
 */
public record LoginRequest(
    String username,
    String password
) {}
