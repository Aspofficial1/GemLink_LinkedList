package ui;

import java.util.List;
import java.util.Scanner;
import service.OriginVerifier;
import service.PriceTracker;
import service.QRCodeService;
import service.TrackingService;

/**
 * MainMenu is the entry point of the user interface layer.
 * It displays the main navigation menu and routes the user
 * to the correct screen based on their selection.
 *
 * A black and white console theme is used throughout the UI.
 * This means all borders, headers, and separators are drawn
 * using plain ASCII characters so the interface looks clean
 * and professional on any terminal without needing colours.
 *
 * All user input is handled through a single Scanner instance
 * that is passed down to child screens to avoid opening
 * multiple Scanner instances on System.in which causes issues.
 *
 * The Scanner bug fix is applied by calling flushScanner()
 * before every getUserInput() call that follows a menu selection.
 * This clears any leftover newline characters in the input buffer
 * that cause input fields to be skipped automatically.
 */
public class MainMenu {

    // ---------------------------------------------------------
    // Constants - black and white theme characters
    // ---------------------------------------------------------

    /**
     * The width of all menu boxes and borders in characters.
     * All screens use this same width for visual consistency.
     */
    private static final int WIDTH = 65;

    /**
     * The horizontal border character used for top and bottom lines.
     */
    private static final char H_BORDER = '-';

    /**
     * The vertical border character used for left and right sides.
     */
    private static final char V_BORDER = '|';

    /**
     * The corner character used at all four corners of menu boxes.
     */
    private static final char CORNER = '+';

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService handles all gem registration and stage
     * operations. Instantiated once here and shared with all screens.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier handles all Ceylon origin verification
     * and fraud alert generation.
     */
    private OriginVerifier originVerifier;

    /**
     * The PriceTracker handles all price appreciation analysis
     * and stage-by-stage value reporting.
     */
    private PriceTracker priceTracker;

    /**
     * The QRCodeService handles QR code generation and management
     * for each registered gem.
     */
    private QRCodeService qrCodeService;

    /**
     * The GemForm handles all user input forms for registering
     * new gems and adding new stages.
     */
    private GemForm gemForm;

    /**
     * The JourneyViewer handles all gem journey display screens.
     */
    private JourneyViewer journeyViewer;

    /**
     * The shared Scanner instance used for all console input.
     * Shared across all screens to prevent multiple Scanner conflicts.
     */
    private Scanner scanner;

    /**
     * Controls the main menu loop.
     * Set to false when the user selects exit to stop the loop.
     */
    private boolean running;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new MainMenu and initialises all service and UI layers.
     * All dependencies are created here and injected into child screens
     * so every part of the application shares the same service instances.
     */
    public MainMenu() {
        this.scanner         = new Scanner(System.in);
        this.trackingService = new TrackingService();
        this.originVerifier  = new OriginVerifier(trackingService);
        this.priceTracker    = new PriceTracker(trackingService);
        this.qrCodeService   = new QRCodeService(trackingService);
        this.gemForm         = new GemForm(trackingService, scanner);
        this.journeyViewer   = new JourneyViewer(
                trackingService, originVerifier,
                priceTracker, qrCodeService, scanner);
        this.running         = true;
    }

    // ---------------------------------------------------------
    // Main menu loop
    // ---------------------------------------------------------

    /**
     * Starts the main menu loop.
     * Displays the welcome screen and then repeatedly shows the
     * main menu until the user chooses to exit.
     * This method is called from Main.java to launch the application.
     */
    public void start() {
        displayWelcomeScreen();

        while (running) {
            displayMainMenu();
            String choice = getUserInput("  Enter your choice");

            switch (choice.trim()) {
                case "1":
                    handleRegisterNewGem();
                    break;
                case "2":
                    handleAddStageToGem();
                    break;
                case "3":
                    handleViewGemJourney();
                    break;
                case "4":
                    handleSearchGems();
                    break;
                case "5":
                    handleOriginVerification();
                    break;
                case "6":
                    handlePriceAnalysis();
                    break;
                case "7":
                    handleQRCodeManagement();
                    break;
                case "8":
                    handleViewAlerts();
                    break;
                case "9":
                    handleStatisticsDashboard();
                    break;
                case "0":
                    handleExit();
                    break;
                default:
                    displayError("Invalid choice. Please enter a number from 0 to 9.");
                    pauseForUser();
                    break;
            }
        }
    }

    // ---------------------------------------------------------
    // Welcome screen
    // ---------------------------------------------------------

    /**
     * Displays the application welcome screen with the project title,
     * institution name, and module details.
     * Shown once when the application first starts.
     */
    private void displayWelcomeScreen() {
        clearScreen();
        printTopBorder();
        printEmptyLine();
        printCentred("GEM ORIGIN TRACKING SYSTEM");
        printEmptyLine();
        printCentred("Ceylon Gem Digital Passport");
        printEmptyLine();
        printDivider();
        printEmptyLine();
        printCentred("National Institute of Business Management");
        printCentred("School of Computing and Engineering");
        printEmptyLine();
        printCentred("HND Software Engineering");
        printCentred("Programming Data Structures and Algorithms");
        printEmptyLine();
        printDivider();
        printEmptyLine();
        printCentred("Protecting the Ceylon Gem Industry");
        printCentred("Using Doubly Linked List Data Structure");
        printEmptyLine();
        printBottomBorder();
        System.out.println();
        pauseForUser();
    }

    // ---------------------------------------------------------
    // Main menu display
    // ---------------------------------------------------------

    /**
     * Displays the main navigation menu with all available options.
     * The menu is drawn using the black and white border theme.
     * Each option is numbered for easy keyboard selection.
     */
    private void displayMainMenu() {
        clearScreen();
        printTopBorder();
        printEmptyLine();
        printCentred("MAIN MENU");
        printEmptyLine();
        printDivider();
        printEmptyLine();
        printMenuItem("1", "Register New Gem");
        printMenuItem("2", "Add Stage to Existing Gem");
        printMenuItem("3", "View Gem Journey");
        printMenuItem("4", "Search Gems");
        printEmptyLine();
        printDivider();
        printEmptyLine();
        printCentred("-- Novel Features --");
        printEmptyLine();
        printMenuItem("5", "Origin Verification and Alert System");
        printMenuItem("6", "Price Appreciation Tracker");
        printMenuItem("7", "QR Code Management");
        printEmptyLine();
        printDivider();
        printEmptyLine();
        printMenuItem("8", "View Fraud Alerts");
        printMenuItem("9", "Statistics Dashboard");
        printEmptyLine();
        printDivider();
        printEmptyLine();
        printMenuItem("0", "Exit");
        printEmptyLine();
        printBottomBorder();
        System.out.println();
    }

    // ---------------------------------------------------------
    // Menu handlers
    // ---------------------------------------------------------

    /**
     * Handles option 1 - Register a new gem.
     * Delegates to GemForm which collects all mining details
     * from the user and calls TrackingService to register the gem.
     */
    private void handleRegisterNewGem() {
        clearScreen();
        printScreenHeader("REGISTER NEW GEM");
        gemForm.showRegisterGemForm();
        pauseForUser();
    }

    /**
     * Handles option 2 - Add a stage to an existing gem.
     * flushScanner() is called before getUserInput() to clear
     * any leftover newline from the previous menu selection
     * that would otherwise cause the input field to be skipped.
     */
    private void handleAddStageToGem() {
        clearScreen();
        printScreenHeader("ADD STAGE TO GEM");
        displayAllGemIds();

        // Flush leftover newline from previous menu selection
        flushScanner();

        String gemId = getUserInput("  Enter Gem ID to add stage to");
        if (gemId.trim().isEmpty()) {
            displayError("Gem ID cannot be empty.");
            pauseForUser();
            return;
        }

        gemForm.showAddStageForm(gemId.trim());
        pauseForUser();
    }

    /**
     * Handles option 3 - View a gem's full journey.
     * flushScanner() is called before getUserInput() to clear
     * any leftover newline from the previous menu selection.
     */
    private void handleViewGemJourney() {
        clearScreen();
        printScreenHeader("VIEW GEM JOURNEY");
        displayAllGemIds();

        // Flush leftover newline from previous menu selection
        flushScanner();

        String gemId = getUserInput("  Enter Gem ID to view");
        if (gemId.trim().isEmpty()) {
            displayError("Gem ID cannot be empty.");
            pauseForUser();
            return;
        }

        journeyViewer.showJourneyMenu(gemId.trim());
    }

    /**
     * Handles option 4 - Search gems by different criteria.
     * Shows a sub-menu of search options and routes accordingly.
     */
    private void handleSearchGems() {
        clearScreen();
        printScreenHeader("SEARCH GEMS");
        printTopBorder();
        printEmptyLine();
        printCentred("Search Options");
        printEmptyLine();
        printMenuItem("1", "Search by Gem Type");
        printMenuItem("2", "Search by Origin District");
        printMenuItem("3", "View All Gems");
        printMenuItem("4", "View Ceylon Verified Gems Only");
        printMenuItem("0", "Back to Main Menu");
        printEmptyLine();
        printBottomBorder();
        System.out.println();

        String choice = getUserInput("  Enter your choice");

        switch (choice.trim()) {
            case "1":
                handleSearchByType();
                break;
            case "2":
                handleSearchByDistrict();
                break;
            case "3":
                handleViewAllGems();
                break;
            case "4":
                handleViewCeylonGems();
                break;
            case "0":
                break;
            default:
                displayError("Invalid choice.");
                pauseForUser();
                break;
        }
    }

    /**
     * Handles searching gems by their type.
     * flushScanner() clears the buffer before reading the gem type
     * so the input field is not skipped after the sub-menu selection.
     */
    private void handleSearchByType() {
        clearScreen();
        printScreenHeader("SEARCH BY GEM TYPE");

        System.out.println("  Common gem types: Blue Sapphire, Pink Sapphire,");
        System.out.println("                    Ruby, Cat's Eye, Alexandrite");
        System.out.println();

        // Flush leftover newline from sub-menu selection
        flushScanner();

        String gemType = getUserInput("  Enter gem type to search");
        if (gemType.trim().isEmpty()) {
            displayError("Gem type cannot be empty.");
            pauseForUser();
            return;
        }

        List<String> results = trackingService.searchByGemType(gemType.trim());
        displaySearchResults(results, "Gems matching type: " + gemType);
        pauseForUser();
    }

    /**
     * Handles searching gems by their origin district.
     * flushScanner() clears the buffer before reading the district
     * so the input field is not skipped after the sub-menu selection.
     */
    private void handleSearchByDistrict() {
        clearScreen();
        printScreenHeader("SEARCH BY DISTRICT");

        originVerifier.displayValidLocations();
        System.out.println();

        // Flush leftover newline from sub-menu selection
        flushScanner();

        String district = getUserInput("  Enter district to search");
        if (district.trim().isEmpty()) {
            displayError("District cannot be empty.");
            pauseForUser();
            return;
        }

        List<String> results = trackingService.searchByDistrict(district.trim());
        displaySearchResults(results, "Gems from district: " + district);
        pauseForUser();
    }

    /**
     * Handles viewing all gems registered in the system.
     * Displays a summary of each gem.
     */
    private void handleViewAllGems() {
        clearScreen();
        printScreenHeader("ALL REGISTERED GEMS");

        List<String> allIds = trackingService.getAllGemIds();
        if (allIds.isEmpty()) {
            displayInfo("No gems have been registered yet.");
        } else {
            System.out.println("  Total gems registered: " + allIds.size());
            System.out.println();
            for (String gemId : allIds) {
                printDivider();
                trackingService.displayGemSummary(gemId);
            }
            printDivider();
        }

        pauseForUser();
    }

    /**
     * Handles viewing only Ceylon verified gems.
     * Filters the list to show only gems that passed origin verification.
     */
    private void handleViewCeylonGems() {
        clearScreen();
        printScreenHeader("CEYLON VERIFIED GEMS");

        List<String> ceylonIds = trackingService.getCeylonVerifiedGems();
        if (ceylonIds.isEmpty()) {
            displayInfo("No Ceylon verified gems found.");
        } else {
            System.out.println("  Verified Ceylon gems: " + ceylonIds.size());
            System.out.println();
            for (String gemId : ceylonIds) {
                printDivider();
                trackingService.displayGemSummary(gemId);
            }
            printDivider();
        }

        pauseForUser();
    }

    /**
     * Handles option 5 - Origin verification and alert system.
     * flushScanner() is called before each Gem ID input field
     * to prevent the field from being skipped after sub-menu selection.
     * This is novel feature 3.
     */
    private void handleOriginVerification() {
        clearScreen();
        printScreenHeader("ORIGIN VERIFICATION AND ALERT SYSTEM");
        printTopBorder();
        printEmptyLine();
        printCentred("Verification Options");
        printEmptyLine();
        printMenuItem("1", "Verify Single Gem Origin");
        printMenuItem("2", "Run Full Authentication");
        printMenuItem("3", "Verify Certificate Presence");
        printMenuItem("4", "Verify All Gems in System");
        printMenuItem("5", "View Valid Ceylon Locations");
        printMenuItem("0", "Back to Main Menu");
        printEmptyLine();
        printBottomBorder();
        System.out.println();

        String choice = getUserInput("  Enter your choice");

        switch (choice.trim()) {
            case "1":
                clearScreen();
                printScreenHeader("VERIFY GEM ORIGIN");
                displayAllGemIds();
                flushScanner();
                String gemId1 = getUserInput("  Enter Gem ID to verify");
                if (!gemId1.trim().isEmpty()) {
                    System.out.println();
                    originVerifier.verifyGemOrigin(gemId1.trim());
                }
                pauseForUser();
                break;

            case "2":
                clearScreen();
                printScreenHeader("FULL AUTHENTICATION");
                displayAllGemIds();
                flushScanner();
                String gemId2 = getUserInput("  Enter Gem ID for full authentication");
                if (!gemId2.trim().isEmpty()) {
                    System.out.println();
                    originVerifier.runFullAuthentication(gemId2.trim());
                }
                pauseForUser();
                break;

            case "3":
                clearScreen();
                printScreenHeader("CERTIFICATE VERIFICATION");
                displayAllGemIds();
                flushScanner();
                String gemId3 = getUserInput("  Enter Gem ID to check certificate");
                if (!gemId3.trim().isEmpty()) {
                    System.out.println();
                    originVerifier.verifyCertificatePresence(gemId3.trim());
                }
                pauseForUser();
                break;

            case "4":
                clearScreen();
                printScreenHeader("VERIFY ALL GEMS");
                System.out.println();
                int failCount = originVerifier.verifyAllGems();
                System.out.println();
                if (failCount == 0) {
                    displayInfo("All gems passed origin verification.");
                } else {
                    displayError(failCount + " gem(s) failed origin verification.");
                }
                pauseForUser();
                break;

            case "5":
                clearScreen();
                printScreenHeader("VALID CEYLON LOCATIONS");
                System.out.println();
                originVerifier.displayValidLocations();
                pauseForUser();
                break;

            case "0":
                break;

            default:
                displayError("Invalid choice.");
                pauseForUser();
                break;
        }
    }

    /**
     * Handles option 6 - Price appreciation tracker.
     * flushScanner() is called before each Gem ID input field
     * to prevent the field from being skipped after sub-menu selection.
     * This is novel feature 2.
     */
    private void handlePriceAnalysis() {
        clearScreen();
        printScreenHeader("PRICE APPRECIATION TRACKER");
        printTopBorder();
        printEmptyLine();
        printCentred("Price Analysis Options");
        printEmptyLine();
        printMenuItem("1", "View Full Price Report for a Gem");
        printMenuItem("2", "View Stage Price Summary");
        printMenuItem("3", "Compare Two Gems");
        printMenuItem("4", "View Weight Loss Analysis");
        printMenuItem("0", "Back to Main Menu");
        printEmptyLine();
        printBottomBorder();
        System.out.println();

        String choice = getUserInput("  Enter your choice");

        switch (choice.trim()) {
            case "1":
                clearScreen();
                printScreenHeader("FULL PRICE REPORT");
                displayAllGemIds();
                flushScanner();
                String gemId1 = getUserInput("  Enter Gem ID");
                if (!gemId1.trim().isEmpty()) {
                    priceTracker.displayPriceReport(gemId1.trim());
                }
                pauseForUser();
                break;

            case "2":
                clearScreen();
                printScreenHeader("STAGE PRICE SUMMARY");
                displayAllGemIds();
                flushScanner();
                String gemId2 = getUserInput("  Enter Gem ID");
                if (!gemId2.trim().isEmpty()) {
                    String stageStr = getUserInput("  Enter stage number");
                    try {
                        int stageNum = Integer.parseInt(stageStr.trim());
                        priceTracker.displayStagePriceSummary(gemId2.trim(), stageNum);
                    } catch (NumberFormatException e) {
                        displayError("Invalid stage number.");
                    }
                }
                pauseForUser();
                break;

            case "3":
                clearScreen();
                printScreenHeader("COMPARE TWO GEMS");
                displayAllGemIds();
                flushScanner();
                String gId1 = getUserInput("  Enter first Gem ID");
                String gId2 = getUserInput("  Enter second Gem ID");
                if (!gId1.trim().isEmpty() && !gId2.trim().isEmpty()) {
                    priceTracker.compareTwoGems(gId1.trim(), gId2.trim());
                }
                pauseForUser();
                break;

            case "4":
                clearScreen();
                printScreenHeader("WEIGHT LOSS ANALYSIS");
                displayAllGemIds();
                flushScanner();
                String gemId4 = getUserInput("  Enter Gem ID");
                if (!gemId4.trim().isEmpty()) {
                    trackingService.displayWeightAnalysis(gemId4.trim());
                }
                pauseForUser();
                break;

            case "0":
                break;

            default:
                displayError("Invalid choice.");
                pauseForUser();
                break;
        }
    }

    /**
     * Handles option 7 - QR code management.
     * flushScanner() is called before each Gem ID input field
     * to prevent the field from being skipped after sub-menu selection.
     * This is novel feature 1.
     */
    private void handleQRCodeManagement() {
        clearScreen();
        printScreenHeader("QR CODE MANAGEMENT");
        printTopBorder();
        printEmptyLine();
        printCentred("QR Code Options");
        printEmptyLine();
        printMenuItem("1", "Generate QR Code for a Gem");
        printMenuItem("2", "Regenerate QR Code for a Gem");
        printMenuItem("3", "Preview QR Code Content");
        printMenuItem("4", "Generate QR Codes for All Gems");
        printMenuItem("5", "View QR Code Status for All Gems");
        printMenuItem("0", "Back to Main Menu");
        printEmptyLine();
        printBottomBorder();
        System.out.println();

        String choice = getUserInput("  Enter your choice");

        switch (choice.trim()) {
            case "1":
                clearScreen();
                printScreenHeader("GENERATE QR CODE");
                displayAllGemIds();
                flushScanner();
                String gemId1 = getUserInput("  Enter Gem ID");
                if (!gemId1.trim().isEmpty()) {
                    String path = qrCodeService.generateQRCode(gemId1.trim());
                    if (path != null) {
                        displayInfo("QR code saved to: " + path);
                    }
                }
                pauseForUser();
                break;

            case "2":
                clearScreen();
                printScreenHeader("REGENERATE QR CODE");
                displayAllGemIds();
                flushScanner();
                String gemId2 = getUserInput("  Enter Gem ID");
                if (!gemId2.trim().isEmpty()) {
                    String path = qrCodeService.regenerateQRCode(gemId2.trim());
                    if (path != null) {
                        displayInfo("QR code regenerated: " + path);
                    }
                }
                pauseForUser();
                break;

            case "3":
                clearScreen();
                printScreenHeader("PREVIEW QR CONTENT");
                displayAllGemIds();
                flushScanner();
                String gemId3 = getUserInput("  Enter Gem ID");
                if (!gemId3.trim().isEmpty()) {
                    qrCodeService.previewQRContent(gemId3.trim());
                }
                pauseForUser();
                break;

            case "4":
                clearScreen();
                printScreenHeader("GENERATE ALL QR CODES");
                System.out.println();
                int count = qrCodeService.generateQRCodesForAllGems();
                displayInfo(count + " QR code(s) generated successfully.");
                pauseForUser();
                break;

            case "5":
                clearScreen();
                printScreenHeader("QR CODE STATUS");
                System.out.println();
                qrCodeService.displayQRCodeStatus();
                pauseForUser();
                break;

            case "0":
                break;

            default:
                displayError("Invalid choice.");
                pauseForUser();
                break;
        }
    }

    /**
     * Handles option 8 - View fraud alerts.
     * Displays all unresolved alerts generated by the OriginVerifier.
     */
    private void handleViewAlerts() {
        clearScreen();
        printScreenHeader("FRAUD ALERTS");

        List<String> alerts = trackingService.getUnresolvedAlerts();

        if (alerts.isEmpty()) {
            displayInfo("No unresolved fraud alerts found.");
        } else {
            System.out.println("  Unresolved alerts: " + alerts.size());
            System.out.println();
            for (int i = 0; i < alerts.size(); i++) {
                System.out.println("  Alert " + (i + 1) + ":");
                System.out.println("  " + alerts.get(i));
                System.out.println();
            }
        }

        pauseForUser();
    }

    /**
     * Handles option 9 - Statistics dashboard.
     * Displays a summary of key metrics from the system.
     */
    private void handleStatisticsDashboard() {
        clearScreen();
        printScreenHeader("STATISTICS DASHBOARD");

        int totalGems  = trackingService.getTotalGemCount();
        int ceylonGems = trackingService.getCeylonGemCount();
        int alertCount = trackingService.getUnresolvedAlertCount();
        int nonCeylon  = totalGems - ceylonGems;

        System.out.println();
        printTopBorder();
        printEmptyLine();
        printCentred("System Statistics");
        printEmptyLine();
        printDivider();
        printEmptyLine();
        printStatRow("Total gems registered",  String.valueOf(totalGems));
        printStatRow("Verified Ceylon gems",    String.valueOf(ceylonGems));
        printStatRow("Unverified gems",         String.valueOf(nonCeylon));
        printStatRow("Unresolved fraud alerts", String.valueOf(alertCount));
        printEmptyLine();
        printDivider();
        printEmptyLine();

        if (totalGems > 0) {
            double ceylonPercent = ((double) ceylonGems / totalGems) * 100;
            printStatRow("Ceylon verification rate",
                    String.format("%.1f%%", ceylonPercent));
        }

        printEmptyLine();
        printBottomBorder();
        System.out.println();

        pauseForUser();
    }

    /**
     * Handles option 0 - Exit the application.
     * Closes the database connection and scanner before exiting
     * to prevent resource leaks and data corruption.
     */
    private void handleExit() {
        clearScreen();
        printTopBorder();
        printEmptyLine();
        printCentred("Thank you for using");
        printCentred("Gem Origin Tracking System");
        printEmptyLine();
        printCentred("Protecting Ceylon Gems");
        printEmptyLine();
        printBottomBorder();
        System.out.println();

        try {
            database.DBConnection.getInstance().closeConnection();
        } catch (Exception e) {
            System.out.println("Warning: Could not close database cleanly.");
        }

        scanner.close();
        running = false;
    }

    // ---------------------------------------------------------
    // Helper display methods
    // ---------------------------------------------------------

    /**
     * Displays all gem IDs currently registered in the system.
     * Shown before any screen that asks the user to enter a gem ID
     * so they can see which IDs are available.
     */
    private void displayAllGemIds() {
        List<String> allIds = trackingService.getAllGemIds();
        if (allIds.isEmpty()) {
            displayInfo("No gems registered yet.");
        } else {
            System.out.println("  Registered Gem IDs:");
            for (String id : allIds) {
                System.out.println("    - " + id);
            }
        }
        System.out.println();
    }

    /**
     * Displays search results as a numbered list.
     * If no results are found, a message is shown instead.
     *
     * @param results the list of gem IDs returned by a search
     * @param title   a description of what was searched
     */
    private void displaySearchResults(List<String> results, String title) {
        System.out.println();
        System.out.println("  " + title);
        System.out.println();

        if (results.isEmpty()) {
            displayInfo("No gems found matching your search.");
        } else {
            System.out.println("  Found " + results.size() + " result(s):");
            System.out.println();
            for (int i = 0; i < results.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + results.get(i));
            }
        }
    }

    // ---------------------------------------------------------
    // Scanner flush helper
    // ---------------------------------------------------------

    /**
     * Flushes any leftover characters remaining in the Scanner buffer.
     * This is called before every getUserInput() that follows a
     * numbered menu selection to prevent the input field from being
     * skipped automatically due to a leftover newline character.
     *
     * The problem occurs because scanner.nextLine() after a menu
     * choice reads the newline from pressing Enter on the menu,
     * but sometimes a residual newline remains in the buffer from
     * a previous screen's pauseForUser() or sub-menu selection.
     * Calling this method before the next input clears that residual.
     */
    private void flushScanner() {
        try {
            if (System.in.available() > 0) {
                scanner.nextLine();
            }
        } catch (Exception e) {
            // If available() is not supported on this platform
            // the flush is simply skipped without crashing
        }
    }

    // ---------------------------------------------------------
    // Black and white theme drawing methods
    // ---------------------------------------------------------

    /**
     * Prints the top border of a menu box.
     */
    private void printTopBorder() {
        System.out.println("  " + CORNER
                + String.valueOf(H_BORDER).repeat(WIDTH) + CORNER);
    }

    /**
     * Prints the bottom border of a menu box.
     */
    private void printBottomBorder() {
        System.out.println("  " + CORNER
                + String.valueOf(H_BORDER).repeat(WIDTH) + CORNER);
    }

    /**
     * Prints a horizontal divider line inside a menu box.
     */
    private void printDivider() {
        System.out.println("  " + V_BORDER
                + String.valueOf(H_BORDER).repeat(WIDTH) + V_BORDER);
    }

    /**
     * Prints an empty line inside a menu box with vertical borders.
     */
    private void printEmptyLine() {
        System.out.println("  " + V_BORDER + " ".repeat(WIDTH) + V_BORDER);
    }

    /**
     * Prints a line of text centred within the menu box.
     *
     * @param text the text to centre and print
     */
    private void printCentred(String text) {
        int totalPadding = WIDTH - text.length();
        int leftPadding  = totalPadding / 2;
        int rightPadding = totalPadding - leftPadding;
        if (leftPadding  < 0) leftPadding  = 0;
        if (rightPadding < 0) rightPadding = 0;
        System.out.println("  " + V_BORDER
                + " ".repeat(leftPadding)
                + text
                + " ".repeat(rightPadding)
                + V_BORDER);
    }

    /**
     * Prints a numbered menu item inside the menu box.
     *
     * @param number the selection number for this menu item
     * @param text   the description of the menu option
     */
    private void printMenuItem(String number, String text) {
        String item    = "  [" + number + "]  " + text;
        int    padding = WIDTH - item.length();
        if (padding < 1) padding = 1;
        System.out.println("  " + V_BORDER
                + item + " ".repeat(padding) + V_BORDER);
    }

    /**
     * Prints a statistics row with a label and value inside the box.
     *
     * @param label the name of the statistic
     * @param value the value of the statistic
     */
    private void printStatRow(String label, String value) {
        String row     = "  " + label + " : " + value;
        int    padding = WIDTH - row.length();
        if (padding < 1) padding = 1;
        System.out.println("  " + V_BORDER
                + row + " ".repeat(padding) + V_BORDER);
    }

    /**
     * Prints a screen header with the screen title centred.
     *
     * @param title the title of the current screen
     */
    private void printScreenHeader(String title) {
        printTopBorder();
        printEmptyLine();
        printCentred(title);
        printEmptyLine();
        printBottomBorder();
        System.out.println();
    }

    /**
     * Displays an error message in a clearly marked format.
     *
     * @param message the error message to display
     */
    private void displayError(String message) {
        System.out.println();
        System.out.println("  ERROR: " + message);
        System.out.println();
    }

    /**
     * Displays an informational message in a clearly marked format.
     *
     * @param message the info message to display
     */
    private void displayInfo(String message) {
        System.out.println();
        System.out.println("  INFO: " + message);
        System.out.println();
    }

    /**
     * Prompts the user for text input and returns what they typed.
     *
     * @param prompt the message shown to the user before the input field
     * @return the trimmed string entered by the user
     */
    private String getUserInput(String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine();
    }

    /**
     * Pauses execution and waits for the user to press Enter.
     * Used at the end of every screen so the user can read the
     * output before the menu refreshes and clears the screen.
     */
    private void pauseForUser() {
        System.out.println();
        System.out.print("  Press Enter to continue...");
        scanner.nextLine();
    }

    /**
     * Clears the console screen by printing multiple blank lines.
     * Cross-platform approach that works on Windows, Mac and Linux.
     */
    private void clearScreen() {
        for (int i = 0; i < 50; i++) {
            System.out.println();
        }
    }
}