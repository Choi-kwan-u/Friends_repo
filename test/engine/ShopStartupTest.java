package engine;

import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import shop.ShopController;
import shop.ShopTestFixtures;
import static org.junit.jupiter.api.Assertions.*;

class ShopStartupTest {
    @TempDir
    Path temp;

    private FileManager files;
    private Path save;
    private boolean isolatedProcess;

    @Test
    void missingSaveCreatesEmptySessionWithoutWriting() throws Exception {
        if (!isolatedProcess) {
            runIsolated("missing");
            return;
        }
        PlayerAssets shared = new PlayerAssets();

        ShopController controller = Core.initializeShop(shared, files);

        assertEquals(0, shared.getBalance());
        assertEquals(0, controller.snapshot().getBalance());
        assertTrue(controller.snapshot().getUnlockedContentIds().isEmpty());
        assertTrue(controller.snapshot().getItemQuantities().isEmpty());
        assertEquals(6, controller.getProducts().size());
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, controller.getSaveStatus());
        assertFalse(Files.exists(save));
    }

    @Test
    void validSaveLoadsIntoTheSameSharedAssetObject() throws Exception {
        if (!isolatedProcess) {
            runIsolated("saved");
            return;
        }
        PlayerAssetsState persisted = new PlayerAssetsState(700,
                Collections.singleton(ShopTestFixtures.UNLOCK),
                Collections.singletonMap("legacy.item", 2));
        files.savePlayerAssets(persisted, save);
        byte[] saved = Files.readAllBytes(save);
        PlayerAssets shared = new PlayerAssets();

        ShopController controller = Core.initializeShop(shared, files);

        assertEquals(700, shared.getBalance());
        assertTrue(shared.isUnlocked(ShopTestFixtures.UNLOCK));
        assertEquals(2, shared.snapshot().getItemQuantities().get("legacy.item"));
        assertEquals(persisted.getBalance(), controller.snapshot().getBalance());
        assertEquals(persisted.getUnlockedContentIds(), controller.snapshot().getUnlockedContentIds());
        assertEquals(persisted.getItemQuantities(), controller.snapshot().getItemQuantities());
        assertEquals(ShopController.SaveStatus.NOT_NEEDED, controller.getSaveStatus());

        shared.restore(new PlayerAssetsState(900, persisted.getUnlockedContentIds(),
                persisted.getItemQuantities()));
        assertEquals(900, controller.snapshot().getBalance());
        assertArrayEquals(saved, Files.readAllBytes(save));
    }

    @Test
    void corruptLoadPreservesMemoryAndFileAndCanBeRetried() throws Exception {
        if (!isolatedProcess) {
            runIsolated("corrupt");
            return;
        }
        PlayerAssets shared = new PlayerAssets();
        PlayerAssetsState before = new PlayerAssetsState(1000,
                Collections.singleton(ShopTestFixtures.UNLOCK),
                Collections.singletonMap("legacy.item", 3));
        shared.restore(before);
        byte[] corrupt = "version=999\nbalance=0\n".getBytes(StandardCharsets.UTF_8);
        Files.write(save, corrupt);

        assertThrows(IOException.class, () -> Core.initializeShop(shared, files));

        assertEquals(before.getBalance(), shared.getBalance());
        assertEquals(before.getUnlockedContentIds(), shared.snapshot().getUnlockedContentIds());
        assertEquals(before.getItemQuantities(), shared.snapshot().getItemQuantities());
        assertArrayEquals(corrupt, Files.readAllBytes(save));

        PlayerAssetsState recovered = new PlayerAssetsState(650,
                Collections.singleton(ShopTestFixtures.UNLOCK),
                Collections.singletonMap("legacy.item", 1));
        files.savePlayerAssets(recovered, save);
        byte[] repairedSave = Files.readAllBytes(save);

        ShopController controller = Core.initializeShop(shared, files);

        assertEquals(650, shared.getBalance());
        assertTrue(shared.isUnlocked(ShopTestFixtures.UNLOCK));
        assertEquals(1, shared.snapshot().getItemQuantities().get("legacy.item"));
        assertEquals(650, controller.snapshot().getBalance());
        assertArrayEquals(repairedSave, Files.readAllBytes(save));
    }

    @Test
    void purchasesUseDefaultSaveAndRestoreOnStartup() throws Exception {
        if (!isolatedProcess) {
            runIsolated("purchase");
            return;
        }
        PlayerAssets shared = ShopTestFixtures.fundedAssets();
        ShopController controller = ShopTestFixtures.controllerUsingDefaultSave(shared, files);

        assertEquals(PurchaseResult.SUCCESS, controller.purchase(ShopTestFixtures.UPGRADE));
        assertEquals(900, files.loadPlayerAssets().getBalance());
        assertTrue(files.loadPlayerAssets().getUnlockedContentIds().contains(ShopTestFixtures.UPGRADE));
        assertEquals(ShopController.SaveStatus.SAVED, controller.getSaveStatus());

        assertEquals(PurchaseResult.SUCCESS, controller.purchase(ShopTestFixtures.UNLOCK));
        assertEquals(400, files.loadPlayerAssets().getBalance());
        assertTrue(files.loadPlayerAssets().getUnlockedContentIds().contains(ShopTestFixtures.UNLOCK));

        PlayerAssets restored = new PlayerAssets();
        ShopController reopened = Core.initializeShop(restored, files);
        assertEquals(400, reopened.snapshot().getBalance());
        assertTrue(restored.isUnlocked(ShopTestFixtures.UPGRADE));
        assertTrue(restored.isUnlocked(ShopTestFixtures.UNLOCK));
    }



    @Test
    void campaignRewardsUseTheShopsDefaultSaveAndSurviveReload() throws Exception {
        if (!isolatedProcess) {
            runIsolated("campaign-reward");
            return;
        }
        PlayerAssets shared = new PlayerAssets();
        ShopController shop = Core.initializeShop(shared, files);
        CampaignRewards rewards = new CampaignRewards(shared, files);
        event.GameplayEventPublisher events = new event.GameplayEventPublisher();
        try (CampaignRewards.StageSubscription subscription = rewards.subscribe(events, 1)) {
            events.publish(new event.LevelCompletedEvent(1, GameplayConfig.NORMAL));
        }
        assertEquals(100, shop.snapshot().getBalance());
        assertEquals(100, files.loadPlayerAssets().getBalance());
        assertEquals(PurchaseResult.SUCCESS, shop.purchase("upgrade.hull.1"));
        PlayerAssets restored = new PlayerAssets();
        Core.initializeShop(restored, files);
        assertEquals(0, restored.getBalance());
        assertTrue(restored.isUnlocked("upgrade.hull.1"));
        assertTrue(Files.isRegularFile(save));
    }

    private void runIsolated(String scenario) throws Exception {
        // Default saves sit beside the compiled classes, so a new working directory alone is not enough.
        Path source = codeLocation(FileManager.class);
        Path classes = temp.resolve(Files.isDirectory(source) ? "classes" : "classes.jar");
        if (Files.isDirectory(source)) {
            try (Stream<Path> paths = Files.walk(source)) {
                for (Path file : (Iterable<Path>) paths::iterator) {
                    if (file.toString().endsWith(".class") && Files.isRegularFile(file)) {
                        Path target = classes.resolve(source.relativize(file));
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target);
                    }
                }
            }
        } else {
            Files.copy(source, classes);
        }
        String classpath = classes + File.pathSeparator + codeLocation(ShopStartupTest.class);
        String runtimeClasspath = System.getProperty("invaders.test.classpath",
                System.getProperty("java.class.path"));
        for (String entry : runtimeClasspath.split(File.pathSeparator)) {
            classpath += File.pathSeparator + Paths.get(entry).toAbsolutePath();
        }
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Paths.get(System.getProperty("java.home"), "bin", executable);
        Path log = temp.resolve("process.log");
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        String coverageAgent = System.getProperty("invaders.test.coverageAgent");
        if (coverageAgent != null && !coverageAgent.isBlank()) {
            Matcher destination = Pattern.compile("destfile=([^,]+)").matcher(coverageAgent);
            if (!destination.find()) {
                throw new IllegalStateException("JaCoCo agent must specify a coverage destination.");
            }
            Path coverageFile = Paths.get(destination.group(1)).toAbsolutePath().normalize();
            String childDestination = temp.toAbsolutePath().normalize().relativize(coverageFile).toString();
            // Relative paths avoid Windows JDK 17 agent-option encoding issues in Korean paths.
            command.add(destination.replaceFirst(Matcher.quoteReplacement("destfile=" + childDestination)));
        }
        // Match the UTF-8 decoder used to read the child process log.
        Collections.addAll(command, "-Djava.awt.headless=true",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-cp", classpath, ShopStartupTest.class.getName(),
                temp.toString(), scenario);
        Process process = new ProcessBuilder(command)
                .directory(temp.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Isolated storage test timed out.");
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static Path codeLocation(Class<?> type) throws Exception {
        return Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    public static void main(String[] args) throws Exception {
        Path expected = Paths.get(args[0]).toAbsolutePath().normalize().resolve(FileManager.ASSETS_FILENAME);
        Path actual = FileManager.resolveStoragePath(FileManager.ASSETS_FILENAME).toAbsolutePath().normalize();
        assertEquals(expected, actual, "Refusing to use a save outside the isolated test directory.");

        ShopStartupTest test = new ShopStartupTest();
        test.isolatedProcess = true;
        test.files = Core.getFileManager();
        test.save = actual;
        switch (args[1]) {
        case "missing":
            test.missingSaveCreatesEmptySessionWithoutWriting();
            break;
        case "saved":
            test.validSaveLoadsIntoTheSameSharedAssetObject();
            break;
        case "corrupt":
            test.corruptLoadPreservesMemoryAndFileAndCanBeRetried();
            break;
        case "purchase":
            test.purchasesUseDefaultSaveAndRestoreOnStartup();
            break;
        case "campaign-reward":
            test.campaignRewardsUseTheShopsDefaultSaveAndSurviveReload();
            break;
        default:
            throw new IllegalArgumentException("Unknown storage test: " + args[1]);
        }
    }
}
