package api.handlers;

import api.ApiResponse;
import database.DBConnection;
import model.GemLinkedList;
import model.GemNode;
import model.GemStage;
import service.OriginVerifier;
import service.TrackingService;

import spark.Request;
import spark.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GemHandler handles all HTTP requests related to gem registration,
 * retrieval, search, and deletion.
 *
 * Each method in this class corresponds to one API endpoint registered
 * in ApiRouter. Every method receives a Spark Request and Response object
 * and returns a JSON string produced by ApiResponse.
 *
 * The handler layer is responsible for:
 * 1. Reading and validating data from the HTTP request
 * 2. Calling the appropriate TrackingService method
 * 3. Building and returning a proper ApiResponse JSON string
 *
 * No business logic lives here — it all stays in TrackingService.
 * This separation keeps the API layer thin and testable.
 */
public class GemHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService handles all gem business logic.
     * Injected via constructor from ApiRouter.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier is used to include verification status
     * in gem response objects so the frontend can show badges.
     */
    private OriginVerifier originVerifier;

    /**
     * The date format expected in all request bodies.
     * The frontend must send dates in this exact format.
     */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new GemHandler with the required service dependencies.
     *
     * @param trackingService the service for all gem operations
     * @param originVerifier  the service for verification status
     */
    public GemHandler(TrackingService trackingService,
                      OriginVerifier originVerifier) {
        this.trackingService = trackingService;
        this.originVerifier  = originVerifier;
    }

    // ---------------------------------------------------------
    // GET /api/gems — get all gems
    // ---------------------------------------------------------

    /**
     * Returns a list of all gem IDs registered in the system
     * along with a summary of each gem.
     *
     * The frontend uses this to populate the dashboard table
     * and the gem selector dropdowns on the compare page.
     *
     * Response data is a list of gem summary maps, each containing
     * the key fields needed for the dashboard without the full
     * stage-by-stage journey detail.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with the list of gem summaries
     */
    public String getAllGems(Request request, Response response) {
        try {
            List<String> allIds = trackingService.getAllGemIds();

            if (allIds.isEmpty()) {
                response.status(200);
                return ApiResponse.success(
                        "No gems registered yet",
                        new ArrayList<>()
                ).toJson();
            }

            // Build a summary map for each gem
            List<Map<String, Object>> gemSummaries = new ArrayList<>();
            for (String gemId : allIds) {
                Map<String, Object> summary = buildGemSummary(gemId);
                if (summary != null) {
                    gemSummaries.add(summary);
                }
            }

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + gemSummaries.size() + " gem(s) successfully",
                    gemSummaries
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve gems: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id — get one gem by ID
    // ---------------------------------------------------------

    /**
     * Returns the full journey of a specific gem including all stage nodes.
     * The frontend uses this to display the timeline on the track page.
     *
     * The response includes the gem overview, all stages in order,
     * weight and price calculations, and the verification status.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the full gem journey data
     */
    public String getGemById(Request request, Response response) {
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

            // Build the full gem detail response
            Map<String, Object> gemDetail = buildGemDetail(list);

            response.status(200);
            return ApiResponse.success(
                    "Gem journey retrieved successfully",
                    gemDetail
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve gem: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/search — search gems
    // ---------------------------------------------------------

    /**
     * Searches for gems by type or origin district.
     * Query parameters: ?type=Blue+Sapphire or ?district=Ratnapura
     *
     * The frontend uses this for the search bar on the track page
     * and the filter options on the dashboard table.
     *
     * @param request  the incoming HTTP request with query parameters
     * @param response the outgoing HTTP response
     * @return a JSON string with matching gem summaries
     */
    public String searchGems(Request request, Response response) {
        try {
            String type     = request.queryParams("type");
            String district = request.queryParams("district");

            if ((type == null || type.trim().isEmpty())
                    && (district == null || district.trim().isEmpty())) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Please provide a search parameter."
                        + " Use ?type=Blue+Sapphire or ?district=Ratnapura"
                ).toJson();
            }

            List<String> matchingIds;

            if (type != null && !type.trim().isEmpty()) {
                matchingIds = trackingService.searchByGemType(type.trim());
            } else {
                matchingIds = trackingService.searchByDistrict(district.trim());
            }

            if (matchingIds.isEmpty()) {
                response.status(200);
                return ApiResponse.success(
                        "No gems found matching your search",
                        new ArrayList<>()
                ).toJson();
            }

            // Build summaries for matching gems
            List<Map<String, Object>> results = new ArrayList<>();
            for (String gemId : matchingIds) {
                Map<String, Object> summary = buildGemSummary(gemId);
                if (summary != null) {
                    results.add(summary);
                }
            }

            response.status(200);
            return ApiResponse.success(
                    "Found " + results.size() + " matching gem(s)",
                    results
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Search failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/ceylon — get verified Ceylon gems
    // ---------------------------------------------------------

    /**
     * Returns only the gems that have been verified as genuine Ceylon gems.
     * The frontend uses this for the Ceylon Verified filter on the dashboard.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with verified Ceylon gem summaries
     */
    public String getCeylonGems(Request request, Response response) {
        try {
            List<String> ceylonIds = trackingService.getCeylonVerifiedGems();

            if (ceylonIds.isEmpty()) {
                response.status(200);
                return ApiResponse.success(
                        "No Ceylon verified gems found",
                        new ArrayList<>()
                ).toJson();
            }

            List<Map<String, Object>> ceylonGems = new ArrayList<>();
            for (String gemId : ceylonIds) {
                Map<String, Object> summary = buildGemSummary(gemId);
                if (summary != null) {
                    ceylonGems.add(summary);
                }
            }

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + ceylonGems.size() + " Ceylon verified gem(s)",
                    ceylonGems
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve Ceylon gems: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // POST /api/gems — register a new gem
    // ---------------------------------------------------------

    /**
     * Registers a new gem in the system using data from the request body.
     * The request body must be a JSON object with these fields:
     *
     * {
     *   "gemType":          "Blue Sapphire",
     *   "colorDescription": "Vivid blue with high saturation",
     *   "originMine":       "Pelmadulla Mine",
     *   "district":         "Ratnapura",
     *   "village":          "Pelmadulla",
     *   "minerName":        "Sumith Perera",
     *   "minerIdNumber":    "199012345678",
     *   "minerContact":     "0771234567",
     *   "weightInCarats":   5.2,
     *   "priceInRupees":    50000,
     *   "miningDate":       "2025-01-15"
     * }
     *
     * All fields except village are required.
     * Returns the new gem ID in the response data.
     *
     * @param request  the incoming HTTP request with JSON body
     * @param response the outgoing HTTP response
     * @return a JSON string with the newly created gem data
     */
    public String registerGem(Request request, Response response) {
        try {
            // Parse the request body JSON into a map
            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body is required. Send gem details as JSON."
                ).toJson();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = ApiResponse.fromJson(body, Map.class);

            // Validate all required fields
            String validationError = validateGemRegistrationData(data);
            if (validationError != null) {
                response.status(400);
                return ApiResponse.badRequest(validationError).toJson();
            }

            // Extract fields from the parsed JSON map
            String gemType          = getString(data, "gemType");
            String colorDescription = getString(data, "colorDescription");
            String originMine       = getString(data, "originMine");
            String district         = getString(data, "district");
            String village          = getStringOrDefault(data, "village", "");
            String minerName        = getString(data, "minerName");
            String minerIdNumber    = getString(data, "minerIdNumber");
            String minerContact     = getString(data, "minerContact");
            double weightInCarats   = getDouble(data, "weightInCarats");
            double priceInRupees    = getDouble(data, "priceInRupees");
            LocalDate miningDate    = parseDate(getString(data, "miningDate"));

            if (miningDate == null) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Invalid mining date format."
                        + " Please use yyyy-MM-dd for example 2025-01-15"
                ).toJson();
            }

            // Call TrackingService to register the gem
            GemLinkedList newList = trackingService.registerNewGem(
                    gemType, colorDescription, originMine, district,
                    village, minerName, minerIdNumber, minerContact,
                    weightInCarats, priceInRupees, miningDate
            );

            if (newList == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "Gem registration failed. Please try again."
                ).toJson();
            }

            // Build the response with the new gem data
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",   newList.getGemId());
            result.put("gemType", gemType);
            result.put("message", "Save this Gem ID for future operations.");
            result.put("stages",  newList.getSize());

            response.status(201);
            return ApiResponse.created(
                    "Gem registered successfully. Gem ID: " + newList.getGemId(),
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Gem registration failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // DELETE /api/gems/:id — delete a gem
    // ---------------------------------------------------------

    /**
     * Deletes a gem and all its stages from the system.
     * This operation is irreversible and removes all database records.
     * The frontend confirms with the user before calling this endpoint.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string confirming deletion or reporting an error
     */
    public String deleteGem(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            // Check the gem exists before trying to delete it
            GemLinkedList list = trackingService.getGemList(gemId.trim());
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            boolean deleted = trackingService.deleteGem(gemId.trim());

            if (!deleted) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to delete gem: " + gemId
                ).toJson();
            }

            response.status(200);
            return ApiResponse.success(
                    "Gem deleted successfully: " + gemId
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Gem deletion failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Response building helpers
    // ---------------------------------------------------------

    /**
     * Builds a summary map for a gem containing key fields only.
     * Used in list responses where full journey detail is not needed.
     * The summary includes the gem ID, type, origin, current stage,
     * weight, price, verification status, and stage count.
     *
     * @param gemId the ID of the gem to build a summary for
     * @return a Map of key-value pairs for the gem summary, or null
     */
    private Map<String, Object> buildGemSummary(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) return null;

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        Map<String, Object> summary = new HashMap<>();
        summary.put("gemId",       gemId);
        summary.put("totalStages", list.getSize());
        summary.put("verified",    originVerifier
                .getVerificationStatusLabel(gemId)
                .contains("VERIFIED CEYLON"));
        summary.put("verificationStatus",
                originVerifier.getVerificationStatusLabel(gemId));
        summary.put("weightLoss",
                list.calculateWeightLoss());
        summary.put("weightLossPercent",
                list.calculateWeightLossPercentage());
        summary.put("priceAppreciation",
                list.calculatePriceAppreciation());

        if (miningNode != null) {
            summary.put("gemType",      miningNode.getGemType());
            summary.put("origin",       miningNode.getLocation());
            summary.put("miningDate",   miningNode.getStageDate().toString());
            summary.put("miner",        miningNode.getPersonName());
            summary.put("originalWeight", miningNode.getWeightInCarats());
            summary.put("miningPrice",  miningNode.getPriceInRupees());
        }

        if (currentNode != null) {
            summary.put("currentStage",    currentNode.getStage().name());
            summary.put("currentStageLabel",
                    currentNode.getStage().getLabel());
            summary.put("currentOwner",    currentNode.getPersonName());
            summary.put("currentLocation", currentNode.getLocation());
            summary.put("currentWeight",   currentNode.getWeightInCarats());
            summary.put("currentPrice",    currentNode.getPriceInRupees());
            summary.put("lastUpdated",     currentNode.getStageDate().toString());
        }

        return summary;
    }

    /**
     * Builds a full detail map for a gem including all stage nodes.
     * Used in single gem responses where the full journey is needed.
     * Includes the gem overview, all stages in order, and calculations.
     *
     * @param list the GemLinkedList to build the detail for
     * @return a Map containing the complete gem journey data
     */
    private Map<String, Object> buildGemDetail(GemLinkedList list) {
        Map<String, Object> detail = new HashMap<>();

        // Add the gem summary fields
        Map<String, Object> summary = buildGemSummary(list.getGemId());
        if (summary != null) {
            detail.putAll(summary);
        }

        // Add the full stage history as an ordered list
        List<GemNode> stages     = list.getAllStages();
        List<Map<String, Object>> stageList = new ArrayList<>();

        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);
            Map<String, Object> stageMap = new HashMap<>();

            stageMap.put("stageNumber",   i + 1);
            stageMap.put("stageType",     node.getStage().name());
            stageMap.put("stageLabel",    node.getStage().getLabel());
            stageMap.put("location",      node.getLocation());
            stageMap.put("personName",    node.getPersonName());
            stageMap.put("date",          node.getStageDate().toString());
            stageMap.put("weightInCarats",node.getWeightInCarats());
            stageMap.put("priceInRupees", node.getPriceInRupees());
            stageMap.put("isCurrent",     i == stages.size() - 1);

            // Add optional fields only if they are set
            if (node.getPersonIdNumber() != null)
                stageMap.put("personIdNumber", node.getPersonIdNumber());
            if (node.getContactNumber() != null)
                stageMap.put("contactNumber", node.getContactNumber());
            if (node.getCertificateNumber() != null)
                stageMap.put("certificateNumber", node.getCertificateNumber());
            if (node.getIssuingAuthority() != null)
                stageMap.put("issuingAuthority", node.getIssuingAuthority());
            if (node.getFlightNumber() != null)
                stageMap.put("flightNumber", node.getFlightNumber());
            if (node.getInvoiceNumber() != null)
                stageMap.put("invoiceNumber", node.getInvoiceNumber());
            if (node.getDestinationCountry() != null)
                stageMap.put("destinationCountry", node.getDestinationCountry());
            if (node.getNotes() != null)
                stageMap.put("notes", node.getNotes());

            // Calculate price increase from previous stage
            if (i > 0) {
                double prevPrice = stages.get(i - 1).getPriceInRupees();
                double increase  = node.getPriceInRupees() - prevPrice;
                stageMap.put("priceIncreaseFromPrevious", increase);
                if (prevPrice > 0) {
                    stageMap.put("priceIncreasePercent",
                            (increase / prevPrice) * 100);
                }
            }

            // Calculate days from previous stage
            if (i > 0) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(
                        stages.get(i - 1).getStageDate(),
                        node.getStageDate()
                );
                stageMap.put("daysFromPreviousStage", days);
            }

            stageList.add(stageMap);
        }

        detail.put("stageHistory", stageList);

        return detail;
    }

    // ---------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------

    /**
     * Validates all required fields for gem registration.
     * Returns an error message string if any field is invalid.
     * Returns null if all fields are valid and registration can proceed.
     *
     * @param data the parsed JSON map from the request body
     * @return an error message string or null if validation passes
     */
    private String validateGemRegistrationData(Map<String, Object> data) {
        if (data == null)
            return "Request body cannot be empty.";
        if (isEmpty(data, "gemType"))
            return "gemType is required.";
        if (isEmpty(data, "colorDescription"))
            return "colorDescription is required.";
        if (isEmpty(data, "originMine"))
            return "originMine is required.";
        if (isEmpty(data, "district"))
            return "district is required.";
        if (isEmpty(data, "minerName"))
            return "minerName is required.";
        if (isEmpty(data, "minerIdNumber"))
            return "minerIdNumber is required.";
        if (isEmpty(data, "minerContact"))
            return "minerContact is required.";
        if (isEmpty(data, "miningDate"))
            return "miningDate is required. Format: yyyy-MM-dd";
        if (!data.containsKey("weightInCarats"))
            return "weightInCarats is required.";
        if (!data.containsKey("priceInRupees"))
            return "priceInRupees is required.";

        double weight = getDouble(data, "weightInCarats");
        if (weight <= 0)
            return "weightInCarats must be greater than zero.";

        double price = getDouble(data, "priceInRupees");
        if (price <= 0)
            return "priceInRupees must be greater than zero.";

        return null;
    }

    // ---------------------------------------------------------
    // Data extraction helpers
    // ---------------------------------------------------------

    /**
     * Safely extracts a String value from a parsed JSON map.
     * Gson parses all JSON values as their raw types so numbers
     * come as Double and strings come as String.
     * This method handles the cast safely.
     *
     * @param data the parsed JSON map
     * @param key  the field name to extract
     * @return the String value or empty string if not found
     */
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return "";
        return value.toString().trim();
    }

    /**
     * Safely extracts a String value with a default fallback.
     *
     * @param data         the parsed JSON map
     * @param key          the field name to extract
     * @param defaultValue the value to return if the key is missing
     * @return the String value or the default if not found
     */
    private String getStringOrDefault(Map<String, Object> data,
                                       String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        String str = value.toString().trim();
        return str.isEmpty() ? defaultValue : str;
    }

    /**
     * Safely extracts a double value from a parsed JSON map.
     * Gson parses JSON numbers as Double by default.
     *
     * @param data the parsed JSON map
     * @param key  the field name to extract
     * @return the double value or 0.0 if not found or invalid
     */
    private double getDouble(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Checks if a field in the map is null, missing, or empty string.
     *
     * @param data the parsed JSON map
     * @param key  the field name to check
     * @return true if the field is missing or empty
     */
    private boolean isEmpty(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return true;
        return value.toString().trim().isEmpty();
    }

    /**
     * Parses a date string in yyyy-MM-dd format into a LocalDate.
     * Returns null if the string is null, empty, or incorrectly formatted.
     *
     * @param dateString the date string to parse
     * @return a LocalDate or null if parsing fails
     */
    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(
                    dateString.trim(),
                    DateTimeFormatter.ofPattern(DATE_FORMAT)
            );
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}