package com.g93.be.security;

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
import com.g93.be.entity.UserStatus;
import com.g93.be.service.TokenBlacklistService;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Filter thực hiện trích xuất và kiểm tra JWT token từ request header.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Cho phép truyền token qua query param `token` (thay vì header Authorization) CHỈ với các
     * route trả file knowledge-document, để FE có thể gắn thẳng URL vào iframe/img/tab mới
     * (những nơi không gửi được custom header). Không áp dụng cho các route khác.
     */
    private static final Pattern QUERY_TOKEN_ALLOWED_PATH =
            Pattern.compile(".*/knowledge-documents/\\d+/(preview|content|download)$");

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            CustomUserDetailsService userDetailsService,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final String jwt = resolveToken(request);
        final String userEmail;

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (tokenBlacklistService.isAccessTokenBlacklisted(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }
            userEmail = jwtTokenProvider.extractUsernameFromAccessToken(jwt);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtTokenProvider.isAccessTokenValid(jwt)) {
                    CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(userEmail);
                    if (userDetails.getUser().getStatus() == UserStatus.ACTIVE) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (Exception e) {
            // Allow filter chain to proceed, request will fail authorization check in Spring Security
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()
                && QUERY_TOKEN_ALLOWED_PATH.matcher(request.getRequestURI()).matches()) {
            return queryToken;
        }

        return null;
    }
}
