package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class SocialNetworkDataFingerprintServiceTest {

    private static final String USERS_PATH =
            "all_csv/merged_dataset/all_user_info.csv";
    private static final String RELATIONSHIPS_PATH =
            "all_csv/merged_dataset/all_following_list.csv";
    private static final byte[] USERS_BYTES =
            "id,name\n01,Alpha\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RELATIONSHIPS_BYTES =
            "user_id,following_list\n01,['02']\n".getBytes(StandardCharsets.UTF_8);

    private final SocialNetworkDataFingerprintService service =
            new SocialNetworkDataFingerprintService();

    @TempDir
    Path temporaryDirectory;

    @Test
    void fingerprintIsStableAcrossRootsAndDescriptorOrder() throws IOException {
        Path firstRoot = writeInputs("first");
        Path secondRoot = writeInputs("second");
        SocialNetworkDataset firstDataset = dataset(List.of(
                descriptor(DatasetFileRole.TWEETS, "tweets.csv"),
                descriptor(DatasetFileRole.RELATIONSHIPS, RELATIONSHIPS_PATH),
                descriptor(DatasetFileRole.USERS, USERS_PATH)));
        SocialNetworkDataset secondDataset = dataset(List.of(
                descriptor(DatasetFileRole.USERS, USERS_PATH),
                descriptor(DatasetFileRole.RELATIONSHIPS, RELATIONSHIPS_PATH)));

        SocialNetworkDataFingerprintService.Fingerprint first =
                service.fingerprint(firstDataset, firstRoot);
        SocialNetworkDataFingerprintService.Fingerprint second =
                service.fingerprint(secondDataset, secondRoot);

        assertEquals(first, second);
        assertEquals(List.of("relationships", "users"),
                first.files().stream()
                        .map(SocialNetworkDataFingerprintService.FileFingerprint::role)
                        .toList());
        assertEquals(sha256(RELATIONSHIPS_BYTES), first.files().get(0).sha256());
        assertEquals(sha256(USERS_BYTES), first.files().get(1).sha256());
        assertEquals(64, first.dataFingerprint().length());
        assertEquals(
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                first.inputContractVersion());
        assertEquals(
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                first.relationProjectionVersion());
    }

    @Test
    void changingRawInputBytesChangesFileAndManifestFingerprints() throws IOException {
        Path root = writeInputs("mutable");
        SocialNetworkDataFingerprintService.Fingerprint before =
                service.fingerprint(dataset(inputDescriptors()), root);
        Files.write(
                root.resolve(RELATIONSHIPS_PATH),
                "user_id,following_list\n01,['03']\n".getBytes(StandardCharsets.UTF_8));

        SocialNetworkDataFingerprintService.Fingerprint after =
                service.fingerprint(dataset(inputDescriptors()), root);

        assertEquals(before.files().get(1).sha256(), after.files().get(1).sha256());
        assertNotEquals(before.files().get(0).sha256(), after.files().get(0).sha256());
        assertNotEquals(before.dataFingerprint(), after.dataFingerprint());
    }

    @Test
    void rejectsUploadDatasetsDuplicateRolesAndPathsOutsideTheRoot() throws IOException {
        Path root = writeInputs("invalid");
        SocialNetworkDataset builtIn = dataset(inputDescriptors());
        SocialNetworkDataset upload = new SocialNetworkDataset(
                "upload-session",
                "upload",
                DatasetType.UPLOAD_SESSION,
                "session-1",
                DatasetStatus.PARTIAL,
                "upload",
                "test",
                null,
                null,
                inputDescriptors(),
                "session-1",
                "normalization-v1",
                "metrics-v1",
                "text-v1",
                "explanation-v1");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.fingerprint(upload, root));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.fingerprint(
                        dataset(List.of(
                                descriptor(DatasetFileRole.USERS, USERS_PATH),
                                descriptor(DatasetFileRole.USERS, "second-users.csv"),
                                descriptor(DatasetFileRole.RELATIONSHIPS, RELATIONSHIPS_PATH))),
                        root));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.fingerprint(
                        dataset(List.of(
                                descriptor(DatasetFileRole.USERS, "../outside.csv"),
                                descriptor(DatasetFileRole.RELATIONSHIPS, RELATIONSHIPS_PATH))),
                        root));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.fingerprint(
                        dataset(List.of(
                                descriptor(DatasetFileRole.USERS, "substitute-users.csv"),
                                descriptor(
                                        DatasetFileRole.RELATIONSHIPS, RELATIONSHIPS_PATH))),
                        root));
        assertEquals(SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID, builtIn.datasetId());
    }

    @Test
    void missingRequiredInputFailsClosed() {
        assertThrows(
                IOException.class,
                () -> service.fingerprint(
                        dataset(inputDescriptors()), temporaryDirectory));
    }

    @Test
    void uploadFingerprintSupportsMixedFormatsAndMultipleFilesWithoutSessionIdentity()
            throws IOException {
        byte[] firstUsers =
                "user_id,username\nu1,one\n".getBytes(StandardCharsets.UTF_8);
        byte[] secondUsers =
                "{\"user_id\":\"u2\",\"username\":\"two\"}\n"
                        .getBytes(StandardCharsets.UTF_8);
        byte[] relationships =
                "{\"source_user_id\":\"u1\",\"target_user_id\":\"u2\"}\n"
                        .getBytes(StandardCharsets.UTF_8);

        Path firstRoot = temporaryDirectory.resolve("upload-first");
        Files.createDirectories(firstRoot);
        DatasetFileDescriptor firstCsv = new DatasetFileDescriptor(
                "file-01-users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8");
        DatasetFileDescriptor firstJsonl = new DatasetFileDescriptor(
                "file-02-users.jsonl", DatasetFileRole.USERS, SourceFormat.JSONL, "utf8");
        DatasetFileDescriptor firstRelationships = new DatasetFileDescriptor(
                "file-03-relationships.jsonl",
                DatasetFileRole.RELATIONSHIPS,
                SourceFormat.JSONL,
                "utf8");
        Map<String, Path> firstFiles = new LinkedHashMap<>();
        firstFiles.put(firstCsv.logicalFileName(), writeStagedFile(
                firstRoot, "part-01.csv", firstUsers));
        firstFiles.put(firstJsonl.logicalFileName(), writeStagedFile(
                firstRoot, "part-02.jsonl", secondUsers));
        firstFiles.put(firstRelationships.logicalFileName(), writeStagedFile(
                firstRoot, "part-03.jsonl", relationships));
        var first = service.fingerprintUpload(
                uploadDataset(List.of(firstCsv, firstJsonl, firstRelationships), "first"),
                firstRoot,
                firstFiles);

        Path secondRoot = temporaryDirectory.resolve("upload-second");
        Files.createDirectories(secondRoot);
        DatasetFileDescriptor renamedRelationships = new DatasetFileDescriptor(
                "renamed-relations.jsonl",
                DatasetFileRole.RELATIONSHIPS,
                SourceFormat.JSONL,
                "utf8");
        DatasetFileDescriptor renamedUsersJsonl = new DatasetFileDescriptor(
                "renamed-users.jsonl", DatasetFileRole.USERS, SourceFormat.JSONL, "utf8");
        DatasetFileDescriptor renamedUsersCsv = new DatasetFileDescriptor(
                "renamed-users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8");
        Map<String, Path> secondFiles = new LinkedHashMap<>();
        secondFiles.put(renamedRelationships.logicalFileName(), writeStagedFile(
                secondRoot, "other-01.jsonl", relationships));
        secondFiles.put(renamedUsersJsonl.logicalFileName(), writeStagedFile(
                secondRoot, "other-02.jsonl", secondUsers));
        secondFiles.put(renamedUsersCsv.logicalFileName(), writeStagedFile(
                secondRoot, "other-03.csv", firstUsers));
        var second = service.fingerprintUpload(
                uploadDataset(List.of(
                        renamedRelationships, renamedUsersJsonl, renamedUsersCsv),
                        "second"),
                secondRoot,
                secondFiles);

        assertEquals(first.dataFingerprint(), second.dataFingerprint());
        assertEquals(3, first.files().size());
        assertEquals(
                List.of("relationships", "users", "users"),
                first.files().stream()
                        .map(SocialNetworkDataFingerprintService.FileFingerprint::role)
                        .toList());

        DatasetFileDescriptor csvInterpretedAsJsonl = new DatasetFileDescriptor(
                firstCsv.logicalFileName(),
                DatasetFileRole.USERS,
                SourceFormat.JSONL,
                "utf8");
        var formatChanged = service.fingerprintUpload(
                uploadDataset(List.of(
                        csvInterpretedAsJsonl, firstJsonl, firstRelationships),
                        "format-changed"),
                firstRoot,
                firstFiles);
        assertNotEquals(first.dataFingerprint(), formatChanged.dataFingerprint());

        Files.writeString(firstFiles.get(firstRelationships.logicalFileName()),
                "{\"source_user_id\":\"u1\",\"target_user_id\":\"u3\"}\n");
        var changed = service.fingerprintUpload(
                uploadDataset(List.of(firstCsv, firstJsonl, firstRelationships), "changed"),
                firstRoot,
                firstFiles);
        assertNotEquals(first.dataFingerprint(), changed.dataFingerprint());
    }

    @Test
    void uploadFingerprintRejectsPathsOutsideTheStagingRoot() throws IOException {
        Path root = temporaryDirectory.resolve("upload-path-check");
        Files.createDirectories(root);
        DatasetFileDescriptor descriptor = new DatasetFileDescriptor(
                "users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8");
        Path outside = temporaryDirectory.resolve("outside-upload.csv");
        Files.write(outside, USERS_BYTES);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.fingerprintUpload(
                        uploadDataset(List.of(descriptor)),
                        root,
                        Map.of(descriptor.logicalFileName(), outside)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.fingerprintUpload(
                        uploadDataset(List.of(new DatasetFileDescriptor(
                                "../outside.csv",
                                DatasetFileRole.USERS,
                                SourceFormat.CSV,
                                "utf8"))),
                        root,
                        Map.of("../outside.csv", writeStagedFile(root, "inside.csv", USERS_BYTES))));
    }

    @Test
    @EnabledIfSystemProperty(named = "fingerprint.real", matches = "true")
    void fingerprintsTheRegisteredBuiltInGraphInputs() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataFingerprintService.Fingerprint fingerprint =
                service.fingerprint(registry.findDataset(
                        SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID));

        assertEquals(2, fingerprint.files().size());
        System.out.printf(
                Locale.ROOT,
                "REAL_INPUT_FINGERPRINT dataset=%s version=%s fingerprint=%s files=%s%n",
                fingerprint.datasetId(),
                fingerprint.datasetVersion(),
                fingerprint.dataFingerprint(),
                fingerprint.files());
    }

    private Path writeInputs(String directoryName) throws IOException {
        Path root = temporaryDirectory.resolve(directoryName);
        Path inputDirectory = root.resolve("all_csv/merged_dataset");
        Files.createDirectories(inputDirectory);
        Files.write(root.resolve(USERS_PATH), USERS_BYTES);
        Files.write(root.resolve(RELATIONSHIPS_PATH), RELATIONSHIPS_BYTES);
        return root;
    }

    private static Path writeStagedFile(Path root, String name, byte[] content)
            throws IOException {
        Path file = root.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static SocialNetworkDataset uploadDataset(
            List<DatasetFileDescriptor> files) {
        return uploadDataset(files, "test");
    }

    private static SocialNetworkDataset uploadDataset(
            List<DatasetFileDescriptor> files, String identity) {
        return new SocialNetworkDataset(
                "upload-" + identity,
                "upload test data",
                DatasetType.UPLOAD_SESSION,
                "session-" + identity,
                DatasetStatus.PARTIAL,
                "test upload",
                "test",
                null,
                null,
                files,
                "session-" + identity,
                "normalization-v1",
                "metrics-v1",
                "text-v1",
                "explanation-v1");
    }

    private static SocialNetworkDataset dataset(List<DatasetFileDescriptor> files) {
        return new SocialNetworkDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                "built-in test data",
                DatasetType.BUILTIN,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                DatasetStatus.AVAILABLE_WITH_WARNINGS,
                "test",
                "test",
                null,
                null,
                files,
                null,
                "normalization-v1",
                "metrics-v1",
                "text-v1",
                "explanation-v1");
    }

    private static List<DatasetFileDescriptor> inputDescriptors() {
        return List.of(
                descriptor(DatasetFileRole.USERS, USERS_PATH),
                descriptor(DatasetFileRole.RELATIONSHIPS, RELATIONSHIPS_PATH));
    }

    private static DatasetFileDescriptor descriptor(DatasetFileRole role, String path) {
        return new DatasetFileDescriptor(path, role, SourceFormat.CSV, "utf8");
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IOException(exception);
        }
    }
}
