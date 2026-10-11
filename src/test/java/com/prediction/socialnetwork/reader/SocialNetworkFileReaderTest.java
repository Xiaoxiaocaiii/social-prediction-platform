package com.prediction.socialnetwork.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialNetworkFileReaderTest {

    private final SocialNetworkFileReader reader = new SocialNetworkFileReader();

    @Test
    void readsBuiltinCsvAndMapsChineseHeadersWithoutChangingIds() {
        String csv = "账号ID,用户名,粉丝数\n1002259603537702912,niwcrso,1475\n";

        FileReadResult result = reader.read(
                bytes(csv),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                true);

        assertEquals(HeaderStatus.VALID, result.report().headerStatus());
        assertEquals(1, result.records().size());
        assertEquals("1002259603537702912", result.records().get(0).fields().get("user_id"));
        assertEquals(2, result.records().get(0).sourceRowNumber());
    }

    @Test
    void readsStandardJsonlAndReportsInvalidLineNumber() {
        String jsonl = """
                {"tweet_id":"9007199254740993123","user_id":"42","created_at":"2025-01-01","text":"hello"}
                not-json
                """;

        FileReadResult result = reader.read(
                bytes(jsonl),
                new DatasetFileDescriptor("tweets.jsonl", DatasetFileRole.TWEETS, SourceFormat.JSONL, "utf8"),
                false);

        assertEquals(1, result.records().size());
        assertEquals("9007199254740993123", result.records().get(0).fields().get("tweet_id"));
        assertEquals(1, result.report().readOkCount());
        assertEquals(1, result.report().readErrorCount());
        assertEquals(2, result.report().issues().get(0).sourceRowNumber());
    }

    @Test
    void parsesFollowingListAndKeepsEmptyListEmpty() {
        assertEquals(List.of("188736341", "3912832153"),
                SocialNetworkFileReader.parseFollowingList("['188736341', '3912832153']"));
        assertEquals(List.of(), SocialNetworkFileReader.parseFollowingList("[]"));
        assertEquals(List.of(), SocialNetworkFileReader.parseFollowingList(null));
        assertEquals(List.of(), SocialNetworkFileReader.parseFollowingList("  "));
        assertThrows(IllegalArgumentException.class,
                () -> SocialNetworkFileReader.parseFollowingList("188736341"));
    }

    @Test
    void acceptsUtf8Bom() {
        byte[] body = "user_id,username\n001,a\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        FileReadResult result = reader.read(
                new ByteArrayInputStream(withBom),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8_bom"),
                false);

        assertEquals("utf8_bom", result.report().encoding());
        assertEquals("001", result.records().get(0).fields().get("user_id"));
    }

    @Test
    void reportsEmptyCsvAsUnusableWithoutInventingRows() {
        FileReadResult result = reader.read(
                bytes(""),
                new DatasetFileDescriptor("empty.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                false);

        assertEquals(0, result.records().size());
        assertEquals(0, result.report().readOkCount());
        assertEquals(0, result.report().readErrorCount());
        org.junit.jupiter.api.Assertions.assertFalse(result.report().isUsable());
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
