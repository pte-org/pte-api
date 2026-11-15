package com.pte.media.internal.service;

import com.pte.media.internal.constant.MediaConstants;

/**
 * Where each kind of file is stored at Cloudinary, under one root per environment ({@code pte/prod}, {@code pte/local})
 * so environments sharing an account never mix files:
 * <pre>
 * {root}/exam/questions        prompt audio and images of EXAM-pool questions
 * {root}/exam/answers          students' official exam answers
 * {root}/practice/questions    prompt audio and images of PRACTICE-pool questions
 * {root}/practice/recordings   students' practice recordings
 * </pre>
 * Files stored before this layout keep their old path; each row records its own.
 */
final class CloudinaryFolders {

    private final String root;

    CloudinaryFolders(String root) {
        this.root = root == null || root.isBlank() ? MediaConstants.DEFAULT_CLOUDINARY_ROOT_FOLDER : stripSlashes(root);
    }

    /** Folder of a question's prompt file; a missing or unknown pool counts as EXAM, the questions' default pool. */
    String questions(String pool) {
        String branch = MediaConstants.POOL_PRACTICE.equals(pool) ? MediaConstants.FOLDER_PRACTICE
                : MediaConstants.FOLDER_EXAM;
        return root + "/" + branch + "/" + MediaConstants.FOLDER_QUESTIONS;
    }

    String examAnswers() {
        return root + "/" + MediaConstants.FOLDER_EXAM + "/" + MediaConstants.FOLDER_ANSWERS;
    }

    String practiceRecordings() {
        return root + "/" + MediaConstants.FOLDER_PRACTICE + "/" + MediaConstants.FOLDER_RECORDINGS;
    }

    /** SQL {@code like} pattern matching every file stored under this root. */
    String likePrefix() {
        return root + "/%";
    }

    /** {@link #likePrefix()} of the given root folder. */
    static String likePrefix(String rootFolder) {
        return new CloudinaryFolders(rootFolder).likePrefix();
    }

    private static String stripSlashes(String value) {
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
