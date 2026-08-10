package com.g93.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.service.UserService;
import com.g93.be.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private CreateUserRequest createUserRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        createUserRequest = new CreateUserRequest();
        createUserRequest.setEmail("testuser@example.com");
        createUserRequest.setFullName("Test User");
        createUserRequest.setRoleId(2L);
        createUserRequest.setPhone("0123456789"); // 2: DOCTOR

        userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setEmail("testuser@example.com");
        userResponse.setFullName("Test User");
    }

    // --- createUser Tests ---

    @Test
    void testCreateUser_Normal() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("testuser@example.com"));
    }

    @Test
    void testCreateUser_Abnormal_NoBody() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()); // GlobalExceptionHandler maps unhandled HttpMessageNotReadableException to 400
    }

    // --- Validation Tests ---

    @Test
    void testCreateUser_Email_Null() throws Exception {
        createUserRequest.setEmail(null);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_Email_Blank() throws Exception {
        createUserRequest.setEmail("   ");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_Email_InvalidFormat() throws Exception {
        createUserRequest.setEmail("invalid-email-format");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_Email_TooLong() throws Exception {
        createUserRequest.setEmail("a".repeat(140) + "@example.com"); // > 150 chars
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_FullName_Null() throws Exception {
        createUserRequest.setFullName(null);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_FullName_Blank() throws Exception {
        createUserRequest.setFullName("   ");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_FullName_TooLong() throws Exception {
        createUserRequest.setFullName("a".repeat(101));
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_Phone_InvalidFormat() throws Exception {
        createUserRequest.setPhone("090abcd123");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_Phone_TooLong() throws Exception {
        createUserRequest.setPhone("1".repeat(21));
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUser_RoleId_Null() throws Exception {
        createUserRequest.setRoleId(null);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }
}
