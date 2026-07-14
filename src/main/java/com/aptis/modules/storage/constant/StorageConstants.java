package com.aptis.modules.storage.constant;

public final class StorageConstants {
    private StorageConstants() {}

    // Cloudinary properties keys - Các khóa truyền tham số hoặc đọc kết quả từ SDK Cloudinary
    public static final String PROP_FOLDER = "folder";              // Key xác định thư mục lưu trữ khi upload
    public static final String PROP_SECURE_URL = "secure_url";      // Key lấy đường dẫn URL HTTPS (CDN URL) từ kết quả Cloudinary trả về
    public static final String PROP_PUBLIC_ID = "public_id";        // Key lấy mã định danh duy nhất (Storage Key) của file trên Cloudinary
    public static final String PROP_UPLOAD_PRESET = "upload_preset"; // Key để khai báo cấu hình upload preset của Cloudinary
    
    // Cloudinary values - Các giá trị cấu hình mặc định của hệ thống Aptis
    public static final String VAL_UPLOAD_PRESET = "aptis_upload_preset"; // Upload Preset đã thiết lập sẵn trên Dashboard Cloudinary
    public static final String VAL_BASE_FOLDER = "aptis";                 // Thư mục gốc của dự án Aptis trên Cloudinary
    
    // Default Folders - Các thư mục con nghiệp vụ để phân loại tệp tin trong thư mục gốc "aptis"
    public static final String FOLDER_QUESTIONS = "questions";      // Thư mục chứa hình ảnh/âm thanh ngân hàng câu hỏi (aptis/questions)
    public static final String FOLDER_ANSWERS = "answers";          // Thư mục chứa file ghi âm/hình ảnh câu trả lời của thí sinh (aptis/answers)

    // Exceptions & Messages
    public static final String UPLOAD_FAILED_MSG = "Failed to upload file to Cloudinary storage";
    public static final String DELETE_FAILED_MSG = "Failed to delete file from Cloudinary storage";
    public static final String EMPTY_FILE_MSG = "Cannot upload an empty file";
}
