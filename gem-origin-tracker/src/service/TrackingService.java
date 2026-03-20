package service;

import database.DBConnection;
import model.GemLinkedList;
import model.GemNode;
import model.GemStage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TrackingService is the main business logic layer of the application.
 * It acts as the bridge between the UI layer and the data layer,
 * coordinating operations between GemLinkedList and DBConnection.
 *
 * All gem registration, stage addition, journey retrieval, and
 * search operations go through this class. The UI never talks
 * directly to the database or the linked list - it always goes
 * through TrackingService to keep the code organised and testable.
 *
 * An in-memory map is used to store all active gem linked lists
 * so we do not reload from the database on every single operation.
 */
public class TrackingService {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The database connection instance used for all persistence operations.
     * Using the singleton ensures only one connection is open at a time.
     */
    private DBConnection db;

    /**
     * An in-memory map that holds all loaded GemLinkedList objects.
     * The key is the gem ID and the value is its linked list.
     * This acts as a runtime cache so we avoid repeated database reads
     * during the same session.
     */
    private Map<String, GemLinkedList> activeGems;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new TrackingService and initialises the database
     * connection and the in-memory gem map.
     * All previously saved gems are loaded into memory on startup
     * so they are immediately available for display and operations.
     */
    public TrackingService() {
        this.db         = DBConnection.getInstance();
        this.activeGems = new HashMap<>();

        // Load all existing gems from the database into memory on startup
        loadAllGemsFromDatabase();
    }

    // ---------------------------------------------------------
    // Gem registration
    // ---------------------------------------------------------

    /**
     * Registers a brand new gem into the system.
     * This is the very first operation performed when a gem is discovered.
     * It creates the first node (MINING stage), builds the linked list,
     * and saves both the gem record and the mining stage to the database.
     *
     * A unique gem ID is generated automatically based on the current
     * timestamp to ensure no two gems share the same ID.
     *
     * @param gemType         the type of gem e.g. Blue Sapphire
     * @param colorDescription hue, tone and saturation details
     * @param originMine      name of the mine where gem was found
     * @param district        district of the mine
     * @param village         village near the mine
     * @param minerName       full name of the miner
     * @param minerIdNumber   NIC number of the miner
     * @param minerContact    contact number of the miner
     * @param weightInCarats  original weight at time of discovery
     * @param priceInRupees   initial estimated value in rupees
     * @param miningDate      date the gem was extracted
     * @return the newly created GemLinkedList, or null if registration failed
     */
    public GemLinkedList registerNewGem(
            String gemType,
            String colorDescription,
            String originMine,
            String district,
            String village,
            String minerName,
            String minerIdNumber,
            String minerContact,
            double weightInCarats,
            double priceInRupees,
            LocalDate miningDate) {

        // Generate a unique gem ID using the current timestamp
        String gemId = generateGemId(gemType);

        // Check if this ID already exists, regenerate if needed
        if (db.gemExists(gemId)) {
            gemId = gemId + "-" + System.currentTimeMillis();
        }

        // Create the mining node as the first node in the linked list
        GemNode miningNode = new GemNode(
                gemId,
                gemType,
                GemStage.MINING,
                district + ", " + village,
                minerName,
                weightInCarats,
                priceInRupees,
                miningDate
        );

        // Set optional fields on the mining node
        miningNode.setPersonIdNumber(minerIdNumber);
        miningNode.setContactNumber(minerContact);
        miningNode.setNotes("Origin mine: " + originMine + " | District: " + district);

        // Create the linked list and add the mining node as the head
        GemLinkedList list = new GemLinkedList(gemId);
        list.addStage(miningNode);

        // Save the gem record to the database
        boolean gemSaved = db.saveGem(list, colorDescription, originMine, district, village);
        if (!gemSaved) {
            System.out.println("Failed to register gem. Database error on gem save.");
            return null;
        }

        // Save the mining stage node to the database
        boolean stageSaved = db.saveStage(miningNode, 1);
        if (!stageSaved) {
            System.out.println("Failed to save mining stage. Rolling back gem registration.");
            db.deleteGem(gemId);
            return null;
        }

        // Store the list in the in-memory map for immediate use
        activeGems.put(gemId, list);

        System.out.println("💎 New gem registered successfully. Gem ID: " + gemId);
        return list;
    }

    // ---------------------------------------------------------
    // Stage addition
    // ---------------------------------------------------------

    /**
     * Adds a new stage to an existing gem's journey.
     * Called each time the gem changes hands or undergoes processing.
     * Creates a new node, appends it to the linked list, and saves
     * the stage to the database.
     *
     * @param gemId          the ID of the gem to update
     * @param stage          the type of stage being added
     * @param location       where this stage is taking place
     * @param personName     the person responsible at this stage
     * @param personIdNumber their NIC or passport number
     * @param contactNumber  their contact number
     * @param weightInCarats the gem weight at this stage
     * @param priceInRupees  the gem value at this stage
     * @param stageDate      the date this stage occurred
     * @return the newly created GemNode, or null if the operation failed
     */
    public GemNode addStageToGem(
            String gemId,
            GemStage stage,
            String location,
            String personName,
            String personIdNumber,
            String contactNumber,
            double weightInCarats,
            double priceInRupees,
            LocalDate stageDate) {

        // Retrieve the gem's linked list from the in-memory map
        GemLinkedList list = getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return null;
        }

        // Create the new stage node
        GemNode newNode = new GemNode(
                gemId,
                list.getMiningNode().getGemType(),
                stage,
                location,
                personName,
                weightInCarats,
                priceInRupees,
                stageDate
        );

        // Set optional identity fields
        newNode.setPersonIdNumber(personIdNumber);
        newNode.setContactNumber(contactNumber);

        // Add the node to the end of the linked list
        list.addStage(newNode);

        // Save the new stage to the database
        // Stage order equals the current size of the list after adding
        boolean saved = db.saveStage(newNode, list.getSize());
        if (!saved) {
            System.out.println("Failed to save stage to database. Removing from list.");
            list.removeStageAt(list.getSize() - 1);
            return null;
        }

        System.out.println("💎 Stage added successfully: "
                + stage.getLabel() + " for Gem: " + gemId);
        return newNode;
    }

    /**
     * Adds export-specific details to an EXPORTING stage node.
     * Called after addStageToGem when the stage type is EXPORTING,
     * to attach the additional flight, invoice, and destination data.
     *
     * @param gemId             the ID of the gem being exported
     * @param flightNumber      the flight number for the export
     * @param invoiceNumber     the invoice number for customs
     * @param destinationCountry the country where the gem is going
     * @return true if the export details were set successfully
     */
    public boolean addExportDetails(String gemId, String flightNumber,
                                    String invoiceNumber, String destinationCountry) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return false;

        // The current tail should be the EXPORTING node just added
        GemNode exportNode = list.getCurrentStageNode();
        if (exportNode == null || exportNode.getStage() != GemStage.EXPORTING) {
            System.out.println("The latest stage is not an EXPORTING stage.");
            return false;
        }

        exportNode.setFlightNumber(flightNumber);
        exportNode.setInvoiceNumber(invoiceNumber);
        exportNode.setDestinationCountry(destinationCountry);

        System.out.println("💎 Export details added for Gem: " + gemId);
        return true;
    }

    /**
     * Adds certificate details to the current stage node.
     * Called when a gem receives official certification,
     * typically at the TRADING or EXPORTING stage.
     *
     * @param gemId               the ID of the gem
     * @param certificateNumber   the certificate number issued
     * @param issuingAuthority    the name of the certifying body
     * @return true if the certificate details were set successfully
     */
    public boolean addCertificateDetails(String gemId,
                                          String certificateNumber,
                                          String issuingAuthority) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return false;

        GemNode currentNode = list.getCurrentStageNode();
        if (currentNode == null) {
            System.out.println("No current stage found for gem: " + gemId);
            return false;
        }

        currentNode.setCertificateNumber(certificateNumber);
        currentNode.setIssuingAuthority(issuingAuthority);

        System.out.println("💎 Certificate details added for Gem: " + gemId);
        return true;
    }

    /**
     * Adds a note to the current stage node.
     * Notes provide additional context that does not fit
     * into the standard fields of a stage.
     *
     * @param gemId the ID of the gem
     * @param note  the note text to add
     * @return true if the note was set successfully
     */
    public boolean addNoteToCurrentStage(String gemId, String note) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return false;

        GemNode currentNode = list.getCurrentStageNode();
        if (currentNode == null) return false;

        currentNode.setNotes(note);
        System.out.println("💎 Note added to current stage for Gem: " + gemId);
        return true;
    }

    // ---------------------------------------------------------
    // Stage removal
    // ---------------------------------------------------------

    /**
     * Removes a stage at a specific position from a gem's journey.
     * Used by authorised users to correct wrongly entered stage data.
     * Also removes the corresponding record from the database and
     * re-saves all remaining stages with corrected order numbers.
     *
     * @param gemId    the ID of the gem
     * @param position the index of the stage to remove (0-based)
     * @return the removed GemNode, or null if the operation failed
     */
    public GemNode removeStage(String gemId, int position) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return null;

        // Remove the node from the in-memory linked list
        GemNode removed = list.removeStageAt(position);
        if (removed == null) return null;

        // Re-sync the database by deleting all stages and re-saving them
        // This ensures stage_order values remain consecutive and correct
        db.deleteAllStages(gemId);
        List<GemNode> remainingStages = list.getAllStages();
        for (int i = 0; i < remainingStages.size(); i++) {
            db.saveStage(remainingStages.get(i), i + 1);
        }

        System.out.println("💎 Stage removed at position " + position
                + " for Gem: " + gemId);
        return removed;
    }

    // ---------------------------------------------------------
    // Journey retrieval
    // ---------------------------------------------------------

    /**
     * Returns the GemLinkedList for a given gem ID.
     * First checks the in-memory map, then falls back to the database
     * if the gem is not currently loaded in memory.
     *
     * @param gemId the ID of the gem to retrieve
     * @return the GemLinkedList for the gem, or null if not found
     */
    public GemLinkedList getGemList(String gemId) {
        // Check in-memory map first to avoid unnecessary database reads
        if (activeGems.containsKey(gemId)) {
            return activeGems.get(gemId);
        }

        // Not in memory, try loading from database
        GemLinkedList list = db.loadGemJourney(gemId);
        if (list != null) {
            activeGems.put(gemId, list);
        }
        return list;
    }

    /**
     * Displays the full forward journey of a gem to the console.
     * Traverses from the mining node to the current owner node.
     * Used by JourneyViewer when a user wants to see a gem's history.
     *
     * @param gemId the ID of the gem to display
     */
    public void displayFullJourney(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return;
        }
        list.displayJourneyForward();
    }

    /**
     * Displays the reverse journey of a gem from current owner to mine.
     * Only possible because we use a doubly linked list.
     * Used when auditors need to trace a gem backwards to its origin.
     *
     * @param gemId the ID of the gem to display in reverse
     */
    public void displayReverseJourney(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return;
        }
        list.displayJourneyBackward();
    }

    /**
     * Displays a short summary of a gem's journey.
     * Shows key details without printing every stage in full.
     * Used in search results and listing screens.
     *
     * @param gemId the ID of the gem to summarise
     */
    public void displayGemSummary(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return;
        }
        list.printSummary();
    }

    /**
     * Returns the current owner node of a gem.
     * The current owner is always the tail node of the linked list.
     *
     * @param gemId the ID of the gem
     * @return the tail GemNode representing the current owner
     */
    public GemNode getCurrentOwner(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return null;
        return list.getCurrentStageNode();
    }

    /**
     * Returns the mining node of a gem.
     * The mining node is always the head node of the linked list.
     * Used by OriginVerifier to check the origin location.
     *
     * @param gemId the ID of the gem
     * @return the head GemNode representing the mining stage
     */
    public GemNode getMiningStage(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return null;
        return list.getMiningNode();
    }

    /**
     * Returns all stage nodes for a gem as a flat list.
     * Used by ReportGenerator to compile the full journey into a report.
     *
     * @param gemId the ID of the gem
     * @return a List of all GemNode objects in chronological order
     */
    public List<GemNode> getAllStages(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return new ArrayList<>();
        return list.getAllStages();
    }

    // ---------------------------------------------------------
    // Search operations
    // ---------------------------------------------------------

    /**
     * Returns all gem IDs currently registered in the system.
     * Queries the database to include gems not currently in memory.
     *
     * @return a List of all gem ID strings
     */
    public List<String> getAllGemIds() {
        return db.getAllGemIds();
    }

    /**
     * Searches for gems by their type.
     * Returns all gem IDs that match the given type string.
     *
     * @param gemType the gem type to search for
     * @return a List of matching gem ID strings
     */
    public List<String> searchByGemType(String gemType) {
        return db.searchGemsByType(gemType);
    }

    /**
     * Searches for gems by their origin district.
     * Returns all gem IDs from mines in the given district.
     *
     * @param district the district name to search for
     * @return a List of matching gem ID strings
     */
    public List<String> searchByDistrict(String district) {
        return db.searchGemsByDistrict(district);
    }

    /**
     * Searches for a specific stage within a gem's journey
     * by the name of the person at that stage.
     *
     * @param gemId      the ID of the gem to search within
     * @param personName the name to search for
     * @return the matching GemNode, or null if not found
     */
    public GemNode searchStageByPerson(String gemId, String personName) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return null;
        return list.findByPersonName(personName);
    }

    /**
     * Searches for a stage within a gem's journey by certificate number.
     * Used to verify whether a certificate belongs to a specific gem.
     *
     * @param gemId             the ID of the gem to search within
     * @param certificateNumber the certificate number to find
     * @return the matching GemNode, or null if not found
     */
    public GemNode searchStageByCertificate(String gemId, String certificateNumber) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) return null;
        return list.findByCertificateNumber(certificateNumber);
    }

    /**
     * Returns all gem IDs that have been verified as genuine Ceylon gems.
     * Used in the dashboard to show authenticated gems.
     *
     * @return a List of verified Ceylon gem IDs
     */
    public List<String> getCeylonVerifiedGems() {
        return db.getCeylonVerifiedGems();
    }

    // ---------------------------------------------------------
    // Statistics
    // ---------------------------------------------------------

    /**
     * Returns the total number of gems registered in the system.
     * Used in the statistics dashboard.
     *
     * @return total gem count
     */
    public int getTotalGemCount() {
        return db.getTotalGemCount();
    }

    /**
     * Returns the number of verified Ceylon gems.
     * Used in the statistics dashboard.
     *
     * @return Ceylon gem count
     */
    public int getCeylonGemCount() {
        return db.getCeylonGemCount();
    }

    /**
     * Returns the number of unresolved fraud alerts.
     * Used in the statistics dashboard to flag pending issues.
     *
     * @return unresolved alert count
     */
    public int getUnresolvedAlertCount() {
        return db.getUnresolvedAlertCount();
    }

    /**
     * Returns all unresolved alert messages.
     * Used in the dashboard to display pending fraud warnings.
     *
     * @return a List of unresolved alert message strings
     */
    public List<String> getUnresolvedAlerts() {
        return db.getUnresolvedAlerts();
    }

    /**
     * Calculates and displays the weight loss for a specific gem.
     * Shows original weight, current weight, and percentage lost.
     *
     * @param gemId the ID of the gem to analyse
     */
    public void displayWeightAnalysis(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return;
        }

        System.out.println("💎 Weight Analysis for Gem ID: " + gemId);

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        if (miningNode != null) {
            System.out.printf("  Original Weight  : %.4f carats%n",
                    miningNode.getWeightInCarats());
        }
        if (currentNode != null) {
            System.out.printf("  Current Weight   : %.4f carats%n",
                    currentNode.getWeightInCarats());
        }
        System.out.printf("  Weight Lost      : %.4f carats%n",
                list.calculateWeightLoss());
        System.out.printf("  Weight Loss      : %.2f%%%n",
                list.calculateWeightLossPercentage());
    }

    /**
     * Calculates and displays the price appreciation for a specific gem.
     * Shows price at each stage from mining to current owner.
     * This is the Price Appreciation Tracker novel feature.
     *
     * @param gemId the ID of the gem to analyse
     */
    public void displayPriceAppreciation(String gemId) {
        GemLinkedList list = getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return;
        }

        System.out.println("💎 Price Appreciation for Gem ID: " + gemId);
        System.out.println("  Stage-by-stage value growth:");
        System.out.println();

        List<GemNode> stages = list.getAllStages();
        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);
            System.out.printf("  Stage %d - %-30s : Rs. %,.2f%n",
                    (i + 1),
                    node.getStage().getLabel(),
                    node.getPriceInRupees());

            // Show the increase from the previous stage
            if (i > 0) {
                double increase = node.getPriceInRupees()
                        - stages.get(i - 1).getPriceInRupees();
                System.out.printf("             Value added at this stage      : Rs. %,.2f%n",
                        increase);
            }
        }

        System.out.println();
        System.out.printf("  Total Price Appreciation : Rs. %,.2f%n",
                list.calculatePriceAppreciation());
    }

    // ---------------------------------------------------------
    // Database loading
    // ---------------------------------------------------------

    /**
     * Loads all gem journeys from the database into the in-memory map.
     * Called once when TrackingService is first created, so all gems
     * are available immediately without waiting for a user search.
     */
    private void loadAllGemsFromDatabase() {
        List<String> allIds = db.getAllGemIds();
        for (String gemId : allIds) {
            GemLinkedList list = db.loadGemJourney(gemId);
            if (list != null) {
                activeGems.put(gemId, list);
            }
        }
        System.out.println("💎 Loaded " + activeGems.size()
                + " gem(s) from database into memory.");
    }

    /**
     * Reloads a specific gem's journey from the database.
     * Used to refresh the in-memory data after an external update.
     *
     * @param gemId the ID of the gem to reload
     */
    public void reloadGemFromDatabase(String gemId) {
        GemLinkedList list = db.loadGemJourney(gemId);
        if (list != null) {
            activeGems.put(gemId, list);
            System.out.println("💎 Gem reloaded from database: " + gemId);
        }
    }

    // ---------------------------------------------------------
    // Gem deletion
    // ---------------------------------------------------------

    /**
     * Deletes a gem and all its stages from the system.
     * Removes the gem from both the in-memory map and the database.
     * Should only be called by authorised administrators.
     *
     * @param gemId the ID of the gem to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteGem(String gemId) {
        activeGems.remove(gemId);
        boolean deleted = db.deleteGem(gemId);
        if (deleted) {
            System.out.println("💎 Gem fully deleted: " + gemId);
        }
        return deleted;
    }

    // ---------------------------------------------------------
    // ID generation
    // ---------------------------------------------------------

    /**
     * Generates a unique gem ID based on the gem type and current time.
     * The ID is formatted as a short type code followed by a timestamp.
     * For example, a Blue Sapphire becomes "BS-1711234567890".
     *
     * Using a timestamp ensures uniqueness even when multiple gems
     * of the same type are registered within the same session.
     *
     * @param gemType the type of gem being registered
     * @return a unique gem ID string
     */
    private String generateGemId(String gemType) {
        // Create a 2-letter code from the first letters of each word
        // in the gem type name e.g. "Blue Sapphire" becomes "BS"
        String[] words = gemType.trim().split("\\s+");
        StringBuilder code = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                code.append(Character.toUpperCase(word.charAt(0)));
            }
        }

        // Append the current timestamp to ensure uniqueness
        return code.toString() + "-" + System.currentTimeMillis();
    }

    /**
     * Returns the in-memory map of all active gem linked lists.
     * Used by the UI layer to display all loaded gems at once
     * without querying the database again.
     *
     * @return the Map of gemId to GemLinkedList
     */
    public Map<String, GemLinkedList> getActiveGems() {
        return activeGems;
    }
}