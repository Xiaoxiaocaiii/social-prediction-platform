package com.prediction.socialnetwork.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.reader.FileReadStatus;
import com.prediction.socialnetwork.reader.RawRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkNormalizerTest {

    private final SocialNetworkNormalizer normalizer = new SocialNetworkNormalizer();

    @Test
    void keepsExternalNodesAndExcludesDuplicateAndSelfLoopEdges() {
        var result = normalizer.normalize(
                List.of(user("1")),
                List.of(
                        relation("1", "2"),
                        relation("1", "2"),
                        relation("1", "1")),
                List.of());

        assertEquals(3, result.relationships().size());
        assertEquals(1, result.relationships().stream()
                .filter(value -> value.relationStatus() == RelationStatus.VALID).count());
        assertEquals(1, result.report().duplicateRelationshipCount());
        assertEquals(1, result.report().selfLoopCount());
        assertEquals(1, result.report().externalNodeCount());
        assertTrue(result.relationships().get(0).analysisIncluded());
        assertFalse(result.relationships().get(1).analysisIncluded());
    }

    @Test
    void expandsBuiltinFollowingListAndKeepsArrayIndex() {
        RawRecord record = raw("following.csv", 4, Map.of(
                "source_user_id", "10",
                "following_list", "['20', '30']"));

        var result = normalizer.normalize(List.of(user("10")), List.of(record), List.of());

        assertEquals(2, result.relationships().size());
        assertEquals(0, result.relationships().get(0).sourceArrayIndex());
        assertEquals(1, result.relationships().get(1).sourceArrayIndex());
        assertEquals("20", result.relationships().get(0).targetUserId());
    }

    @Test
    void linksTweetsAndSeparatesUnassignedSourceAndConflicts() {
        RawRecord linked = tweet("100", "1", "hello");
        RawRecord duplicate = tweet("100", "1", "hello");
        RawRecord conflict = tweet("200", "1", "a");
        RawRecord conflictOther = tweet("200", "1", "b");
        RawRecord unassigned = raw("tweets.csv", 9, Map.of(
                "tweet_id", "300",
                "source_folder", "Arab_API",
                "created_at", "2025-01-01",
                "text", "external"));

        var result = normalizer.normalize(
                List.of(user("1")),
                List.of(),
                List.of(linked, duplicate, conflict, conflictOther, unassigned));

        assertEquals(5, result.tweets().size());
        assertEquals(1, result.tweets().stream()
                .filter(value -> value.dedupStatus() == DedupStatus.DUPLICATE_EXCLUDED_ANALYSIS).count());
        assertEquals(2, result.report().conflictDuplicateCount());
        assertEquals(1, result.report().unassignedSourceCount());
        assertTrue(result.tweets().get(0).analysisIncluded());
        assertFalse(result.tweets().get(2).analysisIncluded());
        assertEquals(AssociationStatus.UNASSIGNED_SOURCE, result.tweets().get(4).authorStatus());
    }

    private static RawRecord user(String id) {
        return raw("users.csv", 2, Map.of("user_id", id, "username", "user-" + id));
    }

    private static RawRecord relation(String source, String target) {
        return raw("relationships.csv", 2, Map.of(
                "source_user_id", source,
                "target_user_id", target));
    }

    private static RawRecord tweet(String id, String author, String text) {
        return raw("tweets.csv", 2, Map.of(
                "tweet_id", id,
                "user_id", author,
                "created_at", "2025-01-01",
                "text", text));
    }

    private static RawRecord raw(String file, int row, Map<String, String> fields) {
        return new RawRecord(file, row, fields, FileReadStatus.READ_OK);
    }
}
