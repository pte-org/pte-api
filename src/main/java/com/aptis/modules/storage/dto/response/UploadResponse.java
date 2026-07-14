package com.aptis.modules.storage.dto.response;

public record UploadResponse(
    String storageKey, // public_id của Cloudinary
    String cdnUrl,      // secure_url (URL truy cập trực tiếp qua CDN)
    String filename,   // tên gốc của file
    Long sizeBytes,    // kích thước file (byte)
    String mimeType    // loại file (ví dụ: image/png, audio/mpeg)
) {}
