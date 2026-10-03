package net.ochibo.wishlist.client;

import net.ochibo.wishlist.core.persistence.ClientWorldKey;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public final class ClientContextResolver {
    private ClientContextResolver() {}

    public static ClientWorldKey current(Minecraft minecraft) {
        if (minecraft.getCurrentServer() != null) {
            return ClientWorldKey.server(minecraft.getCurrentServer().ip);
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return ClientWorldKey.local(root.toString());
        }
        throw new IllegalStateException("No world or server is currently open");
    }
}
