package com.socialmedia.app.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary", matchIfMissing = true)
public class CloudinaryStorageProvider implements FileStorageProvider {

    private final Cloudinary cloudinary;

    @Override
    public String store(MultipartFile file) {
        try {
            String publicId = "socialmedia/" + UUID.randomUUID();
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("public_id", publicId, "resource_type", "image")
            );
            return (String) result.get("secure_url");
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file. Please try again!", ex);
        }
    }
}
