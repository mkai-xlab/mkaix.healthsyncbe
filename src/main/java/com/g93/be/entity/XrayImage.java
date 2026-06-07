package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing an uploaded knee X-ray image.
 * Extends {@link BaseImage} where fileUrl stores the web-viewable PNG format link.
 */
@Entity
@Table(name = "xray_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XrayImage extends BaseImage {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @Column(name = "dicom_url", length = 500)
    private String dicomUrl;

    @Column(name = "body_side", length = 20)
    private String bodySide;

    @Column(name = "view_position", length = 50)
    private String viewPosition;

    @OneToOne(mappedBy = "xrayImage", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DicomInformation dicomInformation;
}
