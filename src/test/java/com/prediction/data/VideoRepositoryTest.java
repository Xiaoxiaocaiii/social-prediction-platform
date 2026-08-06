package com.prediction.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VideoRepositoryTest {

    @Autowired
    private VideoRepository repo;

    @Test
    void databaseIsLoaded() {
        assertThat(repo.count()).isEqualTo(282_481L);
        assertThat(repo.countCategories()).isEqualTo(15L);
    }

    @Test
    void detailReturnsFullCurve() {
        var video = repo.findById("CSsD_UC5X_g");
        assertThat(video).isPresent();
        assertThat(video.get().curve()).hasSize(30);
        assertThat(video.get().peakDay()).isBetween(1, 30);
        assertThat(video.get().day30()).isGreaterThan(0);
    }

    @Test
    void ftsSearchFindsChineseTitle() {
        var result = repo.search(null, "day30_desc", "三毛水果", 1, 10);
        assertThat(result.total()).isGreaterThan(0);
        assertThat(result.items()).allMatch(item -> item.videoId() != null);
    }

    @Test
    void likeFallbackForShortTerm() {
        var result = repo.search(null, "day30_desc", "火锅", 1, 10);
        assertThat(result.total()).isGreaterThan(0);
    }

    @Test
    void pageWithCategoryFilter() {
        var result = repo.search("People  Blogs", "day30_desc", null, 1, 20);
        assertThat(result.items()).hasSize(20);
        assertThat(result.total()).isGreaterThan(20);
        assertThat(result.items()).allMatch(item -> "People  Blogs".equals(item.category()));
    }

    @Test
    void categoryStatsArePresent() {
        List<CategoryStat> stats = repo.categoryStats();
        assertThat(stats).hasSize(15);
        assertThat(stats.get(0).avgDay30()).isGreaterThan(0);
    }
}
