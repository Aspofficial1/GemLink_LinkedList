package ui;

import model.GemLinkedList;
import model.GemNode;
import service.OriginVerifier;
import service.PriceTracker;
import service.QRCodeService;
import service.TrackingService;

import java.util.List;
import java.util.Scanner;

/**
 * JourneyViewer handles all gem journey display screens.
 * It is responsible for showing the full journey of a gem,
 * individual stage details, origin verification results,
 * price appreciation reports, and QR code management.
 *
 * This class acts as the visual output layer for all gem
 * journey related operations. It receives data from the
 * service layer and formats it for console display using
 * the same black and white border theme as MainMenu and GemForm.
 *
 * The doubly linked list structure is visible here because
 * we offer both forward traversal (mine to buyer) and
 * backward traversal (buyer to mine) as display options.
 * Backward traversal is only possible because we use a
 * doubly linked list with prev pointers on each node.
 */
public class JourneyViewer {

    // ---------------------------------------------------------
    // Constants - black and white theme
    // ---------------------------------------------------------

    /**
     * The display width used for all borders and separators.
     * Kept consistent with MainMenu and GemForm.
     */
    private static final int WIDTH = 65;

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem journey data
     * and perform weight and price calculations.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier used to run Ceylon origin checks
     * and display verification results.
     */
    private OriginVerifier originVerifier;

    /**
     * The PriceTracker used to display price appreciation
     * reports and stage-by-stage value analysis.
     */
    private PriceTracker priceTracker;

    /**
     * The QRCodeService used to generate and manage QR codes
     * for gems viewed through the journey viewer.
     */
    private QRCodeService qrCodeService;

    /**
     * The shared Scanner instance passed from MainMenu.
     * Using the same Scanner prevents conflicts on System.in.
     */
    private Scanner scanner;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new JourneyViewer with all required service dependencies.
     * All services are injected from MainMenu so the entire application
     * shares the same service instances.
     *
     * @param trackingService the service for gem journey operations
     * @param originVerifier  the service for origin verification
     * @param priceTracker    the service for price analysis
     * @param qrCodeService   the service for QR code management
     * @param scanner         the shared console input scanner
     */
    public JourneyViewer(TrackingService trackingService,
                         OriginVerifier  originVerifier,
                         PriceTracker    priceTracker,
                         QRCodeService   qrCodeService,
                         Scanner         scanner) {
        this.trackingService = trackingService;
        this.originVerifier  = originVerifier;
        this.priceTracker    = priceTracker;
        this.qrCodeService   = qrCodeService;
        this.scanner         = scanner;
    }

    // ---------------------------------------------------------
    // Journey sub-menu
    // ---------------------------------------------------------

    /**
     * Displays the journey sub-menu for a specific gem.
     * This is the main entry point called from MainMenu when
     * a user wants to view or interact with a gem's journey.
     *
     * The sub-menu offers all viewing and analysis options
     * for the selected gem in one place.
     *
     * @param gemId the ID of the gem to view
     */
    public void showJourneyMenu(String gemId) {
        // Verify the gem exists before showing the menu
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            printError("Gem not found: " + gemId);
            pauseForUser();
            return;
        }

        boolean inSubMenu = true;

        while (inSubMenu) {
            clearScreen();
            printScreenHeader("GEM JOURNEY VIEWER");
            printGemBadge(list);

            System.out.println();
            printTopBorder();
            printEmptyLine();
            printCentred("View Options for Gem: " + gemId);
            printEmptyLine();
            printMenuItem("1", "View Full Journey (Mine to Current Owner)");
            printMenuItem("2", "View Reverse Journey (Current Owner to Mine)");
            printMenuItem("3", "View Gem Summary");
            printMenuItem("4", "View Specific Stage Details");
            printEmptyLine();
            printDivider();
            printEmptyLine();
            printCentred("-- Analysis Options --");
            printEmptyLine();
            printMenuItem("5", "Run Origin Verification");
            printMenuItem("6", "View Price Appreciation Report");
            printMenuItem("7", "View Weight Loss Analysis");
            printEmptyLine();
            printDivider();
            printEmptyLine();
            printCentred("-- QR Code Options --");
            printEmptyLine();
            printMenuItem("8", "Generate QR Code for this Gem");
            printMenuItem("9", "Preview QR Code Content");
            printEmptyLine();
            printDivider();
            printEmptyLine();
            printMenuItem("0", "Back to Main Menu");
            printEmptyLine();
            printBottomBorder();
            System.out.println();

            String choice = getUserInput("  Enter your choice");

            switch (choice.trim()) {
                case "1":
                    showFullJourneyForward(gemId);
                    break;
                case "2":
                    showFullJourneyBackward(gemId);
                    break;
                case "3":
                    showGemSummary(gemId);
                    break;
                case "4":
                    showSpecificStage(gemId);
                    break;
                case "5":
                    showOriginVerification(gemId);
                    break;
                case "6":
                    showPriceReport(gemId);
                    break;
                case "7":
                    showWeightAnalysis(gemId);
                    break;
                case "8":
                    showGenerateQRCode(gemId);
                    break;
                case "9":
                    showQRCodePreview(gemId);
                    break;
                case "0":
                    inSubMenu = false;
                    break;
                default:
                    printError("Invalid choice. Please enter 0 to 9.");
                    pauseForUser();
                    break;
            }
        }
    }

    // ---------------------------------------------------------
    // Journey display screens
    // ---------------------------------------------------------

    /**
     * Displays the full gem journey in forward order.
     * Traverses from the head node (MINING) to the tail node
     * (current owner) using the next pointer of each node.
     *
     * Each stage is displayed in a formatted box with all
     * available fields shown, including optional fields that
     * are only shown if they have been set for that node.
     *
     * A chain diagram is drawn between stages using arrow
     * characters to visually represent the linked list structure.
     *
     * @param gemId the ID of the gem to display
     */
    public void showFullJourneyForward(String gemId) {
        clearScreen();
        printScreenHeader("FULL GEM JOURNEY - FORWARD");

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            printError("Gem not found: " + gemId);
            pauseForUser();
            return;
        }

        List<GemNode> stages = list.getAllStages();
        if (stages.isEmpty()) {
            printError("No stages recorded for this gem.");
            pauseForUser();
            return;
        }

        // Print journey header information
        System.out.println("  Direction  : Mine  -->  Current Owner  (Forward)");
        System.out.println("  Gem ID     : " + gemId);
        System.out.println("  Total Stages: " + list.getSize());
        System.out.println();

        // Print each stage node as a formatted box
        for (int i = 0; i < stages.size(); i++) {
            printStageBox(stages.get(i), i + 1, stages.size());

            // Print a chain arrow between stages to show linked list connection
            // The arrow is not printed after the last stage
            if (i < stages.size() - 1) {
                printChainArrowDown();
            }
        }

        // Print the journey summary at the bottom
        System.out.println();
        printJourneySummary(list);

        pauseForUser();
    }

    /**
     * Displays the full gem journey in reverse order.
     * Traverses from the tail node (current owner) back to the
     * head node (MINING) using the prev pointer of each node.
     *
     * This reverse traversal is only possible because we use a
     * doubly linked list. A singly linked list cannot go backward.
     * This feature is particularly useful for auditors who want to
     * trace a gem back to its origin from the current owner.
     *
     * @param gemId the ID of the gem to display in reverse
     */
    public void showFullJourneyBackward(String gemId) {
        clearScreen();
        printScreenHeader("FULL GEM JOURNEY - REVERSE");

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            printError("Gem not found: " + gemId);
            pauseForUser();
            return;
        }

        List<GemNode> stages = list.getAllStages();
        if (stages.isEmpty()) {
            printError("No stages recorded for this gem.");
            pauseForUser();
            return;
        }

        // Print reverse journey header information
        System.out.println("  Direction  : Current Owner  -->  Mine  (Backward)");
        System.out.println("  Gem ID     : " + gemId);
        System.out.println("  Total Stages: " + list.getSize());
        System.out.println();

        // Print the note explaining why backward traversal is possible
        printInfo("Backward traversal is possible because this system uses a");
        printInfo("Doubly Linked List. Each node has a prev pointer that allows");
        printInfo("traversal in both directions unlike a Singly Linked List.");
        System.out.println();

        // Traverse and print stages in reverse order
        for (int i = stages.size() - 1; i >= 0; i--) {
            printStageBox(stages.get(i), stages.size() - i, stages.size());

            // Print a chain arrow between stages
            if (i > 0) {
                printChainArrowDown();
            }
        }

        System.out.println();
        printJourneySummary(list);
        pauseForUser();
    }

    /**
     * Displays a concise summary of a gem's journey.
     * Shows the key details without printing every stage in full.
     * Useful for a quick overview when the user does not need
     * to see all stage details.
     *
     * @param gemId the ID of the gem to summarise
     */
    public void showGemSummary(String gemId) {
        clearScreen();
        printScreenHeader("GEM SUMMARY");

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            printError("Gem not found: " + gemId);
            pauseForUser();
            return;
        }

        System.out.println();
        printTopBorder();
        printEmptyLine();
        printCentred("Gem Overview");
        printEmptyLine();
        printDivider();
        printEmptyLine();

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        if (miningNode != null) {
            printDetailRow("Gem ID",       list.getGemId());
            printDetailRow("Gem Type",     miningNode.getGemType());
            printDetailRow("Origin",       miningNode.getLocation());
            printDetailRow("Mined On",     miningNode.getStageDate().toString());
            printDetailRow("Miner",        miningNode.getPersonName());
            printDetailRow("Original Wt",  miningNode.getWeightInCarats() + " carats");
            printDetailRow("Origin Price", "Rs. " + miningNode.getPriceInRupees());
        }

        printEmptyLine();
        printDivider();
        printEmptyLine();

        if (currentNode != null) {
            printDetailRow("Current Stage",  currentNode.getStage().getLabel());
            printDetailRow("Current Owner",  currentNode.getPersonName());
            printDetailRow("Current Location", currentNode.getLocation());
            printDetailRow("Current Weight", currentNode.getWeightInCarats() + " carats");
            printDetailRow("Current Price",  "Rs. " + currentNode.getPriceInRupees());
            printDetailRow("Last Updated",   currentNode.getStageDate().toString());
        }

        printEmptyLine();
        printDivider();
        printEmptyLine();
        printDetailRow("Total Stages",
                String.valueOf(list.getSize()));
        printDetailRow("Weight Lost",
                String.format("%.4f carats (%.1f%%)",
                        list.calculateWeightLoss(),
                        list.calculateWeightLossPercentage()));
        printDetailRow("Price Gain",
                String.format("Rs. %,.2f",
                        list.calculatePriceAppreciation()));
        printEmptyLine();
        printBottomBorder();

        // Show origin verification status
        System.out.println();
        String status = originVerifier.getVerificationStatusLabel(gemId);
        printCentredStatus(status);

        System.out.println();
        pauseForUser();
    }

    /**
     * Displays the details of a specific stage chosen by the user.
     * The user enters a stage number (1-based) and the details
     * of that node are shown in a formatted box.
     *
     * @param gemId the ID of the gem to inspect
     */
    public void showSpecificStage(String gemId) {
        clearScreen();
        printScreenHeader("VIEW SPECIFIC STAGE");

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            printError("Gem not found: " + gemId);
            pauseForUser();
            return;
        }

        // Show available stage numbers
        System.out.println("  Gem ID       : " + gemId);
        System.out.println("  Total Stages : " + list.getSize());
        System.out.println();

        List<GemNode> stages = list.getAllStages();
        for (int i = 0; i < stages.size(); i++) {
            System.out.println("  Stage " + (i + 1) + " : "
                    + stages.get(i).getStage().getLabel()
                    + " at " + stages.get(i).getLocation());
        }

        System.out.println();
        String stageInput = getUserInput("  Enter stage number to view (1 to "
                + list.getSize() + ")");

        int stageNumber;
        try {
            stageNumber = Integer.parseInt(stageInput.trim());
        } catch (NumberFormatException e) {
            printError("Invalid stage number.");
            pauseForUser();
            return;
        }

        if (stageNumber < 1 || stageNumber > list.getSize()) {
            printError("Stage number out of range. Valid: 1 to " + list.getSize());
            pauseForUser();
            return;
        }

        // Display the selected stage in a full detail box
        clearScreen();
        printScreenHeader("STAGE " + stageNumber + " DETAILS");
        System.out.println();
        printStageBox(stages.get(stageNumber - 1), stageNumber, list.getSize());

        pauseForUser();
    }

    // ---------------------------------------------------------
    // Analysis screens
    // ---------------------------------------------------------

    /**
     * Runs and displays the origin verification result for a gem.
     * Shows whether the gem passed or failed the Ceylon origin check
     * and displays any alerts that were generated.
     *
     * @param gemId the ID of the gem to verify
     */
    public void showOriginVerification(String gemId) {
        clearScreen();
        printScreenHeader("ORIGIN VERIFICATION");
        System.out.println();
        printInfo("Checking gem origin against known Ceylon mining locations...");
        System.out.println();
        originVerifier.runFullAuthentication(gemId);
        pauseForUser();
    }

    /**
     * Displays the full price appreciation report for a gem.
     * Shows a table of prices at each stage and a console bar chart.
     * Delegates to PriceTracker for the actual report generation.
     *
     * @param gemId the ID of the gem to analyse
     */
    public void showPriceReport(String gemId) {
        clearScreen();
        printScreenHeader("PRICE APPRECIATION REPORT");
        System.out.println();
        priceTracker.displayPriceReport(gemId);
        pauseForUser();
    }

    /**
     * Displays the weight loss analysis for a gem.
     * Shows the original weight, current weight, total loss,
     * and loss percentage calculated across the linked list.
     *
     * @param gemId the ID of the gem to analyse
     */
    public void showWeightAnalysis(String gemId) {
        clearScreen();
        printScreenHeader("WEIGHT LOSS ANALYSIS");
        System.out.println();

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            printError("Gem not found: " + gemId);
            pauseForUser();
            return;
        }

        printTopBorder();
        printEmptyLine();
        printCentred("Weight Analysis - Gem ID: " + gemId);
        printEmptyLine();
        printDivider();
        printEmptyLine();

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        if (miningNode != null) {
            printDetailRow("Original Weight (Mining)",
                    String.format("%.4f carats", miningNode.getWeightInCarats()));
        }

        if (currentNode != null && currentNode != miningNode) {
            printDetailRow("Current Weight",
                    String.format("%.4f carats", currentNode.getWeightInCarats()));
        }

        printEmptyLine();
        printDivider();
        printEmptyLine();

        printDetailRow("Total Weight Lost",
                String.format("%.4f carats", list.calculateWeightLoss()));
        printDetailRow("Weight Loss Percentage",
                String.format("%.2f%%", list.calculateWeightLossPercentage()));

        printEmptyLine();
        printDivider();
        printEmptyLine();

        // Show weight at each stage for a detailed breakdown
        printCentred("Weight at Each Stage");
        printEmptyLine();

        List<GemNode> stages = list.getAllStages();
        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);
            String weightRow = "Stage " + (i + 1)
                    + " (" + node.getStage().getLabel() + ")";
            printDetailRow(weightRow,
                    String.format("%.4f carats", node.getWeightInCarats()));

            // Show weight loss at each cutting stage
            if (i > 0) {
                double stageLoss = stages.get(i - 1).getWeightInCarats()
                        - node.getWeightInCarats();
                if (stageLoss > 0) {
                    printDetailRow("  Loss at this stage",
                            String.format("%.4f carats", stageLoss));
                }
            }
        }

        printEmptyLine();
        printBottomBorder();
        System.out.println();
        pauseForUser();
    }

    // ---------------------------------------------------------
    // QR code screens
    // ---------------------------------------------------------

    /**
     * Generates a QR code for the currently viewed gem and
     * displays the file path where it was saved.
     *
     * @param gemId the ID of the gem to generate the QR code for
     */
    public void showGenerateQRCode(String gemId) {
        clearScreen();
        printScreenHeader("GENERATE QR CODE");
        System.out.println();

        // Check if a QR code already exists for this gem
        if (qrCodeService.qrCodeExists(gemId)) {
            printInfo("A QR code already exists for this gem.");
            String choice = getUserInput(
                    "  Regenerate and overwrite existing QR code? (yes / no)");
            if (!choice.trim().equalsIgnoreCase("yes")) {
                printInfo("QR code generation cancelled.");
                pauseForUser();
                return;
            }
            // Regenerate the QR code with fresh data
            String path = qrCodeService.regenerateQRCode(gemId);
            if (path != null) {
                printSuccess("QR code regenerated successfully.");
                printSuccess("Saved to: " + path);
            } else {
                printError("QR code regeneration failed.");
            }
        } else {
            // Generate a new QR code
            String path = qrCodeService.generateQRCode(gemId);
            if (path != null) {
                printSuccess("QR code generated successfully.");
                printSuccess("Saved to: " + path);
                printInfo("Open the file in any image viewer to scan it.");
            } else {
                printError("QR code generation failed.");
            }
        }

        System.out.println();
        pauseForUser();
    }

    /**
     * Displays a preview of the text content that would be
     * encoded into the gem's QR code.
     * Used before generating the actual image to verify the content.
     *
     * @param gemId the ID of the gem to preview
     */
    public void showQRCodePreview(String gemId) {
        clearScreen();
        printScreenHeader("QR CODE CONTENT PREVIEW");
        System.out.println();
        printInfo("The following content will be encoded into the QR code:");
        System.out.println();
        System.out.println("  " + "-".repeat(WIDTH));
        qrCodeService.previewQRContent(gemId);
        System.out.println("  " + "-".repeat(WIDTH));
        pauseForUser();
    }

    // ---------------------------------------------------------
    // Stage box drawing
    // ---------------------------------------------------------

    /**
     * Draws a formatted box showing all details of a single stage node.
     * The box uses the black and white border theme and shows all
     * available fields including optional ones only if they are set.
     *
     * A position indicator is shown at the top of each box so the
     * user can see which stage in the chain they are looking at.
     *
     * @param node        the GemNode whose data to display
     * @param stageNumber the position of this node in the journey
     * @param totalStages the total number of stages in the journey
     */
    private void printStageBox(GemNode node, int stageNumber, int totalStages) {
        // Stage position header
        String positionLabel = "Stage " + stageNumber + " of " + totalStages
                + "  --  " + node.getStage().getLabel().toUpperCase();

        System.out.println("  +" + "-".repeat(WIDTH) + "+");

        // Centre the position label
        int padding  = WIDTH - positionLabel.length();
        int leftPad  = padding / 2;
        int rightPad = padding - leftPad;
        if (leftPad < 0)  leftPad  = 0;
        if (rightPad < 0) rightPad = 0;
        System.out.println("  |"
                + " ".repeat(leftPad)
                + positionLabel
                + " ".repeat(rightPad)
                + "|");

        System.out.println("  +" + "-".repeat(WIDTH) + "+");

        // Core fields always displayed
        printBoxRow("Gem ID",       node.getGemId());
        printBoxRow("Gem Type",     node.getGemType());
        printBoxRow("Stage",        node.getStage().getLabel());
        printBoxRow("Location",     node.getLocation());
        printBoxRow("Person",       node.getPersonName());
        printBoxRow("Date",         node.getStageDate().toString());
        printBoxRow("Weight",       String.format("%.4f carats",
                node.getWeightInCarats()));
        printBoxRow("Price",        String.format("Rs. %,.2f",
                node.getPriceInRupees()));

        // Optional fields - only printed if they have been set on this node
        if (node.getPersonIdNumber() != null) {
            printBoxRow("NIC / Passport", node.getPersonIdNumber());
        }
        if (node.getContactNumber() != null) {
            printBoxRow("Contact",       node.getContactNumber());
        }
        if (node.getCertificateNumber() != null) {
            printBoxRow("Certificate No", node.getCertificateNumber());
        }
        if (node.getIssuingAuthority() != null) {
            printBoxRow("Issued By",     node.getIssuingAuthority());
        }
        if (node.getFlightNumber() != null) {
            printBoxRow("Flight No",     node.getFlightNumber());
        }
        if (node.getInvoiceNumber() != null) {
            printBoxRow("Invoice No",    node.getInvoiceNumber());
        }
        if (node.getDestinationCountry() != null) {
            printBoxRow("Destination",   node.getDestinationCountry());
        }
        if (node.getNotes() != null) {
            printBoxRow("Notes",         node.getNotes());
        }

        System.out.println("  +" + "-".repeat(WIDTH) + "+");
    }

    /**
     * Prints a single row inside a stage box with a label and value.
     * The label is left-aligned and the value fills the remaining space.
     * Long values are truncated with "..." to prevent border overflow.
     *
     * @param label the field name to show on the left
     * @param value the field value to show on the right
     */
    private void printBoxRow(String label, String value) {
        String row     = "  " + label + " : " + value;
        int    padding = WIDTH - row.length();

        // Truncate very long values to keep the box borders aligned
        if (padding < 1) {
            row     = row.substring(0, WIDTH - 3) + "...";
            padding = 1;
        }

        System.out.println("  |" + row + " ".repeat(padding) + "|");
    }

    /**
     * Prints a downward arrow between two stage boxes to represent
     * the next pointer connecting two nodes in the linked list.
     * This makes the linked list chain visually obvious in the output.
     */
    private void printChainArrowDown() {
        System.out.println("  " + " ".repeat(WIDTH / 2) + "|");
        System.out.println("  " + " ".repeat(WIDTH / 2) + "|  next pointer");
        System.out.println("  " + " ".repeat(WIDTH / 2) + "v");
    }

    /**
     * Prints a summary of the gem's key statistics at the bottom
     * of the full journey view.
     * Shows total stages, weight loss, and price appreciation.
     *
     * @param list the GemLinkedList to summarise
     */
    private void printJourneySummary(GemLinkedList list) {
        System.out.println("  +" + "-".repeat(WIDTH) + "+");

        String summaryTitle = "Journey Summary";
        int padding  = WIDTH - summaryTitle.length();
        int leftPad  = padding / 2;
        int rightPad = padding - leftPad;
        System.out.println("  |"
                + " ".repeat(leftPad)
                + summaryTitle
                + " ".repeat(rightPad)
                + "|");

        System.out.println("  +" + "-".repeat(WIDTH) + "+");

        printBoxRow("Total Stages",    String.valueOf(list.getSize()));
        printBoxRow("Weight Lost",
                String.format("%.4f carats (%.1f%%)",
                        list.calculateWeightLoss(),
                        list.calculateWeightLossPercentage()));
        printBoxRow("Price Gained",
                String.format("Rs. %,.2f", list.calculatePriceAppreciation()));

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        if (miningNode != null) {
            printBoxRow("Origin",
                    miningNode.getLocation() + " on " + miningNode.getStageDate());
        }
        if (currentNode != null) {
            printBoxRow("Current Owner",
                    currentNode.getPersonName()
                    + " at " + currentNode.getLocation());
        }

        System.out.println("  +" + "-".repeat(WIDTH) + "+");
    }

    /**
     * Prints a small gem badge at the top of the journey menu
     * showing the gem ID, type, and total stages at a glance.
     * Gives the user a quick reminder of which gem they are viewing.
     *
     * @param list the GemLinkedList to show the badge for
     */
    private void printGemBadge(GemLinkedList list) {
        GemNode miningNode = list.getMiningNode();
        System.out.println();
        System.out.println("  +" + "-".repeat(WIDTH) + "+");

        String gemInfo = "💎 Gem ID: " + list.getGemId();
        if (miningNode != null) {
            gemInfo += "  |  Type: " + miningNode.getGemType();
        }
        gemInfo += "  |  Stages: " + list.getSize();

        int pad = WIDTH - gemInfo.length();
        if (pad < 1) pad = 1;
        System.out.println("  |" + gemInfo + " ".repeat(pad) + "|");
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
    }

    /**
     * Prints a centred verification status label.
     * The status is shown in a box to make it stand out clearly
     * from the surrounding output.
     *
     * @param status the status label string to display
     */
    private void printCentredStatus(String status) {
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
        int padding  = WIDTH - status.length();
        int leftPad  = Math.max(0, padding / 2);
        int rightPad = Math.max(0, padding - leftPad);
        System.out.println("  |"
                + " ".repeat(leftPad)
                + status
                + " ".repeat(rightPad)
                + "|");
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
    }

    // ---------------------------------------------------------
    // Black and white theme drawing methods
    // ---------------------------------------------------------

    /**
     * Prints the top border of a menu or content box.
     */
    private void printTopBorder() {
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
    }

    /**
     * Prints the bottom border of a menu or content box.
     */
    private void printBottomBorder() {
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
    }

    /**
     * Prints a horizontal divider line inside a box.
     */
    private void printDivider() {
        System.out.println("  |" + "-".repeat(WIDTH) + "|");
    }

    /**
     * Prints an empty line with vertical borders for spacing.
     */
    private void printEmptyLine() {
        System.out.println("  |" + " ".repeat(WIDTH) + "|");
    }

    /**
     * Prints text centred within the box borders.
     *
     * @param text the text to centre and print
     */
    private void printCentred(String text) {
        int padding  = WIDTH - text.length();
        int leftPad  = Math.max(0, padding / 2);
        int rightPad = Math.max(0, padding - leftPad);
        System.out.println("  |"
                + " ".repeat(leftPad)
                + text
                + " ".repeat(rightPad)
                + "|");
    }

    /**
     * Prints a numbered menu item inside the box.
     *
     * @param number the selection number for this item
     * @param text   the description of this menu option
     */
    private void printMenuItem(String number, String text) {
        String item    = "  [" + number + "]  " + text;
        int    padding = WIDTH - item.length();
        if (padding < 1) padding = 1;
        System.out.println("  |" + item + " ".repeat(padding) + "|");
    }

    /**
     * Prints a label and value row in the detail view.
     *
     * @param label the field label on the left
     * @param value the field value on the right
     */
    private void printDetailRow(String label, String value) {
        String row     = "  " + label + " : " + value;
        int    padding = WIDTH - row.length();
        if (padding < 1) padding = 1;
        System.out.println("  |" + row + " ".repeat(padding) + "|");
    }

    /**
     * Prints a screen header with the given title centred.
     *
     * @param title the title of the current screen
     */
    private void printScreenHeader(String title) {
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
        int padding  = WIDTH - title.length();
        int leftPad  = Math.max(0, padding / 2);
        int rightPad = Math.max(0, padding - leftPad);
        System.out.println("  |"
                + " ".repeat(leftPad)
                + title
                + " ".repeat(rightPad)
                + "|");
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
        System.out.println();
    }

    /**
     * Prints an error message clearly marked with ERROR label.
     *
     * @param message the error message to display
     */
    private void printError(String message) {
        System.out.println();
        System.out.println("  ERROR: " + message);
        System.out.println();
    }

    /**
     * Prints an informational message for hints and guidance.
     *
     * @param message the info message to display
     */
    private void printInfo(String message) {
        System.out.println("  INFO: " + message);
    }

    /**
     * Prints a success message clearly marked with SUCCESS label.
     *
     * @param message the success message to display
     */
    private void printSuccess(String message) {
        System.out.println("  SUCCESS: " + message);
    }

    // ---------------------------------------------------------
    // Input and pause helpers
    // ---------------------------------------------------------

    /**
     * Prompts the user for text input and returns what they typed.
     *
     * @param prompt the message shown before the input field
     * @return the string entered by the user
     */
    private String getUserInput(String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine();
    }

    /**
     * Waits for the user to press Enter before continuing.
     * Used at the end of every screen so output can be read
     * before the menu refreshes.
     */
    private void pauseForUser() {
        System.out.println();
        System.out.print("  Press Enter to continue...");
        scanner.nextLine();
    }

    /**
     * Clears the console by printing blank lines.
     * Cross-platform approach that works on Windows, Mac and Linux.
     */
    private void clearScreen() {
        for (int i = 0; i < 50; i++) {
            System.out.println();
        }
    }
}