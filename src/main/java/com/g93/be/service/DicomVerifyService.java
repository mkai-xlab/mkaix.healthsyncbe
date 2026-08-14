package com.g93.be.service;

import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.dto.VerifySessionResultDto;

import java.util.List;

public interface DicomVerifyService {
    // Thực hiện lưu thông tin bệnh nhân đã đc bác sĩ các nhận
    VerifySessionResultDto verifySession(DicomVerifyRequest request, Long requestingUserId, boolean privilegedUser);

    // Xử lý bất đồng bộ sau khi lưu thông tin bệnh nhân
    void processVerifiedSessionAsync(List<Long> savedInstanceIds, String username);
}
