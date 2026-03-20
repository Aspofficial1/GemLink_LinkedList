package ui;

import model.GemLinkedList;
import model.GemNode;
import model.GemStage;
import service.TrackingService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * GemForm handles all user input forms in the application.
 * It is responsible for collecting data from the user through
 * the console and passing it to TrackingService for processing.
 *
 * Every input field is validated before being accepted so that
 * incomplete or incorrect data never reaches the service layer.
 * For example, weights must be positive numbers, dates must be
 * in the correct format, and required fields cannot be left empty.
 *
 * The black and white console theme is maintained here by using
 * the same border characters and formatting as MainMenu.
 */
public class GemForm {

    // ---------------------------------------------------------
    // Constants - black and white theme
    // ---------------------------------------------------------

    /**
     * The display width used for all form borders and separators.
     * Kept the same as MainMenu for visual consistency.
     */
    private static final int WIDTH = 65;

    /**
     * The expected date input format from the user.
     * YYYY-MM-DD is used because it is unambiguous and sorts correctly.
     */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to register gems and add stages.
     * All form data collected here is passed to this service.
     */
    private TrackingService trackingService;

    /**
     * The shared Scanner instance passed from MainMenu.
     * Using the same Scanner prevents conflicts on System.in.
     */
    private Scanner scanner;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new GemForm with the given TrackingService and Scanner.
     * Both are injected from MainMenu so they are shared across
     * the entire UI layer.
     *
     * @param trackingService the service layer for gem operations
     * @param scanner         the shared console input scanner
     */
    public GemForm(TrackingService trackingService, Scanner scanner) {
        this.trackingService = trackingService;
        this.scanner         = scanner;
    }

    // ---------------------------------------------------------
    // Register new gem form
    // ---------------------------------------------------------

    /**
     * Displays and processes the full form for registering a new gem.
     * Collects all required mining details from the user, validates
     * each field, and calls TrackingService to register the gem.
     *
     * The form is split into sections for readability:
     * Section 1 - Gem details (type, color, weight, price)
     * Section 2 - Mining location details
     * Section 3 - Miner personal details
     *
     * If registration is successful the new gem ID is displayed
     * so the user can note it down for future operations.
     */
    public void showRegisterGemForm() {
        printFormHeader("NEW GEM REGISTRATION FORM");
        printFormInfo("Please fill in all required fields marked with (*)");
        printFormInfo("Date format: " + DATE_FORMAT);
        System.out.println();

        // ---------------------------------------------------------
        // Section 1 - Gem details
        // ---------------------------------------------------------
        printSectionHeader("SECTION 1 - GEM DETAILS");

        // Gem type selection
        printFormInfo("Available gem types:");
        printFormInfo("  1. Blue Sapphire    2. Pink Sapphire");
        printFormInfo("  3. Ruby             4. Cat's Eye");
        printFormInfo("  5. Alexandrite      6. Other");
        System.out.println();

        String gemType = getRequiredInput("(*) Gem Type");

        // Color description
        printFormInfo("Describe the color using hue, tone and saturation.");
        printFormInfo("Example: Vivid blue with medium tone and high saturation");
        String colorDesc = getRequiredInput("(*) Color Description");

        // Original weight
        double weight = getPositiveDoubleInput("(*) Original Weight in Carats");

        // Initial price
        double price = getPositiveDoubleInput("(*) Initial Estimated Value in Rupees");

        // ---------------------------------------------------------
        // Section 2 - Mining location details
        // ---------------------------------------------------------
        printSectionHeader("SECTION 2 - MINING LOCATION");

        printFormInfo("Known mining districts: Ratnapura, Matale, Ampara,");
        printFormInfo("                        Badulla, Kandy, Kalutara");
        System.out.println();

        String originMine = getRequiredInput("(*) Mine Name");
        String district   = getRequiredInput("(*) District");
        String village    = getOptionalInput("    Village (optional, press Enter to skip)");

        // Mining date
        LocalDate miningDate = getDateInput("(*) Mining Date (" + DATE_FORMAT + ")");

        // ---------------------------------------------------------
        // Section 3 - Miner details
        // ---------------------------------------------------------
        printSectionHeader("SECTION 3 - MINER DETAILS");

        String minerName    = getRequiredInput("(*) Miner Full Name");
        String minerNIC     = getRequiredInput("(*) Miner NIC Number");
        String minerContact = getRequiredInput("(*) Miner Contact Number");

        // ---------------------------------------------------------
        // Confirmation before saving
        // ---------------------------------------------------------
        printSectionHeader("CONFIRM REGISTRATION");
        System.out.println();
        System.out.println("  Gem Type     : " + gemType);
        System.out.println("  Color        : " + colorDesc);
        System.out.println("  Weight       : " + weight + " carats");
        System.out.println("  Price        : Rs. " + price);
        System.out.println("  Mine         : " + originMine);
        System.out.println("  District     : " + district);
        System.out.println("  Village      : " + (village.isEmpty() ? "Not specified" : village));
        System.out.println("  Mining Date  : " + miningDate);
        System.out.println("  Miner        : " + minerName);
        System.out.println("  Miner NIC    : " + minerNIC);
        System.out.println("  Contact      : " + minerContact);
        System.out.println();

        String confirm = getRequiredInput("Confirm registration? (yes / no)");

        if (!confirm.trim().equalsIgnoreCase("yes")) {
            printFormInfo("Registration cancelled by user.");
            return;
        }

        // ---------------------------------------------------------
        // Call TrackingService to register the gem
        // ---------------------------------------------------------
        GemLinkedList newList = trackingService.registerNewGem(
                gemType,
                colorDesc,
                originMine,
                district,
                village,
                minerName,
                minerNIC,
                minerContact,
                weight,
                price,
                miningDate
        );

        System.out.println();

        if (newList != null) {
            printFormSuccess("Gem registered successfully.");
            printFormSuccess("Your Gem ID is: " + newList.getGemId());
            printFormInfo("Please note down your Gem ID for future operations.");
        } else {
            printFormError("Gem registration failed. Please try again.");
        }
    }

    // ---------------------------------------------------------
    // Add stage form
    // ---------------------------------------------------------

    /**
     * Displays and processes the form for adding a new stage to
     * an existing gem's journey.
     *
     * First shows a stage type selection menu, then collects the
     * appropriate fields for the selected stage type.
     * Export stages collect additional fields (flight number,
     * invoice number, destination country).
     * All stages optionally accept a certificate number.
     *
     * @param gemId the ID of the gem to add the stage to
     */
    public void showAddStageForm(String gemId) {
        printFormHeader("ADD STAGE TO GEM: " + gemId);

        // Check the gem exists before proceeding
        GemLinkedList existingList = trackingService.getGemList(gemId);
        if (existingList == null) {
            printFormError("Gem ID not found: " + gemId);
            return;
        }

        // Show the current journey summary so the user knows where the gem is
        printSectionHeader("CURRENT JOURNEY STATUS");
        existingList.printSummary();
        System.out.println();

        // ---------------------------------------------------------
        // Stage type selection
        // ---------------------------------------------------------
        printSectionHeader("SELECT STAGE TYPE");
        System.out.println();
        System.out.println("  [1]  Cutting and Polishing Stage");
        System.out.println("  [2]  Trading Stage");
        System.out.println("  [3]  Exporting Stage");
        System.out.println("  [4]  Final Buyer Stage");
        System.out.println("  [0]  Cancel");
        System.out.println();

        String stageChoice = getRequiredInput("(*) Select stage type");

        GemStage selectedStage;
        switch (stageChoice.trim()) {
            case "1": selectedStage = GemStage.CUTTING;   break;
            case "2": selectedStage = GemStage.TRADING;   break;
            case "3": selectedStage = GemStage.EXPORTING; break;
            case "4": selectedStage = GemStage.BUYING;    break;
            case "0":
                printFormInfo("Stage addition cancelled.");
                return;
            default:
                printFormError("Invalid stage selection.");
                return;
        }

        // ---------------------------------------------------------
        // Collect common stage fields
        // ---------------------------------------------------------
        printSectionHeader("STAGE DETAILS - " + selectedStage.getLabel().toUpperCase());
        printFormInfo("Location hint: " + selectedStage.getLocationHint());
        System.out.println();

        String    location   = getRequiredInput("(*) Location");
        String    personName = getRequiredInput("(*) Person Full Name");
        String    personNIC  = getRequiredInput("(*) Person NIC or Passport Number");
        String    contact    = getRequiredInput("(*) Contact Number");
        double    weight     = getPositiveDoubleInput("(*) Current Weight in Carats");
        double    price      = getPositiveDoubleInput("(*) Current Value in Rupees");
        LocalDate stageDate  = getDateInput("(*) Stage Date (" + DATE_FORMAT + ")");

        // ---------------------------------------------------------
        // Collect export-specific fields if stage is EXPORTING
        // ---------------------------------------------------------
        String flightNumber       = "";
        String invoiceNumber      = "";
        String destinationCountry = "";

        if (selectedStage == GemStage.EXPORTING) {
            printSectionHeader("EXPORT DETAILS");
            flightNumber       = getRequiredInput("(*) Flight Number");
            invoiceNumber      = getRequiredInput("(*) Invoice Number");
            destinationCountry = getRequiredInput("(*) Destination Country");
        }

        // ---------------------------------------------------------
        // Collect optional certificate details
        // ---------------------------------------------------------
        printSectionHeader("CERTIFICATE DETAILS (Optional)");
        printFormInfo("Press Enter to skip if no certificate at this stage.");
        System.out.println();

        String certificateNumber = getOptionalInput("    Certificate Number");
        String issuingAuthority  = "";
        if (!certificateNumber.isEmpty()) {
            issuingAuthority = getRequiredInput("(*) Issuing Authority Name");
        }

        // ---------------------------------------------------------
        // Optional notes
        // ---------------------------------------------------------
        String notes = getOptionalInput("    Additional Notes (optional)");

        // ---------------------------------------------------------
        // Confirmation before saving
        // ---------------------------------------------------------
        printSectionHeader("CONFIRM STAGE ADDITION");
        System.out.println();
        System.out.println("  Gem ID       : " + gemId);
        System.out.println("  Stage        : " + selectedStage.getLabel());
        System.out.println("  Location     : " + location);
        System.out.println("  Person       : " + personName);
        System.out.println("  NIC          : " + personNIC);
        System.out.println("  Contact      : " + contact);
        System.out.println("  Weight       : " + weight + " carats");
        System.out.println("  Price        : Rs. " + price);
        System.out.println("  Date         : " + stageDate);

        if (selectedStage == GemStage.EXPORTING) {
            System.out.println("  Flight No    : " + flightNumber);
            System.out.println("  Invoice No   : " + invoiceNumber);
            System.out.println("  Destination  : " + destinationCountry);
        }

        if (!certificateNumber.isEmpty()) {
            System.out.println("  Certificate  : " + certificateNumber);
            System.out.println("  Authority    : " + issuingAuthority);
        }

        if (!notes.isEmpty()) {
            System.out.println("  Notes        : " + notes);
        }

        System.out.println();

        String confirm = getRequiredInput("Confirm adding this stage? (yes / no)");

        if (!confirm.trim().equalsIgnoreCase("yes")) {
            printFormInfo("Stage addition cancelled by user.");
            return;
        }

        // ---------------------------------------------------------
        // Call TrackingService to add the stage
        // ---------------------------------------------------------
        GemNode newNode = trackingService.addStageToGem(
                gemId,
                selectedStage,
                location,
                personName,
                personNIC,
                contact,
                weight,
                price,
                stageDate
        );

        if (newNode == null) {
            printFormError("Failed to add stage. Please try again.");
            return;
        }

        // Set export details if EXPORTING stage
        if (selectedStage == GemStage.EXPORTING && !flightNumber.isEmpty()) {
            trackingService.addExportDetails(
                    gemId, flightNumber, invoiceNumber, destinationCountry);
        }

        // Set certificate details if provided
        if (!certificateNumber.isEmpty()) {
            trackingService.addCertificateDetails(
                    gemId, certificateNumber, issuingAuthority);
        }

        // Set notes if provided
        if (!notes.isEmpty()) {
            trackingService.addNoteToCurrentStage(gemId, notes);
        }

        System.out.println();
        printFormSuccess("Stage added successfully.");
        printFormSuccess("Stage: " + selectedStage.getLabel()
                + " | Gem: " + gemId);
    }

    // ---------------------------------------------------------
    // Input collection helpers
    // ---------------------------------------------------------

    /**
     * Prompts the user for a required text input.
     * Repeats the prompt until a non-empty value is entered.
     * Leading and trailing whitespace is removed automatically.
     *
     * @param prompt the label shown before the input field
     * @return the non-empty string entered by the user
     */
    private String getRequiredInput(String prompt) {
        String value = "";
        while (value.trim().isEmpty()) {
            System.out.print("  " + prompt + ": ");
            value = scanner.nextLine();
            if (value.trim().isEmpty()) {
                System.out.println("  This field is required. Please enter a value.");
            }
        }
        return value.trim();
    }

    /**
     * Prompts the user for an optional text input.
     * Returns an empty string if the user presses Enter without typing.
     * No validation is applied to optional fields.
     *
     * @param prompt the label shown before the input field
     * @return the string entered by the user, or empty string if skipped
     */
    private String getOptionalInput(String prompt) {
        System.out.print("  " + prompt + ": ");
        return scanner.nextLine().trim();
    }

    /**
     * Prompts the user for a positive decimal number.
     * Repeats the prompt until a valid positive number is entered.
     * Used for weight and price fields which must be greater than zero.
     *
     * @param prompt the label shown before the input field
     * @return a positive double value entered by the user
     */
    private double getPositiveDoubleInput(String prompt) {
        double value = 0;
        boolean valid = false;

        while (!valid) {
            System.out.print("  " + prompt + ": ");
            String input = scanner.nextLine().trim();

            try {
                value = Double.parseDouble(input);
                if (value <= 0) {
                    System.out.println("  Value must be greater than zero."
                            + " Please try again.");
                } else {
                    valid = true;
                }
            } catch (NumberFormatException e) {
                System.out.println("  Invalid number format."
                        + " Please enter a numeric value e.g. 4.75");
            }
        }

        return value;
    }

    /**
     * Prompts the user for a date in YYYY-MM-DD format.
     * Repeats the prompt until a correctly formatted date is entered.
     * Uses Java's LocalDate parsing which validates the date fully,
     * so invalid dates like 2025-02-30 are caught and rejected.
     *
     * @param prompt the label shown before the date input field
     * @return a valid LocalDate object parsed from the user's input
     */
    private LocalDate getDateInput(String prompt) {
        LocalDate date = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);

        while (date == null) {
            System.out.print("  " + prompt + ": ");
            String input = scanner.nextLine().trim();

            try {
                date = LocalDate.parse(input, formatter);

                // Warn the user if the date is in the future
                // A mining date cannot be in the future
                if (date.isAfter(LocalDate.now())) {
                    System.out.println("  Warning: The date you entered is in the future.");
                    System.out.print("  Are you sure this is correct? (yes / no): ");
                    String confirm = scanner.nextLine().trim();
                    if (!confirm.equalsIgnoreCase("yes")) {
                        date = null;
                    }
                }

            } catch (DateTimeParseException e) {
                System.out.println("  Invalid date format."
                        + " Please use " + DATE_FORMAT
                        + " for example: 2025-01-15");
            }
        }

        return date;
    }

    // ---------------------------------------------------------
    // Black and white theme display helpers
    // ---------------------------------------------------------

    /**
     * Prints the form header with the form title centred.
     * Uses the same border style as MainMenu for visual consistency.
     *
     * @param title the title of the form being displayed
     */
    private void printFormHeader(String title) {
        System.out.println();
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
        System.out.println("  |" + " ".repeat(WIDTH) + "|");

        int padding    = WIDTH - title.length();
        int leftPad    = padding / 2;
        int rightPad   = padding - leftPad;
        System.out.println("  |"
                + " ".repeat(leftPad)
                + title
                + " ".repeat(rightPad)
                + "|");

        System.out.println("  |" + " ".repeat(WIDTH) + "|");
        System.out.println("  +" + "-".repeat(WIDTH) + "+");
        System.out.println();
    }

    /**
     * Prints a section header within a form to separate different
     * groups of input fields visually.
     *
     * @param sectionTitle the title of the form section
     */
    private void printSectionHeader(String sectionTitle) {
        System.out.println();
        System.out.println("  +" + "-".repeat(WIDTH) + "+");

        int padding  = WIDTH - sectionTitle.length();
        int leftPad  = padding / 2;
        int rightPad = padding - leftPad;
        System.out.println("  |"
                + " ".repeat(leftPad)
                + sectionTitle
                + " ".repeat(rightPad)
                + "|");

        System.out.println("  +" + "-".repeat(WIDTH) + "+");
        System.out.println();
    }

    /**
     * Prints a general information message inside the form.
     * Used for hints, instructions, and field guidance text.
     *
     * @param message the informational message to display
     */
    private void printFormInfo(String message) {
        System.out.println("  | " + message);
    }

    /**
     * Prints a success message after a form is submitted successfully.
     * Marked with a clear SUCCESS label to stand out from other output.
     *
     * @param message the success message to display
     */
    private void printFormSuccess(String message) {
        System.out.println("  SUCCESS: " + message);
    }

    /**
     * Prints an error message when a form operation fails.
     * Marked with a clear ERROR label so the user notices it.
     *
     * @param message the error message to display
     */
    private void printFormError(String message) {
        System.out.println("  ERROR: " + message);
    }
}