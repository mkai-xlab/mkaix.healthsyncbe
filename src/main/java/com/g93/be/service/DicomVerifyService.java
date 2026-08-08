package com.g93.be.service;

import com.g93.be.dto.DicomVerifyRequest;

public interface DicomVerifyService {
    com.g93.be.dto.VerifySessionResultDto verifySession(DicomVerifyRequest request, Long requestingUserId, boolean privilegedUser);
    void processVerifiedSessionAsync(java.util.List<Long> savedInstanceIds, String username);
}
