# Hướng Dẫn Code Authentication & Authorization

## 0. Giải Thích Tiếng Việt: Đọc Phần Này Trước

### 0.1 Authentication vs Authorization trong hệ thống này là gì?

Hai khái niệm tách biệt hoàn toàn trong code:

- **Authentication (xác thực)** = "bạn là ai" - xác minh username/password đúng, rồi phát JWT.
  Nằm trong `AuthController` → `AuthServiceImpl` → `JwtTokenProvider`.
- **Authorization (phân quyền)** = "bạn được làm gì" - mỗi request sau đó mang JWT, được đọc ra
  role/permission rồi so với `@PreAuthorize` trên từng endpoint. Nằm trong `JwtAuthenticationFilter`
  + `SecurityConfig` + `@PreAuthorize` rải khắp controller + `AccessControlService`.

Chi tiết mô hình permission (Role/Feature/Permission/RolePermission, `DataInitializer`,
`PermissionCatalog`) được tách sang tài liệu riêng: `docs/rbac-code-guide.md`. Tài liệu này chỉ
nói về việc *xác thực ai đó là ai* và *áp dụng* kết quả phân quyền ở tầng HTTP.

### 0.2 Luồng đăng nhập đầy đủ, từng bước

```
POST /auth/login {username, password}
  │
  ▼
AuthController.login()                       (permitAll - không cần token)
  │
  ▼
AuthServiceImpl.login()
  1. ensureAccountIsActive(username)          → user.status == INACTIVE thì 403 DisabledException
  2. loginAttemptService.ensureLoginAllowed()  → đang bị khoá (>=5 lần sai) thì 423 LoginLockedException
  3. authenticationManager.authenticate(UsernamePasswordAuthenticationToken(username, password))
       │
       ▼  (Spring Security tự điều phối, KHÔNG code tay)
     DaoAuthenticationProvider (đăng ký ở SecurityConfig.authenticationProvider())
       │
       ▼
     CustomUserDetailsService.loadUserByUsername(username)
       - tìm User theo username HOẶC email (userRepository.findByUsernameOrEmail)
       - load toàn bộ Permission của role đó (rolePermissionRepository.findPermissionsByRoleCode)
       - trả về CustomUserDetails(user, permissions)
       │
       ▼
     PasswordEncoder.matches(rawPassword, user.password)  → sai thì BadCredentialsException
       │
       ▼
     trả Authentication với principal = CustomUserDetails
  4. sai mật khẩu → loginAttemptService.recordFailedAttempt(); đủ 5 lần → khoá 15 phút
  5. đúng mật khẩu → loginAttemptService.resetFailedAttempts()
  6. userDetails.getUser().getIsFirstActivated() == true → 428 FirstTimeLoginException
     (bắt buộc đổi mật khẩu trước, chưa cấp token)
  7. jwtTokenProvider.generateAccessToken(userDetails)   → nhúng role, fullName, permissions vào claim
  8. jwtTokenProvider.generateRefreshToken(userDetails)  → chỉ có subject (username), không claim
  9. trả LoginResponse(accessToken, refreshToken, role, username, fullName, permissions)
```

Điểm quan trọng: **permission được nhúng thẳng vào JWT access token lúc đăng nhập**, không đọc
lại DB ở mỗi request sau đó. Đây là lý do vì sao đổi quyền của 1 role (qua màn Quản lý phân quyền)
không có tác dụng ngay với những ai đã đăng nhập từ trước - họ phải đăng nhập lại để nhận JWT mới
có claim `permissions` cập nhật.

### 0.3 Luồng một request bình thường sau khi đã đăng nhập (KHÔNG phải login)

```
Bất kỳ request nào có header Authorization: Bearer <accessToken>
  │
  ▼
JwtAuthenticationFilter.doFilterInternal()     (chạy trước UsernamePasswordAuthenticationFilter)
  1. resolveToken(request)     → lấy từ header Authorization, hoặc query param ?token= CHỈ với
                                  3 route xem file knowledge-document (để nhúng vào <img>/<iframe>)
  2. tokenBlacklistService.isAccessTokenBlacklisted(jwt)  → true (đã logout) thì bỏ qua, không set auth
  3. jwtTokenProvider.extractUsernameFromAccessToken(jwt) → đọc subject, KHÔNG verify chữ ký ở bước này
  4. jwtTokenProvider.isAccessTokenValid(jwt)             → verify chữ ký + hạn dùng, mới thật sự tin token
  5. Đọc role + permissions THẲNG TỪ CLAIM của JWT (không query DB nào cả)
  6. Dựng UsernamePasswordAuthenticationToken(username, null, authorities)
     → principal ở đây LÀ MỘT String (username), KHÔNG PHẢI CustomUserDetails
  7. SecurityContextHolder.getContext().setAuthentication(authToken)
  │
  ▼
Spring Security's FilterSecurityInterceptor đánh giá @PreAuthorize của method sắp gọi
  │
  ▼
Controller method chạy, có thể tự đọc SecurityContextHolder.getContext().getAuthentication()
để lấy username hiện tại - PHẢI dùng authentication.getName(), KHÔNG ép kiểu getPrincipal()
(xem 0.4, đây chính là bug thật đã xảy ra trong dự án).
```

Vì bước 3-6 **không hề gọi CustomUserDetailsService/DB** - toàn bộ thông tin authorization của
1 request thường lấy thẳng từ JWT đã ký, đúng tinh thần "stateless". `CustomUserDetails` chỉ xuất
hiện trong **đúng 1 khoảnh khắc**: lúc `DaoAuthenticationProvider` xử lý request `/auth/login`.

### 0.4 Bài học thật: vì sao không được ép kiểu `(String) authentication.getPrincipal()`

Đây là lỗi thật đã xảy ra trên `ExaminationController` (5 endpoint `/my-total*`), gây
`ClassCastException: CustomUserDetails cannot be cast to String` (500) khi gọi API.

Nguyên nhân: principal của `Authentication` **không cố định kiểu** trong toàn hệ thống -
- Trong luồng request bình thường (mục 0.3): principal là `String` (set bởi `JwtAuthenticationFilter`).
- Trong luồng login (mục 0.2 bước 3): principal là `CustomUserDetails` (set bởi `DaoAuthenticationProvider`
  gọi `CustomUserDetailsService`), như `AuthServiceImpl.java:99` đang cast đúng.

Code nào ép kiểu thẳng `(String) authentication.getPrincipal()` là đặt cược vào việc principal
LUÔN LUÔN là String - đúng ở luồng 0.3 nhưng sai nếu có bất kỳ thay đổi/luồng khác nào khiến
`Authentication` được set với `CustomUserDetails`. Cách đúng, dùng nhất quán trong `PatientController`
và giờ cả `ExaminationController`:

```java
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
String username = authentication.getName();   // an toàn với MỌI loại principal
```

`Authentication.getName()` là accessor chuẩn của Spring Security: tự nhận diện principal là
`String` hay `UserDetails` và trả về đúng username trong cả hai trường hợp
(`AbstractAuthenticationToken.getName()`).

### 0.5 Logout hoạt động thế nào (stateless nhưng vẫn thu hồi được token)?

JWT theo thiết kế không thể "xoá" - token vẫn hợp lệ về mặt chữ ký cho tới khi hết hạn tự nhiên.
Hệ thống giả lập thu hồi bằng **danh sách đen (blacklist) trong Redis**:

```
POST /auth/logout  (Authorization: Bearer <accessToken>, body: {refreshToken})
  │
  ▼
AuthController.logout()
  - Yêu cầu isAuthenticated() - phải có JWT hợp lệ mới logout được
  - Đọc accessToken từ header, refreshToken từ body, username từ Principal
  │
  ▼
AuthServiceImpl.logout(accessToken, refreshToken, username)
  1. Validate cả 2 token còn hạn (isAccessTokenValid / isRefreshTokenValid)
  2. Đối chiếu username trong CẢ 2 token phải khớp với username của người gọi
     → chặn A dùng token hợp lệ của A để logout hộ/phá refreshToken của B
  3. tokenBlacklistService.blacklistAccessToken(token, remainingValidity)
  4. tokenBlacklistService.blacklistRefreshToken(token, remainingValidity)
     → mỗi token được hash SHA-256 làm key Redis, TTL = đúng thời gian còn lại của token
       (không lưu blacklist vĩnh viễn - hết hạn tự nhiên thì Redis tự xoá key)
```

Vì `JwtAuthenticationFilter` luôn kiểm tra `tokenBlacklistService.isAccessTokenBlacklisted(jwt)`
**trước khi** set Authentication (mục 0.3 bước 2), 1 access token đã logout sẽ không set được
SecurityContext nữa dù chữ ký/hạn dùng vẫn hợp lệ → request tiếp theo bị coi như chưa đăng nhập
→ `.anyRequest().authenticated()` chặn lại với 401/403.

### 0.6 Quên mật khẩu / đặt lại mật khẩu (OTP qua email)

```
POST /auth/forgot-password {email}
  → AuthServiceImpl.forgotPassword()
  1. Tìm user theo email; KHÔNG tồn tại thì return NGAY, không báo lỗi
     (chống email enumeration - kẻ tấn công không đoán được email nào có tài khoản)
  2. Sinh OTP 6 chữ số (SecureRandom), hết hạn sau 10 phút
  3. Lưu/ghi đè PasswordResetToken (1 user chỉ có 1 token hiệu lực tại 1 thời điểm)
  4. Gửi email template "reset-password" qua MailUtil

POST /auth/reset-password {email, token, newPassword}
  → AuthServiceImpl.resetPassword()
  1. Tìm user theo email, tìm PasswordResetToken theo user
  2. So khớp token y hệt request.token(), kiểm tra chưa hết hạn (10 phút)
  3. Đổi mật khẩu (mã hoá lại), reset luôn login-lock (failedLoginAttempts=0, loginLockedUntil=null)
  4. Nếu là first-time-login thì bật cờ đã kích hoạt (isFirstActivated=false)
  5. Xoá PasswordResetToken sau khi dùng - token OTP chỉ dùng được 1 lần
```

Cả 2 endpoint đều `@PreAuthorize("permitAll()")` - hợp lý vì người quên mật khẩu, theo định nghĩa,
không đăng nhập được để mang JWT.

### 0.7 Chống brute-force: khoá đăng nhập sau 5 lần sai

`LoginAttemptService` (không liên quan JWT, thuần DB, dùng pessimistic lock để tránh race
condition khi 2 request sai mật khẩu gần như đồng thời):

```
MAX_FAILED_ATTEMPTS = 5, LOCK_DURATION = 15 phút

ensureLoginAllowed(identifier)   → gọi ĐẦU luồng login, trước cả khi kiểm tra mật khẩu
  - findByLoginIdentifierForUpdate(identifier)  [SELECT ... FOR UPDATE - khoá row]
  - đang trong thời gian khoá (loginLockedUntil > now) → ném LoginLockedException ngay,
    KHÔNG cho thử xác thực mật khẩu nữa (tránh lộ oracle "mật khẩu đúng nhưng đang khoá")
  - hết hạn khoá tự nhiên → tự xoá cờ khoá, cho thử lại

recordFailedAttempt(identifier)  → gọi khi authenticationManager.authenticate() ném BadCredentialsException
  - +1 vào failedLoginAttempts
  - đạt 5 → set loginLockedUntil = now + 15 phút, trả về Optional chứa thời điểm hết khoá
  - AuthServiceImpl bắt Optional này và ném LoginLockedException NGAY LẦN SAI THỨ 5
    (không đợi lần thứ 6 mới báo khoá)

resetFailedAttempts(identifier)  → gọi khi đăng nhập ĐÚNG - xoá sạch bộ đếm
```

`identifier` chấp nhận cả username lẫn email (cùng cách `CustomUserDetailsService` tra cứu), vì
`findByLoginIdentifierForUpdate` tra theo cả 2 cột.

### 0.8 Bốn dạng lỗi HTTP đặc thù của auth, `GlobalExceptionHandler` map ra sao

| Exception | HTTP | Khi nào |
| --- | --- | --- |
| `BadCredentialsException` | 401 | Sai username/password (Spring Security tự ném) |
| `DisabledException` | 403, code `ACCOUNT_DEACTIVATED` | `user.status == INACTIVE` |
| `LoginLockedException` | 423 Locked, code `LOGIN_TEMPORARILY_LOCKED` | Đang trong 15 phút khoá sau 5 lần sai |
| `FirstTimeLoginException` | 428, code `FIRST_TIME_LOGIN_REQUIRED` | Tài khoản chưa từng đổi mật khẩu lần đầu |

### 0.9 `SecurityConfig` - bản đồ toàn bộ filter chain

```java
// src/main/java/com/g93/be/config/SecurityConfig.java:41-65
.csrf(csrf -> csrf.disable())                          // API thuần JSON, không dùng cookie session -> CSRF không áp dụng
.sessionManagement(... STATELESS)                       // không tạo/dùng HttpSession, mọi state nằm trong JWT
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // preflight CORS
        .requestMatchers("/auth/login", "/auth/change-password",
                "/auth/forgot-password", "/auth/reset-password",
                "/ws", "/test-stomp.html",
                "/swagger-ui/**", "/v3/api-docs/**", "/error")
        .permitAll()
        .anyRequest().authenticated())                  // MẶC ĐỊNH: mọi endpoint khác cần JWT hợp lệ
.authenticationProvider(authenticationProvider())        // DaoAuthenticationProvider, chỉ dùng lúc login
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

Lưu ý: `/auth/change-password` nằm trong danh sách `permitAll()` ở tầng `SecurityConfig`, nhưng
bản thân `AuthServiceImpl.changePassword()` vẫn tự xác minh `oldPassword` đúng trước khi cho đổi -
không phải "ai gọi cũng đổi được mật khẩu của bất kỳ ai", chỉ là không cần JWT để gọi endpoint này
(người dùng gọi bằng `username` + `oldPassword` trong body, không qua SecurityContext).

`@PreAuthorize("permitAll()")`/`"isAuthenticated()"` trên từng method của `AuthController` là lớp
khai báo thứ hai (method-level, nhờ `@EnableMethodSecurity`) - về ý nghĩa trùng với danh sách ở
`SecurityConfig`, giữ cho rõ ràng ngay tại chỗ định nghĩa endpoint.

### 0.10 Cách dùng phần còn lại của tài liệu

Phần 1 trở xuống là source đối chiếu, liệt kê nguyên văn từng file quan trọng theo đúng dòng thật
trong repo tại thời điểm viết tài liệu này. Đọc mục 0 trước để nắm luồng, rồi mở IDE tới đúng
dòng khi cần hiểu chi tiết 1 đoạn code cụ thể.

## 1. Danh sách file theo trách nhiệm

| File | Vai trò |
| --- | --- |
| `controller/AuthController.java` | HTTP layer cho login/logout/change-password/forgot-password/reset-password |
| `service/impl/AuthServiceImpl.java` | Toàn bộ business logic authentication |
| `service/LoginAttemptService.java` | Đếm và khoá đăng nhập sai (chống brute-force) |
| `service/TokenBlacklistService.java` | Thu hồi token qua Redis (dùng cho logout) |
| `security/JwtTokenProvider.java` | Sinh/giải mã/verify JWT (JJWT library) |
| `security/JwtAuthenticationFilter.java` | Filter đọc JWT mỗi request, set SecurityContext (stateless) |
| `security/CustomUserDetailsService.java` | Cầu nối User entity ↔ Spring Security, chỉ chạy lúc login |
| `security/CustomUserDetails.java` | `UserDetails` implementation, bọc `User` + danh sách permission |
| `security/AccessControlService.java` | SpEL bean `@accessControl` cho các rule phân quyền theo ownership (xem `docs/rbac-code-guide.md`) |
| `config/SecurityConfig.java` | Khai báo filter chain, CORS, PasswordEncoder, AuthenticationProvider |
| `entity/PasswordResetToken.java` | OTP đặt lại mật khẩu |
| `exception/LoginLockedException.java`, `FirstTimeLoginException.java` | Exception riêng cho 2 tình huống auth đặc thù |

## 2. `AuthController.java` - toàn văn có chú thích

```java
// src/main/java/com/g93/be/controller/AuthController.java
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;   // interface, implementation duy nhất là AuthServiceImpl

    @PostMapping("/login")
    @PreAuthorize("permitAll()")             // trùng lặp có chủ đích với SecurityConfig, xem 0.9
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")       // BẮT BUỘC có JWT hợp lệ mới logout được
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request,
            Principal principal) {           // Spring MVC tự inject Principal = Authentication hiện tại
        authService.logout(
                extractBearerToken(authorizationHeader),
                request.refreshToken(),
                principal.getName());        // getName() - không ép kiểu getPrincipal(), xem 0.4
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer access token is required");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Bearer access token is required");
        }
        return token;
    }

    @PostMapping("/change-password")
    @PreAuthorize("permitAll()")             // không cần JWT - tự xác thực bằng oldPassword, xem 0.9
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PostMapping("/forgot-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // Thông báo GIỐNG HỆT dù email có tồn tại hay không - chống email enumeration
        return ResponseEntity.ok("If the email exists, a password reset token has been sent.");
    }

    @PostMapping("/reset-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Password reset successfully");
    }
}
```

## 3. `AuthServiceImpl.login()` - annotated đầy đủ

```java
// src/main/java/com/g93/be/service/impl/AuthServiceImpl.java:75-119
@Override
public LoginResponse login(LoginRequest request) {
    ensureAccountIsActive(request.username());          // 403 nếu status == INACTIVE
    loginAttemptService.ensureLoginAllowed(request.username());  // 423 nếu đang bị khoá

    Authentication authentication;
    try {
        // Giao toàn bộ việc xác thực cho Spring Security - KHÔNG tự so sánh password ở đây.
        // authenticationManager (bean AuthenticationManager) nội bộ gọi đúng 1
        // AuthenticationProvider đã đăng ký: authenticationProvider() trong SecurityConfig
        // (DaoAuthenticationProvider), provider đó tự gọi CustomUserDetailsService rồi
        // PasswordEncoder.matches(...).
        authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    } catch (BadCredentialsException exception) {
        // Sai mật khẩu: ghi nhận lần sai, NẾU đây là lần thứ 5 thì recordFailedAttempt trả về
        // Optional có giá trị -> báo khoá NGAY thay vì để BadCredentialsException (401) đi tiếp.
        loginAttemptService.recordFailedAttempt(request.username())
                .ifPresent(lockedUntil -> { throw new LoginLockedException(lockedUntil); });
        throw exception;    // chưa đủ 5 lần -> vẫn là lỗi sai mật khẩu bình thường (401)
    }

    loginAttemptService.resetFailedAttempts(request.username());   // đúng mật khẩu -> xoá bộ đếm

    // Đây là NƠI DUY NHẤT trong toàn bộ luồng request-thường mà principal chắc chắn là
    // CustomUserDetails - vì Authentication này vừa được DaoAuthenticationProvider tạo ra,
    // không phải JwtAuthenticationFilter (so sánh với mục 0.3/0.4).
    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

    if (Boolean.TRUE.equals(userDetails.getUser().getIsFirstActivated())) {
        throw new FirstTimeLoginException("Account not activated or requires password change on first login.");
    }

    // Permission được đọc 1 LẦN DUY NHẤT ở đây (từ CustomUserDetails, vốn đã load sẵn trong
    // CustomUserDetailsService.loadUserByUsername) rồi đóng băng vào claim JWT - xem 0.2.
    String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
    String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

    return new LoginResponse(
            accessToken, refreshToken,
            userDetails.getUser().getRole().getCode(),
            userDetails.getUsername(),
            userDetails.getUser().getFullName(),
            userDetails.getPermissions());
}
```

## 4. `JwtTokenProvider.java` - sinh và đọc token

```java
// src/main/java/com/g93/be/security/JwtTokenProvider.java:50-63
public String generateAccessToken(CustomUserDetails userDetails) {
    Map<String, Object> extraClaims = new HashMap<>();
    extraClaims.put("role", userDetails.getUser().getRole().getCode());       // vd "DOCTOR"
    extraClaims.put("fullName", userDetails.getUser().getFullName());
    extraClaims.put("permissions", userDetails.getPermissions());             // List<PermissionResponse> đầy đủ
    return buildToken(extraClaims, userDetails.getUsername(), accessExpirationMs, accessKey);
}

public String generateRefreshToken(CustomUserDetails userDetails) {
    // KHÔNG claim gì thêm ngoài subject - refresh token chỉ dùng để cấp lại access token mới,
    // không tự nó mang quyền hạn nào (client không dùng refreshToken để gọi API nghiệp vụ).
    return buildToken(new HashMap<>(), userDetails.getUsername(), refreshExpirationMs, refreshKey);
}
```

`accessKey`/`refreshKey` là 2 `SecretKey` HMAC **khác nhau** (`app.jwt.access-secret` /
`app.jwt.refresh-secret`), nên 1 access token không thể bị dùng nhầm/giả làm refresh token dù
cấu trúc JWT giống nhau - verify sai key sẽ ném `JwtException` ngay ở bước `Jwts.parser().verifyWith(key)`.

`extractPermissionsFromAccessToken()` (dòng 98-117) xử lý 2 dạng dữ liệu có thể có trong claim
`permissions` sau khi JSON serialize/deserialize qua JJWT: `List<Map>` (mỗi phần tử có key `code`)
hoặc `List<String>` thuần - phòng trường hợp định dạng claim đổi giữa các phiên bản token cũ/mới
vẫn đọc được `code` permission ra đúng.

## 5. `JwtAuthenticationFilter.java` - toàn văn có chú thích

```java
// src/main/java/com/g93/be/security/JwtAuthenticationFilter.java:44-90
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
    final String jwt = resolveToken(request);
    if (jwt == null) {
        filterChain.doFilter(request, response);   // không có token -> đi tiếp KHÔNG set Authentication;
        return;                                     // .anyRequest().authenticated() sẽ tự chặn (401) nếu cần
    }

    try {
        if (tokenBlacklistService.isAccessTokenBlacklisted(jwt)) {
            filterChain.doFilter(request, response);   // đã logout -> coi như không có token, xem 0.5
            return;
        }
        String userEmail = jwtTokenProvider.extractUsernameFromAccessToken(jwt);
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtTokenProvider.isAccessTokenValid(jwt)) {   // verify chữ ký + hạn dùng
                List<String> permissions = jwtTokenProvider.extractPermissionsFromAccessToken(jwt);
                String role = jwtTokenProvider.extractRoleFromAccessToken(jwt);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (role != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                if (permissions != null) {
                    permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                }

                // principal = userEmail (String) - KHÔNG PHẢI CustomUserDetails, xem 0.3/0.4
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userEmail, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
    } catch (Exception e) {
        // Nuốt mọi lỗi giải mã token (hết hạn, sai chữ ký, malformed...) - KHÔNG set Authentication,
        // để request đi tiếp và bị chặn tự nhiên bởi .anyRequest().authenticated() (401/403) thay vì
        // filter tự trả lỗi 500 ở đây.
    }

    filterChain.doFilter(request, response);
}

private String resolveToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        return authHeader.substring(7);
    }
    // Ngoại lệ DUY NHẤT: cho phép token qua query param ?token= để FE gắn thẳng URL vào
    // <img>/<iframe>/tab mới cho 3 route xem file knowledge-document (không gửi được header ở đó).
    String queryToken = request.getParameter("token");
    if (queryToken != null && !queryToken.isBlank()
            && QUERY_TOKEN_ALLOWED_PATH.matcher(request.getRequestURI()).matches()) {
        return queryToken;
    }
    return null;
}
```

`SecurityContextHolder.getContext().getAuthentication() == null` ở dòng set token là điều kiện
quan trọng: nếu vì lý do gì đó Authentication ĐÃ được set trước khi filter này chạy (ví dụ chuỗi
filter thay đổi trong tương lai), filter sẽ **không ghi đè** - giữ nguyên authentication đã có.
Đây cũng là lý do lý thuyết duy nhất khiến principal có thể không phải String tại 1 request thường:
nếu một cơ chế khác chạy trước và set `CustomUserDetails` vào SecurityContext trước khi tới đây.

## 6. `CustomUserDetailsService` / `CustomUserDetails` - cầu nối `User` ↔ Spring Security

```java
// src/main/java/com/g93/be/security/CustomUserDetailsService.java:24-37
@Override
public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
    User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
            .orElseThrow(() -> new UsernameNotFoundException(
                    "User not found with username or email: " + usernameOrEmail));
    // Load TOÀN BỘ permission của role NGAY LÚC NÀY (đúng 1 query JOIN qua RolePermission) -
    // đây là snapshot sẽ được đóng băng vào JWT claim, không đọc lại tới lần đăng nhập kế tiếp.
    List<Permission> perms = rolePermissionRepository.findPermissionsByRoleCode(user.getRole().getCode());
    List<PermissionResponse> permissions = perms.stream()
            .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getPriority(),
                    p.getPresentation(),
                    p.getRequiresPermission() != null ? p.getRequiresPermission().getId() : null))
            .collect(Collectors.toList());
    return new CustomUserDetails(user, permissions);
}
```

```java
// src/main/java/com/g93/be/security/CustomUserDetails.java:32-38, 42-45, 60-63
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));  // "ROLE_DOCTOR"
    getPermissionCodes().forEach(p -> authorities.add(new SimpleGrantedAuthority(p))); // "GENERATE_PDF_REPORT",...
    return authorities;
}

@Override
public String getUsername() {
    return user.getUsername();   // đây là điều khiến Authentication.getName() luôn đúng, xem 0.4
}

@Override
public boolean isEnabled() {
    return user.getStatus() == UserStatus.ACTIVE;   // Spring Security tự chặn nếu false (nhưng
}                                                     // login đã tự chặn sớm hơn ở ensureAccountIsActive()
```

`getAuthorities()` gộp CHUNG role và permission vào cùng 1 danh sách `GrantedAuthority` (role có
tiền tố `ROLE_`, permission thì không) - đây là lý do `@PreAuthorize` viết được cả `hasRole('DOCTOR')`
lẫn `hasAuthority('GENERATE_PDF_REPORT')` trên cùng 1 cơ chế, không cần 2 danh sách riêng. Xem
`docs/rbac-code-guide.md` để hiểu Permission/Role model và cách `@PreAuthorize` dùng 2 thứ này.

## 7. `TokenBlacklistService.java` - Redis TTL thay vì DB

```java
// src/main/java/com/g93/be/service/TokenBlacklistService.java:41-51
private void blacklist(String prefix, String token, Duration remainingValidity) {
    long ttlMillis = remainingValidity.toMillis();
    if (ttlMillis <= 0) {
        return;    // token đã hết hạn tự nhiên - không cần blacklist nữa, tiết kiệm 1 write Redis
    }
    redisTemplate.opsForValue().set(
            prefix + hash(token),   // key = SHA-256(token), KHÔNG lưu token gốc trong Redis
            "revoked",
            ttlMillis,
            TimeUnit.MILLISECONDS); // TTL = đúng thời gian còn lại -> Redis tự dọn, không cần job cleanup riêng
}
```

Băm token trước khi dùng làm key vừa tránh lưu bí mật (JWT) trực tiếp trong Redis, vừa giữ độ dài
key cố định bất kể JWT dài ngắn thế nào.

## 8. Test bảo vệ hành vi này

- `AuthServiceImplTest` - login thành công/sai mật khẩu/khoá tài khoản/first-time-login.
- `LoginAttemptServiceTest` - đúng ngưỡng 5 lần, đúng thời gian khoá 15 phút, reset đúng lúc.
- `JwtTokenProviderTest` - sinh/verify/hết hạn/sai chữ ký.
- `JwtAuthenticationFilterTest` (nếu có) hoặc test qua `MockMvc` với JWT thật trong các
  `*IntegrationTest` (`DashboardAnalyticsIntegrationTest` là ví dụ dùng `jwtTokenProvider.generateAccessToken(...)`
  thật rồi gọi API qua `MockMvc` - cách xác thực đáng tin cậy nhất vì đi qua đúng filter chain thật).
- `ExaminationControllerTest.setupSecurityContext()` - minh hoạ cách mock `Authentication` đúng
  (`getName()`, không phải `getPrincipal()`) sau bug fix mô tả ở mục 0.4.
