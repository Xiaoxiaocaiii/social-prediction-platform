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

    /**
     * Paged browsing with optional category filter, search term and sort.
     * Search: FTS5 trigram for terms with at least 3 chars, LIKE fallback otherwise.
     */
    public PageResult<VideoSummary> search(String category, String sort, String q, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        int offset = (safePage - 1) * safeSize;

        StringBuilder cond = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            cond.append("videos.category = ?");
            params.add(category);
        }

        boolean fts = q != null && q.length() >= 3;
        if (fts) {
            if (!cond.isEmpty()) {
                cond.append(" AND ");
            }
            cond.append("videos_fts MATCH ?");
            params.add(escapeFtsQuery(q));
        } else if (q != null && !q.isBlank()) {
            if (!cond.isEmpty()) {
                cond.append(" AND ");
            }
            cond.append("(videos.title LIKE ? OR videos.tags LIKE ? OR videos.description LIKE ?)");
            String like = "%" + q + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String from = "videos"
                + (fts ? " JOIN videos_fts ON videos.video_id = videos_fts.video_id" : "");
        String where = cond.isEmpty() ? "" : " WHERE " + cond;
        String order = switch (sort == null ? "day30_desc" : sort) {
            case "day1_desc" -> " ORDER BY videos.day01 DESC, videos.day30 DESC";
            case "title" -> " ORDER BY videos.title COLLATE NOCASE ASC";
            default -> " ORDER BY videos.day30 DESC, videos.day01 DESC";
        };

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + from + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add(offset);
        List<VideoSummary> items = jdbc.query(
                "SELECT videos.video_id, videos.title, videos.category, videos.user_id, "
                        + "videos.user_image, videos.day30 FROM " + from + where + order
                        + " LIMIT ? OFFSET ?",
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
