package report;

import model.GemLinkedList;
import model.GemNode;
import service.OriginVerifier;
import service.PriceTracker;
import service.TrackingService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ReportGenerator is responsible for creating and saving text
 * based reports for gem journeys.
 *
 * This class generates two types of reports:
 * 1. A full journey report showing every stage of a gem's journey
 *    from mine to current owner with all recorded details.
 * 2. A summary report showing key statistics and analysis data
 *    including weight loss, price appreciation, and verification status.
 *
 * Reports are saved as plain text files in the reports folder.
 * Plain text is used instead of PDF because it requires no external
 * libraries and can be opened on any device without special software.
 *
 * The report format follows the same black and white border theme
 * used throughout the rest of the application so the output is
 * visually consistent whether viewed on screen or in a text file.
 */
public class ReportGenerator {

    // ---------------------------------------------------------
    // Constants
    // ---------------------------------------------------------

    /**
     * The width of all report borders and separators in characters.
     * Kept wider than the console UI to use the full text file width.
     */
    private static final int WIDTH = 75;

    /**
     * The folder where all generated report files are saved.
     * Created automatically if it does not already exist.
     */
    private static final String REPORT_FOLDER = "reports";

    /**
     * The date and time format used in report file names and headers.
     * Using a timestamp in the file name prevents overwriting old reports.
     */
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd_HH-mm-ss";

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem journey data
     * for inclusion in the report.
     */
    private TrackingService trackingService;

    /**
     * The OriginVerifier used to include verification status
     * and results in the report.
     */
    private OriginVerifier originVerifier;

    /**
     * The PriceTracker used to include price appreciation data
     * and stage-by-stage value analysis in the report.
     */
    private PriceTracker priceTracker;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new ReportGenerator and ensures the reports output
     * folder exists on the file system.
     * If the folder does not exist it is created automatically so
     * the first report generation does not fail due to a missing folder.
     *
     * @param trackingService the service for retrieving gem data
     * @param originVerifier  the service for verification status
     * @param priceTracker    the service for price analysis data
     */
    public ReportGenerator(TrackingService trackingService,
                           OriginVerifier  originVerifier,
                           PriceTracker    priceTracker) {
        this.trackingService = trackingService;
        this.originVerifier  = originVerifier;
        this.priceTracker    = priceTracker;

        // Create the reports output folder if it does not already exist
        File reportsFolder = new File(REPORT_FOLDER);
        if (!reportsFolder.exists()) {
            reportsFolder.mkdirs();
            System.out.println("💎 Reports folder created: " + REPORT_FOLDER);
        }
    }

    // ---------------------------------------------------------
    // Full journey report
    // ---------------------------------------------------------

    /**
     * Generates and saves a full journey report for a specific gem.
     * The report includes every stage node in the linked list with
     * all recorded details, plus a summary section at the end.
     *
     * The report file is named using the gem ID and a timestamp
     * so multiple reports for the same gem do not overwrite each other.
     *
     * @param gemId the ID of the gem to generate the report for
     * @return the file path of the saved report, or null if failed
     */
    public String generateFullJourneyReport(String gemId) {
        System.out.println("💎 Generating full journey report for Gem ID: " + gemId);

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            System.out.println("  Report failed: Gem not found - " + gemId);
            return null;
        }

        // Build the report content as a StringBuilder
        StringBuilder report = new StringBuilder();
        buildReportHeader(report, "FULL GEM JOURNEY REPORT", gemId);
        buildGemOverviewSection(report, list);
        buildFullJourneySection(report, list);
        buildWeightAnalysisSection(report, list);
        buildPriceAnalysisSection(report, gemId);
        buildVerificationSection(report, gemId);
        buildReportFooter(report);

        // Save the report to a file
        String fileName = REPORT_FOLDER + File.separator
                + gemId + "_FullReport_"
                + getCurrentTimestamp() + ".txt";

        boolean saved = saveReportToFile(report.toString(), fileName);

        if (saved) {
            System.out.println("💎 Full journey report saved: " + fileName);
            return fileName;
        } else {
            System.out.println("  Failed to save full journey report.");
            return null;
        }
    }

    /**
     * Generates and saves a summary report for a specific gem.
     * The summary report is shorter than the full journey report
     * and shows only the key statistics and verification status.
     * Useful for quick reference without the full stage details.
     *
     * @param gemId the ID of the gem to generate the summary for
     * @return the file path of the saved report, or null if failed
     */
    public String generateSummaryReport(String gemId) {
        System.out.println("💎 Generating summary report for Gem ID: " + gemId);

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            System.out.println("  Report failed: Gem not found - " + gemId);
            return null;
        }

        StringBuilder report = new StringBuilder();
        buildReportHeader(report, "GEM SUMMARY REPORT", gemId);
        buildGemOverviewSection(report, list);
        buildWeightAnalysisSection(report, list);
        buildPriceAnalysisSection(report, gemId);
        buildVerificationSection(report, gemId);
        buildReportFooter(report);

        String fileName = REPORT_FOLDER + File.separator
                + gemId + "_SummaryReport_"
                + getCurrentTimestamp() + ".txt";

        boolean saved = saveReportToFile(report.toString(), fileName);

        if (saved) {
            System.out.println("💎 Summary report saved: " + fileName);
            return fileName;
        } else {
            System.out.println("  Failed to save summary report.");
            return null;
        }
    }

    /**
     * Generates and saves a full system report covering all gems.
     * This report lists every gem registered in the system with
     * a summary for each one, followed by overall statistics.
     * Used by administrators for auditing and record keeping.
     *
     * @return the file path of the saved report, or null if failed
     */
    public String generateAllGemsReport() {
        System.out.println("💎 Generating full system report for all gems.");

        List<String> allIds = trackingService.getAllGemIds();
        if (allIds.isEmpty()) {
            System.out.println("  No gems found. Report not generated.");
            return null;
        }

        StringBuilder report = new StringBuilder();

        // Report header
        buildSectionBorder(report);
        appendCentred(report, "GEM ORIGIN TRACKING SYSTEM");
        appendCentred(report, "FULL SYSTEM REPORT");
        appendCentred(report, "Generated: " + getCurrentReadableDateTime());
        appendCentred(report, "Total Gems in System: " + allIds.size());
        buildSectionBorder(report);
        report.append("\n");

        // Summary for each gem
        for (int i = 0; i < allIds.size(); i++) {
            String gemId = allIds.get(i);
            GemLinkedList list = trackingService.getGemList(gemId);
            if (list != null) {
                report.append("\n");
                appendDivider(report);
                appendLine(report, "GEM " + (i + 1) + " of " + allIds.size());
                appendDivider(report);
                buildGemOverviewSection(report, list);
                buildVerificationSection(report, gemId);
            }
        }

        // Overall statistics section
        report.append("\n");
        buildSectionBorder(report);
        appendCentred(report, "OVERALL SYSTEM STATISTICS");
        buildSectionBorder(report);
        report.append("\n");
        appendLabelValue(report, "Total Gems Registered",
                String.valueOf(trackingService.getTotalGemCount()));
        appendLabelValue(report, "Ceylon Verified Gems",
                String.valueOf(trackingService.getCeylonGemCount()));
        appendLabelValue(report, "Unresolved Fraud Alerts",
                String.valueOf(trackingService.getUnresolvedAlertCount()));
        report.append("\n");
        buildReportFooter(report);

        String fileName = REPORT_FOLDER + File.separator
                + "SystemReport_" + getCurrentTimestamp() + ".txt";

        boolean saved = saveReportToFile(report.toString(), fileName);

        if (saved) {
            System.out.println("💎 System report saved: " + fileName);
            return fileName;
        } else {
            System.out.println("  Failed to save system report.");
            return null;
        }
    }

    // ---------------------------------------------------------
    // Report section builders
    // ---------------------------------------------------------

    /**
     * Builds the standard report header section.
     * Includes the institution name, report type, gem ID,
     * and the date and time the report was generated.
     *
     * @param sb       the StringBuilder to append to
     * @param title    the title of this report type
     * @param gemId    the ID of the gem this report covers
     */
    private void buildReportHeader(StringBuilder sb,
                                   String title,
                                   String gemId) {
        buildSectionBorder(sb);
        appendCentred(sb, "NATIONAL INSTITUTE OF BUSINESS MANAGEMENT");
        appendCentred(sb, "SCHOOL OF COMPUTING AND ENGINEERING");
        appendCentred(sb, "HND SOFTWARE ENGINEERING");
        appendEmptyLine(sb);
        appendCentred(sb, "GEM ORIGIN TRACKING SYSTEM");
        appendCentred(sb, title);
        appendEmptyLine(sb);
        appendCentred(sb, "Gem ID   : " + gemId);
        appendCentred(sb, "Generated: " + getCurrentReadableDateTime());
        buildSectionBorder(sb);
        sb.append("\n");
    }

    /**
     * Builds the gem overview section of the report.
     * Shows the key details from the head and tail nodes of the
     * linked list, representing the origin and current state.
     *
     * @param sb   the StringBuilder to append to
     * @param list the GemLinkedList whose data to include
     */
    private void buildGemOverviewSection(StringBuilder sb,
                                         GemLinkedList list) {
        appendSectionTitle(sb, "GEM OVERVIEW");

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        appendLabelValue(sb, "Gem ID",        list.getGemId());

        if (miningNode != null) {
            appendLabelValue(sb, "Gem Type",      miningNode.getGemType());
            appendLabelValue(sb, "Origin Mine",   miningNode.getLocation());
            appendLabelValue(sb, "Mined On",      miningNode.getStageDate().toString());
            appendLabelValue(sb, "Miner",         miningNode.getPersonName());
            appendLabelValue(sb, "Original Weight",
                    String.format("%.4f carats", miningNode.getWeightInCarats()));
            appendLabelValue(sb, "Mining Price",
                    String.format("Rs. %,.2f", miningNode.getPriceInRupees()));
        }

        if (currentNode != null && currentNode != miningNode) {
            sb.append("\n");
            appendLabelValue(sb, "Current Stage",    currentNode.getStage().getLabel());
            appendLabelValue(sb, "Current Owner",    currentNode.getPersonName());
            appendLabelValue(sb, "Current Location", currentNode.getLocation());
            appendLabelValue(sb, "Current Weight",
                    String.format("%.4f carats", currentNode.getWeightInCarats()));
            appendLabelValue(sb, "Current Price",
                    String.format("Rs. %,.2f", currentNode.getPriceInRupees()));
            appendLabelValue(sb, "Last Updated",    currentNode.getStageDate().toString());
        }

        appendLabelValue(sb, "Total Stages",    String.valueOf(list.getSize()));
        sb.append("\n");
    }

    /**
     * Builds the full journey section showing every stage node.
     * Traverses the linked list from head to tail and formats
     * each node as a structured block in the report.
     *
     * @param sb   the StringBuilder to append to
     * @param list the GemLinkedList whose stages to include
     */
    private void buildFullJourneySection(StringBuilder sb,
                                         GemLinkedList list) {
        appendSectionTitle(sb, "FULL JOURNEY - STAGE BY STAGE");

        List<GemNode> stages = list.getAllStages();

        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);

            // Stage number header
            appendDivider(sb);
            appendLine(sb, "STAGE " + (i + 1) + " OF " + stages.size()
                    + "  --  " + node.getStage().getLabel().toUpperCase());
            appendDivider(sb);

            // Core fields always printed
            appendLabelValue(sb, "Stage Type",   node.getStage().getLabel());
            appendLabelValue(sb, "Location",     node.getLocation());
            appendLabelValue(sb, "Person",       node.getPersonName());
            appendLabelValue(sb, "Date",         node.getStageDate().toString());
            appendLabelValue(sb, "Weight",
                    String.format("%.4f carats", node.getWeightInCarats()));
            appendLabelValue(sb, "Price",
                    String.format("Rs. %,.2f", node.getPriceInRupees()));

            // Optional fields printed only if set
            if (node.getPersonIdNumber() != null) {
                appendLabelValue(sb, "NIC / Passport", node.getPersonIdNumber());
            }
            if (node.getContactNumber() != null) {
                appendLabelValue(sb, "Contact",        node.getContactNumber());
            }
            if (node.getCertificateNumber() != null) {
                appendLabelValue(sb, "Certificate No", node.getCertificateNumber());
            }
            if (node.getIssuingAuthority() != null) {
                appendLabelValue(sb, "Issued By",      node.getIssuingAuthority());
            }
            if (node.getFlightNumber() != null) {
                appendLabelValue(sb, "Flight No",      node.getFlightNumber());
            }
            if (node.getInvoiceNumber() != null) {
                appendLabelValue(sb, "Invoice No",     node.getInvoiceNumber());
            }
            if (node.getDestinationCountry() != null) {
                appendLabelValue(sb, "Destination",    node.getDestinationCountry());
            }
            if (node.getNotes() != null) {
                appendLabelValue(sb, "Notes",          node.getNotes());
            }

            // Show arrow between stages except after the last one
            if (i < stages.size() - 1) {
                sb.append("\n");
                sb.append("  ").append(" ".repeat(WIDTH / 2)).append("|\n");
                sb.append("  ").append(" ".repeat(WIDTH / 2))
                  .append("v  (next node in linked list)\n");
                sb.append("\n");
            }
        }

        sb.append("\n");
    }

    /**
     * Builds the weight analysis section of the report.
     * Shows the original weight, current weight, total loss,
     * and the loss percentage calculated across all nodes.
     *
     * @param sb   the StringBuilder to append to
     * @param list the GemLinkedList to calculate weight data from
     */
    private void buildWeightAnalysisSection(StringBuilder sb,
                                             GemLinkedList list) {
        appendSectionTitle(sb, "WEIGHT ANALYSIS");

        GemNode miningNode  = list.getMiningNode();
        GemNode currentNode = list.getCurrentStageNode();

        if (miningNode != null) {
            appendLabelValue(sb, "Original Weight",
                    String.format("%.4f carats", miningNode.getWeightInCarats()));
        }
        if (currentNode != null) {
            appendLabelValue(sb, "Current Weight",
                    String.format("%.4f carats", currentNode.getWeightInCarats()));
        }

        appendLabelValue(sb, "Total Weight Lost",
                String.format("%.4f carats", list.calculateWeightLoss()));
        appendLabelValue(sb, "Weight Loss Percentage",
                String.format("%.2f%%", list.calculateWeightLossPercentage()));

        // Show weight at each stage for detailed breakdown
        sb.append("\n");
        appendLine(sb, "Weight at each stage:");

        List<GemNode> stages = list.getAllStages();
        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);
            String stageWeight = "  Stage " + (i + 1)
                    + " - " + node.getStage().getLabel();
            appendLabelValue(sb, stageWeight,
                    String.format("%.4f carats", node.getWeightInCarats()));
        }

        sb.append("\n");
    }

    /**
     * Builds the price analysis section of the report.
     * Shows the price at each stage and the appreciation amounts
     * using data from PriceTracker.
     *
     * @param sb    the StringBuilder to append to
     * @param gemId the ID of the gem to generate price data for
     */
    private void buildPriceAnalysisSection(StringBuilder sb, String gemId) {
        appendSectionTitle(sb, "PRICE APPRECIATION ANALYSIS");

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) return;

        List<PriceTracker.PriceRecord> records =
                priceTracker.getPriceHistory(gemId);

        if (records.isEmpty()) {
            appendLine(sb, "No price data available.");
            sb.append("\n");
            return;
        }

        // Column headers for the price table
        String header = String.format("  %-5s  %-28s  %-14s  %-14s  %-8s",
                "Stage", "Stage Name", "Price (Rs.)",
                "Increase (Rs.)", "Change %");
        sb.append(header).append("\n");
        sb.append("  ").append("-".repeat(WIDTH)).append("\n");

        // One row per stage
        for (PriceTracker.PriceRecord record : records) {
            String row = String.format(
                    "  %-5d  %-28s  %-14s  %-14s  %-8s",
                    record.getStageNumber(),
                    shortenText(record.getStageName(), 28),
                    String.format("Rs. %,.0f", record.getPrice()),
                    record.getStageNumber() == 1
                            ? "  - origin"
                            : String.format("Rs. %,.0f", record.getPriceIncrease()),
                    record.getStageNumber() == 1
                            ? "  -"
                            : String.format("%.1f%%", record.getPercentIncrease()));
            sb.append(row).append("\n");
        }

        sb.append("\n");
        appendLabelValue(sb, "Total Price Appreciation",
                String.format("Rs. %,.2f",
                        priceTracker.getTotalPriceAppreciation(gemId)));
        appendLabelValue(sb, "Total Appreciation Percentage",
                String.format("%.1f%%",
                        priceTracker.getTotalAppreciationPercentage(gemId)));

        // Highest value added stage
        GemNode highestNode = priceTracker.getHighestValueAddedStage(gemId);
        if (highestNode != null) {
            appendLabelValue(sb, "Highest Value Added Stage",
                    highestNode.getStage().getLabel()
                    + " at " + highestNode.getLocation());
        }

        sb.append("\n");
    }

    /**
     * Builds the verification section of the report.
     * Shows the Ceylon origin verification status and lists
     * any fraud alerts that have been generated for this gem.
     *
     * @param sb    the StringBuilder to append to
     * @param gemId the ID of the gem to include verification data for
     */
    private void buildVerificationSection(StringBuilder sb, String gemId) {
        appendSectionTitle(sb, "ORIGIN VERIFICATION STATUS");

        String statusLabel = originVerifier.getVerificationStatusLabel(gemId);
        appendLabelValue(sb, "Verification Status", statusLabel);

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list != null) {
            GemNode miningNode = list.getMiningNode();
            if (miningNode != null) {
                appendLabelValue(sb, "Recorded Origin",
                        miningNode.getLocation());
                appendLabelValue(sb, "Mining Date",
                        miningNode.getStageDate().toString());
            }

            // Certificate status
            List<GemNode> stages = list.getAllStages();
            boolean hasCertificate = false;
            for (GemNode node : stages) {
                if (node.getCertificateNumber() != null
                        && !node.getCertificateNumber().trim().isEmpty()) {
                    hasCertificate = true;
                    appendLabelValue(sb, "Certificate Number",
                            node.getCertificateNumber());
                    appendLabelValue(sb, "Issuing Authority",
                            node.getIssuingAuthority() != null
                                    ? node.getIssuingAuthority()
                                    : "Not recorded");
                    break;
                }
            }

            if (!hasCertificate) {
                appendLabelValue(sb, "Certificate Status",
                        "No certificate recorded for this gem");
            }
        }

        sb.append("\n");
    }

    /**
     * Builds the standard report footer section.
     * Includes a disclaimer and the institution name.
     * Printed at the bottom of every report type.
     *
     * @param sb the StringBuilder to append to
     */
    private void buildReportFooter(StringBuilder sb) {
        buildSectionBorder(sb);
        appendCentred(sb, "END OF REPORT");
        appendEmptyLine(sb);
        appendCentred(sb, "This report was generated by the");
        appendCentred(sb, "Gem Origin Tracking System");
        appendCentred(sb, "National Institute of Business Management");
        appendCentred(sb, "HND Software Engineering - PDSA Coursework");
        appendEmptyLine(sb);
        appendCentred(sb, "Report generated on: " + getCurrentReadableDateTime());
        buildSectionBorder(sb);
    }

    // ---------------------------------------------------------
    // File saving
    // ---------------------------------------------------------

    /**
     * Saves the generated report content to a text file.
     * Uses a BufferedWriter for efficient writing of large reports.
     * The file is created if it does not exist and overwritten
     * if it already exists.
     *
     * @param content  the full report text to write to the file
     * @param filePath the full path where the file should be saved
     * @return true if the file was saved successfully, false otherwise
     */
    private boolean saveReportToFile(String content, String filePath) {
        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(filePath))) {
            writer.write(content);
            writer.flush();
            return true;
        } catch (IOException e) {
            System.out.println("  Failed to save report to: " + filePath);
            e.printStackTrace();
            return false;
        }
    }

    // ---------------------------------------------------------
    // Report printing to console
    // ---------------------------------------------------------

    /**
     * Prints a previously generated report file to the console.
     * Reads the file content and prints it line by line.
     * Used when a user wants to view a report without opening a file.
     *
     * @param filePath the path of the report file to print
     */
    public void printReportToConsole(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("  Report file not found: " + filePath);
            return;
        }

        try (java.io.BufferedReader reader =
                     new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println("  Failed to read report file: " + filePath);
            e.printStackTrace();
        }
    }

    /**
     * Lists all report files currently saved in the reports folder.
     * Used in the UI to show the user which reports are available.
     *
     * @return an array of report file names, or empty array if none
     */
    public String[] listSavedReports() {
        File folder = new File(REPORT_FOLDER);
        if (!folder.exists() || !folder.isDirectory()) {
            return new String[0];
        }

        String[] files = folder.list((dir, name) -> name.endsWith(".txt"));
        return files != null ? files : new String[0];
    }

    /**
     * Displays a list of all saved reports to the console.
     * Shows the file name and the index number for easy selection.
     */
    public void displaySavedReports() {
        String[] reports = listSavedReports();

        System.out.println("💎 Saved Reports in: " + REPORT_FOLDER);
        System.out.println();

        if (reports.length == 0) {
            System.out.println("  No reports found.");
            return;
        }

        for (int i = 0; i < reports.length; i++) {
            System.out.println("  " + (i + 1) + ". " + reports[i]);
        }
    }

    // ---------------------------------------------------------
    // String building helpers
    // ---------------------------------------------------------

    /**
     * Appends a full-width border line made of dashes.
     * Used at the start and end of major report sections.
     *
     * @param sb the StringBuilder to append to
     */
    private void buildSectionBorder(StringBuilder sb) {
        sb.append("  +").append("-".repeat(WIDTH)).append("+\n");
    }

    /**
     * Appends a horizontal divider line inside a section.
     * Used to separate groups of fields within a section.
     *
     * @param sb the StringBuilder to append to
     */
    private void appendDivider(StringBuilder sb) {
        sb.append("  ").append("-".repeat(WIDTH)).append("\n");
    }

    /**
     * Appends an empty line for vertical spacing in the report.
     *
     * @param sb the StringBuilder to append to
     */
    private void appendEmptyLine(StringBuilder sb) {
        sb.append("  |").append(" ".repeat(WIDTH)).append("|\n");
    }

    /**
     * Appends a section title formatted with surrounding dashes.
     * Used to clearly label each major section of the report.
     *
     * @param sb    the StringBuilder to append to
     * @param title the section title to display
     */
    private void appendSectionTitle(StringBuilder sb, String title) {
        sb.append("\n");
        sb.append("  ").append("=".repeat(WIDTH)).append("\n");
        sb.append("  ").append(title).append("\n");
        sb.append("  ").append("=".repeat(WIDTH)).append("\n");
        sb.append("\n");
    }

    /**
     * Appends a label and value pair on the same line.
     * The label is left-aligned and the value follows after a colon.
     * Used for all data fields throughout the report.
     *
     * @param sb    the StringBuilder to append to
     * @param label the field name
     * @param value the field value
     */
    private void appendLabelValue(StringBuilder sb,
                                   String label,
                                   String value) {
        sb.append("  ").append(label).append(" : ").append(value).append("\n");
    }

    /**
     * Appends a plain text line to the report.
     * Used for standalone lines that are not label-value pairs.
     *
     * @param sb   the StringBuilder to append to
     * @param line the text line to append
     */
    private void appendLine(StringBuilder sb, String line) {
        sb.append("  ").append(line).append("\n");
    }

    /**
     * Appends text centred within the report width.
     * Used in the header and footer sections for titles.
     *
     * @param sb   the StringBuilder to append to
     * @param text the text to centre and append
     */
    private void appendCentred(StringBuilder sb, String text) {
        int padding  = WIDTH - text.length();
        int leftPad  = Math.max(0, padding / 2);
        int rightPad = Math.max(0, padding - leftPad);
        sb.append("  |")
          .append(" ".repeat(leftPad))
          .append(text)
          .append(" ".repeat(rightPad))
          .append("|\n");
    }

    // ---------------------------------------------------------
    // Utility helpers
    // ---------------------------------------------------------

    /**
     * Returns the current date and time as a timestamp string
     * formatted for use in file names.
     * Uses the TIMESTAMP_FORMAT constant for consistent naming.
     *
     * @return a timestamp string safe for use in file names
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT));
    }

    /**
     * Returns the current date and time as a human readable string
     * for display in report headers and footers.
     *
     * @return a readable date and time string
     */
    private String getCurrentReadableDateTime() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm:ss"));
    }

    /**
     * Shortens a text string to a maximum length for table formatting.
     * Appends "..." if the text exceeds the maximum length to indicate
     * that it has been truncated.
     *
     * @param text      the original text string
     * @param maxLength the maximum number of characters allowed
     * @return the text truncated with "..." if it exceeds maxLength
     */
    private String shortenText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}