package com.aptis.modules.questionbank.constant;

public final class QuestionBankConstants {

    // Operation codes (for audit / logging)
    public static final String OP_QUESTION_CREATE = "questionbank.question.create";
    public static final String OP_QUESTION_UPDATE = "questionbank.question.update";
    public static final String OP_QUESTION_DELETE = "questionbank.question.delete";
    public static final String OP_QUESTION_PUBLISH = "questionbank.question.publish";
    public static final String OP_QUESTION_AUDIO_UPLOAD = "questionbank.question.audio-upload";
    public static final String OP_QUESTION_IMPORT = "questionbank.question.import";

    // Success messages
    public static final String QUESTION_CREATED = "Question created successfully";
    public static final String QUESTION_UPDATED = "Question updated successfully";
    public static final String QUESTION_DELETED = "Question deleted successfully";
    public static final String QUESTION_LIST_SUCCESS = "Questions retrieved successfully";
    public static final String QUESTION_PUBLISHED = "Question published successfully";
    public static final String QUESTION_AUDIO_UPLOADED = "Audio uploaded successfully";
    public static final String QUESTION_IMPORT_SUCCESS = "Questions imported successfully";
    public static final String QUESTION_IMPORT_VALIDATION_FAILED = "Import file failed validation";

    private QuestionBankConstants() {
    }
}
