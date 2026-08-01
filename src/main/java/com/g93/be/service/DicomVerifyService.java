package com.g93.be.service;

import com.g93.be.dto.DicomVerifyRequest;

public interface DicomVerifyService {
    java.util.List<Long> verifySession(DicomVerifyRequest request);
    void processVerifiedSessionAsync(java.util.List<Long> savedInstanceIds, String username);
}
