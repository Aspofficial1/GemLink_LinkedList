package api.handlers;

import api.ApiResponse;
import model.GemLinkedList;
import model.GemNode;
import model.GemStage;
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
 * StageHandler handles all HTTP requests related to adding, retrieving,
 * and removing stages from a gem's doubly linked list journey.
 *
 * Each method corresponds to one API endpoint registered in ApiRouter.
 * The handler validates the request data, calls TrackingService, and
 * returns a structured ApiResponse JSON string.
 *
 * The stage endpoints are the most frequently used endpoints in the system
 * because every time a gem changes hands a new stage must be added to
 * its linked list chain.
 */
public class StageHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService handles all stage business logic.
     * Injected via constructor from ApiRouter.
     */
    private TrackingService trackingService;

    /**
     * The date format expected in all request bodies.
     * The frontend must send dates in this exact format.
     */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new StageHandler with the required service dependency.
     *
     * @param trackingService the service for all stage operations
     */
    public StageHandler(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/stages — get all stages
    // ---------------------------------------------------------

    /**
     * Returns all stages for a specific gem as an ordered list.
     * Each stage in the response represents one node in the doubly
     * linked list, ordered from head (mining) to tail (current owner).
     *
     * The frontend uses this to build the journey timeline on the
     * track gem page and the export documentation page.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the ordered list of stage objects
     */
    public String getAllStages(Request request, Response response) {
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

            List<GemNode> stages     = list.getAllStages();
            List<Map<String, Object>> stageList = new ArrayList<>();

            for (int i = 0; i < stages.size(); i++) {
                GemNode node = stages.get(i);
                stageList.add(buildStageMap(node, i, stages.size()));
            }

            // Build response with stage list and journey summary
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",        gemId.trim());
            result.put("totalStages",  list.getSize());
            result.put("stages",       stageList);
            result.put("weightLoss",
                    list.calculateWeightLoss());
            result.put("weightLossPercent",
                    list.calculateWeightLossPercentage());
            result.put("priceAppreciation",
                    list.calculatePriceAppreciation());

            // Include head and tail node info for quick access
            GemNode head = list.getMiningNode();
            GemNode tail = list.getCurrentStageNode();

            if (head != null) {
                result.put("originLocation", head.getLocation());
                result.put("miningDate",     head.getStageDate().toString());
                result.put("originalWeight", head.getWeightInCarats());
                result.put("miningPrice",    head.getPriceInRupees());
            }

            if (tail != null) {
                result.put("currentStage",    tail.getStage().name());
                result.put("currentStageLabel", tail.getStage().getLabel());
                result.put("currentOwner",    tail.getPersonName());
                result.put("currentLocation", tail.getLocation());
                result.put("currentWeight",   tail.getWeightInCarats());
                result.put("currentPrice",    tail.getPriceInRupees());
            }

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + stages.size() + " stage(s) for gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve stages: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // POST /api/gems/:id/stages — add a new stage
    // ---------------------------------------------------------

    /**
     * Adds a new stage node to the end of the gem's linked list.
     * This is called every time a gem moves to the next stage of
     * its journey — cutting, trading, exporting, or buying.
     *
     * The request body must be a JSON object with these fields:
     *
     * {
     *   "stageType":      "CUTTING",
     *   "location":       "Beruwala, Gem Street",
     *   "personName":     "Mohammed Cassim",
     *   "personIdNumber": "198756781234",
     *   "contactNumber":  "0712345678",
     *   "weightInCarats": 4.8,
     *   "priceInRupees":  150000,
     *   "stageDate":      "2025-01-20"
     * }
     *
     * Optional fields for export stages:
     * {
     *   "flightNumber":       "EK-653",
     *   "invoiceNumber":      "INV-2025-001",
     *   "destinationCountry": "Dubai"
     * }
     *
     * Optional certificate fields:
     * {
     *   "certificateNumber": "GIC-2025-001",
     *   "issuingAuthority":  "National Gem and Jewellery Authority"
     * }
     *
     * @param request  the incoming HTTP request with JSON body
     * @param response the outgoing HTTP response
     * @return a JSON string with the newly added stage data
     */
    public String addStage(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            // Check the gem exists before trying to add a stage
            GemLinkedList existingList =
                    trackingService.getGemList(gemId.trim());
            if (existingList == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            // Parse the request body
            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body is required. Send stage details as JSON."
                ).toJson();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = ApiResponse.fromJson(body, Map.class);

            // Validate required fields
            String validationError = validateStageData(data);
            if (validationError != null) {
                response.status(400);
                return ApiResponse.badRequest(validationError).toJson();
            }

            // Extract and parse all stage fields from the request
            String stageTypeStr    = getString(data, "stageType");
            String location        = getString(data, "location");
            String personName      = getString(data, "personName");
            String personIdNumber  = getString(data, "personIdNumber");
            String contactNumber   = getString(data, "contactNumber");
            double weightInCarats  = getDouble(data, "weightInCarats");
            double priceInRupees   = getDouble(data, "priceInRupees");
            LocalDate stageDate    = parseDate(getString(data, "stageDate"));

            if (stageDate == null) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Invalid stage date format. Use yyyy-MM-dd"
                ).toJson();
            }

            // Parse the stage type enum from the string
            GemStage stageType;
            try {
                stageType = GemStage.valueOf(stageTypeStr.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Invalid stage type: " + stageTypeStr
                        + ". Valid values are: MINING, CUTTING, TRADING,"
                        + " EXPORTING, BUYING"
                ).toJson();
            }

            // Call TrackingService to add the stage node
            GemNode newNode = trackingService.addStageToGem(
                    gemId.trim(), stageType, location, personName,
                    personIdNumber, contactNumber,
                    weightInCarats, priceInRupees, stageDate
            );

            if (newNode == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to add stage to gem: " + gemId
                ).toJson();
            }

            // Add export details if this is an EXPORTING stage
            String flightNumber       = getString(data, "flightNumber");
            String invoiceNumber      = getString(data, "invoiceNumber");
            String destinationCountry = getString(data, "destinationCountry");

            if (stageType == GemStage.EXPORTING
                    && !flightNumber.isEmpty()) {
                trackingService.addExportDetails(
                        gemId.trim(),
                        flightNumber,
                        invoiceNumber,
                        destinationCountry
                );
                newNode.setFlightNumber(flightNumber);
                newNode.setInvoiceNumber(invoiceNumber);
                newNode.setDestinationCountry(destinationCountry);
            }

            // Add certificate details if provided
            String certificateNumber = getString(data, "certificateNumber");
            String issuingAuthority  = getString(data, "issuingAuthority");

            if (!certificateNumber.isEmpty()) {
                trackingService.addCertificateDetails(
                        gemId.trim(),
                        certificateNumber,
                        issuingAuthority
                );
                newNode.setCertificateNumber(certificateNumber);
                newNode.setIssuingAuthority(issuingAuthority);
            }

            // Add notes if provided
            String notes = getString(data, "notes");
            if (!notes.isEmpty()) {
                trackingService.addNoteToCurrentStage(gemId.trim(), notes);
                newNode.setNotes(notes);
            }

            // Get the updated list to return current state
            GemLinkedList updatedList =
                    trackingService.getGemList(gemId.trim());

            // Build response with the new stage and updated journey info
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",        gemId.trim());
            result.put("newStage",
                    buildStageMap(newNode,
                            updatedList != null ? updatedList.getSize() - 1 : 0,
                            updatedList != null ? updatedList.getSize() : 1));
            result.put("totalStages",
                    updatedList != null ? updatedList.getSize() : 1);
            result.put("currentStage", stageType.name());
            result.put("currentStageLabel", stageType.getLabel());

            if (updatedList != null) {
                result.put("weightLoss",
                        updatedList.calculateWeightLoss());
                result.put("priceAppreciation",
                        updatedList.calculatePriceAppreciation());
            }

            response.status(201);
            return ApiResponse.created(
                    "Stage added successfully: "
                    + stageType.getLabel()
                    + " for Gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to add stage: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // DELETE /api/gems/:id/stages/:position — remove a stage
    // ---------------------------------------------------------

    /**
     * Removes a stage node at a specific position from the gem's linked list.
     * Position is 0-based so position 0 is the head (mining) node.
     *
     * This is used by authorised users to correct wrongly entered stages.
     * After deletion the remaining nodes are re-saved with corrected
     * stage_order values to keep the chain consistent.
     *
     * @param request  the incoming HTTP request with :id and :position params
     * @param response the outgoing HTTP response
     * @return a JSON string confirming removal or reporting an error
     */
    public String removeStage(Request request, Response response) {
        try {
            String gemId       = request.params(":id");
            String positionStr = request.params(":position");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            if (positionStr == null || positionStr.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Stage position is required."
                ).toJson();
            }

            int position;
            try {
                position = Integer.parseInt(positionStr.trim());
            } catch (NumberFormatException e) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Stage position must be a valid integer. "
                        + "Position 0 is the first stage."
                ).toJson();
            }

            GemLinkedList list = trackingService.getGemList(gemId.trim());
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            if (position < 0 || position >= list.getSize()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Invalid position: " + position
                        + ". Valid range is 0 to " + (list.getSize() - 1)
                ).toJson();
            }

            GemNode removed = trackingService.removeStage(
                    gemId.trim(), position);

            if (removed == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to remove stage at position: " + position
                ).toJson();
            }

            GemLinkedList updatedList =
                    trackingService.getGemList(gemId.trim());

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",          gemId.trim());
            result.put("removedStage",   removed.getStage().getLabel());
            result.put("removedPosition", position);
            result.put("remainingStages",
                    updatedList != null ? updatedList.getSize() : 0);

            response.status(200);
            return ApiResponse.success(
                    "Stage removed successfully at position " + position
                    + " for Gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to remove stage: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // PUT /api/gems/:id/stages/current/certificate
    // ---------------------------------------------------------

    /**
     * Adds certificate details to the current (tail) stage node.
     * Called after addStage when a certificate is issued at that stage.
     *
     * Request body:
     * {
     *   "certificateNumber": "GIC-2025-001",
     *   "issuingAuthority":  "National Gem and Jewellery Authority"
     * }
     *
     * @param request  the incoming HTTP request with JSON body
     * @param response the outgoing HTTP response
     * @return a JSON string confirming the certificate was added
     */
    public String addCertificate(Request request, Response response) {
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

            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body is required."
                ).toJson();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = ApiResponse.fromJson(body, Map.class);

            String certificateNumber = getString(data, "certificateNumber");
            String issuingAuthority  = getString(data, "issuingAuthority");

            if (certificateNumber.isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "certificateNumber is required."
                ).toJson();
            }

            boolean added = trackingService.addCertificateDetails(
                    gemId.trim(), certificateNumber, issuingAuthority);

            if (!added) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to add certificate to gem: " + gemId
                ).toJson();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",             gemId.trim());
            result.put("certificateNumber", certificateNumber);
            result.put("issuingAuthority",  issuingAuthority);
            result.put("appliedToStage",
                    list.getCurrentStageNode() != null
                        ? list.getCurrentStageNode().getStage().getLabel()
                        : "Unknown");

            response.status(200);
            return ApiResponse.success(
                    "Certificate added successfully to current stage",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to add certificate: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // PUT /api/gems/:id/stages/current/export
    // ---------------------------------------------------------

    /**
     * Adds export-specific details to the current EXPORTING stage node.
     * Must be called after addStage when the stage type is EXPORTING.
     *
     * Request body:
     * {
     *   "flightNumber":       "EK-653",
     *   "invoiceNumber":      "INV-2025-001",
     *   "destinationCountry": "Dubai"
     * }
     *
     * @param request  the incoming HTTP request with JSON body
     * @param response the outgoing HTTP response
     * @return a JSON string confirming the export details were added
     */
    public String addExportDetails(Request request, Response response) {
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

            // Verify the current stage is actually an EXPORTING stage
            GemNode currentNode = list.getCurrentStageNode();
            if (currentNode == null
                    || currentNode.getStage() != GemStage.EXPORTING) {
                response.status(400);
                return ApiResponse.badRequest(
                        "The current stage is not an EXPORTING stage. "
                        + "Export details can only be added to an "
                        + "EXPORTING stage."
                ).toJson();
            }

            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body is required."
                ).toJson();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = ApiResponse.fromJson(body, Map.class);

            String flightNumber       = getString(data, "flightNumber");
            String invoiceNumber      = getString(data, "invoiceNumber");
            String destinationCountry = getString(data, "destinationCountry");

            if (flightNumber.isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "flightNumber is required for export details."
                ).toJson();
            }

            boolean added = trackingService.addExportDetails(
                    gemId.trim(),
                    flightNumber,
                    invoiceNumber,
                    destinationCountry
            );

            if (!added) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to add export details to gem: " + gemId
                ).toJson();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",             gemId.trim());
            result.put("flightNumber",      flightNumber);
            result.put("invoiceNumber",     invoiceNumber);
            result.put("destinationCountry", destinationCountry);

            response.status(200);
            return ApiResponse.success(
                    "Export details added successfully to EXPORTING stage",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to add export details: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // PUT /api/gems/:id/stages/current/notes
    // ---------------------------------------------------------

    /**
     * Adds a note to the current (tail) stage node.
     * Notes provide additional context that does not fit into
     * the standard fields of a stage.
     *
     * Request body:
     * {
     *   "notes": "Additional information about this stage"
     * }
     *
     * @param request  the incoming HTTP request with JSON body
     * @param response the outgoing HTTP response
     * @return a JSON string confirming the note was added
     */
    public String addNotes(Request request, Response response) {
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

            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body is required."
                ).toJson();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = ApiResponse.fromJson(body, Map.class);

            String notes = getString(data, "notes");
            if (notes.isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "notes field is required and cannot be empty."
                ).toJson();
            }

            boolean added = trackingService.addNoteToCurrentStage(
                    gemId.trim(), notes);

            if (!added) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to add notes to gem: " + gemId
                ).toJson();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("gemId", gemId.trim());
            result.put("notes", notes);
            result.put("appliedToStage",
                    list.getCurrentStageNode() != null
                        ? list.getCurrentStageNode().getStage().getLabel()
                        : "Unknown");

            response.status(200);
            return ApiResponse.success(
                    "Notes added successfully to current stage",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to add notes: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Response building helpers
    // ---------------------------------------------------------

    /**
     * Builds a structured map representing a single stage node.
     * Used in both list responses and single stage responses to
     * ensure a consistent structure the frontend can rely on.
     *
     * Includes all core fields, all optional fields if set,
     * and calculated fields like price increase and days elapsed.
     *
     * @param node        the GemNode to convert to a map
     * @param index       the zero-based index of this node in the list
     * @param totalStages the total number of stages in the journey
     * @return a Map containing all stage data
     */
    private Map<String, Object> buildStageMap(GemNode node,
                                               int index,
                                               int totalStages) {
        Map<String, Object> stageMap = new HashMap<>();

        // Core fields always present
        stageMap.put("stageNumber",    index + 1);
        stageMap.put("stageType",      node.getStage().name());
        stageMap.put("stageLabel",     node.getStage().getLabel());
        stageMap.put("locationHint",   node.getStage().getLocationHint());
        stageMap.put("location",       node.getLocation());
        stageMap.put("personName",     node.getPersonName());
        stageMap.put("date",           node.getStageDate().toString());
        stageMap.put("weightInCarats", node.getWeightInCarats());
        stageMap.put("priceInRupees",  node.getPriceInRupees());
        stageMap.put("gemId",          node.getGemId());
        stageMap.put("gemType",        node.getGemType());
        stageMap.put("isCurrent",      index == totalStages - 1);
        stageMap.put("isFirst",        index == 0);

        // Optional fields — only included if they have been set
        if (node.getPersonIdNumber() != null)
            stageMap.put("personIdNumber",   node.getPersonIdNumber());
        if (node.getContactNumber() != null)
            stageMap.put("contactNumber",    node.getContactNumber());
        if (node.getCertificateNumber() != null)
            stageMap.put("certificateNumber", node.getCertificateNumber());
        if (node.getIssuingAuthority() != null)
            stageMap.put("issuingAuthority", node.getIssuingAuthority());
        if (node.getFlightNumber() != null)
            stageMap.put("flightNumber",     node.getFlightNumber());
        if (node.getInvoiceNumber() != null)
            stageMap.put("invoiceNumber",    node.getInvoiceNumber());
        if (node.getDestinationCountry() != null)
            stageMap.put("destinationCountry", node.getDestinationCountry());
        if (node.getNotes() != null)
            stageMap.put("notes",            node.getNotes());

        return stageMap;
    }

    // ---------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------

    /**
     * Validates all required fields for adding a new stage.
     * Returns an error message string if any required field is missing
     * or invalid. Returns null if all fields are valid.
     *
     * @param data the parsed JSON map from the request body
     * @return an error message string or null if validation passes
     */
    private String validateStageData(Map<String, Object> data) {
        if (data == null)
            return "Request body cannot be empty.";
        if (isEmpty(data, "stageType"))
            return "stageType is required. Valid values: "
                   + "MINING, CUTTING, TRADING, EXPORTING, BUYING";
        if (isEmpty(data, "location"))
            return "location is required.";
        if (isEmpty(data, "personName"))
            return "personName is required.";
        if (isEmpty(data, "stageDate"))
            return "stageDate is required. Format: yyyy-MM-dd";
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
     * Safely extracts a double value from a parsed JSON map.
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