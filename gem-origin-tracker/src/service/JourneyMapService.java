package service;

import model.GemLinkedList;
import model.GemNode;
import model.GemStage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JourneyMapService is Novel Feature 5 of the Ceylon Gem Origin Tracking System.
 *
 * It traverses the complete Doubly Linked List of a gem journey and
 * converts every node into a map coordinate point on the Sri Lanka map.
 * Each stage node is assigned GPS coordinates based on its recorded
 * location string, and the full list is returned as an ordered sequence
 * of map pins connected by a route line.
 *
 * This directly and visually demonstrates the Doubly Linked List because:
 *   - Forward traversal (head → tail) draws the route from mine to buyer
 *   - Backward traversal (tail → head) draws the reverse route
 *   - Each node becomes a visible pin on the map
 *   - The prev and next pointers define the connecting route lines
 *
 * Location resolution works by matching the location string of each
 * GemNode against a known lookup table of Sri Lankan gem trading locations.
 * If a known location is matched, its exact GPS coordinates are used.
 * If no match is found, the coordinates default to the centre of Sri Lanka.
 *
 * The service also provides route statistics — total distance travelled,
 * number of location changes, international vs domestic stages, and
 * a summary of the geographic coverage of the gem journey.
 */
public class JourneyMapService {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem linked lists.
     * Injected via constructor.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier used to check Ceylon verification status
     * for each gem when building map data.
     */
    private OriginVerifier originVerifier;

    /**
     * Default coordinates for the centre of Sri Lanka.
     * Used as a fallback when a location cannot be matched.
     */
    private static final double DEFAULT_LAT = 7.8731;
    private static final double DEFAULT_LNG = 80.7718;

    // ---------------------------------------------------------
    // Known Sri Lankan gem location coordinates
    // These are the GPS coordinates of major gem trading locations
    // used to plot stage nodes on the map
    // ---------------------------------------------------------

    /**
     * Known Sri Lankan gem trading and mining locations with GPS coordinates.
     * Keys are lowercase location name fragments for partial matching.
     * Values are double arrays: [latitude, longitude].
     */
    private static final Map<String, double[]> KNOWN_LOCATIONS =
            new HashMap<String, double[]>() {{

        // Mining districts
        put("ratnapura",      new double[]{6.6828,  80.3992});
        put("pelmadulla",     new double[]{6.6200,  80.4300});
        put("eheliyagoda",    new double[]{6.8453,  80.2728});
        put("kuruwita",       new double[]{6.7667,  80.3833});
        put("balangoda",      new double[]{6.6500,  80.7000});
        put("rakwana",        new double[]{6.2833,  80.6500});
        put("matale",         new double[]{7.4675,  80.6234});
        put("elahera",        new double[]{7.8500,  80.7000});
        put("badulla",        new double[]{6.9934,  81.0550});
        put("kandy",          new double[]{7.2906,  80.6337});
        put("galle",          new double[]{6.0535,  80.2210});
        put("matara",         new double[]{5.9549,  80.5550});
        put("hambantota",     new double[]{6.1241,  81.1185});
        put("trincomalee",    new double[]{8.5874,  81.2152});
        put("kurunegala",     new double[]{7.4818,  80.3609});
        put("polonnaruwa",    new double[]{7.9403,  81.0188});
        put("anuradhapura",   new double[]{8.3114,  80.4037});
        put("ampara",         new double[]{7.2917,  81.6722});
        put("moneragala",     new double[]{6.8728,  81.3500});

        // Cutting and trading locations
        put("beruwala",       new double[]{6.4794,  79.9831});
        put("gem street",     new double[]{6.4794,  79.9831});
        put("colombo",        new double[]{6.9271,  79.8612});
        put("gem bureau",     new double[]{6.9271,  79.8612});
        put("pettah",         new double[]{6.9344,  79.8567});
        put("bambalapitiya",  new double[]{6.8959,  79.8556});
        put("kollupitiya",    new double[]{6.9108,  79.8529});
        put("gampaha",        new double[]{7.0917,  79.9997});
        put("negombo",        new double[]{7.2111,  79.8386});
        put("panadura",       new double[]{6.7130,  79.9049});
        put("moratuwa",       new double[]{6.7729,  79.8822});
        put("mount lavinia",  new double[]{6.8389,  79.8640});
        put("maharagama",     new double[]{6.8483,  79.9267});
        put("nugegoda",       new double[]{6.8728,  79.8878});
        put("kaduwela",       new double[]{6.9355,  79.9880});

        // Export locations
        put("katunayake",     new double[]{7.1696,  79.8845});
        put("bandaranaike",   new double[]{7.1696,  79.8845});
        put("airport",        new double[]{7.1696,  79.8845});
        put("customs",        new double[]{6.9371,  79.8427});
        put("port",           new double[]{6.9371,  79.8427});
        put("colombo port",   new double[]{6.9371,  79.8427});
        put("free trade",     new double[]{6.9275,  79.8606});

        // International destinations — approximate coordinates
        put("dubai",          new double[]{25.2048, 55.2708});
        put("hong kong",      new double[]{22.3193, 114.1694});
        put("singapore",      new double[]{1.3521,  103.8198});
        put("london",         new double[]{51.5074, -0.1278});
        put("new york",       new double[]{40.7128, -74.0060});
        put("tokyo",          new double[]{35.6762, 139.6503});
        put("bangkok",        new double[]{13.7563, 100.5018});
        put("mumbai",         new double[]{19.0760, 72.8777});
        put("paris",          new double[]{48.8566, 2.3522});
        put("amsterdam",      new double[]{52.3676, 4.9041});
        put("zurich",         new double[]{47.3769, 8.5417});
        put("geneva",         new double[]{46.2044, 6.1432});
        put("antwerp",        new double[]{51.2194, 4.4025});
        put("tel aviv",       new double[]{32.0853, 34.7818});
        put("jeddah",         new double[]{21.4858, 39.1925});
        put("doha",           new double[]{25.2854, 51.5310});
        put("abu dhabi",      new double[]{24.4539, 54.3773});
        put("kuala lumpur",   new double[]{3.1390,  101.6869});
        put("sydney",         new double[]{-33.8688, 151.2093});
        put("beijing",        new double[]{39.9042, 116.4074});
        put("shanghai",       new double[]{31.2304, 121.4737});
    }};

    /**
     * Stage type to map icon color mapping.
     * Used by the frontend to render different colored pins per stage type.
     */
    private static final Map<String, String> STAGE_PIN_COLORS =
            new HashMap<String, String>() {{
        put("MINING",   "#1B4F8A");   // blue  — mining origin
        put("CUTTING",  "#C9A84C");   // gold  — cutting and polishing
        put("TRADING",  "#166534");   // green — trading
        put("EXPORTING","#7C3AED");   // purple— exporting
        put("BUYING",   "#DC2626");   // red   — final buyer
    }};

    /**
     * Stage type to map icon shape identifier.
     * Used by the frontend to render different pin shapes.
     */
    private static final Map<String, String> STAGE_PIN_ICONS =
            new HashMap<String, String>() {{
        put("MINING",   "mine");
        put("CUTTING",  "cut");
        put("TRADING",  "trade");
        put("EXPORTING","export");
        put("BUYING",   "buyer");
    }};

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new JourneyMapService with required service dependencies.
     *
     * @param trackingService the service for retrieving gem linked lists
     * @param originVerifier  the service for checking Ceylon verification
     */
    public JourneyMapService(TrackingService trackingService,
                              OriginVerifier originVerifier) {
        this.trackingService = trackingService;
        this.originVerifier  = originVerifier;
    }

    // ---------------------------------------------------------
    // Main map data builder
    // ---------------------------------------------------------

    /**
     * Builds the complete map data for a gem journey by traversing
     * the Doubly Linked List from head to tail.
     *
     * Each node in the list becomes a map pin with GPS coordinates,
     * stage details, and visual styling information. The ordered
     * list of pins defines the route line drawn on the map.
     *
     * Returns a complete map data object containing:
     *   gemId           — the gem ID
     *   gemType         — the gem type
     *   isCeylonVerified— verification status
     *   pins            — ordered list of map pin objects
     *   routeCoordinates— flat list of [lat,lng] pairs for the route line
     *   totalStages     — number of nodes in the list
     *   totalDistance   — estimated total distance in km
     *   domesticStages  — number of stages within Sri Lanka
     *   internationalStages — number of stages outside Sri Lanka
     *   originPin       — the first pin (mining node / head)
     *   currentPin      — the last pin (current node / tail)
     *   mapBounds       — lat/lng bounds for auto-fitting the map view
     *   routeStats      — statistical summary of the journey route
     *
     * @param gemId the ID of the gem to build map data for
     * @return a Map containing all map data for the frontend
     */
    public Map<String, Object> buildJourneyMapData(String gemId) {
        Map<String, Object> result = new HashMap<>();

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            result.put("error", "Gem not found: " + gemId);
            return result;
        }

        // Traverse the linked list from head to tail
        List<GemNode> stages = list.getAllStages();
        if (stages.isEmpty()) {
            result.put("error", "No stages found for gem: " + gemId);
            return result;
        }

        boolean isCeylon = originVerifier.verifyGemOrigin(gemId);
        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        // Build map pins from each node
        List<Map<String, Object>> pins = new ArrayList<>();
        List<double[]>            routeCoords = new ArrayList<>();

        int domesticStages       = 0;
        int internationalStages  = 0;
        double totalDistance     = 0;
        double prevLat = 0, prevLng = 0;

        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);

            // Resolve GPS coordinates for this node's location
            double[] coords = resolveCoordinates(node.getLocation());
            double lat = coords[0];
            double lng = coords[1];

            // Determine if this stage is domestic or international
            boolean isInternational = isInternationalLocation(node.getLocation());
            if (isInternational) internationalStages++;
            else                  domesticStages++;

            // Calculate distance from previous pin
            double distFromPrev = 0;
            if (i > 0 && prevLat != 0) {
                distFromPrev = calculateDistance(prevLat, prevLng, lat, lng);
                totalDistance += distFromPrev;
            }

            // Build the pin object for this node
            Map<String, Object> pin = new HashMap<>();
            pin.put("pinIndex",          i);
            pin.put("stageNumber",       i + 1);
            pin.put("stageType",         node.getStage().name());
            pin.put("stageLabel",        node.getStage().getLabel());
            pin.put("location",          node.getLocation());
            pin.put("personName",        node.getPersonName());
            pin.put("date",              node.getStageDate().toString());
            pin.put("weightInCarats",    node.getWeightInCarats());
            pin.put("priceInRupees",     node.getPriceInRupees());
            pin.put("lat",               lat);
            pin.put("lng",               lng);
            pin.put("isHead",            i == 0);
            pin.put("isTail",            i == stages.size() - 1);
            pin.put("isCurrent",         node.equals(currentNode));
            pin.put("isInternational",   isInternational);
            pin.put("pinColor",          getPinColor(node.getStage().name()));
            pin.put("pinIcon",           getPinIcon(node.getStage().name()));
            pin.put("distanceFromPrev",  Math.round(distFromPrev));

            // Add optional fields if present
            if (node.getCertificateNumber() != null)
                pin.put("certificateNumber", node.getCertificateNumber());
            if (node.getFlightNumber() != null)
                pin.put("flightNumber", node.getFlightNumber());
            if (node.getDestinationCountry() != null)
                pin.put("destinationCountry", node.getDestinationCountry());
            if (node.getNotes() != null)
                pin.put("notes", node.getNotes());

            // Price change from previous stage
            if (i > 0) {
                double prevPrice = stages.get(i - 1).getPriceInRupees();
                double increase  = node.getPriceInRupees() - prevPrice;
                pin.put("priceChangeFromPrev", Math.round(increase));
                pin.put("priceChangePercent",
                        prevPrice > 0 ? Math.round((increase / prevPrice) * 100) : 0);
            }

            // Build popup content for when user clicks the pin
            pin.put("popupContent", buildPinPopupContent(node, i + 1));

            pins.add(pin);
            routeCoords.add(new double[]{lat, lng});

            prevLat = lat;
            prevLng = lng;
        }

        // Calculate map bounds for auto-fitting the view
        Map<String, Object> bounds = calculateMapBounds(routeCoords);

        // Build route statistics
        Map<String, Object> routeStats = buildRouteStats(
                stages, totalDistance, domesticStages, internationalStages);

        // Assemble final result
        result.put("gemId",               gemId);
        result.put("gemType",             miningNode.getGemType());
        result.put("isCeylonVerified",    isCeylon);
        result.put("totalStages",         stages.size());
        result.put("pins",                pins);
        result.put("routeCoordinates",    routeCoords);
        result.put("totalDistance",       Math.round(totalDistance));
        result.put("domesticStages",      domesticStages);
        result.put("internationalStages", internationalStages);
        result.put("mapBounds",           bounds);
        result.put("routeStats",          routeStats);

        // Origin and current pin summaries
        if (!pins.isEmpty()) {
            result.put("originPin",  pins.get(0));
            result.put("currentPin", pins.get(pins.size() - 1));
        }

        // Map centre point for initial view
        result.put("mapCenter", buildMapCenter(routeCoords));

        // Backward traversal data — tail to head using prev pointers
        result.put("reverseRoute", buildReverseRoute(pins));

        return result;
    }

    // ---------------------------------------------------------
    // All gems map overview
    // ---------------------------------------------------------

    /**
     * Builds a simplified map overview for all gems in the system.
     * Returns one origin pin per gem showing where each gem was mined.
     * Used on the map overview page to show the geographic distribution
     * of all registered gems.
     *
     * @return a list of origin pin maps for all gems
     */
    public List<Map<String, Object>> buildAllGemsMapOverview() {
        List<String> allIds = trackingService.getAllGemIds();
        List<Map<String, Object>> overview = new ArrayList<>();

        for (String gemId : allIds) {
            try {
                GemLinkedList list = trackingService.getGemList(gemId);
                if (list == null) continue;

                GemNode mining  = list.getMiningNode();
                GemNode current = list.getCurrentStageNode();
                if (mining == null) continue;

                double[] coords = resolveCoordinates(mining.getLocation());
                boolean isCeylon = originVerifier.verifyGemOrigin(gemId);

                Map<String, Object> pin = new HashMap<>();
                pin.put("gemId",          gemId);
                pin.put("gemType",        mining.getGemType());
                pin.put("originLocation", mining.getLocation());
                pin.put("miner",          mining.getPersonName());
                pin.put("miningDate",     mining.getStageDate().toString());
                pin.put("lat",            coords[0]);
                pin.put("lng",            coords[1]);
                pin.put("isCeylonVerified", isCeylon);
                pin.put("totalStages",    list.getSize());
                pin.put("currentStage",   current != null
                        ? current.getStage().getLabel() : "Unknown");
                pin.put("currentPrice",   current != null
                        ? current.getPriceInRupees() : 0);
                pin.put("pinColor",       isCeylon ? "#166534" : "#991B1B");
                pin.put("popupContent",   buildOverviewPopup(gemId, mining,
                        current, isCeylon, list.getSize()));

                overview.add(pin);
            } catch (Exception e) {
                System.out.println("  Warning: Could not build map pin for: " + gemId);
            }
        }

        return overview;
    }

    /**
     * Returns the list of all known Sri Lankan gem locations
     * with their GPS coordinates. Used to populate the map
     * with reference location markers.
     *
     * @return a list of location reference maps
     */
    public List<Map<String, Object>> getKnownLocations() {
        List<Map<String, Object>> locations = new ArrayList<>();

        // Only return Sri Lankan locations (lat > 5.5 and lat < 10.0)
        for (Map.Entry<String, double[]> entry : KNOWN_LOCATIONS.entrySet()) {
            double lat = entry.getValue()[0];
            double lng = entry.getValue()[1];

            // Filter to Sri Lanka only
            if (lat >= 5.5 && lat <= 10.0 && lng >= 79.5 && lng <= 82.0) {
                Map<String, Object> loc = new HashMap<>();
                loc.put("name", capitalise(entry.getKey()));
                loc.put("lat",  lat);
                loc.put("lng",  lng);
                locations.add(loc);
            }
        }

        return locations;
    }

    // ---------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------

    /**
     * Resolves GPS coordinates for a location string by matching
     * against the KNOWN_LOCATIONS lookup table.
     *
     * Performs a case-insensitive partial match — if any known location
     * name is found within the location string, its coordinates are used.
     * Falls back to the centre of Sri Lanka if no match is found.
     *
     * @param location the location string from a GemNode
     * @return a double array [latitude, longitude]
     */
    private double[] resolveCoordinates(String location) {
        if (location == null || location.trim().isEmpty()) {
            return new double[]{DEFAULT_LAT, DEFAULT_LNG};
        }

        String lower = location.toLowerCase().trim();

        // Try to match against known locations
        for (Map.Entry<String, double[]> entry : KNOWN_LOCATIONS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // No match found — return Sri Lanka centre
        return new double[]{DEFAULT_LAT, DEFAULT_LNG};
    }

    /**
     * Determines if a location is international (outside Sri Lanka).
     * Checks if any known international city name appears in the string.
     * Used to count domestic vs international stages in route stats.
     *
     * @param location the location string to check
     * @return true if the location appears to be outside Sri Lanka
     */
    private boolean isInternationalLocation(String location) {
        if (location == null) return false;
        String lower = location.toLowerCase();

        String[] internationalKeywords = {
            "dubai", "hong kong", "singapore", "london", "new york",
            "tokyo", "bangkok", "mumbai", "paris", "amsterdam",
            "zurich", "geneva", "antwerp", "tel aviv", "jeddah",
            "doha", "abu dhabi", "kuala lumpur", "sydney", "beijing",
            "shanghai", "international", "overseas", "export"
        };

        for (String keyword : internationalKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * Calculates the great-circle distance between two GPS coordinates
     * using the Haversine formula. Returns the distance in kilometres.
     *
     * @param lat1 latitude of point 1
     * @param lng1 longitude of point 1
     * @param lat2 latitude of point 2
     * @param lng2 longitude of point 2
     * @return the distance in kilometres
     */
    private double calculateDistance(double lat1, double lng1,
                                      double lat2, double lng2) {
        final int EARTH_RADIUS_KM = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculates the bounding box for a list of route coordinates.
     * Used to auto-fit the map view to show all pins.
     *
     * @param coords the list of [lat, lng] coordinate arrays
     * @return a map with minLat, maxLat, minLng, maxLng
     */
    private Map<String, Object> calculateMapBounds(List<double[]> coords) {
        if (coords.isEmpty()) {
            return new HashMap<String, Object>() {{
                put("minLat", 5.5);  put("maxLat", 10.0);
                put("minLng", 79.5); put("maxLng", 82.0);
            }};
        }

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;

        for (double[] coord : coords) {
            minLat = Math.min(minLat, coord[0]);
            maxLat = Math.max(maxLat, coord[0]);
            minLng = Math.min(minLng, coord[1]);
            maxLng = Math.max(maxLng, coord[1]);
        }

        // Add padding to bounds
        double latPad = Math.max((maxLat - minLat) * 0.2, 0.5);
        double lngPad = Math.max((maxLng - minLng) * 0.2, 0.5);

        Map<String, Object> bounds = new HashMap<>();
        bounds.put("minLat", Math.round((minLat - latPad) * 10000.0) / 10000.0);
        bounds.put("maxLat", Math.round((maxLat + latPad) * 10000.0) / 10000.0);
        bounds.put("minLng", Math.round((minLng - lngPad) * 10000.0) / 10000.0);
        bounds.put("maxLng", Math.round((maxLng + lngPad) * 10000.0) / 10000.0);
        return bounds;
    }

    /**
     * Calculates the geographic centre point of the route.
     * Used to set the initial map view centre.
     *
     * @param coords the list of [lat, lng] coordinate arrays
     * @return a map with lat and lng for the centre point
     */
    private Map<String, Object> buildMapCenter(List<double[]> coords) {
        if (coords.isEmpty()) {
            Map<String, Object> center = new HashMap<>();
            center.put("lat", DEFAULT_LAT);
            center.put("lng", DEFAULT_LNG);
            return center;
        }

        double sumLat = 0, sumLng = 0;
        for (double[] coord : coords) {
            sumLat += coord[0];
            sumLng += coord[1];
        }

        Map<String, Object> center = new HashMap<>();
        center.put("lat", Math.round((sumLat / coords.size()) * 10000.0) / 10000.0);
        center.put("lng", Math.round((sumLng / coords.size()) * 10000.0) / 10000.0);
        return center;
    }

    /**
     * Builds the reverse route by reversing the pins list.
     * This represents the backward traversal of the Doubly Linked List
     * using prev pointers from tail back to head.
     *
     * @param pins the forward ordered list of pin maps
     * @return a reversed list of pin maps for backward traversal
     */
    private List<Map<String, Object>> buildReverseRoute(
            List<Map<String, Object>> pins) {
        List<Map<String, Object>> reverse = new ArrayList<>(pins);
        java.util.Collections.reverse(reverse);

        // Update pin index for the reversed list
        for (int i = 0; i < reverse.size(); i++) {
            Map<String, Object> pin = new HashMap<>(reverse.get(i));
            pin.put("reversePinIndex", i);
            reverse.set(i, pin);
        }

        return reverse;
    }

    /**
     * Builds the route statistics summary for a gem journey.
     * Includes distance, stage counts, unique locations, and
     * the longest single leg of the journey.
     *
     * @param stages            the ordered list of stage nodes
     * @param totalDistance     total route distance in km
     * @param domesticStages    count of Sri Lankan stages
     * @param internationalStages count of international stages
     * @return a map of route statistics
     */
    private Map<String, Object> buildRouteStats(List<GemNode> stages,
                                                 double totalDistance,
                                                 int domesticStages,
                                                 int internationalStages) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalDistanceKm",      Math.round(totalDistance));
        stats.put("domesticStages",        domesticStages);
        stats.put("internationalStages",   internationalStages);
        stats.put("totalStages",           stages.size());

        // Count unique locations
        java.util.Set<String> uniqueLocations = new java.util.HashSet<>();
        for (GemNode node : stages) {
            if (node.getLocation() != null) {
                uniqueLocations.add(node.getLocation().toLowerCase().trim());
            }
        }
        stats.put("uniqueLocations", uniqueLocations.size());

        // Find the journey duration in days
        if (stages.size() >= 2) {
            GemNode first = stages.get(0);
            GemNode last  = stages.get(stages.size() - 1);
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    first.getStageDate(), last.getStageDate());
            stats.put("journeyDays", days);
        } else {
            stats.put("journeyDays", 0);
        }

        // Total price appreciation across journey
        if (stages.size() >= 2) {
            double firstPrice = stages.get(0).getPriceInRupees();
            double lastPrice  = stages.get(stages.size() - 1).getPriceInRupees();
            stats.put("totalPriceAppreciation",
                    Math.round(lastPrice - firstPrice));
            stats.put("appreciationPercent",
                    firstPrice > 0
                    ? Math.round(((lastPrice - firstPrice) / firstPrice) * 100)
                    : 0);
        }

        return stats;
    }

    /**
     * Builds the HTML popup content string for a map pin.
     * This is the text shown when a user clicks on a stage pin.
     *
     * @param node        the GemNode for this pin
     * @param stageNumber the stage number in the list
     * @return a formatted popup content string
     */
    private String buildPinPopupContent(GemNode node, int stageNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("Stage ").append(stageNumber).append(": ")
          .append(node.getStage().getLabel()).append("\n");
        sb.append("Location: ").append(node.getLocation()).append("\n");
        sb.append("Person: ").append(node.getPersonName()).append("\n");
        sb.append("Date: ").append(node.getStageDate()).append("\n");
        sb.append("Weight: ").append(node.getWeightInCarats()).append(" ct\n");
        sb.append("Price: Rs. ")
          .append(String.format("%,.0f", node.getPriceInRupees())).append("\n");

        if (node.getCertificateNumber() != null)
            sb.append("Cert: ").append(node.getCertificateNumber()).append("\n");
        if (node.getFlightNumber() != null)
            sb.append("Flight: ").append(node.getFlightNumber()).append("\n");

        return sb.toString().trim();
    }

    /**
     * Builds a brief popup content string for the overview map.
     *
     * @param gemId    the gem ID
     * @param mining   the mining node
     * @param current  the current stage node
     * @param isCeylon whether the gem is Ceylon verified
     * @param stages   total stage count
     * @return a formatted overview popup string
     */
    private String buildOverviewPopup(String gemId, GemNode mining,
                                       GemNode current, boolean isCeylon,
                                       int stages) {
        StringBuilder sb = new StringBuilder();
        sb.append(gemId).append("\n");
        sb.append("Type: ").append(mining.getGemType()).append("\n");
        sb.append("Origin: ").append(mining.getLocation()).append("\n");
        sb.append("Miner: ").append(mining.getPersonName()).append("\n");
        sb.append("Stages: ").append(stages).append("\n");
        sb.append("Status: ").append(isCeylon
                ? "VERIFIED CEYLON GEM" : "UNVERIFIED").append("\n");
        if (current != null) {
            sb.append("Current: ").append(current.getStage().getLabel());
        }
        return sb.toString().trim();
    }

    /**
     * Returns the pin color hex string for a stage type.
     *
     * @param stageType the stage type name
     * @return a hex color string
     */
    private String getPinColor(String stageType) {
        return STAGE_PIN_COLORS.getOrDefault(stageType, "#888888");
    }

    /**
     * Returns the pin icon identifier for a stage type.
     *
     * @param stageType the stage type name
     * @return a pin icon identifier string
     */
    private String getPinIcon(String stageType) {
        return STAGE_PIN_ICONS.getOrDefault(stageType, "default");
    }

    /**
     * Capitalises the first letter of each word in a string.
     * Used for formatting known location names for display.
     *
     * @param input the string to capitalise
     * @return the capitalised string
     */
    private String capitalise(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}