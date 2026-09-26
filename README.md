# Wishlist

Wishlist is a client-side recipe and materials tracker for Minecraft. The `main` branch currently contains the Forge 1.20.1 implementation in [`forge`](forge). Future Minecraft versions and loader implementations are planned under `versions/<minecraft-version>/`.

## Requirements

- Java 17
- Minecraft 1.20.1
- Forge 47.4.x
- JEI 15.56.0.205 or later and EMI 1.1.24 or later are optional

The mod only needs to be installed on the client.

## Build

From `forge`, run `./gradlew build` (or `gradlew.bat build` on Windows). The mod jar is created in `forge/build/libs`.

## Controls

- Press `O` to open Wishlist.
- Press `H` while a container is open to highlight required items.
- Use the star button on a recipe to add it to a wishlist.

The Wishlist screen can open its settings from the title in the top-left corner. The settings include HUD visibility, size, item count and background opacity.

## License

MIT. See [LICENSE](LICENSE).
