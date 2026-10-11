package com.prediction.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class VideoRepository {

    private final JdbcTemplate jdbc;

    public VideoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM videos", Long.class);
        return n == null ? 0 : n;
    }

    public long countCategories() {
        Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT category) FROM videos", Long.class);
        return n == null ? 0 : n;
    }

    public Optional<Video> findById(String videoId) {
        List<Video> rows = jdbc.query(
                "SELECT video_id, title, category, tags, description, user_id, user_image, video_url, "
                        + "day01, day02, day03, day04, day05, day06, day07, day08, day09, day10, "
                        + "day11, day12, day13, day14, day15, day16, day17, day18, day19, day20, "
                        + "day21, day22, day23, day24, day25, day26, day27, day28, day29, day30 "
                        + "FROM videos WHERE video_id = ?",
                videoRowMapper(), videoId);
        return rows.stream().findFirst();
    }

    public Map<String, Video> findByIds(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(videoIds.size(), "?"));
        List<Video> rows = jdbc.query(
                "SELECT video_id, title, category, tags, description, user_id, user_image, video_url, "
                        + "day01, day02, day03, day04, day05, day06, day07, day08, day09, day10, "
                        + "day11, day12, day13, day14, day15, day16, day17, day18, day19, day20, "
                        + "day21, day22, day23, day24, day25, day26, day27, day28, day29, day30 "
                        + "FROM videos WHERE video_id IN (" + placeholders + ")",
                videoRowMapper(), videoIds.toArray());
        Map<String, Video> videosById = new LinkedHashMap<>();
        for (Video video : rows) {
            videosById.put(video.videoId(), video);
        }
        return videosById;
    }

    /**
     * Paged browsing with optional category filter, search term and sort.
     * Search: FTS5 trigram for terms with at least 3 chars, LIKE fallback otherwise.
     */
    public PageResult<VideoSummary> search(String category, String sort, String q, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        int offset = (safePage - 1) * safeSize;
        String order = switch (sort == null ? "day30_desc" : sort) {
            case "day1_desc" -> " ORDER BY videos.day01 DESC, videos.day30 DESC";
            case "longrun_desc" ->
                    " ORDER BY CASE WHEN videos.day01 > 0"
                            + " THEN (videos.day01 - videos.day30) / videos.day01 ELSE 1 END ASC,"
                            + " videos.day30 DESC";
            case "title" -> " ORDER BY videos.title COLLATE NOCASE ASC";
            default -> " ORDER BY videos.day30 DESC, videos.day01 DESC";
        };

        if (q != null && q.length() >= 3) {
            return searchFts(category, order, q, safeSize, offset);
        }

        List<Object> params = new ArrayList<>();
        StringBuilder cond = new StringBuilder();
        if (category != null && !category.isBlank()) {
            cond.append("category = ?");
            params.add(category);
        }
        if (q != null && !q.isBlank()) {
            if (!cond.isEmpty()) {
                cond.append(" AND ");
            }
            cond.append("(title LIKE ? OR tags LIKE ? OR description LIKE ?)");
            String like = "%" + q + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        String where = cond.isEmpty() ? "" : " WHERE " + cond;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM videos" + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add(offset);
        List<VideoSummary> items = jdbc.query(
                "SELECT video_id, title, category, user_id, user_image, day01, day30 FROM videos"
                        + where + order
                        + " LIMIT ? OFFSET ?",
                summaryMapper(), pageParams.toArray());

        return new PageResult<>(total == null ? 0 : total, items);
    }

    /** FTS search without a direct FTS JOIN (that plan is pathologically slow in SQLite). */
    private PageResult<VideoSummary> searchFts(
            String category, String order, String q, int pageSize, int offset) {
        List<Object> params = new ArrayList<>();
        params.add(escapeFtsQuery(q));
        String where = "video_id IN (SELECT video_id FROM videos_fts WHERE videos_fts MATCH ?)";
        if (category != null && !category.isBlank()) {
            where += " AND category = ?";
            params.add(category);
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM videos WHERE " + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(offset);
        List<VideoSummary> items = jdbc.query(
                "SELECT video_id, title, category, user_id, user_image, day01, day30 FROM videos WHERE "
                        + where + order + " LIMIT ? OFFSET ?",
                summaryMapper(), pageParams.toArray());
        return new PageResult<>(total == null ? 0 : total, items);
    }

    public List<CategoryStat> categoryStats() {
        return jdbc.query(
                "SELECT category, COUNT(*) AS cnt, AVG(day01) AS avg1, AVG(day30) AS avg30 "
                        + "FROM videos GROUP BY category ORDER BY avg30 DESC",
                (rs, i) -> new CategoryStat(
                        rs.getString("category"),
                        rs.getLong("cnt"),
                        rs.getDouble("avg1"),
                        rs.getDouble("avg30")));
    }

    /** Pearson correlation between Day1 and Day30 plus a deterministic scatter sample. */
    public CorrelationStat correlationAndScatter() {
        List<double[]> pairs = jdbc.query(
                "SELECT day01, day30 FROM videos",
                (rs, i) -> new double[] {rs.getDouble(1), rs.getDouble(2)});
        int n = pairs.size();
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (double[] p : pairs) {
            double x = p[0], y = p[1];
            sx += x;
            sy += y;
            sxx += x * x;
            syy += y * y;
            sxy += x * y;
        }
        double denom = Math.sqrt((n * sxx - sx * sx) * (n * syy - sy * sy));
        double pearson = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;

        int sampleSize = Math.min(3000, n);
        double[][] scatter = new double[sampleSize][];
        int step = Math.max(1, n / sampleSize);
        int idx = 0;
        for (int i = 0; i < n && idx < sampleSize; i += step) {
            scatter[idx++] = pairs.get(i);
        }
        return new CorrelationStat(pearson, n, scatter);
    }

    /** Average daily curves: overall and per category. */
    public DecayCurves decayCurves() {
        String daySql = String.join(", ",
                java.util.stream.IntStream.rangeClosed(1, 30)
                        .mapToObj(d -> String.format("AVG(day%02d)", d))
                        .toList());
        double[] overall = jdbc.queryForObject(
                "SELECT " + daySql + " FROM videos",
                (rs, i) -> readAverages(rs));
        Map<String, double[]> byCategory = new LinkedHashMap<>();
        jdbc.query(
                "SELECT category, " + daySql + " FROM videos GROUP BY category",
                (rs, i) -> {
                    byCategory.put(rs.getString(1), readAverages(rs));
                    return null;
                });
        return new DecayCurves(overall, byCategory);
    }

    /** Top-K videos ranked by text relevance (BM25), LIKE fallback for short terms. */
    public List<ReferenceHit> searchRelevant(String q, int topK) {
        int limit = Math.min(Math.max(1, topK), 30);
        if (q == null || q.isBlank()) {
            return List.of();
        }
        if (q.length() >= 3) {
            try {
                return jdbc.query(
                        "WITH matches AS ("
                                + "SELECT video_id, bm25(videos_fts) AS score FROM videos_fts "
                                + "WHERE videos_fts MATCH ? ORDER BY score ASC LIMIT ?"
                                + ") SELECT v.video_id, v.title, v.user_id, v.day30, "
                                + "(SELECT score FROM matches WHERE matches.video_id = v.video_id) AS score "
                                + "FROM videos v WHERE v.video_id IN (SELECT video_id FROM matches) "
                                + "ORDER BY score ASC",
                        (rs, i) -> new ReferenceHit(
                                rs.getString("video_id"),
                                rs.getString("title"),
                                coverPath(rs.getString("video_id")),
                                rs.getString("user_id"),
                                rs.getDouble("day30"),
                                rs.getDouble("score")),
                        escapeFtsQuery(q), limit);
            } catch (org.springframework.dao.DataAccessException e) {
                // malformed FTS query -> fall back to LIKE
            }
        }
        String like = "%" + q + "%";
        return jdbc.query(
                "SELECT video_id, title, user_id, day30 FROM videos "
                        + "WHERE title LIKE ? OR tags LIKE ? OR description LIKE ? "
                        + "ORDER BY day30 DESC LIMIT ?",
                (rs, i) -> new ReferenceHit(
                        rs.getString("video_id"),
                        rs.getString("title"),
                        coverPath(rs.getString("video_id")),
                        rs.getString("user_id"),
                        rs.getDouble("day30"),
                        0),
                like, like, like, limit);
    }

    private static double[] readAverages(ResultSet rs) throws SQLException {
        double[] averages = new double[30];
        for (int d = 0; d < 30; d++) {
            averages[d] = rs.getDouble(d + 1);
        }
        return averages;
    }

    private RowMapper<Video> videoRowMapper() {
        return (rs, i) -> {
            String videoId = rs.getString("video_id");
            double[] curve = new double[30];
            for (int d = 0; d < 30; d++) {
                curve[d] = rs.getDouble(String.format("day%02d", d + 1));
            }
            int peakDay = 1;
            for (int d = 1; d < 30; d++) {
                if (curve[d] > curve[peakDay - 1]) {
                    peakDay = d + 1;
                }
            }
            double decay = curve[0] > 0 ? (curve[0] - curve[29]) / curve[0] : 0.0;
            return new Video(
                    videoId,
                    rs.getString("title"),
                    rs.getString("category"),
                    rs.getString("tags"),
                    rs.getString("description"),
                    rs.getString("user_id"),
                    rs.getString("user_image"),
                    rs.getString("video_url"),
                    curve,
                    curve[29],
                    peakDay,
                    decay);
        };
    }

    private RowMapper<VideoSummary> summaryMapper() {
        return (rs, i) -> {
            String videoId = rs.getString("video_id");
            return new VideoSummary(
                    videoId,
                    rs.getString("title"),
                    rs.getString("category"),
                    coverPath(videoId),
                    rs.getDouble("day01"),
                    rs.getDouble("day30"),
                    rs.getString("user_id"),
                    rs.getString("user_image"));
        };
    }

    /** URL path convention for a cover: /covers/{first2}/{id}.jpg. */
    public static String coverPath(String videoId) {
        String prefix = videoId.length() >= 2 ? videoId.substring(0, 2) : "xx";
        return "/covers/" + prefix + "/" + videoId + ".jpg";
    }

    private static String escapeFtsQuery(String q) {
        String clean = q.replace('"', ' ').trim();
        return "\"" + clean + "\"";
    }
}
