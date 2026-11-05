package com.pte.scoring.internal.rubric.catalog;

import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;
import com.pte.scoring.rubric.TraitSource;

/** Pronunciation and Oral Fluency (Score Guide pp.45-46) are described once and shared by the seven speaking tasks. */
public final class PronunciationFluencyTraits {

    private static final int MAX = 5;

    private PronunciationFluencyTraits() {
    }

    public static TraitDefinition pronunciation(String taskPages) {
        return CatalogSupport.trait(RubricTraits.PRONUNCIATION, "Phát âm", "Pronunciation", TraitScale.fixed(MAX),
                TraitSource.AZURE, taskPages + ", tr.45", CatalogSupport.levels(
                        "Non-English: pronunciation seems completely characteristic of another language. Many "
                                + "consonants and vowels are mispronounced, mis-ordered or omitted. Listeners may "
                                + "find more than 1/2 of the speech unintelligible.",
                        "Intrusive: many consonants and vowels are mispronounced, resulting in a strong intrusive "
                                + "foreign accent. Listeners may have difficulty understanding about 1/3 of the words.",
                        "Intermediate: some consonants and vowels are consistently mispronounced. At least 2/3 of "
                                + "speech is intelligible, but listeners might need to adjust to the accent.",
                        "Good: most vowels and consonants are pronounced correctly. Some consistent errors might "
                                + "make a few words unclear.",
                        "Advanced: vowels and consonants are pronounced clearly and unambiguously. A few minor "
                                + "consonant, vowel or stress distortions do not affect intelligibility. All words "
                                + "are easily understandable.",
                        "Highly proficient: all vowels and consonants are produced in a manner that is easily "
                                + "understood by regular speakers of the language. Stress is placed correctly in all "
                                + "words and sentence-level stress is fully appropriate."));
    }

    public static TraitDefinition oralFluency(String taskPages) {
        return CatalogSupport.trait(RubricTraits.ORAL_FLUENCY, "Độ trôi chảy", "Oral Fluency", TraitScale.fixed(MAX),
                TraitSource.AZURE, taskPages + ", tr.46", CatalogSupport.levels(
                        "Disfluent: speech is slow and labored with little discernable phrase grouping, multiple "
                                + "hesitations, pauses, false starts, and/or major phonological simplifications. "
                                + "Most words are isolated.",
                        "Limited: speech has irregular phrasing or sentence rhythm. Poor phrasing, staccato or "
                                + "syllabic timing, and/or multiple hesitations, repetitions, and/or false starts "
                                + "make spoken performance notably uneven or discontinuous.",
                        "Intermediate: speech may be uneven or staccato. No more than two or three hesitations, "
                                + "repetitions or false starts. There may be one long pause, but not two or more.",
                        "Good: speech is at an acceptable speed but may be uneven. There may be more than one "
                                + "hesitation, but most words are spoken in continuous phrases.",
                        "Advanced: speech has an acceptable rhythm with appropriate phrasing and word emphasis. "
                                + "There is no more than one hesitation, one repetition or a false start.",
                        "Highly proficient: speech shows smooth rhythm and phrasing. There are no hesitations, "
                                + "repetitions, false starts or phonological simplifications."));
    }
}
