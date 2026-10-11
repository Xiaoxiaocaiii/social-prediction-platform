package com.prediction.socialnetwork.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialNetworkStateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesCanonicalLowercaseValues() {
        assertEquals(
                "idle,loading,available,empty,limited,failed,disabled,unavailable",
                java.util.Arrays.stream(SocialNetworkState.values())
                        .map(SocialNetworkState::value)
                        .reduce((left, right) -> left + "," + right)
                        .orElseThrow());
    }

    @Test
    void supportsValueLookupAndJsonRoundTrip() throws Exception {
        assertEquals(SocialNetworkState.LIMITED,
                SocialNetworkState.fromValue("limited"));
        assertEquals("\"available\"",
                objectMapper.writeValueAsString(SocialNetworkState.AVAILABLE));
        assertEquals(SocialNetworkState.EMPTY,
                objectMapper.readValue("\"empty\"", SocialNetworkState.class));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(IllegalArgumentException.class,
                () -> SocialNetworkState.fromValue("checking"));
    }
}
