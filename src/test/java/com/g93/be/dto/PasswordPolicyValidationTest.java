package com.g93.be.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void changePassword_AcceptsStrongPassword() {
        ChangePasswordRequest minimumLength = new ChangePasswordRequest(
                "doctor.one", "CurrentPassword@1", "Test@123");
        ChangePasswordRequest maximumLength = new ChangePasswordRequest(
                "doctor.one", "CurrentPassword@1", "Password@12345678901234567890123");

        assertTrue(validator.validate(minimumLength).isEmpty());
        assertTrue(validator.validate(maximumLength).isEmpty());
    }

    @Test
    void changePassword_RejectsMissingRequiredCharacterTypes() {
        assertInvalid(new ChangePasswordRequest("doctor.one", "old", "newpassword@1"));
        assertInvalid(new ChangePasswordRequest("doctor.one", "old", "NewPassword@"));
        assertInvalid(new ChangePasswordRequest("doctor.one", "old", "NewPassword12"));
    }

    @Test
    void changePassword_RejectsPasswordOutsideLengthBoundary() {
        assertInvalid(new ChangePasswordRequest("doctor.one", "old", "Ab@1234"));
        assertInvalid(new ChangePasswordRequest(
                "doctor.one", "old", "Ab@123456789012345678901234567890"));
    }

    @Test
    void resetPassword_UsesSameStrongPasswordPolicy() {
        ResetPasswordRequest valid = new ResetPasswordRequest(
                "doctor.one@healthsync.vn", "123456", "NewPassword@2");
        ResetPasswordRequest invalid = new ResetPasswordRequest(
                "doctor.one@healthsync.vn", "123456", "newpassword");

        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(validator.validate(invalid).isEmpty());
    }

    private void assertInvalid(ChangePasswordRequest request) {
        assertFalse(validator.validate(request).isEmpty());
    }
}
