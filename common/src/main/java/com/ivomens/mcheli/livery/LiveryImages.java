package com.ivomens.mcheli.livery;

import com.ivomens.mcheli.config.LiveryPolicy;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Server-side checks for uploaded livery images.
 * <p>
 * Uploads come from untrusted clients and are later decoded by every other client that sees the
 * vehicle, so the server fully decodes each upload and stores its own re-encoded copy. That drops
 * any metadata or trailing data and guarantees every stored file is a plain, well-formed PNG.
 */
public final class LiveryImages {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] IHDR_TYPE = {'I', 'H', 'D', 'R'};
    /** The signature is followed by the IHDR chunk: length, type, then width and height. */
    private static final int IHDR_TYPE_OFFSET = 12;
    private static final int IHDR_WIDTH_OFFSET = 16;
    private static final int IHDR_HEIGHT_OFFSET = 20;
    private static final int IHDR_END = 24;
    private static final int MIN_DIMENSION = 16;

    static {
        // never touch a display on a dedicated server
        System.setProperty("java.awt.headless", "true");
    }

    private LiveryImages() {
    }

    /** A cleaned-up image ready to be stored. */
    public record Sanitized(byte[] png, String id, int width, int height) {
    }

    public static final class RejectedException extends Exception {
        public RejectedException(String message) {
            super(message);
        }
    }

    /**
     * Decodes, size-checks and re-encodes an uploaded PNG.
     *
     * @throws RejectedException with a message that can be shown to the uploader
     */
    public static Sanitized sanitize(byte[] upload, LiveryPolicy policy) throws RejectedException {
        if (upload.length > policy.maxBytes()) {
            throw new RejectedException("Image is larger than " + policy.maxBytes() / 1024 + " KB.");
        }
        if (!hasPngSignature(upload)) {
            throw new RejectedException("Only PNG images are supported.");
        }
        // A small file can declare a huge image, so check the header before decoding anything.
        if (upload.length < IHDR_END || !isIhdrChunk(upload)) {
            throw new RejectedException("The image could not be read.");
        }
        checkDimensions(readInt(upload, IHDR_WIDTH_OFFSET), readInt(upload, IHDR_HEIGHT_OFFSET), policy);

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(upload));
        } catch (IOException | RuntimeException e) {
            throw new RejectedException("The image could not be read.");
        }
        if (image == null) {
            throw new RejectedException("The image could not be read.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        checkDimensions(width, height, policy);

        byte[] png = encode(image);
        if (png.length > LiveryManager.MAX_IMAGE_BYTES) {
            throw new RejectedException("The image is too large once converted. Try a simpler image.");
        }
        return new Sanitized(png, sha256(png), width, height);
    }

    private static void checkDimensions(int width, int height, LiveryPolicy policy) throws RejectedException {
        if (width > policy.maxDimension() || height > policy.maxDimension()) {
            throw new RejectedException("Image must be at most " + policy.maxDimension() + " pixels on each side.");
        }
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            throw new RejectedException("Image is too small.");
        }
    }

    /** The PNG specification requires IHDR to be the first chunk. */
    private static boolean isIhdrChunk(byte[] data) {
        for (int i = 0; i < IHDR_TYPE.length; i++) {
            if (data[IHDR_TYPE_OFFSET + i] != IHDR_TYPE[i]) return false;
        }
        return true;
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8
                | (data[offset + 3] & 0xFF);
    }

    private static boolean hasPngSignature(byte[] data) {
        if (data.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    private static byte[] encode(BufferedImage image) throws RejectedException {
        BufferedImage argb = image;
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = argb.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(argb, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RejectedException("The image could not be converted.");
        }
    }

    /** Lower-case hex SHA-256, the form livery ids take. */
    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
