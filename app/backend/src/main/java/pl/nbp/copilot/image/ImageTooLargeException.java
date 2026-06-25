package pl.nbp.copilot.image;

/**
 * Thrown by {@link ImageCompressor} when the compressed image still exceeds
 * the configured {@code app.image.max-bytes} limit.
 */
public class ImageTooLargeException extends RuntimeException {

    /**
     * Creates a new exception with a message describing the actual and maximum sizes.
     *
     * @param actualBytes the byte size of the compressed image
     * @param maxBytes    the configured maximum allowed size in bytes
     */
    public ImageTooLargeException(long actualBytes, long maxBytes) {
        super("Compressed image (%d bytes) exceeds limit (%d bytes)".formatted(actualBytes, maxBytes));
    }
}
