package net.ochibo.wishlist.core.material;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MaterialCalculation(List<MaterialSummaryRow> rows, Map<UUID, NodeQuantity> nodeQuantities) {}
