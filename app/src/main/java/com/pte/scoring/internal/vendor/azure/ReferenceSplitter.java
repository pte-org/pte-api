package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.vendor.speech.TokenAlignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cuts a reference text into one part per audio chunk, following where each
 * chunk's recognized words fall in the reference. Azure scores a chunk against
 * the text it is given, so each chunk must get only the words it was heard
 * saying, in the reference's own spelling and punctuation.
 */
final class ReferenceSplitter {

    private ReferenceSplitter() {
    }

    static List<String> split(List<String> heardByChunk, String reference) {
        String whole = reference.trim();
        if (heardByChunk.size() <= 1) {
            return heardByChunk.isEmpty() ? List.of() : List.of(whole);
        }
        String[] words = whole.isEmpty() ? new String[0] : whole.split("\\s+");
        int[] bounds = bounds(heardByChunk, words);
        List<String> parts = new ArrayList<>(heardByChunk.size());
        for (int chunk = 0; chunk < heardByChunk.size(); chunk++) {
            parts.add(String.join(" ", Arrays.copyOfRange(words, bounds[chunk], bounds[chunk + 1])));
        }
        return parts;
    }

    private static int[] bounds(List<String> heardByChunk, String[] words) {
        List<String> reference = new ArrayList<>();
        List<Integer> wordOf = new ArrayList<>();
        for (int word = 0; word < words.length; word++) {
            for (String token : TokenAlignment.normalize(words[word])) {
                reference.add(token);
                wordOf.add(word);
            }
        }
        List<String> heard = new ArrayList<>();
        List<Integer> chunkOf = new ArrayList<>();
        for (int chunk = 0; chunk < heardByChunk.size(); chunk++) {
            for (String token : TokenAlignment.normalize(heardByChunk.get(chunk))) {
                heard.add(token);
                chunkOf.add(chunk);
            }
        }
        int[] lastWord = new int[heardByChunk.size()];
        Arrays.fill(lastWord, -1);
        int[][] lcs = lcsTable(heard, reference);
        int i = 0;
        int j = 0;
        while (i < heard.size() && j < reference.size()) {
            if (heard.get(i).equals(reference.get(j))) {
                lastWord[chunkOf.get(i)] = wordOf.get(j);
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        int[] bounds = new int[heardByChunk.size() + 1];
        for (int chunk = 0; chunk < heardByChunk.size() - 1; chunk++) {
            bounds[chunk + 1] = Math.max(bounds[chunk], lastWord[chunk] + 1);
        }
        bounds[heardByChunk.size()] = words.length;
        return bounds;
    }

    private static int[][] lcsTable(List<String> a, List<String> b) {
        int[][] table = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                table[i][j] = a.get(i).equals(b.get(j))
                        ? table[i + 1][j + 1] + 1
                        : Math.max(table[i + 1][j], table[i][j + 1]);
            }
        }
        return table;
    }
}
