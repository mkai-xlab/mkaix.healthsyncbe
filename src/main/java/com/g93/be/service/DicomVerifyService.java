package com.g93.be.service;

import com.g93.be.dto.DicomVerifyRequest;

public interface DicomVerifyService {
    void verifySession(DicomVerifyRequest request);
}
