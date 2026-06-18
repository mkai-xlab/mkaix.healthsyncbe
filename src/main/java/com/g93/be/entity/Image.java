package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extension", length = 20)
    private String extension;

    @Column(name = "s3_bucket_id", length = 255)
    private String s3BucketId;

    @Column(name = "s3_bucket_key", length = 500)
    private String s3BucketKey;
}
