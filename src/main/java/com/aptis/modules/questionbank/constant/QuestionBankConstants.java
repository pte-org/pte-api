package com.aptis.modules.questionbank.constant;

public final class QuestionBankConstants {

    // Operation codes (for audit / logging)
    public static final String OP_QUESTION_CREATE = "questionbank.question.create";
    public static final String OP_QUESTION_UPDATE = "questionbank.question.update";
    public static final String OP_QUESTION_DELETE = "questionbank.question.delete";

    // Success messages
    public static final String QUESTION_CREATED = "Question created successfully";
    public static final String QUESTION_UPDATED = "Question updated successfully";
    public static final String QUESTION_DELETED = "Question deleted successfully";
    public static final String QUESTION_LIST_SUCCESS = "Questions retrieved successfully";

    private QuestionBankConstants() {
    }
}
