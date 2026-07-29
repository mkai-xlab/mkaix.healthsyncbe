package com.g93.be.security;

import com.g93.be.dto.PermissionResponse;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private CustomUserDetails mockUserDetails;

    // 256-bit (32 byte) keys base64 encoded for testing HS256
    private final String accessSecret = "aW52YWxpZEFjY2Vzc1NlY3JldEtleVN0cmluZ1RoYXRJc0xvbmcyNTZCaXRz"; 
    private final String refreshSecret = "aW52YWxpZFJlZnJlc2hTZWNyZXRLZXlTdHJpbmdUaGF0SXNMb25nMjU2Qml0cw=="; 
    
    // Set short expiration for testing some cases
    private final long accessExpirationMs = 3600000; // 1 hour
    private final long refreshExpirationMs = 86400000; // 1 day
    private final String issuer = "HealthSync";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                accessSecret,
                refreshSecret,
                accessExpirationMs,
                refreshExpirationMs,
                issuer
        );

        Role role = new Role();
        role.setCode("DOCTOR");

        User user = new User();
        user.setUsername("test_user");
        user.setFullName("Test User");
        user.setRole(role);

        PermissionResponse perm1 = new PermissionResponse(1L, "CREATE_PATIENT", "Create Patient", 1, "Create", null);
        PermissionResponse perm2 = new PermissionResponse(2L, "VIEW_REPORT", "View Report", 1, "View", null);
        List<PermissionResponse> permissions = List.of(perm1, perm2);

        mockUserDetails = new CustomUserDetails(user, permissions);
    }

    // --- ACCESS TOKEN TESTS ---

    @Test
    void generateAccessToken_and_ExtractUsername_Success() {
        String token = jwtTokenProvider.generateAccessToken(mockUserDetails);
        
        assertNotNull(token);
        String username = jwtTokenProvider.extractUsernameFromAccessToken(token);
        assertEquals("test_user", username);
    }

    @Test
    void generatedTokensAreUniquePerLoginSession() {
        String firstToken = jwtTokenProvider.generateAccessToken(mockUserDetails);
        String secondToken = jwtTokenProvider.generateAccessToken(mockUserDetails);

        assertNotEquals(firstToken, secondToken);
    }

    @Test
    void extractRoleFromAccessToken_Success() {
        String token = jwtTokenProvider.generateAccessToken(mockUserDetails);
        
        String role = jwtTokenProvider.extractRoleFromAccessToken(token);
        assertEquals("DOCTOR", role);
    }

    @Test
    void extractPermissionsFromAccessToken_Success() {
        String token = jwtTokenProvider.generateAccessToken(mockUserDetails);
        
        List<String> permissions = jwtTokenProvider.extractPermissionsFromAccessToken(token);
        assertNotNull(permissions);
        assertEquals(2, permissions.size());
        assertTrue(permissions.contains("CREATE_PATIENT"));
        assertTrue(permissions.contains("VIEW_REPORT"));
    }

    @Test
    void isAccessTokenValid_ValidToken_ReturnsTrue() {
        String token = jwtTokenProvider.generateAccessToken(mockUserDetails);
        
        boolean isValid = jwtTokenProvider.isAccessTokenValid(token, mockUserDetails);
        assertTrue(isValid);
    }

    @Test
    void isAccessTokenValid_InvalidUserDetails_ReturnsFalse() {
        String token = jwtTokenProvider.generateAccessToken(mockUserDetails);
        
        User wrongUser = new User();
        wrongUser.setUsername("wrong_user");
        CustomUserDetails wrongDetails = new CustomUserDetails(wrongUser, new ArrayList<>());

        boolean isValid = jwtTokenProvider.isAccessTokenValid(token, wrongDetails);
        assertFalse(isValid);
    }

    @Test
    void isAccessTokenValid_ExpiredToken_ReturnsFalse() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
                accessSecret,
                refreshSecret,
                1, // 1 ms expiration
                1, 
                issuer
        );

        String token = shortLivedProvider.generateAccessToken(mockUserDetails);
        
        Thread.sleep(10); // Wait for expiration
        
        boolean isValid = shortLivedProvider.isAccessTokenValid(token, mockUserDetails);
        assertFalse(isValid);
    }

    // --- REFRESH TOKEN TESTS ---

    @Test
    void generateRefreshToken_and_ExtractUsername_Success() {
        String token = jwtTokenProvider.generateRefreshToken(mockUserDetails);
        
        assertNotNull(token);
        String username = jwtTokenProvider.extractUsernameFromRefreshToken(token);
        assertEquals("test_user", username);
    }

    @Test
    void isRefreshTokenValid_ValidToken_ReturnsTrue() {
        String token = jwtTokenProvider.generateRefreshToken(mockUserDetails);
        
        boolean isValid = jwtTokenProvider.isRefreshTokenValid(token, mockUserDetails);
        assertTrue(isValid);
    }

    @Test
    void remainingValidityMatchesConfiguredTokenLifetime() {
        String accessToken = jwtTokenProvider.generateAccessToken(mockUserDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(mockUserDetails);

        long accessTtl = jwtTokenProvider.getAccessTokenRemainingValidity(accessToken).toMillis();
        long refreshTtl = jwtTokenProvider.getRefreshTokenRemainingValidity(refreshToken).toMillis();

        assertTrue(accessTtl > 0 && accessTtl <= accessExpirationMs);
        assertTrue(refreshTtl > 0 && refreshTtl <= refreshExpirationMs);
        assertTrue(jwtTokenProvider.isRefreshTokenValid(refreshToken));
    }

    @Test
    void isRefreshTokenValid_InvalidUserDetails_ReturnsFalse() {
        String token = jwtTokenProvider.generateRefreshToken(mockUserDetails);
        
        User wrongUser = new User();
        wrongUser.setUsername("wrong_user");
        CustomUserDetails wrongDetails = new CustomUserDetails(wrongUser, new ArrayList<>());

        boolean isValid = jwtTokenProvider.isRefreshTokenValid(token, wrongDetails);
        assertFalse(isValid);
    }

    @Test
    void isRefreshTokenValid_ExpiredToken_ReturnsFalse() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
                accessSecret,
                refreshSecret,
                1, 
                1, // 1 ms expiration
                issuer
        );

        String token = shortLivedProvider.generateRefreshToken(mockUserDetails);
        
        Thread.sleep(10); // Wait for expiration
        
        boolean isValid = shortLivedProvider.isRefreshTokenValid(token, mockUserDetails);
        assertFalse(isValid);
    }
}
