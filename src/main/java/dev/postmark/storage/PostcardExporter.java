package dev.postmark.storage;

import dev.postmark.model.Postcard;
import dev.postmark.render.PostcardPainter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Publish the two sides together; failed exports never leave an apparent completed pair. */
public final class PostcardExporter {
    private PostcardExporter() {}
    public static Path export(AlbumStore store,Postcard card) throws IOException {
        Path exports=store.directory().resolve("exports"); Files.createDirectories(exports);
        String suffix=UUID.randomUUID().toString();
        Path staging=Files.createDirectory(exports.resolve(".pending-"+suffix));
        Path target=exports.resolve("postmark-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+suffix.substring(0,8));
        try {
            write(staging.resolve("front.png"),PostcardPainter.paint(card,store::image));
            write(staging.resolve("envelope.png"),PostcardPainter.paintBack(card));
            try { Files.move(staging,target,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException | AccessDeniedException e) { Files.move(staging,target); }
            return target;
        } finally {
            // Only these two files can have been created inside this fresh task-owned directory.
            Files.deleteIfExists(staging.resolve("front.png"));
            Files.deleteIfExists(staging.resolve("envelope.png"));
            Files.deleteIfExists(staging);
        }
    }
    private static void write(Path path,java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        if(!ImageIO.write(image,"PNG",bytes)) throw new IOException("PNG encoder unavailable");
        Files.write(path,bytes.toByteArray(),StandardOpenOption.CREATE_NEW);
    }
}