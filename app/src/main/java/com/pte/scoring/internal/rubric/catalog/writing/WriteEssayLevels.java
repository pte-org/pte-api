package com.pte.scoring.internal.rubric.catalog.writing;

import com.pte.scoring.rubric.LevelDescriptor;

import java.util.List;

import static com.pte.scoring.internal.rubric.catalog.CatalogSupport.levels;

/** Level descriptions of the Write Essay traits (Score Guide pp.34-39), quoted in English. */
final class WriteEssayLevels {

    private WriteEssayLevels() {
    }

    static List<LevelDescriptor> content() {
        return levels(
                "The essay does not properly deal with the prompt.",
                "The essay attempts to address the prompt, but demonstrates an incomplete understanding of it, "
                        + "with communication limited to generic or repetitive phrasing. Supporting details, if "
                        + "present, are used in a disjointed or haphazard manner.",
                "The essay attempts to address the prompt, but does so superficially, with little relevant "
                        + "information and largely generic statements. Few supporting details are included.",
                "The essay is relevant to the prompt but does not address the main points adequately. Supporting "
                        + "detail is often missing or inappropriate.",
                "The essay adequately addresses the main point of the prompt. The argument is generally "
                        + "convincing, though lacks depth or nuance. Supporting detail is inconsistent.",
                "The essay adequately addresses the prompt, presenting a persuasive argument with relevant ideas. "
                        + "Main points are highlighted and relevant supporting detail is given, with minor "
                        + "exceptions.",
                "The essay fully addresses the prompt in depth, demonstrating full command of the argument by "
                        + "reformulating the issue in own words and expanding on important points with "
                        + "specificity. The argument is supported convincingly with subsidiary points and "
                        + "relevant examples.");
    }

    static List<LevelDescriptor> form() {
        return levels(
                "Length is less than 120 or more than 380 words. Essay is written in capital letters, contains no "
                        + "punctuation or only consists of bullet points or very short sentences.",
                "Length is between 120 and 199 or between 301 and 380 words.",
                "Length is between 200 and 300 words.");
    }

    static List<LevelDescriptor> developmentStructureCoherence() {
        return levels(
                "There is no recognisable structure.",
                "Response consists of disconnected ideas. There is no hierarchy of ideas or coherence among "
                        + "points. No clear position or opinion can be identified.",
                "There is little recognisable structure. Ideas are presented in a disorganised manner and are "
                        + "difficult to follow. The essay lacks coherence and mainly consists of disconnected "
                        + "elements.",
                "A position or opinion is present, although it is not sufficiently developed into a logical "
                        + "argument. The response consists mainly of unconnected ideas and requires significant "
                        + "effort to follow.",
                "Conventional structure is mostly present, but some elements may be missing, requiring some "
                        + "effort to follow. An argument is present but lacks development of some elements.",
                "The essay has a conventional and appropriate structure that follows logically, if not always "
                        + "smoothly. An argument is clear, with some points developed at length. Introduction, "
                        + "conclusion and logical paragraphs are present.",
                "The essay has an effective logical structure, flows smoothly, and can be followed with ease. An "
                        + "argument is clear and cohesive, developed systematically at length. A well-developed "
                        + "introduction and conclusion are present.");
    }

    static List<LevelDescriptor> grammar() {
        return levels(
                "Contains mainly simple structures and/or several basic mistakes.",
                "Shows a relatively high degree of grammatical control. No mistakes which would lead to "
                        + "misunderstandings.",
                "Shows consistent grammatical control of complex language. Errors are rare and difficult to spot.");
    }

    static List<LevelDescriptor> generalLinguisticRange() {
        return levels(
                "Meaning is not accessible.",
                "Vocabulary and linguistic expression are highly restricted. There are significant limitations "
                        + "in communication and ideas are generally unclear. Errors in language use are "
                        + "pervasive and impede meaning.",
                "Limited vocabulary and simple expressions dominate the response. Communication is compromised "
                        + "and some ideas are unclear. Basic errors in language use are common.",
                "The range of expression and vocabulary is narrow and simple expressions are used repeatedly. "
                        + "Communication is restricted to simple ideas. Errors in language use cause some "
                        + "disruptions for the reader.",
                "The range of expression and vocabulary is sufficient to articulate basic ideas. Most ideas are "
                        + "clear, but limitations are evident when conveying complex or abstract ideas. Errors "
                        + "cause occasional lapses in clarity.",
                "A variety of expressions and vocabulary are used appropriately throughout the response. Ideas "
                        + "are expressed clearly without much sign of restriction. Occasional errors are "
                        + "present, but the meaning is clear.",
                "A variety of expressions and vocabulary are used appropriately to formulate ideas with ease and "
                        + "precision. No signs of limitations restricting what can be communicated. Errors, if "
                        + "present, are rare and minor.");
    }

    static List<LevelDescriptor> vocabularyRange() {
        return levels(
                "Contains mainly basic vocabulary insufficient to deal with the topic at the required level.",
                "Shows a good range of vocabulary for matters connected to general academic topics. Lexical "
                        + "shortcomings lead to circumlocution or some imprecision.",
                "Good command of a broad lexical repertoire, idiomatic expressions and colloquialisms.");
    }

    static List<LevelDescriptor> spelling() {
        return levels("More than one spelling error.", "One spelling error.", "Correct spelling.");
    }
}
