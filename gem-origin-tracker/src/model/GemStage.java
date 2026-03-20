package model;
/**
 * GemStage defines the possible stages in a gemstone's journey.
 * Using an enum here ensures only valid stage types can be assigned
 * to a node, preventing invalid data entry in the tracking chain.
 */
public enum GemStage {

    /**
     * The first stage - where the gem is physically extracted from the earth.
     * Every gem's linked list must start with this stage.
     */
    MINING,

    /**
     * The cutting and polishing stage.
     * Weight loss occurs here, which the system tracks for value assessment.
     */
    CUTTING,

    /**
     * The trading stage where the gem changes ownership locally.
     * Certificate details are typically recorded at this stage.
     */
    TRADING,

    /**
     * The export stage before the gem leaves Sri Lanka.
     * Flight and invoice details are captured here.
     */
    EXPORTING,

    /**
     * The final stage - the gem reaches its international buyer.
     * This is the tail node of the linked list.
     */
    BUYING;

    /**
     * Returns a clean, readable label for each stage.
     * Used when printing the gem journey to the console or report.
     */
    public String getLabel() {
        switch (this) {
            case MINING:    return "Mining Stage";
            case CUTTING:   return "Cutting and Polishing Stage";
            case TRADING:   return "Trading Stage";
            case EXPORTING: return "Exporting Stage";
            case BUYING:    return "Final Buyer Stage";
            default:        return "Unknown Stage";
        }
    }

    /**
     * Returns a short location hint for each stage.
     * Helps users understand which part of the supply chain they are entering.
     */
    public String getLocationHint() {
        switch (this) {
            case MINING:    return "e.g. Ratnapura, Elahera, Okanda";
            case CUTTING:   return "e.g. Beruwala, Colombo";
            case TRADING:   return "e.g. Colombo Gem Bureau";
            case EXPORTING: return "e.g. Katunayake Airport";
            case BUYING:    return "e.g. Dubai, Bangkok, New York";
            default:        return "";
        }
    }
}

// by using an enum, the rest of your code can never accidentally create a stage called "minnig" or "buyer" by typo. Every other class will import this.