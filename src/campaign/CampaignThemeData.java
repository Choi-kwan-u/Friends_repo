package campaign;

/** Resource paths for the seven official stages, independent of difficulty settings. */
public final class CampaignThemeData {
    private static final CampaignThemeData[] THEMES = {
        new CampaignThemeData("stage_01"), new CampaignThemeData("stage_02"),
        new CampaignThemeData("stage_03"), new CampaignThemeData("stage_04"),
        new CampaignThemeData("stage_05"), new CampaignThemeData("stage_06"),
        new CampaignThemeData("stage_07")
    };

    private final String backgroundPath;
    private final String bgmPath;

    private CampaignThemeData(String stage) {
        this.backgroundPath = "backgrounds/campaign/" + stage + ".png";
        this.bgmPath = "audio/bgm/campaign/" + stage + ".wav";
    }

    /** Returns null outside official Campaign levels so callers keep the default theme. */
    public static CampaignThemeData forLevel(int level) {
        return level >= 1 && level <= THEMES.length ? THEMES[level - 1] : null;
    }

    public String getBackgroundPath() {
        return backgroundPath;
    }

    /** Stages 3, 5 and 7 map directly to their supplied boss tracks. */
    public String getBgmPath() {
        return bgmPath;
    }
}
