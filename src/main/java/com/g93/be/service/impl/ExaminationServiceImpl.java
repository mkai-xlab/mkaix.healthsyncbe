package com.g93.be.service.impl;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Examination;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.service.ExaminationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.g93.be.mapper.ExaminationMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExaminationServiceImpl implements ExaminationService {

    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final ExaminationMapper examinationMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getAllExaminations(Pageable pageable) {
        Page<Examination> examinationPage = examinationRepository.findAll(pageable);
        List<ExaminationDto> content = examinationPage.getContent().stream()
                .map(ex -> {
                    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
                    return examinationMapper.toDto(ex, instances);
                })
                .toList();

        return new PageResponse<>(
                content,
                examinationPage.getNumber(),
                examinationPage.getSize(),
                examinationPage.getTotalElements(),
                examinationPage.getTotalPages(),
                examinationPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public ExaminationDto getExaminationById(Long id) {
        Examination examination = examinationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Examination with id " + id + " not found"));
        List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examination.getId());
        return examinationMapper.toDto(examination, instances);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByDoctorId(Long doctorId, Pageable pageable) {
        Page<Examination> examinationPage = examinationRepository.findByDoctorId(doctorId, pageable);
        List<ExaminationDto> content = examinationPage.getContent().stream()
                .map(ex -> {
                    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
                    return examinationMapper.toDto(ex, instances);
                })
                .toList();

        return new PageResponse<>(
                content,
                examinationPage.getNumber(),
                examinationPage.getSize(),
                examinationPage.getTotalElements(),
                examinationPage.getTotalPages(),
                examinationPage.isLast()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExaminationDto> getExaminationsByPatientId(Long patientId, Pageable pageable) {
        Page<Examination> examinationPage = examinationRepository.findByPatientId(patientId, pageable);
        List<ExaminationDto> content = examinationPage.getContent().stream()
                .map(ex -> {
                    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
                    return examinationMapper.toDto(ex, instances);
                })
                .toList();

        return new PageResponse<>(
                content,
                examinationPage.getNumber(),
                examinationPage.getSize(),
                examinationPage.getTotalElements(),
                examinationPage.getTotalPages(),
                examinationPage.isLast()
        );
    }

    @Override
    @Transactional
    public void markAsViewed(Long id) {
        Examination examination = examinationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Examination with id " + id + " not found"));
        examination.setIsViewed(1);
        examinationRepository.save(examination);
    }
}
