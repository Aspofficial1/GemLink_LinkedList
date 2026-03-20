package api.handlers;

import api.ApiResponse;
import database.DBConnection;
import model.GemLinkedList;
import model.GemNode;
import service.OriginVerifier;
import service.TrackingService;

import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VerificationHandler handles all HTTP requests related to gem origin
 * verification, Ceylon authentication, certificate checking, and
 * fraud risk score calculation.
 *
 * This handler exposes the three novel features of the project through
 * the REST API so the React frontend can display verification results,
 * risk scores, and authentication status on the track gem page.
 *
 * Each method validates the request, calls the appropriate service
 * method, and returns a structured ApiResponse JSON string.
 */
public class VerificationHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem linked lists
     * for verification operations.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier handles all Ceylon origin verification logic
     * including location matching, certificate checking, and alert generation.
     */
    private OriginVerifier originVerifier;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new VerificationHandler with the required service dependencies.
     *
     * @param trackingService the service for retrieving gem data
     * @param originVerifier  the service for all verification operations
     */
    public VerificationHandler(TrackingService trackingService,
                                OriginVerifier originVerifier) {
        this.trackingService = trackingService;
        this.originVerifier  = originVerifier;
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/verify — full authentication
    // ---------------------------------------------------------

    /**
     * Runs the full authentication check on a gem.
     * This runs all three checks in sequence:
     * 1. Origin location check against known Ceylon mining areas
     * 2. Certificate presence check
     * 3. Current location consistency check
     *
     * The frontend uses this on the track gem page to show the
     * full authentication result with all three check results.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the full authentication result
     */
    public String runFullAuthentication(Request request, Response response) {
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

            // Run each check individually so we can return granular results
            boolean originOk      = originVerifier.verifyGemOrigin(gemId.trim());
            boolean certificateOk = originVerifier
                    .verifyCertificatePresence(gemId.trim());
            boolean locationOk    = originVerifier
                    .checkCurrentLocationConsistency(gemId.trim());
            boolean allPassed     = originOk && certificateOk && locationOk;

            // Build the detailed authentication result map
            Map<String, Object> authResult = new HashMap<>();
            authResult.put("gemId",            gemId.trim());
            authResult.put("allChecksPassed",  allPassed);
            authResult.put("overallStatus",
                    allPassed ? "VERIFIED GENUINE CEYLON GEM"
                              : "AUTHENTICATION FAILED");
            authResult.put("originCheck",      buildCheckResult(
                    "Origin Location Check", originOk,
                    "Origin matches a known Sri Lankan mining area",
                    "Origin does not match any known Sri Lankan mining area"
            ));
            authResult.put("certificateCheck", buildCheckResult(
                    "Certificate Presence Check", certificateOk,
                    "A valid certificate was found for this gem",
                    "No certificate has been recorded for this gem"
            ));
            authResult.put("locationCheck",    buildCheckResult(
                    "Location Consistency Check", locationOk,
                    "Current location is consistent with the gem records",
                    "Location inconsistency detected in the gem records"
            ));

            // Add gem overview details to the response
            GemNode miningNode = list.getMiningNode();
            if (miningNode != null) {
                authResult.put("gemType",
                        miningNode.getGemType());
                authResult.put("recordedOrigin",
                        miningNode.getLocation());
                authResult.put("miningDate",
                        miningNode.getStageDate().toString());
                authResult.put("miner",
                        miningNode.getPersonName());
            }

            authResult.put("verificationStatusLabel",
                    originVerifier.getVerificationStatusLabel(gemId.trim()));

            response.status(200);
            return ApiResponse.success(
                    allPassed
                        ? "Gem passed all authentication checks"
                        : "Gem failed one or more authentication checks",
                    authResult
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Authentication failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/verify/origin — origin check only
    // ---------------------------------------------------------

    /**
     * Checks only the origin location of a gem against the known
     * list of Sri Lankan mining areas stored in the database.
     *
     * Returns whether the gem passed or failed the origin check
     * along with the matched location name if found.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the origin verification result
     */
    public String verifyOrigin(Request request, Response response) {
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

            boolean passed = originVerifier.verifyGemOrigin(gemId.trim());

            GemNode miningNode = list.getMiningNode();
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",   gemId.trim());
            result.put("passed",  passed);
            result.put("status",
                    passed ? "VERIFIED" : "FAILED");
            result.put("statusMessage",
                    passed
                        ? "Origin confirmed as Sri Lankan mining area"
                        : "Origin does not match any known Sri Lankan mining location");

            if (miningNode != null) {
                result.put("recordedOrigin", miningNode.getLocation());
                result.put("gemType",        miningNode.getGemType());
                result.put("miningDate",     miningNode.getStageDate().toString());
            }

            response.status(200);
            return ApiResponse.success(
                    passed
                        ? "Origin verification passed"
                        : "Origin verification failed",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Origin verification failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/verify/certificate — certificate check only
    // ---------------------------------------------------------

    /**
     * Checks whether a valid certificate has been recorded for the gem.
     * If a certificate is found, the certificate number, issuing authority,
     * and the stage at which it was recorded are included in the response.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the certificate verification result
     */
    public String verifyCertificate(Request request, Response response) {
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

            boolean hasCertificate = originVerifier
                    .verifyCertificatePresence(gemId.trim());

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",          gemId.trim());
            result.put("hasCertificate", hasCertificate);
            result.put("status",
                    hasCertificate ? "CERTIFICATE FOUND" : "NO CERTIFICATE");
            result.put("statusMessage",
                    hasCertificate
                        ? "A valid certificate has been recorded for this gem"
                        : "No certificate has been recorded. "
                          + "Buyers should request certification before purchase.");

            // Find and include the certificate details if present
            if (hasCertificate) {
                List<GemNode> stages = list.getAllStages();
                for (GemNode node : stages) {
                    if (node.getCertificateNumber() != null
                            && !node.getCertificateNumber().trim().isEmpty()) {
                        result.put("certificateNumber",
                                node.getCertificateNumber());
                        result.put("issuingAuthority",
                                node.getIssuingAuthority() != null
                                        ? node.getIssuingAuthority()
                                        : "Not specified");
                        result.put("certifiedAtStage",
                                node.getStage().getLabel());
                        result.put("certifiedAtLocation",
                                node.getLocation());
                        result.put("certificationDate",
                                node.getStageDate().toString());
                        break;
                    }
                }
            }

            response.status(200);
            return ApiResponse.success(
                    hasCertificate
                        ? "Certificate found for gem: " + gemId
                        : "No certificate found for gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Certificate verification failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/verify/all — verify all gems
    // ---------------------------------------------------------

    /**
     * Runs origin verification on every gem registered in the system.
     * Returns a summary of how many passed and failed along with
     * a list of verification results for each gem.
     *
     * The frontend uses this on the alerts page and dashboard to show
     * the overall system verification health.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with the bulk verification summary
     */
    public String verifyAllGems(Request request, Response response) {
        try {
            List<String> allIds = trackingService.getAllGemIds();

            if (allIds.isEmpty()) {
                response.status(200);
                return ApiResponse.success(
                        "No gems found in the system to verify",
                        new HashMap<String, Object>() {{
                            put("totalGems",  0);
                            put("passed",     0);
                            put("failed",     0);
                            put("results",    new ArrayList<>());
                        }}
                ).toJson();
            }

            int passCount = 0;
            int failCount = 0;
            List<Map<String, Object>> results = new ArrayList<>();

            for (String gemId : allIds) {
                boolean passed = originVerifier.verifyGemOrigin(gemId);

                Map<String, Object> gemResult = new HashMap<>();
                gemResult.put("gemId",  gemId);
                gemResult.put("passed", passed);
                gemResult.put("status", passed ? "VERIFIED" : "FAILED");
                gemResult.put("statusLabel",
                        originVerifier.getVerificationStatusLabel(gemId));

                GemLinkedList list = trackingService.getGemList(gemId);
                if (list != null && list.getMiningNode() != null) {
                    gemResult.put("gemType",
                            list.getMiningNode().getGemType());
                    gemResult.put("origin",
                            list.getMiningNode().getLocation());
                }

                results.add(gemResult);

                if (passed) passCount++;
                else        failCount++;
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalGems",       allIds.size());
            summary.put("passed",          passCount);
            summary.put("failed",          failCount);
            summary.put("passRate",
                    allIds.size() > 0
                        ? (passCount * 100.0 / allIds.size())
                        : 0);
            summary.put("results",         results);

            response.status(200);
            return ApiResponse.success(
                    "Bulk verification complete. "
                    + passCount + " passed, "
                    + failCount + " failed.",
                    summary
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Bulk verification failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/verify/locations — get valid Ceylon locations
    // ---------------------------------------------------------

    /**
     * Returns the list of valid Sri Lankan gem mining locations
     * stored in the database.
     *
     * The frontend uses this to populate the location hints on
     * the gem registration form and the origin verification page.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with the valid location lists
     */
    public String getValidLocations(Request request, Response response) {
        try {
            DBConnection db = DBConnection.getInstance();

            List<String> districts = db.getCeylonMiningDistricts();
            List<String> villages  = db.getCeylonMiningVillages();

            Map<String, Object> locations = new HashMap<>();
            locations.put("districts", districts);
            locations.put("villages",  villages);
            locations.put("totalDistricts", districts.size());
            locations.put("totalVillages",  villages.size());
            locations.put("description",
                    "These are the known Sri Lankan gem mining areas "
                    + "used for Ceylon origin verification.");

            response.status(200);
            return ApiResponse.success(
                    "Valid Ceylon mining locations retrieved successfully",
                    locations
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve valid locations: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/risk — fraud risk score
    // ---------------------------------------------------------

    /**
     * Calculates and returns the fraud risk score for a specific gem.
     * This is one of the novel features of the project.
     *
     * The risk score is calculated from 0 to 100 based on:
     * - Origin not verified:        adds 30 points
     * - Missing certificate:        adds 25 points
     * - Unusual price jump:         adds 20 points
     * - Suspicious location:        adds 15 points
     * - Incomplete stage data:      adds 10 points
     *
     * The frontend uses this to display the circular risk gauge
     * on the track gem page.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the fraud risk score and breakdown
     */
    public String getFraudRiskScore(Request request, Response response) {
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

            // Calculate each risk factor independently
            boolean originVerified = originVerifier
                    .verifyGemOrigin(gemId.trim());
            boolean hasCertificate = originVerifier
                    .verifyCertificatePresence(gemId.trim());
            boolean hasUnusualPriceJump = detectUnusualPriceJump(list);
            boolean hasSuspiciousLocation = detectSuspiciousLocation(list);
            boolean hasIncompleteData = detectIncompleteStageData(list);

            // Calculate total risk score
            int score = 0;
            if (!originVerified)       score += 30;
            if (!hasCertificate)       score += 25;
            if (hasUnusualPriceJump)   score += 20;
            if (hasSuspiciousLocation) score += 15;
            if (hasIncompleteData)     score += 10;

            // Clamp score between 0 and 100
            score = Math.max(0, Math.min(100, score));

            // Determine risk level label
            String riskLevel;
            String riskColor;
            if (score <= 30) {
                riskLevel = "LOW RISK";
                riskColor = "#166534";
            } else if (score <= 60) {
                riskLevel = "MEDIUM RISK";
                riskColor = "#92400E";
            } else {
                riskLevel = "HIGH RISK";
                riskColor = "#991B1B";
            }

            // Build detailed risk factor breakdown
            List<Map<String, Object>> riskFactors = new ArrayList<>();
            riskFactors.add(buildRiskFactor(
                    "Origin not verified",
                    30, !originVerified,
                    "The gem's origin location does not match any "
                    + "known Sri Lankan mining area"
            ));
            riskFactors.add(buildRiskFactor(
                    "Missing certificate",
                    25, !hasCertificate,
                    "No official gemological certificate has been "
                    + "recorded for this gem"
            ));
            riskFactors.add(buildRiskFactor(
                    "Unusual price jump detected",
                    20, hasUnusualPriceJump,
                    "The price increased by more than 500 percent "
                    + "between two consecutive stages"
            ));
            riskFactors.add(buildRiskFactor(
                    "Suspicious location",
                    15, hasSuspiciousLocation,
                    "A stage location does not match the expected "
                    + "location for that stage type"
            ));
            riskFactors.add(buildRiskFactor(
                    "Incomplete stage data",
                    10, hasIncompleteData,
                    "One or more stages are missing required "
                    + "contact or identity information"
            ));

            // Build the complete risk score response
            Map<String, Object> riskScore = new HashMap<>();
            riskScore.put("gemId",       gemId.trim());
            riskScore.put("score",       score);
            riskScore.put("riskLevel",   riskLevel);
            riskScore.put("riskColor",   riskColor);
            riskScore.put("riskFactors", riskFactors);
            riskScore.put("recommendation",
                    score <= 30
                        ? "This gem appears to be authentic. "
                          + "Safe to proceed with purchase."
                        : score <= 60
                            ? "Exercise caution. Request additional "
                              + "documentation before purchasing."
                            : "High fraud risk detected. Do not purchase "
                              + "without thorough independent verification.");
            riskScore.put("totalStages", list.getSize());
            riskScore.put("hasOriginVerification", originVerified);
            riskScore.put("hasCertificate",        hasCertificate);

            response.status(200);
            return ApiResponse.success(
                    "Fraud risk score calculated for gem: " + gemId,
                    riskScore
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Risk score calculation failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Risk calculation helpers
    // ---------------------------------------------------------

    /**
     * Detects whether any consecutive stage has an unusually large
     * price increase. A jump of more than 500 percent between two
     * consecutive stages is considered unusual and suspicious.
     *
     * @param list the GemLinkedList to analyse
     * @return true if an unusual price jump is detected
     */
    private boolean detectUnusualPriceJump(GemLinkedList list) {
        List<GemNode> stages = list.getAllStages();
        if (stages.size() < 2) return false;

        for (int i = 1; i < stages.size(); i++) {
            double prevPrice    = stages.get(i - 1).getPriceInRupees();
            double currentPrice = stages.get(i).getPriceInRupees();

            if (prevPrice > 0) {
                double increasePercent =
                        ((currentPrice - prevPrice) / prevPrice) * 100;
                if (increasePercent > 500) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects whether any stage has a suspicious location that does not
     * match the expected location type for that stage.
     * For example, an EXPORTING stage that has a location matching a mine
     * area is suspicious because gems should not be exported from mines.
     *
     * @param list the GemLinkedList to analyse
     * @return true if a suspicious location is detected
     */
    private boolean detectSuspiciousLocation(GemLinkedList list) {
        List<GemNode> stages = list.getAllStages();
        for (GemNode node : stages) {
            // A buying stage that claims to be in a Sri Lankan mine is suspicious
            if (node.getStage() == model.GemStage.BUYING) {
                String location = node.getLocation().toLowerCase();
                if (location.contains("ratnapura")
                        || location.contains("elahera")
                        || location.contains("okanda")) {
                    return true;
                }
            }

            // A mining stage that claims to be at an airport is suspicious
            if (node.getStage() == model.GemStage.MINING) {
                String location = node.getLocation().toLowerCase();
                if (location.contains("airport")
                        || location.contains("dubai")
                        || location.contains("bangkok")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects whether any stage is missing required contact or
     * identity information. Stages without person ID numbers or
     * contact numbers are considered to have incomplete data.
     *
     * @param list the GemLinkedList to analyse
     * @return true if incomplete stage data is detected
     */
    private boolean detectIncompleteStageData(GemLinkedList list) {
        List<GemNode> stages = list.getAllStages();
        for (GemNode node : stages) {
            if (node.getPersonIdNumber() == null
                    || node.getPersonIdNumber().trim().isEmpty()) {
                return true;
            }
            if (node.getContactNumber() == null
                    || node.getContactNumber().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------
    // Response building helpers
    // ---------------------------------------------------------

    /**
     * Builds a structured map representing a single check result.
     * Used when building the full authentication response to give
     * the frontend a consistent structure for each check.
     *
     * @param checkName      the name of the check
     * @param passed         whether the check passed
     * @param passMessage    message shown when the check passes
     * @param failMessage    message shown when the check fails
     * @return a Map representing this check result
     */
    private Map<String, Object> buildCheckResult(String checkName,
                                                   boolean passed,
                                                   String passMessage,
                                                   String failMessage) {
        Map<String, Object> check = new HashMap<>();
        check.put("checkName", checkName);
        check.put("passed",    passed);
        check.put("status",    passed ? "PASSED" : "FAILED");
        check.put("message",   passed ? passMessage : failMessage);
        return check;
    }

    /**
     * Builds a structured map representing a single risk factor.
     * Used when building the fraud risk score response to give
     * the frontend a consistent structure for each risk factor row.
     *
     * @param factorName  the name of the risk factor
     * @param points      the number of risk points this factor adds
     * @param active      whether this factor is currently contributing to the score
     * @param description a description of what this risk factor means
     * @return a Map representing this risk factor
     */
    private Map<String, Object> buildRiskFactor(String factorName,
                                                  int points,
                                                  boolean active,
                                                  String description) {
        Map<String, Object> factor = new HashMap<>();
        factor.put("factorName",  factorName);
        factor.put("points",      points);
        factor.put("active",      active);
        factor.put("description", description);
        factor.put("dotColor",    active ? "#991B1B" : "#166534");
        return factor;
    }
}