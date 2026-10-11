package com.prediction.socialnetwork.text;

import com.prediction.socialnetwork.normalization.NormalizedTweet;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SocialNetworkTextAnalysis {

    public static final String TEXT_PROCESSING_VERSION = "text-processing-v1.0";
    public static final String STOPWORDS_VERSION = "stopwords-v1.0";
    public static final String TOKENIZER_VERSION = "tokenizer-v1.0";
    public static final String TFIDF_VERSION = "tfidf-v1.0";

    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MENTION = Pattern.compile("@[\\w.\\-]+");
    private static final Pattern ENGLISH = Pattern.compile("[A-Za-z]+(?:[-'][A-Za-z]+)*");
    private static final Pattern CHINESE = Pattern.compile("[\\u4E00-\\u9FFF]+");
    private static final Set<String> EN_STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "has", "have", "in", "is", "it", "of", "on", "or", "that", "the",
            "to", "was", "were", "with", "this", "these", "they", "their");
    private static final Set<String> ZH_STOPWORDS = Set.of(
            "的", "了", "和", "是", "在", "有", "与", "及", "也", "就", "都",
            "而", "被", "将", "这", "那", "一个", "我们", "你们", "他们");

    public Result analyze(List<NormalizedTweet> tweets) {
        List<Document> documents = tweets.stream()
                .filter(NormalizedTweet::analysisIncluded)
                .map(this::document)
                .toList();
        List<Document> valid = documents.stream()
                .filter(document -> !document.tokens().isEmpty())
                .toList();
        Map<String, Keyword> keywords = keywordStats(valid);
        List<Tfidf> tfidf = tfidf(valid);
        Map<String, Integer> languageCounts = new LinkedHashMap<>();
        for (String language : List.of("zh", "en", "mixed", "other", "unknown")) {
            languageCounts.put(language, (int) documents.stream()
                    .filter(document -> document.language().equals(language)).count());
        }
        int empty = (int) documents.stream()
                .filter(document -> document.status().equals("text_empty")).count();
        return new Result(
                documents,
                keywords.values().stream().sorted(keywordComparator()).toList(),
                tfidf,
                languageCounts,
                documents.size(),
                valid.size(),
                empty,
                TEXT_PROCESSING_VERSION,
                STOPWORDS_VERSION,
                TOKENIZER_VERSION,
                TFIDF_VERSION);
    }

    private Document document(NormalizedTweet tweet) {
        String original = tweet.fields().getOrDefault("text", "");
        String normalized = Normalizer.normalize(original, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim();
        String analysisText = URL.matcher(normalized).replaceAll(" ")
                .replaceAll(MENTION.pattern(), " ");
        String language = language(analysisText);
        List<String> tokens = tokens(analysisText);
        String status = normalized.isBlank() ? "text_empty"
                : tokens.isEmpty() ? "text_normalized" : "text_tokenized";
        return new Document(tweet.tweetId(), tweet.authorUserId(), original, normalized,
                language, tokens, status, tweet.sourceFileName(), tweet.sourceRowNumber());
    }

    private static String language(String text) {
        int zh = 0;
        int en = 0;
        int other = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (codePoint >= 0x4E00 && codePoint <= 0x9FFF) {
                zh++;
            } else if ((codePoint >= 'A' && codePoint <= 'Z')
                    || (codePoint >= 'a' && codePoint <= 'z')) {
                en++;
            } else if (Character.isLetter(codePoint)) {
                other++;
            }
            i += Character.charCount(codePoint);
        }
        if (zh >= 2 && en >= 2) {
            return "mixed";
        }
        if (zh >= 2) {
            return "zh";
        }
        if (en >= 2) {
            return "en";
        }
        return other > 0 ? "other" : "unknown";
    }

    private static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        Matcher english = ENGLISH.matcher(text);
        while (english.find()) {
            String token = english.group().toLowerCase(Locale.ROOT);
            if (!EN_STOPWORDS.contains(token)) {
                result.add(token);
            }
        }
        Matcher chinese = CHINESE.matcher(text);
        while (chinese.find()) {
            String value = chinese.group();
            for (int i = 0; i + 1 < value.length(); i += 2) {
                String token = value.substring(i, i + 2);
                if (!ZH_STOPWORDS.contains(token)) {
                    result.add(token);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static Map<String, Keyword> keywordStats(List<Document> documents) {
        Map<String, MutableKeyword> values = new HashMap<>();
        for (Document document : documents) {
            Set<String> seen = new HashSet<>();
            for (String token : document.tokens()) {
                MutableKeyword keyword = values.computeIfAbsent(token,
                        ignored -> new MutableKeyword(token, languageOf(token)));
                keyword.termFrequency++;
                if (seen.add(token)) {
                    keyword.documentFrequency++;
                    if (document.authorUserId() != null) {
                        keyword.userIds.add(document.authorUserId());
                    }
                }
            }
        }
        Map<String, Keyword> result = new HashMap<>();
        values.forEach((term, value) -> result.put(term, value.toRecord()));
        return result;
    }

    private static List<Tfidf> tfidf(List<Document> documents) {
        int totalDocuments = documents.size();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Document document : documents) {
            document.tokens().stream().distinct()
                    .forEach(token -> documentFrequency.merge(token, 1, Integer::sum));
        }
        List<Tfidf> result = new ArrayList<>();
        for (Document document : documents) {
            Map<String, Integer> frequencies = new HashMap<>();
            document.tokens().forEach(token -> frequencies.merge(token, 1, Integer::sum));
            frequencies.forEach((term, frequency) -> {
                double tf = 1.0 + Math.log(frequency);
                double idf = Math.log((totalDocuments + 1.0)
                        / (documentFrequency.get(term) + 1.0)) + 1.0;
                result.add(new Tfidf(document.tweetId(), term, languageOf(term),
                        tf * idf, frequency, documentFrequency.get(term)));
            });
        }
        result.sort(Comparator.comparingDouble(Tfidf::score).reversed()
                .thenComparing(Tfidf::documentFrequency, Comparator.reverseOrder())
                .thenComparing(Tfidf::term)
                .thenComparing(Tfidf::tweetId));
        return result;
    }

    private static Comparator<Keyword> keywordComparator() {
        return Comparator.comparingInt(Keyword::termFrequency).reversed()
                .thenComparingInt(Keyword::documentFrequency).reversed()
                .thenComparing(Keyword::term);
    }

    private static String languageOf(String token) {
        return token.codePoints().allMatch(codePoint ->
                codePoint >= 0x4E00 && codePoint <= 0x9FFF) ? "zh" : "en";
    }

    public record Result(
            List<Document> documents,
            List<Keyword> keywords,
            List<Tfidf> tfidf,
            Map<String, Integer> languageCounts,
            int analysisSampleCount,
            int validTextSampleCount,
            int emptyTextSampleCount,
            String textProcessingVersion,
            String stopwordsVersion,
            String tokenizerVersion,
            String tfidfVersion) {
    }

    public record Document(
            String tweetId,
            String authorUserId,
            String textOriginal,
            String textNormalized,
            String language,
            List<String> tokens,
            String status,
            String sourceFile,
            int sourceRowNumber) {
    }

    public record Keyword(
            String term,
            String language,
            int termFrequency,
            int documentFrequency,
            int userFrequency,
            int sampleCount) {
    }

    public record Tfidf(
            String tweetId,
            String term,
            String language,
            double score,
            int termFrequency,
            int documentFrequency) {
    }

    private static final class MutableKeyword {
        private final String term;
        private final String language;
        private int termFrequency;
        private int documentFrequency;
        private final Set<String> userIds = new HashSet<>();

        private MutableKeyword(String term, String language) {
            this.term = term;
            this.language = language;
        }

        private Keyword toRecord() {
            return new Keyword(term, language, termFrequency, documentFrequency,
                    userIds.size(), documentFrequency);
        }
    }
}
