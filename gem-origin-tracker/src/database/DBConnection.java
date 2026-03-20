package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.GemNode;
import model.GemStage;
import model.GemLinkedList;

import java.time.LocalDate;

/**
 * DBConnection handles all communication between the Java application
 * and the SQLite database.
 *
 * This class follows the Singleton pattern, meaning only one database
 * connection is created and reused throughout the entire application.
 * Creating a new connection every time a database operation is needed
 * would be slow and waste resources.
 *
 * SQLite was chosen because it stores the entire database in a single
 * file, requires no server setup, and works perfectly for a desktop
 * Java application of this scale.
 */
public class DBConnection {

    // ---------------------------------------------------------
    // Singleton instance and connection details
    // ---------------------------------------------------------

    /**
     * The single shared instance of DBConnection.
     * Created once and reused across the entire application.
     */
    private static DBConnection instance;

    /**
     * The active JDBC connection to the SQLite database file.
     * All queries and updates go through this connection.
     */
    private Connection connection;

    /**
     * The path to the SQLite database file.
     * The file is created automatically if it does not exist.
     * Stored in the project root folder for easy access.
     */
    private static final String DB_URL = "jdbc:sqlite:gem_tracker.db";

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Private constructor to prevent external instantiation.
     * Only called once internally by getInstance().
     * Establishes the database connection and initialises the schema.
     */
    private DBConnection() {
        try {
            // Load the SQLite JDBC driver class
            Class.forName("org.sqlite.JDBC");

            // Open the connection to the database file
            connection = DriverManager.getConnection(DB_URL);

            // Enable foreign key support in SQLite
            // SQLite does not enforce foreign keys by default,
            // so this must be turned on explicitly per connection
            Statement stmt = connection.createStatement();
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.close();

            // Run the schema to create tables if they do not exist
            initialiseSchema();

            System.out.println("💎 Database connected successfully: " + DB_URL);

        } catch (ClassNotFoundException e) {
            System.out.println("SQLite JDBC driver not found. Add sqlite-jdbc jar to your project.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Failed to connect to the database.");
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------
    // Singleton access
    // ---------------------------------------------------------

    /**
     * Returns the single shared instance of DBConnection.
     * Creates it on the first call, then returns the same instance
     * on every subsequent call.
     *
     * @return the shared DBConnection instance
     */
    public static DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    /**
     * Returns the raw JDBC Connection object.
     * Used internally when PreparedStatements need to be created.
     *
     * @return the active Connection object
     */
    public Connection getConnection() {
        return connection;
    }

    // ---------------------------------------------------------
    // Schema initialisation
    // ---------------------------------------------------------

    /**
     * Reads and executes the schema.sql file to create all tables.
     * Each CREATE TABLE statement uses IF NOT EXISTS, so existing
     * tables are never dropped or modified on re-runs.
     *
     * The schema is split by semicolons to execute each statement
     * individually, since JDBC cannot run multiple statements at once.
     */
    private void initialiseSchema() {
        // SQL statements are written directly here to avoid file I/O issues
        // during early development. These mirror the schema.sql file exactly.
        String[] schemaStatements = {

            // gems table
            "CREATE TABLE IF NOT EXISTS gems (" +
            "    gem_id              TEXT PRIMARY KEY," +
            "    gem_type            TEXT NOT NULL," +
            "    color_description   TEXT," +
            "    original_weight     REAL NOT NULL," +
            "    origin_mine         TEXT NOT NULL," +
            "    origin_district     TEXT NOT NULL," +
            "    origin_village      TEXT," +
            "    mining_date         TEXT NOT NULL," +
            "    is_ceylon_gem       INTEGER DEFAULT 1," +
            "    qr_code_path        TEXT," +
            "    created_at          TEXT DEFAULT (datetime('now'))" +
            ")",

            // gem_stages table
            "CREATE TABLE IF NOT EXISTS gem_stages (" +
            "    stage_id            INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    gem_id              TEXT NOT NULL," +
            "    stage_type          TEXT NOT NULL," +
            "    stage_order         INTEGER NOT NULL," +
            "    location            TEXT NOT NULL," +
            "    person_name         TEXT NOT NULL," +
            "    person_id_number    TEXT," +
            "    contact_number      TEXT," +
            "    weight_in_carats    REAL NOT NULL," +
            "    price_in_rupees     REAL NOT NULL," +
            "    stage_date          TEXT NOT NULL," +
            "    certificate_number  TEXT," +
            "    issuing_authority   TEXT," +
            "    flight_number       TEXT," +
            "    invoice_number      TEXT," +
            "    destination_country TEXT," +
            "    notes               TEXT," +
            "    created_at          TEXT DEFAULT (datetime('now'))," +
            "    FOREIGN KEY (gem_id) REFERENCES gems(gem_id) ON DELETE CASCADE ON UPDATE CASCADE" +
            ")",

            // gem_alerts table
            "CREATE TABLE IF NOT EXISTS gem_alerts (" +
            "    alert_id            INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    gem_id              TEXT NOT NULL," +
            "    alert_type          TEXT NOT NULL," +
            "    alert_message       TEXT NOT NULL," +
            "    is_resolved         INTEGER DEFAULT 0," +
            "    created_at          TEXT DEFAULT (datetime('now'))," +
            "    FOREIGN KEY (gem_id) REFERENCES gems(gem_id) ON DELETE CASCADE ON UPDATE CASCADE" +
            ")",

            // ceylon_mining_locations table
            "CREATE TABLE IF NOT EXISTS ceylon_mining_locations (" +
            "    location_id         INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    district            TEXT NOT NULL," +
            "    village             TEXT," +
            "    mine_name           TEXT," +
            "    is_active           INTEGER DEFAULT 1" +
            ")",

            // Indexes for faster queries
            "CREATE INDEX IF NOT EXISTS idx_gem_stages_gem_id ON gem_stages(gem_id)",
            "CREATE INDEX IF NOT EXISTS idx_gem_stages_order  ON gem_stages(gem_id, stage_order)",
            "CREATE INDEX IF NOT EXISTS idx_gems_type         ON gems(gem_type)",
            "CREATE INDEX IF NOT EXISTS idx_alerts_gem_id     ON gem_alerts(gem_id)",
            "CREATE INDEX IF NOT EXISTS idx_alerts_resolved   ON gem_alerts(is_resolved)",

            // Pre-populate mining locations only if table is empty
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Ratnapura',  'Pelmadulla',  'Pelmadulla Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Ratnapura',  'Elapatha',    'Elapatha Gem Pit')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Ratnapura',  'Kuruwita',    'Kuruwita Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Ratnapura',  'Ratnapura',   'Ratnapura City Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Matale',     'Elahera',     'Elahera Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Ampara',     'Okanda',      'Okanda Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Badulla',    'Bibile',      'Bibile Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Badulla',    'Okkampitiya', 'Okkampitiya Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Kandy',      'Hasalaka',    'Hasalaka Mine')",
            "INSERT OR IGNORE INTO ceylon_mining_locations (district, village, mine_name) VALUES ('Kalutara',   'Meetiyagoda', 'Meetiyagoda Mine')"
        };

        try {
            Statement stmt = connection.createStatement();
            for (String sql : schemaStatements) {
                stmt.execute(sql);
            }
            stmt.close();
            System.out.println("💎 Database schema initialised successfully.");
        } catch (SQLException e) {
            System.out.println("Failed to initialise database schema.");
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------
    // Gem CRUD operations
    // ---------------------------------------------------------

    /**
     * Saves a new gem record to the gems table.
     * Called when a new gem is registered in the system for the first time.
     * The mining node of the linked list provides all required data.
     *
     * @param list        the GemLinkedList whose head node has mining data
     * @param colorDesc   the color description of the gem
     * @param originMine  the name of the mine
     * @param district    the district of the mine
     * @param village     the village near the mine
     * @return true if saved successfully, false otherwise
     */
    public boolean saveGem(GemLinkedList list, String colorDesc,
                           String originMine, String district, String village) {
        // The head node of the list holds the original mining information
        GemNode miningNode = list.getMiningNode();
        if (miningNode == null) {
            System.out.println("Cannot save gem: mining stage node is missing.");
            return false;
        }

        String sql = "INSERT OR IGNORE INTO gems " +
                     "(gem_id, gem_type, color_description, original_weight, " +
                     " origin_mine, origin_district, origin_village, mining_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, list.getGemId());
            ps.setString(2, miningNode.getGemType());
            ps.setString(3, colorDesc);
            ps.setDouble(4, miningNode.getWeightInCarats());
            ps.setString(5, originMine);
            ps.setString(6, district);
            ps.setString(7, village);
            ps.setString(8, miningNode.getStageDate().toString());
            ps.executeUpdate();
            System.out.println("💎 Gem saved to database: " + list.getGemId());
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to save gem: " + list.getGemId());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Updates the QR code file path for a gem after it has been generated.
     * Called by QRCodeService after the QR code image is created.
     *
     * @param gemId       the gem whose QR code path needs updating
     * @param qrCodePath  the file path to the generated QR code image
     * @return true if updated successfully, false otherwise
     */
    public boolean updateQRCodePath(String gemId, String qrCodePath) {
        String sql = "UPDATE gems SET qr_code_path = ? WHERE gem_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, qrCodePath);
            ps.setString(2, gemId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to update QR code path for: " + gemId);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Updates the Ceylon verification status of a gem.
     * Called by OriginVerifier after checking the mining location.
     *
     * @param gemId         the gem to update
     * @param isCeylonGem   true if verified as Ceylon gem, false otherwise
     * @return true if updated successfully, false otherwise
     */
    public boolean updateCeylonStatus(String gemId, boolean isCeylonGem) {
        String sql = "UPDATE gems SET is_ceylon_gem = ? WHERE gem_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, isCeylonGem ? 1 : 0);
            ps.setString(2, gemId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to update Ceylon status for: " + gemId);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deletes a gem and all its stages from the database.
     * Because ON DELETE CASCADE is set in schema.sql, deleting
     * the gem row automatically deletes all its stage rows too.
     *
     * @param gemId the ID of the gem to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteGem(String gemId) {
        String sql = "DELETE FROM gems WHERE gem_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gemId);
            ps.executeUpdate();
            System.out.println("💎 Gem deleted from database: " + gemId);
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to delete gem: " + gemId);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks whether a gem ID already exists in the database.
     * Used before inserting a new gem to prevent duplicate entries.
     *
     * @param gemId the gem ID to check
     * @return true if the gem already exists, false otherwise
     */
    public boolean gemExists(String gemId) {
        String sql = "SELECT COUNT(*) FROM gems WHERE gem_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.out.println("Failed to check gem existence: " + gemId);
            e.printStackTrace();
        }
        return false;
    }

    // ---------------------------------------------------------
    // Stage CRUD operations
    // ---------------------------------------------------------

    /**
     * Saves a single stage node to the gem_stages table.
     * Called every time a new stage is added to the linked list,
     * so the database stays in sync with the in-memory list.
     *
     * The stage_order value is the node's position in the list,
     * used to reconstruct the linked list correctly when loading.
     *
     * @param node       the GemNode to save
     * @param stageOrder the position of this node in the chain (1-based)
     * @return true if saved successfully, false otherwise
     */
    public boolean saveStage(GemNode node, int stageOrder) {
        String sql = "INSERT INTO gem_stages " +
                     "(gem_id, stage_type, stage_order, location, person_name, " +
                     " person_id_number, contact_number, weight_in_carats, price_in_rupees, " +
                     " stage_date, certificate_number, issuing_authority, " +
                     " flight_number, invoice_number, destination_country, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  node.getGemId());
            ps.setString(2,  node.getStage().name());
            ps.setInt   (3,  stageOrder);
            ps.setString(4,  node.getLocation());
            ps.setString(5,  node.getPersonName());
            ps.setString(6,  node.getPersonIdNumber());
            ps.setString(7,  node.getContactNumber());
            ps.setDouble(8,  node.getWeightInCarats());
            ps.setDouble(9,  node.getPriceInRupees());
            ps.setString(10, node.getStageDate().toString());
            ps.setString(11, node.getCertificateNumber());
            ps.setString(12, node.getIssuingAuthority());
            ps.setString(13, node.getFlightNumber());
            ps.setString(14, node.getInvoiceNumber());
            ps.setString(15, node.getDestinationCountry());
            ps.setString(16, node.getNotes());
            ps.executeUpdate();
            System.out.println("💎 Stage saved: " + node.getStage().getLabel()
                    + " for Gem: " + node.getGemId());
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to save stage for gem: " + node.getGemId());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads all stages for a given gem from the database and
     * reconstructs them into a GemLinkedList in the correct order.
     *
     * Stages are loaded ordered by stage_order so the linked list
     * is built from head (mining) to tail (current owner) correctly.
     *
     * @param gemId the ID of the gem whose journey to load
     * @return a fully reconstructed GemLinkedList, or null if not found
     */
    public GemLinkedList loadGemJourney(String gemId) {
        String sql = "SELECT * FROM gem_stages WHERE gem_id = ? ORDER BY stage_order ASC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gemId);
            ResultSet rs = ps.executeQuery();

            GemLinkedList list = new GemLinkedList(gemId);

            while (rs.next()) {
                // Parse each row back into a GemNode object
                GemStage  stage  = GemStage.valueOf(rs.getString("stage_type"));
                LocalDate date   = LocalDate.parse(rs.getString("stage_date"));

                GemNode node = new GemNode(
                    rs.getString("gem_id"),
                    loadGemType(gemId),
                    stage,
                    rs.getString("location"),
                    rs.getString("person_name"),
                    rs.getDouble("weight_in_carats"),
                    rs.getDouble("price_in_rupees"),
                    date
                );

                // Set optional fields only if they are not null in the database
                if (rs.getString("person_id_number")  != null)
                    node.setPersonIdNumber(rs.getString("person_id_number"));
                if (rs.getString("contact_number")    != null)
                    node.setContactNumber(rs.getString("contact_number"));
                if (rs.getString("certificate_number") != null)
                    node.setCertificateNumber(rs.getString("certificate_number"));
                if (rs.getString("issuing_authority") != null)
                    node.setIssuingAuthority(rs.getString("issuing_authority"));
                if (rs.getString("flight_number")     != null)
                    node.setFlightNumber(rs.getString("flight_number"));
                if (rs.getString("invoice_number")    != null)
                    node.setInvoiceNumber(rs.getString("invoice_number"));
                if (rs.getString("destination_country") != null)
                    node.setDestinationCountry(rs.getString("destination_country"));
                if (rs.getString("notes")             != null)
                    node.setNotes(rs.getString("notes"));

                // Add each reconstructed node to the linked list
                list.addStage(node);
            }

            if (list.isEmpty()) {
                System.out.println("No stages found for Gem ID: " + gemId);
                return null;
            }

            System.out.println("💎 Journey loaded for Gem ID: " + gemId
                    + " | Stages: " + list.getSize());
            return list;

        } catch (SQLException e) {
            System.out.println("Failed to load journey for gem: " + gemId);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Helper method to fetch the gem type from the gems table.
     * Used during loadGemJourney to set the gem type on each node.
     *
     * @param gemId the gem ID to look up
     * @return the gem type string, or "Unknown" if not found
     */
    private String loadGemType(String gemId) {
        String sql = "SELECT gem_type FROM gems WHERE gem_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("gem_type");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    /**
     * Deletes all stages for a given gem from the gem_stages table.
     * Used when a gem's journey needs to be fully reset and re-entered.
     *
     * @param gemId the ID of the gem whose stages to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteAllStages(String gemId) {
        String sql = "DELETE FROM gem_stages WHERE gem_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gemId);
            ps.executeUpdate();
            System.out.println("💎 All stages deleted for Gem ID: " + gemId);
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to delete stages for gem: " + gemId);
            e.printStackTrace();
            return false;
        }
    }

    // ---------------------------------------------------------
    // Search operations
    // ---------------------------------------------------------

    /**
     * Returns a list of all gem IDs stored in the database.
     * Used by the search and dashboard features to list all gems.
     *
     * @return a List of gem ID strings
     */
    public List<String> getAllGemIds() {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT gem_id FROM gems ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getString("gem_id"));
            }
        } catch (SQLException e) {
            System.out.println("Failed to retrieve gem IDs.");
            e.printStackTrace();
        }
        return ids;
    }

    /**
     * Returns all gem IDs that match a specific gem type.
     * Used when users search gems by type e.g. all Blue Sapphires.
     *
     * @param gemType the gem type to search for
     * @return a List of matching gem ID strings
     */
    public List<String> searchGemsByType(String gemType) {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT gem_id FROM gems WHERE LOWER(gem_type) LIKE LOWER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + gemType + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("gem_id"));
            }
        } catch (SQLException e) {
            System.out.println("Failed to search gems by type: " + gemType);
            e.printStackTrace();
        }
        return ids;
    }

    /**
     * Returns all gem IDs that originated from a specific district.
     * Used when verifying or listing gems from a particular mining area.
     *
     * @param district the district name to search for
     * @return a List of matching gem ID strings
     */
    public List<String> searchGemsByDistrict(String district) {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT gem_id FROM gems WHERE LOWER(origin_district) LIKE LOWER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + district + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("gem_id"));
            }
        } catch (SQLException e) {
            System.out.println("Failed to search gems by district: " + district);
            e.printStackTrace();
        }
        return ids;
    }

    /**
     * Returns all gem IDs that are verified as genuine Ceylon gems.
     * Used in the dashboard to show the count of authentic gems.
     *
     * @return a List of gem IDs where is_ceylon_gem = 1
     */
    public List<String> getCeylonVerifiedGems() {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT gem_id FROM gems WHERE is_ceylon_gem = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getString("gem_id"));
            }
        } catch (SQLException e) {
            System.out.println("Failed to retrieve Ceylon verified gems.");
            e.printStackTrace();
        }
        return ids;
    }

    // ---------------------------------------------------------
    // Alert operations
    // ---------------------------------------------------------

    /**
     * Saves a fraud alert to the gem_alerts table.
     * Called by OriginVerifier when a gem's origin cannot be verified.
     *
     * @param gemId        the gem that triggered the alert
     * @param alertType    the type of alert e.g. ORIGIN_MISMATCH
     * @param alertMessage the human readable description of the alert
     * @return true if saved successfully, false otherwise
     */
    public boolean saveAlert(String gemId, String alertType, String alertMessage) {
        String sql = "INSERT INTO gem_alerts (gem_id, alert_type, alert_message) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gemId);
            ps.setString(2, alertType);
            ps.setString(3, alertMessage);
            ps.executeUpdate();
            System.out.println("💎 Alert saved for Gem ID: " + gemId
                    + " | Type: " + alertType);
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to save alert for gem: " + gemId);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns all unresolved alerts from the gem_alerts table.
     * Used by the dashboard to warn users of pending fraud alerts.
     *
     * @return a List of alert messages that are not yet resolved
     */
    public List<String> getUnresolvedAlerts() {
        List<String> alerts = new ArrayList<>();
        String sql = "SELECT gem_id, alert_type, alert_message, created_at " +
                     "FROM gem_alerts WHERE is_resolved = 0 ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String entry = "Gem: "    + rs.getString("gem_id")
                             + " | Type: "    + rs.getString("alert_type")
                             + " | Message: " + rs.getString("alert_message")
                             + " | Date: "    + rs.getString("created_at");
                alerts.add(entry);
            }
        } catch (SQLException e) {
            System.out.println("Failed to retrieve unresolved alerts.");
            e.printStackTrace();
        }
        return alerts;
    }

    /**
     * Marks a specific alert as resolved in the database.
     * Called when an administrator reviews and clears an alert.
     *
     * @param alertId the ID of the alert to resolve
     * @return true if updated successfully, false otherwise
     */
    public boolean resolveAlert(int alertId) {
        String sql = "UPDATE gem_alerts SET is_resolved = 1 WHERE alert_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, alertId);
            ps.executeUpdate();
            System.out.println("💎 Alert resolved: ID " + alertId);
            return true;
        } catch (SQLException e) {
            System.out.println("Failed to resolve alert: " + alertId);
            e.printStackTrace();
            return false;
        }
    }

    // ---------------------------------------------------------
    // Mining location operations
    // ---------------------------------------------------------

    /**
     * Returns all active Ceylon mining location districts.
     * Used by OriginVerifier to validate a gem's claimed origin.
     *
     * @return a List of district name strings
     */
    public List<String> getCeylonMiningDistricts() {
        List<String> districts = new ArrayList<>();
        String sql = "SELECT DISTINCT district FROM ceylon_mining_locations WHERE is_active = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) {
                districts.add(rs.getString("district").toLowerCase());
            }
        } catch (SQLException e) {
            System.out.println("Failed to retrieve mining districts.");
            e.printStackTrace();
        }
        return districts;
    }

    /**
     * Returns all active Ceylon mining villages.
     * Used by OriginVerifier for more precise origin matching.
     *
     * @return a List of village name strings
     */
    public List<String> getCeylonMiningVillages() {
        List<String> villages = new ArrayList<>();
        String sql = "SELECT DISTINCT village FROM ceylon_mining_locations " +
                     "WHERE is_active = 1 AND village IS NOT NULL";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) {
                villages.add(rs.getString("village").toLowerCase());
            }
        } catch (SQLException e) {
            System.out.println("Failed to retrieve mining villages.");
            e.printStackTrace();
        }
        return villages;
    }

    // ---------------------------------------------------------
    // Statistics for dashboard
    // ---------------------------------------------------------

    /**
     * Returns the total number of gems registered in the system.
     * Used in the statistics dashboard.
     *
     * @return total gem count as an integer
     */
    public int getTotalGemCount() {
        String sql = "SELECT COUNT(*) FROM gems";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Returns the total number of verified Ceylon gems.
     * Used in the statistics dashboard.
     *
     * @return count of gems where is_ceylon_gem = 1
     */
    public int getCeylonGemCount() {
        String sql = "SELECT COUNT(*) FROM gems WHERE is_ceylon_gem = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Returns the total number of unresolved fraud alerts.
     * Used in the statistics dashboard to highlight pending issues.
     *
     * @return count of unresolved alerts
     */
    public int getUnresolvedAlertCount() {
        String sql = "SELECT COUNT(*) FROM gem_alerts WHERE is_resolved = 0";
        try (Statement stmt = connection.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ---------------------------------------------------------
    // Connection management
    // ---------------------------------------------------------

    /**
     * Closes the database connection when the application exits.
     * Should be called in the main method's finally block or
     * when the application window is closed.
     *
     * Not closing the connection can cause data corruption in SQLite.
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("💎 Database connection closed.");
            }
        } catch (SQLException e) {
            System.out.println("Failed to close database connection.");
            e.printStackTrace();
        }
    }
}