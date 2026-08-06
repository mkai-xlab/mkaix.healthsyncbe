package com.g93.be.service;

import org.springframework.core.io.Resource;

public interface ImageService {
    /**
     * Reads the image file from the disk given an image ID.
     * @param imageId The ID of the image in the database.
     * @return The resource corresponding to the image, or null if not found.
     */
    Resource getImageResource(Long imageId);
}
