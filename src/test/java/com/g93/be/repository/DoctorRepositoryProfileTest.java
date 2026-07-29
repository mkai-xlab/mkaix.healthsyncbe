package com.g93.be.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;
import com.g93.be.entity.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DoctorRepositoryProfileTest {

    @Test
    void profileQueryFetchesAvatarBeforeRepositorySessionCloses() throws NoSuchMethodException {
        assertFetchesDoctorResponseRelations(
                DoctorRepository.class.getMethod("findProfileByUsername", String.class));
        assertFetchesDoctorResponseRelations(
                DoctorRepository.class.getMethod("findDetailsById", Long.class));
        assertFetchesDoctorResponseRelations(DoctorRepository.class.getMethod("findAll"));
        assertFetchesDoctorResponseRelations(
                DoctorRepository.class.getMethod("findAllByStatus", UserStatus.class));
        assertFetchesDoctorResponseRelations(
                DoctorRepository.class.getMethod("findAll", Specification.class, Pageable.class));
    }

    private void assertFetchesDoctorResponseRelations(Method method) {
        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);
        assertNotNull(entityGraph, method.getName() + " must define an EntityGraph");
        assertArrayEquals(new String[] {"avatar", "role"}, entityGraph.attributePaths());
    }
}
