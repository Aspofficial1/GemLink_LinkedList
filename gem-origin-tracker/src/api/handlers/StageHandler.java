package api.handlers;

import api.ApiResponse;
import model.GemLinkedList;
import model.GemNode;
import model.GemStage;
import service.AuditService;
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
 * updating, and removing stages from a gem's doubly linked list journey.
 *
 * Each method corresponds to one API endpoint registered in ApiRouter.
 * The handler validates the request data, calls TrackingService, and
 * returns a structured ApiResponse JSON string.
 *
 * NEW — updateStage() method added:
 *   PUT /api/gems/:id/stages/:position
 *   Allows updating any field of any stage node in the linked list.
 *   Records old value and new value to the audit log automatically.
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
     * The AuditService records every change to the audit log.
     * Called after every update and delete operation so the
     * complete change history is preserved permanently.
     */
    private AuditService auditService;

    /**
     * The date format expected in all request bodies.
     * The frontend must send dates in this exact format.
     */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new StageHandler with the required service dependencies.
     *
     * @param trackingService the service for all stage operations
     * @param auditService    the service for recording audit log entries
     */
    public StageHandler(TrackingService trackingService,
                        AuditService auditService) {
        this.trackingService = trackingService;
        this.auditService    = auditService;
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/stages — get all stages
    // ---------------------------------------------------------

    /**
     * Returns all stages for a specific gem as an ordered list.
     * Each stage in the response represents one node in the doubly
     * linked list, ordered from head (mining) to tail (current owner).
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

            List<GemNode> stages  = list.getAllStages();
            List<Map<String, Object>> stageList = new ArrayList<>();

            for (int i = 0; i < stages.size(); i++) {
                GemNode node = stages.get(i);
                stageList.add(buildStageMap(node, i, stages.size()));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",       gemId.trim());
            result.put("totalStages", list.getSize());
            result.put("stages",      stageList);
            result.put("weightLoss",
                    list.calculateWeightLoss());
            result.put("weightLossPercent",
                    list.calculateWeightLossPercentage());
            result.put("priceAppreciation",
                    list.calculatePriceAppreciation());

            GemNode head = list.getMiningNode();
            GemNode tail = list.getCurrentStageNode();

            if (head != null) {
                result.put("originLocation", head.getLocation());
                result.put("miningDate",     head.getStageDate().toString());
                result.put("originalWeight", head.getWeightInCarats());
                result.put("miningPrice",    head.getPriceInRupees());
            }

            if (tail != null) {
                result.put("currentStage",      tail.getStage().name());
                result.put("currentStageLabel", tail.getStage().getLabel());
                result.put("currentOwner",      tail.getPersonName());
                result.put("currentLocation",   tail.getLocation());
                result.put("currentWeight",     tail.getWeightInCarats());
                result.put("currentPrice",      tail.getPriceInRupees());
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

            GemLinkedList existingList =
                    trackingService.getGemList(gemId.trim());
            if (existingList == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body is required. Send stage details as JSON."
                ).toJson();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = ApiResponse.fromJson(body, Map.class);

            String validationError = validateStageData(data);
            if (validationError != null) {
                response.status(400);
                return ApiResponse.badRequest(validationError).toJson();
            }

            String    stageTypeStr   = getString(data, "stageType");
            String    location       = getString(data, "location");
            String    personName     = getString(data, "personName");
            String    personIdNumber = getString(data, "personIdNumber");
            String    contactNumber  = getString(data, "contactNumber");
            double    weightInCarats = getDouble(data, "weightInCarats");
            double    priceInRupees  = getDouble(data, "priceInRupees");
            LocalDate stageDate      = parseDate(getString(data, "stageDate"));

            if (stageDate == null) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Invalid stage date format. Use yyyy-MM-dd"
                ).toJson();
            }

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

            // Add export details if EXPORTING stage
            String flightNumber       = getString(data, "flightNumber");
            String invoiceNumber      = getString(data, "invoiceNumber");
            String destinationCountry = getString(data, "destinationCountry");

            if (stageType == GemStage.EXPORTING && !flightNumber.isEmpty()) {
                trackingService.addExportDetails(
                        gemId.trim(), flightNumber, invoiceNumber, destinationCountry);
                newNode.setFlightNumber(flightNumber);
                newNode.setInvoiceNumber(invoiceNumber);
                newNode.setDestinationCountry(destinationCountry);
            }

            // Add certificate if provided
            String certificateNumber = getString(data, "certificateNumber");
            String issuingAuthority  = getString(data, "issuingAuthority");

            if (!certificateNumber.isEmpty()) {
                trackingService.addCertificateDetails(
                        gemId.trim(), certificateNumber, issuingAuthority);
                newNode.setCertificateNumber(certificateNumber);
                newNode.setIssuingAuthority(issuingAuthority);
            }

            // Add notes if provided
            String notes = getString(data, "notes");
            if (!notes.isEmpty()) {
                trackingService.addNoteToCurrentStage(gemId.trim(), notes);
                newNode.setNotes(notes);
            }

            // Record in audit log
            GemLinkedList updatedList = trackingService.getGemList(gemId.trim());
            int stageNumber = updatedList != null ? updatedList.getSize() : 1;
            auditService.logStageAdded(
                    gemId.trim(), stageNumber,
                    stageType.name(), location, personName,
                    weightInCarats, priceInRupees,
                    stageDate.toString()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",     gemId.trim());
            result.put("newStage",
                    buildStageMap(newNode,
                            updatedList != null ? updatedList.getSize() - 1 : 0,
                            updatedList != null ? updatedList.getSize() : 1));
            result.put("totalStages",
                    updatedList != null ? updatedList.getSize() : 1);
            result.put("currentStage",      stageType.name());
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
    // PUT /api/gems/:id/stages/:position — update a stage
    // ---------------------------------------------------------

    /**
     * Updates one or more fields of an existing stage node in the
     * gem's doubly linked list at the given position.
     *
     * Before updating, the handler captures the full old state of
     * the node as a snapshot. After updating, the new state is also
     * captured. Both are written to the audit log so the complete
     * change history is permanently preserved.
     *
     * Only fields included in the request body are updated.
     * Fields not included are left unchanged.
     *
     * Request body (all fields optional — only send what you want to change):
     * {
     *   "location":        "Colombo, Gem Bureau",
     *   "personName":      "Nimal Fernando",
     *   "personIdNumber":  "199012345678",
     *   "contactNumber":   "0771234567",
     *   "weightInCarats":  4.5,
     *   "priceInRupees":   350000,
     *   "stageDate":       "2025-01-25",
     *   "certificateNumber": "GIC-2025-001",
     *   "issuingAuthority":  "National Gem Authority",
     *   "flightNumber":    "EK-653",
     *   "invoiceNumber":   "INV-2025-001",
     *   "destinationCountry": "Dubai",
     *   "notes":           "Corrected location name"
     * }
     *
     * @param request  the incoming HTTP request with :id and :position params
     * @param response the outgoing HTTP response
     * @return a JSON string with the updated stage data and audit record
     */
    public String updateStage(Request request, Response response) {
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
                        "Stage position is required. Position 0 is the first stage."
                ).toJson();
            }

            int position;
            try {
                position = Integer.parseInt(positionStr.trim());
            } catch (NumberFormatException e) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Stage position must be a valid integer."
                ).toJson();
            }

            // Get the gem linked list
            GemLinkedList list = trackingService.getGemList(gemId.trim());
            if (list == null) {
                response.status(404);
                return ApiResponse.notFound("Gem", gemId).toJson();
            }

            // Validate position is in range
            if (position < 0 || position >= list.getSize()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Invalid position: " + position
                        + ". Valid range is 0 to " + (list.getSize() - 1)
                ).toJson();
            }

            // Get the node at this position
            List<GemNode> allStages = list.getAllStages();
            GemNode node = allStages.get(position);

            // Capture full OLD state before any changes
            String oldSnapshot = auditService.buildNodeSnapshot(node);

            // Parse request body
            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body is required. Send fields to update as JSON."
                ).toJson();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = ApiResponse.fromJson(body, Map.class);

            if (data == null || data.isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest(
                        "Request body cannot be empty. Send at least one field to update."
                ).toJson();
            }

            // Track which fields were actually changed
            List<String> changedFields = new ArrayList<>();

            // ── Update location ──────────────────────────────────
            if (data.containsKey("location")) {
                String newLocation = getString(data, "location");
                if (!newLocation.isEmpty() && !newLocation.equals(node.getLocation())) {
                    auditService.logStageUpdated(
                            gemId.trim(), position + 1, node.getStage().name(),
                            "location", node.getLocation(), newLocation);
                    node.setLocation(newLocation);
                    changedFields.add("location");
                }
            }

            // ── Update personName ────────────────────────────────
            if (data.containsKey("personName")) {
                String newName = getString(data, "personName");
                if (!newName.isEmpty() && !newName.equals(node.getPersonName())) {
                    auditService.logStageUpdated(
                            gemId.trim(), position + 1, node.getStage().name(),
                            "personName", node.getPersonName(), newName);
                    node.setPersonName(newName);
                    changedFields.add("personName");
                }
            }

            // ── Update personIdNumber ────────────────────────────
            if (data.containsKey("personIdNumber")) {
                String newId = getString(data, "personIdNumber");
                if (!newId.isEmpty()) {
                    String oldId = node.getPersonIdNumber() != null
                            ? node.getPersonIdNumber() : "";
                    if (!newId.equals(oldId)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "personIdNumber", oldId, newId);
                        node.setPersonIdNumber(newId);
                        changedFields.add("personIdNumber");
                    }
                }
            }

            // ── Update contactNumber ─────────────────────────────
            if (data.containsKey("contactNumber")) {
                String newContact = getString(data, "contactNumber");
                if (!newContact.isEmpty()) {
                    String oldContact = node.getContactNumber() != null
                            ? node.getContactNumber() : "";
                    if (!newContact.equals(oldContact)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "contactNumber", oldContact, newContact);
                        node.setContactNumber(newContact);
                        changedFields.add("contactNumber");
                    }
                }
            }

            // ── Update weightInCarats ────────────────────────────
            if (data.containsKey("weightInCarats")) {
                double newWeight = getDouble(data, "weightInCarats");
                if (newWeight > 0 && newWeight != node.getWeightInCarats()) {
                    auditService.logStageUpdated(
                            gemId.trim(), position + 1, node.getStage().name(),
                            "weightInCarats",
                            String.valueOf(node.getWeightInCarats()),
                            String.valueOf(newWeight));
                    node.setWeightInCarats(newWeight);
                    changedFields.add("weightInCarats");
                }
            }

            // ── Update priceInRupees ─────────────────────────────
            if (data.containsKey("priceInRupees")) {
                double newPrice = getDouble(data, "priceInRupees");
                if (newPrice > 0 && newPrice != node.getPriceInRupees()) {
                    auditService.logStageUpdated(
                            gemId.trim(), position + 1, node.getStage().name(),
                            "priceInRupees",
                            String.valueOf(node.getPriceInRupees()),
                            String.valueOf(newPrice));
                    node.setPriceInRupees(newPrice);
                    changedFields.add("priceInRupees");
                }
            }

            // ── Update stageDate ─────────────────────────────────
            if (data.containsKey("stageDate")) {
                LocalDate newDate = parseDate(getString(data, "stageDate"));
                if (newDate != null && !newDate.equals(node.getStageDate())) {
                    auditService.logStageUpdated(
                            gemId.trim(), position + 1, node.getStage().name(),
                            "stageDate",
                            node.getStageDate().toString(),
                            newDate.toString());
                    node.setStageDate(newDate);
                    changedFields.add("stageDate");
                }
            }

            // ── Update certificateNumber ─────────────────────────
            if (data.containsKey("certificateNumber")) {
                String newCert = getString(data, "certificateNumber");
                if (!newCert.isEmpty()) {
                    String oldCert = node.getCertificateNumber() != null
                            ? node.getCertificateNumber() : "";
                    if (!newCert.equals(oldCert)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "certificateNumber", oldCert, newCert);
                        node.setCertificateNumber(newCert);
                        changedFields.add("certificateNumber");
                    }
                }
            }

            // ── Update issuingAuthority ──────────────────────────
            if (data.containsKey("issuingAuthority")) {
                String newAuth = getString(data, "issuingAuthority");
                if (!newAuth.isEmpty()) {
                    String oldAuth = node.getIssuingAuthority() != null
                            ? node.getIssuingAuthority() : "";
                    if (!newAuth.equals(oldAuth)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "issuingAuthority", oldAuth, newAuth);
                        node.setIssuingAuthority(newAuth);
                        changedFields.add("issuingAuthority");
                    }
                }
            }

            // ── Update flightNumber ──────────────────────────────
            if (data.containsKey("flightNumber")) {
                String newFlight = getString(data, "flightNumber");
                if (!newFlight.isEmpty()) {
                    String oldFlight = node.getFlightNumber() != null
                            ? node.getFlightNumber() : "";
                    if (!newFlight.equals(oldFlight)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "flightNumber", oldFlight, newFlight);
                        node.setFlightNumber(newFlight);
                        changedFields.add("flightNumber");
                    }
                }
            }

            // ── Update invoiceNumber ─────────────────────────────
            if (data.containsKey("invoiceNumber")) {
                String newInvoice = getString(data, "invoiceNumber");
                if (!newInvoice.isEmpty()) {
                    String oldInvoice = node.getInvoiceNumber() != null
                            ? node.getInvoiceNumber() : "";
                    if (!newInvoice.equals(oldInvoice)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "invoiceNumber", oldInvoice, newInvoice);
                        node.setInvoiceNumber(newInvoice);
                        changedFields.add("invoiceNumber");
                    }
                }
            }

            // ── Update destinationCountry ────────────────────────
            if (data.containsKey("destinationCountry")) {
                String newDest = getString(data, "destinationCountry");
                if (!newDest.isEmpty()) {
                    String oldDest = node.getDestinationCountry() != null
                            ? node.getDestinationCountry() : "";
                    if (!newDest.equals(oldDest)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "destinationCountry", oldDest, newDest);
                        node.setDestinationCountry(newDest);
                        changedFields.add("destinationCountry");
                    }
                }
            }

            // ── Update notes ─────────────────────────────────────
            if (data.containsKey("notes")) {
                String newNotes = getString(data, "notes");
                if (!newNotes.isEmpty()) {
                    String oldNotes = node.getNotes() != null
                            ? node.getNotes() : "";
                    if (!newNotes.equals(oldNotes)) {
                        auditService.logStageUpdated(
                                gemId.trim(), position + 1, node.getStage().name(),
                                "notes", oldNotes, newNotes);
                        node.setNotes(newNotes);
                        changedFields.add("notes");
                    }
                }
            }

            // If nothing actually changed, return early
            if (changedFields.isEmpty()) {
                response.status(200);
                return ApiResponse.success(
                        "No changes detected — all submitted values match existing data.",
                        buildStageMap(node, position, list.getSize())
                ).toJson();
            }

            // Persist the updated node back to the database
            boolean saved = trackingService.updateStageInDatabase(
                    gemId.trim(), position, node);

            if (!saved) {
                response.status(500);
                return ApiResponse.serverError(
                        "Stage updated in memory but failed to save to database."
                ).toJson();
            }

            // Capture NEW state after all changes
            String newSnapshot = auditService.buildNodeSnapshot(node);

            // Write a single summary audit log entry for the whole update
            auditService.logStageUpdated(
                    gemId.trim(), position + 1, node.getStage().name(),
                    "FULL_UPDATE", oldSnapshot, newSnapshot
            );

            // Build response
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",          gemId.trim());
            result.put("position",       position);
            result.put("stageNumber",    position + 1);
            result.put("changedFields",  changedFields);
            result.put("totalChanged",   changedFields.size());
            result.put("oldSnapshot",    oldSnapshot);
            result.put("newSnapshot",    newSnapshot);
            result.put("updatedStage",   buildStageMap(node, position, list.getSize()));

            response.status(200);
            return ApiResponse.success(
                    "Stage updated successfully at position " + position
                    + " — " + changedFields.size() + " field(s) changed"
                    + " for Gem: " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to update stage: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // DELETE /api/gems/:id/stages/:position — remove a stage
    // ---------------------------------------------------------

    /**
     * Removes a stage node at a specific position from the gem's linked list.
     * Before deleting, the full node state is captured and written to the
     * audit log so the deletion is permanently recorded.
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

            // Capture stage before deleting for audit log
            List<GemNode> stagesBefore = list.getAllStages();
            GemNode nodeToDelete = stagesBefore.get(position);

            // Log deletion BEFORE it happens so old data is captured
            auditService.logStageDeleted(
                    gemId.trim(), position + 1, nodeToDelete);

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
            result.put("gemId",           gemId.trim());
            result.put("removedStage",    removed.getStage().getLabel());
            result.put("removedStageType",removed.getStage().name());
            result.put("removedPosition", position);
            result.put("removedLocation", removed.getLocation());
            result.put("removedPerson",   removed.getPersonName());
            result.put("removedDate",     removed.getStageDate().toString());
            result.put("removedWeight",   removed.getWeightInCarats());
            result.put("removedPrice",    removed.getPriceInRupees());
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
                return ApiResponse.badRequest("Request body is required.").toJson();
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

            // Record in audit log
            GemNode currentNode = list.getCurrentStageNode();
            int stageNumber = list.getSize();
            auditService.logCertificateAdded(
                    gemId.trim(), stageNumber,
                    certificateNumber, issuingAuthority);

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",             gemId.trim());
            result.put("certificateNumber", certificateNumber);
            result.put("issuingAuthority",  issuingAuthority);
            result.put("appliedToStage",
                    currentNode != null
                        ? currentNode.getStage().getLabel()
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

            GemNode currentNode = list.getCurrentStageNode();
            if (currentNode == null
                    || currentNode.getStage() != GemStage.EXPORTING) {
                response.status(400);
                return ApiResponse.badRequest(
                        "The current stage is not an EXPORTING stage. "
                        + "Export details can only be added to an EXPORTING stage."
                ).toJson();
            }

            String body = request.body();
            if (body == null || body.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Request body is required.").toJson();
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
                    gemId.trim(), flightNumber, invoiceNumber, destinationCountry);

            if (!added) {
                response.status(500);
                return ApiResponse.serverError(
                        "Failed to add export details to gem: " + gemId
                ).toJson();
            }

            // Record in audit log
            auditService.logExportAdded(
                    gemId.trim(), list.getSize(),
                    flightNumber, invoiceNumber, destinationCountry);

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",              gemId.trim());
            result.put("flightNumber",       flightNumber);
            result.put("invoiceNumber",      invoiceNumber);
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
                return ApiResponse.badRequest("Request body is required.").toJson();
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

            // Record in audit log
            auditService.logNoteAdded(gemId.trim(), list.getSize(), notes);

            GemNode currentNode = list.getCurrentStageNode();
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",        gemId.trim());
            result.put("notes",        notes);
            result.put("appliedToStage",
                    currentNode != null
                        ? currentNode.getStage().getLabel()
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

        if (node.getPersonIdNumber()   != null)
            stageMap.put("personIdNumber",    node.getPersonIdNumber());
        if (node.getContactNumber()    != null)
            stageMap.put("contactNumber",     node.getContactNumber());
        if (node.getCertificateNumber() != null)
            stageMap.put("certificateNumber", node.getCertificateNumber());
        if (node.getIssuingAuthority() != null)
            stageMap.put("issuingAuthority",  node.getIssuingAuthority());
        if (node.getFlightNumber()     != null)
            stageMap.put("flightNumber",      node.getFlightNumber());
        if (node.getInvoiceNumber()    != null)
            stageMap.put("invoiceNumber",     node.getInvoiceNumber());
        if (node.getDestinationCountry() != null)
            stageMap.put("destinationCountry", node.getDestinationCountry());
        if (node.getNotes()            != null)
            stageMap.put("notes",             node.getNotes());

        return stageMap;
    }

    // ---------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------

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

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return "";
        return value.toString().trim();
    }

    private double getDouble(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private boolean isEmpty(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return true;
        return value.toString().trim().isEmpty();
    }

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