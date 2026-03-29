# Ceylon Gem Origin Tracking System
## Complete Technical Documentation

**NIBM HND Software Engineering — PDSA Coursework 1**  
**Data Structure: Doubly Linked List**  
**Stack: Java (Spark) + SQLite + React + TypeScript**

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Data Structure — Doubly Linked List](#3-data-structure--doubly-linked-list)
4. [Backend — Java Files](#4-backend--java-files)
5. [Database Design](#5-database-design)
6. [REST API — All 57 Endpoints](#6-rest-api--all-57-endpoints)
7. [Frontend — React Files](#7-frontend--react-files)
8. [Novel Feature 1 — Audit Trail](#8-novel-feature-1--audit-trail)
9. [Novel Feature 2 — Price Estimator](#9-novel-feature-2--price-estimator)
10. [Novel Feature 3 — Journey Map](#10-novel-feature-3--journey-map)
11. [Edit & Delete with Audit Recording](#11-edit--delete-with-audit-recording)
12. [Bug Fixes](#12-bug-fixes)
13. [How to Run](#13-how-to-run)

---

## 1. Project Overview

The Ceylon Gem Origin Tracking System is a full-stack web application that provides a complete digital passport for Sri Lankan gemstones. Every gemstone is tracked from the moment it is mined until it reaches its final buyer. Each stage of the journey is stored as a node in a **Doubly Linked List**.

### Problem Solved
- Sri Lankan gem supply chain was entirely paper-based
- Gem certificates were easily forged
- Non-Ceylon stones were sold as genuine Ceylon gems
- No digital mechanism existed to track chain of custody
- Buyers could not independently verify gem provenance

### Solution
- Every gem = one `GemLinkedList` (Doubly Linked List)
- Every stage = one `GemNode` (a node in the list)
- Head node = MINING stage (origin)
- Tail node = current owner (most recent stage)
- Forward traversal = chronological journey (head → tail)
- Backward traversal = reverse audit trace (tail → head)

---

## 2. System Architecture

```
┌─────────────────────────────────────┐
│   React + TypeScript Frontend        │
│   Port 8080 | 11 Pages | api.ts     │
└──────────────┬──────────────────────┘
               │ HTTP JSON (fetch)
               ▼
┌─────────────────────────────────────┐
│   Java REST API Backend             │
│   Port 4567 | Spark Framework       │
│   57 Endpoints | 10 Handler Classes │
└──────────────┬──────────────────────┘
               │ JDBC
               ▼
┌─────────────────────────────────────┐
│   SQLite Database                   │
│   gem_tracker.db                    │
│   3 Tables: gems, gem_stages,       │
│             gem_audit_log           │
└─────────────────────────────────────┘
```

### Technology Stack

| Component | Technology | Version |
|---|---|---|
| Backend Language | Java | JDK 24 |
| REST Framework | Spark Java | 2.9.4 |
| HTTP Server | Jetty (embedded) | 9.4.51 |
| Database | SQLite + JDBC | 3.45.1 |
| JSON Library | Gson | 2.10.1 |
| QR Code Library | ZXing | 3.5.1 |
| Frontend | React + TypeScript | 18 + TS5 |
| Build Tool | Vite | 5.x |
| CSS Framework | Tailwind CSS | 3.x |
| Animation | Framer Motion | 11.x |
| Map Library | Leaflet.js (CDN) | 1.9.4 |

---

## 3. Data Structure — Doubly Linked List

### Why Doubly Linked List?

| Data Structure | Why Not Chosen |
|---|---|
| Singly Linked List | No backward traversal — cannot trace journey in reverse |
| Array / ArrayList | O(n) deletion with shifting — inefficient for middle removal |
| Binary Search Tree | Unordered — gem stages have a fixed chronological sequence |
| Stack / Queue | Only access head or tail — cannot access middle stages |
| **Doubly Linked List** | **O(1) tail insert, O(1) delete, bidirectional — PERFECT FIT** |

### GemNode — Node Structure

**File:** `src/model/GemNode.java`

```java
public class GemNode {
    // Core identification
    private GemStage stage;         // MINING/CUTTING/TRADING/EXPORTING/BUYING
    private String   gemId;         // Unique gem ID e.g. BS-1774732541974
    private String   gemType;       // e.g. Blue Sapphire, Ruby

    // Location and person
    private String   location;      // Where this stage occurred
    private String   personName;    // Who was responsible
    private String   personIdNumber;// NIC or passport number
    private String   contactNumber; // Contact phone

    // Weight and value
    private double   weightInCarats;// Weight at this stage
    private double   priceInRupees; // Value at this stage

    // Date and certificate
    private LocalDate stageDate;         // Date of stage
    private String    certificateNumber; // Optional certificate
    private String    issuingAuthority;  // Optional issuing body

    // Export-specific (EXPORTING stage only)
    private String    flightNumber;      // Export flight number
    private String    invoiceNumber;     // Export invoice
    private String    destinationCountry;// Destination country

    // Notes
    private String    notes;             // Additional notes

    // Doubly Linked List pointers
    public GemNode next;  // Next stage (forward)
    public GemNode prev;  // Previous stage (backward)
}
```

### All Getter Methods

```java
getStage()              // Returns GemStage enum
getGemId()              // Returns gem ID string
getGemType()            // Returns gem type string
getLocation()           // Returns location
getPersonName()         // Returns person name
getPersonIdNumber()     // Returns NIC number
getContactNumber()      // Returns contact number
getWeightInCarats()     // Returns weight as double
getPriceInRupees()      // Returns price as double
getStageDate()          // Returns LocalDate
getCertificateNumber()  // Returns certificate number
getIssuingAuthority()   // Returns issuing authority
getFlightNumber()       // Returns flight number
getInvoiceNumber()      // Returns invoice number
getDestinationCountry() // Returns destination country
getNotes()              // Returns notes
```

### All Setter Methods

```java
// Original setters
setPersonIdNumber(String)     // Set NIC number
setContactNumber(String)      // Set contact
setCertificateNumber(String)  // Set certificate
setIssuingAuthority(String)   // Set authority
setFlightNumber(String)       // Set flight number
setInvoiceNumber(String)      // Set invoice
setDestinationCountry(String) // Set destination
setNotes(String)              // Set notes
setPriceInRupees(double)      // Set price

// NEW setters added (were missing — caused updateStage() to fail)
setLocation(String)           // Set location
setPersonName(String)         // Set person name
setWeightInCarats(double)     // Set weight
setStageDate(LocalDate)       // Set stage date
```

### GemLinkedList — All Methods

**File:** `src/model/GemLinkedList.java`

```java
addStage(GemNode)               // Append node to tail — O(1)
removeStageAt(int position)     // Remove node at index — O(n)
getAllStages()                   // Returns List<GemNode> head to tail — O(n)
getMiningNode()                  // Returns head (MINING stage) — O(1)
getCurrentStageNode()            // Returns tail (current owner) — O(1)
displayJourneyForward()          // Print head → tail — O(n)
displayJourneyBackward()         // Print tail → head — O(n)
findByPersonName(String)         // Linear search by name — O(n)
findByCertificateNumber(String)  // Linear search by cert — O(n)
calculateWeightLoss()            // tail.weight - head.weight — O(1)
calculateWeightLossPercentage()  // weight loss / original * 100 — O(1)
calculatePriceAppreciation()     // tail.price - head.price — O(1)
getSize()                        // Total node count — O(1)
getGemId()                       // Returns gem ID — O(1)
```

### Stage Addition Algorithm — O(1)

```java
// addStage(GemNode newNode)
if (head == null) {
    head = newNode;
    tail = newNode;
    newNode.prev = null;
    newNode.next = null;
} else {
    newNode.prev = tail;   // Link new node back to current tail
    tail.next = newNode;   // Link current tail forward to new node
    tail = newNode;        // Update tail pointer
    newNode.next = null;   // New node has no successor
}
size++;
```

### Stage Deletion Algorithm — O(n)

```java
// removeStageAt(int position)
GemNode target = traverse to position;  // O(n)

if (target == head) {
    head = target.next;
    if (head != null) head.prev = null;
} else if (target == tail) {
    tail = target.prev;
    if (tail != null) tail.next = null;
} else {
    target.prev.next = target.next;  // Bypass target forward
    target.next.prev = target.prev;  // Bypass target backward
}
size--;
// After: delete all DB stages, re-save all remaining in order
```

### Complexity Analysis

| Operation | Time | Space |
|---|---|---|
| Add stage to tail | O(1) | O(1) |
| Remove stage at position | O(n) | O(1) |
| Forward traversal | O(n) | O(1) |
| Backward traversal | O(n) | O(1) |
| Get head node | O(1) | O(1) |
| Get tail node | O(1) | O(1) |
| Find by person name | O(n) | O(1) |
| Calculate weight loss | O(1) | O(1) |
| Overall space for n stages | — | O(n) |

### GemStage Enum

**File:** `src/model/GemStage.java`

```java
MINING    // Head — always first node
CUTTING   // Cutting and polishing
TRADING   // Gem trader
EXPORTING // Export with flight/invoice details
BUYING    // Final buyer — tail when purchased
```

---

## 4. Backend — Java Files

### File Structure

```
Gem_Java_Project/
├── Main.java                          // Entry point — CLI or API mode
├── src/
│   ├── api/
│   │   ├── ApiRouter.java             // Registers all 57 endpoints
│   │   ├── ApiServer.java             // Starts Spark on port 4567
│   │   ├── ApiResponse.java           // Standardised JSON wrapper
│   │   ├── CorsFilter.java            // CORS headers for all responses
│   │   └── handlers/
│   │       ├── GemHandler.java        // GET/POST/DELETE /gems
│   │       ├── StageHandler.java      // GET/POST/PUT/DELETE /stages
│   │       ├── StatsHandler.java      // Stats + compare
│   │       ├── AlertHandler.java      // Fraud alerts
│   │       ├── VerificationHandler.java // Origin verification
│   │       ├── QRHandler.java         // QR code operations
│   │       ├── ReportHandler.java     // Report generation
│   │       ├── AuditHandler.java      // Audit trail — Feature 1
│   │       ├── PriceEstimatorHandler.java // Price estimator — Feature 2
│   │       └── JourneyMapHandler.java // Journey map — Feature 3
│   ├── service/
│   │   ├── TrackingService.java       // Main business logic bridge
│   │   ├── AuditService.java          // Records all changes to audit log
│   │   ├── OriginVerifier.java        // Ceylon certification logic
│   │   ├── PriceEstimator.java        // Market value estimation engine
│   │   ├── JourneyMapService.java     // GPS coordinate mapping
│   │   ├── PriceTracker.java          // Price history tracking
│   │   ├── QRCodeService.java         // ZXing QR generation
│   │   └── ReportGenerator.java       // Text report builder
│   ├── model/
│   │   ├── GemLinkedList.java         // Doubly Linked List class
│   │   ├── GemNode.java               // Node class — all 18 fields
│   │   └── GemStage.java              // Enum: MINING/CUTTING/TRADING/EXPORTING/BUYING
│   ├── database/
│   │   └── DBConnection.java          // SQLite JDBC singleton
│   └── report/
│       └── ReportGenerator.java       // Gem journey report builder
```

### Main.java

```
Purpose  : Entry point of the application
Mode 1   : CLI mode — terminal based interface
Mode 2   : API server mode — starts Spark on port 4567
Key call : ApiServer.start() → ApiRouter.registerRoutes()
```

### ApiRouter.java

```
Purpose       : Registers all 57 endpoints with Spark framework
Critical Rule : Static routes MUST be registered before dynamic :id routes
Method        : registerAllGemRoutes() — guarantees correct order

Order for /api/gems/*:
  1. GET /api/gems              (exact — no conflict)
  2. GET /api/gems/search       (static — BEFORE /:id)
  3. GET /api/gems/ceylon       (static — BEFORE /:id)
  4. GET /api/gems/compare      (static — BEFORE /:id)
  5. GET /api/gems/:id          (dynamic — LAST)
```

### ApiResponse.java

```java
// Every API response uses this wrapper
{
  "success":    true/false,
  "message":    "Human readable message",
  "data":       { ... payload ... },
  "statusCode": 200/201/400/404/500,
  "timestamp":  1774732541974
}
```

### GemHandler.java

```
Constructor : GemHandler(TrackingService, OriginVerifier, AuditService)
Methods     :
  getAllGems()    → GET /api/gems
  getGemById()   → GET /api/gems/:id
  searchGems()   → GET /api/gems/search
  getCeylonGems()→ GET /api/gems/ceylon
  registerGem()  → POST /api/gems
                   Calls auditService.logGemRegistered() after success
  deleteGem()    → DELETE /api/gems/:id
                   Calls auditService.logGemDeleted() BEFORE deleting
```

### StageHandler.java

```
Constructor : StageHandler(TrackingService, AuditService)
Methods     :
  getAllStages()    → GET /api/gems/:id/stages
  addStage()       → POST /api/gems/:id/stages
                     Calls auditService.logStageAdded()
  updateStage()    → PUT /api/gems/:id/stages/:position  ← NEW
                     Captures old snapshot → updates fields →
                     saves to DB → captures new snapshot →
                     calls auditService.logStageUpdated() per field
  removeStage()    → DELETE /api/gems/:id/stages/:position
                     Calls auditService.logStageDeleted() BEFORE delete
  addCertificate() → PUT /api/gems/:id/stages/current/certificate
  addExportDetails()→ PUT /api/gems/:id/stages/current/export
  addNotes()       → PUT /api/gems/:id/stages/current/notes
```

### TrackingService.java

```
Purpose  : Main business logic bridge between handlers and database
Pattern  : Maintains in-memory HashMap<String, GemLinkedList> activeGems
Startup  : loadAllGemsFromDatabase() populates map from SQLite on boot

Key Methods:
  registerNewGem()         // Creates first MINING node + saves to DB
  addStageToGem()          // Appends new node to tail + saves to DB
  removeStage()            // Removes node + resyncs all DB stage_order
  updateStageInDatabase()  // ← NEW: deleteAll + re-save all in order
  getGemList()             // Returns from map or loads from DB
  deleteGem()              // Removes from map + deletes from DB
  getAllGemIds()            // Queries DB for all gem IDs
  searchByGemType()        // DB search by gem type
  searchByDistrict()       // DB search by district
  getCeylonVerifiedGems()  // Returns verified gem IDs
```

### updateStageInDatabase() — New Method

```java
public boolean updateStageInDatabase(String gemId, int position, GemNode node) {
    GemLinkedList list = getGemList(gemId);
    // 1. Delete ALL stage records for this gem
    db.deleteAllStages(gemId);
    // 2. Re-save ALL nodes from in-memory list in correct order
    List<GemNode> allStages = list.getAllStages();
    for (int i = 0; i < allStages.size(); i++) {
        db.saveStage(allStages.get(i), i + 1);  // stage_order = i+1
    }
    return true;
}
```

### DBConnection.java

```
Pattern  : Singleton — only one SQLite connection open at a time
Database : gem_tracker.db (file-based, no server needed)
Tables   : Creates gems, gem_stages, gem_audit_log on startup

Key Methods:
  getInstance()       // Singleton access
  saveGem()           // INSERT into gems table
  saveStage()         // INSERT into gem_stages table
  loadGemJourney()    // SELECT + rebuild GemLinkedList from DB
  deleteGem()         // DELETE gem + all stages
  deleteAllStages()   // DELETE all stages for a gem (before resync)
  getAllGemIds()       // SELECT all gem_id values
  executeRaw()        // Execute raw SQL (used for table creation)
  insertAuditLog()    // INSERT into gem_audit_log
  queryAuditLogs()    // SELECT from gem_audit_log
  queryAuditLogsFiltered() // SELECT with two filter params
```

### AuditService.java

```
Purpose    : Records every modification as an immutable audit log entry
Pattern    : Shared singleton across GemHandler, StageHandler, AuditHandler
Table      : gem_audit_log (append-only — never UPDATE or DELETE)

Action Type Constants:
  ACTION_GEM_REGISTERED   = "GEM_REGISTERED"
  ACTION_STAGE_ADDED      = "STAGE_ADDED"
  ACTION_STAGE_UPDATED    = "STAGE_UPDATED"
  ACTION_STAGE_DELETED    = "STAGE_DELETED"
  ACTION_GEM_DELETED      = "GEM_DELETED"
  ACTION_CERTIFICATE_ADDED= "CERTIFICATE_ADDED"
  ACTION_EXPORT_ADDED     = "EXPORT_ADDED"
  ACTION_NOTE_ADDED       = "NOTE_ADDED"

Log Methods:
  logGemRegistered(gemId, gemType, origin, weight, miner)
  logStageAdded(gemId, stageNumber, stageType, location, person, weight, price, date)
  logStageUpdated(gemId, stageNumber, stageType, fieldChanged, oldValue, newValue)
  logStageDeleted(gemId, stageNumber, GemNode)
  logGemDeleted(gemId, gemType, stageCount)
  logCertificateAdded(gemId, stageNumber, certNumber, authority)
  logExportAdded(gemId, stageNumber, flightNumber, invoiceNumber, destination)
  logNoteAdded(gemId, stageNumber, note)

Snapshot Method:
  buildNodeSnapshot(GemNode) → Formatted string of ALL node fields
  Used to capture full before/after state for STAGE_UPDATED records

Retrieval Methods:
  getAllAuditLogs()
  getAuditLogsForGem(gemId)
  getAuditLogsByAction(action)
  getAuditSummary()
  getRecentAuditLogs(limit)
  getAuditLogsForGemByAction(gemId, action)
```

### OriginVerifier.java

```
Purpose : Verifies gem origin against known Sri Lankan mining locations
Checks  :
  1. District check — against 6 valid districts:
     Ratnapura, Matale, Galle, Monaragala, Kalutara, Kurunegala
  2. Village check — against 10 verified villages:
     Ratnapura, Pelmadulla, Elahera, Kahawatte, Doranegoda,
     Okkampitiya, Meetiyagoda, Balangoda, Eheliyagoda, Nivitigala

Result : VERIFIED CEYLON → 1.35x price premium
         UNVERIFIED      → 0.75x price discount
         ORIGIN_MISMATCH → Fraud alert generated
```

### PriceEstimator.java

```
Purpose  : Calculates Low/Mid/High market value estimates
Formula  : Mid = BasePricePerCarat × Weight × WeightMult × OriginMult × StageMult
           Low = Mid × 0.80
           High= Mid × 1.20

Base Prices per Carat (Rs.):
  Blue Sapphire: 150,000  |  Ruby: 120,000
  Cat's Eye:     100,000  |  Star Sapphire: 90,000
  Yellow Sapphire: 80,000 |  Alexandrite: 200,000
  Spinel: 70,000          |  Moonstone: 30,000

Origin Multipliers:
  VERIFIED CEYLON : 1.35x
  UNVERIFIED      : 0.75x

Stage Multipliers:
  1 stage: 1.0x  |  2 stages: 2.5x  |  3 stages: 4.0x
  4 stages: 6.0x |  5 stages: 7.5x

Pricing Status:
  UNDERPRICED   : actual < 60% of mid estimate
  FAIRLY_PRICED : 60% ≤ actual ≤ 150% of mid estimate
  OVERPRICED    : actual > 150% of mid estimate
```

### JourneyMapService.java

```
Purpose  : Converts GemLinkedList nodes to GPS coordinates
Method   : Location string → HashMap lookup → lat/lng coordinates
Locations: 40+ Sri Lankan gem locations + international cities

GPS Coordinate Lookup includes:
  Ratnapura, Pelmadulla, Elahera, Kahawatte, Matale,
  Colombo Port, Colombo Airport, Galle, Kandy, Kurunegala
  + Dubai, Bangkok, New York, London, Tokyo, Antwerp etc.

Distance Formula: Haversine
  a = sin²(Δlat/2) + cos(lat1) × cos(lat2) × sin²(Δlon/2)
  c = 2 × atan2(√a, √(1-a))
  distance = 6371 × c  (km)

Output:
  MapPin per node: lat, lng, colour, popup content
  routeCoordinates[]: head to tail (forward traversal)
  reverseRoute[]:     tail to head (backward traversal)
  totalDistance: sum of all stage-to-stage Haversine distances
```

---

## 5. Database Design

### gems Table

```sql
CREATE TABLE IF NOT EXISTS gems (
  gem_id           TEXT PRIMARY KEY,
  gem_type         TEXT NOT NULL,
  color_description TEXT,
  origin_mine      TEXT,
  district         TEXT,
  village          TEXT,
  is_verified      INTEGER DEFAULT 0,
  registered_at    TEXT NOT NULL
);
```

### gem_stages Table

```sql
CREATE TABLE IF NOT EXISTS gem_stages (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  gem_id              TEXT NOT NULL,
  stage_order         INTEGER NOT NULL,
  stage_type          TEXT NOT NULL,
  location            TEXT,
  person_name         TEXT,
  person_id_number    TEXT,
  contact_number      TEXT,
  weight_in_carats    REAL,
  price_in_rupees     REAL,
  stage_date          TEXT,
  certificate_number  TEXT,
  issuing_authority   TEXT,
  flight_number       TEXT,
  invoice_number      TEXT,
  destination_country TEXT,
  notes               TEXT
);
```

### gem_audit_log Table

```sql
CREATE TABLE IF NOT EXISTS gem_audit_log (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  gem_id        TEXT NOT NULL,
  action        TEXT NOT NULL,
  stage_number  INTEGER,
  stage_type    TEXT,
  field_changed TEXT,
  old_value     TEXT,
  new_value     TEXT,
  description   TEXT,
  changed_at    TEXT NOT NULL
);
-- Append-only: never UPDATE or DELETE from this table
```

---

## 6. REST API — All 57 Endpoints

### Gem Operations (7)

```
GET    /api/gems                        getAllGems
GET    /api/gems/search?type=&district= searchGems
GET    /api/gems/ceylon                 getCeylonGems
GET    /api/gems/compare?gem1=&gem2=    compareGems
GET    /api/gems/:id                    getGemById
POST   /api/gems                        registerGem
DELETE /api/gems/:id                    deleteGem
```

### Stage Operations (7)

```
GET    /api/gems/:id/stages                     getAllStages
POST   /api/gems/:id/stages                     addStage
PUT    /api/gems/:id/stages/:position           updateStage  ← NEW
DELETE /api/gems/:id/stages/:position           removeStage
PUT    /api/gems/:id/stages/current/certificate addCertificate
PUT    /api/gems/:id/stages/current/export      addExportDetails
PUT    /api/gems/:id/stages/current/notes       addNotes
```

### Verification (6)

```
GET /api/gems/:id/verify            runFullAuthentication
GET /api/gems/:id/verify/origin     verifyOrigin
GET /api/gems/:id/verify/certificate verifyCertificate
GET /api/verify/all                 verifyAllGems
GET /api/verify/locations           getValidLocations
GET /api/gems/:id/risk              getFraudRiskScore
```

### Alerts (4)

```
GET /api/alerts                 getAllAlerts
GET /api/alerts/unresolved      getUnresolvedAlerts
GET /api/alerts/gem/:gemId      getAlertsByGem
PUT /api/alerts/:id/resolve     resolveAlert
```

### Statistics (5)

```
GET /api/stats                  getAllStats
GET /api/stats/summary          getDashboardSummary
GET /api/gems/:id/price         getPriceHistory
GET /api/gems/:id/weight        getWeightAnalysis
GET /api/gems/compare           compareGems
```

### QR Codes (6)

```
GET  /api/gems/:id/qr           getQRStatus
POST /api/gems/:id/qr           generateQRCode
PUT  /api/gems/:id/qr           regenerateQRCode
GET  /api/gems/:id/qr/download  downloadQRCode
GET  /api/gems/:id/qr/preview   previewQRContent
GET  /api/qr/status             getAllQRStatus
```

### Reports (4)

```
POST /api/gems/:id/report/full    generateFullReport
POST /api/gems/:id/report/summary generateSummaryReport
POST /api/report/all              generateAllGemsReport
GET  /api/reports                 listSavedReports
```

### Audit Trail (5)

```
GET /api/audit                      getAllAuditLogs
GET /api/audit/summary              getAuditSummary
GET /api/audit/recent?limit=20      getRecentAuditLogs
GET /api/audit/gem/:gemId           getAuditLogsForGem
GET /api/audit/action/:action       getAuditLogsByAction
```

### Price Estimator (5)

```
GET /api/estimate/overview          getMarketOverview
GET /api/estimate/all               getAllEstimates
GET /api/estimate/compare           compareEstimates
GET /api/estimate/:gemId/summary    getEstimateSummaryForGem
GET /api/estimate/:gemId            getEstimateForGem
```

### Journey Map (6)

```
GET /api/map/overview               getAllGemsMapOverview
GET /api/map/locations              getKnownLocations
GET /api/map/:gemId/pins            getJourneyPins
GET /api/map/:gemId/route           getJourneyRoute
GET /api/map/:gemId/stats           getJourneyStats
GET /api/map/:gemId                 getJourneyMapData
```

### Health (1)

```
GET /api/health                     Health check
```

---

## 7. Frontend — React Files

### File Structure

```
ceylon-gem-ledger-main/src/
├── api/
│   └── api.ts                     // All 57 typed API functions
├── pages/
│   ├── AppPages.tsx               // Root router — renders page by subPage state
│   └── app/
│       ├── Sidebar.tsx            // Fixed left nav — alert badge — NEW FEATURES divider
│       ├── TopBar.tsx             // Fixed top bar with page title
│       ├── DashboardPage.tsx      // Home — 4 metric cards + stats
│       ├── TrackPage.tsx          // Gem journey timeline + Add Stage form
│       ├── RegisterPage.tsx       // 3-step gem registration form
│       ├── ComparePage.tsx        // Side-by-side gem comparison
│       ├── AlertsPage.tsx         // Fraud alerts — filter + resolve
│       ├── ReportsPage.tsx        // Report generation + certificate view
│       ├── QRPage.tsx             // QR code management
│       ├── SettingsPage.tsx       // App settings
│       ├── EditPage.tsx           // ← NEW: Edit + Delete stages
│       ├── AuditPage.tsx          // ← NEW: Change history viewer
│       ├── PriceEstimatorPage.tsx // ← NEW: Market value estimation
│       └── JourneyMapPage.tsx     // ← NEW: Interactive Leaflet map
```

### api.ts — Key Functions Added

```typescript
// Update a stage (NEW)
updateStage(gemId: string, position: number, stageData: {
    location?:           string;
    personName?:         string;
    personIdNumber?:     string;
    contactNumber?:      string;
    weightInCarats?:     number;
    priceInRupees?:      number;
    stageDate?:          string;
    certificateNumber?:  string;
    issuingAuthority?:   string;
    flightNumber?:       string;
    invoiceNumber?:      string;
    destinationCountry?: string;
    notes?:              string;
})
// Calls PUT /api/gems/:id/stages/:position
```

### Sidebar.tsx — Navigation Items

```typescript
const sidebarItems = [
  { id: "dashboard",  icon: LayoutDashboard, label: "Dashboard"      },
  { id: "track",      icon: Search,          label: "Track Gem"       },
  { id: "register",   icon: PlusCircle,      label: "Register Gem"    },
  { id: "compare",    icon: GitCompare,       label: "Compare Gems"   },
  { id: "alerts",     icon: AlertTriangle,   label: "Fraud Alerts"    },
  { id: "reports",    icon: FileText,        label: "Reports"         },
  { id: "qr",         icon: QrCode,          label: "QR Codes"        },
  { id: "__divider__",label: "NEW FEATURES"                           },
  { id: "edit",       icon: Edit3,           label: "Edit & Delete"   },// NEW
  { id: "audit",      icon: Clock,           label: "Audit Trail"     },// NEW
  { id: "estimate",   icon: TrendingUp,      label: "Price Estimator" },// NEW
  { id: "map",        icon: Map,             label: "Journey Map"     },// NEW
  { id: "settings",   icon: Settings,        label: "Settings"        },
];
// Alert badge on "alerts" item — refreshed every 30 seconds
// NEW badge on edit, audit, estimate, map items
```

### AppPages.tsx — Route Added

```typescript
// NEW routes added
{subPage === "edit"     && <EditPage />}
{subPage === "audit"    && <AuditPage />}
{subPage === "estimate" && <PriceEstimatorPage />}
{subPage === "map"      && <JourneyMapPage />}
```

---

## 8. Novel Feature 1 — Audit Trail

### What It Does
Records every modification to any gem or stage as an immutable, append-only log entry with full before-and-after value capture.

### How It Works

```
User edits a stage
        ↓
StageHandler.updateStage()
        ↓
auditService.buildNodeSnapshot(node)  ← Capture OLD state
        ↓
Update fields on node (setLocation, setPersonName, etc.)
        ↓
For each changed field:
  auditService.logStageUpdated(gemId, position, stageType,
                               fieldName, oldValue, newValue)
        ↓
trackingService.updateStageInDatabase(gemId, position, node)
  → db.deleteAllStages(gemId)
  → db.saveStage() for each remaining node in order
        ↓
auditService.buildNodeSnapshot(node)  ← Capture NEW state
        ↓
auditService.logStageUpdated(gemId, ..., "FULL_UPDATE",
                              oldSnapshot, newSnapshot)
        ↓
Written to gem_audit_log — PERMANENTLY — never modified
```

### AuditPage.tsx Features
- 6 summary stat cards (total changes, added, updated, deleted, registered, certificates)
- Gem dropdown + manual ID search
- 9 filter tabs (All / Added / Updated / Deleted / Registered / Gem Deleted / Certificate / Export / Notes)
- Colour-coded timeline: green = addition, amber = update, red = deletion
- Click to expand → Before (Old Value) and After (New Value) panels side by side

---

## 9. Novel Feature 2 — Price Estimator

### Formula

```
Mid  = BasePricePerCarat × WeightInCarats × WeightMult × OriginMult × StageMult
Low  = Mid × 0.80
High = Mid × 1.20
```

### PriceEstimatorPage.tsx Features
- Market overview: total estimated vs actual portfolio value
- Per-gem: Low / Mid / High estimate cards
- Visual range bar showing where actual price falls
- Line chart of price history across all stages
- Collapsible calculation breakdown
- Stage price history table

---

## 10. Novel Feature 3 — Journey Map

### How GPS Coordinates Are Resolved

```
GemNode.location (String) → HashMap lookup → {lat, lng}
Example: "Ratnapura, Pelmadulla" → {6.6828, 80.3992}
         "Colombo Airport"       → {7.1803, 79.8842}
         "Dubai"                 → {25.2048, 55.2708}
```

### Haversine Distance Formula

```java
private double calculateDistance(double lat1, double lon1,
                                  double lat2, double lon2) {
    double R = 6371; // Earth radius in km
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat/2) * Math.sin(dLat/2)
             + Math.cos(Math.toRadians(lat1))
             * Math.cos(Math.toRadians(lat2))
             * Math.sin(dLon/2) * Math.sin(dLon/2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
}
```

### Pin Colours by Stage Type

```
MINING    → Blue    (#1B4F8A)
CUTTING   → Amber   (#C9A84C)
TRADING   → Green   (#166534)
EXPORTING → Purple  (#7C3AED)
BUYING    → Red     (#DC2626)
```

### Forward vs Backward Traversal on Map

```
Forward  (toggle OFF): draw route head → tail  (next pointers)
Backward (toggle ON) : draw route tail → head  (prev pointers)
```

Leaflet.js loaded from CDN (not npm) to avoid TypeScript type conflicts:
```typescript
// Injected dynamically into document head on first render
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"/>
<link  href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css"/>
// Accessed as window.L in React useEffect hook
```

### JourneyMapPage.tsx Features
- Interactive Leaflet.js map with OpenStreetMap tiles
- Colour-coded pins per stage type with click popups
- Forward / Backward traversal toggle button
- Route polyline drawn on map
- Stats panel: total distance, domestic stages, international stages
- Stage details table below the map

---

## 11. Edit & Delete with Audit Recording

### EditPage.tsx Features
- Gem selector dropdown + manual ID search
- Gem overview summary card
- Each stage shown as a card with coloured left border
- **Edit button** → inline form expands with all fields for that stage type
- **Save button** → PUT /api/gems/:id/stages/:position → audit log recorded
- **Delete button** → two-step confirm → DELETE → node removed from DLL
- Auto-refresh after every save or delete

### Editable Fields Per Stage Type

```
MINING    : location, personName, personIdNumber, contactNumber,
            weightInCarats, priceInRupees, stageDate, notes

CUTTING   : + certificateNumber, issuingAuthority

TRADING   : + certificateNumber, issuingAuthority

EXPORTING : + flightNumber, invoiceNumber, destinationCountry,
              certificateNumber, issuingAuthority

BUYING    : location, personName, personIdNumber, contactNumber,
            weightInCarats, priceInRupees, stageDate, notes
```

### Delete Flow

```
User clicks Delete button
        ↓
Two-step confirm: "Sure?" → "Yes, Delete"
        ↓
auditService.logStageDeleted(gemId, position, node)  ← BEFORE delete
        ↓
trackingService.removeStage(gemId, position)
  → list.removeStageAt(position)     ← DLL pointer reconnection
  → db.deleteAllStages(gemId)        ← Clear DB
  → db.saveStage() for each remaining ← Resync with correct order
        ↓
Frontend auto-refreshes gem detail
```

---

## 12. Bug Fixes

### Bug 1 — JavaScript Number Precision

**Problem:** Gem IDs like `1774732541974` exceed `Number.MAX_SAFE_INTEGER`
(2^53 - 1 = 9007199254740991). JavaScript silently corrupted the last
digits when parsing as a JSON number, causing all API requests for that
gem to return 404.

**Fix:** `String.valueOf(gemId)` in all Java handler classes forces the
gem ID to serialize as a JSON string instead of a JSON number.

```java
// WRONG — serializes as JSON number (precision lost in JS)
result.put("gemId", gemId);

// CORRECT — serializes as JSON string (no precision loss)
result.put("gemId", String.valueOf(gemId));
```

### Bug 2 — Spark Route Order

**Problem:** Dynamic route `/api/gems/:id` captured the word "search"
as a gem ID when registered before `/api/gems/search`.

**Fix:** All `/api/gems/*` routes registered in one method in correct order:

```java
private void registerAllGemRoutes() {
    get("/api/gems",         gemHandler::getAllGems);    // 1. exact
    get("/api/gems/search",  gemHandler::searchGems);   // 2. static
    get("/api/gems/ceylon",  gemHandler::getCeylonGems);// 3. static
    get("/api/gems/compare", statsHandler::compareGems);// 4. static
    get("/api/gems/:id",     gemHandler::getGemById);   // 5. dynamic LAST
    post("/api/gems",        gemHandler::registerGem);
    delete("/api/gems/:id",  gemHandler::deleteGem);
}
```

### Bug 3 — Missing Setters in GemNode

**Problem:** `StageHandler.updateStage()` called `node.setLocation()`,
`node.setPersonName()`, `node.setWeightInCarats()`, `node.setStageDate()`
but these methods did not exist in `GemNode.java`.

**Fix:** Added 4 missing setter methods to `GemNode.java`:

```java
public void setLocation(String location)          { this.location = location; }
public void setPersonName(String personName)       { this.personName = personName; }
public void setWeightInCarats(double w)            { this.weightInCarats = w; }
public void setStageDate(LocalDate stageDate)      { this.stageDate = stageDate; }
```

### Bug 4 — Shared AuditService Instance

**Problem:** If separate `AuditService` instances had been created per
handler, table initialisation could have conflicted and audit entries
could have been inconsistent.

**Fix:** Single `AuditService` instance created once in `ApiRouter`
constructor and injected into all three handlers:

```java
AuditService auditService = new AuditService(trackingService);
this.gemHandler   = new GemHandler(trackingService, originVerifier, auditService);
this.stageHandler = new StageHandler(trackingService, auditService);
this.auditHandler = new AuditHandler(auditService, trackingService);
```

---

## 13. How to Run

### Start Backend

```cmd
cd C:\Users\Abhishake\OneDrive\Desktop\PDSA_Project\Project\Gem_Java_Project

:: Clean compiled files
Get-ChildItem -Recurse -Filter "*.class" | Remove-Item -Force

:: Run Main.java in VS Code using the Run button
:: Select option 2 for API mode

:: Server starts at:
http://localhost:4567/api/health
```

### Start Frontend

```cmd
cd ceylon-gem-ledger-main\ceylon-gem-ledger-main
npm run dev

:: Frontend available at:
http://localhost:8080
```

### Verify Everything Works

```
http://localhost:4567/api/health          ← Backend health check
http://localhost:4567/api/gems            ← All gems JSON
http://localhost:8080                     ← Frontend app
```

---

## Summary — What Was Built

| Item | Detail |
|---|---|
| Data Structure | Doubly Linked List |
| Backend Endpoints | 57 REST endpoints |
| Frontend Pages | 11 pages |
| Database Tables | 3 (gems, gem_stages, gem_audit_log) |
| Novel Feature 1 | Audit Trail — immutable change history |
| Novel Feature 2 | Price Estimator — market value engine |
| Novel Feature 3 | Journey Map — GPS + DLL traversal visualised |
| Bugs Fixed | 4 (JS precision, route order, missing setters, shared service) |
| Backend Language | Java + Spark Framework |
| Frontend Language | React + TypeScript + Tailwind CSS |
| Database | SQLite (file-based, no server needed) |

---

*Ceylon Gem Origin Tracking System — NIBM HND Software Engineering PDSA Coursework 1*