-- Student-facing instruction per task type, editable by admins even after the runtime contract is locked.
-- Standard rows start with the text pte-app used to hard-code (TaskTypeMeta.instruction), so delivery is unchanged.
-- Nullable: custom task types created before this version have no instruction and clients fall back to their default.
ALTER TABLE question_types
    ADD COLUMN instruction varchar(500);

UPDATE question_types SET instruction = 'Introduce yourself using the prompts below.'
WHERE task_type_key = 'PERSONAL_INTRODUCTION';
UPDATE question_types SET instruction = 'Read the text aloud as naturally as possible.'
WHERE task_type_key = 'READ_ALOUD';
UPDATE question_types SET instruction = 'Listen to the sentence and repeat it.'
WHERE task_type_key = 'REPEAT_SENTENCE';
UPDATE question_types SET instruction = 'Describe the image in detail.'
WHERE task_type_key = 'DESCRIBE_IMAGE';
UPDATE question_types SET instruction = 'Re-tell the lecture in your own words.'
WHERE task_type_key = 'RE_TELL_LECTURE';
UPDATE question_types SET instruction = 'Listen to the question and answer with a word or short phrase.'
WHERE task_type_key = 'ANSWER_SHORT_QUESTION';
UPDATE question_types SET instruction = 'Summarize the discussion in your own words.'
WHERE task_type_key = 'SUMMARIZE_GROUP_DISCUSSION';
UPDATE question_types SET instruction = 'Respond appropriately to the situation.'
WHERE task_type_key = 'RESPOND_TO_A_SITUATION';
UPDATE question_types SET instruction = 'Summarize the passage in one sentence.'
WHERE task_type_key = 'SUMMARIZE_WRITTEN_TEXT';
UPDATE question_types SET instruction = 'Plan, write and revise an essay about the topic below.'
WHERE task_type_key = 'WRITE_ESSAY';
UPDATE question_types SET instruction = 'Write a summary of the recording.'
WHERE task_type_key = 'SUMMARIZE_SPOKEN_TEXT';
UPDATE question_types SET instruction = 'Type the sentence you hear.'
WHERE task_type_key = 'WRITE_FROM_DICTATION';
UPDATE question_types SET instruction = 'Select the best word for each blank.'
WHERE task_type_key = 'FILL_IN_THE_BLANKS_DROPDOWN';
UPDATE question_types SET instruction = 'Select all the correct responses.'
WHERE task_type_key = 'MC_READING_MULTIPLE';
UPDATE question_types SET instruction = 'Re-order the paragraphs into the correct sequence.'
WHERE task_type_key = 'RE_ORDER_PARAGRAPHS';
UPDATE question_types SET instruction = 'In the text below some words are missing. Drag words from the box below to the appropriate place in the text. To undo an answer choice, drag the word back to the box below the text.'
WHERE task_type_key = 'FILL_IN_THE_BLANKS_DRAG_AND_DROP';
UPDATE question_types SET instruction = 'Select the correct response.'
WHERE task_type_key = 'MC_READING_SINGLE';
UPDATE question_types SET instruction = 'Listen to the recording and answer the question by selecting the correct responses. You will need to select more than one response.'
WHERE task_type_key = 'MC_LISTENING_MULTIPLE';
UPDATE question_types SET instruction = 'You will hear a recording. Type the missing words in each blank.'
WHERE task_type_key = 'FILL_IN_THE_BLANKS_TYPE_IN';
UPDATE question_types SET instruction = 'Select the summary that best represents the recording.'
WHERE task_type_key = 'HIGHLIGHT_CORRECT_SUMMARY';
UPDATE question_types SET instruction = 'Select the correct response.'
WHERE task_type_key = 'MC_LISTENING_SINGLE';
UPDATE question_types SET instruction = 'Select the word that completes the recording.'
WHERE task_type_key = 'SELECT_MISSING_WORD';
UPDATE question_types SET instruction = 'You will hear a recording. Below is a transcript of the recording. Some words in the transcript differ from what the speaker(s) said. Double-click the words that are different.'
WHERE task_type_key = 'HIGHLIGHT_INCORRECT_WORDS';
