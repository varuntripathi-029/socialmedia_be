package com.socialmedia.app.service;

import com.socialmedia.app.service.storage.FileStorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProvider fileStorageProvider;

    public String storeFile(MultipartFile file) {
        return fileStorageProvider.store(file);
    }
}
