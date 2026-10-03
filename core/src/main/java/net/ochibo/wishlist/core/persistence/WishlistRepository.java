package net.ochibo.wishlist.core.persistence;

import net.ochibo.wishlist.core.model.Wishlist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class WishlistRepository {
    public record LoadError(Path file, String message, String wishlistName) {
        public LoadError(Path file, String message) { this(file, message, null); }
    }
    public record LoadBatch(List<Wishlist> wishlists, List<LoadError> errors) {}

    private final Path directory;
    private final WishlistBinaryCodec codec;

    public WishlistRepository(Path directory) { this(directory, new WishlistBinaryCodec()); }
    public WishlistRepository(Path directory, WishlistBinaryCodec codec) {
        this.directory = directory;
        this.codec = codec;
    }

    public Path directory() { return directory; }

    public void save(Wishlist wishlist) throws IOException {
        Files.createDirectories(directory);
        Path target = pathFor(wishlist.id());
        Path temp = directory.resolve(wishlist.id() + ".wlst.tmp");
        try (OutputStream out = Files.newOutputStream(temp)) { codec.write(wishlist, out); }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public List<Wishlist> loadAll() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<Wishlist> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.wlst")) {
            for (Path file : stream) {
                try (InputStream in = Files.newInputStream(file)) { result.add(codec.read(in)); }
            }
        }
        result.sort(Comparator.comparing(Wishlist::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Wishlist::id));
        return List.copyOf(result);
    }

    public LoadBatch loadAllTolerant() throws IOException {
        if (!Files.isDirectory(directory)) return new LoadBatch(List.of(), List.of());
        List<Wishlist> result = new ArrayList<>();
        List<LoadError> errors = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.wlst")) {
            for (Path file : stream) {
                try (InputStream in = Files.newInputStream(file)) {
                    result.add(codec.read(in));
                } catch (Exception e) {
                    String name = null;
                    try (InputStream in = Files.newInputStream(file)) { name = codec.readName(in); }
                    catch (Exception ignored) { /* A broken header has no recoverable name. */ }
                    errors.add(new LoadError(file,
                            e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()), name));
                }
            }
        }
        result.sort(Comparator.comparing(Wishlist::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Wishlist::id));
        errors.sort(Comparator.comparing(e -> e.file().toString()));
        return new LoadBatch(List.copyOf(result), List.copyOf(errors));
    }

    public Wishlist load(UUID id) throws IOException {
        try (InputStream in = Files.newInputStream(pathFor(id))) { return codec.read(in); }
    }

    public boolean delete(UUID id) throws IOException { return Files.deleteIfExists(pathFor(id)); }
    public Path pathFor(UUID id) { return directory.resolve(id.toString() + ".wlst"); }
}
