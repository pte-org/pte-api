package com.pte.media.internal.constant;

import java.time.Duration;

public final class MediaConstants {

    public static final String MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND";
    public static final String MEDIA_ALREADY_UPLOADED = "MEDIA_ALREADY_UPLOADED";
    public static final String UNSUPPORTED_CONTENT_TYPE = "UNSUPPORTED_CONTENT_TYPE";
    public static final String MEDIA_NOT_YET_UPLOADED = "MEDIA_NOT_YET_UPLOADED";
    public static final String PRACTICE_MEDIA_BINDING_INVALID = "PRACTICE_MEDIA_BINDING_INVALID";
    public static final String CONTENT_TYPE_REQUIRED = "Content type is required";
    public static final String ACADEMIC_DRAFT_WRITE_REQUIRED_FOR_QUESTION_MEDIA =
            "Only academic staff, academic managers, or platform admins may upload question media";
    public static final String STUDENT_REQUIRED_FOR_RESPONSE_AUDIO = "Only students may upload response audio";
    public static final String MEDIA_ASSET_OWNED_BY_ANOTHER_AUTHOR = "The media asset is owned by another author";
    public static final String CLOUDINARY_CREDENTIALS_NOT_CONFIGURED = "Cloudinary credentials are not configured";
    public static final String SHA1_UNAVAILABLE = "SHA-1 is unavailable";
    public static final long MAX_AUTHORING_BYTES = 25L * 1024 * 1024;
    public static final long MAX_SUBMISSION_BYTES = 25L * 1024 * 1024;
    public static final long MAX_PRACTICE_RESPONSE_BYTES = 10L * 1024 * 1024;

    public static final String IMAGE_PROMPT = "IMAGE_PROMPT";
    public static final String AUDIO_PROMPT = "AUDIO_PROMPT";
    public static final String STUDENT_RESPONSE_AUDIO = "STUDENT_RESPONSE_AUDIO";

    public static final String AUDIO_MPEG = "audio/mpeg";
    public static final String AUDIO_WAV = "audio/wav";
    public static final String AUDIO_WEBM = "audio/webm";
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_JPEG = "image/jpeg";

    /** A practice recording younger than this is left alone: its answer may still be on the way. */
    public static final Duration ORPHAN_AUDIO_GRACE = Duration.ofHours(24);
    public static final int MEDIA_PURGE_BATCH_SIZE = 100;
    /** A question file younger than this is left alone: its question or generation job may still be in progress. */
    public static final Duration UNUSED_AUTHORING_MEDIA_GRACE = Duration.ofHours(24);
    public static final String CLOUDINARY_DESTROY_RESULT_OK = "ok";
    public static final String CLOUDINARY_DESTROY_RESULT_NOT_FOUND = "not found";

    /** Root of every stored file when none is configured; each environment should set its own. */
    public static final String DEFAULT_CLOUDINARY_ROOT_FOLDER = "pte/local";
    public static final String FOLDER_EXAM = "exam";
    public static final String FOLDER_PRACTICE = "practice";
    public static final String FOLDER_QUESTIONS = "questions";
    public static final String FOLDER_ANSWERS = "answers";
    public static final String FOLDER_RECORDINGS = "recordings";
    public static final String POOL_EXAM = "EXAM";
    public static final String POOL_PRACTICE = "PRACTICE";
    public static final String QUESTION_POOL_INVALID = "Question pool must be EXAM or PRACTICE";

    private MediaConstants() {
    }
}
