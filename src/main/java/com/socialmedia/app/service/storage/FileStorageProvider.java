package com.socialmedia.app.service.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * A pluggable backend for persisting uploaded media. Swapping providers (e.g. Cloudinary → S3)
 * means adding one new implementation here — nothing else in the app depends on the storage
 * mechanism directly.
 */
public interface FileStorageProvider {

    /**
     * Persists the given file and returns a permanent, publicly reachable URL for it.
     */
    String store(MultipartFile file);
}
