package model;

import java.time.LocalDate;

/**
 * GemNode represents a single stage in a gemstone's journey.
 * Each node in the doubly linked list holds all data recorded
 * at that particular stage, plus pointers to the next and
 * previous nodes to allow traversal in both directions.
 *
 * A doubly linked list is used instead of a singly linked list
 * because we need to traverse backwards when generating reports
 * and verifying the chain of custody from buyer back to mine.
 */
public class GemNode {

    // ---------------------------------------------------------
    // Core stage identification
    // ---------------------------------------------------------

    /**
     * The type of stage this node represents.
     * Uses the GemStage enum to prevent invalid stage values.
     */
    private GemStage stage;

    /**
     * A unique ID for this specific gem, shared across all nodes
     * in the same chain. Used to group nodes belonging to one gem.
     */
    private String gemId;

    /**
     * The type of gem, e.g. Blue Sapphire, Ruby, Cat's Eye.
     * Stored in each node so the report can display it at every stage.
     */
    private String gemType;

    // ---------------------------------------------------------
    // Location and person details
    // ---------------------------------------------------------

    /**
     * The physical location where this stage took place.
     * e.g. "Ratnapura" for mining, "Beruwala" for cutting.
     */
    private String location;

    /**
     * The name of the person responsible at this stage.
     * Could be a miner, cutter, trader, exporter, or buyer.
     */
    private String personName;

    /**
     * The national ID or passport number of the person at this stage.
     * Stored for identity verification purposes.
     */
    private String personIdNumber;

    /**
     * The contact number of the person at this stage.
     * Useful for tracing ownership disputes.
     */
    private String contactNumber;

    // ---------------------------------------------------------
    // Weight and value details
    // ---------------------------------------------------------

    /**
     * The weight of the gem in carats at this specific stage.
     * Weight changes after cutting, so each node records its own weight.
     */
    private double weightInCarats;

    /**
     * The price of the gem at this stage in Sri Lankan Rupees.
     * Used by PriceTracker to show value appreciation across nodes.
     */
    private double priceInRupees;

    // ---------------------------------------------------------
    // Date and certificate details
    // ---------------------------------------------------------

    /**
     * The date on which this stage occurred.
     * LocalDate is used instead of String to allow date comparisons.
     */
    private LocalDate stageDate;

    /**
     * The certificate number issued at this stage, if any.
     * Typically recorded at the trading or exporting stage.
     */
    private String certificateNumber;

    /**
     * The name of the authority that issued the certificate.
     * e.g. "National Gem and Jewellery Authority of Sri Lanka".
     */
    private String issuingAuthority;

    // ---------------------------------------------------------
    // Export-specific details
    // ---------------------------------------------------------

    /**
     * The flight number used when exporting the gem.
     * Only relevant at the EXPORTING stage, left null otherwise.
     */
    private String flightNumber;

    /**
     * The invoice number generated at the export stage.
     * Used for customs and tax documentation.
     */
    private String invoiceNumber;

    /**
     * The destination country for export.
     * e.g. "Dubai", "Bangkok", "New York".
     */
    private String destinationCountry;

    // ---------------------------------------------------------
    // Additional notes
    // ---------------------------------------------------------

    /**
     * Any extra notes the user wants to record at this stage.
     * Provides flexibility for information that does not fit
     * into the other fields.
     */
    private String notes;

    // ---------------------------------------------------------
    // Doubly linked list pointers
    // ---------------------------------------------------------

    /**
     * Pointer to the next node in the chain.
     * Points to the stage that happened after this one.
     * Null if this is the last stage (current owner).
     */
    public GemNode next;

    /**
     * Pointer to the previous node in the chain.
     * Points to the stage that happened before this one.
     * Null if this is the first stage (mining).
     * This is what makes it a doubly linked list, allowing
     * backwards traversal which is not possible in a singly linked list.
     */
    public GemNode prev;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new GemNode with the minimum required information.
     * All other fields can be set using their respective setter methods
     * after the node is created.
     *
     * @param gemId       the unique ID of the gem this node belongs to
     * @param gemType     the type of gem e.g. Blue Sapphire
     * @param stage       the stage type from the GemStage enum
     * @param location    where this stage took place
     * @param personName  who was responsible at this stage
     * @param weightInCarats the weight of the gem at this stage
     * @param priceInRupees  the value of the gem at this stage
     * @param stageDate   the date this stage occurred
     */
    public GemNode(String gemId, String gemType, GemStage stage,
                   String location, String personName,
                   double weightInCarats, double priceInRupees,
                   LocalDate stageDate) {

        this.gemId          = gemId;
        this.gemType        = gemType;
        this.stage          = stage;
        this.location       = location;
        this.personName     = personName;
        this.weightInCarats = weightInCarats;
        this.priceInRupees  = priceInRupees;
        this.stageDate      = stageDate;

        // New nodes always start unlinked
        this.next = null;
        this.prev = null;
    }

    // ---------------------------------------------------------
    // Getters
    // ---------------------------------------------------------

    /** Returns the stage type of this node. */
    public GemStage getStage() { return stage; }

    /** Returns the unique gem ID shared across the chain. */
    public String getGemId() { return gemId; }

    /** Returns the type of gem recorded at this stage. */
    public String getGemType() { return gemType; }

    /** Returns the location where this stage took place. */
    public String getLocation() { return location; }

    /** Returns the name of the person at this stage. */
    public String getPersonName() { return personName; }

    /** Returns the ID number of the person at this stage. */
    public String getPersonIdNumber() { return personIdNumber; }

    /** Returns the contact number of the person at this stage. */
    public String getContactNumber() { return contactNumber; }

    /** Returns the gem weight in carats at this stage. */
    public double getWeightInCarats() { return weightInCarats; }

    /** Returns the gem price in rupees at this stage. */
    public double getPriceInRupees() { return priceInRupees; }

    /** Returns the date this stage occurred. */
    public LocalDate getStageDate() { return stageDate; }

    /** Returns the certificate number recorded at this stage. */
    public String getCertificateNumber() { return certificateNumber; }

    /** Returns the name of the certificate issuing authority. */
    public String getIssuingAuthority() { return issuingAuthority; }

    /** Returns the flight number used during export. */
    public String getFlightNumber() { return flightNumber; }

    /** Returns the invoice number generated at export. */
    public String getInvoiceNumber() { return invoiceNumber; }

    /** Returns the destination country for export. */
    public String getDestinationCountry() { return destinationCountry; }

    /** Returns any additional notes recorded at this stage. */
    public String getNotes() { return notes; }

    // ---------------------------------------------------------
    // Setters
    // ---------------------------------------------------------

    /** Sets the ID number of the person at this stage. */
    public void setPersonIdNumber(String personIdNumber) {
        this.personIdNumber = personIdNumber;
    }

    /** Sets the contact number of the person at this stage. */
    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    /** Sets the certificate number for this stage. */
    public void setCertificateNumber(String certificateNumber) {
        this.certificateNumber = certificateNumber;
    }

    /** Sets the name of the certificate issuing authority. */
    public void setIssuingAuthority(String issuingAuthority) {
        this.issuingAuthority = issuingAuthority;
    }

    /** Sets the flight number used during export. */
    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    /** Sets the invoice number generated at export. */
    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    /** Sets the destination country for export. */
    public void setDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
    }

    /** Sets any additional notes for this stage. */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /** Updates the price at this stage, used by PriceTracker. */
    public void setPriceInRupees(double priceInRupees) {
        this.priceInRupees = priceInRupees;
    }

    // ---------------------------------------------------------
    // Display helper
    // ---------------------------------------------------------

    /**
     * Returns a formatted summary of this node's data.
     * Used by JourneyViewer to print each stage cleanly.
     * Each field is printed on its own line for readability.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Stage        : ").append(stage.getLabel()).append("\n");
        sb.append("  Gem ID       : ").append(gemId).append("\n");
        sb.append("  Gem Type     : ").append(gemType).append("\n");
        sb.append("  Location     : ").append(location).append("\n");
        sb.append("  Person       : ").append(personName).append("\n");
        sb.append("  Weight       : ").append(weightInCarats).append(" carats\n");
        sb.append("  Price        : Rs. ").append(priceInRupees).append("\n");
        sb.append("  Date         : ").append(stageDate).append("\n");

        // Only print optional fields if they have been set
        if (certificateNumber != null)
            sb.append("  Certificate  : ").append(certificateNumber).append("\n");
        if (issuingAuthority != null)
            sb.append("  Authority    : ").append(issuingAuthority).append("\n");
        if (flightNumber != null)
            sb.append("  Flight No    : ").append(flightNumber).append("\n");
        if (invoiceNumber != null)
            sb.append("  Invoice No   : ").append(invoiceNumber).append("\n");
        if (destinationCountry != null)
            sb.append("  Destination  : ").append(destinationCountry).append("\n");
        if (notes != null)
            sb.append("  Notes        : ").append(notes).append("\n");

        return sb.toString();
    }
}