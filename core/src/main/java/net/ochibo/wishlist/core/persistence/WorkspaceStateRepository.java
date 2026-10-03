package net.ochibo.wishlist.core.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class WorkspaceStateRepository {
    private static final int MAGIC = 0x57535441; // WSTA
    private static final int VERSION = 2;
    private static final int MAX_COMBINED = 10_000;
    private static final int MAX_COMPLETED = 10_000;
    private static final int MAX_KEY_BYTES = 1_048_576;

    private final Path file;

    public WorkspaceStateRepository(Path file) {
        this.file = file;
    }

    public Optional<WorkspaceState> load() throws IOException {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid workspace state magic");
            int version = in.readInt();
            if (version < 1 || version > VERSION) throw new IOException("Unsupported workspace state version: " + version);
            UUID active = readUuid(in);
            int count = in.readInt();
            if (count < 0 || count > MAX_COMBINED) throw new IOException("Invalid combined selection count: " + count);
            List<UUID> combined = new ArrayList<>(count);
            for (int i = 0; i < count; i++) combined.add(readUuid(in));
            List<String> completed = new ArrayList<>();
            if (version >= 2) {
                int completedCount = in.readInt();
                if (completedCount < 0 || completedCount > MAX_COMPLETED)
                    throw new IOException("Invalid completed material count: " + completedCount);
                for (int i = 0; i < completedCount; i++) completed.add(readString(in));
            }
            return Optional.of(new WorkspaceState(active, combined, completed));
        } catch (EOFException e) {
            throw new IOException("Truncated workspace state", e);
        }
    }

    public void save(WorkspaceState state) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                writeUuid(out, state.activeId());
                out.writeInt(state.combinedSelection().size());
                for (UUID id : state.combinedSelection()) writeUuid(out, id);
                if (state.completedMaterials().size() > MAX_COMPLETED)
                    throw new IOException("Too many completed materials");
                out.writeInt(state.completedMaterials().size());
                for (String key : state.completedMaterials()) writeString(out, key);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_KEY_BYTES) throw new IOException("Invalid material key length: " + length);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_KEY_BYTES) throw new IOException("Material key is too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
