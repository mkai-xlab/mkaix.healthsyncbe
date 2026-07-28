package com.g93.be.mapper;

import com.g93.be.dto.DoctorResponse;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.Image;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DoctorMapperTest {

    private final DoctorMapper doctorMapper = new DoctorMapper();

    @Test
    void toResponseReturnsCurrentUserAvatarUrl() {
        Image avatar = new Image();
        avatar.setFilePath("https://cdn.example.com/avatar/doctor.jpg");
        Doctor doctor = new Doctor();
        doctor.setAvatar(avatar);

        DoctorResponse response = doctorMapper.toResponse(doctor);

        assertEquals(avatar.getFilePath(), response.getAvatarUrl());
    }

    @Test
    void toResponseReturnsNullAvatarUrlWhenUserHasNoAvatar() {
        DoctorResponse response = doctorMapper.toResponse(new Doctor());

        assertNull(response.getAvatarUrl());
    }
}
