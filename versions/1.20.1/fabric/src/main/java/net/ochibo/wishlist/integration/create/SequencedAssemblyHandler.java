package net.ochibo.wishlist.integration.create;

import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import net.ochibo.wishlist.api.recipe.ExpansionPlan;
import net.ochibo.wishlist.api.recipe.OutputOption;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionDecision;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionHandler;
import net.ochibo.wishlist.spi.recipe.RecipeExpansionRequest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Create 6.0.x Sequenced Assembly semantics.
 *
 * <p>The initial ingredient is consumed once. Additional step ingredients and
 * fluids are consumed once per loop. A deployer held item marked
 * {@code keepHeldItem} is auxiliary instead of consumable. Unknown step classes
 * block expansion rather than falling back to a lossy JEI interpretation.</p>
 */
public final class SequencedAssemblyHandler implements RecipeExpansionHandler<SequencedAssemblyRecipe> {
    @Override
    public RecipeExpansionDecision expand(RecipeExpansionRequest<SequencedAssemblyRecipe> request) {
        SequencedAssemblyRecipe recipe = request.recipe();
        if (recipe.getClass() != SequencedAssemblyRecipe.class) return RecipeExpansionDecision.decline();

        int loops = recipe.getLoops();
        if (loops <= 0) {
            return CreateExpansionSupport.blocked("create_sequence_loops", "Sequenced Assembly has a non-positive loop count");
        }

        Target target = target(recipe.resultPool, request.targetOutput());
        if (target == null) return RecipeExpansionDecision.decline();
        if (target.ambiguousAmount()) {
            return CreateExpansionSupport.blocked("create_sequence_target_amount",
                    "Sequenced Assembly has multiple matching target outputs with different amounts");
        }

        ExpansionPlan.Builder plan = ExpansionPlan.builder(target.stack(), target.stack().getCount())
                .successProbability(target.probability());
        double totalWeight = totalWeight(recipe.resultPool);
        if (!(totalWeight > 0) || !Double.isFinite(totalWeight)) {
            return CreateExpansionSupport.blocked("create_sequence_outputs", "Sequenced Assembly has invalid result weights");
        }
        for (ProcessingOutput output : recipe.resultPool) {
            ItemStack stack = output.getStack();
            if (stack.isEmpty()) continue;
            double probability = output.getChance() / totalWeight;
            if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
                return CreateExpansionSupport.blocked("create_sequence_probability", "Sequenced Assembly has invalid result probability");
            }
            plan.addOutput(OutputOption.of(stack, stack.getCount(), probability));
        }

        if (!CreateExpansionSupport.addConsumed(plan, recipe.getIngredient(), 1)) {
            return CreateExpansionSupport.blocked("create_sequence_initial",
                    "Sequenced Assembly initial ingredient cannot be represented losslessly");
        }

        Set<ItemLike> machines = new HashSet<>();
        for (SequencedRecipe<?> sequenced : recipe.getSequence()) {
            ProcessingRecipe<?> step = sequenced.getRecipe();
            IAssemblyRecipe assembly = sequenced.getAsAssemblyRecipe();
            if (!knownStep(step)) {
                return CreateExpansionSupport.blocked("create_sequence_unknown_step",
                        "Unsupported Sequenced Assembly step class: " + step.getClass().getName());
            }
            if (!assembly.supportsAssembly()) {
                return CreateExpansionSupport.blocked("create_sequence_unsupported_step",
                        "Sequenced Assembly step reports that assembly is unsupported: " + step.getClass().getName());
            }

            assembly.addRequiredMachines(machines);
            if (step instanceof DeployerApplicationRecipe deployer) {
                RecipeExpansionDecision problem = addDeployer(plan, deployer, loops);
                if (problem != null) return problem;
                continue;
            }

            List<Ingredient> itemIngredients = new ArrayList<>();
            assembly.addAssemblyIngredients(itemIngredients);
            for (Ingredient ingredient : itemIngredients) {
                if (!CreateExpansionSupport.addConsumed(plan, ingredient, loops)) {
                    return CreateExpansionSupport.blocked("create_sequence_step_ingredient",
                            "Sequenced Assembly step ingredient cannot be represented losslessly");
                }
            }

            List<FluidIngredient> fluidIngredients = new ArrayList<>();
            assembly.addAssemblyFluidIngredients(fluidIngredients);
            for (FluidIngredient fluid : fluidIngredients) {
                long amount;
                try {
                    amount = Math.multiplyExact((long) fluid.getRequiredAmount(), loops);
                } catch (ArithmeticException overflow) {
                    return CreateExpansionSupport.blocked("create_sequence_amount_overflow",
                            "Sequenced Assembly fluid amount overflows a 64-bit quantity");
                }
                long millibuckets = CreateExpansionSupport.millibucketsRoundedUp(amount);
                if (millibuckets <= 0 || !CreateExpansionSupport.addFluid(plan, fluid.getMatchingFluidStacks(), millibuckets)) {
                    return CreateExpansionSupport.blocked("create_sequence_fluid",
                            "Sequenced Assembly fluid ingredient cannot be represented losslessly");
                }
            }
        }
        CreateExpansionSupport.addMachines(plan, machines);
        return RecipeExpansionDecision.resolved(plan.build());
    }

    private static RecipeExpansionDecision addDeployer(
            ExpansionPlan.Builder plan, DeployerApplicationRecipe deployer, int loops) {
        Ingredient held = deployer.getRequiredHeldItem();
        if (deployer.shouldKeepHeldItem()) {
            if (!CreateExpansionSupport.addAuxiliary(plan, held, 1)) {
                return CreateExpansionSupport.blocked("create_sequence_deployer_tool",
                        "Reusable deployer item cannot be represented losslessly");
            }
        } else if (!CreateExpansionSupport.addConsumed(plan, held, loops)) {
            return CreateExpansionSupport.blocked("create_sequence_deployer_input",
                    "Deployer input cannot be represented losslessly");
        }
        return null;
    }

    private static boolean knownStep(ProcessingRecipe<?> step) {
        Class<?> type = step.getClass();
        return type == DeployerApplicationRecipe.class
                || type == FillingRecipe.class
                || type == PressingRecipe.class
                || type == CuttingRecipe.class;
    }

    private static Target target(List<ProcessingOutput> outputs, ItemStack requested) {
        double total = totalWeight(outputs);
        if (!(total > 0) || !Double.isFinite(total)) return null;
        ItemStack selected = ItemStack.EMPTY;
        double selectedWeight = 0;
        int amount = -1;
        boolean ambiguous = false;
        for (ProcessingOutput output : outputs) {
            ItemStack stack = output.getStack();
            if (stack.isEmpty() || !CreateExpansionSupport.sameRequestedItem(requested, stack)) continue;
            if (selected.isEmpty()) {
                selected = stack.copy();
                amount = stack.getCount();
            } else if (amount != stack.getCount() || !ItemStack.isSameItemSameTags(selected, stack)) {
                ambiguous = true;
            }
            selectedWeight += output.getChance();
        }
        if (selected.isEmpty()) return null;
        return new Target(selected, selectedWeight / total, ambiguous);
    }

    private static double totalWeight(List<ProcessingOutput> outputs) {
        double total = 0;
        for (ProcessingOutput output : outputs) total += output.getChance();
        return total;
    }

    private record Target(ItemStack stack, double probability, boolean ambiguousAmount) { }
}
