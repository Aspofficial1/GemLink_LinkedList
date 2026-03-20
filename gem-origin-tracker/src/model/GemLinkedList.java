package model;

import java.util.ArrayList;
import java.util.List;

/**
 * GemLinkedList is the core data structure of this entire project.
 * It implements a doubly linked list where each node represents
 * one stage in a gemstone's journey from mine to final buyer.
 *
 * A doubly linked list was chosen over a singly linked list because:
 * 1. We can traverse forward to show the full journey chronologically.
 * 2. We can traverse backward to verify the chain from buyer to mine.
 * 3. We can delete any middle node without losing the chain integrity.
 *
 * Each GemLinkedList instance represents ONE gem's complete journey.
 */
public class GemLinkedList {

    /**
     * The head node - always the MINING stage.
     * This is where every gem's journey begins.
     * If head is null, the gem has not been registered yet.
     */
    private GemNode head;

    /**
     * The tail node - always the most recent stage.
     * Keeping a tail pointer allows us to add new stages
     * in O(1) time without traversing the whole list.
     */
    private GemNode tail;

    /**
     * The total number of stages currently in this gem's journey.
     * Tracked manually to avoid counting nodes every time we need the size.
     */
    private int size;

    /**
     * The unique ID of the gem this linked list belongs to.
     * Every node in this list shares this same gem ID.
     */
    private String gemId;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates an empty linked list for a specific gem.
     * The first node must be added using addStage() after construction.
     *
     * @param gemId the unique identifier for this gem
     */
    public GemLinkedList(String gemId) {
        this.gemId = gemId;
        this.head  = null;
        this.tail  = null;
        this.size  = 0;
    }

    // ---------------------------------------------------------
    // Core linked list operations
    // ---------------------------------------------------------

    /**
     * Adds a new stage node to the end of the linked list.
     * This is the primary operation used throughout the system
     * whenever a gem moves to the next stage of its journey.
     *
     * The new node becomes the new tail, and its prev pointer
     * is set to the old tail, maintaining the doubly linked structure.
     *
     * @param newNode the GemNode to add at the end of the chain
     */
    public void addStage(GemNode newNode) {
        if (newNode == null) {
            System.out.println("Cannot add a null node to the gem journey.");
            return;
        }

        if (head == null) {
            // First node added - it becomes both head and tail
            head = newNode;
            tail = newNode;
            newNode.prev = null;
            newNode.next = null;
        } else {
            // Link the new node after the current tail
            tail.next    = newNode;
            newNode.prev = tail;
            newNode.next = null;
            tail         = newNode;
        }

        size++;
        System.out.println("💎 Stage added: " + newNode.getStage().getLabel()
                + " for Gem ID: " + gemId);
    }

    /**
     * Inserts a new stage node at a specific position in the list.
     * Position index starts at 0 (head).
     *
     * This is used when a missed stage needs to be inserted between
     * existing stages, for example adding a broker node between
     * a cutter node and a trader node.
     *
     * @param newNode  the GemNode to insert
     * @param position the index at which to insert (0 = before head)
     */
    public void insertStageAt(GemNode newNode, int position) {
        if (newNode == null) {
            System.out.println("Cannot insert a null node.");
            return;
        }

        // If position is 0 or list is empty, insert at the beginning
        if (position <= 0 || head == null) {
            newNode.next = head;
            newNode.prev = null;
            if (head != null) {
                head.prev = newNode;
            }
            head = newNode;
            if (tail == null) {
                tail = newNode;
            }
            size++;
            return;
        }

        // If position is beyond the list size, add at the end
        if (position >= size) {
            addStage(newNode);
            return;
        }

        // Traverse to the node just before the desired position
        GemNode current = head;
        int index = 0;
        while (current != null && index < position - 1) {
            current = current.next;
            index++;
        }

        // Insert newNode between current and current.next
        GemNode nextNode    = current.next;
        current.next        = newNode;
        newNode.prev        = current;
        newNode.next        = nextNode;
        if (nextNode != null) {
            nextNode.prev   = newNode;
        } else {
            // The new node is now the last node
            tail = newNode;
        }

        size++;
        System.out.println("💎 Stage inserted at position " + position
                + " for Gem ID: " + gemId);
    }

    /**
     * Removes a stage node at a specific position from the list.
     * Used by authorized users to correct wrongly entered stages.
     *
     * After deletion, the prev and next pointers of the surrounding
     * nodes are updated to keep the chain intact.
     *
     * @param position the index of the node to remove (0 = head)
     * @return the removed GemNode, or null if position is invalid
     */
    public GemNode removeStageAt(int position) {
        if (head == null) {
            System.out.println("The gem journey is empty. Nothing to remove.");
            return null;
        }

        if (position < 0 || position >= size) {
            System.out.println("Invalid position. Valid range is 0 to " + (size - 1));
            return null;
        }

        GemNode toRemove;

        if (position == 0) {
            // Removing the head node
            toRemove  = head;
            head      = head.next;
            if (head != null) {
                head.prev = null;
            } else {
                // List is now empty
                tail = null;
            }
        } else {
            // Traverse to the node at the given position
            GemNode current = head;
            int index = 0;
            while (current != null && index < position) {
                current = current.next;
                index++;
            }

            toRemove = current;

            // Reconnect the surrounding nodes around the removed node
            if (toRemove.prev != null) {
                toRemove.prev.next = toRemove.next;
            }
            if (toRemove.next != null) {
                toRemove.next.prev = toRemove.prev;
            } else {
                // Removed node was the tail, update tail pointer
                tail = toRemove.prev;
            }
        }

        // Disconnect the removed node from the list
        toRemove.next = null;
        toRemove.prev = null;
        size--;

        System.out.println("💎 Stage removed at position " + position
                + " for Gem ID: " + gemId);
        return toRemove;
    }

    // ---------------------------------------------------------
    // Traversal operations
    // ---------------------------------------------------------

    /**
     * Traverses the list from head to tail and prints every stage.
     * This is the forward traversal - showing the gem's journey
     * in chronological order from mine to current owner.
     *
     * Used by JourneyViewer to display the complete gem history.
     */
    public void displayJourneyForward() {
        if (head == null) {
            System.out.println("No stages recorded for Gem ID: " + gemId);
            return;
        }

        System.out.println("💎 Full Journey of Gem ID: " + gemId);
        System.out.println("  Direction: Mine to Current Owner (Forward)");
        System.out.println("  Total Stages: " + size);
        System.out.println();

        GemNode current = head;
        int stageNumber = 1;

        while (current != null) {
            System.out.println("  --- Stage " + stageNumber + " ---");
            System.out.println(current.toString());
            stageNumber++;
            current = current.next;
        }
    }

    /**
     * Traverses the list from tail to head and prints every stage.
     * This is the backward traversal - showing the gem's journey
     * in reverse order from current owner back to the mine.
     *
     * This is only possible because we are using a doubly linked list.
     * A singly linked list cannot traverse backwards.
     * Used during origin verification to trace back to the mine node.
     */
    public void displayJourneyBackward() {
        if (tail == null) {
            System.out.println("No stages recorded for Gem ID: " + gemId);
            return;
        }

        System.out.println("💎 Reverse Journey of Gem ID: " + gemId);
        System.out.println("  Direction: Current Owner back to Mine (Backward)");
        System.out.println("  Total Stages: " + size);
        System.out.println();

        GemNode current = tail;
        int stageNumber = size;

        while (current != null) {
            System.out.println("  --- Stage " + stageNumber + " ---");
            System.out.println(current.toString());
            stageNumber--;
            current = current.prev;
        }
    }

    /**
     * Returns all nodes as a List for report generation
     * and database saving purposes.
     *
     * Having a List makes it easy to pass all nodes to
     * ReportGenerator and DBConnection without re-traversing.
     *
     * @return a List of all GemNode objects in order from head to tail
     */
    public List<GemNode> getAllStages() {
        List<GemNode> stages = new ArrayList<>();
        GemNode current = head;
        while (current != null) {
            stages.add(current);
            current = current.next;
        }
        return stages;
    }

    // ---------------------------------------------------------
    // Search operations
    // ---------------------------------------------------------

    /**
     * Searches for a node by stage type and returns the first match.
     * Used when we need to find a specific stage, for example
     * finding the MINING node to verify origin location.
     *
     * @param stage the GemStage type to search for
     * @return the first GemNode matching the stage, or null if not found
     */
    public GemNode findByStage(GemStage stage) {
        GemNode current = head;
        while (current != null) {
            if (current.getStage() == stage) {
                return current;
            }
            current = current.next;
        }
        System.out.println("No stage of type " + stage.getLabel()
                + " found for Gem ID: " + gemId);
        return null;
    }

    /**
     * Searches for a node by the person's name at that stage.
     * Used to find all stages handled by a specific individual.
     *
     * @param personName the name of the person to search for
     * @return the first GemNode where the person name matches
     */
    public GemNode findByPersonName(String personName) {
        GemNode current = head;
        while (current != null) {
            if (current.getPersonName().equalsIgnoreCase(personName)) {
                return current;
            }
            current = current.next;
        }
        System.out.println("No stage found for person: " + personName);
        return null;
    }

    /**
     * Searches for a node by certificate number.
     * Used to verify whether a certificate belongs to this gem.
     *
     * @param certificateNumber the certificate number to look for
     * @return the GemNode that holds this certificate, or null
     */
    public GemNode findByCertificateNumber(String certificateNumber) {
        GemNode current = head;
        while (current != null) {
            if (certificateNumber.equals(current.getCertificateNumber())) {
                return current;
            }
            current = current.next;
        }
        System.out.println("No stage found with certificate: " + certificateNumber);
        return null;
    }

    // ---------------------------------------------------------
    // Origin verification helper
    // ---------------------------------------------------------

    /**
     * Returns the head node of the list, which is always the MINING stage.
     * Used by OriginVerifier to check whether the first node's location
     * is a valid Sri Lankan mining area.
     *
     * @return the head GemNode (mining stage), or null if list is empty
     */
    public GemNode getMiningNode() {
        return head;
    }

    /**
     * Returns the tail node of the list, which is the most recent stage.
     * Used to show the current owner and current location of the gem.
     *
     * @return the tail GemNode (current stage), or null if list is empty
     */
    public GemNode getCurrentStageNode() {
        return tail;
    }

    // ---------------------------------------------------------
    // Weight loss calculation
    // ---------------------------------------------------------

    /**
     * Calculates the total weight lost from mining to the current stage.
     * Compares the weight in the head node (original mining weight)
     * against the weight in the tail node (current weight).
     *
     * This is used in reports to show how much the gem lost during cutting.
     *
     * @return weight lost in carats, or 0 if less than 2 stages exist
     */
    public double calculateWeightLoss() {
        if (head == null || head == tail) {
            return 0;
        }
        double originalWeight = head.getWeightInCarats();
        double currentWeight  = tail.getWeightInCarats();
        return originalWeight - currentWeight;
    }

    /**
     * Calculates the percentage of weight lost from mining to current stage.
     * Used in reports and the value assessment dashboard.
     *
     * @return weight loss as a percentage, or 0 if not enough data
     */
    public double calculateWeightLossPercentage() {
        if (head == null || head.getWeightInCarats() == 0) {
            return 0;
        }
        double lostWeight    = calculateWeightLoss();
        double originalWeight = head.getWeightInCarats();
        return (lostWeight / originalWeight) * 100;
    }

    // ---------------------------------------------------------
    // Price appreciation
    // ---------------------------------------------------------

    /**
     * Calculates the total price increase from mining to current stage.
     * Compares the price at the head node against the price at the tail node.
     *
     * Used by PriceTracker to show value added across the supply chain.
     *
     * @return price increase in rupees, or 0 if less than 2 stages exist
     */
    public double calculatePriceAppreciation() {
        if (head == null || head == tail) {
            return 0;
        }
        double miningPrice  = head.getPriceInRupees();
        double currentPrice = tail.getPriceInRupees();
        return currentPrice - miningPrice;
    }

    // ---------------------------------------------------------
    // Utility methods
    // ---------------------------------------------------------

    /**
     * Returns the total number of stages in the gem's journey.
     *
     * @return the size of the linked list
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the unique gem ID this linked list belongs to.
     *
     * @return the gem ID string
     */
    public String getGemId() {
        return gemId;
    }

    /**
     * Checks whether the gem journey has any stages recorded.
     *
     * @return true if the list has no nodes, false otherwise
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Clears all nodes from the list.
     * Used when resetting a gem record due to data entry errors.
     * Sets head, tail, and size back to their initial values.
     */
    public void clearJourney() {
        head = null;
        tail = null;
        size = 0;
        System.out.println("💎 Journey cleared for Gem ID: " + gemId);
    }

    /**
     * Prints a simple summary of the gem's journey.
     * Shows gem ID, total stages, origin location, and current location.
     * Used in search results and dashboard listings.
     */
    public void printSummary() {
        System.out.println("💎 Gem ID       : " + gemId);
        System.out.println("  Total Stages  : " + size);
        if (head != null) {
            System.out.println("  Origin        : " + head.getLocation()
                    + " (" + head.getStageDate() + ")");
            System.out.println("  Gem Type      : " + head.getGemType());
            System.out.println("  Original Wt   : " + head.getWeightInCarats() + " carats");
        }
        if (tail != null && tail != head) {
            System.out.println("  Current Stage : " + tail.getStage().getLabel());
            System.out.println("  Current Owner : " + tail.getPersonName());
            System.out.println("  Current Wt    : " + tail.getWeightInCarats() + " carats");
            System.out.println("  Current Price : Rs. " + tail.getPriceInRupees());
        }
        if (head != null) {
            System.out.printf("  Weight Lost   : %.2f carats (%.1f%%)%n",
                    calculateWeightLoss(), calculateWeightLossPercentage());
            System.out.printf("  Price Gain    : Rs. %.2f%n",
                    calculatePriceAppreciation());
        }
    }
}