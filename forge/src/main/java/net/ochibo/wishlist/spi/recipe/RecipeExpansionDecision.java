package net.ochibo.wishlist.spi.recipe;

import net.ochibo.wishlist.api.recipe.ExpansionPlan;
import net.ochibo.wishlist.api.recipe.ExpansionProblem;
import java.util.Objects;
import java.util.Optional;

/** Exactly one outcome; BLOCKED forbids less authoritative fallback interpretation. */
public final class RecipeExpansionDecision {
    public enum Kind { DECLINE, RESOLVED, BLOCKED }
    private static final RecipeExpansionDecision DECLINE = new RecipeExpansionDecision(
            Kind.DECLINE, Optional.empty(), Optional.empty());
    private final Kind kind;
    private final Optional<ExpansionPlan> plan;
    private final Optional<ExpansionProblem> problem;
    private RecipeExpansionDecision(Kind kind, Optional<ExpansionPlan> plan,
                                    Optional<ExpansionProblem> problem) {
        this.kind = kind;
        this.plan = plan;
        this.problem = problem;
    }
    /** This handler does not own the recipe semantics. */
    public static RecipeExpansionDecision decline() { return DECLINE; }
    /** The plan must describe the complete semantics, never a partial result. */
    public static RecipeExpansionDecision resolved(ExpansionPlan plan) {
        return new RecipeExpansionDecision(Kind.RESOLVED,
                Optional.of(Objects.requireNonNull(plan, "plan")), Optional.empty());
    }
    /** This handler understands the recipe but cannot represent it safely. */
    public static RecipeExpansionDecision blocked(ExpansionProblem problem) {
        return new RecipeExpansionDecision(Kind.BLOCKED, Optional.empty(),
                Optional.of(Objects.requireNonNull(problem, "problem")));
    }
    public Kind kind() { return kind; }
    public Optional<ExpansionPlan> plan() { return plan; }
    public Optional<ExpansionProblem> problem() { return problem; }
}
