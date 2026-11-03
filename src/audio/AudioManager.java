package audio;

import engine.Core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides global audio control for background music and sound effects.
 * This class manages BGM playback, SFX playback, volume levels,
 * and the global mute state.
 */
public class AudioManager {

    /** Application logger. */
    private static final Logger logger = Core.getLogger();

    /** Shared audio resource cache for background music and sound effects. */
    private static AudioCache audioCache;

    /** Shared mixer that combines BGM and SFX into a single audio output line. */
    private static AudioMixer audioMixer;

    /** Looping audio bus for background music. */
    private static final AudioBus bgmBus = new AudioBus(true);

    /** Non-looping audio bus for independently controlled sound effects. */
    private static final AudioBus sfxBus = new AudioBus();

    /** Whether the mixer, audio buses, and cache were successfully initialized. */
    private static boolean initialized = false;

    /** Global mute state, visible across audio control threads. */
    private static volatile boolean muted = false;

    /** Total initialization attempts, including startup and mixer recovery. */
    private static int initAttempts = 0;

    /** Maximum initialization attempts during this application run. */
    private static final int MAX_INIT_ATTEMPTS = 10;

    static {
        initialize();
    }

    /**
     * Initializes the audio system and required internal resources.
     * Returns immediately if already initialized or the attempt limit is reached.
     * Each attempt clears active playbacks but preserves bus settings and cached
     * resources. The error listener requests recovery after mixer cleanup.
     */
    private synchronized static void initialize() {
        if (initialized || initAttempts >= MAX_INIT_ATTEMPTS) return;
        initAttempts++;
        try {
            bgmBus.stopAll();
            sfxBus.stopAll();
            if (audioMixer != null) audioMixer.shutdown();
            audioMixer = new AudioMixer(new AudioMixer.MixerErrorListener() {
                /** Marks audio unavailable and retries initialization under the manager lock. */
                @Override
                public void onError(Exception e) {
                    synchronized (AudioManager.class) {
                        initialized = false;
                        initialize();
                    }
                }
            });
            audioMixer.registerPlaybackGroup("bgm", bgmBus);
            audioMixer.registerPlaybackGroup("sfx", sfxBus);
            if (audioCache == null) {
                audioCache = new AudioCache();
                audioCache.preload("audio/bgm");
                audioCache.preload("audio/sfx");
            }
            logger.info("Audio system initialized.");
            initialized = true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize audio system: " + e, e);
        }
    }

    /**
     * Plays the specified background music in a continuous loop.
     * Playback failures are logged.
     * Source WAV resources are converted to the mixer's 44.1 kHz, 16-bit signed
     * stereo PCM format by the cache; 48 kHz and mono sources are supported.
     *
     * @param path the resource path relative to the classpath root,
     *             without a leading slash or the res directory prefix
     */
    public static void playBGM(String path) {
        initialize();
        if (!initialized) return;
        try {
            AudioResource resource = audioCache.getAudioData(path);
            AudioBus group = bgmBus;
            synchronized (group) {
                group.stopAll();
                group.play(resource);
                group.setPaused(false);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to play BGM [path=" + path + "]: " + e, e);
        }
    }

    /**
     * Stops the currently playing background music.
     */
    public static void stopBGM() {
        try {
            bgmBus.stopAll();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to stop BGM: " + e, e);
        }
    }

    /**
     * Pauses the currently playing background music.
     */
    public static void pauseBGM() {
        try {
            bgmBus.setPaused(true);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to pause BGM: " + e, e);
        }
    }

    /**
     * Resumes the previously paused background music.
     */
    public static void resumeBGM() {
        try {
            bgmBus.setPaused(false);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to resume BGM: " + e, e);
        }
    }

    /**
     * Plays the specified sound effect once.
     * Multiple sound effects can play simultaneously.
     * Each playback receives a separate ID.
     * Finished playbacks are automatically removed by the bus during sample collection.
     * Playback failures are logged.
     * Source WAV resources are converted to the mixer's 44.1 kHz, 16-bit signed
     * stereo PCM format by the cache; 48 kHz and mono sources are supported.
     *
     * @param path the resource path relative to the classpath root,
     *             without a leading slash or the res directory prefix
     * @return the playback ID, or -1 if playback fails
     */
    public static int playSFX(String path) {
        initialize();
        if (!initialized) return -1;
        try {
            return sfxBus.play(audioCache.getAudioData(path));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to play SFX [path=" + path + "]: " + e, e);
        }
        return -1;
    }

    /**
     * Stops the specified sound effect by removing its playback from the bus.
     * Cached audio sample data remains available for subsequent playback.
     * If the ID is unknown or already completed, logs a warning
     * and returns without stopping any other sound effect.
     *
     * @param id the playback ID returned by playSFX
     */
    public static void stopSFX(int id) {
        try {
            sfxBus.stop(id);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to stop SFX [id=" + id + "]: " + e, e);
        }
    }

    /**
     * Sets the background music volume.
     * Invalid volume values are logged and ignored.
     *
     * @param vol the volume level, from 0 to 100
     */
    public static void setBGMVolume(int vol) {
        try {
            bgmBus.setVolume(vol);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to set BGM volume [volume=" + vol + "]: " + e, e);
        }
    }

    /**
     * Sets the sound effect volume.
     * Invalid volume values are logged and ignored.
     *
     * @param vol the volume level, from 0 to 100
     */
    public static void setSFXVolume(int vol) {
        try {
            sfxBus.setVolume(vol);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to set SFX volume [volume=" + vol + "]: " + e, e);
        }
    }

    /**
     * Returns the current background music volume.
     *
     * @return the BGM volume level, from 0 to 100
     */
    public static int getBGMVolume() {
        try {
            return bgmBus.getVolume();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get BGM volume; returning default 50: " + e, e);
        }
        return 50;
    }

    /**
     * Returns the current sound effect volume.
     *
     * @return the SFX volume level, from 0 to 100
     */
    public static int getSFXVolume() {
        try {
            return sfxBus.getVolume();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get SFX volume; returning default 50: " + e, e);
        }
        return 50;
    }

    /**
     * Updates the global mute state without changing stored volume levels.
     *
     * @param value true to mute audio, false to restore audio
     */
    public static synchronized void setMuted(boolean value) {
        try {
            bgmBus.setMuted(value);
            sfxBus.setMuted(value);
            muted = value;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to set global mute [muted=" + value + "]: " + e, e);
        }
    }

    /**
     * Returns the global mute setting.
     *
     * @return true if muting is enabled, otherwise false
     */
    public static synchronized boolean isMuted() {
        return muted;
    }
}
