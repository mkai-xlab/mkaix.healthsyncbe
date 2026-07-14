package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 100, nullable = false, unique = true)
    private String code;

    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(name = "priority")
    private Integer priority = 1;

    @Column(name = "presentation")
    private String presentation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requires_permission_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Permission requiresPermission;
}
