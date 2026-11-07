package com.prediction.socialnetwork.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.DedupStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SocialNetworkTextAnalysisTest {

    private final SocialNetworkTextAnalysis analysis = new SocialNetworkTextAnalysis();

    @Test
    void preservesOriginalTextWhileNormalizingUrlsMentionsAndStopwords() {
        var result = analysis.analyze(List.of(
                tweet("1", "42", "The Research @team https://example.com").withAnalysisIncluded(true),
                tweet("2", "42", "  ").withAnalysisIncluded(true),
                tweet("3", null, "研究 数据").withAnalysisIncluded(true)));

        assertEquals(3, result.analysisSampleCount());
        assertEquals(2, result.validTextSampleCount());
        assertEquals(1, result.emptyTextSampleCount());

        var document = result.documents().get(0);
        assertEquals("The Research @team https://example.com", document.textOriginal());
        assertEquals("The Research @team https://example.com", document.textNormalized());
        assertEquals(List.of("research"), document.tokens());
        assertEquals("en", document.language());
        assertEquals("text_tokenized", document.status());
        assertTrue(result.documents().get(1).tokens().isEmpty());
    }

    @Test
    void calculatesKeywordDocumentFrequencyAndTfidfOnlyForIncludedDocuments() {
        var excluded = tweet("excluded", "9", "research")
                .withAnalysisIncluded(false);
        var result = analysis.analyze(List.of(
                tweet("1", "42", "research research").withAnalysisIncluded(true),
                tweet("2", "43", "research data").withAnalysisIncluded(true),
                excluded));

        var research = result.keywords().stream()
                .filter(keyword -> keyword.term().equals("research"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, research.termFrequency());
        assertEquals(2, research.documentFrequency());
        assertEquals(2, research.userFrequency());
        assertTrue(result.tfidf().stream().anyMatch(value ->
                value.tweetId().equals("1") && value.term().equals("research")));
        assertFalse(result.tfidf().stream()
                .anyMatch(value -> value.tweetId().equals("excluded")));
        assertEquals("text-processing-v1.0", result.textProcessingVersion());
    }

    @Test
    void returnsTheSameTopHundredTfidfItemsAsFullSorting() {
        List<NormalizedTweet> tweets = new ArrayList<>();
        for (int documentIndex = 0; documentIndex < 40; documentIndex++) {
            String uniqueTerms = "term" + alphabetic(documentIndex * 4)
                    + " term" + alphabetic(documentIndex * 4 + 1)
                    + " term" + alphabetic(documentIndex * 4 + 2)
                    + " term" + alphabetic(documentIndex * 4 + 3);
            tweets.add(tweet("tweet-" + documentIndex, "author-" + documentIndex,
                    "shared " + uniqueTerms).withAnalysisIncluded(true));
        }

        var result = analysis.analyze(tweets);
        Map<String, Integer> documentFrequencies = new HashMap<>();
        for (var document : result.documents()) {
            document.tokens().stream().distinct()
                    .forEach(term -> documentFrequencies.merge(term, 1, Integer::sum));
        }
        List<SocialNetworkTextAnalysis.Tfidf> allItems = new ArrayList<>();
        for (var document : result.documents()) {
            Map<String, Integer> frequencies = new HashMap<>();
            document.tokens().forEach(term -> frequencies.merge(term, 1, Integer::sum));
            frequencies.forEach((term, frequency) -> {
                int documentFrequency = documentFrequencies.get(term);
                double termFrequency = 1.0 + Math.log(frequency);
                double inverseDocumentFrequency = Math.log(
                        (result.validTextSampleCount() + 1.0)
                                / (documentFrequency + 1.0)) + 1.0;
                allItems.add(new SocialNetworkTextAnalysis.Tfidf(
                        document.tweetId(),
                        term,
                        "en",
                        termFrequency * inverseDocumentFrequency,
                        frequency,
                        documentFrequency));
            });
        }
        Comparator<SocialNetworkTextAnalysis.Tfidf> comparator =
                Comparator.comparingDouble(SocialNetworkTextAnalysis.Tfidf::score).reversed()
                        .thenComparing(
                                SocialNetworkTextAnalysis.Tfidf::documentFrequency,
                                Comparator.reverseOrder())
                        .thenComparing(SocialNetworkTextAnalysis.Tfidf::term)
                        .thenComparing(SocialNetworkTextAnalysis.Tfidf::tweetId);
        List<SocialNetworkTextAnalysis.Tfidf> expected = allItems.stream()
                .sorted(comparator)
                .limit(100)
                .toList();

        assertEquals(100, result.tfidf().size());
        assertEquals(expected, result.tfidf());
    }

    @Test
    void mergesParallelKeywordCountsAndUsersAcrossChunks() {
        List<NormalizedTweet> tweets = new ArrayList<>();
        for (int documentIndex = 0; documentIndex < 20_000; documentIndex++) {
            tweets.add(tweet(
                    "parallel-" + documentIndex,
                    "author-" + documentIndex % 5,
                    "shared shared research research").withAnalysisIncluded(true));
        }

        var result = analysis.analyze(tweets);
        var research = result.keywords().stream()
                .filter(keyword -> keyword.term().equals("research"))
                .findFirst()
                .orElseThrow();
        assertEquals(40_000, research.termFrequency());
        assertEquals(20_000, research.documentFrequency());
        assertEquals(5, research.userFrequency());
        assertEquals(20_000, research.sampleCount());

        List<String> expectedTweetIds = tweets.stream()
                .map(NormalizedTweet::tweetId)
                .sorted()
                .limit(100)
                .toList();
        assertEquals(expectedTweetIds, result.tfidf().stream()
                .map(SocialNetworkTextAnalysis.Tfidf::tweetId)
                .toList());
        assertTrue(result.tfidf().stream()
                .allMatch(item -> item.term().equals("research")));
    }

    private static String alphabetic(int value) {
        StringBuilder result = new StringBuilder();
        do {
            result.append((char) ('a' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return result.reverse().toString();
    }

    private static TestTweet tweet(String id, String author, String text) {
        return new TestTweet(id, author, text);
    }

    private record TestTweet(String id, String author, String text) {
        private NormalizedTweet withAnalysisIncluded(boolean included) {
            return new NormalizedTweet(
                    "tweets.csv",
                    2,
                    id,
                    author,
                    "builtin",
                    Map.of("tweet_id", id, "user_id", author == null ? "" : author, "text", text),
                    author == null ? AssociationStatus.UNASSIGNED_SOURCE : AssociationStatus.LINKED,
                    DedupStatus.UNIQUE,
                    NormalizationStatus.NORMALIZED,
                    included,
                    Set.of());
        }
    }
}
