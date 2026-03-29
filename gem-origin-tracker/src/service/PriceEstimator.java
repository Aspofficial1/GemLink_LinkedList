package service;

import model.GemLinkedList;
import model.GemNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PriceEstimator is Novel Feature 4 of the Ceylon Gem Origin Tracking System.
 *
 * It analyses the complete price history stored in each node of the
 * Doubly Linked List and calculates an estimated fair market value
 * for a gem based on its type, weight, origin, number of stages,
 * and the observed price growth pattern across the chain.
 *
 * The estimator also compares the estimated value against the actual
 * current price and flags gems that appear to be significantly
 * underpriced or overpriced relative to their journey data.
 *
 * This directly demonstrates the value of the Doubly Linked List
 * because the price at every node is used in the calculation —
 * the estimator traverses the full list from head to tail to
 * build the price growth profile before making its estimate.
 *
 * How the estimation works:
 *   1. Traverse the linked list from head to tail
 *   2. Calculate the observed price growth rate at each stage
 *   3. Apply gem type multipliers based on known market rates
 *   4. Apply weight multipliers — heavier gems command higher prices
 *   5. Apply origin multipliers — verified Ceylon gems are worth more
 *   6. Apply stage count multipliers — more stages = more provenance
 *   7. Produce a low estimate, a mid estimate, and a high estimate
 *   8. Compare estimates against the actual current price
 *   9. Flag as underpriced, overpriced, or fairly priced
 */
public class PriceEstimator {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem linked lists.
     * Injected via constructor.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier used to check if a gem is Ceylon verified.
     * Ceylon verification adds a premium to the estimated value.
     */
    private OriginVerifier originVerifier;

    // ---------------------------------------------------------
    // Gem type base price multipliers (per carat in rupees)
    // These are approximate Sri Lankan market rates used for estimation
    // ---------------------------------------------------------

    /**
     * Base price per carat in rupees for known gem types.
     * Used as the starting point for market value estimation.
     * All values are approximate mid-market rates.
     */
    private static final Map<String, Double> GEM_TYPE_BASE_PRICE = new HashMap<String, Double>() {{
        put("blue sapphire",   150000.0);
        put("ruby",            120000.0);
        put("yellow sapphire",  80000.0);
        put("pink sapphire",    90000.0);
        put("alexandrite",     200000.0);
        put("cat's eye",       100000.0);
        put("spinel",           60000.0);
        put("chrysoberyl",      50000.0);
        put("tourmaline",       40000.0);
        put("topaz",            30000.0);
        put("amethyst",         15000.0);
        put("garnet",           20000.0);
        put("moonstone",        25000.0);
        put("zircon",           18000.0);
        put("aquamarine",       35000.0);
        put("emerald",         180000.0);
        put("default",          40000.0);
    }};

    /**
     * Stage count multipliers — each additional stage increases
     * provenance and therefore estimated market value.
     */
    private static final Map<Integer, Double> STAGE_MULTIPLIERS = new HashMap<Integer, Double>() {{
        put(1, 1.0);   // mining only — raw unprocessed gem
        put(2, 2.5);   // cut and polished
        put(3, 4.0);   // cut, traded
        put(4, 6.0);   // cut, traded, exported or bought
        put(5, 7.5);   // full chain with buyer
    }};

    /**
     * Ceylon origin premium multiplier.
     * Genuine verified Ceylon gems command a higher market price
     * than gems of unverified or non-Ceylon origin.
     */
    private static final double CEYLON_VERIFIED_PREMIUM = 1.35;

    /**
     * Unverified origin discount multiplier.
     * Gems with unverified origin are discounted in market estimates.
     */
    private static final double UNVERIFIED_ORIGIN_DISCOUNT = 0.75;

    /**
     * Weight tier multipliers — larger gems are exponentially
     * more valuable per carat than smaller gems.
     */
    private static final double WEIGHT_UNDER_1CT   = 0.7;
    private static final double WEIGHT_1_TO_2CT    = 1.0;
    private static final double WEIGHT_2_TO_5CT    = 1.4;
    private static final double WEIGHT_5_TO_10CT   = 2.0;
    private static final double WEIGHT_OVER_10CT   = 3.0;

    /**
     * Confidence band — low estimate is 80% of mid estimate,
     * high estimate is 130% of mid estimate.
     */
    private static final double LOW_BAND  = 0.80;
    private static final double HIGH_BAND = 1.30;

    /**
     * Price deviation thresholds for flagging over/underpricing.
     * If actual price is below 60% of estimate it is underpriced.
     * If actual price is above 150% of estimate it is overpriced.
     */
    private static final double UNDERPRICED_THRESHOLD = 0.60;
    private static final double OVERPRICED_THRESHOLD  = 1.50;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new PriceEstimator with required service dependencies.
     *
     * @param trackingService the service for retrieving gem linked lists
     * @param originVerifier  the service for checking Ceylon verification
     */
    public PriceEstimator(TrackingService trackingService,
                          OriginVerifier originVerifier) {
        this.trackingService = trackingService;
        this.originVerifier  = originVerifier;
    }

    // ---------------------------------------------------------
    // Main estimation method
    // ---------------------------------------------------------

    /**
     * Calculates a full price estimate for a gem based on its
     * linked list journey data.
     *
     * Traverses the Doubly Linked List from head to tail to collect
     * all price data points, then applies type, weight, origin, and
     * stage count multipliers to produce a market value estimate.
     *
     * Returns a complete result map containing:
     *   gemId             — the gem ID
     *   gemType           — the gem type
     *   currentWeight     — current weight in carats
     *   actualCurrentPrice— the actual recorded current price
     *   estimatedLow      — low end of the estimated range
     *   estimatedMid      — mid point estimate
     *   estimatedHigh     — high end of the estimated range
     *   pricingStatus     — UNDERPRICED / FAIRLY_PRICED / OVERPRICED
     *   pricingStatusLabel— human readable status label
     *   pricingStatusColor— hex color for the status badge
     *   deviationPercent  — how far actual price deviates from estimate
     *   deviationLabel    — human readable deviation description
     *   priceGrowthRate   — observed growth rate across stages
     *   stageCount        — number of stages traversed
     *   isCeylonVerified  — whether origin is verified
     *   basePrice         — base price per carat used
     *   weightMultiplier  — weight multiplier applied
     *   originMultiplier  — origin multiplier applied
     *   stageMultiplier   — stage count multiplier applied
     *   priceHistory      — list of price at each stage
     *   recommendation    — actionable recommendation text
     *
     * @param gemId the ID of the gem to estimate
     * @return a Map containing the full estimation result
     */
    public Map<String, Object> estimateGemValue(String gemId) {
        Map<String, Object> result = new HashMap<>();

        // Get the gem linked list
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            result.put("error", "Gem not found: " + gemId);
            return result;
        }

        // Traverse the list from head to tail
        List<GemNode> stages = list.getAllStages();
        if (stages.isEmpty()) {
            result.put("error", "No stages found for gem: " + gemId);
            return result;
        }

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        // Extract key values from the linked list nodes
        String gemType      = miningNode.getGemType();
        double currentWeight= currentNode.getWeightInCarats();
        double actualPrice  = currentNode.getPriceInRupees();
        double miningPrice  = miningNode.getPriceInRupees();
        int    stageCount   = stages.size();
        boolean isCeylon    = originVerifier.verifyGemOrigin(gemId);

        // Build price history by traversing list
        List<Map<String, Object>> priceHistory = buildPriceHistory(stages);

        // Calculate observed price growth rate across the full list
        double priceGrowthRate = calculateGrowthRate(miningPrice, actualPrice, stageCount);

        // Step 1 — Get base price per carat for gem type
        double basePrice = getBasePricePerCarat(gemType);

        // Step 2 — Apply weight multiplier
        double weightMultiplier = getWeightMultiplier(currentWeight);

        // Step 3 — Apply origin multiplier
        double originMultiplier = isCeylon
                ? CEYLON_VERIFIED_PREMIUM
                : UNVERIFIED_ORIGIN_DISCOUNT;

        // Step 4 — Apply stage count multiplier
        double stageMultiplier = getStageMultiplier(stageCount);

        // Step 5 — Calculate mid estimate
        double estimatedMid = basePrice
                * currentWeight
                * weightMultiplier
                * originMultiplier
                * stageMultiplier;

        // Step 6 — Calculate low and high range
        double estimatedLow  = estimatedMid * LOW_BAND;
        double estimatedHigh = estimatedMid * HIGH_BAND;

        // Step 7 — Determine pricing status
        String pricingStatus = determinePricingStatus(actualPrice, estimatedMid);
        double deviationPercent = calculateDeviationPercent(actualPrice, estimatedMid);

        // Step 8 — Build full result map
        result.put("gemId",               gemId);
        result.put("gemType",             gemType);
        result.put("currentWeight",       currentWeight);
        result.put("stageCount",          stageCount);
        result.put("isCeylonVerified",    isCeylon);
        result.put("miningPrice",         miningPrice);
        result.put("actualCurrentPrice",  actualPrice);
        result.put("estimatedLow",        Math.round(estimatedLow));
        result.put("estimatedMid",        Math.round(estimatedMid));
        result.put("estimatedHigh",       Math.round(estimatedHigh));
        result.put("pricingStatus",       pricingStatus);
        result.put("pricingStatusLabel",  getPricingStatusLabel(pricingStatus));
        result.put("pricingStatusColor",  getPricingStatusColor(pricingStatus));
        result.put("pricingStatusTextColor", getPricingStatusTextColor(pricingStatus));
        result.put("deviationPercent",    Math.round(deviationPercent * 10.0) / 10.0);
        result.put("deviationLabel",      buildDeviationLabel(deviationPercent, pricingStatus));
        result.put("priceGrowthRate",     Math.round(priceGrowthRate * 10.0) / 10.0);
        result.put("basePrice",           basePrice);
        result.put("weightMultiplier",    weightMultiplier);
        result.put("originMultiplier",    originMultiplier);
        result.put("stageMultiplier",     stageMultiplier);
        result.put("priceHistory",        priceHistory);
        result.put("recommendation",      buildRecommendation(pricingStatus, deviationPercent, gemType));
        result.put("estimateRangeLabel",  buildRangeLabel(estimatedLow, estimatedMid, estimatedHigh));
        result.put("calculationBreakdown",buildCalculationBreakdown(
                basePrice, currentWeight, weightMultiplier,
                originMultiplier, stageMultiplier, estimatedMid));

        return result;
    }

    // ---------------------------------------------------------
    // Batch estimation
    // ---------------------------------------------------------

    /**
     * Calculates price estimates for all gems in the system.
     * Returns a list of summary estimates for the overview table.
     * Each entry contains the key pricing fields without the full breakdown.
     *
     * @return a list of summary estimate maps for all gems
     */
    public List<Map<String, Object>> estimateAllGems() {
        List<String> allIds = trackingService.getAllGemIds();
        List<Map<String, Object>> results = new ArrayList<>();

        for (String gemId : allIds) {
            try {
                Map<String, Object> estimate = estimateGemValue(gemId);
                if (!estimate.containsKey("error")) {
                    // Build a lightweight summary for list display
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("gemId",              estimate.get("gemId"));
                    summary.put("gemType",            estimate.get("gemType"));
                    summary.put("currentWeight",      estimate.get("currentWeight"));
                    summary.put("actualCurrentPrice", estimate.get("actualCurrentPrice"));
                    summary.put("estimatedMid",       estimate.get("estimatedMid"));
                    summary.put("estimatedLow",       estimate.get("estimatedLow"));
                    summary.put("estimatedHigh",      estimate.get("estimatedHigh"));
                    summary.put("pricingStatus",      estimate.get("pricingStatus"));
                    summary.put("pricingStatusLabel", estimate.get("pricingStatusLabel"));
                    summary.put("pricingStatusColor", estimate.get("pricingStatusColor"));
                    summary.put("pricingStatusTextColor", estimate.get("pricingStatusTextColor"));
                    summary.put("deviationPercent",   estimate.get("deviationPercent"));
                    summary.put("isCeylonVerified",   estimate.get("isCeylonVerified"));
                    summary.put("recommendation",     estimate.get("recommendation"));
                    results.add(summary);
                }
            } catch (Exception e) {
                System.out.println("  Warning: Could not estimate price for gem: " + gemId);
            }
        }

        return results;
    }

    /**
     * Returns a market overview summary across all gems.
     * Shows total estimated portfolio value, average deviation,
     * counts of underpriced and overpriced gems.
     *
     * @return a map with portfolio-level statistics
     */
    public Map<String, Object> getMarketOverview() {
        List<Map<String, Object>> allEstimates = estimateAllGems();

        double totalEstimated   = 0;
        double totalActual      = 0;
        int    underpriced      = 0;
        int    overpriced       = 0;
        int    fairlyPriced     = 0;
        double totalDeviation   = 0;

        for (Map<String, Object> est : allEstimates) {
            totalEstimated += toDouble(est.get("estimatedMid"));
            totalActual    += toDouble(est.get("actualCurrentPrice"));
            totalDeviation += Math.abs(toDouble(est.get("deviationPercent")));

            String status = (String) est.get("pricingStatus");
            if ("UNDERPRICED".equals(status))    underpriced++;
            else if ("OVERPRICED".equals(status)) overpriced++;
            else                                  fairlyPriced++;
        }

        double avgDeviation = allEstimates.isEmpty()
                ? 0 : totalDeviation / allEstimates.size();

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalGems",           allEstimates.size());
        overview.put("totalEstimatedValue", Math.round(totalEstimated));
        overview.put("totalActualValue",    Math.round(totalActual));
        overview.put("underpricedCount",    underpriced);
        overview.put("overpricedCount",     overpriced);
        overview.put("fairlyPricedCount",   fairlyPriced);
        overview.put("averageDeviation",    Math.round(avgDeviation * 10.0) / 10.0);
        overview.put("portfolioDifference", Math.round(totalEstimated - totalActual));

        return overview;
    }

    // ---------------------------------------------------------
    // Private calculation helpers
    // ---------------------------------------------------------

    /**
     * Looks up the base price per carat for a gem type.
     * Performs a case-insensitive partial match against known types.
     * Falls back to the default base price if no match is found.
     *
     * @param gemType the gem type string from the linked list node
     * @return the base price per carat in rupees
     */
    private double getBasePricePerCarat(String gemType) {
        if (gemType == null) return GEM_TYPE_BASE_PRICE.get("default");
        String lower = gemType.toLowerCase().trim();

        for (Map.Entry<String, Double> entry : GEM_TYPE_BASE_PRICE.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return GEM_TYPE_BASE_PRICE.get("default");
    }

    /**
     * Returns the weight multiplier for a gem based on its carat weight.
     * Larger gems are exponentially more valuable per carat.
     *
     * @param weightInCarats the current weight of the gem
     * @return the weight multiplier to apply
     */
    private double getWeightMultiplier(double weightInCarats) {
        if (weightInCarats < 1.0)  return WEIGHT_UNDER_1CT;
        if (weightInCarats < 2.0)  return WEIGHT_1_TO_2CT;
        if (weightInCarats < 5.0)  return WEIGHT_2_TO_5CT;
        if (weightInCarats < 10.0) return WEIGHT_5_TO_10CT;
        return WEIGHT_OVER_10CT;
    }

    /**
     * Returns the stage count multiplier for a gem.
     * More stages means more documented provenance and higher value.
     * Uses the pre-defined STAGE_MULTIPLIERS table with a cap at 5.
     *
     * @param stageCount the number of nodes in the linked list
     * @return the stage multiplier to apply
     */
    private double getStageMultiplier(int stageCount) {
        int key = Math.min(stageCount, 5);
        return STAGE_MULTIPLIERS.getOrDefault(key, 7.5);
    }

    /**
     * Calculates the observed compound price growth rate across stages.
     * Uses the formula: rate = ((finalPrice / initialPrice) ^ (1/stages)) - 1
     * Returns 0 if the mining price is zero to avoid division by zero.
     *
     * @param miningPrice  the price at the mining (head) node
     * @param currentPrice the price at the current (tail) node
     * @param stageCount   the number of stages in the list
     * @return the compound growth rate as a percentage
     */
    private double calculateGrowthRate(double miningPrice,
                                        double currentPrice,
                                        int stageCount) {
        if (miningPrice <= 0 || stageCount <= 1) return 0;
        double ratio = currentPrice / miningPrice;
        double rate  = Math.pow(ratio, 1.0 / (stageCount - 1)) - 1;
        return rate * 100;
    }

    /**
     * Determines the pricing status by comparing actual vs estimated price.
     *
     * @param actualPrice   the actual recorded current price
     * @param estimatedMid  the calculated mid estimate
     * @return UNDERPRICED, OVERPRICED, or FAIRLY_PRICED
     */
    private String determinePricingStatus(double actualPrice,
                                           double estimatedMid) {
        if (estimatedMid <= 0) return "FAIRLY_PRICED";
        double ratio = actualPrice / estimatedMid;
        if (ratio < UNDERPRICED_THRESHOLD) return "UNDERPRICED";
        if (ratio > OVERPRICED_THRESHOLD)  return "OVERPRICED";
        return "FAIRLY_PRICED";
    }

    /**
     * Calculates the deviation percentage between actual and estimated price.
     * Positive means actual is above estimate (overpriced).
     * Negative means actual is below estimate (underpriced).
     *
     * @param actualPrice  the actual recorded current price
     * @param estimatedMid the calculated mid estimate
     * @return the deviation as a percentage
     */
    private double calculateDeviationPercent(double actualPrice,
                                              double estimatedMid) {
        if (estimatedMid <= 0) return 0;
        return ((actualPrice - estimatedMid) / estimatedMid) * 100;
    }

    /**
     * Builds the price history list by traversing the linked list.
     * Each entry contains the stage details and price information.
     *
     * @param stages the ordered list of GemNode objects
     * @return a list of price history entry maps
     */
    private List<Map<String, Object>> buildPriceHistory(List<GemNode> stages) {
        List<Map<String, Object>> history = new ArrayList<>();
        double prevPrice = 0;

        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put("stageNumber",  i + 1);
            entry.put("stageName",    node.getStage().getLabel());
            entry.put("location",     node.getLocation());
            entry.put("date",         node.getStageDate().toString());
            entry.put("price",        node.getPriceInRupees());
            entry.put("weight",       node.getWeightInCarats());

            if (i > 0 && prevPrice > 0) {
                double increase = node.getPriceInRupees() - prevPrice;
                double pct      = (increase / prevPrice) * 100;
                entry.put("priceIncrease",        Math.round(increase));
                entry.put("priceIncreasePercent", Math.round(pct * 10.0) / 10.0);
            } else {
                entry.put("priceIncrease",        0);
                entry.put("priceIncreasePercent", 0.0);
            }

            prevPrice = node.getPriceInRupees();
            history.add(entry);
        }

        return history;
    }

    /**
     * Builds the calculation breakdown map showing each multiplier applied.
     * Used on the frontend to explain how the estimate was calculated.
     *
     * @param basePrice        base price per carat
     * @param weight           gem weight in carats
     * @param weightMultiplier weight multiplier applied
     * @param originMultiplier origin multiplier applied
     * @param stageMultiplier  stage count multiplier applied
     * @param finalEstimate    the calculated mid estimate
     * @return a map describing each step of the calculation
     */
    private List<Map<String, Object>> buildCalculationBreakdown(
            double basePrice, double weight, double weightMultiplier,
            double originMultiplier, double stageMultiplier,
            double finalEstimate) {

        List<Map<String, Object>> steps = new ArrayList<>();

        addStep(steps, "Base Price per Carat",
                String.format("Rs. %.0f / ct (gem type market rate)", basePrice),
                Math.round(basePrice));

        addStep(steps, "Weight Factor",
                String.format("%.2f ct × weight tier %.1fx", weight, weightMultiplier),
                Math.round(basePrice * weight * weightMultiplier));

        addStep(steps, "Origin Premium",
                String.format("Origin multiplier: %.2fx", originMultiplier),
                Math.round(basePrice * weight * weightMultiplier * originMultiplier));

        addStep(steps, "Provenance Factor",
                String.format("Stage count multiplier: %.1fx", stageMultiplier),
                Math.round(finalEstimate));

        return steps;
    }

    /**
     * Adds a single calculation step to the breakdown list.
     *
     * @param steps       the list to add to
     * @param label       the step label
     * @param description the step description
     * @param runningTotal the running total after this step
     */
    private void addStep(List<Map<String, Object>> steps,
                         String label, String description,
                         long runningTotal) {
        Map<String, Object> step = new HashMap<>();
        step.put("label",        label);
        step.put("description",  description);
        step.put("runningTotal", runningTotal);
        steps.add(step);
    }

    // ---------------------------------------------------------
    // Label and color helpers
    // ---------------------------------------------------------

    /**
     * Returns a human readable label for a pricing status.
     *
     * @param status the pricing status constant
     * @return a human readable label string
     */
    private String getPricingStatusLabel(String status) {
        switch (status) {
            case "UNDERPRICED":  return "Potentially Underpriced";
            case "OVERPRICED":   return "Potentially Overpriced";
            case "FAIRLY_PRICED":return "Fairly Priced";
            default:             return status;
        }
    }

    /**
     * Returns a background hex color for a pricing status badge.
     *
     * @param status the pricing status constant
     * @return a hex color string
     */
    private String getPricingStatusColor(String status) {
        switch (status) {
            case "UNDERPRICED":   return "#DCFCE7";
            case "OVERPRICED":    return "#FEE2E2";
            case "FAIRLY_PRICED": return "#DBEAFE";
            default:              return "#F3F4F6";
        }
    }

    /**
     * Returns a text hex color for a pricing status badge.
     *
     * @param status the pricing status constant
     * @return a hex color string
     */
    private String getPricingStatusTextColor(String status) {
        switch (status) {
            case "UNDERPRICED":   return "#166534";
            case "OVERPRICED":    return "#991B1B";
            case "FAIRLY_PRICED": return "#1E40AF";
            default:              return "#374151";
        }
    }

    /**
     * Builds a human readable deviation label string.
     *
     * @param deviationPercent the deviation as a percentage
     * @param pricingStatus    the pricing status constant
     * @return a formatted deviation description
     */
    private String buildDeviationLabel(double deviationPercent,
                                        String pricingStatus) {
        double abs = Math.abs(deviationPercent);
        switch (pricingStatus) {
            case "UNDERPRICED":
                return String.format("%.1f%% below estimated market value", abs);
            case "OVERPRICED":
                return String.format("%.1f%% above estimated market value", abs);
            default:
                return String.format("Within %.1f%% of estimated market value", abs);
        }
    }

    /**
     * Builds a formatted price range label string.
     *
     * @param low  the low estimate
     * @param mid  the mid estimate
     * @param high the high estimate
     * @return a formatted range description
     */
    private String buildRangeLabel(double low, double mid, double high) {
        return String.format("Rs. %s — Rs. %s (mid: Rs. %s)",
                formatPrice(low), formatPrice(high), formatPrice(mid));
    }

    /**
     * Builds an actionable recommendation string based on pricing status.
     *
     * @param status           the pricing status constant
     * @param deviationPercent the deviation percentage
     * @param gemType          the gem type for context
     * @return a recommendation string for the gem owner or buyer
     */
    private String buildRecommendation(String status,
                                        double deviationPercent,
                                        String gemType) {
        double abs = Math.abs(deviationPercent);
        switch (status) {
            case "UNDERPRICED":
                return String.format(
                    "This %s appears to be priced %.1f%% below its estimated market value. " +
                    "Consider obtaining a professional gemological valuation before selling.",
                    gemType, abs);
            case "OVERPRICED":
                return String.format(
                    "This %s appears to be priced %.1f%% above its estimated market value. " +
                    "Buyers should verify the gem quality and request a certified appraisal.",
                    gemType, abs);
            default:
                return String.format(
                    "This %s is priced within the expected market range. " +
                    "The price reflects its origin, weight, and journey provenance.",
                    gemType);
        }
    }

    /**
     * Formats a price as a compact string with K or M suffix.
     *
     * @param price the price to format
     * @return a formatted price string
     */
    private String formatPrice(double price) {
        if (price >= 1_000_000) {
            return String.format("%.1fM", price / 1_000_000);
        } else if (price >= 1_000) {
            return String.format("%.0fK", price / 1_000);
        }
        return String.format("%.0f", price);
    }

    /**
     * Safely converts an Object to a double value.
     * Returns 0.0 if the object is null or not a number.
     *
     * @param value the object to convert
     * @return the double value or 0.0
     */
    private double toDouble(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}