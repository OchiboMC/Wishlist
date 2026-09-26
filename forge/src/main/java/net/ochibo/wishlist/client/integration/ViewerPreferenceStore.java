package net.ochibo.wishlist.client.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class ViewerPreferenceStore {
    private static final String KEY = "recipeViewer";
    private final Path file;

    public ViewerPreferenceStore(Path file) {
        this.file = file;
    }

    public ViewerPreference load() {
        if (!Files.isRegularFile(file)) return ViewerPreference.AUTO;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            String value = properties.getProperty(KEY, "AUTO").trim().toUpperCase(Locale.ROOT);
            return ViewerPreference.valueOf(value);
        } catch (IOException | IllegalArgumentException e) {
            return ViewerPreference.AUTO;
        }
    }

    public void save(ViewerPreference preference) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Properties properties = new Properties();
        properties.setProperty(KEY, preference.name());
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Wishlist client settings");
        }
    }
}
