package pl.nbp.copilot.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.config.AppProperties;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImageCompressor}.
 *
 * <p>Real image bytes are generated in-memory via {@code javax.imageio.ImageIO}
 * and {@code BufferedImage} — no external fixture files required.
 */
@DisplayName("ImageCompressor")
class ImageCompressorTest {

    private AppProperties appProperties;
    private ImageCompressor compressor;

    @BeforeEach
    void setUp() {
        appProperties = mock(AppProperties.class);
        AppProperties.Image image = mock(AppProperties.Image.class);
        when(appProperties.image()).thenReturn(image);
        // Default: 5 MB limit — large enough for all happy-path tests
        when(image.maxBytes()).thenReturn(5L * 1024 * 1024);
        compressor = new ImageCompressor(appProperties);
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /** Creates a minimal synthetic JPEG byte array of the given dimensions. */
    private byte[] createJpeg(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        fillGradient(img);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "JPEG", baos);
        return baos.toByteArray();
    }

    /** Creates a minimal synthetic PNG byte array of the given dimensions. */
    private byte[] createPng(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        fillGradient(img);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    /** Fills image with a simple gradient so it is not entirely blank. */
    private void fillGradient(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, img.getWidth() / 2, img.getHeight());
        g.setColor(Color.BLUE);
        g.fillRect(img.getWidth() / 2, 0, img.getWidth() / 2, img.getHeight());
        g.dispose();
    }

    /** Decodes a data URL, strips the prefix and returns the image. */
    private BufferedImage decodeDataUrl(String dataUrl) throws Exception {
        String prefix = "data:image/jpeg;base64,";
        assertTrue(dataUrl.startsWith(prefix), "Data URL must start with " + prefix);
        byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("compress() returns string starting with 'data:image/jpeg;base64,'")
    void compress_returnsDataUrlPrefix() throws Exception {
        byte[] jpeg = createJpeg(200, 150);
        String result = compressor.compress(jpeg);
        assertTrue(result.startsWith("data:image/jpeg;base64,"),
                "Result must start with data:image/jpeg;base64,");
    }

    @Test
    @DisplayName("Base64 payload in data URL can be decoded without exception")
    void compress_base64IsValid() throws Exception {
        byte[] jpeg = createJpeg(200, 150);
        String result = compressor.compress(jpeg);
        String b64 = result.substring("data:image/jpeg;base64,".length());
        assertDoesNotThrow(() -> Base64.getDecoder().decode(b64),
                "Base64 payload must be decodable");
    }

    @Test
    @DisplayName("3000x2000 JPEG input is resized so the long edge is <= 1568px")
    void compress_largeJpeg_longEdgeAtMost1568() throws Exception {
        byte[] jpeg = createJpeg(3000, 2000);
        String result = compressor.compress(jpeg);
        BufferedImage out = decodeDataUrl(result);
        int longEdge = Math.max(out.getWidth(), out.getHeight());
        assertTrue(longEdge <= 1568,
                "Long edge must be <= 1568 but was " + longEdge);
    }

    @Test
    @DisplayName("100x80 PNG input is NOT upscaled (output dimensions <= 100x80)")
    void compress_smallPng_notUpscaled() throws Exception {
        byte[] png = createPng(100, 80);
        String result = compressor.compress(png);
        BufferedImage out = decodeDataUrl(result);
        assertTrue(out.getWidth() <= 100,
                "Width must not exceed original 100 but was " + out.getWidth());
        assertTrue(out.getHeight() <= 80,
                "Height must not exceed original 80 but was " + out.getHeight());
    }

    @Test
    @DisplayName("200x200 PNG is re-encoded as JPEG (data URL starts with data:image/jpeg)")
    void compress_png_reEncodedAsJpeg() throws Exception {
        byte[] png = createPng(200, 200);
        String result = compressor.compress(png);
        assertTrue(result.startsWith("data:image/jpeg;base64,"),
                "PNG input must be re-encoded as JPEG");
    }

    @Test
    @DisplayName("ImageTooLargeException thrown when compressed image exceeds imageMaxBytes")
    void compress_throwsWhenExceedsLimit() throws Exception {
        // Tiny limit (1 byte) — any real image will exceed it
        AppProperties.Image tinyLimit = mock(AppProperties.Image.class);
        when(tinyLimit.maxBytes()).thenReturn(1L);
        when(appProperties.image()).thenReturn(tinyLimit);

        byte[] jpeg = createJpeg(200, 200);
        assertThrows(ImageTooLargeException.class, () -> compressor.compress(jpeg),
                "Must throw ImageTooLargeException when compressed size exceeds limit");
    }

    @Test
    @DisplayName("Data URL round-trip: strip prefix, base64-decode, ImageIO.read returns non-null")
    void compress_roundTrip_imageIoReadNonNull() throws Exception {
        byte[] jpeg = createJpeg(400, 300);
        String result = compressor.compress(jpeg);
        String b64 = result.substring("data:image/jpeg;base64,".length());
        byte[] decoded = Base64.getDecoder().decode(b64);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(decoded));
        assertNotNull(img, "Decoded bytes must be readable by ImageIO.read");
    }
}
