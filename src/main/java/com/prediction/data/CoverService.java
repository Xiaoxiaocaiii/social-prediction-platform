package com.prediction.data;

import com.prediction.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class CoverService {

    private static final String FILENAME_PATTERN = "[A-Za-z0-9_-]+\\.jpg";
    private static final String PREFIX_PATTERN = "[A-Za-z0-9_-]{1,2}";

    private final AppProperties props;

    public CoverService(AppProperties props) {
        this.props = props;
    }

    /**
     * Resolve a cover file. If it does not exist yet, try to extract it from
     * the local zip on demand; returns empty when it cannot be served.
     */
    public Optional<Resource> resolve(String prefix, String filename) {
        if (prefix == null || filename == null
                || !prefix.matches(PREFIX_PATTERN) || !filename.matches(FILENAME_PATTERN)) {
            return Optional.empty();
        }
        Path dest = props.coversDir().resolve(prefix).resolve(filename);
        if (Files.exists(dest)) {
            return Optional.of(new FileSystemResource(dest));
        }
        String videoId = filename.substring(0, filename.length() - 4);
        try {
            if (extractFromZip(videoId, dest)) {
                return Optional.of(new FileSystemResource(dest));
            }
        } catch (IOException ignored) {
            // fall through to placeholder
        }
        return Optional.empty();
    }

    public Resource placeholder() {
        return new ClassPathResource("static/img/cover-placeholder.svg");
    }

    private boolean extractFromZip(String videoId, Path dest) throws IOException {
        Path zip = props.coversZip();
        if (zip == null || !Files.exists(zip)) {
            return false;
        }
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            ZipEntry entry = zipFile.getEntry("img_yt/" + videoId + ".jpg");
            if (entry == null) {
                return false;
            }
            Files.createDirectories(dest.getParent());
            try (InputStream in = zipFile.getInputStream(entry);
                    OutputStream out = Files.newOutputStream(dest)) {
                in.transferTo(out);
            }
            return true;
        }
    }
}
