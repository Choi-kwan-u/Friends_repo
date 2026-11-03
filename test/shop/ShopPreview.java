package shop;

import audio.AudioManager;
import currency.PermanentUpgrade;
import currency.PlayerAssets;
import currency.PlayerAssetsState;
import currency.ShopCatalog.Category;
import engine.Core;
import engine.DrawManager;
import engine.FileManager;
import engine.Frame;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import screen.ShopScreen;
import javax.swing.JOptionPane;

/** Runs the shop with sample products and an isolated save file. */
public final class ShopPreview {
    private static final Canvas KEY_SOURCE = new Canvas();
    private static Path output;

    private ShopPreview() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--upgrades".equals(args[0])) {
            runUpgradePreview();
            return;
        }
        boolean smoke = args.length > 0 && "--smoke".equals(args[0]);
        output = Files.createTempDirectory(smoke ? "shop-smoke-" : "shop-preview-");
        System.out.println("Preview files: " + output);
        FileManager files = Core.getFileManager();
        ShopTestFixtures.SaveFile saveFile = new ShopTestFixtures.SaveFile(files,
                output.resolve("assets.properties"));
        boolean originalMuted = AudioManager.isMuted();
        AudioManager.setMuted(true);
        Frame frame = new Frame(448, 520);
        frame.setTitle("Shop preview - sample products only");
        Core.getDrawManager().setFrame(frame);
        try {
            PlayerAssets assets = ShopTestFixtures.fundedAssets();
            ShopController controller = ShopTestFixtures.controller(assets, saveFile,
                    ShopTestFixtures.previewProducts());
            if (smoke) {
                smoke(frame, assets, controller, files, saveFile);
            } else {
                int choice;
                do {
                    int result = frame.setScreen(new ShopScreen(
                            frame.getWidth(), frame.getHeight(), 60, controller));
                    check(result == ShopScreen.RETURN_TO_MAIN_MENU, "Return contract");
                    System.out.println("Returned Main Menu code: " + result);
                    releaseKeys();
                    choice = JOptionPane.showOptionDialog(frame,
                            "Shop returned code " + result + ". Reopen with the same assets?",
                            "Shop preview", JOptionPane.DEFAULT_OPTION,
                            JOptionPane.INFORMATION_MESSAGE, null,
                            new String[] { "Reopen shop", "Close preview" }, "Reopen shop");
                } while (choice == 0);
            }
            System.out.println("Preview artifacts: " + output);
        } finally {
            releaseKeys();
            AudioManager.setMuted(originalMuted);
            frame.dispose();
        }
    }

    private static void runUpgradePreview() throws Exception {
        Path directory = java.nio.file.Paths.get(System.getProperty("invaders.storageDir"));
        Files.createDirectories(directory);
        Files.createDirectories(java.nio.file.Paths.get(System.getProperty("user.home")));
        seedUpgradePreview(Core.getFileManager(), directory);
        System.out.println("Upgrade preview saves: " + directory);
        System.out.println("Open Shop, purchase upgrades, then start Campaign. Preview credits are granted once.");
        Core.run(ShopTestFixtures.previewProducts());
    }

    static void seedUpgradePreview(FileManager files, Path directory) throws IOException {
        Path save = directory.resolve(FileManager.ASSETS_FILENAME);
        if (!Files.exists(save)) {
            files.savePlayerAssets(new PlayerAssetsState(3000, null, null), save);
        }
    }

    private static void smoke(Frame frame, PlayerAssets assets, ShopController shop, FileManager files,
            ShopTestFixtures.SaveFile saveFile)
            throws Exception {
        int space = KeyEvent.VK_SPACE;
        int down = KeyEvent.VK_DOWN;
        int right = KeyEvent.VK_RIGHT;
        int escape = KeyEvent.VK_ESCAPE;
        int retry = KeyEvent.VK_R;
        run(frame, shop, new int[] {0, space, space, space, 0, down, 0, space, 0, space, 0, escape},
                new String[] {"01-products", "02-upgrade", null, null, null, null, null,
                    "03-permanent", null, "04-already-owned", null, null});
        check(assets.getBalance() == 400, "Both purchase paths and held confirmation");
        check(PermanentUpgrade.HULL.getLevel(assets.snapshot()) == 1, "One upgrade tier");
        check(assets.isUnlocked(ShopTestFixtures.UNLOCK), "Permanent ownership");
        Path save = saveFile.getPath();
        byte[] saved = Files.readAllBytes(save);

        Path blocker = output.resolve("not-a-directory");
        Files.write(blocker, new byte[] {1});
        saveFile.setPath(blocker.resolve("assets.properties"));
        run(frame, shop, new int[] {0, space, space, 0, escape},
                new String[] {null, "05-save-failed", null, null, null});
        check(assets.getBalance() == 150 && PermanentUpgrade.HULL.getLevel(assets.snapshot()) == 2,
                "Failed save retains completed purchase");
        check(Arrays.equals(saved, Files.readAllBytes(save)), "Previous save preserved");
        saveFile.setPath(save);
        run(frame, shop, new int[] {0, retry, retry, 0, escape},
                new String[] {"06-reentry-pending", "07-retry-saved", null, null, null});
        check(shop.getSaveStatus() == ShopController.SaveStatus.SAVED, "Retry saves");
        PlayerAssetsState restored = saveFile.load();
        check(restored.getBalance() == 150
                && PermanentUpgrade.HULL.getLevel(restored) == 2
                && restored.getUnlockedContentIds().contains(ShopTestFixtures.UNLOCK), "Reload both assets");

        run(frame, ShopTestFixtures.controller(assets, saveFile, java.util.Collections.<ShopProduct>emptyList()),
                new int[] {0, space}, new String[] {"08-empty", null});
        run(frame, ShopTestFixtures.controller(new PlayerAssets(), saveFile),
                new int[] {0, space, 0, escape}, new String[] {null, "09-insufficient", null, null});

        List<ShopProduct> many = new ArrayList<ShopProduct>();
        for (int i = 0; i < 8; i++) {
            many.add(new ShopProduct("TEST_" + i, "Sample product with a very long readable name " + i,
                    Category.UPGRADE, Integer.MAX_VALUE));
        }
        run(frame, ShopTestFixtures.controller(assets, saveFile, many),
                new int[] {0, right, 0, right, 0, right, 0, right, 0, escape},
                new String[] {"10-long-labels", null, null, null, null, "11-scroll", null, null, null, null});

        run(frame, ShopTestFixtures.controller(ShopTestFixtures.fundedAssets(), saveFile,
                        ShopTestFixtures.previewProducts()),
                new int[] {0, right, 0, down, 0, right, 0, KeyEvent.VK_UP, 0, escape},
                new String[] {"12-two-shelves", "13-upgrade-card", null, null, null,
                    "14-ship-card", null, "15-remembered-card", null, null});

        run(frame, shop, new int[] {0, KeyEvent.VK_UP, 0, space},
                new String[] {null, "16-back-option", null, null});
        System.out.println("SMOKE PASSED: purchases, held input, rejection, save retry,"
                + " reentry, reload, empty catalog, card navigation, Back and Escape.");
    }

    private static void run(Frame frame, ShopController controller, final int[] keys, final String[] captures) {
        releaseKeys();
        int result = frame.setScreen(new ShopScreen(frame.getWidth(), frame.getHeight(), 60, controller) {
            private int step;

            @Override public void initialize() {
                super.initialize();
                inputDelay = Core.getCooldown(0);
            }

            @Override protected void update() {
                if (step >= keys.length) throw new AssertionError("Screen failed to return to menu");
                releaseKeys();
                if (keys[step] != 0) setKey(keys[step], true);
                super.update();
                if (captures[step] != null) capture(captures[step]);
                step++;
            }
        });
        check(result == ShopScreen.RETURN_TO_MAIN_MENU, "Frame.setScreen return code");
    }

    private static void capture(String name) {
        try {
            Field buffer = DrawManager.class.getDeclaredField("backBuffer");
            buffer.setAccessible(true);
            ImageIO.write((BufferedImage) buffer.get(null), "png", output.resolve(name + ".png").toFile());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void releaseKeys() {
        for (int code = 0; code < 256; code++) setKey(code, false);
    }

    private static void setKey(int code, boolean down) {
        KeyEvent event = new KeyEvent(KEY_SOURCE, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
