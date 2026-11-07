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
import java.util.List;
import java.util.Locale;
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
