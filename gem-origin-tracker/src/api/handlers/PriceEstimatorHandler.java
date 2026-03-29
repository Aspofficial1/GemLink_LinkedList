package api.handlers;

import api.ApiResponse;
import model.GemLinkedList;
import service.PriceEstimator;
import service.TrackingService;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PriceEstimatorHandler handles all HTTP requests related to gem market
 * value estimation and price analysis.
 *
 * Each method corresponds to one API endpoint registered in ApiRouter.
 * Every method reads from the PriceEstimator service and returns a
 * JSON string produced by ApiResponse.
 *
 * The price estimator calculates market value ranges based on gem type,
 * weight, origin verification status, and the number of documented
 * stages in the Doubly Linked List journey.
 *
 * Endpoints handled:
 *   GET /api/estimate/:gemId          — full estimate for one gem
 *   GET /api/estimate/:gemId/summary  — brief estimate summary
 *   GET /api/estimate/all             — estimates for all gems
 *   GET /api/estimate/overview        — market overview statistics
 *   GET /api/estimate/compare         — compare estimates of two gems
 */
public class PriceEstimatorHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The PriceEstimator handles all market value calculation logic.
     * Injected via constructor from ApiRouter.
     */
    private PriceEstimator priceEstimator;

    /**
     * The TrackingService is used to validate gem IDs and retrieve
     * gem data before running estimations.
     */
    private TrackingService trackingService;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new PriceEstimatorHandler with required dependencies.
     *
     * @param priceEstimator  the service for market value calculations
     * @param trackingService the service for gem data retrieval
     */
    public PriceEstimatorHandler(PriceEstimator priceEstimator,
                                  TrackingService trackingService) {
        this.priceEstimator  = priceEstimator;
        this.trackingService = trackingService;
    }

    // ---------------------------------------------------------
    // GET /api/estimate/:gemId — full estimate for one gem
    // ---------------------------------------------------------

    /**
     * Returns a full price estimation for a specific gem.
     *
     * Traverses the gem's Doubly Linked List from head to tail,
     * collects all price data points, applies gem type, weight,
     * origin, and stage count multipliers, and returns:
     *   - low, mid, and high price estimates
     *   - pricing status: UNDERPRICED, FAIRLY_PRICED, OVERPRICED
     *   - deviation percentage from estimated market value
     *   - step-by-step calculation breakdown
     *   - price history across all stages
     *   - actionable recommendation for the gem owner or buyer
     *
     * @param request  the incoming HTTP request with :gemId path param
     * @param response the outgoing HTTP response
     * @return a JSON string with the full estimation result
     */
    public String getEstimateForGem(Request request, Response response) {
        try {
            String gemId = request.params(":gemId");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Gem ID is required."
                ).toJson();
            }

            // Validate the gem exists
            GemLinkedList list = trackingService.getGemList(gemId.trim());
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            // Run the full estimation
            Map<String, Object> estimate =
                    priceEstimator.estimateGemValue(gemId.trim());

            if (estimate.containsKey("error")) {
                response.status(400);
                return ApiResponse.error(
                        (String) estimate.get("error")
                ).toJson();
            }

            response.status(200);
            return ApiResponse.success(
                    "Price estimation completed for gem " + gemId,
                    estimate
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Price estimation failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/estimate/:gemId/summary — brief estimate summary
    // ---------------------------------------------------------

    /**
     * Returns a brief price estimation summary for a specific gem.
     * Lighter than the full estimation — returns only the key fields
     * without the detailed calculation breakdown or price history.
     *
     * Used on the dashboard and track gem page to show a quick
     * pricing indicator without loading the full estimation data.
     *
     * Summary fields returned:
     *   gemId, gemType, actualCurrentPrice, estimatedMid,
     *   estimatedLow, estimatedHigh, pricingStatus,
     *   pricingStatusLabel, pricingStatusColor, deviationPercent,
     *   deviationLabel, recommendation, isCeylonVerified
     *
     * @param request  the incoming HTTP request with :gemId path param
     * @param response the outgoing HTTP response
     * @return a JSON string with the brief estimation summary
     */
    public String getEstimateSummaryForGem(Request request, Response response) {
        try {
            String gemId = request.params(":gemId");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            GemLinkedList list = trackingService.getGemList(gemId.trim());
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            Map<String, Object> full =
                    priceEstimator.estimateGemValue(gemId.trim());

            if (full.containsKey("error")) {
                response.status(400);
                return ApiResponse.error((String) full.get("error")).toJson();
            }

            // Build lightweight summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("gemId",                 full.get("gemId"));
            summary.put("gemType",               full.get("gemType"));
            summary.put("currentWeight",         full.get("currentWeight"));
            summary.put("stageCount",            full.get("stageCount"));
            summary.put("isCeylonVerified",      full.get("isCeylonVerified"));
            summary.put("actualCurrentPrice",    full.get("actualCurrentPrice"));
            summary.put("estimatedLow",          full.get("estimatedLow"));
            summary.put("estimatedMid",          full.get("estimatedMid"));
            summary.put("estimatedHigh",         full.get("estimatedHigh"));
            summary.put("estimateRangeLabel",    full.get("estimateRangeLabel"));
            summary.put("pricingStatus",         full.get("pricingStatus"));
            summary.put("pricingStatusLabel",    full.get("pricingStatusLabel"));
            summary.put("pricingStatusColor",    full.get("pricingStatusColor"));
            summary.put("pricingStatusTextColor",full.get("pricingStatusTextColor"));
            summary.put("deviationPercent",      full.get("deviationPercent"));
            summary.put("deviationLabel",        full.get("deviationLabel"));
            summary.put("recommendation",        full.get("recommendation"));
            summary.put("priceGrowthRate",       full.get("priceGrowthRate"));

            response.status(200);
            return ApiResponse.success(
                    "Price estimate summary for gem " + gemId,
                    summary
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve estimate summary: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/estimate/all — estimates for all gems
    // ---------------------------------------------------------

    /**
     * Returns brief price estimation summaries for all gems.
     * Used on the Price Estimator page to show the overview table
     * with pricing status for every gem in the system.
     *
     * Supports optional query parameter filtering:
     *   ?status=UNDERPRICED   — filter by pricing status
     *   ?status=OVERPRICED
     *   ?status=FAIRLY_PRICED
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with estimate summaries for all gems
     */
    public String getAllEstimates(Request request, Response response) {
        try {
            String statusFilter = request.queryParams("status");

            List<Map<String, Object>> estimates =
                    priceEstimator.estimateAllGems();

            // Apply status filter if provided
            if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                List<Map<String, Object>> filtered = new ArrayList<>();
                for (Map<String, Object> est : estimates) {
                    if (statusFilter.trim().equalsIgnoreCase(
                            (String) est.get("pricingStatus"))) {
                        filtered.add(est);
                    }
                }
                estimates = filtered;
            }

            // Count by status for the filter tabs
            int underpriced  = 0;
            int overpriced   = 0;
            int fairlyPriced = 0;

            for (Map<String, Object> est : estimates) {
                String status = (String) est.get("pricingStatus");
                if ("UNDERPRICED".equals(status))    underpriced++;
                else if ("OVERPRICED".equals(status)) overpriced++;
                else                                  fairlyPriced++;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("estimates",        estimates);
            result.put("totalGems",        estimates.size());
            result.put("underpricedCount", underpriced);
            result.put("overpricedCount",  overpriced);
            result.put("fairlyPricedCount",fairlyPriced);

            response.status(200);
            return ApiResponse.success(
                    "Retrieved estimates for " + estimates.size() + " gem(s)",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve all estimates: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/estimate/overview — market overview statistics
    // ---------------------------------------------------------

    /**
     * Returns portfolio-level market overview statistics.
     * Shows total estimated value, total actual value, counts of
     * underpriced and overpriced gems, and average deviation.
     *
     * Used on the dashboard and the Price Estimator page header
     * to give a high-level view of the gem portfolio valuation.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with market overview statistics
     */
    public String getMarketOverview(Request request, Response response) {
        try {
            Map<String, Object> overview =
                    priceEstimator.getMarketOverview();

            // Add recent estimates list for the overview page
            List<Map<String, Object>> allEstimates =
                    priceEstimator.estimateAllGems();
            overview.put("gems", allEstimates);

            response.status(200);
            return ApiResponse.success(
                    "Market overview retrieved successfully",
                    overview
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve market overview: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/estimate/compare — compare two gem estimates
    // ---------------------------------------------------------

    /**
     * Returns and compares the price estimates for two gems side by side.
     * Query params: ?gem1=BS-123&gem2=RB-456
     *
     * Returns a comparison map showing both estimates with winner
     * indicators on each metric — similar to the gem comparison
     * page but focused on price estimation data.
     *
     * Comparison rows returned:
     *   Actual Price, Estimated Low, Estimated Mid, Estimated High,
     *   Weight, Stage Count, Growth Rate, Deviation %, Status
     *
     * @param request  the incoming HTTP request with gem1 and gem2 params
     * @param response the outgoing HTTP response
     * @return a JSON string with the side by side estimate comparison
     */
    public String compareEstimates(Request request, Response response) {
        try {
            String gem1Id = request.queryParams("gem1");
            String gem2Id = request.queryParams("gem2");

            if (gem1Id == null || gem1Id.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "gem1 query parameter is required. Use ?gem1=ID&gem2=ID"
                ).toJson();
            }
            if (gem2Id == null || gem2Id.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "gem2 query parameter is required. Use ?gem1=ID&gem2=ID"
                ).toJson();
            }
            if (gem1Id.trim().equals(gem2Id.trim())) {
                response.status(400);
                return ApiResponse.badRequest(
                        "gem1 and gem2 must be different gem IDs."
                ).toJson();
            }

            // Validate both gems exist
            GemLinkedList list1 = trackingService.getGemList(gem1Id.trim());
            GemLinkedList list2 = trackingService.getGemList(gem2Id.trim());

            if (list1 == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gem1Id).toJson();
            }
            if (list2 == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gem2Id).toJson();
            }

            // Get full estimates for both gems
            Map<String, Object> est1 =
                    priceEstimator.estimateGemValue(gem1Id.trim());
            Map<String, Object> est2 =
                    priceEstimator.estimateGemValue(gem2Id.trim());

            if (est1.containsKey("error")) {
                response.status(400);
                return ApiResponse.error((String) est1.get("error")).toJson();
            }
            if (est2.containsKey("error")) {
                response.status(400);
                return ApiResponse.error((String) est2.get("error")).toJson();
            }

            // Build comparison rows
            List<Map<String, Object>> comparisonRows = new ArrayList<>();

            addComparisonRow(comparisonRows, "Gem Type",
                    strVal(est1, "gemType"),
                    strVal(est2, "gemType"),
                    false, false);

            addComparisonRow(comparisonRows, "Current Weight (ct)",
                    formatNum(est1, "currentWeight") + " ct",
                    formatNum(est2, "currentWeight") + " ct",
                    dblVal(est1, "currentWeight") > dblVal(est2, "currentWeight"),
                    dblVal(est2, "currentWeight") > dblVal(est1, "currentWeight"));

            addComparisonRow(comparisonRows, "Actual Price (Rs.)",
                    "Rs. " + formatRs(est1, "actualCurrentPrice"),
                    "Rs. " + formatRs(est2, "actualCurrentPrice"),
                    dblVal(est1, "actualCurrentPrice") > dblVal(est2, "actualCurrentPrice"),
                    dblVal(est2, "actualCurrentPrice") > dblVal(est1, "actualCurrentPrice"));

            addComparisonRow(comparisonRows, "Estimated Low (Rs.)",
                    "Rs. " + formatRs(est1, "estimatedLow"),
                    "Rs. " + formatRs(est2, "estimatedLow"),
                    dblVal(est1, "estimatedLow") > dblVal(est2, "estimatedLow"),
                    dblVal(est2, "estimatedLow") > dblVal(est1, "estimatedLow"));

            addComparisonRow(comparisonRows, "Estimated Mid (Rs.)",
                    "Rs. " + formatRs(est1, "estimatedMid"),
                    "Rs. " + formatRs(est2, "estimatedMid"),
                    dblVal(est1, "estimatedMid") > dblVal(est2, "estimatedMid"),
                    dblVal(est2, "estimatedMid") > dblVal(est1, "estimatedMid"));

            addComparisonRow(comparisonRows, "Estimated High (Rs.)",
                    "Rs. " + formatRs(est1, "estimatedHigh"),
                    "Rs. " + formatRs(est2, "estimatedHigh"),
                    dblVal(est1, "estimatedHigh") > dblVal(est2, "estimatedHigh"),
                    dblVal(est2, "estimatedHigh") > dblVal(est1, "estimatedHigh"));

            addComparisonRow(comparisonRows, "Stage Count",
                    strVal(est1, "stageCount") + " stages",
                    strVal(est2, "stageCount") + " stages",
                    dblVal(est1, "stageCount") > dblVal(est2, "stageCount"),
                    dblVal(est2, "stageCount") > dblVal(est1, "stageCount"));

            addComparisonRow(comparisonRows, "Price Growth Rate",
                    formatNum(est1, "priceGrowthRate") + "% / stage",
                    formatNum(est2, "priceGrowthRate") + "% / stage",
                    dblVal(est1, "priceGrowthRate") > dblVal(est2, "priceGrowthRate"),
                    dblVal(est2, "priceGrowthRate") > dblVal(est1, "priceGrowthRate"));

            addComparisonRow(comparisonRows, "Pricing Status",
                    strVal(est1, "pricingStatusLabel"),
                    strVal(est2, "pricingStatusLabel"),
                    false, false);

            addComparisonRow(comparisonRows, "Ceylon Verified",
                    Boolean.TRUE.equals(est1.get("isCeylonVerified")) ? "Yes" : "No",
                    Boolean.TRUE.equals(est2.get("isCeylonVerified")) ? "Yes" : "No",
                    Boolean.TRUE.equals(est1.get("isCeylonVerified")),
                    Boolean.TRUE.equals(est2.get("isCeylonVerified")));

            // Count wins
            int gem1Wins = 0;
            int gem2Wins = 0;
            for (Map<String, Object> row : comparisonRows) {
                if (Boolean.TRUE.equals(row.get("gem1Wins"))) gem1Wins++;
                if (Boolean.TRUE.equals(row.get("gem2Wins"))) gem2Wins++;
            }

            // Build result
            Map<String, Object> result = new HashMap<>();
            result.put("gem1Id",           gem1Id.trim());
            result.put("gem2Id",           gem2Id.trim());
            result.put("gem1Type",         strVal(est1, "gemType"));
            result.put("gem2Type",         strVal(est2, "gemType"));
            result.put("gem1Wins",         gem1Wins);
            result.put("gem2Wins",         gem2Wins);
            result.put("overallWinner",    gem1Wins >= gem2Wins ? gem1Id.trim() : gem2Id.trim());
            result.put("overallWinnerLabel", gem1Wins >= gem2Wins
                    ? strVal(est1, "gemType") + " has higher estimated value"
                    : strVal(est2, "gemType") + " has higher estimated value");
            result.put("comparisonRows",   comparisonRows);
            result.put("estimate1",        buildEstimateSummaryMap(est1));
            result.put("estimate2",        buildEstimateSummaryMap(est2));

            response.status(200);
            return ApiResponse.success(
                    "Estimate comparison completed",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Estimate comparison failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------

    /**
     * Adds a single comparison row to the comparison rows list.
     * Each row represents one metric being compared between two gems.
     *
     * @param rows     the list to add the row to
     * @param metric   the name of the metric being compared
     * @param value1   the value for gem 1
     * @param value2   the value for gem 2
     * @param gem1Wins true if gem 1 wins on this metric
     * @param gem2Wins true if gem 2 wins on this metric
     */
    private void addComparisonRow(List<Map<String, Object>> rows,
                                   String metric,
                                   String value1, String value2,
                                   boolean gem1Wins, boolean gem2Wins) {
        Map<String, Object> row = new HashMap<>();
        row.put("metric",   metric);
        row.put("value1",   value1);
        row.put("value2",   value2);
        row.put("gem1Wins", gem1Wins);
        row.put("gem2Wins", gem2Wins);
        rows.add(row);
    }

    /**
     * Builds a lightweight estimate summary map from a full estimate map.
     * Used in the comparison response to include a summary of each gem.
     *
     * @param full the full estimation result map
     * @return a lightweight summary map
     */
    private Map<String, Object> buildEstimateSummaryMap(
            Map<String, Object> full) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("gemId",               full.get("gemId"));
        summary.put("gemType",             full.get("gemType"));
        summary.put("actualCurrentPrice",  full.get("actualCurrentPrice"));
        summary.put("estimatedMid",        full.get("estimatedMid"));
        summary.put("estimatedLow",        full.get("estimatedLow"));
        summary.put("estimatedHigh",       full.get("estimatedHigh"));
        summary.put("pricingStatus",       full.get("pricingStatus"));
        summary.put("pricingStatusLabel",  full.get("pricingStatusLabel"));
        summary.put("pricingStatusColor",  full.get("pricingStatusColor"));
        summary.put("pricingStatusTextColor", full.get("pricingStatusTextColor"));
        summary.put("deviationPercent",    full.get("deviationPercent"));
        summary.put("deviationLabel",      full.get("deviationLabel"));
        summary.put("isCeylonVerified",    full.get("isCeylonVerified"));
        summary.put("recommendation",      full.get("recommendation"));
        return summary;
    }

    /**
     * Safely extracts a String value from an estimate map.
     *
     * @param map the estimate map
     * @param key the key to extract
     * @return the string value or empty string if not found
     */
    private String strVal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return "—";
        return value.toString();
    }

    /**
     * Safely extracts a double value from an estimate map.
     *
     * @param map the estimate map
     * @param key the key to extract
     * @return the double value or 0.0 if not found
     */
    private double dblVal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Formats a numeric map value to 2 decimal places.
     *
     * @param map the estimate map
     * @param key the key to format
     * @return a formatted number string
     */
    private String formatNum(Map<String, Object> map, String key) {
        return String.format("%.2f", dblVal(map, key));
    }

    /**
     * Formats a rupee price map value with thousand separators.
     *
     * @param map the estimate map
     * @param key the key to format
     * @return a formatted rupee string with commas
     */
    private String formatRs(Map<String, Object> map, String key) {
        long val = Math.round(dblVal(map, key));
        return String.format("%,d", val);
    }
}