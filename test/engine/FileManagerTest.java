package engine;

import currency.PlayerAssetsState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class FileManagerTest {

    @TempDir
    Path tempDir;

    private FileManager fileManager;
    private Path savePath;

    @BeforeEach
    void setUp() {
        fileManager = FileManager.getInstance();
        savePath = tempDir.resolve("player_assets.properties");
    }

    @Test
    @DisplayName("Default player assets save path shares directory with scores file")
    void testDefaultAssetPathSharesDirectoryWithScores() {
        Path defaultAssetPath = FileManager.resolveStoragePath(FileManager.ASSETS_FILENAME);
        Path scoresPath = FileManager.resolveStoragePath("scores");

        assertEquals(scoresPath.getParent(), defaultAssetPath.getParent());
        assertEquals("player_assets.properties", defaultAssetPath.getFileName().toString());
    }

    @Test
    @DisplayName("Configured storage directory keeps scores and assets outside build output")
    void testConfiguredStorageDirectory() throws IOException {
        String previous = System.getProperty("invaders.storageDir");
        try {
            System.setProperty("invaders.storageDir", tempDir.toString());
            assertEquals(tempDir.resolve("scores"), FileManager.resolveStoragePath("scores"));
            assertEquals(savePath, FileManager.resolveStoragePath(FileManager.ASSETS_FILENAME));
            PlayerAssetsState state = new PlayerAssetsState(125,
                    java.util.Collections.emptySet(), java.util.Collections.emptyMap());
            fileManager.savePlayerAssets(state);
            assertTrue(Files.exists(savePath));
            assertEquals(125, fileManager.loadPlayerAssets().getBalance());
        } finally {
            if (previous == null) {
                System.clearProperty("invaders.storageDir");
            } else {
                System.setProperty("invaders.storageDir", previous);
            }
        }
    }

    @Test
    @DisplayName("loadPlayerAssets throws IOException when unlocked content ID has malformed URL encoding")
    void testLoadMalformedEncodedUnlockIdThrowsIOException() throws IOException {
        String malformedData = "version=1\nbalance=100\nunlockedContentIds=VALID_SHIP,%ZZ_INVALID\nitemQuantities=ITEM_A:1\n";
        Files.writeString(savePath, malformedData);

        IOException ex = assertThrows(IOException.class, () -> fileManager.loadPlayerAssets(savePath));
        assertTrue(ex.getMessage().contains("Malformed URL-encoded unlocked content ID"));
    }

    @Test
    @DisplayName("loadPlayerAssets throws IOException when item ID has malformed URL encoding")
    void testLoadMalformedEncodedInventoryIdThrowsIOException() throws IOException {
        String malformedData = "version=1\nbalance=100\nunlockedContentIds=VALID_SHIP\nitemQuantities=%ZZ_BAD_ITEM:3\n";
        Files.writeString(savePath, malformedData);

        IOException ex = assertThrows(IOException.class, () -> fileManager.loadPlayerAssets(savePath));
        assertTrue(ex.getMessage().contains("Malformed URL-encoded item ID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%FF", "%C3%28", "%E3%81", "%80", "%C0%AF",
            "%ED%A0%80", "%ED%B0%80", "%ED%A0%BD%ED%BA%80", "%F4%90%80%80",
            "%F0%9F%9A", "%C3X%A9", "%E3+%81%82"})
    @DisplayName("loadPlayerAssets rejects invalid UTF-8 in unlocked content IDs without modifying the save")
    void testLoadInvalidUtf8UnlockIdThrowsIOException(String encodedId) throws IOException {
        String data = "version=1\nbalance=100\nunlockedContentIds=SHIP" + encodedId
                + "\nitemQuantities=ITEM_A:1\n";
        Files.writeString(savePath, data);
        byte[] original = Files.readAllBytes(savePath);

        IOException ex = assertThrows(IOException.class, () -> fileManager.loadPlayerAssets(savePath));

        assertTrue(ex.getMessage().contains("Malformed URL-encoded unlocked content ID"));
        assertArrayEquals(original, Files.readAllBytes(savePath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%FF", "%C3%28", "%E3%81", "%80", "%C0%AF",
            "%ED%A0%80", "%ED%B0%80", "%ED%A0%BD%ED%BA%80", "%F4%90%80%80",
            "%F0%9F%9A", "%C3X%A9", "%E3+%81%82"})
    @DisplayName("loadPlayerAssets rejects invalid UTF-8 in inventory IDs without modifying the save")
    void testLoadInvalidUtf8InventoryIdThrowsIOException(String encodedId) throws IOException {
        String data = "version=1\nbalance=100\nunlockedContentIds=VALID_SHIP\nitemQuantities=ITEM"
                + encodedId + ":3\n";
        Files.writeString(savePath, data);
        byte[] original = Files.readAllBytes(savePath);

        IOException ex = assertThrows(IOException.class, () -> fileManager.loadPlayerAssets(savePath));

        assertTrue(ex.getMessage().contains("Malformed URL-encoded item ID"));
        assertArrayEquals(original, Files.readAllBytes(savePath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHIP한글🚀", "SHIP\uFFFD", "SHIP+ %,:;/", "SHIP%FF"})
    @DisplayName("Valid Unicode and escaped characters round-trip in both asset ID fields")
    void testValidUtf8AssetIdsRoundTrip(String id) throws IOException {
        PlayerAssetsState original = new PlayerAssetsState(100, Set.of(id), Map.of(id, 3));

        fileManager.savePlayerAssets(original, savePath);
        PlayerAssetsState loaded = fileManager.loadPlayerAssets(savePath);

        assertEquals(original.getBalance(), loaded.getBalance());
        assertEquals(original.getUnlockedContentIds(), loaded.getUnlockedContentIds());
        assertEquals(original.getItemQuantities(), loaded.getItemQuantities());
    }

    @Test
    @DisplayName("savePlayerAssets write failure preserves preexisting valid save file")
    void testFailedSavePreservesPreviousValidFile() throws IOException {
        Path isolatedDir = tempDir.resolve("restricted_dir");
        Files.createDirectories(isolatedDir);
        Path targetFile = isolatedDir.resolve("player_assets.properties");

        PlayerAssetsState initialValidState = new PlayerAssetsState(
                350,
                Set.of("SHIP_ORIGINAL"),
                Map.of("SHIELD", 2)
        );
        fileManager.savePlayerAssets(initialValidState, targetFile);
        assertTrue(Files.exists(targetFile));
        byte[] originalContent = Files.readAllBytes(targetFile);

        PlayerAssetsState nextState = new PlayerAssetsState(
                9999,
                Set.of("SHIP_NEW"),
                Map.of("SHIELD", 100)
        );

        PosixFileAttributeView posixView = Files.getFileAttributeView(isolatedDir, PosixFileAttributeView.class);
        DosFileAttributeView dosView = Files.getFileAttributeView(targetFile, DosFileAttributeView.class);
        Set<PosixFilePermission> originalPermissions = null;
        boolean originallyReadOnly = false;

        if (posixView != null) {
            originalPermissions = posixView.readAttributes().permissions();
        } else {
            assumeTrue(dosView != null, "Filesystem must support POSIX permissions or DOS read-only attributes");
            originallyReadOnly = dosView.readAttributes().isReadOnly();
        }

        try {
            if (posixView != null) {
                Set<PosixFilePermission> restrictedPermissions = EnumSet.copyOf(originalPermissions);
                restrictedPermissions.remove(PosixFilePermission.OWNER_WRITE);
                restrictedPermissions.remove(PosixFilePermission.GROUP_WRITE);
                restrictedPermissions.remove(PosixFilePermission.OTHERS_WRITE);
                posixView.setPermissions(restrictedPermissions);
                assumeFalse(Files.isWritable(isolatedDir), "Process must not bypass directory write permissions");
            } else {
                // Windows enforces read-only files when replacing the destination, unlike directories.
                dosView.setReadOnly(true);
                assertTrue(dosView.readAttributes().isReadOnly());
                assumeFalse(Files.isWritable(targetFile), "Process must respect the file's read-only attribute");
            }

            assertThrows(IOException.class, () -> fileManager.savePlayerAssets(nextState, targetFile));

            assertArrayEquals(originalContent, Files.readAllBytes(targetFile));
            PlayerAssetsState reloaded = fileManager.loadPlayerAssets(targetFile);
            assertEquals(350, reloaded.getBalance());
            assertTrue(reloaded.getUnlockedContentIds().contains("SHIP_ORIGINAL"));
            assertEquals(2, reloaded.getItemQuantities().get("SHIELD"));
        } finally {
            if (posixView != null) {
                posixView.setPermissions(originalPermissions);
            } else {
                dosView.setReadOnly(originallyReadOnly);
            }
        }
    }

    @Test
    @DisplayName("savePlayerAssets rejects null state before I/O begins")
    void testSaveNullStateThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> fileManager.savePlayerAssets(null, savePath));
    }

    @Test
    @DisplayName("Round-trip serialization preserves characters requiring encoding")
    void testRoundTripWithSpecialCharacters() throws IOException {
        Set<String> unlocks = Set.of("STAGE:01,BONUS", "SHIP/SPACE BLUE");
        Map<String, Integer> items = Map.of("POTION:HEALTH,SUPER", 5, "GEM%BLUE", 10);
        PlayerAssetsState state = new PlayerAssetsState(1200, unlocks, items);

        fileManager.savePlayerAssets(state, savePath);
        PlayerAssetsState loaded = fileManager.loadPlayerAssets(savePath);

        assertEquals(1200, loaded.getBalance());
        assertEquals(unlocks, loaded.getUnlockedContentIds());
        assertEquals(items, loaded.getItemQuantities());
    }
}
