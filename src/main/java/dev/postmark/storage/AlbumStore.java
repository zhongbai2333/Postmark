package dev.postmark.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.postmark.model.Album;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

public final class AlbumStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    public AlbumStore(Path root, String scope) throws IOException {
        directory = root.resolve(hash(scope.getBytes(StandardCharsets.UTF_8)).substring(0, 24));
        Files.createDirectories(directory.resolve("assets"));
    }
    public Path directory() { return directory; }
    public Album load() throws IOException {
        Path path = directory.resolve("album.json");
        if (!Files.exists(path)) return Album.empty();
        if (Files.size(path) > 32 * 1024 * 1024) throw new IOException("Album exceeds 32 MB");
        try (Reader reader = Files.newBufferedReader(path)) {
            Album album = JSON.fromJson(reader, Album.class);
            if (album == null) throw new IOException("Album is empty");
            return album;
        } catch (RuntimeException e) {
            throw new IOException("Cannot read album; original file has been preserved: " + path, e);
        }
    }
    public synchronized void save(Album album) throws IOException {
        byte[] bytes=JSON.toJson(album).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>32*1024*1024) throw new IOException("Album exceeds 32 MB; previous draft was preserved");
        writeAtomic(directory.resolve("album.json"),bytes);
    }
    public String putImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", bytes)) throw new IOException("PNG encoder unavailable");
        byte[] data = bytes.toByteArray(); String name = hash(data) + ".png";
        Path path = assetPath(name);
        if (!Files.exists(path)) writeAtomic(path, data);
        return name;
    }
    public Path assetPath(String name) throws IOException {
        if (name == null || !name.matches("[a-f0-9]{64}\\.png")) throw new IOException("Invalid image asset name");
        return directory.resolve("assets").resolve(name);
    }
    public BufferedImage image(String name) throws IOException {
        BufferedImage image = ImageIO.read(assetPath(name).toFile());
        if (image == null) throw new IOException("Unreadable image: " + name);
        return image;
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static void writeAtomic(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".postmark-", ".tmp");
        try {
            Files.write(temporary, data);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}