package api.handlers;

import api.ApiResponse;
import model.GemLinkedList;
import service.JourneyMapService;
import service.TrackingService;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JourneyMapHandler handles all HTTP requests related to the gem journey
 * map visualizer feature.
 *
 * Each method corresponds to one API endpoint registered in ApiRouter.
 * Every method reads from the JourneyMapService and returns a JSON string
 * produced by ApiResponse.
 *
 * The journey map converts each node in the gem's Doubly Linked List into
 * a GPS coordinate pin on a Sri Lanka map. The ordered sequence of pins
 * from head to tail defines the route line that visually demonstrates
 * the linked list traversal from mining origin to final buyer.
 *
 * Endpoints handled:
 *   GET /api/map/:gemId          — full journey map data for one gem
 *   GET /api/map/:gemId/pins     — only the pin list for one gem
 *   GET /api/map/:gemId/route    — route coordinates for one gem
 *   GET /api/map/:gemId/stats    — route statistics for one gem
 *   GET /api/map/overview        — all gems origin pins on one map
 *   GET /api/map/locations       — all known Sri Lankan gem locations
 */
public class JourneyMapHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The JourneyMapService handles all map data building logic.
     * Injected via constructor from ApiRouter.
     */
    private JourneyMapService journeyMapService;

    /**
     * The TrackingService is used to validate gem IDs before
     * querying map data to return a proper 404 if gem not found.
     */
    private TrackingService trackingService;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new JourneyMapHandler with required service dependencies.
     *
     * @param journeyMapService the service for building map data
     * @param trackingService   the service for gem validation
     */
    public JourneyMapHandler(JourneyMapService journeyMapService,
                              TrackingService trackingService) {
        this.journeyMapService = journeyMapService;
        this.trackingService   = trackingService;
    }

    // ---------------------------------------------------------
    // GET /api/map/:gemId — full journey map data for one gem
    // ---------------------------------------------------------

    /**
     * Returns the complete map data for a gem journey.
     *
     * Traverses the gem's Doubly Linked List from head to tail,
     * converts each node into a GPS-coordinate map pin, and returns
     * the full data set needed to render the journey on a map.
     *
     * Response includes:
     *   gemId               — the gem ID
     *   gemType             — the gem type
     *   isCeylonVerified    — origin verification status
     *   totalStages         — number of nodes in the linked list
     *   pins                — ordered list of map pin objects
     *   routeCoordinates    — flat lat/lng pairs for the route line
     *   totalDistance       — estimated total journey distance in km
     *   domesticStages      — stages within Sri Lanka
     *   internationalStages — stages outside Sri Lanka
     *   originPin           — the head node (mining stage) pin
     *   currentPin          — the tail node (current stage) pin
     *   mapBounds           — lat/lng bounds for auto-fitting
     *   mapCenter           — centre point for initial map view
     *   reverseRoute        — backward traversal pin list
     *   routeStats          — journey statistics summary
     *
     * @param request  the incoming HTTP request with :gemId path param
     * @param response the outgoing HTTP response
     * @return a JSON string with the full map data
     */
    public String getJourneyMapData(Request request, Response response) {
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

            // Build the full map data by traversing the linked list
            Map<String, Object> mapData =
                    journeyMapService.buildJourneyMapData(gemId.trim());

            if (mapData.containsKey("error")) {
                response.status(400);
                return ApiResponse.error(
                        (String) mapData.get("error")
                ).toJson();
            }

            response.status(200);
            return ApiResponse.success(
                    "Journey map data built for gem " + gemId
                    + " — " + list.getSize() + " stage(s) mapped",
                    mapData
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to build journey map data: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/map/:gemId/pins — pin list only
    // ---------------------------------------------------------

    /**
     * Returns only the map pin list for a gem journey.
     * Lighter than the full map data endpoint — returns only the pins
     * without route coordinates, bounds, or statistics.
     *
     * Used when the frontend only needs to render the pin markers
     * without drawing the full route line or fitting bounds.
     *
     * Each pin contains:
     *   stageNumber, stageType, stageLabel, location, personName,
     *   date, weightInCarats, priceInRupees, lat, lng,
     *   isHead, isTail, isCurrent, isInternational,
     *   pinColor, pinIcon, popupContent
     *
     * @param request  the incoming HTTP request with :gemId path param
     * @param response the outgoing HTTP response
     * @return a JSON string with the pin list only
     */
    public String getJourneyPins(Request request, Response response) {
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

            Map<String, Object> mapData =
                    journeyMapService.buildJourneyMapData(gemId.trim());

            if (mapData.containsKey("error")) {
                response.status(400);
                return ApiResponse.error(
                        (String) mapData.get("error")
                ).toJson();
            }

            // Return only the pins list
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",      gemId.trim());
            result.put("totalStages",mapData.get("totalStages"));
            result.put("pins",       mapData.get("pins"));
            result.put("originPin",  mapData.get("originPin"));
            result.put("currentPin", mapData.get("currentPin"));

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + list.getSize() + " map pin(s) for gem " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve journey pins: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/map/:gemId/route — route coordinates only
    // ---------------------------------------------------------

    /**
     * Returns only the route coordinate list for a gem journey.
     * Each coordinate is a [latitude, longitude] pair in the order
     * the gem travelled — from mining origin to current location.
     *
     * Used when the frontend only needs the route line data without
     * the full pin detail objects.
     *
     * Also returns the reverse route (backward traversal using prev
     * pointers) to demonstrate the Doubly Linked List capability.
     *
     * @param request  the incoming HTTP request with :gemId path param
     * @param response the outgoing HTTP response
     * @return a JSON string with forward and backward route coordinates
     */
    public String getJourneyRoute(Request request, Response response) {
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

            Map<String, Object> mapData =
                    journeyMapService.buildJourneyMapData(gemId.trim());

            if (mapData.containsKey("error")) {
                response.status(400);
                return ApiResponse.error(
                        (String) mapData.get("error")
                ).toJson();
            }

            // Return route coordinates and reverse route
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",            gemId.trim());
            result.put("totalStages",      mapData.get("totalStages"));
            result.put("totalDistance",    mapData.get("totalDistance"));
            result.put("routeCoordinates", mapData.get("routeCoordinates"));
            result.put("reverseRoute",     mapData.get("reverseRoute"));
            result.put("mapBounds",        mapData.get("mapBounds"));
            result.put("mapCenter",        mapData.get("mapCenter"));
            result.put("domesticStages",   mapData.get("domesticStages"));
            result.put("internationalStages", mapData.get("internationalStages"));

            response.status(200);
            return ApiResponse.success(
                    "Route data retrieved for gem " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve journey route: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/map/:gemId/stats — route statistics
    // ---------------------------------------------------------

    /**
     * Returns the route statistics for a gem journey.
     * Used on the map page to show a summary panel alongside the map.
     *
     * Statistics include:
     *   totalDistanceKm       — estimated total journey distance
     *   domesticStages        — stages within Sri Lanka
     *   internationalStages   — stages outside Sri Lanka
     *   totalStages           — total number of nodes
     *   uniqueLocations       — number of distinct locations visited
     *   journeyDays           — days from mining to current stage
     *   totalPriceAppreciation— total price increase across journey
     *   appreciationPercent   — price appreciation as percentage
     *
     * @param request  the incoming HTTP request with :gemId path param
     * @param response the outgoing HTTP response
     * @return a JSON string with the route statistics
     */
    public String getJourneyStats(Request request, Response response) {
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

            Map<String, Object> mapData =
                    journeyMapService.buildJourneyMapData(gemId.trim());

            if (mapData.containsKey("error")) {
                response.status(400);
                return ApiResponse.error(
                        (String) mapData.get("error")
                ).toJson();
            }

            // Return stats with gem overview
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",         gemId.trim());
            result.put("gemType",        mapData.get("gemType"));
            result.put("isCeylonVerified", mapData.get("isCeylonVerified"));
            result.put("routeStats",     mapData.get("routeStats"));
            result.put("totalStages",    mapData.get("totalStages"));
            result.put("totalDistance",  mapData.get("totalDistance"));
            result.put("domesticStages", mapData.get("domesticStages"));
            result.put("internationalStages", mapData.get("internationalStages"));
            result.put("originPin",      mapData.get("originPin"));
            result.put("currentPin",     mapData.get("currentPin"));

            response.status(200);
            return ApiResponse.success(
                    "Journey statistics retrieved for gem " + gemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve journey statistics: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/map/overview — all gems overview map
    // ---------------------------------------------------------

    /**
     * Returns a simplified overview map showing the origin pin
     * for every gem in the system on a single map.
     *
     * Each pin represents one gem's mining location.
     * Green pins are Ceylon verified, red pins are unverified.
     *
     * Used on the Map Overview page to show the geographic distribution
     * of all registered gems across Sri Lanka mining areas.
     *
     * Supports optional filtering:
     *   ?verified=true  — show only Ceylon verified gems
     *   ?verified=false — show only unverified gems
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with all gem origin pins
     */
    public String getAllGemsMapOverview(Request request, Response response) {
        try {
            String verifiedFilter = request.queryParams("verified");

            List<Map<String, Object>> allPins =
                    journeyMapService.buildAllGemsMapOverview();

            // Apply verified filter if provided
            if (verifiedFilter != null && !verifiedFilter.trim().isEmpty()) {
                boolean filterValue = Boolean.parseBoolean(verifiedFilter.trim());
                List<Map<String, Object>> filtered = new ArrayList<>();
                for (Map<String, Object> pin : allPins) {
                    Boolean isVerified = (Boolean) pin.get("isCeylonVerified");
                    if (isVerified != null && isVerified == filterValue) {
                        filtered.add(pin);
                    }
                }
                allPins = filtered;
            }

            // Count verified vs unverified
            int verified   = 0;
            int unverified = 0;
            for (Map<String, Object> pin : allPins) {
                Boolean isVerified = (Boolean) pin.get("isCeylonVerified");
                if (Boolean.TRUE.equals(isVerified)) verified++;
                else unverified++;
            }

            // Build overview result
            Map<String, Object> result = new HashMap<>();
            result.put("pins",           allPins);
            result.put("totalGems",      allPins.size());
            result.put("verifiedCount",  verified);
            result.put("unverifiedCount",unverified);
            result.put("mapCenter",      buildSriLankaCentre());
            result.put("defaultZoom",    7);

            response.status(200);
            return ApiResponse.success(
                    "Map overview retrieved — " + allPins.size() + " gem(s) plotted",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to build map overview: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/map/locations — known gem locations
    // ---------------------------------------------------------

    /**
     * Returns the list of all known Sri Lankan gem mining and trading
     * locations with their GPS coordinates.
     *
     * Used to populate a reference layer on the map showing where
     * known gem locations are, even if no gem has been registered there.
     *
     * Each location entry contains:
     *   name — the location name
     *   lat  — latitude
     *   lng  — longitude
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with all known location coordinates
     */
    public String getKnownLocations(Request request, Response response) {
        try {
            List<Map<String, Object>> locations =
                    journeyMapService.getKnownLocations();

            Map<String, Object> result = new HashMap<>();
            result.put("locations",     locations);
            result.put("totalLocations",locations.size());
            result.put("mapCenter",     buildSriLankaCentre());

            response.status(200);
            return ApiResponse.success(
                    "Retrieved " + locations.size() + " known gem locations",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve known locations: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------

    /**
     * Builds the default Sri Lanka map centre coordinates.
     * Used as the initial view centre for overview maps.
     *
     * @return a map with lat and lng for the centre of Sri Lanka
     */
    private Map<String, Object> buildSriLankaCentre() {
        Map<String, Object> centre = new HashMap<>();
        centre.put("lat", 7.8731);
        centre.put("lng", 80.7718);
        centre.put("zoom", 7);
        return centre;
    }
}
