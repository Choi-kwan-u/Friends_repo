package shop;

import currency.PermanentUpgrade;
import currency.PlayerAssetsState;
import engine.Core;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UpgradePreviewTest {
    @TempDir Path temp;

    @Test
    void previewCreditsAreSeededOnceAndNeverReplaceProgress() throws Exception {
        ShopPreview.seedUpgradePreview(Core.getFileManager(), temp);
        Path previewSave = temp.resolve(engine.FileManager.ASSETS_FILENAME);
        assertEquals(3000, Core.getFileManager().loadPlayerAssets(previewSave).getBalance());
        Core.getFileManager().savePlayerAssets(new PlayerAssetsState(17,
                Collections.singleton(PermanentUpgrade.HULL.getProductId(1)), null), previewSave);
        byte[] previous = Files.readAllBytes(previewSave);
        ShopPreview.seedUpgradePreview(Core.getFileManager(), temp);
        assertArrayEquals(previous, Files.readAllBytes(previewSave));
    }

    @Test
    void previewPreferencesAreIndependentOfOtherSessions() throws Exception {
        PreviewPreferencesFactory first = new PreviewPreferencesFactory();
        first.userRoot().node("engine").putInt("left", 65);
        first.userRoot().flush();
        assertEquals(65, first.userRoot().node("engine").getInt("left", 37));
        assertEquals(37, new PreviewPreferencesFactory().userRoot().node("engine").getInt("left", 37));
        assertEquals(37, first.systemRoot().node("engine").getInt("left", 37));
        first.userRoot().node("engine").removeNode();
        assertEquals(0, first.userRoot().childrenNames().length);
    }

}
