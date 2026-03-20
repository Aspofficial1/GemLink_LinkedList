package api.handlers;

import api.ApiResponse;
import model.GemLinkedList;
import model.GemNode;
import service.OriginVerifier;
import service.PriceTracker;
import service.TrackingService;

import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StatsHandler handles all HTTP requests related to statistics,
 * dashboard data, price analysis, weight analysis, and gem comparison.
 *
 * This handler feeds the React frontend dashboard with all the numbers
 * and chart data it needs. Every stat card, chart, and comparison table
 * on the frontend gets its data from the endpoints handled here.
 *
 * Each method validates the request, calls the appropriate service
 * method, and returns a structured ApiResponse JSON string.
 */
public class StatsHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem data and statistics.
     * Injected via constructor from ApiRouter.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier used to include verification status
     * in statistics responses.
     */
    private OriginVerifier originVerifier;

    /**
     * The PriceTracker used for price appreciation analysis.
     * Created internally since ApiRouter does not inject it.
     */
    private PriceTracker priceTracker;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new StatsHandler with the required service dependencies.
     *
     * @param trackingService the service for gem data and statistics
     * @param originVerifier  the service for verification status
     */
    public StatsHandler(TrackingService trackingService,
                        OriginVerifier originVerifier) {
        this.trackingService = trackingService;
        this.originVerifier  = originVerifier;
        this.priceTracker    = new PriceTracker(trackingService);
    }

    // ---------------------------------------------------------
    // GET /api/stats — all system statistics
    // ---------------------------------------------------------

    /**
     * Returns all system statistics in a single response.
     * This is the primary endpoint for the dashboard overview page.
     *
     * The response includes:
     * - Total gem count
     * - Ceylon verified gem count
     * - Unresolved alert count
     * - Total stages recorded
     * - Average stages per gem
     * - Total value tracked in rupees
     * - Ceylon verification rate as a percentage
     * - Top gem types by count
     * - Top origin districts by count
     *
     * The frontend uses this to populate all four stat cards and
     * any summary sections on the dashboard page.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with all system statistics
     */
    public String getAllStats(Request request, Response response) {
        try {
            int totalGems      = trackingService.getTotalGemCount();
            int ceylonGems     = trackingService.getCeylonGemCount();
            int alertCount     = trackingService.getUnresolvedAlertCount();
            int nonCeylonGems  = totalGems - ceylonGems;
            List<String> allIds = trackingService.getAllGemIds();

            // Calculate total stages across all gems
            int totalStages = 0;
            double totalValue = 0;
            Map<String, Integer> gemTypeCounts    = new HashMap<>();
            Map<String, Integer> districtCounts   = new HashMap<>();

            for (String gemId : allIds) {
                GemLinkedList list = trackingService.getGemList(gemId);
                if (list != null) {
                    totalStages += list.getSize();

                    // Add current price to total value
                    GemNode currentNode = list.getCurrentStageNode();
                    if (currentNode != null) {
                        totalValue += currentNode.getPriceInRupees();
                    }

                    // Count gem types
                    GemNode miningNode = list.getMiningNode();
                    if (miningNode != null) {
                        String gemType = miningNode.getGemType();
                        gemTypeCounts.put(gemType,
                                gemTypeCounts.getOrDefault(gemType, 0) + 1);

                        // Extract district from location string
                        String location = miningNode.getLocation();
                        String district = extractDistrict(location);
                        if (district != null) {
                            districtCounts.put(district,
                                    districtCounts.getOrDefault(district, 0) + 1);
                        }
                    }
                }
            }

            // Calculate rates and averages
            double ceylonRate    = totalGems > 0
                    ? (ceylonGems * 100.0 / totalGems) : 0;
            double avgStagesPerGem = totalGems > 0
                    ? (totalStages * 1.0 / totalGems) : 0;

            // Build the complete stats response map
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalGems",        totalGems);
            stats.put("ceylonVerified",   ceylonGems);
            stats.put("notVerified",      nonCeylonGems);
            stats.put("unresolvedAlerts", alertCount);
            stats.put("totalStages",      totalStages);
            stats.put("totalValueRupees", totalValue);
            stats.put("ceylonRate",
                    String.format("%.1f%%", ceylonRate));
            stats.put("ceylonRateValue",  ceylonRate);
            stats.put("avgStagesPerGem",
                    String.format("%.1f", avgStagesPerGem));
            stats.put("avgStagesValue",   avgStagesPerGem);
            stats.put("gemTypeCounts",    sortMapByValue(gemTypeCounts));
            stats.put("districtCounts",   sortMapByValue(districtCounts));
            stats.put("topGemType",
                    getTopKey(gemTypeCounts));
            stats.put("topDistrict",
                    getTopKey(districtCounts));

            // Add trend indicators for the dashboard stat cards
            stats.put("trends", buildTrendIndicators(
                    totalGems, ceylonGems, alertCount, totalStages));

            response.status(200);
            return ApiResponse.success(
                    "System statistics retrieved successfully",
                    stats
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve statistics: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/stats/summary — dashboard summary
    // ---------------------------------------------------------

    /**
     * Returns a brief summary of key statistics for the dashboard header cards.
     * This is a lighter version of getAllStats that returns only the four
     * numbers needed for the stat cards at the top of the dashboard.
     *
     * The frontend calls this more frequently than getAllStats because
     * it is cheaper to compute and the dashboard header always needs
     * up-to-date counts.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with the four key dashboard metrics
     */
    public String getDashboardSummary(Request request, Response response) {
        try {
            int totalGems   = trackingService.getTotalGemCount();
            int ceylonGems  = trackingService.getCeylonGemCount();
            int alertCount  = trackingService.getUnresolvedAlertCount();

            // Calculate total stages quickly
            int totalStages = 0;
            List<String> allIds = trackingService.getAllGemIds();
            for (String gemId : allIds) {
                GemLinkedList list = trackingService.getGemList(gemId);
                if (list != null) totalStages += list.getSize();
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalGems",         totalGems);
            summary.put("ceylonVerified",     ceylonGems);
            summary.put("unresolvedAlerts",   alertCount);
            summary.put("totalStages",        totalStages);
            summary.put("ceylonRate",
                    totalGems > 0
                        ? String.format("%.1f%%", ceylonGems * 100.0 / totalGems)
                        : "0.0%");
            summary.put("avgStages",
                    totalGems > 0
                        ? String.format("%.1f", totalStages * 1.0 / totalGems)
                        : "0.0");

            // Trend texts for the stat card bottom rows
            summary.put("gemsTrend",    "Total registered gems");
            summary.put("ceylonTrend",
                    totalGems > 0
                        ? String.format("%.1f%% verification rate",
                                ceylonGems * 100.0 / totalGems)
                        : "No gems yet");
            summary.put("alertsTrend",
                    alertCount == 0
                        ? "All clear"
                        : alertCount + " need review");
            summary.put("stagesTrend",
                    String.format("avg %.1f per gem",
                            totalGems > 0
                                ? totalStages * 1.0 / totalGems : 0));

            response.status(200);
            return ApiResponse.success(
                    "Dashboard summary retrieved successfully",
                    summary
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve dashboard summary: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/price — price history for a gem
    // ---------------------------------------------------------

    /**
     * Returns the price history for a specific gem at each stage.
     * Used by the Recharts line chart on the dashboard and the
     * price appreciation report on the track gem page.
     *
     * The response includes price at each stage, price increase
     * from the previous stage, percentage increase, and a total
     * appreciation summary.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the price history data
     */
    public String getPriceHistory(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            GemLinkedList list = trackingService.getGemList(gemId.trim());
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            // Get price records from PriceTracker
            List<PriceTracker.PriceRecord> records =
                    priceTracker.getPriceHistory(gemId.trim());

            if (records.isEmpty()) {
                response.status(200);
                return ApiResponse.success(
                        "No price data found for gem: " + gemId,
                        new ArrayList<>()
                ).toJson();
            }

            // Convert PriceRecord objects to maps for JSON serialization
            List<Map<String, Object>> priceData = new ArrayList<>();
            for (PriceTracker.PriceRecord record : records) {
                Map<String, Object> point = new HashMap<>();
                point.put("stageNumber",      record.getStageNumber());
                point.put("stageName",        record.getStageName());
                point.put("location",         record.getLocation());
                point.put("personName",       record.getPersonName());
                point.put("date",             record.getDate());
                point.put("price",            record.getPrice());
                point.put("priceInThousands", record.getPrice() / 1000.0);
                point.put("priceIncrease",    record.getPriceIncrease());
                point.put("percentIncrease",  record.getPercentIncrease());

                // Short stage label for chart X axis
                point.put("stageShortLabel",
                        getStageShorLabel(record.getStageName()));

                priceData.add(point);
            }

            // Build highest value stage info
            GemNode highestNode =
                    priceTracker.getHighestValueAddedStage(gemId.trim());

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",               gemId.trim());
            result.put("priceData",           priceData);
            result.put("totalAppreciation",
                    priceTracker.getTotalPriceAppreciation(gemId.trim()));
            result.put("appreciationPercent",
                    priceTracker.getTotalAppreciationPercentage(gemId.trim()));
            result.put("miningPrice",
                    list.getMiningNode() != null
                        ? list.getMiningNode().getPriceInRupees() : 0);
            result.put("currentPrice",
                    list.getCurrentStageNode() != null
                        ? list.getCurrentStageNode().getPriceInRupees() : 0);

            if (highestNode != null) {
                result.put("highestValueStage",
                        highestNode.getStage().getLabel());
                result.put("highestValueLocation",
                        highestNode.getLocation());
            }

            // Add chart-ready data with gem type for the legend
            GemNode miningNode = list.getMiningNode();
            if (miningNode != null) {
                result.put("gemType", miningNode.getGemType());
            }

            response.status(200);
            return ApiResponse.success(
                    "Price history retrieved for gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve price history: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/weight — weight analysis for a gem
    // ---------------------------------------------------------

    /**
     * Returns the weight analysis for a specific gem.
     * Shows the original weight at mining, current weight after cutting,
     * total weight lost in carats, and weight loss as a percentage.
     *
     * Also returns a per-stage weight breakdown so the frontend can
     * show exactly at which stage the weight was lost.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the weight analysis data
     */
    public String getWeightAnalysis(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            GemLinkedList list = trackingService.getGemList(gemId.trim());
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            GemNode miningNode  = list.getMiningNode();
            GemNode currentNode = list.getCurrentStageNode();
            List<GemNode> stages = list.getAllStages();

            // Build per-stage weight breakdown
            List<Map<String, Object>> weightByStage = new ArrayList<>();
            for (int i = 0; i < stages.size(); i++) {
                GemNode node = stages.get(i);
                Map<String, Object> stageWeight = new HashMap<>();
                stageWeight.put("stageNumber",   i + 1);
                stageWeight.put("stageName",     node.getStage().getLabel());
                stageWeight.put("stageType",     node.getStage().name());
                stageWeight.put("location",      node.getLocation());
                stageWeight.put("date",          node.getStageDate().toString());
                stageWeight.put("weight",        node.getWeightInCarats());

                // Calculate loss from previous stage
                if (i > 0) {
                    double prevWeight   = stages.get(i - 1).getWeightInCarats();
                    double stageLoss    = prevWeight - node.getWeightInCarats();
                    double stageLossPct = prevWeight > 0
                            ? (stageLoss / prevWeight) * 100 : 0;
                    stageWeight.put("lossFromPrevious",        stageLoss);
                    stageWeight.put("lossPercentFromPrevious", stageLossPct);
                } else {
                    stageWeight.put("lossFromPrevious",        0.0);
                    stageWeight.put("lossPercentFromPrevious", 0.0);
                }

                weightByStage.add(stageWeight);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",         gemId.trim());
            result.put("weightByStage", weightByStage);
            result.put("totalWeightLoss",
                    list.calculateWeightLoss());
            result.put("weightLossPercent",
                    list.calculateWeightLossPercentage());
            result.put("weightLossFormatted",
                    String.format("%.4f carats (%.2f%%)",
                            list.calculateWeightLoss(),
                            list.calculateWeightLossPercentage()));

            if (miningNode != null) {
                result.put("originalWeight",
                        miningNode.getWeightInCarats());
                result.put("gemType",
                        miningNode.getGemType());
            }

            if (currentNode != null) {
                result.put("currentWeight",
                        currentNode.getWeightInCarats());
                result.put("currentStage",
                        currentNode.getStage().getLabel());
            }

            response.status(200);
            return ApiResponse.success(
                    "Weight analysis retrieved for gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve weight analysis: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/compare — compare two gems
    // ---------------------------------------------------------

    /**
     * Returns a side-by-side comparison of two gems.
     * Query parameters: ?gem1=BS-123&gem2=RB-456
     *
     * The response includes all comparison metrics with a winner
     * indicator for each metric showing which gem has the better value.
     * The frontend uses this to populate the comparison table and
     * the grouped bar chart on the compare gems page.
     *
     * @param request  the incoming HTTP request with gem1 and gem2 query params
     * @param response the outgoing HTTP response
     * @return a JSON string with the full gem comparison data
     */
    public String compareGems(Request request, Response response) {
        try {
            String gemId1 = request.queryParams("gem1");
            String gemId2 = request.queryParams("gem2");

            if (gemId1 == null || gemId1.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "gem1 query parameter is required. "
                        + "Example: ?gem1=BS-123&gem2=RB-456"
                ).toJson();
            }

            if (gemId2 == null || gemId2.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "gem2 query parameter is required. "
                        + "Example: ?gem1=BS-123&gem2=RB-456"
                ).toJson();
            }

            GemLinkedList list1 = trackingService.getGemList(gemId1.trim());
            GemLinkedList list2 = trackingService.getGemList(gemId2.trim());

            if (list1 == null) {
                response.status(404);
                return ApiResponse.notFound("Gem 1", gemId1).toJson();
            }

            if (list2 == null) {
                response.status(404);
                return ApiResponse.notFound("Gem 2", gemId2).toJson();
            }

            GemNode mine1    = list1.getMiningNode();
            GemNode mine2    = list2.getMiningNode();
            GemNode current1 = list1.getCurrentStageNode();
            GemNode current2 = list2.getCurrentStageNode();

            // Build comparison rows with winner indicators
            List<Map<String, Object>> comparisonRows = new ArrayList<>();

            comparisonRows.add(buildComparisonRow(
                    "Gem Type",
                    mine1 != null ? mine1.getGemType() : "N/A",
                    mine2 != null ? mine2.getGemType() : "N/A",
                    false, false
            ));

            comparisonRows.add(buildComparisonRow(
                    "Origin",
                    mine1 != null ? mine1.getLocation() : "N/A",
                    mine2 != null ? mine2.getLocation() : "N/A",
                    false, false
            ));

            double origWeight1 = mine1 != null ? mine1.getWeightInCarats() : 0;
            double origWeight2 = mine2 != null ? mine2.getWeightInCarats() : 0;
            comparisonRows.add(buildComparisonRow(
                    "Original Weight (ct)",
                    String.format("%.4f ct", origWeight1),
                    String.format("%.4f ct", origWeight2),
                    true,
                    origWeight1 > origWeight2
            ));

            double currWeight1 = current1 != null ? current1.getWeightInCarats() : 0;
            double currWeight2 = current2 != null ? current2.getWeightInCarats() : 0;
            comparisonRows.add(buildComparisonRow(
                    "Current Weight (ct)",
                    String.format("%.4f ct", currWeight1),
                    String.format("%.4f ct", currWeight2),
                    true,
                    currWeight1 > currWeight2
            ));

            double lossP1 = list1.calculateWeightLossPercentage();
            double lossP2 = list2.calculateWeightLossPercentage();
            comparisonRows.add(buildComparisonRow(
                    "Weight Loss %",
                    String.format("%.2f%%", lossP1),
                    String.format("%.2f%%", lossP2),
                    true,
                    lossP1 < lossP2
            ));

            double minePrice1 = mine1 != null ? mine1.getPriceInRupees() : 0;
            double minePrice2 = mine2 != null ? mine2.getPriceInRupees() : 0;
            comparisonRows.add(buildComparisonRow(
                    "Mining Price (Rs.)",
                    String.format("Rs. %,.0f", minePrice1),
                    String.format("Rs. %,.0f", minePrice2),
                    false, false
            ));

            double currPrice1 = current1 != null ? current1.getPriceInRupees() : 0;
            double currPrice2 = current2 != null ? current2.getPriceInRupees() : 0;
            comparisonRows.add(buildComparisonRow(
                    "Current Price (Rs.)",
                    String.format("Rs. %,.0f", currPrice1),
                    String.format("Rs. %,.0f", currPrice2),
                    true,
                    currPrice1 > currPrice2
            ));

            double appreciation1 = list1.calculatePriceAppreciation();
            double appreciation2 = list2.calculatePriceAppreciation();
            comparisonRows.add(buildComparisonRow(
                    "Total Appreciation (Rs.)",
                    String.format("Rs. %,.0f", appreciation1),
                    String.format("Rs. %,.0f", appreciation2),
                    true,
                    appreciation1 > appreciation2
            ));

            double appPct1 = minePrice1 > 0
                    ? (appreciation1 / minePrice1) * 100 : 0;
            double appPct2 = minePrice2 > 0
                    ? (appreciation2 / minePrice2) * 100 : 0;
            comparisonRows.add(buildComparisonRow(
                    "Appreciation %",
                    String.format("%.1f%%", appPct1),
                    String.format("%.1f%%", appPct2),
                    true,
                    appPct1 > appPct2
            ));

            comparisonRows.add(buildComparisonRow(
                    "Number of Stages",
                    String.valueOf(list1.getSize()),
                    String.valueOf(list2.getSize()),
                    true,
                    list1.getSize() > list2.getSize()
            ));

            boolean hasCert1 = originVerifier.verifyCertificatePresence(gemId1.trim());
            boolean hasCert2 = originVerifier.verifyCertificatePresence(gemId2.trim());
            comparisonRows.add(buildComparisonRow(
                    "Has Certificate",
                    hasCert1 ? "Yes" : "No",
                    hasCert2 ? "Yes" : "No",
                    true,
                    hasCert1 && !hasCert2
            ));

            boolean verified1 = originVerifier.quickLocationCheck(
                    mine1 != null ? mine1.getLocation() : "");
            boolean verified2 = originVerifier.quickLocationCheck(
                    mine2 != null ? mine2.getLocation() : "");
            comparisonRows.add(buildComparisonRow(
                    "Verification Status",
                    verified1 ? "VERIFIED" : "UNVERIFIED",
                    verified2 ? "VERIFIED" : "UNVERIFIED",
                    true,
                    verified1 && !verified2
            ));

            // Build price chart data for both gems
            List<Map<String, Object>> chartData =
                    buildComparisonChartData(list1, list2, gemId1, gemId2);

            // Determine overall winner
            int gem1Wins = 0;
            int gem2Wins = 0;
            for (Map<String, Object> row : comparisonRows) {
                Boolean isComparable = (Boolean) row.get("isComparable");
                if (isComparable != null && isComparable) {
                    Boolean gem1Wins_row = (Boolean) row.get("gem1Wins");
                    Boolean gem2Wins_row = (Boolean) row.get("gem2Wins");
                    if (gem1Wins_row != null && gem1Wins_row) gem1Wins++;
                    if (gem2Wins_row != null && gem2Wins_row) gem2Wins++;
                }
            }

            // Build the full comparison response
            Map<String, Object> result = new HashMap<>();
            result.put("gemId1",         gemId1.trim());
            result.put("gemId2",         gemId2.trim());
            result.put("gem1Type",
                    mine1 != null ? mine1.getGemType() : "Unknown");
            result.put("gem2Type",
                    mine2 != null ? mine2.getGemType() : "Unknown");
            result.put("comparisonRows", comparisonRows);
            result.put("chartData",      chartData);
            result.put("gem1Wins",       gem1Wins);
            result.put("gem2Wins",       gem2Wins);
            result.put("overallWinner",
                    gem1Wins > gem2Wins ? gemId1.trim()
                    : gem2Wins > gem1Wins ? gemId2.trim()
                    : "TIE");
            result.put("overallWinnerLabel",
                    gem1Wins > gem2Wins
                        ? (mine1 != null ? mine1.getGemType() : gemId1)
                            + " wins overall"
                    : gem2Wins > gem1Wins
                        ? (mine2 != null ? mine2.getGemType() : gemId2)
                            + " wins overall"
                    : "Both gems are evenly matched");

            response.status(200);
            return ApiResponse.success(
                    "Gem comparison completed successfully",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Gem comparison failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Response building helpers
    // ---------------------------------------------------------

    /**
     * Builds a single comparison row map for the comparison table.
     * Each row has the metric name, two values to compare, and
     * winner indicators for both gems.
     *
     * @param metric       the name of the metric being compared
     * @param value1       the value for gem 1
     * @param value2       the value for gem 2
     * @param isComparable whether this row has a meaningful winner
     * @param gem1Wins     whether gem 1 wins this metric
     * @return a Map representing this comparison row
     */
    private Map<String, Object> buildComparisonRow(String metric,
                                                    String value1,
                                                    String value2,
                                                    boolean isComparable,
                                                    boolean gem1Wins) {
        Map<String, Object> row = new HashMap<>();
        row.put("metric",       metric);
        row.put("value1",       value1);
        row.put("value2",       value2);
        row.put("isComparable", isComparable);

        if (isComparable) {
            boolean gem2Wins = !gem1Wins
                    && !value1.equals(value2);
            row.put("gem1Wins", gem1Wins);
            row.put("gem2Wins", gem2Wins);
            row.put("isTie",    !gem1Wins && !gem2Wins);
        } else {
            row.put("gem1Wins", false);
            row.put("gem2Wins", false);
            row.put("isTie",    false);
        }

        return row;
    }

    /**
     * Builds the chart data for the comparison bar chart.
     * Returns an array of data points where each point represents
     * a stage name and the price of both gems at that stage.
     *
     * Stage names are aligned so both gems are shown at the same
     * X axis position even if they have different numbers of stages.
     *
     * @param list1   the linked list for gem 1
     * @param list2   the linked list for gem 2
     * @param gemId1  the ID of gem 1 for labeling
     * @param gemId2  the ID of gem 2 for labeling
     * @return a List of chart data point maps
     */
    private List<Map<String, Object>> buildComparisonChartData(
            GemLinkedList list1, GemLinkedList list2,
            String gemId1, String gemId2) {

        List<GemNode> stages1 = list1.getAllStages();
        List<GemNode> stages2 = list2.getAllStages();

        // Use the longer list length to cover all stages
        int maxStages = Math.max(stages1.size(), stages2.size());

        List<Map<String, Object>> chartData = new ArrayList<>();

        for (int i = 0; i < maxStages; i++) {
            Map<String, Object> point = new HashMap<>();

            // Use stage label from whichever gem has a stage at this index
            String stageLabel;
            if (i < stages1.size()) {
                stageLabel = getStageShorLabel(
                        stages1.get(i).getStage().getLabel());
            } else {
                stageLabel = getStageShorLabel(
                        stages2.get(i).getStage().getLabel());
            }

            point.put("stage", stageLabel);
            point.put("gem1",
                    i < stages1.size()
                        ? stages1.get(i).getPriceInRupees() / 1000.0
                        : null);
            point.put("gem2",
                    i < stages2.size()
                        ? stages2.get(i).getPriceInRupees() / 1000.0
                        : null);

            chartData.add(point);
        }

        return chartData;
    }

    /**
     * Builds trend indicator maps for the dashboard stat cards.
     * Each trend shows a label and a positive or negative direction
     * to be displayed below the number in the stat card.
     *
     * @param totalGems   total gem count
     * @param ceylonGems  Ceylon verified count
     * @param alertCount  unresolved alert count
     * @param totalStages total stages recorded
     * @return a Map of trend data for each stat card
     */
    private Map<String, Object> buildTrendIndicators(int totalGems,
                                                       int ceylonGems,
                                                       int alertCount,
                                                       int totalStages) {
        Map<String, Object> trends = new HashMap<>();

        trends.put("gemsTrend",    "Total registered gems");
        trends.put("gemsPositive", true);

        double ceylonRate = totalGems > 0
                ? (ceylonGems * 100.0 / totalGems) : 0;
        trends.put("ceylonTrend",
                String.format("%.1f%% verification rate", ceylonRate));
        trends.put("ceylonPositive", ceylonRate >= 80);

        trends.put("alertsTrend",
                alertCount == 0 ? "All gems clear" : alertCount + " need review");
        trends.put("alertsPositive", alertCount == 0);

        trends.put("stagesTrend",
                String.format("avg %.1f per gem",
                        totalGems > 0 ? totalStages * 1.0 / totalGems : 0));
        trends.put("stagesPositive", true);

        return trends;
    }

    // ---------------------------------------------------------
    // Utility helpers
    // ---------------------------------------------------------

    /**
     * Extracts the district name from a location string.
     * Location strings are typically formatted as "Village, District"
     * so this method takes the last part after the comma.
     *
     * @param location the full location string
     * @return the district name or the full string if no comma found
     */
    private String extractDistrict(String location) {
        if (location == null || location.trim().isEmpty()) return null;
        String[] parts = location.split(",");
        if (parts.length >= 2) {
            return parts[parts.length - 1].trim();
        }
        return location.trim();
    }

    /**
     * Returns a short label for a stage name suitable for chart X axis.
     * Long stage names like "Cutting and Polishing Stage" are shortened
     * to "Cut" so they fit on chart axis labels without overlapping.
     *
     * @param stageName the full stage name
     * @return a short label string
     */
    private String getStageShorLabel(String stageName) {
        if (stageName == null) return "Unknown";
        String lower = stageName.toLowerCase();
        if (lower.contains("mining"))    return "Mine";
        if (lower.contains("cutting"))   return "Cut";
        if (lower.contains("trading"))   return "Trade";
        if (lower.contains("exporting")) return "Export";
        if (lower.contains("buying"))    return "Buy";
        return stageName.length() > 8
                ? stageName.substring(0, 8) : stageName;
    }

    /**
     * Sorts a Map by its integer values in descending order.
     * Used to return gem type and district counts from highest to lowest
     * so the frontend can display the most common types first.
     *
     * @param map the Map to sort by value
     * @return a new List of maps each with key and count fields in sorted order
     */
    private List<Map<String, Object>> sortMapByValue(
            Map<String, Integer> map) {
        List<Map<String, Object>> result = new ArrayList<>();

        map.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name",  entry.getKey());
                    item.put("count", entry.getValue());
                    result.add(item);
                });

        return result;
    }

    /**
     * Returns the key with the highest value from a count map.
     * Used to find the most common gem type or origin district.
     *
     * @param map the Map of name to count
     * @return the key with the highest count or "N/A" if the map is empty
     */
    private String getTopKey(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return "N/A";
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");
    }
}