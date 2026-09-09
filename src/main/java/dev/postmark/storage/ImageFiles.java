package dev.postmark.storage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class ImageFiles {
    private ImageFiles() {}
    public static BufferedImage readPhoto(Path path) throws IOException {
        if (Files.size(path) > 32L * 1024 * 1024) throw new IOException("Photo exceeds 32 MB");
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Use a PNG or JPEG image");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
                    throw new IOException("Use a PNG or JPEG image");
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || (long)w*h > 32_000_000)
                    throw new IOException("Photo exceeds 8192 pixels or 32 megapixels");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}