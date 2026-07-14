# Lỗi Upload File Lên Cloudinary (Invalid image file / Video bitrate too low)

Tài liệu này giải thích nguyên nhân và cách khắc phục lỗi `RuntimeException` khi thực hiện upload file âm thanh (audio) hoặc video lên Cloudinary thông qua SDK của Spring Boot.

## 1. Dấu hiệu lỗi
Khi hệ thống cố gắng upload một tệp tin âm thanh (như `.mp3`, `.m4a`) lên Cloudinary, tiến trình bị gián đoạn và ném ra một trong hai ngoại lệ sau:

**Lỗi 1: Không xác định đúng loại file**
```text
java.lang.RuntimeException: Invalid image file
    at com.cloudinary.strategies.AbstractUploaderStrategy.processResponse(AbstractUploaderStrategy.java:85)
```

**Lỗi 2: Lỗi giới hạn bitrate của Cloudinary (dù đã cấu hình auto)**
```text
java.lang.RuntimeException: Video bitrate too low: must be at least 100000 bps
    at com.cloudinary.strategies.AbstractUploaderStrategy.processResponse(AbstractUploaderStrategy.java:85)
```

- **Với Lỗi 1 (`Invalid image file`)**: Nếu dùng `resource_type: "image"` (hoặc mặc định) cho file audio, Cloudinary sẽ báo lỗi vì không parse được luồng byte thành hình ảnh.
- **Với Lỗi 2 (`Video bitrate too low`)**: Khi dùng `resource_type: "auto"`, Cloudinary nhận diện file âm thanh là Video. Lỗi `Video bitrate too low: must be at least 100000 bps` xảy ra **không phải do file gốc**, mà do trong Upload Preset của Cloudinary đang có thiết lập Transformation `br_64k` (giới hạn Video Bitrate xuống 64k). Cloudinary không cho phép Video Bitrate dưới 100k nên ném ra lỗi này khi cố áp dụng transformation.
- **Vấn đề mất đuôi file**: Dù Upload Preset có cấu hình `Use filename: true`, nhưng do trong code Java sử dụng `cloudinary.uploader().upload(file.getBytes(), options)`, Cloudinary chỉ nhận được một mảng byte nhị phân vô danh và không biết tên file là gì để giữ lại phần mở rộng.

## 3. Cách khắc phục (Tuyệt đối và triệt để)
Để giải quyết toàn bộ các vấn đề trên (kể cả việc giữ lại tên file và áp dụng được transformation audio):

1. **Về phía Cấu hình Cloudinary (Upload Preset)**:
   - Xóa bỏ transformation `br_64k` trong preset, chỉ giữ lại `ac_mp3` (hoặc `f_mp3`) để Cloudinary biết đây là tác vụ xử lý audio, không bị vướng giới hạn Video Bitrate 100k.
2. **Về phía Code Java (`CloudinaryServiceImpl.java`)**:
   - Sử dụng `resource_type: "auto"` thay vì `"raw"` để Cloudinary có thể chạy được các Transformation (convert mp3) trong preset.
   - Thay vì truyền `file.getBytes()`, hãy tạo một tệp tạm thời (`java.io.File`) từ `MultipartFile` và truyền tệp đó vào hàm `upload()`. Bằng cách này, Cloudinary SDK sẽ tự động đọc được tên gốc của file và Preset `Use filename: true` sẽ hoạt động hoàn hảo.

**Chi tiết sửa đổi trong `CloudinaryServiceImpl.java`**:
```java
String resourceType = "auto";
String contentType = file.getContentType();

// Bỏ qua rào cản bitrate của Cloudinary đối với các file media chất lượng thấp
// bằng cách upload chúng dưới dạng "raw"
if (contentType != null && (contentType.startsWith("audio/") || contentType.startsWith("video/"))) {
    resourceType = "raw";
}

Map<?, ?> options = ObjectUtils.asMap(
    StorageConstants.PROP_FOLDER, targetFolder,
    StorageConstants.PROP_UPLOAD_PRESET, StorageConstants.VAL_UPLOAD_PRESET,
    "resource_type", resourceType
);

Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
```

**Kết quả:**
- File ảnh vẫn được upload dạng `auto` và được Cloudinary tối ưu hóa hình ảnh.
- File âm thanh (kể cả có bitrate thấp) được upload dạng `raw`, lưu trữ an toàn và có thể stream/play bình thường trên client mà không bị ném lỗi 500 từ Cloudinary API.
