package com.aptis.modules.storage.service;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.modules.storage.constant.StorageConstants;
import com.aptis.modules.storage.domain.exception.StorageException;
import com.aptis.modules.storage.dto.response.UploadResponse;
import com.aptis.modules.storage.interfaces.StorageService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@Service
public class CloudinaryServiceImpl implements StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudinaryServiceImpl.class);
    private final Cloudinary cloudinary;

    public CloudinaryServiceImpl(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public UploadResponse uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new StorageException(StorageConstants.EMPTY_FILE_MSG);
        }

        try {
            String targetFolder = (folder == null || folder.isBlank())
                    ? StorageConstants.VAL_BASE_FOLDER
                    : StorageConstants.VAL_BASE_FOLDER + "/" + folder;

            Map<String, Object> options = new java.util.HashMap<>();
            options.put(StorageConstants.PROP_FOLDER, targetFolder);
            options.put(StorageConstants.PROP_UPLOAD_PRESET, StorageConstants.VAL_UPLOAD_PRESET);
            options.put("resource_type", "auto"); // Đổi về auto để Preset có thể convert mp3

            // Tạo temp file để Cloudinary tự động đọc được Original Filename
            java.io.File tempFile = java.io.File.createTempFile("upload_", "_" + file.getOriginalFilename());
            try {
                file.transferTo(tempFile);
                Map<?, ?> uploadResult = cloudinary.uploader().upload(tempFile, options);

                String secureUrl = (String) uploadResult.get(StorageConstants.PROP_SECURE_URL);
                String publicId = (String) uploadResult.get(StorageConstants.PROP_PUBLIC_ID);
                
                return new UploadResponse(
                    publicId,
                    secureUrl,
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType()
                );
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error uploading file to Cloudinary: ", e);
            throw new StorageException(StorageConstants.UPLOAD_FAILED_MSG, e);
        }
    }

    @Override
    public void deleteFile(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        try {
            cloudinary.uploader().destroy(storageKey, ObjectUtils.emptyMap());
        } catch (IOException e) {
            LOGGER.error("Error deleting file from Cloudinary with key {}: ", storageKey, e);
            throw new StorageException(StorageConstants.DELETE_FAILED_MSG, e);
        }
    }
}
