package net.ochibo.wishlist.core.persistence;

import net.ochibo.wishlist.core.model.IngredientChoice;
import net.ochibo.wishlist.core.model.RecipeKey;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.tree.RecipeNode;
import net.ochibo.wishlist.core.tree.TerminalReason;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WishlistBinaryCodec {
    public static final int VERSION = 3;
    private static final byte[] MAGIC = {'W','L','S','T'};
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_CHILDREN = 10_000;
    private static final int MAX_CANDIDATES = 10_000;
    private static final int MAX_STRING_BYTES = 1_048_576;
    private static final int MAX_DEPTH = 256;

    public void write(Wishlist wishlist, OutputStream output) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(output));
        out.write(MAGIC);
        out.writeInt(VERSION);
        writeUuid(out, wishlist.id());
        writeString(out, wishlist.name());
        out.writeLong(wishlist.createdAtEpochMillis());
        out.writeLong(wishlist.updatedAtEpochMillis());
        writeBoundedSize(out, wishlist.entries().size(), MAX_ENTRIES, "entries");
        for (WishlistEntry entry : wishlist.entries()) writeEntry(out, entry);
        out.flush();
    }

    public Wishlist read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input));
        byte[] magic = new byte[4];
        in.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++) if (magic[i] != MAGIC[i]) throw new IOException("Invalid WLST magic");
        int version = in.readInt();
        if (version < 1 || version > VERSION) throw new UnsupportedWishlistVersionException(version);
        UUID id = readUuid(in);
        String name = readString(in);
        long created = in.readLong();
        long updated = in.readLong();
        int count = readBoundedSize(in, MAX_ENTRIES, "entries");
        List<WishlistEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(readEntry(in, version));
        return Wishlist.restore(id, name, created, updated, entries);
    }

    private void writeEntry(DataOutputStream out, WishlistEntry entry) throws IOException {
        writeUuid(out, entry.id());
        writeString(out, entry.outputItemId());
        out.writeLong(entry.requestedCount());
        writeString(out, entry.rootRecipe().id());
        writeBoundedSize(out, entry.children().size(), MAX_CHILDREN, "entry children");
        for (RecipeNode node : entry.children()) writeNode(out, node, 0);
    }

    private WishlistEntry readEntry(DataInputStream in, int version) throws IOException {
        UUID id = readUuid(in);
        String output = readString(in);
        long requested = in.readLong();
        if (requested <= 0) throw new IOException("Invalid requested count: " + requested);
        RecipeKey root = new RecipeKey(readString(in));
        int childCount = readBoundedSize(in, MAX_CHILDREN, "entry children");
        List<RecipeNode> children = new ArrayList<>(childCount);
        for (int i = 0; i < childCount; i++) children.add(readNode(in, 0, version));
        return WishlistEntry.restore(id, output, requested, root, children);
    }

    private void writeNode(DataOutputStream out, RecipeNode node, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Tree depth exceeds " + MAX_DEPTH);
        writeUuid(out, node.id());
        writeIngredient(out, node.ingredient());
        out.writeBoolean(node.selectedRecipe() != null);
        if (node.selectedRecipe() != null) writeString(out, node.selectedRecipe().id());
        out.writeBoolean(node.selectedCandidateItemId() != null);
        if (node.selectedCandidateItemId() != null) writeString(out, node.selectedCandidateItemId());
        out.writeBoolean(node.expanded());
        out.writeByte(node.terminalReason().ordinal());
        writeBoundedSize(out, node.children().size(), MAX_CHILDREN, "node children");
        for (RecipeNode child : node.children()) writeNode(out, child, depth + 1);
    }

    private RecipeNode readNode(DataInputStream in, int depth, int version) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Tree depth exceeds " + MAX_DEPTH);
        UUID id = readUuid(in);
        IngredientChoice ingredient = readIngredient(in);
        RecipeKey selected = in.readBoolean() ? new RecipeKey(readString(in)) : null;
        String selectedCandidate = version >= 2 && in.readBoolean() ? readString(in) : null;
        boolean expanded = in.readBoolean();
        int reasonOrdinal = in.readUnsignedByte();
        TerminalReason[] reasons = TerminalReason.values();
        if (reasonOrdinal >= reasons.length) throw new IOException("Invalid terminal reason: " + reasonOrdinal);
        int count = readBoundedSize(in, MAX_CHILDREN, "node children");
        List<RecipeNode> children = new ArrayList<>(count);
        for (int i = 0; i < count; i++) children.add(readNode(in, depth + 1, version));
        RecipeNode node = new RecipeNode(id, ingredient);
        node.restoreState(selected, selectedCandidate, expanded, reasons[reasonOrdinal], children);
        return node;
    }

    private void writeIngredient(DataOutputStream out, IngredientChoice ingredient) throws IOException {
        writeString(out, ingredient.displayKey());
        out.writeLong(ingredient.count());
        writeBoundedSize(out, ingredient.candidates().size(), MAX_CANDIDATES, "ingredient candidates");
        for (String candidate : ingredient.candidates()) writeString(out, candidate);
    }

    private IngredientChoice readIngredient(DataInputStream in) throws IOException {
        String key = readString(in);
        long count = in.readLong();
        if (count <= 0) throw new IOException("Invalid ingredient count: " + count);
        int candidateCount = readBoundedSize(in, MAX_CANDIDATES, "ingredient candidates");
        List<String> candidates = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++) candidates.add(readString(in));
        return new IngredientChoice(key, candidates, count);
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("String too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Invalid string length: " + length);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeBoundedSize(DataOutputStream out, int size, int max, String label) throws IOException {
        if (size < 0 || size > max) throw new IOException(label + " exceeds limit: " + size);
        out.writeInt(size);
    }

    private static int readBoundedSize(DataInputStream in, int max, String label) throws IOException {
        int size;
        try { size = in.readInt(); }
        catch (EOFException e) { throw new IOException("Truncated " + label, e); }
        if (size < 0 || size > max) throw new IOException("Invalid " + label + " count: " + size);
        return size;
    }
}
