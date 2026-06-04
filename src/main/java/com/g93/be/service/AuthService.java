package com.g93.be.service;

import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.exception.FirstTimeLoginException;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Service xử lý các tác vụ liên quan đến xác thực và phân quyền (Authentication).
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Thực hiện logic đăng nhập cho người dùng.
     * 
     * @param request Chứa username (email) và password của người dùng.
     * @return LoginResponse chứa accessToken, refreshToken, role và username.
     * @throws FirstTimeLoginException Nếu người dùng đăng nhập lần đầu tiên mà chưa đổi mật khẩu.
     */
    public LoginResponse login(LoginRequest request) {
        // 1. Xác thực thông tin đăng nhập từ client qua AuthenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        // 2. Lấy thông tin chi tiết người dùng sau khi xác thực thành công
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 3. Kiểm tra xem người dùng có đăng nhập lần đầu tiên (isFirstActivated = true) hay không
        // Nếu trường isFirstActivated là true, hệ thống sẽ ném ngoại lệ để yêu cầu đổi mật khẩu.
        if (Boolean.TRUE.equals(userDetails.getUser().getIsFirstActivated())) {
            throw new FirstTimeLoginException("Tài khoản chưa được kích hoạt/yêu cầu thay đổi mật khẩu trong lần đăng nhập đầu tiên.");
        }

        // 4. Tạo mã Access Token và Refresh Token nếu tài khoản hợp lệ và đã qua bước kiểm tra trên
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        // 5. Trả về thông tin đăng nhập đầy đủ cho client
        return new LoginResponse(
                accessToken,
                refreshToken,
                userDetails.getUser().getRole(),
                userDetails.getUsername()
        );
    }
}
