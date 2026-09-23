package com.pte.media.internal.constant;

public final class MediaConstants {

    public static final String MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND";
    public static final String MEDIA_ALREADY_UPLOADED = "MEDIA_ALREADY_UPLOADED";
    public static final String UNSUPPORTED_CONTENT_TYPE = "UNSUPPORTED_CONTENT_TYPE";
    public static final String MEDIA_NOT_YET_UPLOADED = "MEDIA_NOT_YET_UPLOADED";
    public static final String CONTENT_TYPE_REQUIRED = "Content type is required";
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
