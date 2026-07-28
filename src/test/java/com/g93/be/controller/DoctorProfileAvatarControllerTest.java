package com.g93.be.controller;

import com.g93.be.dto.DoctorResponse;
import com.g93.be.service.DoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorProfileAvatarControllerTest {

    @Test
    void uploadsAvatarForAuthenticatedDoctorAndReturnsUpdatedProfile() {
        DoctorService doctorService = mock(DoctorService.class);
        DoctorController controller = new DoctorController(doctorService);
        Principal principal = () -> "doctor.one";
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        DoctorResponse expected = new DoctorResponse();
        expected.setAvatarUrl("/api/v1/files/avatars/7/avatar.png");
        when(doctorService.updateDoctorAvatar("doctor.one", file)).thenReturn(expected);

        ResponseEntity<DoctorResponse> response = controller.updateProfileAvatar(principal, file);

        assertSame(expected, response.getBody());
        verify(doctorService).updateDoctorAvatar("doctor.one", file);
    }
}
