package service;

import database.DBConnection;
import java.util.List;
import model.GemLinkedList;
import model.GemNode;
import model.GemStage;

/**
 * OriginVerifier is responsible for verifying whether a gemstone
 * truly originated from Sri Lanka (Ceylon).
 *
 * This is one of the three novel features of this project.
 * The alert system built into this class warns buyers when a gem's
 * recorded origin does not match any known Sri Lankan mining location,
 * helping to prevent the sale of foreign gems as genuine Ceylon gems.
 *
 * Verification works by examining the head node (MINING stage) of the
 * gem's linked list, because the head node always holds the original
 * mining location. This is only possible because we use a linked list
 * where the first node is always the mine of origin.
 *
 * The list of valid Ceylon mining locations is loaded from the database
 * so that new mining areas can be added without changing this code.
 */
public class OriginVerifier {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The database connection used to load Ceylon mining locations
     * and to save fraud alerts when verification fails.
     */
    private DBConnection db;

    /**
     * The TrackingService used to retrieve gem linked lists
     * when verifying by gem ID.
     */
    private TrackingService trackingService;

    /**
     * The list of valid Sri Lankan mining districts loaded from
     * the ceylon_mining_locations table in the database.
     * Stored as a field so we do not reload it on every verification call.
     */
    private List<String> validDistricts;

    /**
     * The list of valid Sri Lankan mining villages loaded from
     * the ceylon_mining_locations table in the database.
     * Used for more precise location matching beyond just the district.
     */
    private List<String> validVillages;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new OriginVerifier and loads the list of valid
     * Ceylon mining locations from the database.
     * Loading locations once at construction time is more efficient
     * than querying the database on every single verification call.
     *
     * @param trackingService the TrackingService instance to use
     *                        for retrieving gem linked lists
     */
    public OriginVerifier(TrackingService trackingService) {
        this.trackingService = trackingService;
        this.db              = DBConnection.getInstance();

        // Load valid mining locations from the database once
        loadValidLocations();
    }

    // ---------------------------------------------------------
    // Location loading
    // ---------------------------------------------------------

    /**
     * Loads the list of valid Ceylon mining districts and villages
     * from the database into memory.
     * Called once during construction to avoid repeated database reads.
     * All district and village names are stored in lowercase to allow
     * case-insensitive comparisons during verification.
     */
    private void loadValidLocations() {
        validDistricts = db.getCeylonMiningDistricts();
        validVillages  = db.getCeylonMiningVillages();

        System.out.println("💎 OriginVerifier loaded "
                + validDistricts.size() + " valid districts and "
                + validVillages.size()  + " valid villages.");
    }

    /**
     * Reloads the valid location lists from the database.
     * Called if new mining locations have been added to the database
     * during the current session and the verifier needs to be refreshed.
     */
    public void refreshValidLocations() {
        loadValidLocations();
        System.out.println("💎 OriginVerifier location data refreshed.");
    }

    // ---------------------------------------------------------
    // Primary verification method
    // ---------------------------------------------------------

    /**
     * Verifies whether a gem is a genuine Ceylon gem by checking
     * the mining location recorded in the head node of its linked list.
     *
     * The verification process works in three steps:
     * Step 1 - Check if the location contains a known district name.
     * Step 2 - Check if the location contains a known village name.
     * Step 3 - If neither matches, generate a fraud alert.
     *
     * The result is printed to the console and saved to the database.
     * The Ceylon status of the gem is updated in the database accordingly.
     *
     * @param gemId the ID of the gem to verify
     * @return true if the gem is verified as a Ceylon gem, false otherwise
     */
    public boolean verifyGemOrigin(String gemId) {
        System.out.println();
        System.out.println("💎 Starting origin verification for Gem ID: " + gemId);
        System.out.println();

        // Retrieve the gem's linked list to access the mining node
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            System.out.println("  Verification failed: Gem not found in system.");
            return false;
        }

        // The head node of the linked list is always the MINING stage
        // This is why the linked list structure is so important here -
        // we can always trust that head = origin, regardless of how many
        // stages have been added after it
        GemNode miningNode = list.getMiningNode();
        if (miningNode == null) {
            System.out.println("  Verification failed: No mining stage found for gem.");
            generateAlert(gemId, "MISSING_MINING_STAGE",
                    "This gem has no mining stage recorded. Origin cannot be verified.");
            return false;
        }

        // Verify that the mining node is actually tagged as MINING
        if (miningNode.getStage() != GemStage.MINING) {
            System.out.println("  Verification warning: First stage is not MINING.");
        }

        // Get the recorded location from the mining node
        String recordedLocation = miningNode.getLocation();
        if (recordedLocation == null || recordedLocation.trim().isEmpty()) {
            System.out.println("  Verification failed: Mining location is empty.");
            generateAlert(gemId, "EMPTY_LOCATION",
                    "The mining location field is empty. Origin cannot be verified.");
            db.updateCeylonStatus(gemId, false);
            return false;
        }

        System.out.println("  Recorded mining location : " + recordedLocation);
        System.out.println("  Gem type                 : " + miningNode.getGemType());
        System.out.println("  Miner                    : " + miningNode.getPersonName());
        System.out.println("  Mining date              : " + miningNode.getStageDate());
        System.out.println();

        // Perform the location check against known Ceylon mining areas
        boolean isVerified = checkLocationAgainstCeylonAreas(recordedLocation);

        if (isVerified) {
            // Gem passed verification - update its Ceylon status in the database
            db.updateCeylonStatus(gemId, true);
            printVerificationSuccess(gemId, recordedLocation);
        } else {
            // Gem failed verification - save an alert and update status
            db.updateCeylonStatus(gemId, false);
            String alertMessage = "WARNING: The recorded origin '"
                    + recordedLocation
                    + "' does not match any known Sri Lankan mining location. "
                    + "This gem may not be a genuine Ceylon gem.";
            generateAlert(gemId, "ORIGIN_MISMATCH", alertMessage);
            printVerificationFailure(gemId, recordedLocation);
        }

        return isVerified;
    }

    // ---------------------------------------------------------
    // Location matching
    // ---------------------------------------------------------

    /**
     * Checks whether a given location string matches any known
     * Sri Lankan mining district or village.
     *
     * The check is case-insensitive and uses a contains comparison
     * so that a location like "Pelmadulla, Ratnapura" will match
     * the district "Ratnapura" even though it includes extra text.
     *
     * @param location the recorded mining location string to check
     * @return true if the location matches a known Ceylon mining area
     */
    private boolean checkLocationAgainstCeylonAreas(String location) {
        String locationLower = location.toLowerCase().trim();

        // Step 1: Check if any known district name appears in the location
        for (String district : validDistricts) {
            if (locationLower.contains(district.toLowerCase())) {
                System.out.println("  District match found: " + district);
                return true;
            }
        }

        // Step 2: Check if any known village name appears in the location
        for (String village : validVillages) {
            if (village != null && locationLower.contains(village.toLowerCase())) {
                System.out.println("  Village match found: " + village);
                return true;
            }
        }

        // Step 3: No match found in either district or village lists
        System.out.println("  No match found in known Ceylon mining locations.");
        return false;
    }

    /**
     * Verifies a gem using a GemLinkedList object directly
     * instead of looking it up by gem ID.
     * Used when the list is already in memory and a re-lookup
     * from the database would be unnecessary.
     *
     * @param list the GemLinkedList to verify
     * @return true if the gem is verified as a Ceylon gem, false otherwise
     */
    public boolean verifyGemOriginFromList(GemLinkedList list) {
        if (list == null) return false;
        return verifyGemOrigin(list.getGemId());
    }

    // ---------------------------------------------------------
    // Quick check methods
    // ---------------------------------------------------------

    /**
     * Performs a quick location check without saving any alerts
     * or updating the database.
     * Used for pre-validation before a full verification is triggered,
     * for example when a user is entering mining details in the form.
     *
     * @param location the location string to check
     * @return true if the location matches a Ceylon mining area
     */
    public boolean quickLocationCheck(String location) {
        if (location == null || location.trim().isEmpty()) return false;
        return checkLocationAgainstCeylonAreas(location);
    }

    /**
     * Returns a verification status label for a given gem ID.
     * Used in the UI to display a simple text badge next to a gem.
     * Returns one of three possible strings:
     * "VERIFIED CEYLON GEM", "NOT VERIFIED", or "UNVERIFIED - NO DATA".
     *
     * @param gemId the ID of the gem to check
     * @return a status label string
     */
    public String getVerificationStatusLabel(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) return "UNVERIFIED - NO DATA";

        GemNode miningNode = list.getMiningNode();
        if (miningNode == null) return "UNVERIFIED - NO MINING STAGE";

        boolean isValid = checkLocationAgainstCeylonAreas(miningNode.getLocation());
        return isValid ? "VERIFIED CEYLON GEM" : "NOT VERIFIED - ORIGIN MISMATCH";
    }

    /**
     * Checks whether a gem's current stage location matches
     * where it should be based on its destination record.
     * Used to detect if a gem has deviated from its expected route.
     *
     * @param gemId the ID of the gem to check
     * @return true if the current location is consistent with records
     */
    public boolean checkCurrentLocationConsistency(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) return false;

        GemNode currentNode = list.getCurrentStageNode();
        if (currentNode == null) return false;

        // Check if an EXPORTING node exists with a destination
        GemNode exportNode = list.findByStage(GemStage.EXPORTING);
        if (exportNode == null) {
            // No export stage means gem is still in Sri Lanka - that is fine
            return true;
        }

        // If the gem has been exported, the current location should not
        // be a Sri Lankan mining area anymore
        if (currentNode.getStage() == GemStage.BUYING) {
            String buyerLocation = currentNode.getLocation();
            if (buyerLocation != null
                    && checkLocationAgainstCeylonAreas(buyerLocation)) {
                System.out.println("  Location inconsistency: Gem is marked as sold"
                        + " abroad but buyer location appears to be in Sri Lanka.");
                generateAlert(gemId, "LOCATION_INCONSISTENCY",
                        "Buyer location '" + buyerLocation
                        + "' appears to be in Sri Lanka but gem was exported.");
                return false;
            }
        }

        return true;
    }

    // ---------------------------------------------------------
    // Bulk verification
    // ---------------------------------------------------------

    /**
     * Verifies the origin of all gems currently loaded in the system.
     * Prints a summary report showing how many gems passed and failed.
     * Used by administrators to run a full audit of all gem records.
     *
     * @return the number of gems that failed verification
     */
    public int verifyAllGems() {
        List<String> allIds = trackingService.getAllGemIds();

        if (allIds.isEmpty()) {
            System.out.println("No gems found in the system to verify.");
            return 0;
        }

        System.out.println("💎 Starting bulk origin verification for all gems.");
        System.out.println("  Total gems to verify: " + allIds.size());
        System.out.println();

        int passCount = 0;
        int failCount = 0;

        for (String gemId : allIds) {
            boolean passed = verifyGemOrigin(gemId);
            if (passed) {
                passCount++;
            } else {
                failCount++;
            }
            System.out.println();
        }

        // Print the bulk verification summary
        System.out.println("💎 Bulk Verification Complete");
        System.out.println("  Total gems verified : " + allIds.size());
        System.out.println("  Passed              : " + passCount);
        System.out.println("  Failed              : " + failCount);

        return failCount;
    }

    // ---------------------------------------------------------
    // Certificate verification
    // ---------------------------------------------------------

    /**
     * Verifies whether a gem has a valid certificate recorded
     * at its trading or exporting stage.
     * Gems without certificates are flagged as potentially unverified.
     *
     * @param gemId the ID of the gem to check
     * @return true if a certificate is found, false otherwise
     */
    public boolean verifyCertificatePresence(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) return false;

        // Check all nodes in the list for a certificate number
        List<GemNode> allStages = list.getAllStages();
        for (GemNode node : allStages) {
            if (node.getCertificateNumber() != null
                    && !node.getCertificateNumber().trim().isEmpty()) {
                System.out.println("💎 Certificate found for Gem: " + gemId);
                System.out.println("  Certificate No : " + node.getCertificateNumber());
                System.out.println("  Issued by      : " + node.getIssuingAuthority());
                System.out.println("  At stage       : " + node.getStage().getLabel());
                return true;
            }
        }

        // No certificate found - generate a warning alert
        System.out.println("  No certificate found for Gem: " + gemId);
        generateAlert(gemId, "MISSING_CERTIFICATE",
                "No official certificate has been recorded for this gem. "
                + "Buyers should request certification before purchase.");
        return false;
    }

    /**
     * Verifies the full authenticity of a gem by running all
     * available checks in sequence:
     * 1. Origin location check
     * 2. Certificate presence check
     * 3. Current location consistency check
     *
     * Prints a final combined result at the end.
     * This is the most thorough verification available in the system.
     *
     * @param gemId the ID of the gem to fully authenticate
     * @return true if the gem passed all checks, false if any check failed
     */
    public boolean runFullAuthentication(String gemId) {
        System.out.println();
        System.out.println("💎 Running full authentication for Gem ID: " + gemId);
        System.out.println();

        boolean originOk      = verifyGemOrigin(gemId);
        boolean certificateOk = verifyCertificatePresence(gemId);
        boolean locationOk    = checkCurrentLocationConsistency(gemId);

        System.out.println();
        System.out.println("💎 Full Authentication Result for Gem ID: " + gemId);
        System.out.println("  Origin check       : " + (originOk      ? "PASSED" : "FAILED"));
        System.out.println("  Certificate check  : " + (certificateOk ? "PASSED" : "FAILED"));
        System.out.println("  Location check     : " + (locationOk    ? "PASSED" : "FAILED"));
        System.out.println();

        boolean allPassed = originOk && certificateOk && locationOk;
        if (allPassed) {
            System.out.println("  RESULT: This gem has passed all authentication checks.");
            System.out.println("          It is a VERIFIED GENUINE CEYLON GEM.");
        } else {
            System.out.println("  RESULT: This gem has FAILED one or more authentication checks.");
            System.out.println("          Buyers should exercise caution.");
        }

        return allPassed;
    }

    // ---------------------------------------------------------
    // Alert generation
    // ---------------------------------------------------------

    /**
     * Saves a fraud alert to the database and prints it to the console.
     * Called whenever a verification check fails.
     * Alerts are stored in the gem_alerts table and displayed in the
     * dashboard so administrators can review and resolve them.
     *
     * @param gemId        the ID of the gem that triggered the alert
     * @param alertType    a short code describing the type of alert
     * @param alertMessage the full human readable alert description
     */
    private void generateAlert(String gemId, String alertType, String alertMessage) {
        // Save the alert to the database for later review
        db.saveAlert(gemId, alertType, alertMessage);

        // Print the alert clearly to the console so it is immediately visible
        System.out.println();
        System.out.println("  ALERT - " + alertType);
        System.out.println("  " + alertMessage);
        System.out.println();
    }

    // ---------------------------------------------------------
    // Display helpers
    // ---------------------------------------------------------

    /**
     * Prints a formatted success message when a gem passes verification.
     * Shows the gem ID and the matched location clearly.
     *
     * @param gemId    the verified gem ID
     * @param location the location that matched a Ceylon mining area
     */
    private void printVerificationSuccess(String gemId, String location) {
        System.out.println("  RESULT: VERIFIED GENUINE CEYLON GEM");
        System.out.println("  Gem ID   : " + gemId);
        System.out.println("  Location : " + location);
        System.out.println("  Status   : Origin confirmed as Sri Lankan mining area.");
    }

    /**
     * Prints a formatted failure message when a gem fails verification.
     * Shows the gem ID and the unmatched location clearly.
     *
     * @param gemId    the unverified gem ID
     * @param location the location that did not match any Ceylon mining area
     */
    private void printVerificationFailure(String gemId, String location) {
        System.out.println("  RESULT: ORIGIN VERIFICATION FAILED");
        System.out.println("  Gem ID   : " + gemId);
        System.out.println("  Location : " + location);
        System.out.println("  Status   : Location does not match any known"
                + " Sri Lankan mining area.");
        System.out.println("  Action   : A fraud alert has been saved"
                + " and the gem is marked as unverified.");
    }

    /**
     * Prints the full list of valid Ceylon mining locations to the console.
     * Used in the UI when a user needs to see accepted location names
     * before entering mining details for a new gem.
     */
    public void displayValidLocations() {
        System.out.println("💎 Known Sri Lankan Gem Mining Locations");
        System.out.println();
        System.out.println("  Districts:");
        for (String district : validDistricts) {
            System.out.println("    - " + district);
        }
        System.out.println();
        System.out.println("  Villages:");
        for (String village : validVillages) {
            if (village != null) {
                System.out.println("    - " + village);
            }
        }
    }
}