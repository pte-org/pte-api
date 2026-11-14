package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.live.GenerationLiveContext;
import com.pte.itembank.internal.generation.live.LiveEvent;

import java.util.List;
import java.util.Map;

/** Tells the live view the outcome of the duplicate check: the content's hash and the task type it was compared in. */
final class DuplicateCheck {

    private DuplicateCheck() {
    }

    static void report(String taskTypeKey, GeneratedItem item, boolean duplicate) {
        String hash = ContentHasher.hash(item.stimulusText());
        String shown = hash.substring(0, Math.min(hash.length(), GenerationLiveConstants.CONTENT_HASH_SHOWN));
        GenerationLiveContext.check(GenerationLiveConstants.CHECK_DUPLICATE,
                duplicate ? GenerationLiveConstants.CHECK_FAILED : GenerationLiveConstants.CHECK_PASSED, Map.of(),
                List.of(new LiveEvent.CheckDetail(GenerationLiveConstants.DETAIL_CONTENT_HASH,
                                GenerationLiveConstants.UNIT_TEXT, shown, null),
                        new LiveEvent.CheckDetail(GenerationLiveConstants.DETAIL_TASK_TYPE,
                                GenerationLiveConstants.UNIT_TEXT, taskTypeKey, null)));
    }
}
