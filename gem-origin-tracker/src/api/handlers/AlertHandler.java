package api.handlers;

import api.ApiResponse;
import database.DBConnection;
import service.TrackingService;

import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AlertHandler handles all HTTP requests related to fraud alerts.
 *
 * Fraud alerts are generated automatically by OriginVerifier when a gem
 * fails origin verification, has a missing certificate, or shows a
 * location inconsistency. This handler exposes those alerts through
 * the REST API so the React frontend can display them on the alerts page
 * and the dashboard fraud alert panel.
 *
 * Each method validates the request, calls the appropriate database or
 * service method, and returns a structured ApiResponse JSON string.
 */
public class AlertHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve alert data and statistics.
     * Injected via constructor from ApiRouter.
     */
    private TrackingService trackingService;

    /**
     * The database connection used for direct alert operations
     * such as resolving alerts and fetching alerts by gem ID.
     * Alert operations that are not exposed through TrackingService
     * are handled directly through DBConnection here.
     */
    private DBConnection db;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new AlertHandler with the required service dependency.
     * Also gets the shared DBConnection instance for direct alert queries.
     *
     * @param trackingService the service for alert statistics and retrieval
     */
    public AlertHandler(TrackingService trackingService) {
        this.trackingService = trackingService;
        this.db              = DBConnection.getInstance();
    }

    // ---------------------------------------------------------
    // GET /api/alerts — get all alerts
    // ---------------------------------------------------------

    /**
     * Returns all fraud alerts in the system, both resolved and unresolved.
     * Each alert includes the gem ID, alert type, message, resolution status,
     * and the date it was created.
     *
     * The frontend uses this on the alerts page to show the full alert list
     * with filter options for resolved and unresolved.
     *
     * The response includes both resolved and unresolved alerts so the
     * frontend can filter them client-side using the filter tabs.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with all alerts and a summary count
     */
    public String getAllAlerts(Request request, Response response) {
        try {
            // Get unresolved alerts from TrackingService
            List<String> unresolvedRaw = trackingService.getUnresolvedAlerts();

            // Parse the raw alert strings into structured maps
            List<Map<String, Object>> unresolvedAlerts =
                    parseAlertStrings(unresolvedRaw, false);

            // Get total alert counts for the summary
            int unresolvedCount = trackingService.getUnresolvedAlertCount();
            int totalGems       = trackingService.getTotalGemCount();

            // Build the response with alerts and summary
            Map<String, Object> result = new HashMap<>();
            result.put("unresolved",      unresolvedAlerts);
            result.put("unresolvedCount", unresolvedCount);
            result.put("totalGems",       totalGems);
            result.put("alertRate",
                    totalGems > 0
                        ? (unresolvedCount * 100.0 / totalGems)
                        : 0.0);
            result.put("summary",
                    buildAlertSummary(unresolvedCount));

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + unresolvedCount + " unresolved alert(s)",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve alerts: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/alerts/unresolved — get unresolved alerts only
    // ---------------------------------------------------------

    /**
     * Returns only the unresolved fraud alerts.
     * Used by the dashboard notification bell to show the count
     * and by the alerts page when the Unresolved filter is active.
     *
     * Unresolved alerts are the ones that need immediate attention
     * from administrators or buyers before a transaction proceeds.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with only unresolved alerts
     */
    public String getUnresolvedAlerts(Request request, Response response) {
        try {
            List<String> rawAlerts = trackingService.getUnresolvedAlerts();
            List<Map<String, Object>> alerts =
                    parseAlertStrings(rawAlerts, false);

            Map<String, Object> result = new HashMap<>();
            result.put("alerts",  alerts);
            result.put("count",   alerts.size());
            result.put("message",
                    alerts.isEmpty()
                        ? "No unresolved fraud alerts. All gems are clear."
                        : alerts.size() + " alert(s) require your attention.");

            // Add alert type breakdown for the frontend filter tabs
            result.put("byType", buildAlertTypeBreakdown(alerts));

            response.status(200);
            return ApiResponse.success(
                    alerts.isEmpty()
                        ? "No unresolved alerts found"
                        : "Found " + alerts.size() + " unresolved alert(s)",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve unresolved alerts: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/alerts/gem/:gemId — get alerts for a gem
    // ---------------------------------------------------------

    /**
     * Returns all alerts associated with a specific gem ID.
     * Used on the track gem page to show any alerts for the
     * gem being viewed alongside its journey timeline.
     *
     * If the gem has no alerts an empty list is returned so
     * the frontend can show a clear status indicator.
     *
     * @param request  the incoming HTTP request with :gemId path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with alerts for the specified gem
     */
    public String getAlertsByGem(Request request, Response response) {
        try {
            String gemId = request.params(":gemId");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            // Get all unresolved alerts and filter by gem ID
            // This approach reuses the existing TrackingService method
            // and filters client-side to avoid adding a new DB method
            List<String> allRaw = trackingService.getUnresolvedAlerts();
            List<Map<String, Object>> gemAlerts = new ArrayList<>();

            for (String alertStr : allRaw) {
                if (alertStr.contains("Gem: " + gemId.trim())) {
                    Map<String, Object> alert = parseAlertString(alertStr, false);
                    if (alert != null) {
                        gemAlerts.add(alert);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",  gemId.trim());
            result.put("alerts", gemAlerts);
            result.put("count",  gemAlerts.size());
            result.put("hasAlerts", !gemAlerts.isEmpty());
            result.put("gemStatus",
                    gemAlerts.isEmpty()
                        ? "CLEAR"
                        : "ALERTS PRESENT");
            result.put("gemStatusMessage",
                    gemAlerts.isEmpty()
                        ? "No fraud alerts found for this gem."
                        : gemAlerts.size()
                          + " fraud alert(s) found for this gem. "
                          + "Please review before proceeding.");

            response.status(200);
            return ApiResponse.success(
                    gemAlerts.isEmpty()
                        ? "No alerts found for gem: " + gemId
                        : "Found " + gemAlerts.size()
                          + " alert(s) for gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve alerts for gem: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // PUT /api/alerts/:id/resolve — resolve an alert
    // ---------------------------------------------------------

    /**
     * Marks a specific alert as resolved in the database.
     * Called when an administrator reviews and clears an alert
     * after confirming the gem is authentic or the issue is resolved.
     *
     * The alert ID is the integer primary key from the gem_alerts table.
     * After resolution the alert remains in the database but is marked
     * with is_resolved = 1 so it no longer appears in the unresolved list.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string confirming the alert was resolved
     */
    public String resolveAlert(Request request, Response response) {
        try {
            String alertIdStr = request.params(":id");

            if (alertIdStr == null || alertIdStr.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Alert ID is required."
                ).toJson();
            }

            int alertId;
            try {
                alertId = Integer.parseInt(alertIdStr.trim());
            } catch (NumberFormatException e) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Alert ID must be a valid integer."
                ).toJson();
            }

            if (alertId <= 0) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Alert ID must be a positive integer."
                ).toJson();
            }

            // Call DBConnection directly to resolve the alert
            // TrackingService does not expose a resolveAlert method
            // because alert resolution is a direct database operation
            boolean resolved = db.resolveAlert(alertId);

            if (!resolved) {
                response.status(404);
                return ApiResponse.error(
                        "Alert not found or already resolved. "
                        + "Alert ID: " + alertId,
                        404
                ).toJson();
            }

            // Get updated counts after resolution
            int remainingUnresolved = trackingService.getUnresolvedAlertCount();

            Map<String, Object> result = new HashMap<>();
            result.put("alertId",            alertId);
            result.put("resolved",           true);
            result.put("remainingUnresolved", remainingUnresolved);
            result.put("message",
                    "Alert " + alertId + " has been marked as resolved. "
                    + remainingUnresolved
                    + " unresolved alert(s) remaining.");

            response.status(200);
            return ApiResponse.success(
                    "Alert resolved successfully. Alert ID: " + alertId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to resolve alert: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Response building helpers
    // ---------------------------------------------------------

    /**
     * Parses a list of raw alert strings from TrackingService into
     * a list of structured maps the frontend can work with easily.
     *
     * The raw alert strings from TrackingService are formatted as:
     * "Gem: BS-123 | Type: ORIGIN_MISMATCH | Message: ... | Date: ..."
     *
     * This method splits those strings into proper key-value maps
     * with typed fields for the frontend.
     *
     * @param rawAlerts  the list of raw alert strings
     * @param isResolved whether these alerts are resolved or not
     * @return a list of structured alert maps
     */
    private List<Map<String, Object>> parseAlertStrings(
            List<String> rawAlerts, boolean isResolved) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        int alertId = 1;

        for (String alertStr : rawAlerts) {
            Map<String, Object> alert = parseAlertString(alertStr, isResolved);
            if (alert != null) {
                alert.put("id", alertId++);
                alerts.add(alert);
            }
        }

        return alerts;
    }

    /**
     * Parses a single raw alert string into a structured map.
     * Handles the specific format produced by DBConnection.getUnresolvedAlerts()
     * which is: "Gem: ID | Type: TYPE | Message: MSG | Date: DATE"
     *
     * If the string does not match the expected format it is treated as
     * a plain message and wrapped in a basic map structure.
     *
     * @param alertStr   the raw alert string to parse
     * @param isResolved whether this alert is resolved
     * @return a structured Map for this alert or null if parsing fails
     */
    private Map<String, Object> parseAlertString(
            String alertStr, boolean isResolved) {
        if (alertStr == null || alertStr.trim().isEmpty()) return null;

        Map<String, Object> alert = new HashMap<>();
        alert.put("rawMessage", alertStr);
        alert.put("resolved",   isResolved);

        try {
            // Parse the formatted alert string by splitting on " | "
            String[] parts = alertStr.split(" \\| ");

            for (String part : parts) {
                if (part.startsWith("Gem: ")) {
                    alert.put("gemId",
                            part.substring("Gem: ".length()).trim());
                } else if (part.startsWith("Type: ")) {
                    String type = part.substring("Type: ".length()).trim();
                    alert.put("alertType", type);
                    alert.put("alertTypeLabel",
                            formatAlertTypeLabel(type));
                    alert.put("alertTypeBadgeColor",
                            getAlertTypeBadgeColor(type));
                    alert.put("alertTypeBadgeTextColor",
                            getAlertTypeBadgeTextColor(type));
                } else if (part.startsWith("Message: ")) {
                    alert.put("message",
                            part.substring("Message: ".length()).trim());
                } else if (part.startsWith("Date: ")) {
                    alert.put("date",
                            part.substring("Date: ".length()).trim());
                }
            }

            // Set defaults for missing fields
            if (!alert.containsKey("alertType")) {
                alert.put("alertType",      "UNKNOWN");
                alert.put("alertTypeLabel", "Unknown Alert");
            }
            if (!alert.containsKey("message")) {
                alert.put("message", alertStr);
            }
            if (!alert.containsKey("date")) {
                alert.put("date", "Unknown date");
            }

            // Add severity level based on alert type
            alert.put("severity", getAlertSeverity(
                    (String) alert.get("alertType")));

            // Add actionable recommendation based on alert type
            alert.put("recommendation", getAlertRecommendation(
                    (String) alert.get("alertType")));

        } catch (Exception e) {
            // If parsing fails return a basic structure
            alert.put("alertType",      "UNKNOWN");
            alert.put("alertTypeLabel", "Alert");
            alert.put("message",        alertStr);
            alert.put("date",           "Unknown");
            alert.put("severity",       "MEDIUM");
        }

        return alert;
    }

    /**
     * Builds a summary map of alert counts by type.
     * Used in the dashboard to show the breakdown of alert types.
     *
     * @param unresolvedCount the total count of unresolved alerts
     * @return a Map with summary data
     */
    private Map<String, Object> buildAlertSummary(int unresolvedCount) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUnresolved",    unresolvedCount);
        summary.put("requiresAction",     unresolvedCount > 0);
        summary.put("actionMessage",
                unresolvedCount == 0
                    ? "All gems are verified. No action required."
                    : unresolvedCount + " gem(s) require verification review.");
        return summary;
    }

    /**
     * Builds a breakdown of alerts grouped by their type.
     * Used by the frontend to populate the filter tab counts.
     *
     * @param alerts the list of alert maps to analyse
     * @return a Map of alert type to count
     */
    private Map<String, Object> buildAlertTypeBreakdown(
            List<Map<String, Object>> alerts) {
        Map<String, Object> breakdown = new HashMap<>();
        int originMismatch     = 0;
        int missingCertificate = 0;
        int locationIssue      = 0;
        int other              = 0;

        for (Map<String, Object> alert : alerts) {
            String type = (String) alert.getOrDefault("alertType", "OTHER");
            switch (type) {
                case "ORIGIN_MISMATCH":
                    originMismatch++;
                    break;
                case "MISSING_CERTIFICATE":
                    missingCertificate++;
                    break;
                case "LOCATION_INCONSISTENCY":
                    locationIssue++;
                    break;
                case "MISSING_MINING_STAGE":
                case "EMPTY_LOCATION":
                    locationIssue++;
                    break;
                default:
                    other++;
                    break;
            }
        }

        breakdown.put("ORIGIN_MISMATCH",      originMismatch);
        breakdown.put("MISSING_CERTIFICATE",   missingCertificate);
        breakdown.put("LOCATION_INCONSISTENCY", locationIssue);
        breakdown.put("OTHER",                 other);
        breakdown.put("total",
                originMismatch + missingCertificate + locationIssue + other);

        return breakdown;
    }

    // ---------------------------------------------------------
    // Alert type formatting helpers
    // ---------------------------------------------------------

    /**
     * Converts an alert type code into a human readable label.
     * Used by the frontend for display in alert type badges.
     *
     * @param alertType the alert type code string
     * @return a human readable label string
     */
    private String formatAlertTypeLabel(String alertType) {
        if (alertType == null) return "Unknown Alert";
        switch (alertType.trim().toUpperCase()) {
            case "ORIGIN_MISMATCH":
                return "Origin Mismatch";
            case "MISSING_CERTIFICATE":
                return "Missing Certificate";
            case "LOCATION_INCONSISTENCY":
                return "Location Inconsistency";
            case "MISSING_MINING_STAGE":
                return "Missing Mining Stage";
            case "EMPTY_LOCATION":
                return "Empty Location";
            default:
                return alertType;
        }
    }

    /**
     * Returns the badge background color for an alert type.
     * These colors match the frontend design system exactly.
     *
     * @param alertType the alert type code string
     * @return a hex color string for the badge background
     */
    private String getAlertTypeBadgeColor(String alertType) {
        if (alertType == null) return "#F4F4F4";
        switch (alertType.trim().toUpperCase()) {
            case "ORIGIN_MISMATCH":
                return "#FEE2E2";
            case "MISSING_CERTIFICATE":
                return "#FEF3C7";
            case "LOCATION_INCONSISTENCY":
                return "#EDE9FE";
            case "MISSING_MINING_STAGE":
                return "#FEE2E2";
            case "EMPTY_LOCATION":
                return "#FEF3C7";
            default:
                return "#F4F4F4";
        }
    }

    /**
     * Returns the badge text color for an alert type.
     * These colors match the frontend design system exactly.
     *
     * @param alertType the alert type code string
     * @return a hex color string for the badge text
     */
    private String getAlertTypeBadgeTextColor(String alertType) {
        if (alertType == null) return "#555555";
        switch (alertType.trim().toUpperCase()) {
            case "ORIGIN_MISMATCH":
                return "#991B1B";
            case "MISSING_CERTIFICATE":
                return "#92400E";
            case "LOCATION_INCONSISTENCY":
                return "#5B21B6";
            case "MISSING_MINING_STAGE":
                return "#991B1B";
            case "EMPTY_LOCATION":
                return "#92400E";
            default:
                return "#555555";
        }
    }

    /**
     * Returns the severity level for an alert type.
     * HIGH severity alerts require immediate action before any transaction.
     * MEDIUM severity alerts should be reviewed but are not blocking.
     * LOW severity alerts are informational only.
     *
     * @param alertType the alert type code string
     * @return a severity level string: HIGH, MEDIUM, or LOW
     */
    private String getAlertSeverity(String alertType) {
        if (alertType == null) return "MEDIUM";
        switch (alertType.trim().toUpperCase()) {
            case "ORIGIN_MISMATCH":
                return "HIGH";
            case "MISSING_MINING_STAGE":
                return "HIGH";
            case "MISSING_CERTIFICATE":
                return "MEDIUM";
            case "LOCATION_INCONSISTENCY":
                return "MEDIUM";
            case "EMPTY_LOCATION":
                return "LOW";
            default:
                return "LOW";
        }
    }

    /**
     * Returns an actionable recommendation message for each alert type.
     * The frontend displays this below the alert message to guide the user
     * on what action to take to resolve the alert.
     *
     * @param alertType the alert type code string
     * @return a recommendation string
     */
    private String getAlertRecommendation(String alertType) {
        if (alertType == null)
            return "Review this alert and take appropriate action.";
        switch (alertType.trim().toUpperCase()) {
            case "ORIGIN_MISMATCH":
                return "Do not proceed with this transaction until the origin "
                     + "has been independently verified by the National Gem "
                     + "and Jewellery Authority of Sri Lanka.";
            case "MISSING_CERTIFICATE":
                return "Request an official gemological certificate from the "
                     + "National Gem and Jewellery Authority before proceeding "
                     + "with any purchase or export.";
            case "LOCATION_INCONSISTENCY":
                return "Verify the reported location of the gem with the "
                     + "current owner and check all stage records for "
                     + "inconsistencies before proceeding.";
            case "MISSING_MINING_STAGE":
                return "This gem has no mining stage recorded. Contact the "
                     + "seller to provide proof of mining origin before "
                     + "any transaction.";
            case "EMPTY_LOCATION":
                return "The mining location field is empty. A valid location "
                     + "must be provided before this gem can be verified.";
            default:
                return "Review this alert with the gem owner and resolve "
                     + "any discrepancies before proceeding.";
        }
    }
}