package audio;

import javax.sound.sampled.AudioFormat;

/**
 * Holds the audio format and PCM sample bytes used for mixer playback.
 * The sample array is shared with the cache and must not be modified.
 */
public class AudioResource {

    /** Format describing the cached audio samples. */
    private final AudioFormat format;

    /** Audio sample bytes, excluding the source file's container headers. */
    private final byte[] data;

    /**
     * Creates an audio resource using the supplied format and sample array.
     * The array is stored directly without copying.
     *
     * @param format the format of the audio samples
     * @param data the audio sample bytes
     */
    AudioResource(AudioFormat format, byte[] data) {
        this.format = format;
        this.data = data;
    }

    /**
     * Returns the format describing the audio samples.
     *
     * @return the audio sample format
     */
    public AudioFormat getFormat() {
        return format;
    }

    /**
     * Returns the number of sample bytes, not the number of audio frames.
     *
     * @return the audio data length in bytes
     */
    public int size() {
        return data.length;
    }

    /**
     * Returns a single sample byte without exposing the backing array.
     *
     * @param index the zero-based byte index, not a sample or frame index
     * @return the byte at the specified index
     * @throws IndexOutOfBoundsException if the index is negative or at least {@link #size()}
     */
    public byte getData(int index) {
        return data[index];
    }
}
