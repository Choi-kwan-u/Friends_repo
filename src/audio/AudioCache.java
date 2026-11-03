package audio;

import engine.Core;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Caches audio formats and sample data by classpath resource path.
 * Resources are loaded on first request and reused for subsequent playback.
 * Source audio is converted to the mixer's PCM format once when first loaded;
 * source files are not modified. Cache access is synchronized.
 */
public class AudioCache {

    /** Application logger. */
    private final Logger logger = Core.getLogger();

    /** Successfully loaded audio resources indexed by their classpath paths. */
    private final Map<String, AudioResource> cacheMap = new HashMap<>();

    /** Loads all WAV files beneath a classpath directory and logs newly cached and failed counts. */
    public synchronized void preload(String directory) {
        if (directory == null) return;
        URL resource = AudioCache.class.getClassLoader().getResource(directory);
        if (resource == null) return;
        int cachedBefore = cacheMap.size();
        int failed = 0;
        try {
            if ("jar".equals(resource.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) resource.openConnection();
                // Own this handle so closing it does not close the class loader's cached JAR.
                connection.setUseCaches(false);
                String prefix = connection.getEntryName();
                if (!prefix.endsWith("/")) prefix += "/";
                try (JarFile jar = connection.getJarFile()) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String path = entry.getName();
                        if (!entry.isDirectory() && path.startsWith(prefix)
                                && path.toLowerCase(Locale.ROOT).endsWith(".wav")) {
                            if (getAudioData(path) == null) failed++;
                        }
                    }
                }
            } else {
                Path root = Path.of(resource.toURI());
                try (Stream<Path> files = Files.walk(root)) {
                    var wavFiles = files.filter(Files::isRegularFile)
                            .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".wav"))
                            .iterator();
                    while (wavFiles.hasNext()) {
                        String path = directory + "/"
                                + root.relativize(wavFiles.next()).toString().replace('\\', '/');
                        if (getAudioData(path) == null) failed++;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to preload audio: " + directory + " / " + e);
        }
        logger.info("Audio preload [directory=" + directory + "]: "
                + (cacheMap.size() - cachedBefore) + " newly cached, " + failed + " failed");
    }

    /**
     * Returns cached audio data, loading and caching the resource if necessary.
     * Null paths and failed loads are not cached.
     *
     * @param path the resource path relative to the classpath root,
     *             without a leading slash or the res directory prefix
     * @return the cached or newly loaded resource, or null if the path is null
     *         or the resource cannot be loaded
     */
    public synchronized AudioResource getAudioData(String path) {
        if (path == null) return null;
        if (cacheMap.containsKey(path)) return cacheMap.get(path);

        AudioResource data = readAudioData(path);
        if (data != null) cacheMap.put(path, data);

        return data;
    }

    /**
     * Reads the format and sample data from an audio resource.
     * The input stream is closed automatically, and loading failures are logged.
     * This method does not update the cache.
     *
     * @param path the non-null classpath resource path
     * @return the loaded resource, or null if it is missing or reading fails
     */
    private AudioResource readAudioData(String path) {
        URL resource = AudioCache.class.getClassLoader().getResource(path);

        if (resource == null) {
            logger.warning("Audio resource not found: " + path);
            return null;
        }

        try (AudioInputStream stream = AudioSystem.getAudioInputStream(resource)) {
            if (AudioMixer.FORMAT.matches(stream.getFormat())) {
                return readPcm(stream);
            }

            if (!AudioSystem.isConversionSupported(AudioMixer.FORMAT, stream.getFormat())) {
                throw new IOException("Cannot convert audio format: " + stream.getFormat());
            }
            try (AudioInputStream converted =
                         AudioSystem.getAudioInputStream(AudioMixer.FORMAT, stream)) {
                return readPcm(converted);
            }
        } catch (Exception e) {
            logger.warning("Failed to read audio file: " + path + " / " + e);
        }

        return null;
    }

    /** Reads complete mixer frames from decoded or resampled output. */
    private static AudioResource readPcm(AudioInputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int frameSize = AudioMixer.FORMAT.getFrameSize();
        byte[] buffer = new byte[frameSize * 1024];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        byte[] data = output.toByteArray();
        if (data.length == 0 || data.length % frameSize != 0) {
            throw new IOException("Audio must contain complete, non-empty PCM frames");
        }
        return new AudioResource(AudioMixer.FORMAT, data);
    }
}
