package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkDataFingerprintService {

    public static final String INPUT_CONTRACT_VERSION = "network-input-contract-v1.0";
    public static final String RELATION_PROJECTION_VERSION =
            "undirected-follow-projection-v1.0";
    private static final String BUILTIN_NORMALIZATION_VERSION = "normalization-v1";
    private static final Map<DatasetFileRole, String> BUILTIN_GRAPH_INPUT_PATHS = Map.of(
            DatasetFileRole.USERS,
            "all_csv/merged_dataset/all_user_info.csv",
            DatasetFileRole.RELATIONSHIPS,
            "all_csv/merged_dataset/all_following_list.csv");

    private static final int BUFFER_BYTES = 64 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Fingerprint fingerprint(SocialNetworkDataset dataset) throws IOException {
        return fingerprint(dataset, Path.of("").toAbsolutePath());
    }

    public Fingerprint fingerprint(SocialNetworkDataset dataset, Path dataRoot)
            throws IOException {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(dataRoot, "dataRoot");
        requireSupportedDataset(dataset);

        Path normalizedRoot = dataRoot.toAbsolutePath().normalize();
        List<DatasetFileDescriptor> descriptors = graphInputDescriptors(dataset);
        List<FileFingerprint> fileFingerprints = new ArrayList<>(descriptors.size());
        for (DatasetFileDescriptor descriptor : descriptors) {
            Path inputPath = resolveInputPath(normalizedRoot, descriptor.logicalFileName());
            fileFingerprints.add(fingerprintFile(descriptor, inputPath));
        }
        fileFingerprints.sort(Comparator.comparing(FileFingerprint::role)
                .thenComparing(FileFingerprint::logicalPath));

        Manifest manifest = new Manifest(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                INPUT_CONTRACT_VERSION,
                RELATION_PROJECTION_VERSION,
                fileFingerprints.stream()
                        .map(file -> new ManifestFile(
                                file.role(),
                                file.logicalPath(),
                                file.byteLength(),
                                file.sha256()))
                        .toList());
        return new Fingerprint(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                INPUT_CONTRACT_VERSION,
                RELATION_PROJECTION_VERSION,
                fileFingerprints,
                sha256(canonicalManifest(manifest)));
    }

    public Fingerprint fingerprintUpload(
            SocialNetworkDataset dataset, Path stagingRoot, Map<String, Path> stagedFiles)
            throws IOException {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(stagingRoot, "stagingRoot");
        Objects.requireNonNull(stagedFiles, "stagedFiles");
        if (!dataset.isUploadSession()) {
            throw new IllegalArgumentException("Upload input fingerprint requires an upload dataset");
        }

        Path normalizedRoot = stagingRoot.toAbsolutePath().normalize();
        Path realRoot = normalizedRoot.toRealPath();
        Set<String> registeredNames = new HashSet<>();
        List<DatasetFileDescriptor> graphInputs = new ArrayList<>();
        Map<String, Path> realPaths = new HashMap<>();
        for (DatasetFileDescriptor descriptor : dataset.files()) {
            if (!registeredNames.add(descriptor.logicalFileName())) {
                throw new IllegalArgumentException("Upload dataset contains duplicate logical files");
            }
            validateUploadLogicalName(descriptor.logicalFileName());
            Path mappedPath = stagedFiles.get(descriptor.logicalFileName());
            if (mappedPath == null) {
                throw new IllegalArgumentException("A registered upload file has no staged input");
            }
            Path stagedPath = mappedPath.toAbsolutePath().normalize();
            if (!stagedPath.startsWith(normalizedRoot)
                    || !Files.isRegularFile(stagedPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(
                        "Upload input must be a regular file inside its staging directory");
            }
            Path realPath = stagedPath.toRealPath();
            if (!realPath.startsWith(realRoot)) {
                throw new IllegalArgumentException(
                        "Upload input must remain inside its staging directory");
            }
            realPaths.put(descriptor.logicalFileName(), realPath);
            if (descriptor.role() == DatasetFileRole.USERS
                    || descriptor.role() == DatasetFileRole.RELATIONSHIPS) {
                if (!"utf8".equalsIgnoreCase(descriptor.encoding())) {
                    throw new IllegalArgumentException(
                            "Upload graph inputs must use UTF-8 encoding");
                }
                graphInputs.add(descriptor);
            }
        }
        if (!registeredNames.equals(stagedFiles.keySet())) {
            throw new IllegalArgumentException(
                    "Staged inputs must match the registered upload files");
        }

        List<FileFingerprint> fileFingerprints = new ArrayList<>(graphInputs.size());
        Map<String, String> formatsByLogicalName = new HashMap<>();
        for (DatasetFileDescriptor descriptor : graphInputs) {
            FileFingerprint fingerprint =
                    fingerprintFile(descriptor, realPaths.get(descriptor.logicalFileName()));
            fileFingerprints.add(fingerprint);
            formatsByLogicalName.put(
                    descriptor.logicalFileName(), descriptor.format().name().toLowerCase(Locale.ROOT));
        }
        fileFingerprints.sort(Comparator.comparing(FileFingerprint::role)
                .thenComparing(FileFingerprint::logicalPath));

        List<UploadManifestFile> manifestFiles = fileFingerprints.stream()
                .map(file -> new UploadManifestFile(
                        file.role(),
                        formatsByLogicalName.get(file.logicalPath()),
                        file.byteLength(),
                        file.sha256()))
                .sorted(Comparator.comparing(UploadManifestFile::role)
                        .thenComparing(UploadManifestFile::format)
                        .thenComparing(UploadManifestFile::sha256)
                        .thenComparingLong(UploadManifestFile::byteLength))
                .toList();
        UploadManifest manifest = new UploadManifest(
                dataset.normalizationVersion(),
                INPUT_CONTRACT_VERSION,
                RELATION_PROJECTION_VERSION,
                manifestFiles);
        return new Fingerprint(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                INPUT_CONTRACT_VERSION,
                RELATION_PROJECTION_VERSION,
                fileFingerprints,
                sha256(canonicalManifest(manifest)));
    }

    private static void validateUploadLogicalName(String logicalFileName) {
        String canonicalName = logicalFileName.replace('\\', '/');
        if (canonicalName.isBlank()
                || ".".equals(canonicalName)
                || "..".equals(canonicalName)
                || canonicalName.startsWith("/")
                || canonicalName.contains("/")) {
            throw new IllegalArgumentException(
                    "Upload logical file names must be relative file names");
        }
    }

    private static void requireSupportedDataset(SocialNetworkDataset dataset) {
        if (dataset.type() != DatasetType.BUILTIN
                || !SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID.equals(dataset.datasetId())
                || !SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION
                        .equals(dataset.datasetVersion())
                || !BUILTIN_NORMALIZATION_VERSION.equals(dataset.normalizationVersion())) {
            throw new IllegalArgumentException(
                    "Input fingerprint supports built-in think-tank-social-network/v1 only");
        }
    }

    private static List<DatasetFileDescriptor> graphInputDescriptors(
            SocialNetworkDataset dataset) {
        List<DatasetFileDescriptor> selected = new ArrayList<>(2);
        for (DatasetFileRole role : List.of(
                DatasetFileRole.USERS, DatasetFileRole.RELATIONSHIPS)) {
            List<DatasetFileDescriptor> matching = dataset.files().stream()
                    .filter(file -> file.role() == role)
                    .toList();
            if (matching.size() != 1) {
                throw new IllegalArgumentException(
                        "Dataset must register exactly one " + role + " input file");
            }
            DatasetFileDescriptor descriptor = matching.get(0);
            String expectedPath = BUILTIN_GRAPH_INPUT_PATHS.get(role);
            if (descriptor.format() != SourceFormat.CSV
                    || !"utf8".equalsIgnoreCase(descriptor.encoding())
                    || !expectedPath.equals(descriptor.logicalFileName().replace('\\', '/'))) {
                throw new IllegalArgumentException(
                        "Graph input files must match the registered UTF-8 CSV paths");
            }
            selected.add(descriptor);
        }
        return selected;
    }

    private static Path resolveInputPath(Path root, String logicalFileName) {
        String canonicalLogicalPath = logicalFileName.replace('\\', '/');
        final Path relativePath;
        try {
            relativePath = Path.of(canonicalLogicalPath);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid logical input path", exception);
        }
        if (relativePath.isAbsolute()
                || relativePath.getNameCount() == 0
                || relativePath.normalize().startsWith("..")) {
            throw new IllegalArgumentException(
                    "Input path must remain relative to the dataset root");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Input path must remain relative to the dataset root");
        }
        return resolved;
    }

    private static FileFingerprint fingerprintFile(
            DatasetFileDescriptor descriptor, Path inputPath) throws IOException {
        if (!Files.isRegularFile(inputPath)) {
            throw new IOException("Required graph input file is missing: "
                    + descriptor.logicalFileName());
        }
        BasicFileAttributes before = Files.readAttributes(
                inputPath, BasicFileAttributes.class);
        MessageDigest digest = newDigest();
        long byteLength = 0;
        byte[] buffer = new byte[BUFFER_BYTES];
        try (InputStream input = Files.newInputStream(inputPath)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                byteLength += read;
            }
        }
        BasicFileAttributes after = Files.readAttributes(
                inputPath, BasicFileAttributes.class);
        if (byteLength != before.size()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || (before.fileKey() != null
                        && after.fileKey() != null
                        && !before.fileKey().equals(after.fileKey()))) {
            throw new IOException(
                    "Graph input changed while its fingerprint was being calculated: "
                            + descriptor.logicalFileName());
        }
        return new FileFingerprint(
                descriptor.role().name().toLowerCase(java.util.Locale.ROOT),
                descriptor.logicalFileName().replace('\\', '/'),
                byteLength,
                HexFormat.of().formatHex(digest.digest()));
    }

    private static byte[] canonicalManifest(Object manifest) throws IOException {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(manifest);
        } catch (JsonProcessingException exception) {
            throw new IOException("Unable to serialize the input fingerprint manifest", exception);
        }
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(newDigest().digest(value));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @JsonPropertyOrder({
        "datasetId",
        "datasetVersion",
        "normalizationVersion",
        "inputContractVersion",
        "relationProjectionVersion",
        "files"
    })
    private record Manifest(
            String datasetId,
            String datasetVersion,
            String normalizationVersion,
            String inputContractVersion,
            String relationProjectionVersion,
            List<ManifestFile> files) {
    }

    @JsonPropertyOrder({"role", "logicalPath", "byteLength", "sha256"})
    private record ManifestFile(
            String role,
            String logicalPath,
            long byteLength,
            String sha256) {
    }

    @JsonPropertyOrder({
        "normalizationVersion",
        "inputContractVersion",
        "relationProjectionVersion",
        "files"
    })
    private record UploadManifest(
            String normalizationVersion,
            String inputContractVersion,
            String relationProjectionVersion,
            List<UploadManifestFile> files) {
    }

    @JsonPropertyOrder({"role", "format", "byteLength", "sha256"})
    private record UploadManifestFile(
            String role,
            String format,
            long byteLength,
            String sha256) {
    }

    public record FileFingerprint(
            String role,
            String logicalPath,
            long byteLength,
            String sha256) {
    }

    public record Fingerprint(
            String datasetId,
            String datasetVersion,
            String normalizationVersion,
            String inputContractVersion,
            String relationProjectionVersion,
            List<FileFingerprint> files,
            String dataFingerprint) {

        public Fingerprint {
            files = List.copyOf(files);
        }
    }
}
