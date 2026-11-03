package audio;

import engine.Core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mixes registered audio buses into a single PCM output line.
 * A daemon worker collects stereo samples, applies bus volumes, clips the final
 * sum to 16-bit range, and writes audio in chunks.
 */
public class AudioMixer implements Runnable {

    /** Required playback format: 44.1 kHz, signed 16-bit stereo PCM, little-endian. */
    public static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);

    /** Number of stereo frames mixed per chunk (10 milliseconds). */
    private static final int CHUNK_FRAMES = 441;

    /** Requested output line buffer size in frames (40 milliseconds). */
    private static final int BUFFER_FRAMES = CHUNK_FRAMES * 4;

    /** Application logger used to report worker failures. */
    public final Logger logger;

    /** Audio buses registered by caller-supplied keys. */
    private final Map<String, AudioBus> audioBuses = new HashMap<>();

    /** Reusable queue of individual left samples, drained after each bus. */
    private final Queue<Double> leftBuffer = new ArrayDeque<>();

    /** Reusable queue of individual right samples, drained after each bus. */
    private final Queue<Double> rightBuffer = new ArrayDeque<>();

    /** Whether the worker should continue producing audio. */
    private boolean alive = false;

    /** Output line receiving mixed PCM chunks. */
    private final SourceDataLine line;

    /** Optional callback invoked on the worker after an error and output cleanup. */
    private final MixerErrorListener unexpectedStopListener;

    /**
     * Opens the output line and immediately starts a daemon mixing worker.
     *
     * @param listener callback for worker errors after cleanup, or null to disable notification
     * @throws LineUnavailableException if the output line cannot be opened
     * @throws IllegalArgumentException if no line supports {@link #FORMAT}
     */
    public AudioMixer(MixerErrorListener listener) throws LineUnavailableException {
        logger = Core.getLogger();
        unexpectedStopListener = listener;

        line = AudioSystem.getSourceDataLine(FORMAT);
        try {
            line.open(FORMAT, BUFFER_FRAMES * FORMAT.getFrameSize());
            line.start();

            alive = true;
            Thread worker = new Thread(this);
            worker.setDaemon(true);
            worker.start();
        } catch (Exception e) {
            closeLine();
            alive = false;
            throw e;
        }
    }

    /**
     * Runs the output loop, handling partial writes and logging failures.
     * Invoked by the constructor's worker; callers must not start another loop.
     * Closes the output line before notifying the error listener.
     */
    @Override
    public void run() {
        Exception failure = null;
        try {
            byte[] chunk = new byte[CHUNK_FRAMES * FORMAT.getFrameSize()];
            while (isAlive() && line.isOpen()) {
                mix(chunk);
                int offset = 0;
                while (offset < chunk.length) {
                    int written = line.write(chunk, offset, chunk.length - offset);
                    if (written <= 0) throw new IllegalStateException("Audio output stopped");
                    offset += written;
                }
            }
        } catch (Exception e) {
            logger.severe("Audio mixer stopped due to an unexpected error: " + e);
            failure = e;
        } finally {
            try {
                closeLine();
            } finally {
                shutdown();
            }
        }
        if (failure != null && unexpectedStopListener != null)
            unexpectedStopListener.onError(failure);
    }

    /** Releases the output device even if stopping or flushing fails. */
    private void closeLine() {
        try {
            line.stop();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to stop audio output line.", e);
        }

        try {
            line.flush();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to flush audio output line.", e);
        }

        try {
            line.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to close audio output line.", e);
        }
    }

    /**
     * Fills a buffer with mixed frames, applying each bus's volume before final clipping.
     *
     * @param buffer the output buffer whose length is a multiple of the frame size
     */
    private synchronized void mix(byte[] buffer) {
        for (int offset = 0; offset < buffer.length; offset += FORMAT.getFrameSize()) {
            double left = 0.0;
            double right = 0.0;
            for (AudioBus bus : audioBuses.values()) {
                double tLeft = 0.0;
                double tRight = 0.0;
                bus.collectSamples(leftBuffer, rightBuffer);
                while (!leftBuffer.isEmpty() && !rightBuffer.isEmpty()) {
                    tLeft += leftBuffer.poll();
                    tRight += rightBuffer.poll();
                }
                left += tLeft * (bus.getVolume() / 50.0);
                right += tRight * (bus.getVolume() / 50.0);
            }
            writeSample(buffer, offset, left);
            writeSample(buffer, offset + 2, right);
        }
    }

    /**
     * Rounds and clips a sample, then stores it as a signed little-endian 16-bit value.
     *
     * @param output the destination byte array
     * @param offset the index of the sample's first byte
     * @param value the mixed sample amplitude
     */
    private static void writeSample(byte[] output, int offset, double value) {
        int sample = (int) Math.max(-32768, Math.min(32767, Math.round(value)));
        output[offset] = (byte) sample;
        output[offset + 1] = (byte) (sample >> 8);
    }

    /**
     * Registers an audio bus, replacing any bus stored under the same key.
     * Registering the same bus under different keys processes it more than once per frame.
     *
     * @param key the registration key
     * @param bus the bus to mix
     * @throws IllegalArgumentException if the bus is null
     */
    public synchronized void registerPlaybackGroup(String key, AudioBus bus) {
        if (bus == null) throw new IllegalArgumentException("Group cannot be null");
        audioBuses.put(key, bus);
    }

    /**
     * Removes a bus from this mixer without clearing its playbacks.
     *
     * @param key the registration key to remove
     * @throws IllegalArgumentException if the key is not registered
     */
    public synchronized void unregisterPlaybackGroup(String key) {
        AudioBus group = audioBuses.remove(key);
        if (group == null) throw new IllegalArgumentException("No playback group found for key : " + key);
    }

    /**
     * Returns the bus registered under a key.
     *
     * @param key the registration key
     * @return the registered audio bus
     * @throws IllegalArgumentException if the key is not registered
     */
    public synchronized AudioBus getPlaybackGroup(String key) {
        AudioBus group = audioBuses.get(key);
        if (group == null) throw new IllegalArgumentException("No playback group found for key : " + key);

        return group;
    }

    /**
     * Returns the worker's run flag, not whether its thread has fully terminated.
     *
     * @return true while the mixer is marked as running
     */
    public synchronized boolean isAlive() {
        return alive;
    }

    /**
     * Requests that the output loop stop after its current chunk.
     * Repeated calls are allowed. This method neither waits for worker termination
     * nor closes the output line, and it does not unblock a pending write.
     */
    public synchronized void shutdown() {
        alive = false;
    }

    /** Receives worker exceptions after the mixer has released its output line. */
    public interface MixerErrorListener {
        /**
         * Handles an unexpected mixer failure on the terminating worker thread.
         * Normal shutdown does not invoke this callback.
         *
         * @param e the exception that interrupted audio output
         */
        void onError(Exception e);
    }
}
