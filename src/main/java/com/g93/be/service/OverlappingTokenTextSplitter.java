package com.g93.be.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link TextSplitter} that windows text by token count like Spring AI's
 * {@code TokenTextSplitter}, but repeats a trailing window of tokens
 * ({@code chunkOverlap}) at the start of the next chunk instead of starting the
 * next window exactly where the previous one ended.
 *
 * <p>Without overlap, a sentence or table row that happens to land on a chunk
 * boundary is split so that neither resulting chunk contains it whole, and a
 * similarity search for that exact content can miss both halves. Overlap
 * guarantees any span of text up to {@code chunkOverlap} tokens long survives
 * intact inside at least one chunk.
 */
public class OverlappingTokenTextSplitter extends TextSplitter {

    private static final List<Character> DEFAULT_PUNCTUATION_MARKS = List.of('.', '?', '!', '\n');
    private static final EncodingType DEFAULT_ENCODING_TYPE = EncodingType.CL100K_BASE;

    private final Encoding encoding;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int minChunkSizeChars;
    private final int minChunkLengthToEmbed;
    private final int maxNumChunks;
    private final boolean keepSeparator;
    private final List<Character> punctuationMarks;

    private OverlappingTokenTextSplitter(
            EncodingType encodingType,
            int chunkSize,
            int chunkOverlap,
            int minChunkSizeChars,
            int minChunkLengthToEmbed,
            int maxNumChunks,
            boolean keepSeparator,
            List<Character> punctuationMarks) {
        Assert.isTrue(chunkSize > 0, "chunkSize must be positive");
        Assert.isTrue(chunkOverlap >= 0 && chunkOverlap < chunkSize,
                "chunkOverlap must be non-negative and smaller than chunkSize");
        Assert.notEmpty(punctuationMarks, "punctuationMarks must not be empty");
        EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
        this.encoding = registry.getEncoding(encodingType);
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.minChunkSizeChars = minChunkSizeChars;
        this.minChunkLengthToEmbed = minChunkLengthToEmbed;
        this.maxNumChunks = maxNumChunks;
        this.keepSeparator = keepSeparator;
        this.punctuationMarks = punctuationMarks;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> tokens = encode(text);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int numChunks = 0;
        while (start < tokens.size() && numChunks < this.maxNumChunks) {
            int end = Math.min(start + this.chunkSize, tokens.size());
            String chunkText = decode(tokens.subList(start, end));

            if (chunkText.trim().isEmpty()) {
                start = end;
                continue;
            }

            boolean reachedEndOfText = end >= tokens.size();
            if (!reachedEndOfText) {
                int lastPunctuation = getLastPunctuationIndex(chunkText);
                if (lastPunctuation != -1 && lastPunctuation > this.minChunkSizeChars) {
                    chunkText = chunkText.substring(0, lastPunctuation + 1);
                }
            }

            String chunkTextToAppend = this.keepSeparator
                    ? chunkText.trim()
                    : chunkText.replace(System.lineSeparator(), " ").trim();
            if (chunkTextToAppend.length() > this.minChunkLengthToEmbed) {
                chunks.add(chunkTextToAppend);
            }
            numChunks++;

            if (reachedEndOfText) {
                break;
            }

            int consumedTokens = encode(chunkText).size();
            if (consumedTokens <= 0) {
                consumedTokens = end - start;
            }
            start += Math.max(1, consumedTokens - this.chunkOverlap);
        }

        return chunks;
    }

    protected int getLastPunctuationIndex(String chunkText) {
        int maxLastPunctuation = -1;
        for (Character punctuationMark : this.punctuationMarks) {
            maxLastPunctuation = Math.max(maxLastPunctuation, chunkText.lastIndexOf(punctuationMark));
        }
        return maxLastPunctuation;
    }

    private List<Integer> encode(String text) {
        return this.encoding.encode(text).boxed();
    }

    private String decode(List<Integer> tokens) {
        IntArrayList array = new IntArrayList(tokens.size());
        tokens.forEach(array::add);
        return this.encoding.decode(array);
    }

    public static final class Builder {

        private EncodingType encodingType = DEFAULT_ENCODING_TYPE;
        private int chunkSize = 700;
        private int chunkOverlap = 120;
        private int minChunkSizeChars = 250;
        private int minChunkLengthToEmbed = 20;
        private int maxNumChunks = 10_000;
        private boolean keepSeparator = true;
        private List<Character> punctuationMarks = DEFAULT_PUNCTUATION_MARKS;

        private Builder() {
        }

        public Builder encodingType(EncodingType encodingType) {
            this.encodingType = encodingType;
            return this;
        }

        public Builder chunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder chunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
            return this;
        }

        public Builder minChunkSizeChars(int minChunkSizeChars) {
            this.minChunkSizeChars = minChunkSizeChars;
            return this;
        }

        public Builder minChunkLengthToEmbed(int minChunkLengthToEmbed) {
            this.minChunkLengthToEmbed = minChunkLengthToEmbed;
            return this;
        }

        public Builder maxNumChunks(int maxNumChunks) {
            this.maxNumChunks = maxNumChunks;
            return this;
        }

        public Builder keepSeparator(boolean keepSeparator) {
            this.keepSeparator = keepSeparator;
            return this;
        }

        public Builder punctuationMarks(List<Character> punctuationMarks) {
            this.punctuationMarks = punctuationMarks;
            return this;
        }

        public OverlappingTokenTextSplitter build() {
            return new OverlappingTokenTextSplitter(this.encodingType, this.chunkSize, this.chunkOverlap,
                    this.minChunkSizeChars, this.minChunkLengthToEmbed, this.maxNumChunks, this.keepSeparator,
                    this.punctuationMarks);
        }
    }
}
