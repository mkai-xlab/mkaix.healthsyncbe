package com.g93.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing a user profile avatar image.
 */
@Entity
@Table(name = "avatar_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvatarImage extends BaseImage {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}
