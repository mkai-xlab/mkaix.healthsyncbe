package com.g93.be.exception;

/**
 * Exception được ném ra khi người dùng đăng nhập lần đầu tiên mà chưa đổi mật khẩu.
 */
public class FirstTimeLoginException extends RuntimeException {
    public FirstTimeLoginException(String message) {
        super(message);
    }
}
