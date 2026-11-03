package audio;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns active playbacks and their shared volume, mute, pause, and loop settings.
 * Supplies individual stereo samples for the mixer to sum and scale.
 * Playback and setting operations are synchronized on this bus.
 */
public class AudioBus {
    /** Active playbacks indexed by IDs assigned within this bus. */
    private Map<Integer, AudioPlayback> playbacks = new ConcurrentHashMap<>();

    /** Volume from 0 to 100; the mixer treats 50 as the original gain. */
    private int volume = 50;

    /** Whether sample collection leaves all playback positions unchanged. */
    private boolean paused = false;

    /** Whether samples are suppressed while playback positions still advance. */
    private boolean muted = false;

    /** Whether playbacks restart when they reach the end of their resources. */
    private boolean looping;

    /** Next playback ID to assign within this bus. */
    private int nextId = 0;

    /**
     * Creates an audio bus with volume 50, initially unpaused and unmuted.
     *
     * @param looping whether playbacks repeat when they reach the end
     */
    public AudioBus(boolean looping) {
        this.looping = looping;
    }

    /** Creates a non-looping audio bus with volume 50. */
    public AudioBus() {
        this(false);
    }

    /**
     * Adds an independent playback starting at the beginning of a resource.
     * Playback follows this bus's current settings.
     *
     * @param resource the audio resource in {@link AudioMixer#FORMAT}
     * @return the playback ID assigned by this bus
     * @throws IllegalArgumentException if the format is incompatible or the data
     *         is empty or does not contain complete frames
     * @throws NullPointerException if the resource is null
     */
    public synchronized int play(AudioResource resource) {
        int id = nextId++;
        playbacks.put(id, new AudioPlayback(resource));

        return id;
    }

    /**
     * Removes a playback without discarding its cached audio resource.
     *
     * @param id the playback ID assigned by this bus
     * @throws IllegalArgumentException if the ID is not active
     */
    public synchronized void stop(int id) {
        AudioPlayback playback = playbacks.remove(id);
        if (playback == null)
            throw new IllegalArgumentException("No active playback found for ID: " + id);
    }

    /** Removes all playbacks without changing bus settings or cached resources. */
    public synchronized void stopAll() {
        playbacks.clear();
    }

    /**
     * Collects one frame from each active playback by appending its unscaled
     * left and right samples to the respective queues. Existing queue contents
     * are not cleared. The mixer applies this bus's volume after summing samples.
     * Muting suppresses samples but still advances playbacks; pausing suppresses
     * samples and prevents advancement. Finished non-looping playbacks are removed.
     *
     * @param left a non-null queue with sufficient capacity for the left samples
     * @param right a distinct, non-null queue with sufficient capacity for the right samples
     */
    public synchronized void collectSamples(Queue<Double> left, Queue<Double> right) {
        for (Map.Entry<Integer, AudioPlayback> entry : playbacks.entrySet()) {
            AudioPlayback playback = entry.getValue();
            if (!muted && !paused) {
                left.offer(playback.getLeftSample());
                right.offer(playback.getRightSample());
            }
            if (!paused) playback.advance(looping);
            if (playback.isFinished()) playbacks.remove(entry.getKey());
        }
    }

    /**
     * Sets the bus volume without changing playback positions.
     *
     * @param volume the level from 0 to 100; 50 preserves the original gain
     * @throws IllegalArgumentException if the level is outside 0 to 100
     */
    public synchronized void setVolume(int volume) {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100: " + volume);
        }
        this.volume = volume;
    }

    /**
     * Returns the configured volume, including while muted.
     *
     * @return the volume level from 0 to 100
     */
    public synchronized int getVolume() {
        return volume;
    }

    /**
     * Sets whether sample collection pauses all playbacks.
     *
     * @param paused true to preserve playback positions, false to resume advancement
     */
    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Returns whether playback advancement is paused.
     *
     * @return true if the bus is paused
     */
    public synchronized boolean isPaused() {
        return paused;
    }

    /**
     * Sets whether samples are suppressed without changing the configured volume.
     * Unpaused playbacks continue advancing while muted.
     *
     * @param muted true to suppress samples, false to restore them
     */
    public synchronized void setMuted(boolean muted) {
        this.muted = muted;
    }

    /**
     * Returns whether sample output is muted.
     *
     * @return true if the bus is muted
     */
    public synchronized boolean isMuted() {
        return muted;
    }

    /**
     * Sets the loop policy for current and future playbacks.
     *
     * @param looping true to restart playbacks at the end, false to let them finish
     */
    public synchronized void setLooping(boolean looping) {
        this.looping = looping;
    }

    /**
     * Returns whether playbacks restart at the end of their resources.
     *
     * @return true if looping is enabled
     */
    public synchronized boolean isLooping() {
        return looping;
    }

    /** Holds a shared PCM resource and the byte position of one playback. */
    private static class AudioPlayback {
        /** Shared source samples, read without modifying the cached resource. */
        private AudioResource resource;
        /** Byte offset of the current stereo frame, or the data size at the end. */
        private int position = 0;

        /**
         * Creates playback at the beginning of a resource in the mixer format.
         *
         * @param resource the shared, non-empty PCM resource
         * @throws IllegalArgumentException if the format or frame alignment is invalid
         * @throws NullPointerException if the resource is null
         */
        public AudioPlayback(AudioResource resource) {
            if (!AudioMixer.FORMAT.matches(resource.getFormat()))
                throw new IllegalArgumentException("Unexpected audio format: " + resource.getFormat());

            if (resource.size() == 0 || resource.size() % AudioMixer.FORMAT.getFrameSize() != 0)
                throw new IllegalArgumentException("Audio must contain complete stereo PCM frames");

            this.resource = resource;
        }

        /**
         * Reads the current left sample without advancing playback or applying gain.
         *
         * @return the signed 16-bit sample value, or zero after playback ends
         */
        public double getLeftSample() {
            if (position >= resource.size())
                return 0.0;

            return (short) ((resource.getData(position) & 0xff) | (resource.getData(position + 1) << 8));
        }

        /**
         * Reads the current right sample without advancing playback or applying gain.
         *
         * @return the signed 16-bit sample value, or zero after playback ends
         */
        public double getRightSample() {
            if (position >= resource.size())
                return 0.0;

            return (short) ((resource.getData(position + 2) & 0xff) | (resource.getData(position + 3) << 8));
        }

        /**
         * Advances one stereo frame, wrapping to the beginning when looping.
         * Non-looping playback stays at the end once all frames are consumed.
         *
         * @param loop true to restart playback when it reaches the end
         */
        public void advance(boolean loop) {
            if (position < resource.size())
                position += AudioMixer.FORMAT.getFrameSize();
            if (position == resource.size()) {
                if (loop) position = 0;
            }
        }

        /**
         * Returns whether playback has consumed all frames without wrapping.
         *
         * @return true if the playback position is at the end of the resource
         */
        public boolean isFinished() {
            return position == resource.size();
        }
    }
}
