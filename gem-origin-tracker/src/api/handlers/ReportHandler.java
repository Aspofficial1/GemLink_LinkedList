package api.handlers;

import api.ApiResponse;
import model.GemLinkedList;
import model.GemNode;
import report.ReportGenerator;
import service.PriceTracker;
import service.TrackingService;

import spark.Request;
import spark.Response;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReportHandler handles all HTTP requests related to report generation
 * and retrieval of saved report files.
 *
 * This handler exposes the ReportGenerator functionality through the
 * REST API so the React frontend can trigger report generation and
 * list all saved reports on the reports page.
 *
 * Reports are saved as plain text files in the reports folder on the
 * server. The handler returns the file path and content of generated
 * reports so the frontend can display them or offer a download link.
 *
 * Each method validates the request, calls the appropriate service
 * method, and returns a structured ApiResponse JSON string.
 */
public class ReportHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem data for reports.
     * Injected via constructor from ApiRouter.
     */
    private TrackingService trackingService;

    /**
     * The ReportGenerator handles all report file generation.
     * Injected via constructor from ApiRouter.
     */
    private ReportGenerator reportGenerator;

    /**
     * The PriceTracker used to include price analysis in reports.
     * Created internally since it depends on TrackingService.
     */
    private PriceTracker priceTracker;

    /**
     * The folder where all generated report files are saved.
     * Must match the folder defined in ReportGenerator.
     */
    private static final String REPORT_FOLDER = "reports";

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new ReportHandler with the required service dependencies.
     *
     * @param trackingService the service for gem data retrieval
     * @param reportGenerator the service for report file generation
     * @param priceTracker    the service for price analysis data
     */
    public ReportHandler(TrackingService trackingService,
                         ReportGenerator reportGenerator,
                         PriceTracker    priceTracker) {
        this.trackingService = trackingService;
        this.reportGenerator = reportGenerator;
        this.priceTracker    = priceTracker;
    }

    // ---------------------------------------------------------
    // POST /api/gems/:id/report/full — generate full journey report
    // ---------------------------------------------------------

    /**
     * Generates a full journey report for a specific gem and saves
     * it as a text file in the reports folder.
     *
     * The full report includes every stage node in the linked list
     * with all recorded details, weight analysis, price appreciation,
     * and origin verification status.
     *
     * The response includes the file path, the report content as a
     * string, and a download URL the frontend can use.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the report file details and content
     */
    public String generateFullReport(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            String cleanGemId = gemId.trim();

            // Check the gem exists before generating the report
            GemLinkedList list = trackingService.getGemList(cleanGemId);
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", cleanGemId).toJson();
            }

            // Generate the full journey report file
            String savedPath = reportGenerator
                    .generateFullJourneyReport(cleanGemId);

            if (savedPath == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to generate full report for gem: " + cleanGemId
                ).toJson();
            }

            // Read the report file content to include in the response
            String reportContent = readReportFile(savedPath);

            // Get file details
            File reportFile = new File(savedPath);
            String fileName = reportFile.getName();

            // Build the response with all report details
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",       cleanGemId);
            result.put("reportType",  "FULL_JOURNEY");
            result.put("filePath",    savedPath);
            result.put("fileName",    fileName);
            result.put("downloadUrl",
                    "/api/reports/download/" + fileName);
            result.put("content",     reportContent);
            result.put("contentLines",
                    reportContent != null
                        ? reportContent.split("\n").length : 0);

            if (reportFile.exists()) {
                result.put("fileSizeBytes", reportFile.length());
                result.put("fileSizeKB",
                        String.format("%.1f KB",
                                reportFile.length() / 1024.0));
            }

            // Add gem summary for quick reference
            GemNode miningNode = list.getMiningNode();
            if (miningNode != null) {
                result.put("gemType",   miningNode.getGemType());
                result.put("origin",    miningNode.getLocation());
                result.put("totalStages", list.getSize());
            }

            response.status(201);
            return ApiResponse.created(
                    "Full journey report generated successfully for gem: "
                    + cleanGemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Report generation failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // POST /api/gems/:id/report/summary — generate summary report
    // ---------------------------------------------------------

    /**
     * Generates a summary report for a specific gem and saves it
     * as a text file in the reports folder.
     *
     * The summary report is shorter than the full report and shows
     * only the key statistics, verification status, and highlights
     * without the full stage-by-stage journey detail.
     *
     * Useful for quick reference documents that need to be shared
     * with buyers or exporters without the full technical detail.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the summary report details and content
     */
    public String generateSummaryReport(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            String cleanGemId = gemId.trim();

            // Check the gem exists
            GemLinkedList list = trackingService.getGemList(cleanGemId);
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", cleanGemId).toJson();
            }

            // Generate the summary report file
            String savedPath = reportGenerator
                    .generateSummaryReport(cleanGemId);

            if (savedPath == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to generate summary report for gem: "
                        + cleanGemId
                ).toJson();
            }

            // Read the report file content
            String reportContent = readReportFile(savedPath);

            File reportFile = new File(savedPath);
            String fileName = reportFile.getName();

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",       cleanGemId);
            result.put("reportType",  "SUMMARY");
            result.put("filePath",    savedPath);
            result.put("fileName",    fileName);
            result.put("downloadUrl",
                    "/api/reports/download/" + fileName);
            result.put("content",     reportContent);
            result.put("contentLines",
                    reportContent != null
                        ? reportContent.split("\n").length : 0);

            if (reportFile.exists()) {
                result.put("fileSizeBytes", reportFile.length());
                result.put("fileSizeKB",
                        String.format("%.1f KB",
                                reportFile.length() / 1024.0));
            }

            GemNode miningNode = list.getMiningNode();
            if (miningNode != null) {
                result.put("gemType",    miningNode.getGemType());
                result.put("origin",     miningNode.getLocation());
                result.put("totalStages", list.getSize());
            }

            response.status(201);
            return ApiResponse.created(
                    "Summary report generated successfully for gem: "
                    + cleanGemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Summary report generation failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // POST /api/report/all — generate full system report
    // ---------------------------------------------------------

    /**
     * Generates a full system report covering all gems registered
     * in the system and saves it as a text file.
     *
     * The system report includes a summary of every gem with its
     * key metrics and verification status, followed by overall
     * system statistics for the entire gem database.
     *
     * This is used by administrators for auditing and record keeping.
     * The report file can be large if many gems are registered.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with the system report file details
     */
    public String generateAllGemsReport(Request request, Response response) {
        try {
            List<String> allIds = trackingService.getAllGemIds();

            if (allIds.isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "No gems registered in the system. "
                        + "Register at least one gem before generating a system report."
                ).toJson();
            }

            // Generate the full system report file
            String savedPath = reportGenerator.generateAllGemsReport();

            if (savedPath == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to generate system report."
                ).toJson();
            }

            // Read a preview of the report content
            // Full system reports can be very large so we only
            // include the first 100 lines in the response
            String fullContent   = readReportFile(savedPath);
            String previewContent = getReportPreview(fullContent, 100);

            File reportFile = new File(savedPath);
            String fileName = reportFile.getName();

            Map<String, Object> result = new HashMap<>();
            result.put("reportType",    "FULL_SYSTEM");
            result.put("filePath",      savedPath);
            result.put("fileName",      fileName);
            result.put("downloadUrl",
                    "/api/reports/download/" + fileName);
            result.put("totalGemsIncluded", allIds.size());
            result.put("contentPreview",    previewContent);
            result.put("totalLines",
                    fullContent != null
                        ? fullContent.split("\n").length : 0);
            result.put("note",
                    "This is a preview of the first 100 lines. "
                    + "Use the download URL to get the full report.");

            if (reportFile.exists()) {
                result.put("fileSizeBytes", reportFile.length());
                result.put("fileSizeKB",
                        String.format("%.1f KB",
                                reportFile.length() / 1024.0));
            }

            // Add system stats summary
            result.put("systemStats", buildSystemStatsSummary(allIds));

            response.status(201);
            return ApiResponse.created(
                    "System report generated successfully. "
                    + allIds.size() + " gem(s) included.",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "System report generation failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/reports — list all saved reports
    // ---------------------------------------------------------

    /**
     * Returns a list of all report files currently saved in the
     * reports folder on the server.
     *
     * Each report in the response includes the file name, size,
     * last modified date, report type inferred from the name,
     * and a download URL.
     *
     * The frontend uses this to populate the reports page table
     * where users can see and download previously generated reports.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with the list of saved report files
     */
    public String listSavedReports(Request request, Response response) {
        try {
            File reportsFolder = new File(REPORT_FOLDER);

            // If the reports folder does not exist yet return empty list
            if (!reportsFolder.exists() || !reportsFolder.isDirectory()) {
                Map<String, Object> result = new HashMap<>();
                result.put("reports", new ArrayList<>());
                result.put("totalReports", 0);
                result.put("message",
                        "No reports have been generated yet. "
                        + "Generate a report from the Track Gem page.");

                response.status(200);
                return ApiResponse.success(
                        "No reports found",
                        result
                ).toJson();
            }

            // Get all .txt files in the reports folder
            File[] reportFiles = reportsFolder.listFiles(
                    (dir, name) -> name.endsWith(".txt"));

            if (reportFiles == null || reportFiles.length == 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("reports",      new ArrayList<>());
                result.put("totalReports", 0);
                result.put("message",
                        "No report files found in the reports folder.");

                response.status(200);
                return ApiResponse.success(
                        "No reports found",
                        result
                ).toJson();
            }

            // Build a list of report details for each file
            List<Map<String, Object>> reportList = new ArrayList<>();

            for (File file : reportFiles) {
                Map<String, Object> reportInfo = new HashMap<>();
                reportInfo.put("fileName",     file.getName());
                reportInfo.put("filePath",     file.getPath());
                reportInfo.put("fileSizeBytes", file.length());
                reportInfo.put("fileSizeKB",
                        String.format("%.1f KB", file.length() / 1024.0));
                reportInfo.put("lastModified",
                        new java.util.Date(file.lastModified()).toString());
                reportInfo.put("downloadUrl",
                        "/api/reports/download/" + file.getName());

                // Infer report type from the file name
                String reportType = inferReportType(file.getName());
                reportInfo.put("reportType",      reportType);
                reportInfo.put("reportTypeLabel", formatReportTypeLabel(reportType));

                // Extract gem ID from the file name if present
                String gemId = extractGemIdFromFileName(file.getName());
                if (gemId != null) {
                    reportInfo.put("gemId", gemId);
                }

                reportList.add(reportInfo);
            }

            // Sort by last modified date, most recent first
            reportList.sort((a, b) -> {
                String dateA = (String) a.get("lastModified");
                String dateB = (String) b.get("lastModified");
                return dateB.compareTo(dateA);
            });

            Map<String, Object> result = new HashMap<>();
            result.put("reports",      reportList);
            result.put("totalReports", reportList.size());
            result.put("reportFolder",
                    reportsFolder.getAbsolutePath());

            response.status(200);
            return ApiResponse.success(
                    "Found " + reportList.size() + " saved report(s)",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to list reports: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------

    /**
     * Reads the content of a report file and returns it as a string.
     * Returns null if the file does not exist or cannot be read.
     * Used to include report content directly in the API response
     * so the frontend can display it without a separate download.
     *
     * @param filePath the full path to the report file
     * @return the file content as a string or null if unreadable
     */
    private String readReportFile(String filePath) {
        if (filePath == null) return null;

        File file = new File(filePath);
        if (!file.exists()) return null;

        try (java.io.BufferedReader reader =
                new java.io.BufferedReader(new java.io.FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (Exception e) {
            System.out.println("Failed to read report file: " + filePath);
            return null;
        }
    }

    /**
     * Returns the first N lines of a report as a preview string.
     * Used for large system reports where returning the full content
     * in the API response would be too large.
     *
     * @param content   the full report content string
     * @param maxLines  the maximum number of lines to include
     * @return the first maxLines lines of the content
     */
    private String getReportPreview(String content, int maxLines) {
        if (content == null) return "";
        String[] lines = content.split("\n");
        StringBuilder preview = new StringBuilder();
        int limit = Math.min(maxLines, lines.length);
        for (int i = 0; i < limit; i++) {
            preview.append(lines[i]).append("\n");
        }
        if (lines.length > maxLines) {
            preview.append("\n... (")
                    .append(lines.length - maxLines)
                    .append(" more lines) ...");
        }
        return preview.toString();
    }

    /**
     * Infers the report type from the file name.
     * Report files are named with patterns like:
     * BS-123_FullReport_2025-01-15_10-30-00.txt
     * BS-123_SummaryReport_2025-01-15_10-30-00.txt
     * SystemReport_2025-01-15_10-30-00.txt
     *
     * @param fileName the report file name
     * @return a report type string
     */
    private String inferReportType(String fileName) {
        if (fileName == null) return "UNKNOWN";
        if (fileName.contains("FullReport"))    return "FULL_JOURNEY";
        if (fileName.contains("SummaryReport")) return "SUMMARY";
        if (fileName.contains("SystemReport"))  return "FULL_SYSTEM";
        return "UNKNOWN";
    }

    /**
     * Converts a report type code into a human readable label.
     *
     * @param reportType the report type code string
     * @return a human readable label
     */
    private String formatReportTypeLabel(String reportType) {
        switch (reportType) {
            case "FULL_JOURNEY": return "Full Journey Report";
            case "SUMMARY":      return "Summary Report";
            case "FULL_SYSTEM":  return "Full System Report";
            default:             return "Report";
        }
    }

    /**
     * Extracts the gem ID from a report file name.
     * File names follow the pattern GemID_ReportType_Timestamp.txt
     * This method returns the first part before the first underscore.
     * Returns null if the file name does not contain a gem ID.
     *
     * @param fileName the report file name
     * @return the gem ID string or null if not found
     */
    private String extractGemIdFromFileName(String fileName) {
        if (fileName == null) return null;

        // SystemReport files do not have a gem ID prefix
        if (fileName.startsWith("SystemReport")) return null;

        // Gem ID is the part before the first underscore followed by a dash
        // For example: BS-1773990209789_FullReport_...
        int underscoreIndex = fileName.indexOf('_');
        if (underscoreIndex > 0) {
            String candidate = fileName.substring(0, underscoreIndex);
            // Check that it looks like a gem ID (contains a dash)
            if (candidate.contains("-")) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Builds a quick summary of system statistics for inclusion
     * in the system report response.
     *
     * @param allIds the list of all gem IDs in the system
     * @return a Map of key system statistics
     */
    private Map<String, Object> buildSystemStatsSummary(List<String> allIds) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGems",       trackingService.getTotalGemCount());
        stats.put("ceylonVerified",  trackingService.getCeylonGemCount());
        stats.put("unresolvedAlerts",
                trackingService.getUnresolvedAlertCount());

        int totalStages = 0;
        for (String gemId : allIds) {
            GemLinkedList list = trackingService.getGemList(gemId);
            if (list != null) {
                totalStages += list.getSize();
            }
        }
        stats.put("totalStages", totalStages);
        stats.put("avgStagesPerGem",
                allIds.size() > 0
                    ? String.format("%.1f", totalStages * 1.0 / allIds.size())
                    : "0.0");

        return stats;
    }
}