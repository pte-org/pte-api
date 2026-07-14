package com.aptis.modules.storage.interfaces;

import org.springframework.web.multipart.MultipartFile;
import com.aptis.modules.storage.dto.response.UploadResponse;

public interface StorageService {
    UploadResponse uploadFile(MultipartFile file, String folder);
    void deleteFile(String storageKey);
}
