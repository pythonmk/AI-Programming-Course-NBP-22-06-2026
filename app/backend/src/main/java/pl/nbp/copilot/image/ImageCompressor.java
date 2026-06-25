package pl.nbp.copilot.image;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.config.AppProperties;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * Spring component that compresses raw image bytes and returns a JPEG data URL.
 *
 * <p>Processing steps:
 * <ol>
 *   <li>Re-encodes the image as JPEG regardless of the input format.</li>
 *   <li>Resizes so that the <em>longest edge</em> is at most 1568 pixels while
 *       preserving the aspect ratio. Images already within that bound are still
 *       re-encoded but never upscaled.</li>
 *   <li>Verifies the resulting byte size against {@code app.image.max-bytes};
 *       throws {@link ImageTooLargeException} when the limit is exceeded.</li>
 * </ol>
 *
 * @see ImageTooLargeException
 */
@Component
public class ImageCompressor {

    /** Maximum long edge in pixels after resizing. */
    private static final int MAX_EDGE_PX = 1568;

    private static final String DATA_URL_PREFIX = "data:image/jpeg;base64,";

    private final AppProperties appProperties;

    /**
     * Creates a new {@code ImageCompressor}.
     *
     * @param appProperties application configuration providing the image size limit
     */
    public ImageCompressor(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Compresses raw image bytes and returns a JPEG data URL.
     *
     * <p>The returned string has the form {@code data:image/jpeg;base64,<base64>}.
     * Images larger than 1568px on the longest edge are scaled down to fit.
     * Images already within the 1568px bound are re-encoded as JPEG but not upscaled.
     *
     * @param rawBytes raw image bytes in any format supported by {@code javax.imageio}
     * @return data URL string ready for use in a multimodal LLM call
     * @throws ImageTooLargeException if the compressed result exceeds the configured byte limit
     * @throws UncheckedIOException   if an I/O error occurs during compression
     */
    public String compress(byte[] rawBytes) {
        try {
            // Read the source image to determine its actual dimensions so we
            // can cap the target size and avoid upscaling small images.
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (source == null) {
                throw new UncheckedIOException(
                        new IOException("Cannot decode image: unsupported format or corrupt bytes"));
            }

            int targetWidth = Math.min(source.getWidth(), MAX_EDGE_PX);
            int targetHeight = Math.min(source.getHeight(), MAX_EDGE_PX);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thumbnails.of(source)
                    .size(targetWidth, targetHeight)
                    .keepAspectRatio(true)
                    .outputFormat("JPEG")
                    .toOutputStream(baos);

            byte[] compressed = baos.toByteArray();
            long maxBytes = appProperties.image().maxBytes();
            if (compressed.length > maxBytes) {
                throw new ImageTooLargeException(compressed.length, maxBytes);
            }

            return DATA_URL_PREFIX + Base64.getEncoder().encodeToString(compressed);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress image", e);
        }
    }
}
