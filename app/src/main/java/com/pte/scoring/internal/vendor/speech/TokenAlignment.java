package com.pte.scoring.internal.vendor.speech;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word alignment of a spoken transcript against its reference, ported from the
 * research POC so both give the same numbers: tokens are casefolded words
 * (apostrophes kept inside a word), matched like Python's
 * {@code difflib.SequenceMatcher} with autojunk off, and scored as the F1 of
 * reference recall and candidate precision on a 0-100 scale, one decimal.
 */
public final class TokenAlignment {

    private static final Pattern WORD = Pattern.compile("[^\\W_]+(?:'[^\\W_]+)*", Pattern.UNICODE_CHARACTER_CLASS);

    public record Block(int referenceStart, int candidateStart, int size) {
    }

    private final List<Block> blocks;
    private final List<String> missingTokens;
    private final List<String> extraTokens;
    private final double score;
    private final double precision;
    private final double recall;

    private TokenAlignment(List<Block> blocks, List<String> missingTokens, List<String> extraTokens,
                           double score, double precision, double recall) {
        this.blocks = blocks;
        this.missingTokens = missingTokens;
        this.extraTokens = extraTokens;
        this.score = score;
        this.precision = precision;
        this.recall = recall;
    }

    public static List<String> normalize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT).replace('’', '\''));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /** @throws IllegalArgumentException when the reference holds no words */
    public static TokenAlignment align(String reference, String candidate) {
        List<String> ref = normalize(reference);
        List<String> cand = normalize(candidate);
        if (ref.isEmpty()) {
            throw new IllegalArgumentException("reference text must contain at least one word");
        }
        List<Block> blocks = matchingBlocks(ref, cand);
        boolean[] refMatched = new boolean[ref.size()];
        boolean[] candMatched = new boolean[cand.size()];
        int matched = 0;
        for (Block block : blocks) {
            for (int offset = 0; offset < block.size(); offset++) {
                refMatched[block.referenceStart() + offset] = true;
                candMatched[block.candidateStart() + offset] = true;
            }
            matched += block.size();
        }
        double recall = (double) matched / ref.size();
        double precision = cand.isEmpty() ? 0.0 : (double) matched / cand.size();
        double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        return new TokenAlignment(List.copyOf(blocks), unmatched(ref, refMatched), unmatched(cand, candMatched),
                percent(f1), percent(precision), percent(recall));
    }

    public double score() {
        return score;
    }

    public double precision() {
        return precision;
    }

    public double recall() {
        return recall;
    }

    public List<String> missingTokens() {
        return missingTokens;
    }

    public List<String> extraTokens() {
        return extraTokens;
    }

    public List<Block> blocks() {
        return blocks;
    }

    private static List<String> unmatched(List<String> tokens, boolean[] matched) {
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!matched[i]) {
                rest.add(tokens.get(i));
            }
        }
        return List.copyOf(rest);
    }

    /** Rounds the exact binary value half-even, which is what Python's {@code round(x, 1)} does. */
    private static double percent(double fraction) {
        return new BigDecimal(fraction * 100.0).setScale(1, RoundingMode.HALF_EVEN).doubleValue();
    }

    // --- difflib.SequenceMatcher.get_matching_blocks, without junk heuristics ---

    private static List<Block> matchingBlocks(List<String> a, List<String> b) {
        Map<String, List<Integer>> b2j = new HashMap<>();
        for (int j = 0; j < b.size(); j++) {
            b2j.computeIfAbsent(b.get(j), key -> new ArrayList<>()).add(j);
        }
        List<Block> found = new ArrayList<>();
        // Explicit stack instead of recursion; ranges are searched in the same order difflib uses.
        List<int[]> queue = new ArrayList<>();
        queue.add(new int[] {0, a.size(), 0, b.size()});
        while (!queue.isEmpty()) {
            int[] range = queue.removeLast();
            Block block = longestMatch(a, b2j, range[0], range[1], range[2], range[3]);
            if (block.size() == 0) {
                continue;
            }
            found.add(block);
            if (range[0] < block.referenceStart() && range[2] < block.candidateStart()) {
                queue.add(new int[] {range[0], block.referenceStart(), range[2], block.candidateStart()});
            }
            int aEnd = block.referenceStart() + block.size();
            int bEnd = block.candidateStart() + block.size();
            if (aEnd < range[1] && bEnd < range[3]) {
                queue.add(new int[] {aEnd, range[1], bEnd, range[3]});
            }
        }
        found.sort((x, y) -> x.referenceStart() != y.referenceStart()
                ? Integer.compare(x.referenceStart(), y.referenceStart())
                : Integer.compare(x.candidateStart(), y.candidateStart()));
        return mergeAdjacent(found);
    }

    private static Block longestMatch(List<String> a, Map<String, List<Integer>> b2j,
                                      int alo, int ahi, int blo, int bhi) {
        int bestI = alo;
        int bestJ = blo;
        int bestSize = 0;
        Map<Integer, Integer> j2len = new HashMap<>();
        for (int i = alo; i < ahi; i++) {
            Map<Integer, Integer> newJ2len = new HashMap<>();
            for (int j : b2j.getOrDefault(a.get(i), List.of())) {
                if (j < blo) {
                    continue;
                }
                if (j >= bhi) {
                    break;
                }
                int k = j2len.getOrDefault(j - 1, 0) + 1;
                newJ2len.put(j, k);
                if (k > bestSize) {
                    bestI = i - k + 1;
                    bestJ = j - k + 1;
                    bestSize = k;
                }
            }
            j2len = newJ2len;
        }
        return new Block(bestI, bestJ, bestSize);
    }

    private static List<Block> mergeAdjacent(List<Block> blocks) {
        List<Block> merged = new ArrayList<>();
        for (Block block : blocks) {
            Block last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && last.referenceStart() + last.size() == block.referenceStart()
                    && last.candidateStart() + last.size() == block.candidateStart()) {
                merged.set(merged.size() - 1,
                        new Block(last.referenceStart(), last.candidateStart(), last.size() + block.size()));
            } else {
                merged.add(block);
            }
        }
        return merged;
    }
}
