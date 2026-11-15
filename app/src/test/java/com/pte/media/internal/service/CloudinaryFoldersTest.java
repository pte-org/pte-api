package com.pte.media.internal.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryFoldersTest {

    private final CloudinaryFolders folders = new CloudinaryFolders("/pte/prod/");

    @Test
    void examAndPracticeFilesLiveInSeparateBranchesUnderTheEnvironmentRoot() {
        assertThat(folders.questions("EXAM")).isEqualTo("pte/prod/exam/questions");
        assertThat(folders.examAnswers()).isEqualTo("pte/prod/exam/answers");
        assertThat(folders.questions("PRACTICE")).isEqualTo("pte/prod/practice/questions");
        assertThat(folders.practiceRecordings()).isEqualTo("pte/prod/practice/recordings");
    }

    @Test
    void aMissingPoolMeansExamAndAMissingRootMeansLocal() {
        assertThat(folders.questions(null)).isEqualTo("pte/prod/exam/questions");
        assertThat(new CloudinaryFolders(" ").examAnswers()).isEqualTo("pte/local/exam/answers");
    }
}
