package service;

import database.DBConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.GemNode;

/**
 * AuditService tracks every change made to the gem linked list.
 *
 * Every time a stage is added, updated, or deleted, AuditService
 * records the full before and after state of the change in the
 * gem_audit_log database table.
 *
 * This creates a complete, tamper-evident history of every
 * modification made to any gem in the system — showing exactly
 * what changed, when it changed, and what the old and new values
 * were at the time of the change.
 *
 * The audit log is separate from the main gem data and is never
 * modified or deleted — it is a permanent append-only record.
 *
 * Action types recorded:
 *   STAGE_ADDED    — a new node was appended to the linked list
 *   STAGE_UPDATED  — a field on an existing node was changed
 *   STAGE_DELETED  — a node was removed from the linked list
 *   GEM_REGISTERED — a new gem was registered in the system
 *   GEM_DELETED    — a gem and all its stages were deleted
 *   CERTIFICATE_ADDED — a certificate was added to a stage
 *   EXPORT_ADDED      — export details were added to a stage
 *   NOTE_ADDED        — a note was added to a stage
 */
public class AuditService {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * Database connection used to persist and retrieve audit logs.
     * Uses the Singleton instance shared across all services.
     */
    private DBConnection dbConnection;

    /**
     * TrackingService used to retrieve current gem state
     * when building before/after snapshots of changes.
     */
    private TrackingService trackingService;

    /**
     * Formatter for audit log timestamps.
     * All timestamps are stored in ISO local datetime format.
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------------------------------------------------------
    // Action type constants
    // ---------------------------------------------------------

    public static final String ACTION_STAGE_ADDED      = "STAGE_ADDED";
    public static final String ACTION_STAGE_UPDATED    = "STAGE_UPDATED";
    public static final String ACTION_STAGE_DELETED    = "STAGE_DELETED";
    public static final String ACTION_GEM_REGISTERED   = "GEM_REGISTERED";
    public static final String ACTION_GEM_DELETED      = "GEM_DELETED";
    public static final String ACTION_CERTIFICATE_ADDED= "CERTIFICATE_ADDED";
    public static final String ACTION_EXPORT_ADDED     = "EXPORT_ADDED";
    public static final String ACTION_NOTE_ADDED       = "NOTE_ADDED";

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new AuditService with the required dependencies.
     * Also ensures the audit log table exists in the database.
     *
     * @param trackingService the service used to retrieve gem data
     */
    public AuditService(TrackingService trackingService) {
        this.dbConnection   = DBConnection.getInstance();
        this.trackingService = trackingService;
        initAuditTable();
    }

    // ---------------------------------------------------------
    // Table initialisation
    // ---------------------------------------------------------

    /**
     * Creates the gem_audit_log table if it does not already exist.
     * Called once during construction to ensure the table is ready
     * before any audit records are written.
     *
     * The table is append-only — records are never updated or deleted.
     * This guarantees the audit trail cannot be tampered with.
     */
    private void initAuditTable() {
        String sql =
            "CREATE TABLE IF NOT EXISTS gem_audit_log (" +
            "  id           INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  gem_id       TEXT    NOT NULL," +
            "  action       TEXT    NOT NULL," +
            "  stage_number INTEGER," +
            "  stage_type   TEXT," +
            "  field_changed TEXT," +
            "  old_value    TEXT," +
            "  new_value    TEXT," +
            "  description  TEXT," +
            "  changed_at   TEXT    NOT NULL" +
            ")";
        dbConnection.executeRaw(sql);
    }

    // ---------------------------------------------------------
    // Log methods — called by TrackingService on each change
    // ---------------------------------------------------------

    /**
     * Logs the registration of a new gem.
     * Called immediately after a new gem is saved to the database.
     * Records the gem type, origin, weight, and miner details.
     *
     * @param gemId    the newly assigned gem ID
     * @param gemType  the type of gem registered
     * @param origin   the mining origin location
     * @param weight   the original weight in carats
     * @param miner    the miner name
     */
    public void logGemRegistered(String gemId, String gemType,
                                  String origin, double weight,
                                  String miner) {
        String description = String.format(
            "New gem registered: %s | Origin: %s | Weight: %.2f ct | Miner: %s",
            gemType, origin, weight, miner
        );
        insertLog(gemId, ACTION_GEM_REGISTERED, null, null,
                  null, null,
                  String.format("%s at %s (%.2f ct)", gemType, origin, weight),
                  description);
    }

    /**
     * Logs the addition of a new stage node to the linked list.
     * Called after a new GemNode is successfully appended to the tail.
     * Records the full details of the new stage.
     *
     * @param gemId       the gem ID the stage was added to
     * @param stageNumber the position of the new node in the list
     * @param stageType   the type of stage added
     * @param location    the location of the new stage
     * @param personName  the person responsible at this stage
     * @param weight      the gem weight at this stage
     * @param price       the gem price at this stage
     * @param date        the date of this stage
     */
    public void logStageAdded(String gemId, int stageNumber,
                               String stageType, String location,
                               String personName, double weight,
                               double price, String date) {
        String newValue = String.format(
            "Stage: %s | Location: %s | Person: %s | Weight: %.2f ct | Price: Rs.%.0f | Date: %s",
            stageType, location, personName, weight, price, date
        );
        String description = String.format(
            "Stage %d added to linked list — %s at %s by %s on %s",
            stageNumber, stageType, location, personName, date
        );
        insertLog(gemId, ACTION_STAGE_ADDED, stageNumber, stageType,
                  null, null, newValue, description);
    }

    /**
     * Logs the deletion of a stage node from the linked list.
     * Called before the node is removed so the old state can be captured.
     * Records the full details of what was deleted.
     *
     * @param gemId       the gem ID the stage was removed from
     * @param stageNumber the position of the deleted node
     * @param node        the GemNode that is being deleted
     */
    public void logStageDeleted(String gemId, int stageNumber,
                                 GemNode node) {
        String oldValue = String.format(
            "Stage: %s | Location: %s | Person: %s | Weight: %.2f ct | Price: Rs.%.0f | Date: %s",
            node.getStage().name(),
            node.getLocation(),
            node.getPersonName(),
            node.getWeightInCarats(),
            node.getPriceInRupees(),
            node.getStageDate().toString()
        );
        String description = String.format(
            "Stage %d deleted from linked list — %s at %s (prev pointer and next pointer reconnected)",
            stageNumber, node.getStage().name(), node.getLocation()
        );
        insertLog(gemId, ACTION_STAGE_DELETED, stageNumber,
                  node.getStage().name(),
                  oldValue, null, null, description);
    }

    /**
     * Logs a field update on an existing stage node.
     * Records the exact field name, old value, and new value.
     * Called when any field on a node is changed.
     *
     * @param gemId        the gem ID of the updated stage
     * @param stageNumber  the position of the updated node
     * @param stageType    the type of the updated stage
     * @param fieldChanged the name of the field that was changed
     * @param oldValue     the value before the change
     * @param newValue     the value after the change
     */
    public void logStageUpdated(String gemId, int stageNumber,
                                 String stageType, String fieldChanged,
                                 String oldValue, String newValue) {
        String description = String.format(
            "Stage %d (%s) updated — field '%s' changed",
            stageNumber, stageType, fieldChanged
        );
        insertLog(gemId, ACTION_STAGE_UPDATED, stageNumber, stageType,
                  fieldChanged, oldValue, newValue, description);
    }

    /**
     * Logs the addition of a certificate to a stage.
     * Called when certificate details are added to the current stage node.
     *
     * @param gemId             the gem ID
     * @param stageNumber       the stage that received the certificate
     * @param certificateNumber the certificate number added
     * @param issuingAuthority  the authority that issued the certificate
     */
    public void logCertificateAdded(String gemId, int stageNumber,
                                     String certificateNumber,
                                     String issuingAuthority) {
        String newValue = String.format(
            "Certificate: %s | Authority: %s", certificateNumber, issuingAuthority
        );
        String description = String.format(
            "Certificate added to stage %d — cert no: %s issued by %s",
            stageNumber, certificateNumber, issuingAuthority
        );
        insertLog(gemId, ACTION_CERTIFICATE_ADDED, stageNumber, null,
                  "certificateNumber", null, newValue, description);
    }

    /**
     * Logs the addition of export details to a stage.
     * Called when export details are added to an EXPORTING stage node.
     *
     * @param gemId              the gem ID
     * @param stageNumber        the stage that received export details
     * @param flightNumber       the export flight number
     * @param invoiceNumber      the export invoice number
     * @param destinationCountry the destination country
     */
    public void logExportAdded(String gemId, int stageNumber,
                                String flightNumber, String invoiceNumber,
                                String destinationCountry) {
        String newValue = String.format(
            "Flight: %s | Invoice: %s | Destination: %s",
            flightNumber, invoiceNumber, destinationCountry
        );
        String description = String.format(
            "Export details added to stage %d — flight %s to %s",
            stageNumber, flightNumber, destinationCountry
        );
        insertLog(gemId, ACTION_EXPORT_ADDED, stageNumber, "EXPORTING",
                  "exportDetails", null, newValue, description);
    }

    /**
     * Logs the addition of a note to a stage.
     * Called when notes are added to the current stage node.
     *
     * @param gemId       the gem ID
     * @param stageNumber the stage that received the note
     * @param note        the note text that was added
     */
    public void logNoteAdded(String gemId, int stageNumber, String note) {
        String description = String.format(
            "Note added to stage %d: %s", stageNumber, note
        );
        insertLog(gemId, ACTION_NOTE_ADDED, stageNumber, null,
                  "notes", null, note, description);
    }

    /**
     * Logs the deletion of an entire gem and all its stages.
     * Called before the gem is deleted so the snapshot can be captured.
     * Records the number of stages that were deleted.
     *
     * @param gemId      the gem ID being deleted
     * @param gemType    the type of the gem
     * @param stageCount the number of stages the gem had
     */
    public void logGemDeleted(String gemId, String gemType, int stageCount) {
        String oldValue = String.format(
            "Gem: %s | Stages: %d", gemType, stageCount
        );
        String description = String.format(
            "Gem deleted — %s (%s) with %d stages removed from system",
            gemId, gemType, stageCount
        );
        insertLog(gemId, ACTION_GEM_DELETED, null, null,
                  null, oldValue, null, description);
    }

    // ---------------------------------------------------------
    // Retrieval methods
    // ---------------------------------------------------------

    /**
     * Returns all audit log entries for a specific gem.
     * Results are ordered by changed_at descending — newest first.
     * Each entry is a map with all audit log fields.
     *
     * @param gemId the gem ID to retrieve audit logs for
     * @return a list of audit log entry maps
     */
    public List<Map<String, Object>> getAuditLogsForGem(String gemId) {
        String sql =
            "SELECT id, gem_id, action, stage_number, stage_type, " +
            "field_changed, old_value, new_value, description, changed_at " +
            "FROM gem_audit_log WHERE gem_id = ? ORDER BY changed_at DESC";
        return dbConnection.queryAuditLogs(sql, gemId);
    }

    /**
     * Returns all audit log entries across all gems.
     * Results are ordered by changed_at descending — newest first.
     * Used on the audit page to show the full system history.
     *
     * @return a list of all audit log entry maps
     */
    public List<Map<String, Object>> getAllAuditLogs() {
        String sql =
            "SELECT id, gem_id, action, stage_number, stage_type, " +
            "field_changed, old_value, new_value, description, changed_at " +
            "FROM gem_audit_log ORDER BY changed_at DESC";
        return dbConnection.queryAuditLogs(sql, null);
    }

    /**
     * Returns audit log entries filtered by action type.
     * Used to show only deletions, only updates, or only additions.
     *
     * @param action the action type to filter by
     * @return a list of matching audit log entry maps
     */
    public List<Map<String, Object>> getAuditLogsByAction(String action) {
        String sql =
            "SELECT id, gem_id, action, stage_number, stage_type, " +
            "field_changed, old_value, new_value, description, changed_at " +
            "FROM gem_audit_log WHERE action = ? ORDER BY changed_at DESC";
        return dbConnection.queryAuditLogs(sql, action);
    }

    /**
     * Returns a summary count of each action type across all gems.
     * Used on the audit dashboard to show totals for each change type.
     *
     * @return a map of action type to count
     */
    public Map<String, Object> getAuditSummary() {
        List<Map<String, Object>> all = getAllAuditLogs();

        int added       = 0;
        int updated     = 0;
        int deleted     = 0;
        int registered  = 0;
        int gemDeleted  = 0;
        int certificates= 0;
        int exports     = 0;
        int notes       = 0;

        for (Map<String, Object> log : all) {
            String action = (String) log.get("action");
            if (action == null) continue;
            switch (action) {
                case ACTION_STAGE_ADDED:       added++;        break;
                case ACTION_STAGE_UPDATED:     updated++;      break;
                case ACTION_STAGE_DELETED:     deleted++;      break;
                case ACTION_GEM_REGISTERED:    registered++;   break;
                case ACTION_GEM_DELETED:       gemDeleted++;   break;
                case ACTION_CERTIFICATE_ADDED: certificates++; break;
                case ACTION_EXPORT_ADDED:      exports++;      break;
                case ACTION_NOTE_ADDED:        notes++;        break;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalChanges",      all.size());
        summary.put("stagesAdded",       added);
        summary.put("stagesUpdated",     updated);
        summary.put("stagesDeleted",     deleted);
        summary.put("gemsRegistered",    registered);
        summary.put("gemsDeleted",       gemDeleted);
        summary.put("certificatesAdded", certificates);
        summary.put("exportsAdded",      exports);
        summary.put("notesAdded",        notes);
        return summary;
    }

    /**
     * Returns the most recent N audit log entries across all gems.
     * Used on the dashboard for a quick activity feed.
     *
     * @param limit the maximum number of entries to return
     * @return a list of the most recent audit log entries
     */
    public List<Map<String, Object>> getRecentAuditLogs(int limit) {
        String sql =
            "SELECT id, gem_id, action, stage_number, stage_type, " +
            "field_changed, old_value, new_value, description, changed_at " +
            "FROM gem_audit_log ORDER BY changed_at DESC LIMIT " + limit;
        return dbConnection.queryAuditLogs(sql, null);
    }

    /**
     * Returns audit log entries for a specific gem filtered by action.
     * Combines gem ID and action type filtering.
     *
     * @param gemId  the gem ID to filter by
     * @param action the action type to filter by
     * @return a list of matching audit log entries
     */
    public List<Map<String, Object>> getAuditLogsForGemByAction(
            String gemId, String action) {
        String sql =
            "SELECT id, gem_id, action, stage_number, stage_type, " +
            "field_changed, old_value, new_value, description, changed_at " +
            "FROM gem_audit_log WHERE gem_id = ? AND action = ? " +
            "ORDER BY changed_at DESC";
        return dbConnection.queryAuditLogsFiltered(sql, gemId, action);
    }

    // ---------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------

    /**
     * Inserts a single audit log entry into the gem_audit_log table.
     * Called by all public log methods after building their parameters.
     * Automatically adds the current timestamp to every entry.
     *
     * @param gemId        the gem ID being audited
     * @param action       the action type constant
     * @param stageNumber  the stage number affected (or null)
     * @param stageType    the stage type affected (or null)
     * @param fieldChanged the field that changed (or null)
     * @param oldValue     the value before the change (or null)
     * @param newValue     the value after the change (or null)
     * @param description  a human readable description of the change
     */
    private void insertLog(String gemId, String action,
                           Integer stageNumber, String stageType,
                           String fieldChanged, String oldValue,
                           String newValue, String description) {
        String changedAt = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        String sql =
            "INSERT INTO gem_audit_log " +
            "(gem_id, action, stage_number, stage_type, field_changed, " +
            " old_value, new_value, description, changed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbConnection.insertAuditLog(
            sql, gemId, action, stageNumber, stageType,
            fieldChanged, oldValue, newValue, description, changedAt
        );
    }

    /**
     * Builds a formatted snapshot string of a GemNode's current state.
     * Used to capture the full before/after state of a node when logging.
     *
     * @param node the GemNode to snapshot
     * @return a formatted string describing all node fields
     */
    public String buildNodeSnapshot(GemNode node) {
        if (node == null) return "null";

        StringBuilder sb = new StringBuilder();
        sb.append("Stage: ").append(node.getStage().name());
        sb.append(" | Location: ").append(node.getLocation());
        sb.append(" | Person: ").append(node.getPersonName());
        sb.append(" | Weight: ").append(node.getWeightInCarats()).append(" ct");
        sb.append(" | Price: Rs.").append((int) node.getPriceInRupees());
        sb.append(" | Date: ").append(node.getStageDate());

        if (node.getPersonIdNumber() != null)
            sb.append(" | NIC: ").append(node.getPersonIdNumber());
        if (node.getCertificateNumber() != null)
            sb.append(" | Cert: ").append(node.getCertificateNumber());
        if (node.getFlightNumber() != null)
            sb.append(" | Flight: ").append(node.getFlightNumber());
        if (node.getDestinationCountry() != null)
            sb.append(" | Dest: ").append(node.getDestinationCountry());
        if (node.getNotes() != null)
            sb.append(" | Notes: ").append(node.getNotes());

        return sb.toString();
    }

    /**
     * Returns the formatted current timestamp string.
     * Used when building audit log entries.
     *
     * @return the current date and time as a formatted string
     */
    public String getCurrentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }
}