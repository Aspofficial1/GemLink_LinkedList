package service;

import model.GemLinkedList;
import model.GemNode;
import model.GemStage;

import java.util.ArrayList;
import java.util.List;

/**
 * PriceTracker is responsible for tracking and analysing the price
 * appreciation of a gemstone at every stage of its journey.
 *
 * This is novel feature 2 of the project.
 * The gem industry has a well known problem where the value added
 * at each stage of the supply chain is not transparent to buyers.
 * A miner may sell a gem for Rs. 10,000 but the final buyer pays
 * $4,200 with no understanding of where the value was added.
 *
 * PriceTracker solves this by traversing the doubly linked list
 * and calculating the exact price increase at each node, showing
 * buyers and traders a clear picture of the gem's value journey.
 *
 * The doubly linked list is essential here because we need to
 * compare each node's price with the previous node's price,
 * which requires backwards access through the prev pointer.
 */
public class PriceTracker {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The TrackingService used to retrieve gem linked lists
     * when price analysis is requested by gem ID.
     */
    private TrackingService trackingService;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new PriceTracker with a reference to the TrackingService.
     * The TrackingService provides access to gem linked lists without
     * PriceTracker needing to talk to the database directly.
     *
     * @param trackingService the TrackingService instance to use
     */
    public PriceTracker(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // ---------------------------------------------------------
    // Core price analysis methods
    // ---------------------------------------------------------

    /**
     * Calculates and returns a list of PriceRecord objects,
     * one for each stage in the gem's journey.
     *
     * Each PriceRecord holds the stage name, price at that stage,
     * the price increase from the previous stage, and the percentage
     * increase. This data is used to display the price chart and
     * the stage-by-stage appreciation table in the UI.
     *
     * The traversal goes forward from head to tail through the linked
     * list, comparing each node's price to the previous node's price
     * using the prev pointer of the doubly linked list.
     *
     * @param gemId the ID of the gem to analyse
     * @return a List of PriceRecord objects, or empty list if not found
     */
    public List<PriceRecord> getPriceHistory(String gemId) {
        List<PriceRecord> records = new ArrayList<>();

        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found for price analysis: " + gemId);
            return records;
        }

        List<GemNode> stages = list.getAllStages();
        if (stages.isEmpty()) {
            System.out.println("No stages found for gem: " + gemId);
            return records;
        }

        // Build a PriceRecord for each stage node
        for (int i = 0; i < stages.size(); i++) {
            GemNode currentNode = stages.get(i);

            double currentPrice    = currentNode.getPriceInRupees();
            double previousPrice   = (i == 0) ? 0 : stages.get(i - 1).getPriceInRupees();
            double priceIncrease   = currentPrice - previousPrice;

            // Calculate percentage increase from the previous stage
            // Avoid division by zero for the first node (mining stage)
            double percentIncrease = 0;
            if (previousPrice > 0) {
                percentIncrease = (priceIncrease / previousPrice) * 100;
            }

            PriceRecord record = new PriceRecord(
                    currentNode.getStage().getLabel(),
                    currentNode.getLocation(),
                    currentNode.getPersonName(),
                    currentNode.getStageDate().toString(),
                    currentPrice,
                    priceIncrease,
                    percentIncrease,
                    i + 1
            );

            records.add(record);
        }

        return records;
    }

    /**
     * Returns the total price appreciation from the mining stage
     * to the most recent stage of the gem's journey.
     *
     * This single number shows how much value the gem has gained
     * from the moment it was discovered to its current state.
     *
     * @param gemId the ID of the gem to analyse
     * @return total price increase in rupees, or 0 if not enough data
     */
    public double getTotalPriceAppreciation(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) return 0;
        return list.calculatePriceAppreciation();
    }

    /**
     * Returns the total price appreciation as a percentage of the
     * original mining price.
     *
     * For example, if the mining price was Rs. 10,000 and the current
     * price is Rs. 350,000, the appreciation is 3400%.
     * This percentage helps international buyers understand how much
     * value Sri Lanka adds to its gems through the supply chain.
     *
     * @param gemId the ID of the gem to analyse
     * @return total appreciation as a percentage, or 0 if not enough data
     */
    public double getTotalAppreciationPercentage(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) return 0;

        GemNode miningNode = list.getMiningNode();
        if (miningNode == null || miningNode.getPriceInRupees() == 0) return 0;

        double originalPrice    = miningNode.getPriceInRupees();
        double totalAppreciation = list.calculatePriceAppreciation();

        return (totalAppreciation / originalPrice) * 100;
    }

    /**
     * Returns the stage at which the gem gained the most value.
     * This is found by traversing all nodes and finding the one
     * with the highest price increase compared to its previous node.
     *
     * Knowing the highest value-adding stage helps traders understand
     * which part of the supply chain contributes the most to the price.
     *
     * @param gemId the ID of the gem to analyse
     * @return the GemNode where the largest price increase occurred,
     *         or null if not enough data
     */
    public GemNode getHighestValueAddedStage(String gemId) {
        List<PriceRecord> records = getPriceHistory(gemId);
        if (records.size() < 2) return null;

        GemLinkedList list   = trackingService.getGemList(gemId);
        List<GemNode> stages = list.getAllStages();

        double highestIncrease = Double.MIN_VALUE;
        int    highestIndex    = 1;

        // Start from index 1 because index 0 has no previous stage to compare
        for (int i = 1; i < records.size(); i++) {
            if (records.get(i).getPriceIncrease() > highestIncrease) {
                highestIncrease = records.get(i).getPriceIncrease();
                highestIndex    = i;
            }
        }

        return stages.get(highestIndex);
    }

    /**
     * Returns the stage at which the gem gained the least value
     * or lost value (negative increase).
     * Used to identify bottlenecks or undervalued stages in the
     * supply chain.
     *
     * @param gemId the ID of the gem to analyse
     * @return the GemNode where the smallest price increase occurred,
     *         or null if not enough data
     */
    public GemNode getLowestValueAddedStage(String gemId) {
        List<PriceRecord> records = getPriceHistory(gemId);
        if (records.size() < 2) return null;

        GemLinkedList list   = trackingService.getGemList(gemId);
        List<GemNode> stages = list.getAllStages();

        double lowestIncrease = Double.MAX_VALUE;
        int    lowestIndex    = 1;

        // Start from index 1 because index 0 has no previous stage to compare
        for (int i = 1; i < records.size(); i++) {
            if (records.get(i).getPriceIncrease() < lowestIncrease) {
                lowestIncrease = records.get(i).getPriceIncrease();
                lowestIndex    = i;
            }
        }

        return stages.get(lowestIndex);
    }

    // ---------------------------------------------------------
    // Display methods
    // ---------------------------------------------------------

    /**
     * Prints a full price appreciation report to the console.
     * Shows every stage with its price, the increase from the
     * previous stage, and the percentage increase.
     *
     * A visual bar is drawn next to each stage using repeated
     * characters to give a rough chart-like appearance in the
     * console output.
     *
     * This display is used by JourneyViewer when a user selects
     * the price analysis option from the main menu.
     *
     * @param gemId the ID of the gem to display the report for
     */
    public void displayPriceReport(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return;
        }

        List<PriceRecord> records = getPriceHistory(gemId);
        if (records.isEmpty()) {
            System.out.println("No price data available for Gem ID: " + gemId);
            return;
        }

        GemNode miningNode = list.getMiningNode();

        System.out.println();
        System.out.println("💎 Price Appreciation Report");
        System.out.println("  Gem ID   : " + gemId);
        if (miningNode != null) {
            System.out.println("  Gem Type : " + miningNode.getGemType());
        }
        System.out.println();

        // Print the column headers for the report table
        System.out.printf("  %-5s  %-30s  %-15s  %-15s  %-10s%n",
                "Stage", "Stage Name", "Price (Rs.)", "Increase (Rs.)", "Increase %");
        System.out.println("  "
                + "-".repeat(5)  + "  "
                + "-".repeat(30) + "  "
                + "-".repeat(15) + "  "
                + "-".repeat(15) + "  "
                + "-".repeat(10));

        // Print one row per stage
        for (PriceRecord record : records) {
            System.out.printf("  %-5d  %-30s  %-15s  %-15s  %-10s%n",
                    record.getStageNumber(),
                    record.getStageName(),
                    String.format("Rs. %,.2f", record.getPrice()),
                    record.getStageNumber() == 1
                            ? "  - (origin)"
                            : String.format("Rs. %,.2f", record.getPriceIncrease()),
                    record.getStageNumber() == 1
                            ? "  -"
                            : String.format("%.1f%%", record.getPercentIncrease()));
        }

        System.out.println();

        // Print the summary totals at the bottom
        System.out.printf("  Total Price Appreciation : Rs. %,.2f%n",
                getTotalPriceAppreciation(gemId));
        System.out.printf("  Total Appreciation       : %.1f%%%n",
                getTotalAppreciationPercentage(gemId));

        // Print the highest value added stage
        GemNode highestNode = getHighestValueAddedStage(gemId);
        if (highestNode != null) {
            System.out.println("  Highest Value Stage      : "
                    + highestNode.getStage().getLabel()
                    + " at " + highestNode.getLocation());
        }

        System.out.println();
        displayConsoleBarChart(gemId);
    }

    /**
     * Prints a simple horizontal bar chart to the console showing
     * the relative price at each stage of the gem's journey.
     *
     * Each bar is made of repeated characters whose length is
     * proportional to the price at that stage relative to the
     * highest price in the journey. This gives a quick visual
     * overview of how the gem's value grows across stages.
     *
     * @param gemId the ID of the gem to display the chart for
     */
    public void displayConsoleBarChart(String gemId) {
        List<PriceRecord> records = getPriceHistory(gemId);
        if (records.isEmpty()) return;

        // Find the maximum price to scale all bars relative to it
        double maxPrice = 0;
        for (PriceRecord record : records) {
            if (record.getPrice() > maxPrice) {
                maxPrice = record.getPrice();
            }
        }

        if (maxPrice == 0) return;

        System.out.println("  Price Chart (each symbol = relative value)");
        System.out.println();

        int maxBarLength = 40;

        for (PriceRecord record : records) {
            // Calculate bar length proportional to the maximum price
            int barLength = (int) ((record.getPrice() / maxPrice) * maxBarLength);
            if (barLength < 1) barLength = 1;

            // Build the bar string using repeated characters
            String bar = "#".repeat(barLength);

            System.out.printf("  Stage %-2d  %-22s  |%-40s|  Rs. %,.0f%n",
                    record.getStageNumber(),
                    shortenLabel(record.getStageName(), 22),
                    bar,
                    record.getPrice());
        }

        System.out.println();
    }

    /**
     * Displays a short comparison of two gems' price appreciation.
     * Shows which gem appreciated more in absolute and percentage terms.
     * Used when a trader wants to compare the value growth of two gems.
     *
     * @param gemId1 the ID of the first gem
     * @param gemId2 the ID of the second gem
     */
    public void compareTwoGems(String gemId1, String gemId2) {
        GemLinkedList list1 = trackingService.getGemList(gemId1);
        GemLinkedList list2 = trackingService.getGemList(gemId2);

        if (list1 == null) {
            System.out.println("Gem not found: " + gemId1);
            return;
        }
        if (list2 == null) {
            System.out.println("Gem not found: " + gemId2);
            return;
        }

        double appreciation1 = getTotalPriceAppreciation(gemId1);
        double appreciation2 = getTotalPriceAppreciation(gemId2);
        double percentage1   = getTotalAppreciationPercentage(gemId1);
        double percentage2   = getTotalAppreciationPercentage(gemId2);

        System.out.println();
        System.out.println("💎 Price Appreciation Comparison");
        System.out.println();
        System.out.printf("  %-30s  %-15s  %-15s%n",
                "Metric", gemId1, gemId2);
        System.out.println("  " + "-".repeat(30) + "  "
                + "-".repeat(15) + "  " + "-".repeat(15));

        // Mining price
        GemNode mine1 = list1.getMiningNode();
        GemNode mine2 = list2.getMiningNode();
        System.out.printf("  %-30s  Rs. %-11,.0f  Rs. %-11,.0f%n",
                "Mining Price",
                mine1 != null ? mine1.getPriceInRupees() : 0,
                mine2 != null ? mine2.getPriceInRupees() : 0);

        // Current price
        GemNode curr1 = list1.getCurrentStageNode();
        GemNode curr2 = list2.getCurrentStageNode();
        System.out.printf("  %-30s  Rs. %-11,.0f  Rs. %-11,.0f%n",
                "Current Price",
                curr1 != null ? curr1.getPriceInRupees() : 0,
                curr2 != null ? curr2.getPriceInRupees() : 0);

        // Total appreciation
        System.out.printf("  %-30s  Rs. %-11,.0f  Rs. %-11,.0f%n",
                "Total Appreciation",
                appreciation1, appreciation2);

        // Percentage appreciation
        System.out.printf("  %-30s  %-14.1f%%  %-14.1f%%%n",
                "Appreciation Percentage",
                percentage1, percentage2);

        // Number of stages
        System.out.printf("  %-30s  %-15d  %-15d%n",
                "Number of Stages",
                list1.getSize(), list2.getSize());

        System.out.println();

        // Determine which gem performed better
        if (appreciation1 > appreciation2) {
            System.out.println("  Result: Gem " + gemId1
                    + " has higher total price appreciation.");
        } else if (appreciation2 > appreciation1) {
            System.out.println("  Result: Gem " + gemId2
                    + " has higher total price appreciation.");
        } else {
            System.out.println("  Result: Both gems have equal price appreciation.");
        }
    }

    /**
     * Prints a price summary for a specific stage of a gem's journey.
     * Used when a user wants to see the price details of just one stage
     * without viewing the entire journey report.
     *
     * @param gemId       the ID of the gem
     * @param stageNumber the stage number to display (1-based)
     */
    public void displayStagePriceSummary(String gemId, int stageNumber) {
        List<PriceRecord> records = getPriceHistory(gemId);

        if (records.isEmpty()) {
            System.out.println("No price records found for Gem: " + gemId);
            return;
        }

        if (stageNumber < 1 || stageNumber > records.size()) {
            System.out.println("Invalid stage number. Valid range: 1 to "
                    + records.size());
            return;
        }

        PriceRecord record = records.get(stageNumber - 1);

        System.out.println();
        System.out.println("💎 Stage Price Summary");
        System.out.println("  Gem ID         : " + gemId);
        System.out.println("  Stage Number   : " + record.getStageNumber());
        System.out.println("  Stage Name     : " + record.getStageName());
        System.out.println("  Location       : " + record.getLocation());
        System.out.println("  Person         : " + record.getPersonName());
        System.out.println("  Date           : " + record.getDate());
        System.out.printf ("  Price          : Rs. %,.2f%n", record.getPrice());

        if (stageNumber > 1) {
            System.out.printf("  Price Increase : Rs. %,.2f%n",
                    record.getPriceIncrease());
            System.out.printf("  Increase       : %.1f%%%n",
                    record.getPercentIncrease());
        } else {
            System.out.println("  Price Increase : - (this is the origin stage)");
        }
    }

    // ---------------------------------------------------------
    // Utility helpers
    // ---------------------------------------------------------

    /**
     * Shortens a label string to a maximum length for display.
     * Used when printing the bar chart to prevent stage names
     * from breaking the table alignment.
     *
     * @param label     the original label string
     * @param maxLength the maximum number of characters allowed
     * @return the label truncated with "..." if it exceeds maxLength
     */
    private String shortenLabel(String label, int maxLength) {
        if (label == null) return "";
        if (label.length() <= maxLength) return label;
        return label.substring(0, maxLength - 3) + "...";
    }

    // ---------------------------------------------------------
    // Inner class - PriceRecord
    // ---------------------------------------------------------

    /**
     * PriceRecord is a simple data container that holds the price
     * information for one stage in a gem's journey.
     *
     * It is defined as an inner class of PriceTracker because it is
     * only used by PriceTracker and has no meaning outside of it.
     * Using an inner class keeps the related code in one place and
     * avoids creating a separate file for a small data holder.
     */
    public static class PriceRecord {

        /** The display name of the stage e.g. "Mining Stage". */
        private String stageName;

        /** The location where this stage took place. */
        private String location;

        /** The name of the person responsible at this stage. */
        private String personName;

        /** The date this stage occurred as a string. */
        private String date;

        /** The price of the gem at this stage in rupees. */
        private double price;

        /** The price increase from the previous stage in rupees. */
        private double priceIncrease;

        /** The price increase as a percentage of the previous stage price. */
        private double percentIncrease;

        /** The position of this stage in the journey, starting from 1. */
        private int stageNumber;

        /**
         * Creates a new PriceRecord with all required fields.
         *
         * @param stageName       the name of the stage
         * @param location        where this stage took place
         * @param personName      who was responsible at this stage
         * @param date            when this stage occurred
         * @param price           the gem price at this stage
         * @param priceIncrease   the increase from the previous stage
         * @param percentIncrease the percentage increase
         * @param stageNumber     the position in the journey
         */
        public PriceRecord(String stageName, String location,
                           String personName, String date,
                           double price, double priceIncrease,
                           double percentIncrease, int stageNumber) {
            this.stageName       = stageName;
            this.location        = location;
            this.personName      = personName;
            this.date            = date;
            this.price           = price;
            this.priceIncrease   = priceIncrease;
            this.percentIncrease = percentIncrease;
            this.stageNumber     = stageNumber;
        }

        /** Returns the stage name. */
        public String getStageName()       { return stageName;       }

        /** Returns the location of this stage. */
        public String getLocation()        { return location;        }

        /** Returns the person name at this stage. */
        public String getPersonName()      { return personName;      }

        /** Returns the date of this stage. */
        public String getDate()            { return date;            }

        /** Returns the gem price at this stage. */
        public double getPrice()           { return price;           }

        /** Returns the price increase from the previous stage. */
        public double getPriceIncrease()   { return priceIncrease;   }

        /** Returns the percentage increase from the previous stage. */
        public double getPercentIncrease() { return percentIncrease; }

        /** Returns the position of this stage in the journey. */
        public int    getStageNumber()     { return stageNumber;     }
    }
}