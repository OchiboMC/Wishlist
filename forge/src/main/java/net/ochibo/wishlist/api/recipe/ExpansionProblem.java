package net.ochibo.wishlist.api.recipe;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Stable machine-readable problem code and human-readable diagnostic detail. */
public final class ExpansionProblem {
    private final ResourceLocation code;
    private final String message;

    private ExpansionProblem(ResourceLocation code, String message) {
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }
    public static ExpansionProblem of(ResourceLocation code, String message) {
        return new ExpansionProblem(code, message);
    }
    public ResourceLocation code() { return code; }
    public String message() { return message; }
}
