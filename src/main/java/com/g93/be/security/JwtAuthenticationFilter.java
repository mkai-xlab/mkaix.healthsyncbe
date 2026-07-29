package com.g93.be.security;

import com.g93.be.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Filter thực hiện trích xuất và kiểm tra JWT token từ request header.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            if (tokenBlacklistService.isAccessTokenBlacklisted(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }
            userEmail = jwtTokenProvider.extractUsernameFromAccessToken(jwt);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Stateless validation: Extract authorities from JWT directly
                if (jwtTokenProvider.isAccessTokenValid(jwt)) {
                    List<String> permissions = jwtTokenProvider.extractPermissionsFromAccessToken(jwt);
                    String role = jwtTokenProvider.extractRoleFromAccessToken(jwt);

                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (role != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    if (permissions != null) {
                        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Cho phép filter tiếp tục xử lý, SecurityContextHolder không được set authentication sẽ tự động trả về 401/403.
        }

        filterChain.doFilter(request, response);
    }
}
