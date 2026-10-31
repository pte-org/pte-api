package com.pte.chat.promptsuite;

import com.pte.chat.internal.prompt.ChatPrompts;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates the machine-checkable pass rules of a {@link PromptSuiteCase} against one model reply. Pure and
 * deterministic, so it runs in CI against canned replies even though the live suite does not.
 *
 * <p>Text is compared in Unicode NFC and case-insensitively, because Vietnamese can arrive decomposed. The
 * answer-text rules need an on-topic reply; when the reply is off-topic they fail with a clear detail.
 */
public final class PromptSuiteRules {

    public static final String LANG_VI = "vi";
    public static final String LANG_EN = "en";
    public static final String LANG_UNKNOWN = "unknown";

    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Pattern FENCED_CODE = Pattern.compile("```.*?```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final String LEADING_MARKUP = "#*_>-\"'`";
    private static final String VI_DIACRITICS = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợ"
            + "ùúủũụưừứửữựỳýỷỹỵđ";
    private static final double DIACRITIC_WEIGHT = 0.25;
    private static final double MIN_EVIDENCE = 2.0;
    private static final Set<String> VI_WORDS = Set.of("và", "là", "của", "một", "không", "những", "được", "cho",
            "các", "với", "này", "trong", "khi", "có", "để", "bạn", "mình", "câu", "thì", "nên", "vì", "nhưng",
            "cũng", "đã", "sẽ", "đang", "rất", "như", "từ", "nếu", "hoặc", "tôi", "gì", "nào", "đó", "người",
            "dùng", "nghĩa", "ví", "dụ", "đúng", "sai");
    private static final Set<String> EN_WORDS = Set.of("the", "is", "are", "was", "were", "be", "been", "a", "an",
            "and", "or", "of", "to", "in", "on", "for", "with", "that", "this", "it", "you", "your", "not", "as",
            "at", "by", "can", "should", "use", "when", "if", "but", "so", "do", "does", "have", "has", "they",
            "we", "from", "which", "what");

    private PromptSuiteRules() {
    }

    /** One rule's outcome; {@code detail} says why it failed (or what was measured). */
    public record RuleResult(String rule, boolean passed, String detail) {
    }

    /** Runs every rule the case defines. {@code markdown} is the raw model text, empty when off-topic. */
    public static List<RuleResult> evaluate(PromptSuiteCase testCase, boolean onTopic, String markdown) {
        PromptSuiteCase.PassRule rule = testCase.passRule();
        String text = nfc(markdown == null ? "" : markdown);
        List<RuleResult> results = new ArrayList<>();
        if (rule.refusalNotEmpty()) {
            String refusal = ChatPrompts.refusal(testCase.language());
            results.add(new RuleResult("refusalNotEmpty", refusal != null && !refusal.isBlank(),
                    "fixed refusal for " + testCase.language()));
        }
        if (notEmpty(rule.mustNotContain())) {
            results.add(mustNotContain(rule.mustNotContain(), text));
        }
        if (rule.answerLanguage() != null) {
            results.add(needsAnswer("answerLanguage", onTopic, () -> answerLanguage(rule.answerLanguage(), text)));
        }
        if (notEmpty(rule.mustNotStartWithPraise())) {
            results.add(needsAnswer("mustNotStartWithPraise", onTopic,
                    () -> noPraise(rule.mustNotStartWithPraise(), text)));
        }
        if (rule.maxWords() != null) {
            results.add(needsAnswer("maxWords", onTopic, () -> maxWords(rule.maxWords(), text)));
        }
        if (notEmpty(rule.mustContainAny())) {
            results.add(needsAnswer("mustContainAny", onTopic, () -> containsAny(rule.mustContainAny(), text)));
        }
        return List.copyOf(results);
    }

    public static boolean allPassed(List<RuleResult> results) {
        return results.stream().allMatch(RuleResult::passed);
    }

    // ---- individual rules ----------------------------------------------------------------------------------

    private static RuleResult needsAnswer(String name, boolean onTopic, Supplier<RuleResult> check) {
        return onTopic ? check.get() : new RuleResult(name, false, "reply was off-topic, there is no answer to check");
    }

    private static RuleResult mustNotContain(List<String> banned, String text) {
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String item : banned) {
            if (haystack.contains(nfc(item).toLowerCase(Locale.ROOT))) {
                return new RuleResult("mustNotContain", false, "contains banned text '" + item + "'");
            }
        }
        return new RuleResult("mustNotContain", true, "none of " + banned.size() + " banned strings");
    }

    private static RuleResult containsAny(List<String> wanted, String text) {
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String item : wanted) {
            if (haystack.contains(nfc(item).toLowerCase(Locale.ROOT))) {
                return new RuleResult("mustContainAny", true, "found '" + item + "'");
            }
        }
        return new RuleResult("mustContainAny", false, "none of " + wanted + " found");
    }

    private static RuleResult maxWords(int limit, String text) {
        int words = wordCount(text);
        return new RuleResult("maxWords", words <= limit, words + " words, limit " + limit);
    }

    private static RuleResult noPraise(List<String> openers, String text) {
        String start = stripLeadingMarkup(text).toLowerCase(Locale.ROOT);
        for (String opener : openers) {
            String banned = nfc(opener).toLowerCase(Locale.ROOT);
            if (start.startsWith(banned) && endsAtWordBoundary(start, banned.length())) {
                return new RuleResult("mustNotStartWithPraise", false, "opens with '" + opener + "'");
            }
        }
        return new RuleResult("mustNotStartWithPraise", true, "no banned opener");
    }

    private static RuleResult answerLanguage(String expected, String text) {
        String detected = detectLanguage(text);
        return new RuleResult("answerLanguage", expected.equals(detected),
                "expected " + expected + ", detected " + detected);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /**
     * Vietnamese or English by a simple score: common stopwords of each language, plus a small weight per
     * Vietnamese-only letter. Code and inline code are ignored. Too little evidence, or a tie, is "unknown".
     */
    public static String detectLanguage(String markdown) {
        String text = INLINE_CODE.matcher(FENCED_CODE.matcher(nfc(markdown)).replaceAll(" ")).replaceAll(" ")
                .toLowerCase(Locale.ROOT);
        double vi = 0;
        double en = 0;
        Matcher words = WORD.matcher(text);
        while (words.find()) {
            String word = words.group();
            if (VI_WORDS.contains(word)) {
                vi++;
            }
            if (EN_WORDS.contains(word)) {
                en++;
            }
        }
        long diacritics = text.chars().filter(c -> VI_DIACRITICS.indexOf(c) >= 0).count();
        vi += diacritics * DIACRITIC_WEIGHT;
        if (vi + en < MIN_EVIDENCE || vi == en) {
            return LANG_UNKNOWN;
        }
        return vi > en ? LANG_VI : LANG_EN;
    }

    /** Whitespace-separated tokens that hold at least one letter or digit (bullets and rules do not count). */
    public static int wordCount(String text) {
        int count = 0;
        for (String token : nfc(text).trim().split("\\s+")) {
            if (token.codePoints().anyMatch(Character::isLetterOrDigit)) {
                count++;
            }
        }
        return count;
    }

    private static String stripLeadingMarkup(String text) {
        int i = 0;
        while (i < text.length() && (Character.isWhitespace(text.charAt(i))
                || LEADING_MARKUP.indexOf(text.charAt(i)) >= 0)) {
            i++;
        }
        return text.substring(i);
    }

    private static boolean endsAtWordBoundary(String text, int index) {
        return index >= text.length() || !Character.isLetterOrDigit(text.charAt(index));
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static String nfc(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }
}
