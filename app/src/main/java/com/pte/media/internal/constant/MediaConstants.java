package com.pte.media.internal.constant;

public final class MediaConstants {

    public static final String MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND";
    public static final String MEDIA_ALREADY_UPLOADED = "MEDIA_ALREADY_UPLOADED";
    public static final String UNSUPPORTED_CONTENT_TYPE = "UNSUPPORTED_CONTENT_TYPE";
    public static final String MEDIA_NOT_YET_UPLOADED = "MEDIA_NOT_YET_UPLOADED";
    public static final String CONTENT_TYPE_REQUIRED = "Content type is required";
    public static final String PLATFORM_AUTHOR_REQUIRED_FOR_QUESTION_MEDIA =
            "Only platform authors may upload question media";
    public static final String STUDENT_REQUIRED_FOR_RESPONSE_AUDIO = "Only students may upload response audio";
    public static final String MEDIA_ASSET_OWNED_BY_ANOTHER_AUTHOR = "The media asset is owned by another author";
    public static final String CLOUDINARY_CREDENTIALS_NOT_CONFIGURED = "Cloudinary credentials are not configured";
    public static final String SHA1_UNAVAILABLE = "SHA-1 is unavailable";
    public static final long MAX_AUTHORING_BYTES = 25L * 1024 * 1024;
    public static final long MAX_SUBMISSION_BYTES = 25L * 1024 * 1024;

    public static final String IMAGE_PROMPT = "IMAGE_PROMPT";
    public static final String AUDIO_PROMPT = "AUDIO_PROMPT";
    public static final String STUDENT_RESPONSE_AUDIO = "STUDENT_RESPONSE_AUDIO";

    public static final String AUDIO_MPEG = "audio/mpeg";
    public static final String AUDIO_WAV = "audio/wav";
    public static final String AUDIO_WEBM = "audio/webm";
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_JPEG = "image/jpeg";

    private MediaConstants() {
    }
}
