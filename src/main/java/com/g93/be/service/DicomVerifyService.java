package com.g93.be.service;

import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.dto.VerifySessionResultDto;

import java.util.List;

public interface DicomVerifyService {
    VerifySessionResultDto verifySession(DicomVerifyRequest request, Long requestingUserId, boolean privilegedUser);

    void processVerifiedSessionAsync(List<Long> savedInstanceIds, String username);
}
