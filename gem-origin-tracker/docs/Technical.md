# Technical Documentation — Ceylon Gem Origin Tracking System

## System Overview

The system is a full-stack application with three distinct layers. The data layer uses SQLite for persistent storage. The business logic layer is written entirely in Java and uses a Doubly Linked List as the core data structure. The presentation layer has two modes — a CLI terminal interface and a REST API server that serves a React frontend.

When the application starts, Main.java asks the user to choose between CLI mode and API mode. In both modes, the same Java service layer handles all business logic. The only difference is the interface layer that calls the services.

---

## Startup Flow

```
Main.java starts
    ↓
DBConnection.getInstance() — opens SQLite connection, creates tables if they do not exist
    ↓
TrackingService constructor — loads all gems from database into memory HashMap
    ↓
User chooses mode (1 = CLI, 2 = API)
    ↓
CLI Mode:                           API Mode:
MainMenu.start()                    ApiServer.start()
    ↓                                   ↓
Terminal menu loop                  CorsFilter.apply() — sets CORS headers
    ↓                                   ↓
GemForm / JourneyViewer             ApiRouter.registerRoutes() — registers 38 endpoints
    ↓                                   ↓
TrackingService methods             Spark listens on port 4567
    ↓                                   ↓
DBConnection methods                HTTP requests handled by handler classes
    ↓                                   ↓
SQLite database                     TrackingService methods
                                        ↓
                                    DBConnection methods
                                        ↓
                                    SQLite database
```

---

## File Descriptions

### Main.java
The application entry point. Contains three methods — `showModeSelectionMenu()` which displays the mode selection menu and reads the user choice, `startCliMode()` which creates a MainMenu instance and starts the terminal loop, and `startApiMode()` which creates an ApiServer instance, starts it, and waits for the user to press Enter to stop. Registers a JVM shutdown hook that closes the database connection and stops the API server cleanly on exit.

---

### model/GemNode.java
Represents one node in the Doubly Linked List. Each node is one stage of a gem's journey. Contains all fields for a single stage: gemId, gemType, stage, location, personName, weightInCarats, priceInRupees, stageDate, personIdNumber, contactNumber, certificateNumber, issuingAuthority, flightNumber, invoiceNumber, destinationCountry, notes. Also contains two pointer fields: `next` pointing to the next stage node and `prev` pointing to the previous stage node. These two pointers are what make this a Doubly Linked List rather than a Singly Linked List.

---

### model/GemLinkedList.java
The core data structure class. Manages one complete gem journey as a Doubly Linked List. Contains `head` pointing to the first node (mining stage) and `tail` pointing to the most recent stage. Contains `size` tracking the number of nodes.

Key operations:
- `addStage(GemNode node)` — appends a new node to the tail, sets node.prev to old tail, sets old tail.next to new node, updates tail reference, increments size
- `removeStageAt(int position)` — traverses to the node at position, fixes prev and next pointers of surrounding nodes, removes the node
- `getAllStages()` — traverses from head to tail using next pointers, builds ArrayList of all nodes
- `displayJourneyForward()` — prints each node from head to tail using next pointers
- `displayJourneyBackward()` — starts at tail, prints each node using prev pointers back to head
- `calculateWeightLoss()` — returns head weight minus tail weight
- `calculateWeightLossPercentage()` — returns weight loss divided by head weight times 100
- `calculatePriceAppreciation()` — returns tail price minus head price
- `getMiningNode()` — returns head
- `getCurrentStageNode()` — returns tail
- `findByPersonName(String name)` — traverses list looking for matching person name
- `findByCertificateNumber(String cert)` — traverses list looking for matching certificate number

---

### model/GemStage.java
An enum defining the five valid stages of a gem journey. Values are MINING, CUTTING, TRADING, EXPORTING, BUYING. Each enum value has a `label` field with a human readable name like "Mining Stage" and a `locationHint` field describing what kind of location is expected at that stage. These hints are displayed in the CLI to guide the user.

---

### database/DBConnection.java
Manages the SQLite database connection using the Singleton pattern — only one instance is ever created. Uses JDBC to connect to gem_tracker.db at the project root. On first connection, reads schema.sql and creates all four tables if they do not exist.

Key methods:
- `getInstance()` — returns the single shared instance, creates it if null
- `saveGem(GemLinkedList list, String colorDesc, String mine, String district, String village)` — INSERT into gems table
- `saveStage(GemNode node, int stageOrder)` — INSERT into gem_stages table
- `loadGemJourney(String gemId)` — SELECT all stages for a gem ordered by stage_order, builds GemLinkedList by appending each node
- `getAllGemIds()` — SELECT all gem IDs from gems table
- `searchGemsByType(String type)` — SELECT gem IDs where gem_type matches
- `searchGemsByDistrict(String district)` — SELECT gem IDs where district matches
- `gemExists(String gemId)` — returns true if gem ID already in database
- `deleteGem(String gemId)` — DELETE from gems and all related stages and alerts
- `deleteAllStages(String gemId)` — DELETE all stage records for a gem, used when re-saving after stage removal
- `getTotalGemCount()` — SELECT COUNT from gems table
- `getCeylonGemCount()` — SELECT COUNT where is_ceylon_verified equals true
- `getUnresolvedAlertCount()` — SELECT COUNT from gem_alerts where is_resolved equals false
- `getUnresolvedAlerts()` — SELECT all unresolved alerts as formatted strings
- `saveAlert(String gemId, String alertType, String message)` — INSERT into gem_alerts table
- `resolveAlert(int alertId)` — UPDATE gem_alerts SET is_resolved = 1
- `getCeylonMiningDistricts()` — SELECT all districts from ceylon_locations
- `getCeylonMiningVillages()` — SELECT all villages from ceylon_locations
- `getCeylonVerifiedGems()` — SELECT gem IDs where is_ceylon_verified equals true
- `closeConnection()` — closes the JDBC connection

---

### database/schema.sql
SQL file that defines the four database tables. The `gems` table stores gem registration data with columns for gem_id (primary key), gem_type, color_description, origin_mine, district, village, registered_at, and is_ceylon_verified. The `gem_stages` table stores each journey stage with columns for id, gem_id (foreign key), stage_type, location, person_name, person_id_number, contact_number, weight_in_carats, price_in_rupees, stage_date, stage_order, certificate_number, issuing_authority, flight_number, invoice_number, destination_country, and notes. The `gem_alerts` table stores fraud alerts with columns for id, gem_id, alert_type, message, created_at, and is_resolved. The `ceylon_locations` table stores valid mining locations with columns for id, location_type (DISTRICT or VILLAGE), and location_name.

---

### service/TrackingService.java
The main business logic layer. Acts as the bridge between the UI or API handler layer and the database layer. On construction, loads all gem journeys from the database into an in-memory HashMap called `activeGems` where the key is the gem ID and the value is the GemLinkedList.

Key methods:
- `registerNewGem(...)` — generates a unique gem ID from the gem type abbreviation and current timestamp, creates the first GemNode with MINING stage, creates a GemLinkedList, saves to database, adds to activeGems map
- `addStageToGem(...)` — gets the gem list from activeGems, creates new GemNode, calls list.addStage(), saves new node to database
- `addExportDetails(...)` — sets flight number, invoice number, destination country on the current tail node
- `addCertificateDetails(...)` — sets certificate number and issuing authority on the current tail node
- `addNoteToCurrentStage(...)` — sets notes field on the current tail node
- `removeStage(...)` — calls list.removeStageAt(), then deletes all stages from database and re-saves remaining stages to keep stage_order values consecutive
- `getGemList(String gemId)` — checks activeGems first, falls back to database if not found
- `getAllGemIds()` — delegates to DBConnection
- `searchByGemType(...)` — delegates to DBConnection
- `searchByDistrict(...)` — delegates to DBConnection
- `getCeylonVerifiedGems()` — delegates to DBConnection
- `getTotalGemCount()` — delegates to DBConnection
- `getCeylonGemCount()` — delegates to DBConnection
- `getUnresolvedAlertCount()` — delegates to DBConnection
- `getUnresolvedAlerts()` — delegates to DBConnection
- `deleteGem(...)` — removes from activeGems map and delegates delete to DBConnection
- `displayFullJourney(...)` — calls list.displayJourneyForward()
- `displayReverseJourney(...)` — calls list.displayJourneyBackward()
- `displayWeightAnalysis(...)` — prints weight loss calculations to console
- `displayPriceAppreciation(...)` — prints price at each stage and total appreciation to console

---

### service/OriginVerifier.java
Implements Novel Feature 3 — the origin verification and fraud alert system. On construction, loads all valid Ceylon mining districts and villages from the ceylon_locations database table into two HashSets for O(1) lookup.

Key methods:
- `verifyGemOrigin(String gemId)` — gets the mining node, extracts location string, calls quickLocationCheck, if fails saves a ORIGIN_MISMATCH alert to database
- `quickLocationCheck(String location)` — converts location to lowercase, checks if any known district or village name is contained in the location string
- `verifyCertificatePresence(String gemId)` — traverses all stages looking for a non-null certificate number, if none found saves MISSING_CERTIFICATE alert
- `checkCurrentLocationConsistency(String gemId)` — checks if any stage location contains suspicious keywords that do not match the stage type
- `getVerificationStatusLabel(String gemId)` — returns a human readable verification status string
- `runFullAuthentication(String gemId)` — runs all three checks and prints detailed results to console

---

### service/PriceTracker.java
Implements Novel Feature 2 — the price appreciation tracker. Works with the gem linked list to calculate price growth through the supply chain.

Key methods:
- `getPriceHistory(String gemId)` — traverses the linked list and builds a list of PriceRecord objects, each containing stage name, location, person, date, price, price increase from previous stage, and percentage increase
- `getTotalPriceAppreciation(String gemId)` — returns tail price minus head price
- `getTotalAppreciationPercentage(String gemId)` — returns appreciation divided by head price times 100
- `getHighestValueAddedStage(String gemId)` — traverses the list finding which stage had the largest price increase
- `PriceRecord` — inner static class with fields stageNumber, stageName, location, personName, date, price, priceIncrease, percentIncrease and their getters

---

### service/QRCodeService.java
Implements Novel Feature 1 — the QR code digital passport. Uses the ZXing library to generate QR codes.

Key methods:
- `generateQRCode(String gemId)` — builds the QR code content string from all stage details, calls ZXing QRCodeWriter to create a BitMatrix, renders it as a BufferedImage, saves as PNG to the qrcodes folder, returns the file path
- `regenerateQRCode(String gemId)` — deletes the existing QR file if present, calls generateQRCode to create a fresh one with latest data
- `qrCodeExists(String gemId)` — checks if the PNG file exists in the qrcodes folder
- `getQRCodePath(String gemId)` — returns the expected file path for the gem's QR code
- `previewQRContent(String gemId)` — builds and prints the text that would be encoded in the QR code without generating the image
- `buildQRContent(String gemId)` — private method that builds the formatted text string containing all gem details for encoding

---

### report/ReportGenerator.java
Generates plain text report files and saves them to the reports folder.

Key methods:
- `generateFullJourneyReport(String gemId)` — builds a detailed text report including gem overview, all stages with full details, weight analysis, price appreciation, and verification status, saves to reports folder with timestamp in filename
- `generateSummaryReport(String gemId)` — builds a shorter report with key statistics only
- `generateAllGemsReport()` — generates a system-wide report covering all registered gems
- `buildReportHeader(String gemId)` — private method that builds the top section of any report
- `buildStageSection(GemNode node, int index)` — private method that formats one stage node as text

---

### api/ApiResponse.java
Standard JSON response wrapper used by every API endpoint. Every response from the system follows the same structure: success boolean, message string, data object, timestamp long, and statusCode integer. Uses Gson for serialization.

Key static factory methods:
- `success(String message, Object data)` — creates response with success true and status 200
- `success(String message)` — creates response with success true, null data, and status 200
- `created(String message, Object data)` — creates response with success true and status 201
- `error(String message)` — creates response with success false and status 400
- `error(String message, int statusCode)` — creates response with specific status code
- `notFound(String resourceName, String id)` — creates 404 response
- `serverError(String message)` — creates 500 response
- `badRequest(String message)` — creates 400 response
- `toJson()` — converts the ApiResponse object to a JSON string
- `fromJson(String json, Class clazz)` — parses a JSON string into a Java object

---

### api/ApiServer.java
Manages the Spark HTTP server lifecycle using the Singleton pattern. Configures the port, thread pool, CORS filter, and all routes before waiting for initialization.

Key methods:
- `getInstance()` — returns the single shared instance
- `start()` — sets port to 4567, configures thread pool with 8 max threads, calls CorsFilter.apply(), calls router.registerRoutes(), calls awaitInitialization(), prints startup banner
- `stop()` — calls Spark.stop() to release the port
- `isRunning()` — returns whether the server is currently active
- `getBaseUrl()` — returns the full API base URL string

---

### api/CorsFilter.java
Configures HTTP headers that allow the React frontend running on port 8080 to call the Java API running on port 4567. Without CORS headers, the browser blocks cross-origin requests.

Key method:
- `apply()` — registers a Spark before() filter that adds Access-Control-Allow-Origin, Access-Control-Allow-Methods, Access-Control-Allow-Headers, and Access-Control-Allow-Credentials headers to every response, also registers an options() handler that responds to browser preflight requests with 200 OK

---

### api/ApiRouter.java
Registers all 38 REST API endpoints with the Spark framework. Maps every URL pattern and HTTP method to its corresponding handler method.

Key methods:
- `registerRoutes()` — calls all eight private registration methods in order
- `registerHealthRoutes()` — registers GET /api/health
- `registerGemRoutes()` — registers 6 gem endpoints
- `registerStageRoutes()` — registers 6 stage endpoints
- `registerVerificationRoutes()` — registers 6 verification endpoints
- `registerAlertRoutes()` — registers 4 alert endpoints
- `registerStatsRoutes()` — registers 5 statistics endpoints
- `registerQRRoutes()` — registers 6 QR code endpoints
- `registerReportRoutes()` — registers 4 report endpoints
- `registerErrorHandlers()` — registers notFound and internalServerError handlers that return JSON instead of HTML

---

### api/handlers/GemHandler.java
Handles all HTTP requests for gem registration and retrieval.

Endpoints handled: GET /api/gems, GET /api/gems/:id, GET /api/gems/search, GET /api/gems/ceylon, POST /api/gems, DELETE /api/gems/:id

---

### api/handlers/StageHandler.java
Handles all HTTP requests for adding and managing gem journey stages.

Endpoints handled: GET /api/gems/:id/stages, POST /api/gems/:id/stages, DELETE /api/gems/:id/stages/:position, PUT /api/gems/:id/stages/current/certificate, PUT /api/gems/:id/stages/current/export, PUT /api/gems/:id/stages/current/notes

---

### api/handlers/VerificationHandler.java
Handles all HTTP requests for origin verification and fraud risk scoring.

Endpoints handled: GET /api/gems/:id/verify, GET /api/gems/:id/verify/origin, GET /api/gems/:id/verify/certificate, GET /api/verify/all, GET /api/verify/locations, GET /api/gems/:id/risk

---

### api/handlers/AlertHandler.java
Handles all HTTP requests for fraud alert management.

Endpoints handled: GET /api/alerts, GET /api/alerts/unresolved, GET /api/alerts/gem/:gemId, PUT /api/alerts/:id/resolve

---

### api/handlers/StatsHandler.java
Handles all HTTP requests for system statistics and gem comparison.

Endpoints handled: GET /api/stats, GET /api/stats/summary, GET /api/gems/:id/price, GET /api/gems/:id/weight, GET /api/gems/compare

---

### api/handlers/QRHandler.java
Handles all HTTP requests for QR code generation, download, and preview. The download endpoint serves raw PNG bytes directly to the browser rather than a JSON response, allowing the frontend to use the endpoint URL directly as an img src attribute.

Endpoints handled: GET /api/gems/:id/qr, POST /api/gems/:id/qr, PUT /api/gems/:id/qr, GET /api/gems/:id/qr/download, GET /api/gems/:id/qr/preview, GET /api/qr/status

---

### api/handlers/ReportHandler.java
Handles all HTTP requests for report generation and listing.

Endpoints handled: POST /api/gems/:id/report/full, POST /api/gems/:id/report/summary, POST /api/report/all, GET /api/reports

---

### ui/MainMenu.java
The main CLI menu loop. Displays numbered menu options and reads user input in a while loop until the user selects exit. Creates instances of GemForm and JourneyViewer and delegates all operations to them. Also displays direct statistics summaries and calls OriginVerifier for bulk verification.

---

### ui/GemForm.java
CLI form for registering new gems and adding stages. Reads all input fields line by line from the console, validates them, and calls TrackingService methods to save the data.

---

### ui/JourneyViewer.java
CLI viewer for displaying gem journeys. Allows the user to enter a gem ID and see the full forward journey, backward journey, weight analysis, price appreciation, verification status, and QR code generation.

---

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS gems (
    gem_id TEXT PRIMARY KEY,
    gem_type TEXT NOT NULL,
    color_description TEXT,
    origin_mine TEXT,
    district TEXT,
    village TEXT,
    registered_at TEXT,
    is_ceylon_verified INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gem_stages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    gem_id TEXT NOT NULL,
    stage_type TEXT NOT NULL,
    location TEXT,
    person_name TEXT,
    person_id_number TEXT,
    contact_number TEXT,
    weight_in_carats REAL,
    price_in_rupees REAL,
    stage_date TEXT,
    stage_order INTEGER,
    certificate_number TEXT,
    issuing_authority TEXT,
    flight_number TEXT,
    invoice_number TEXT,
    destination_country TEXT,
    notes TEXT,
    FOREIGN KEY (gem_id) REFERENCES gems(gem_id)
);

CREATE TABLE IF NOT EXISTS gem_alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    gem_id TEXT NOT NULL,
    alert_type TEXT NOT NULL,
    message TEXT,
    created_at TEXT,
    is_resolved INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ceylon_locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    location_type TEXT NOT NULL,
    location_name TEXT NOT NULL
);
```

---

## How Data Flows Through the System

### Registering a New Gem
```
User submits form (CLI or React frontend)
    ↓
GemHandler.registerGem() or GemForm.registerNewGem() validates input
    ↓
TrackingService.registerNewGem() generates gem ID, creates GemNode with MINING stage
    ↓
new GemLinkedList(gemId) — creates empty list
list.addStage(miningNode) — sets head = tail = miningNode, size = 1
    ↓
DBConnection.saveGem() — INSERT into gems table
DBConnection.saveStage() — INSERT into gem_stages with stage_order = 1
    ↓
activeGems.put(gemId, list) — adds to in-memory cache
    ↓
Response returned to user with new gem ID
```

### Adding a Stage
```
User submits stage form (CLI or React frontend)
    ↓
StageHandler.addStage() or GemForm validates input
    ↓
TrackingService.addStageToGem() retrieves gem list from activeGems
    ↓
new GemNode(...) — creates new node
list.addStage(newNode):
    newNode.prev = old tail
    old tail.next = newNode
    tail = newNode
    size++
    ↓
DBConnection.saveStage() — INSERT with stage_order = list.getSize()
    ↓
Response returned to user
```

### Verifying Origin
```
Request to GET /api/gems/:id/verify
    ↓
VerificationHandler.runFullAuthentication()
    ↓
OriginVerifier.verifyGemOrigin() — gets mining node, calls quickLocationCheck
quickLocationCheck() — converts to lowercase, checks against knownDistricts and knownVillages HashSets
    ↓
If fails: DBConnection.saveAlert() — INSERT ORIGIN_MISMATCH alert
    ↓
OriginVerifier.verifyCertificatePresence() — traverses list for certificate
If fails: DBConnection.saveAlert() — INSERT MISSING_CERTIFICATE alert
    ↓
Response with all three check results and overall verification status
```