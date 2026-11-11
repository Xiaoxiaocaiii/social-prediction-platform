package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.NetworkEdge;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkGroupInsightCalculatorTest {

    @Test
    void countsOverlapsAndPagesMaximumCliquesWithTheirOriginalIndexes() {
        CliqueEnumerationResult result = result(
                CliqueEnumerationStatus.COMPLETED,
                List.of(
                        List.of("a", "b", "c"),
                        List.of("a", "b", "c", "d"),
                        List.of("a", "b", "c", "e"),
                        List.of("a", "c", "e")));

        var first = SocialNetworkGroupInsightCalculator.clique(result, 0, 1);
        assertEquals(4, first.totalCount());
        assertEquals("a", first.firstNodeId());
        assertEquals("c", first.secondNodeId());
        assertEquals(4, first.commonCliqueCount());
        assertEquals(4, first.maximumSize());
        assertEquals(2, first.maximumCount());
        assertEquals(List.of("a", "b", "c"), first.commonMembers());
        assertEquals(1, first.maximumCliques().get(0).index());
        assertEquals(List.of("a", "b", "c", "d"), first.maximumCliques().get(0).members());

        var second = SocialNetworkGroupInsightCalculator.clique(result, 1, 1);
        assertEquals(2, second.maximumCliques().get(0).index());
        assertEquals(List.of("a", "b", "c", "e"), second.maximumCliques().get(0).members());

        var shared = SocialNetworkGroupInsightCalculator.sharedCliques(result, "a", "b", 1, 1);
        assertEquals(3, shared.totalCount());
        assertEquals(1, shared.cliques().get(0).index());
        assertThrows(IllegalArgumentException.class,
                () -> SocialNetworkGroupInsightCalculator.sharedCliques(result, "a", "a", 0, 1));
    }

    @Test
    void refusesPartialEnumerationAndInvalidPageSizes() {
        CliqueEnumerationResult partial = result(
                CliqueEnumerationStatus.TIME_LIMIT, List.of(List.of("a", "b", "c")));
        assertThrows(IllegalArgumentException.class,
                () -> SocialNetworkGroupInsightCalculator.clique(partial, 0, 20));
        CliqueEnumerationResult complete = result(
                CliqueEnumerationStatus.COMPLETED, List.of(List.of("a", "b", "c")));
        assertThrows(IllegalArgumentException.class,
                () -> SocialNetworkGroupInsightCalculator.clique(complete, 0, 101));
    }

    @Test
    void emptyCompleteEnumerationDoesNotInventAMaximum() {
        var insight = SocialNetworkGroupInsightCalculator.clique(
                result(CliqueEnumerationStatus.COMPLETED, List.of()), 0, 20);
        assertEquals(0, insight.maximumSize());
        assertEquals(0, insight.maximumCount());
        assertEquals(List.of(), insight.commonMembers());
    }

    @Test
    void countsEachCrossCommunityNodePairOnlyOnce() {
        var pairs = SocialNetworkGroupInsightCalculator.communityPairs(
                List.of(
                        new NetworkEdge("a", "b", "following"),
                        new NetworkEdge("b", "a", "following"),
                        new NetworkEdge("a", "b", "following"),
                        new NetworkEdge("a", "c", "following"),
                        new NetworkEdge("c", "d", "following"),
                        new NetworkEdge("a", "a", "following"),
                        new NetworkEdge("x", "a", "following")),
                Map.of("a", "C1", "b", "C2", "c", "C2", "d", "C3"));
        assertEquals(List.of(
                new SocialNetworkGroupInsightCalculator.CommunityPair("C1", "C2", 2, 3),
                new SocialNetworkGroupInsightCalculator.CommunityPair("C2", "C3", 1, 1)), pairs);
    }

    private static CliqueEnumerationResult result(
            CliqueEnumerationStatus status, List<List<String>> cliques) {
        return new CliqueEnumerationResult(
                cliques, status, CliqueEnumerationLimits.DEFAULT, 100, null);
    }
}
