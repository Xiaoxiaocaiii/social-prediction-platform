package com.prediction.socialnetwork.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkCsvSerializerTest {

    @Test
    void preservesChineseHeadersStringIdsAndMultilineTweetText() {
        Map<String, Object> tweet = new LinkedHashMap<>();
        tweet.put("tweet_id", "9007199254740993123");
        tweet.put("text", "第一行，含逗号\n第二行，含\"引号\"");

        String csv = new SocialNetworkCsvSerializer().serialize(
                List.of("推文 ID", "推文原文"),
                List.of(Map.of(
                        "推文 ID", tweet.get("tweet_id"),
                        "推文原文", tweet.get("text"))));

        assertEquals("\uFEFF推文 ID,推文原文\r\n"
                + "9007199254740993123,\"第一行，含逗号\n第二行，含\"\"引号\"\"\"\r\n", csv);
    }

    @Test
    void writesMissingValuesAsEmptyCells() {
        String csv = new SocialNetworkCsvSerializer().serialize(
                List.of("user_id", "bio"),
                List.of(Map.of("user_id", "001", "bio", "")));

        assertEquals("\uFEFFuser_id,bio\r\n001,\r\n", csv);
    }

    @Test
    void rejectsEmptyColumnDefinitions() {
        assertThrows(IllegalArgumentException.class,
                () -> new SocialNetworkCsvSerializer().serialize(List.of(), List.of()));
    }
}
