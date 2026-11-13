package com.pageturn.book;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.pageturn.common.error.ApiException;

/**
 * Turns an uploaded file into a safe, small cover picture.
 *
 * <p>The upload is never stored as-is. It must really decode as a JPG or PNG picture (the file name and the
 * browser's claimed type are ignored), it is shrunk to cover size, and a brand-new JPG is drawn from the
 * pixels. That throws away anything hidden in the file — scripts, malformed data, and photo metadata such
 * as GPS location.
 */
final class CoverImages {

    static final String CONTENT_TYPE = "image/jpeg";
    private static final int MAX_WIDTH = 600;
    private static final int MAX_HEIGHT = 900;
    private static final int MIN_SIDE = 50;
    /** Refuse absurd sizes before decoding ("decompression bomb" protection). */
    private static final int MAX_SIDE = 12_000;
    /** At most 40 megapixels (a modern phone photo is ~12–48 MP). */
    private static final long MAX_PIXELS = 40_000_000L;
    /** Only this many pictures are decoded at the same moment, so parallel uploads can't exhaust memory. */
    private static final java.util.concurrent.Semaphore DECODING = new java.util.concurrent.Semaphore(2);
    private static final String NOT_A_PICTURE = "This file isn't a valid JPG or PNG picture.";

    private CoverImages() {
    }

    static byte[] toSafeJpeg(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw ApiException.badRequest("Choose a picture to upload.");
        }
        boolean acquired;
        try {
            acquired = DECODING.tryAcquire(15, java.util.concurrent.TimeUnit.SECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            throw ApiException.tooManyRequests("The server is busy processing pictures. Please try again in a moment.");
        }
        try {
            return decodeAndRedraw(upload);
        }
        finally {
            DECODING.release();
        }
    }

    private static byte[] decodeAndRedraw(byte[] upload) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw ApiException.badRequest(NOT_A_PICTURE);
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw ApiException.badRequest(NOT_A_PICTURE);
                }
                reader.setInput(input, true, true); // ignore metadata entirely
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_SIDE || height < MIN_SIDE) {
                    throw ApiException.badRequest("The picture is too small (at least 50 × 50 pixels).");
                }
                if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw ApiException.badRequest("The picture is too large (at most 40 megapixels and 12000 pixels on a side).");
                }
                // Read only every n-th pixel of big pictures, chosen from the LARGER ratio, so even a very
                // long, thin picture is decoded at roughly cover size (a few MB of memory at most).
                int step = Math.max(1, (int) Math.floor(Math.max(width / (double) MAX_WIDTH, height / (double) MAX_HEIGHT)));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage decoded = reader.read(0, param);
                return encodeJpeg(fitInside(decoded));
            }
            finally {
                reader.dispose();
            }
        }
        catch (ApiException ex) {
            throw ex;
        }
        catch (IOException | RuntimeException ex) {
            throw ApiException.badRequest(NOT_A_PICTURE);
        }
    }

    /** Scales down to fit 600 × 900, onto a white background (transparent PNG areas become white). */
    private static BufferedImage fitInside(BufferedImage source) {
        double scale = Math.min(1.0, Math.min(MAX_WIDTH / (double) source.getWidth(), MAX_HEIGHT / (double) source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        }
        finally {
            g.dispose();
        }
        return result;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), param);
        }
        finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
