package api.handlers;

import api.ApiResponse;
import service.AuditService;
import service.TrackingService;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AuditHandler handles all HTTP requests related to the audit trail
 * and change history of the gem origin tracking system.
 *
 * Each method corresponds to one API endpoint registered in ApiRouter.
 * Every method reads from the AuditService and returns a JSON string
 * produced by ApiResponse.
 *
 * The audit trail records every change made to any gem or stage
 * in the system — including additions, updates, deletions, and
 * certificate or export detail changes.
 *
 * Endpoints handled:
 *   GET /api/audit                        — all audit logs
 *   GET /api/audit/summary                — counts by action type
 *   GET /api/audit/recent                 — most recent N logs
 *   GET /api/audit/gem/:gemId             — logs for one gem
 *   GET /api/audit/gem/:gemId/:action     — logs for gem filtered by action
 *   GET /api/audit/action/:action         — all logs for an action type
 */
public class AuditHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The AuditService handles all audit log retrieval logic.
     * Injected via constructor from ApiRouter.
     */
    private AuditService auditService;

    /**
     * The TrackingService is used to validate gem IDs before
     * querying audit logs to return a proper 404 if gem not found.
     */
    private TrackingService trackingService;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new AuditHandler with the required service dependencies.
     *
     * @param auditService    the service for audit log operations
     * @param trackingService the service for gem validation
     */
    public AuditHandler(AuditService auditService,
                        TrackingService trackingService) {
        this.auditService    = auditService;
        this.trackingService = trackingService;
    }

    // ---------------------------------------------------------
    // GET /api/audit — get all audit logs
    // ---------------------------------------------------------

    /**
     * Returns all audit log entries across all gems in the system.
     * Results are ordered newest first.
     *
     * Supports optional query parameter filtering:
     *   ?action=STAGE_ADDED     — filter by action type
     *   ?limit=50               — limit number of results
     *
     * The frontend uses this to display the full system audit timeline.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with all audit log entries
     */
    public String getAllAuditLogs(Request request, Response response) {
        try {
            String actionFilter = request.queryParams("action");
            String limitParam   = request.queryParams("limit");

            List<Map<String, Object>> logs;

            if (actionFilter != null && !actionFilter.trim().isEmpty()) {
                // Filter by action type
                logs = auditService.getAuditLogsByAction(actionFilter.trim());
            } else {
                // Get all logs
                logs = auditService.getAllAuditLogs();
            }

            // Apply limit if specified
            if (limitParam != null && !limitParam.trim().isEmpty()) {
                try {
                    int limit = Integer.parseInt(limitParam.trim());
                    if (limit > 0 && limit < logs.size()) {
                        logs = logs.subList(0, limit);
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Enrich logs with formatted labels for the frontend
            List<Map<String, Object>> enriched = enrichLogs(logs);

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + enriched.size() + " audit log entries",
                    enriched
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve audit logs: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/audit/summary — get audit summary counts
    // ---------------------------------------------------------

    /**
     * Returns a summary of audit log counts grouped by action type.
     * Used on the audit dashboard to display stat cards showing
     * how many additions, updates, deletions have occurred.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with action type counts
     */
    public String getAuditSummary(Request request, Response response) {
        try {
            Map<String, Object> summary = auditService.getAuditSummary();

            // Add recent activity for the dashboard feed
            List<Map<String, Object>> recent = auditService.getRecentAuditLogs(5);
            summary.put("recentActivity", enrichLogs(recent));

            response.status(200);
            return ApiResponse.success(
                    "Audit summary retrieved successfully",
                    summary
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve audit summary: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/audit/recent — get most recent audit logs
    // ---------------------------------------------------------

    /**
     * Returns the most recent audit log entries across all gems.
     * Defaults to 20 entries. Supports ?limit=N query parameter.
     * Used on the dashboard for a quick activity feed widget.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with recent audit log entries
     */
    public String getRecentAuditLogs(Request request, Response response) {
        try {
            String limitParam = request.queryParams("limit");
            int limit = 20;

            if (limitParam != null && !limitParam.trim().isEmpty()) {
                try {
                    limit = Integer.parseInt(limitParam.trim());
                    if (limit <= 0 || limit > 200) limit = 20;
                } catch (NumberFormatException ignored) {}
            }

            List<Map<String, Object>> logs = auditService.getRecentAuditLogs(limit);
            List<Map<String, Object>> enriched = enrichLogs(logs);

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + enriched.size() + " recent audit entries",
                    enriched
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve recent audit logs: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/audit/gem/:gemId — get logs for one gem
    // ---------------------------------------------------------

    /**
     * Returns all audit log entries for a specific gem.
     * Results are ordered newest first.
     *
     * Supports optional filtering:
     *   ?action=STAGE_DELETED — filter by action type
     *
     * Used on the audit page to show the change history of one gem.
     *
     * @param request  the incoming HTTP request with :gemId path param
     * @param response the outgoing HTTP response
     * @return a JSON string with the gem's audit log entries
     */
    public String getAuditLogsForGem(Request request, Response response) {
        try {
            String gemId = request.params(":gemId");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            String actionFilter = request.queryParams("action");
            List<Map<String, Object>> logs;

            if (actionFilter != null && !actionFilter.trim().isEmpty()) {
                logs = auditService.getAuditLogsForGemByAction(
                        gemId.trim(), actionFilter.trim());
            } else {
                logs = auditService.getAuditLogsForGem(gemId.trim());
            }

            List<Map<String, Object>> enriched = enrichLogs(logs);

            // Build response with gem summary info
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",       gemId);
            result.put("totalChanges",enriched.size());
            result.put("logs",        enriched);

            // Add change type breakdown for this gem
            result.put("changeBreakdown", buildChangeBreakdown(logs));

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + enriched.size() + " audit entries for gem " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve gem audit logs: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/audit/action/:action — get logs by action type
    // ---------------------------------------------------------

    /**
     * Returns all audit log entries for a specific action type.
     * Valid action types are the ACTION_* constants in AuditService.
     *
     * Example: GET /api/audit/action/STAGE_DELETED
     *          returns all stage deletion events across all gems.
     *
     * Used on the audit page filter tabs.
     *
     * @param request  the incoming HTTP request with :action path param
     * @param response the outgoing HTTP response
     * @return a JSON string with matching audit log entries
     */
    public String getAuditLogsByAction(Request request, Response response) {
        try {
            String action = request.params(":action");

            if (action == null || action.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Action type is required.").toJson();
            }

            // Validate action type
            if (!isValidAction(action.trim())) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Invalid action type: " + action +
                        ". Valid types: STAGE_ADDED, STAGE_UPDATED, STAGE_DELETED, " +
                        "GEM_REGISTERED, GEM_DELETED, CERTIFICATE_ADDED, " +
                        "EXPORT_ADDED, NOTE_ADDED"
                ).toJson();
            }

            List<Map<String, Object>> logs =
                    auditService.getAuditLogsByAction(action.trim());
            List<Map<String, Object>> enriched = enrichLogs(logs);

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + enriched.size() + " entries for action: " + action,
                    enriched
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve audit logs by action: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------

    /**
     * Enriches a list of raw audit log maps with additional display fields.
     * Adds human readable labels, color codes, and icons for the frontend
     * so the UI does not need to perform any string manipulation.
     *
     * Added fields:
     *   actionLabel     — human readable action name
     *   actionColor     — hex color for the action badge
     *   actionIcon      — emoji icon for the action type
     *   isUpdate        — true if this is an update action
     *   isDeletion      — true if this is a deletion action
     *   isAddition      — true if this is an addition action
     *   hasBeforeAfter  — true if both old and new values are present
     *
     * @param logs the raw audit log maps from AuditService
     * @return the enriched list with display fields added
     */
    private List<Map<String, Object>> enrichLogs(
            List<Map<String, Object>> logs) {
        List<Map<String, Object>> enriched = new ArrayList<>();

        for (Map<String, Object> log : logs) {
            Map<String, Object> entry = new HashMap<>(log);
            String action = (String) log.get("action");
            if (action == null) action = "";

            // Add human readable label
            entry.put("actionLabel",    getActionLabel(action));

            // Add color for badge
            entry.put("actionColor",    getActionColor(action));

            // Add text color for badge
            entry.put("actionTextColor", getActionTextColor(action));

            // Add icon
            entry.put("actionIcon",     getActionIcon(action));

            // Add boolean flags for frontend conditional rendering
            entry.put("isUpdate",       action.equals(AuditService.ACTION_STAGE_UPDATED));
            entry.put("isDeletion",     action.equals(AuditService.ACTION_STAGE_DELETED)
                                     || action.equals(AuditService.ACTION_GEM_DELETED));
            entry.put("isAddition",     action.equals(AuditService.ACTION_STAGE_ADDED)
                                     || action.equals(AuditService.ACTION_GEM_REGISTERED));
            entry.put("hasBeforeAfter", log.get("old_value") != null
                                     && log.get("new_value") != null);

            // Format the changed_at timestamp for display
            Object changedAt = log.get("changed_at");
            if (changedAt != null) {
                entry.put("formattedDate", changedAt.toString());
            }

            enriched.add(entry);
        }

        return enriched;
    }

    /**
     * Builds a breakdown count of each action type from a list of logs.
     * Used to show how many adds, updates, and deletes a gem has had.
     *
     * @param logs the raw audit logs to count
     * @return a map of action type to count for this gem
     */
    private Map<String, Object> buildChangeBreakdown(
            List<Map<String, Object>> logs) {
        Map<String, Object> breakdown = new HashMap<>();
        int added = 0, updated = 0, deleted = 0, other = 0;

        for (Map<String, Object> log : logs) {
            String action = (String) log.get("action");
            if (action == null) continue;
            switch (action) {
                case AuditService.ACTION_STAGE_ADDED:
                case AuditService.ACTION_GEM_REGISTERED:
                    added++; break;
                case AuditService.ACTION_STAGE_UPDATED:
                    updated++; break;
                case AuditService.ACTION_STAGE_DELETED:
                case AuditService.ACTION_GEM_DELETED:
                    deleted++; break;
                default:
                    other++; break;
            }
        }

        breakdown.put("additions", added);
        breakdown.put("updates",   updated);
        breakdown.put("deletions", deleted);
        breakdown.put("other",     other);
        return breakdown;
    }

    /**
     * Returns a human readable label for an action type constant.
     *
     * @param action the action type constant string
     * @return a human readable label string
     */
    private String getActionLabel(String action) {
        switch (action) {
            case AuditService.ACTION_STAGE_ADDED:       return "Stage Added";
            case AuditService.ACTION_STAGE_UPDATED:     return "Stage Updated";
            case AuditService.ACTION_STAGE_DELETED:     return "Stage Deleted";
            case AuditService.ACTION_GEM_REGISTERED:    return "Gem Registered";
            case AuditService.ACTION_GEM_DELETED:       return "Gem Deleted";
            case AuditService.ACTION_CERTIFICATE_ADDED: return "Certificate Added";
            case AuditService.ACTION_EXPORT_ADDED:      return "Export Details Added";
            case AuditService.ACTION_NOTE_ADDED:        return "Note Added";
            default:                                     return action;
        }
    }

    /**
     * Returns a background hex color for an action type badge.
     * Green tones for additions, amber for updates, red for deletions.
     *
     * @param action the action type constant string
     * @return a hex color string
     */
    private String getActionColor(String action) {
        switch (action) {
            case AuditService.ACTION_STAGE_ADDED:
            case AuditService.ACTION_GEM_REGISTERED:    return "#DCFCE7";
            case AuditService.ACTION_STAGE_UPDATED:     return "#FEF9C3";
            case AuditService.ACTION_STAGE_DELETED:
            case AuditService.ACTION_GEM_DELETED:       return "#FEE2E2";
            case AuditService.ACTION_CERTIFICATE_ADDED: return "#DBEAFE";
            case AuditService.ACTION_EXPORT_ADDED:      return "#EDE9FE";
            case AuditService.ACTION_NOTE_ADDED:        return "#F3F4F6";
            default:                                     return "#F3F4F6";
        }
    }

    /**
     * Returns a text hex color for an action type badge.
     * Matches the background color tone.
     *
     * @param action the action type constant string
     * @return a hex color string for the text
     */
    private String getActionTextColor(String action) {
        switch (action) {
            case AuditService.ACTION_STAGE_ADDED:
            case AuditService.ACTION_GEM_REGISTERED:    return "#166534";
            case AuditService.ACTION_STAGE_UPDATED:     return "#92400E";
            case AuditService.ACTION_STAGE_DELETED:
            case AuditService.ACTION_GEM_DELETED:       return "#991B1B";
            case AuditService.ACTION_CERTIFICATE_ADDED: return "#1E40AF";
            case AuditService.ACTION_EXPORT_ADDED:      return "#5B21B6";
            case AuditService.ACTION_NOTE_ADDED:        return "#374151";
            default:                                     return "#374151";
        }
    }

    /**
     * Returns a text icon for an action type.
     * Used in the audit timeline as a visual indicator.
     *
     * @param action the action type constant string
     * @return a text icon string
     */
    private String getActionIcon(String action) {
        switch (action) {
            case AuditService.ACTION_STAGE_ADDED:       return "+";
            case AuditService.ACTION_STAGE_UPDATED:     return "~";
            case AuditService.ACTION_STAGE_DELETED:     return "-";
            case AuditService.ACTION_GEM_REGISTERED:    return "*";
            case AuditService.ACTION_GEM_DELETED:       return "X";
            case AuditService.ACTION_CERTIFICATE_ADDED: return "C";
            case AuditService.ACTION_EXPORT_ADDED:      return "E";
            case AuditService.ACTION_NOTE_ADDED:        return "N";
            default:                                     return "?";
        }
    }

    /**
     * Validates that an action type string is one of the known constants.
     * Used to reject requests with invalid action type parameters.
     *
     * @param action the action type string to validate
     * @return true if the action is a known valid type
     */
    private boolean isValidAction(String action) {
        switch (action) {
            case AuditService.ACTION_STAGE_ADDED:
            case AuditService.ACTION_STAGE_UPDATED:
            case AuditService.ACTION_STAGE_DELETED:
            case AuditService.ACTION_GEM_REGISTERED:
            case AuditService.ACTION_GEM_DELETED:
            case AuditService.ACTION_CERTIFICATE_ADDED:
            case AuditService.ACTION_EXPORT_ADDED:
            case AuditService.ACTION_NOTE_ADDED:
                return true;
            default:
                return false;
        }
    }
}