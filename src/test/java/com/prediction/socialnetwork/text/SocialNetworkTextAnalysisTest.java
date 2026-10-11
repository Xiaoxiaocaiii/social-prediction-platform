package com.prediction.socialnetwork.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.DedupStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
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
