# Ceylon Gem Origin Tracking System

## Why This System Was Created

Sri Lanka loses over one billion dollars worth of gems every year due to fraud, smuggling, and counterfeit origin claims. African and Madagascar gems are frequently smuggled into Sri Lanka and sold internationally as genuine Ceylon gems, damaging the reputation of Sri Lanka's gem industry and cheating buyers worldwide. There was no existing digital system that could track a gem from the moment it is mined until it reaches the final buyer, and verify its origin at every step.

This system was created to solve that problem. It gives every Ceylon gem a digital passport — a complete, tamper-evident chain of custody from mine to buyer — so that anyone in the world can verify a gem's authentic Sri Lankan origin with a single QR code scan.

The system was built as the coursework project for Programming Data Structures and Algorithms at the National Institute of Business Management, HND Software Engineering programme. The core data structure selected is a Doubly Linked List, which models each gem's journey as a chain of nodes where every stage can be traversed forward and backward.

---

## Project Structure

```
Gem_Java_Project/
    gem-origin-tracker/
        src/
            api/
                ApiResponse.java
                ApiRouter.java
                ApiServer.java
                CorsFilter.java
                handlers/
                    AlertHandler.java
                    GemHandler.java
                    QRHandler.java
                    ReportHandler.java
                    StageHandler.java
                    StatsHandler.java
                    VerificationHandler.java
            database/
                DBConnection.java
                schema.sql
            model/
                GemLinkedList.java
                GemNode.java
                GemStage.java
            report/
                ReportGenerator.java
            service/
                OriginVerifier.java
                PriceTracker.java
                QRCodeService.java
                TrackingService.java
            ui/
                GemForm.java
                JourneyViewer.java
                MainMenu.java
            Main.java
    lib/
        core-3.5.2.jar
        gson-2.10.1.jar
        javase-3.5.2.jar
        javax.servlet-api-3.1.0.jar
        jetty-http-9.4.51.v20230217.jar
        jetty-io-9.4.51.v20230217.jar
        jetty-server-9.4.51.v20230217.jar
        jetty-servlet-9.4.51.v20230217.jar
        jetty-util-9.4.51.v20230217.jar
        jetty-webapp-9.4.51.v20230217.jar
        slf4j-api-2.0.9.jar
        slf4j-simple-2.0.9.jar
        spark-core-2.9.4.jar
        sqlite-jdbc-3.45.1.0.jar
    qrcodes/
    reports/
    gem_tracker.db
```

---

## Data Structure — Doubly Linked List

The Doubly Linked List is the core data structure of the entire system. Each gem in the system has its own linked list. Every time the gem moves to a new stage — mining, cutting, trading, exporting, buying — a new node is appended to the tail of the list.

Each node stores the complete details of one stage: location, person name, weight, price, date, and optional certificate or export details. Every node has a `next` pointer pointing to the next stage and a `prev` pointer pointing to the previous stage.

This structure allows:
- **Forward traversal** — from mining node to current owner using next pointers
- **Backward traversal** — from current owner back to the mine using prev pointers
- **Stage insertion** — new stages appended to the tail in O(1) time
- **Stage removal** — any node removed with O(n) search and O(1) pointer adjustment

The Doubly Linked List was chosen over a Singly Linked List because backward traversal is essential for fraud auditing — investigators need to trace a gem back to its origin, not just forward.

---

## Input Requirements

1. Gem type — e.g. Blue Sapphire, Ruby, Cat's Eye
2. Gem color description — hue, tone, saturation details
3. Original weight in carats at time of mining
4. Initial estimated price in Sri Lankan Rupees
5. Origin mine name and location
6. Mining district — must match a known Sri Lankan mining district
7. Miner full name, NIC number, and contact number
8. Mining date in yyyy-MM-dd format
9. Stage details for each subsequent stage — location, person name, NIC, contact, weight, price, date
10. Optional export details — flight number, invoice number, destination country

---

## Output Requirements

1. Unique gem ID generated from gem type abbreviation and timestamp — e.g. BS-1773990209789
2. Complete journey timeline showing every stage from mine to current owner
3. Origin verification result — VERIFIED GENUINE CEYLON GEM or FAILED
4. Fraud risk score from 0 to 100 with factor breakdown
5. Price appreciation report showing value increase at each stage
6. Weight analysis showing carats lost during cutting and processing
7. QR code PNG image encoding the full gem journey for mobile scanning
8. Fraud alert notifications when certificate is missing or origin is suspicious
9. Official gem certificate document showing the full chain of custody
10. Text report files saved to the reports folder for record keeping

---

## Process Requirements

1. Register a new gem — creates the head node of the linked list and saves to SQLite database
2. Add a stage — creates a new tail node, sets prev pointer to previous tail, saves to database
3. Remove a stage — removes a node at a given position, reconnects prev and next pointers
4. Forward traversal — traverse the linked list from head to tail using next pointers
5. Backward traversal — traverse the linked list from tail to head using prev pointers
6. Origin verification — compare mining location against known Sri Lankan districts and villages
7. Fraud risk calculation — score 0 to 100 based on five weighted risk factors
8. Price appreciation tracking — calculate value increase at each stage and percentage growth
9. Weight loss analysis — calculate carats lost between original weight and current weight
10. QR code generation — encode gem journey text into a PNG QR image using ZXing library

---

## Three Novel Features

### Feature 1 — QR Code Digital Passport
Every gem can have a QR code generated that encodes its complete origin-to-buyer journey. The QR code can be scanned by any mobile device without installing any app. The encoded text includes gem ID, type, origin, all stage details, weight, price, and verification status. This allows buyers anywhere in the world to instantly verify a gem's authenticity.

### Feature 2 — Price Appreciation Tracker
The system records the gem's price at every stage of the journey and automatically calculates the price increase between each stage, the total appreciation in rupees, and the appreciation as a percentage of the original mining price. This gives miners, traders, and exporters a complete financial picture of how the gem's value grows through the supply chain.

### Feature 3 — Origin Verification and Fraud Alert System
The system automatically checks whether a gem's recorded mining location matches any known Sri Lankan gem mining area. It also checks for certificate presence, location consistency, unusual price jumps, and incomplete stage data. When any check fails, a fraud alert is automatically saved to the database. The fraud risk score combines all five factors into a single number from 0 to 100.

---

## How to Run

### CLI Mode
```
1. Open the project in VS Code
2. Run Main.java
3. Enter 1 at the mode selection menu
4. Use the terminal menu to register gems, add stages, view journeys
```

### API Server Mode
```
1. Open the project in VS Code
2. Run Main.java
3. Enter 2 at the mode selection menu
4. Server starts on http://localhost:4567
5. Test with: http://localhost:4567/api/health
6. Connect the React frontend at http://localhost:8080
```

---

## Technologies Used

| Technology | Version | Purpose |
|---|---|---|
| Java | JDK 24 | Core application language |
| SQLite | 3.45.1.0 | Embedded database |
| Spark Java | 2.9.4 | Lightweight HTTP server |
| Jetty | 9.4.51 | Embedded web server used by Spark |
| Gson | 2.10.1 | JSON serialization and deserialization |
| ZXing | 3.5.2 | QR code generation |
| SLF4J | 2.0.9 | Logging framework |

---

## API Base URL

```
http://localhost:4567/api
```

### Key Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | /api/health | Check if server is running |
| GET | /api/gems | Get all gems |
| POST | /api/gems | Register new gem |
| GET | /api/gems/:id | Get gem journey |
| GET | /api/gems/:id/verify | Verify gem origin |
| GET | /api/gems/:id/risk | Get fraud risk score |
| POST | /api/gems/:id/qr | Generate QR code |
| GET | /api/stats/summary | Dashboard statistics |
| GET | /api/alerts/unresolved | Get fraud alerts |

---

## Database

The system uses SQLite with a single file `gem_tracker.db` at the project root. The database has four tables:

- **gems** — stores gem registration details including type, origin, district, color, and miner info
- **gem_stages** — stores each stage node with all journey details and stage order number
- **gem_alerts** — stores fraud alerts with type, message, and resolution status
- **ceylon_locations** — stores valid Sri Lankan mining districts and villages used for origin verification

---

## Project Details

- **Institution:** National Institute of Business Management
- **Programme:** HND Software Engineering
- **Module:** Programming Data Structures and Algorithms
- **Coursework:** CW1 — Group Project (2 members)
- **Data Structure:** Doubly Linked List
- **Submission:** GitHub link on LMS + Reflective Report PDF on LMS
