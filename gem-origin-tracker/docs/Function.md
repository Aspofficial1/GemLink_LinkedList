# Function Reference — Ceylon Gem Origin Tracking System

## model/GemNode.java

| Function | What It Does |
|---|---|
| `GemNode(String gemId, String gemType, GemStage stage, String location, String personName, double weightInCarats, double priceInRupees, LocalDate stageDate)` | Constructor — creates a new stage node with all required fields. Sets next and prev to null. |
| `getGemId()` | Returns the gem ID this node belongs to |
| `getGemType()` | Returns the gem type string e.g. Blue Sapphire |
| `getStage()` | Returns the GemStage enum value for this node |
| `getLocation()` | Returns the location string for this stage |
| `getPersonName()` | Returns the name of the person responsible at this stage |
| `getWeightInCarats()` | Returns the gem weight at this stage |
| `getPriceInRupees()` | Returns the gem value at this stage |
| `getStageDate()` | Returns the LocalDate of this stage |
| `getPersonIdNumber()` | Returns the NIC number of the person at this stage or null |
| `getContactNumber()` | Returns the contact number of the person at this stage or null |
| `getCertificateNumber()` | Returns the certificate number if set or null |
| `getIssuingAuthority()` | Returns the certificate issuing authority if set or null |
| `getFlightNumber()` | Returns the export flight number if set or null |
| `getInvoiceNumber()` | Returns the export invoice number if set or null |
| `getDestinationCountry()` | Returns the export destination country if set or null |
| `getNotes()` | Returns the notes text if set or null |
| `getNext()` | Returns the next GemNode pointer — used for forward traversal |
| `getPrev()` | Returns the previous GemNode pointer — used for backward traversal |
| `setPersonIdNumber(String v)` | Sets the NIC number field |
| `setContactNumber(String v)` | Sets the contact number field |
| `setCertificateNumber(String v)` | Sets the certificate number field |
| `setIssuingAuthority(String v)` | Sets the issuing authority field |
| `setFlightNumber(String v)` | Sets the flight number field |
| `setInvoiceNumber(String v)` | Sets the invoice number field |
| `setDestinationCountry(String v)` | Sets the destination country field |
| `setNotes(String v)` | Sets the notes field |
| `setNext(GemNode node)` | Sets the next pointer — called by GemLinkedList during stage addition |
| `setPrev(GemNode node)` | Sets the prev pointer — called by GemLinkedList during stage addition |

---

## model/GemLinkedList.java

| Function | What It Does |
|---|---|
| `GemLinkedList(String gemId)` | Constructor — creates an empty linked list for the given gem ID |
| `addStage(GemNode newNode)` | Appends a new node to the tail. If list is empty sets head and tail to newNode. Otherwise sets newNode.prev = tail, tail.next = newNode, updates tail to newNode. Increments size. |
| `removeStageAt(int position)` | Traverses to the node at the given 0-based position. Fixes the surrounding prev and next pointers to skip the removed node. Decrements size. Returns the removed node. |
| `getAllStages()` | Traverses from head to tail using next pointers. Adds each node to an ArrayList. Returns the complete list in chronological order. |
| `getMiningNode()` | Returns the head node which is always the mining stage |
| `getCurrentStageNode()` | Returns the tail node which is always the most recent stage |
| `getGemId()` | Returns the gem ID this list belongs to |
| `getSize()` | Returns the number of nodes in the list |
| `displayJourneyForward()` | Traverses from head to tail using next pointers and prints each node's details to the console |
| `displayJourneyBackward()` | Starts at tail and traverses backwards using prev pointers, prints each node to the console |
| `printSummary()` | Prints a brief summary showing gem ID, type, origin, current stage, number of stages |
| `calculateWeightLoss()` | Returns head weight minus tail weight in carats |
| `calculateWeightLossPercentage()` | Returns weight loss divided by head weight multiplied by 100 |
| `calculatePriceAppreciation()` | Returns tail price minus head price in rupees |
| `findByPersonName(String name)` | Traverses the list comparing person names case-insensitively. Returns the first matching node or null. |
| `findByCertificateNumber(String cert)` | Traverses the list comparing certificate numbers. Returns the first matching node or null. |

---

## model/GemStage.java

| Value | Label | Location Hint |
|---|---|---|
| `MINING` | Mining Stage | Mine location, district, village |
| `CUTTING` | Cutting and Polishing Stage | Gem cutting workshop or gem street |
| `TRADING` | Trading Stage | Gem bureau or trading house |
| `EXPORTING` | Exporting Stage | International airport or customs |
| `BUYING` | Buying Stage | International buyer location |

| Function | What It Does |
|---|---|
| `getLabel()` | Returns the human readable stage name string |
| `getLocationHint()` | Returns a hint about what kind of location is expected at this stage |

---

## database/DBConnection.java

| Function | What It Does |
|---|---|
| `getInstance()` | Returns the single shared DBConnection instance. Creates it on first call using the Singleton pattern. |
| `initializeDatabase()` | Private method called on first connection. Reads schema.sql and executes all CREATE TABLE statements. Seeds the ceylon_locations table with known Sri Lankan mining districts and villages. |
| `saveGem(GemLinkedList list, String colorDesc, String mine, String district, String village)` | Executes INSERT INTO gems with all registration details. Returns true on success. |
| `saveStage(GemNode node, int stageOrder)` | Executes INSERT INTO gem_stages with all stage details and the stage_order number. Returns true on success. |
| `loadGemJourney(String gemId)` | Executes SELECT on gem_stages WHERE gem_id = gemId ORDER BY stage_order ASC. Creates a GemLinkedList and calls addStage for each row. Returns the complete list. |
| `getAllGemIds()` | Executes SELECT gem_id FROM gems. Returns a List of all gem ID strings. |
| `searchGemsByType(String gemType)` | Executes SELECT gem_id FROM gems WHERE gem_type LIKE. Returns matching gem ID list. |
| `searchGemsByDistrict(String district)` | Executes SELECT gem_id FROM gems WHERE district LIKE. Returns matching gem ID list. |
| `gemExists(String gemId)` | Executes SELECT COUNT from gems WHERE gem_id = gemId. Returns true if count is greater than zero. |
| `deleteGem(String gemId)` | Executes DELETE FROM gems, gem_stages, and gem_alerts WHERE gem_id = gemId. Returns true on success. |
| `deleteAllStages(String gemId)` | Executes DELETE FROM gem_stages WHERE gem_id = gemId. Used before re-saving stages after removal. |
| `getTotalGemCount()` | Executes SELECT COUNT from gems. Returns integer count. |
| `getCeylonGemCount()` | Executes SELECT COUNT from gems WHERE is_ceylon_verified = 1. Returns integer count. |
| `getUnresolvedAlertCount()` | Executes SELECT COUNT from gem_alerts WHERE is_resolved = 0. Returns integer count. |
| `getUnresolvedAlerts()` | Executes SELECT from gem_alerts WHERE is_resolved = 0. Returns list of formatted strings. |
| `saveAlert(String gemId, String alertType, String message)` | Executes INSERT INTO gem_alerts with current timestamp and is_resolved = 0. |
| `resolveAlert(int alertId)` | Executes UPDATE gem_alerts SET is_resolved = 1 WHERE id = alertId. Returns true if row was updated. |
| `getCeylonMiningDistricts()` | Executes SELECT location_name FROM ceylon_locations WHERE location_type = DISTRICT. Returns list. |
| `getCeylonMiningVillages()` | Executes SELECT location_name FROM ceylon_locations WHERE location_type = VILLAGE. Returns list. |
| `getCeylonVerifiedGems()` | Executes SELECT gem_id FROM gems WHERE is_ceylon_verified = 1. Returns list. |
| `closeConnection()` | Calls connection.close() on the JDBC connection. Called on JVM shutdown. |

---

## service/TrackingService.java

| Function | What It Does |
|---|---|
| `TrackingService()` | Constructor — creates DBConnection singleton, creates empty activeGems HashMap, calls loadAllGemsFromDatabase() |
| `registerNewGem(String gemType, String colorDescription, String originMine, String district, String village, String minerName, String minerIdNumber, String minerContact, double weightInCarats, double priceInRupees, LocalDate miningDate)` | Generates gem ID, creates mining GemNode, creates GemLinkedList, saves to database, adds to activeGems |
| `addStageToGem(String gemId, GemStage stage, String location, String personName, String personIdNumber, String contactNumber, double weightInCarats, double priceInRupees, LocalDate stageDate)` | Gets gem list, creates new GemNode, calls list.addStage(), saves to database |
| `addExportDetails(String gemId, String flightNumber, String invoiceNumber, String destinationCountry)` | Gets current tail node, sets flight, invoice, destination fields |
| `addCertificateDetails(String gemId, String certificateNumber, String issuingAuthority)` | Gets current tail node, sets certificate and authority fields |
| `addNoteToCurrentStage(String gemId, String note)` | Gets current tail node, sets notes field |
| `removeStage(String gemId, int position)` | Calls list.removeStageAt(), deletes all stages from database, re-saves remaining stages with correct order numbers |
| `getGemList(String gemId)` | Checks activeGems HashMap first. If not found calls DBConnection.loadGemJourney() and caches in activeGems. |
| `displayFullJourney(String gemId)` | Gets gem list, calls list.displayJourneyForward() |
| `displayReverseJourney(String gemId)` | Gets gem list, calls list.displayJourneyBackward() |
| `displayGemSummary(String gemId)` | Gets gem list, calls list.printSummary() |
| `getCurrentOwner(String gemId)` | Gets gem list, returns list.getCurrentStageNode() |
| `getMiningStage(String gemId)` | Gets gem list, returns list.getMiningNode() |
| `getAllStages(String gemId)` | Gets gem list, returns list.getAllStages() |
| `getAllGemIds()` | Delegates to DBConnection.getAllGemIds() |
| `searchByGemType(String gemType)` | Delegates to DBConnection.searchGemsByType() |
| `searchByDistrict(String district)` | Delegates to DBConnection.searchGemsByDistrict() |
| `searchStageByPerson(String gemId, String personName)` | Gets gem list, calls list.findByPersonName() |
| `searchStageByCertificate(String gemId, String certificateNumber)` | Gets gem list, calls list.findByCertificateNumber() |
| `getCeylonVerifiedGems()` | Delegates to DBConnection.getCeylonVerifiedGems() |
| `getTotalGemCount()` | Delegates to DBConnection.getTotalGemCount() |
| `getCeylonGemCount()` | Delegates to DBConnection.getCeylonGemCount() |
| `getUnresolvedAlertCount()` | Delegates to DBConnection.getUnresolvedAlertCount() |
| `getUnresolvedAlerts()` | Delegates to DBConnection.getUnresolvedAlerts() |
| `deleteGem(String gemId)` | Removes from activeGems, delegates to DBConnection.deleteGem() |
| `reloadGemFromDatabase(String gemId)` | Calls DBConnection.loadGemJourney() and updates activeGems |
| `getActiveGems()` | Returns the full activeGems HashMap |
| `loadAllGemsFromDatabase()` | Private — called in constructor. Gets all gem IDs, loads each journey, puts in activeGems. |
| `generateGemId(String gemType)` | Private — splits gem type into words, takes first letter of each word, appends current timestamp |
| `displayWeightAnalysis(String gemId)` | Gets gem list, prints original weight, current weight, weight lost, weight loss percentage |
| `displayPriceAppreciation(String gemId)` | Gets gem list, traverses all stages printing price at each stage and value added |

---

## service/OriginVerifier.java

| Function | What It Does |
|---|---|
| `OriginVerifier(TrackingService trackingService)` | Constructor — loads known districts and villages from database into knownDistricts and knownVillages HashSets |
| `verifyGemOrigin(String gemId)` | Gets mining node, calls quickLocationCheck on location string, saves ORIGIN_MISMATCH alert if fails, returns boolean |
| `quickLocationCheck(String location)` | Converts location to lowercase, returns true if any known district or village name is found within the location string |
| `verifyCertificatePresence(String gemId)` | Traverses all stages using getAllStages(), checks each node for non-null certificateNumber, saves MISSING_CERTIFICATE alert if none found |
| `checkCurrentLocationConsistency(String gemId)` | Checks for suspicious location patterns — e.g. BUYING stage with mine location, MINING stage with airport location |
| `getVerificationStatusLabel(String gemId)` | Calls verifyGemOrigin and verifyCertificatePresence, returns a formatted status string |
| `runFullAuthentication(String gemId)` | Runs all three checks, prints detailed results to console, returns combined result |

---

## service/PriceTracker.java

| Function | What It Does |
|---|---|
| `PriceTracker(TrackingService trackingService)` | Constructor — stores reference to tracking service |
| `getPriceHistory(String gemId)` | Gets all stages, iterates them building PriceRecord list, calculates price increase and percentage increase for each stage compared to previous |
| `getTotalPriceAppreciation(String gemId)` | Gets gem list, returns tail price minus head price |
| `getTotalAppreciationPercentage(String gemId)` | Returns total appreciation divided by head price times 100 |
| `getHighestValueAddedStage(String gemId)` | Iterates all stages tracking which consecutive pair had the largest price difference |
| `PriceRecord.getStageNumber()` | Returns the stage sequence number |
| `PriceRecord.getStageName()` | Returns the stage label string |
| `PriceRecord.getLocation()` | Returns the stage location |
| `PriceRecord.getPersonName()` | Returns the person name at this stage |
| `PriceRecord.getDate()` | Returns the stage date as string |
| `PriceRecord.getPrice()` | Returns the price in rupees at this stage |
| `PriceRecord.getPriceIncrease()` | Returns the increase in price from the previous stage |
| `PriceRecord.getPercentIncrease()` | Returns the percentage increase from the previous stage |

---

## service/QRCodeService.java

| Function | What It Does |
|---|---|
| `QRCodeService(TrackingService trackingService)` | Constructor — stores reference to tracking service, creates qrcodes directory if it does not exist |
| `generateQRCode(String gemId)` | Calls buildQRContent(), uses ZXing QRCodeWriter to create BitMatrix, renders as BufferedImage, saves PNG to qrcodes folder, returns file path |
| `regenerateQRCode(String gemId)` | Deletes existing QR file if present, calls generateQRCode() to create fresh one |
| `qrCodeExists(String gemId)` | Checks if the PNG file at the expected path exists on disk |
| `getQRCodePath(String gemId)` | Returns the file path string for the gem's QR code PNG |
| `previewQRContent(String gemId)` | Calls buildQRContent() and prints the result to console |
| `buildQRContent(String gemId)` | Private — builds a formatted multi-line string containing gem ID, type, origin, miner, all stage details including location, person, date, weight, price, and verification status |

---

## report/ReportGenerator.java

| Function | What It Does |
|---|---|
| `ReportGenerator(TrackingService trackingService, OriginVerifier originVerifier, PriceTracker priceTracker)` | Constructor — stores service references, creates reports directory if it does not exist |
| `generateFullJourneyReport(String gemId)` | Builds complete text report with header, all stage details, weight analysis, price appreciation, and verification. Saves to reports folder with timestamp. Returns file path. |
| `generateSummaryReport(String gemId)` | Builds shorter report with gem overview and key statistics. Saves to reports folder. Returns file path. |
| `generateAllGemsReport()` | Iterates all gem IDs, builds a system-wide report covering all gems. Saves to reports folder. Returns file path. |
| `buildReportHeader(String gemId)` | Private — builds the top section with gem ID, type, origin, miner, dates, and a divider line |
| `buildStageSection(GemNode node, int index)` | Private — formats one stage node as text with all fields |
| `saveReportToFile(String content, String fileName)` | Private — writes the report string to a .txt file in the reports folder |

---

## api/ApiResponse.java

| Function | What It Does |
|---|---|
| `success(String message, Object data)` | Creates ApiResponse with success=true, status=200, the given message, and data |
| `success(String message)` | Creates ApiResponse with success=true, status=200, the given message, null data |
| `created(String message, Object data)` | Creates ApiResponse with success=true, status=201, the given message, and data |
| `error(String message)` | Creates ApiResponse with success=false, status=400, the given message |
| `error(String message, int statusCode)` | Creates ApiResponse with success=false and the given status code |
| `notFound(String resourceName, String id)` | Creates ApiResponse with success=false, status=404, and a not found message |
| `serverError(String message)` | Creates ApiResponse with success=false, status=500 |
| `badRequest(String message)` | Creates ApiResponse with success=false, status=400 |
| `toJson()` | Converts this ApiResponse to a pretty-printed JSON string using Gson |
| `toJson(Object object)` | Static — converts any Java object to JSON string |
| `fromJson(String json, Class clazz)` | Static — parses a JSON string into a Java object of the given class |
| `isSuccess()` | Returns the success boolean |
| `getMessage()` | Returns the message string |
| `getData()` | Returns the data object |
| `getTimestamp()` | Returns the Unix timestamp in milliseconds |
| `getStatusCode()` | Returns the HTTP status code |

---

## api/ApiServer.java

| Function | What It Does |
|---|---|
| `getInstance()` | Returns the single shared ApiServer instance using Singleton pattern |
| `start()` | Configures port 4567, configures thread pool, calls CorsFilter.apply(), calls router.registerRoutes(), calls awaitInitialization(), prints startup banner. Returns true on success. |
| `stop()` | Calls Spark.stop() to shut down the embedded Jetty server and release port 4567 |
| `isRunning()` | Returns true if the server has been started and not yet stopped |
| `getPort()` | Returns the port number 4567 |
| `getBaseUrl()` | Returns the string http://localhost:4567/api |
| `printStartupBanner()` | Private — prints a formatted ASCII banner showing status, port, base URL, and key endpoints |

---

## api/CorsFilter.java

| Function | What It Does |
|---|---|
| `apply()` | Registers a Spark before() filter that adds CORS headers to every response. Registers an options() handler that responds to browser preflight requests with 200 OK and echoes back the requested headers and methods. |

---

## api/ApiRouter.java

| Function | What It Does |
|---|---|
| `ApiRouter()` | Constructor — creates TrackingService, OriginVerifier, PriceTracker, QRCodeService, ReportGenerator instances and injects them into all seven handler classes |
| `registerRoutes()` | Calls all eight private registration methods, prints route table to console |
| `registerHealthRoutes()` | Registers GET /api/health with an inline lambda that returns server status JSON |
| `registerGemRoutes()` | Registers GET /api/gems, GET /api/gems/:id, GET /api/gems/search, GET /api/gems/ceylon, POST /api/gems, DELETE /api/gems/:id |
| `registerStageRoutes()` | Registers GET /api/gems/:id/stages, POST /api/gems/:id/stages, DELETE /api/gems/:id/stages/:position, PUT /api/gems/:id/stages/current/certificate, PUT /api/gems/:id/stages/current/export, PUT /api/gems/:id/stages/current/notes |
| `registerVerificationRoutes()` | Registers GET /api/gems/:id/verify, GET /api/gems/:id/verify/origin, GET /api/gems/:id/verify/certificate, GET /api/verify/all, GET /api/verify/locations, GET /api/gems/:id/risk |
| `registerAlertRoutes()` | Registers GET /api/alerts, GET /api/alerts/unresolved, GET /api/alerts/gem/:gemId, PUT /api/alerts/:id/resolve |
| `registerStatsRoutes()` | Registers GET /api/stats, GET /api/stats/summary, GET /api/gems/:id/price, GET /api/gems/:id/weight, GET /api/gems/compare |
| `registerQRRoutes()` | Registers GET /api/gems/:id/qr, POST /api/gems/:id/qr, PUT /api/gems/:id/qr, GET /api/gems/:id/qr/download, GET /api/gems/:id/qr/preview, GET /api/qr/status |
| `registerReportRoutes()` | Registers POST /api/gems/:id/report/full, POST /api/gems/:id/report/summary, POST /api/report/all, GET /api/reports |
| `registerErrorHandlers()` | Registers notFound handler returning 404 JSON and internalServerError handler returning 500 JSON |
| `printRouteTable()` | Private — prints quick reference URLs to the console |

---

## api/handlers/GemHandler.java

| Function | What It Does |
|---|---|
| `GemHandler(TrackingService trackingService, OriginVerifier originVerifier)` | Constructor |
| `getAllGems(Request request, Response response)` | Calls trackingService.getAllGemIds(), builds a summary map for each gem using buildGemSummary(), returns JSON list |
| `getGemById(Request request, Response response)` | Reads :id param, calls trackingService.getGemList(), builds full detail map using buildGemDetail(), returns JSON |
| `searchGems(Request request, Response response)` | Reads ?type or ?district query param, calls appropriate search method, returns JSON list |
| `getCeylonGems(Request request, Response response)` | Calls trackingService.getCeylonVerifiedGems(), builds summaries, returns JSON list |
| `registerGem(Request request, Response response)` | Parses JSON body, validates all required fields, calls trackingService.registerNewGem(), returns created gem ID |
| `deleteGem(Request request, Response response)` | Reads :id param, verifies gem exists, calls trackingService.deleteGem(), returns success message |
| `buildGemSummary(String gemId)` | Private — builds a HashMap with key gem summary fields for use in list responses |
| `buildGemDetail(GemLinkedList list)` | Private — builds a HashMap with full gem data including all stage history nodes |
| `validateGemRegistrationData(Map data)` | Private — checks all required fields are present and valid, returns error message or null |
| `getString(Map data, String key)` | Private — safely extracts a String from a parsed JSON map |
| `getDouble(Map data, String key)` | Private — safely extracts a double from a parsed JSON map |
| `isEmpty(Map data, String key)` | Private — returns true if a field is missing or empty |
| `parseDate(String dateString)` | Private — parses yyyy-MM-dd string to LocalDate, returns null if parsing fails |

---

## api/handlers/StageHandler.java

| Function | What It Does |
|---|---|
| `StageHandler(TrackingService trackingService)` | Constructor |
| `getAllStages(Request request, Response response)` | Gets all stages for a gem, builds stage map list, includes weight loss and price appreciation in response |
| `addStage(Request request, Response response)` | Parses JSON body, validates fields, calls trackingService.addStageToGem(), handles optional export and certificate fields, returns new stage data |
| `removeStage(Request request, Response response)` | Reads :position param, validates range, calls trackingService.removeStage(), returns confirmation |
| `addCertificate(Request request, Response response)` | Reads certificate number and authority from body, calls trackingService.addCertificateDetails() |
| `addExportDetails(Request request, Response response)` | Validates current stage is EXPORTING, reads export fields from body, calls trackingService.addExportDetails() |
| `addNotes(Request request, Response response)` | Reads notes from body, calls trackingService.addNoteToCurrentStage() |
| `buildStageMap(GemNode node, int index, int totalStages)` | Private — builds a HashMap representing one stage node for JSON serialization |
| `validateStageData(Map data)` | Private — validates all required fields, returns error message or null |

---

## api/handlers/VerificationHandler.java

| Function | What It Does |
|---|---|
| `VerificationHandler(TrackingService trackingService, OriginVerifier originVerifier)` | Constructor |
| `runFullAuthentication(Request request, Response response)` | Runs origin check, certificate check, and location check, returns all three results with overall status |
| `verifyOrigin(Request request, Response response)` | Runs only the origin location check, returns result with matched location if found |
| `verifyCertificate(Request request, Response response)` | Checks for certificate presence, returns certificate details if found |
| `verifyAllGems(Request request, Response response)` | Runs origin verification on every gem, returns pass and fail counts with per-gem results |
| `getValidLocations(Request request, Response response)` | Returns all valid Ceylon mining districts and villages from database |
| `getFraudRiskScore(Request request, Response response)` | Calculates five risk factors, sums the score, returns score with breakdown and recommendation |
| `detectUnusualPriceJump(GemLinkedList list)` | Private — returns true if any consecutive stage pair has more than 500 percent price increase |
| `detectSuspiciousLocation(GemLinkedList list)` | Private — returns true if any stage has a location that does not match the expected location type for that stage |
| `detectIncompleteStageData(GemLinkedList list)` | Private — returns true if any stage is missing person ID number or contact number |
| `buildCheckResult(String name, boolean passed, String passMsg, String failMsg)` | Private — builds a HashMap for one verification check result |
| `buildRiskFactor(String name, int points, boolean active, String description)` | Private — builds a HashMap for one risk factor row |

---

## api/handlers/AlertHandler.java

| Function | What It Does |
|---|---|
| `AlertHandler(TrackingService trackingService)` | Constructor — also gets DBConnection singleton |
| `getAllAlerts(Request request, Response response)` | Gets unresolved alerts, parses them, returns with count and summary |
| `getUnresolvedAlerts(Request request, Response response)` | Gets only unresolved alerts with type breakdown |
| `getAlertsByGem(Request request, Response response)` | Filters unresolved alerts for a specific gem ID |
| `resolveAlert(Request request, Response response)` | Reads alert ID, calls DBConnection.resolveAlert(), returns remaining unresolved count |
| `parseAlertStrings(List rawAlerts, boolean isResolved)` | Private — parses the raw alert strings from TrackingService into structured maps |
| `parseAlertString(String alertStr, boolean isResolved)` | Private — parses a single alert string splitting on pipe delimiters |
| `buildAlertSummary(int unresolvedCount)` | Private — builds a summary map with count and action message |
| `buildAlertTypeBreakdown(List alerts)` | Private — counts alerts by type for filter tab counts |
| `formatAlertTypeLabel(String alertType)` | Private — converts alert type code to human readable label |
| `getAlertTypeBadgeColor(String alertType)` | Private — returns badge background hex color for alert type |
| `getAlertTypeBadgeTextColor(String alertType)` | Private — returns badge text hex color for alert type |
| `getAlertSeverity(String alertType)` | Private — returns HIGH, MEDIUM, or LOW for the alert type |
| `getAlertRecommendation(String alertType)` | Private — returns an actionable recommendation message for the alert type |

---

## api/handlers/StatsHandler.java

| Function | What It Does |
|---|---|
| `StatsHandler(TrackingService trackingService, OriginVerifier originVerifier)` | Constructor — also creates PriceTracker instance |
| `getAllStats(Request request, Response response)` | Calculates total gems, Ceylon count, alert count, total stages, total value, gem type counts, district counts, trend indicators |
| `getDashboardSummary(Request request, Response response)` | Returns the four key numbers needed for dashboard stat cards |
| `getPriceHistory(Request request, Response response)` | Calls priceTracker.getPriceHistory(), converts to chart data points |
| `getWeightAnalysis(Request request, Response response)` | Gets all stages, calculates per-stage weight loss, returns breakdown |
| `compareGems(Request request, Response response)` | Reads gem1 and gem2 query params, gets both gem lists, builds comparison rows with winner indicators, builds chart data |
| `buildComparisonRow(String metric, String value1, String value2, boolean isComparable, boolean gem1Wins)` | Private — builds one row map for the comparison table |
| `buildComparisonChartData(GemLinkedList list1, GemLinkedList list2, String gemId1, String gemId2)` | Private — builds bar chart data aligning stage prices from both gems |
| `buildTrendIndicators(int totalGems, int ceylonGems, int alertCount, int totalStages)` | Private — builds trend text strings for stat card bottom rows |
| `extractDistrict(String location)` | Private — extracts the district portion from a location string by splitting on comma |
| `getStageShorLabel(String stageName)` | Private — converts full stage name to short chart label e.g. Mine, Cut, Trade |
| `sortMapByValue(Map map)` | Private — sorts a name-to-count map by count descending |
| `getTopKey(Map map)` | Private — returns the key with the highest value from a count map |

---

## api/handlers/QRHandler.java

| Function | What It Does |
|---|---|
| `QRHandler(QRCodeService qrCodeService)` | Constructor |
| `getQRStatus(Request request, Response response)` | Checks if QR exists, returns status, file size, and download URL |
| `generateQRCode(Request request, Response response)` | Checks QR does not already exist, calls qrCodeService.generateQRCode(), returns file details |
| `regenerateQRCode(Request request, Response response)` | Calls qrCodeService.regenerateQRCode(), returns new file details |
| `downloadQRCode(Request request, Response response)` | Sets Content-Type to image/png, reads PNG file, writes bytes directly to response output stream. Returns null because bytes are written directly. |
| `previewQRContent(Request request, Response response)` | Captures console output of qrCodeService.previewQRContent(), returns text content |
| `getAllQRStatus(Request request, Response response)` | Scans qrcodes folder for PNG files, returns list of all QR codes with details |

---

## api/handlers/ReportHandler.java

| Function | What It Does |
|---|---|
| `ReportHandler(TrackingService trackingService, ReportGenerator reportGenerator, PriceTracker priceTracker)` | Constructor |
| `generateFullReport(Request request, Response response)` | Calls reportGenerator.generateFullJourneyReport(), reads file content, returns file details and content |
| `generateSummaryReport(Request request, Response response)` | Calls reportGenerator.generateSummaryReport(), returns file details and content |
| `generateAllGemsReport(Request request, Response response)` | Calls reportGenerator.generateAllGemsReport(), returns preview of first 100 lines |
| `listSavedReports(Request request, Response response)` | Scans reports folder for .txt files, returns list sorted by last modified date |
| `readReportFile(String filePath)` | Private — reads a text file and returns content as string |
| `getReportPreview(String content, int maxLines)` | Private — returns the first N lines of a report string |
| `inferReportType(String fileName)` | Private — determines report type from file name pattern |
| `formatReportTypeLabel(String reportType)` | Private — converts report type code to human readable label |
| `extractGemIdFromFileName(String fileName)` | Private — extracts the gem ID prefix from a report file name |
| `buildSystemStatsSummary(List allIds)` | Private — builds quick stats for inclusion in system report response |

---

## ui/MainMenu.java

| Function | What It Does |
|---|---|
| `MainMenu()` | Constructor — creates TrackingService, OriginVerifier, PriceTracker, QRCodeService, GemForm, JourneyViewer instances |
| `start()` | Main loop — displays menu and reads user choice until exit is selected |
| `displayMainMenu()` | Prints all numbered menu options to console |
| `handleSearchAndView()` | Reads gem ID or search term, delegates to JourneyViewer |
| `handleStatistics()` | Gets counts from TrackingService, prints dashboard summary to console |
| `handleAlerts()` | Gets unresolved alerts from TrackingService, prints each alert |
| `handleBulkVerification()` | Iterates all gem IDs, calls OriginVerifier for each, prints results |

---

## ui/GemForm.java

| Function | What It Does |
|---|---|
| `GemForm(TrackingService trackingService)` | Constructor |
| `registerNewGem()` | Prompts for all registration fields, validates input, calls trackingService.registerNewGem() |
| `addStageToGem()` | Prompts for gem ID and stage details, calls trackingService.addStageToGem() |
| `addExportDetails()` | Prompts for flight, invoice, destination, calls trackingService.addExportDetails() |
| `addCertificateDetails()` | Prompts for certificate number and authority, calls trackingService.addCertificateDetails() |
| `readDouble(String prompt)` | Private — reads a double from console with validation |
| `readDate(String prompt)` | Private — reads a date string and parses to LocalDate with validation |

---

## ui/JourneyViewer.java

| Function | What It Does |
|---|---|
| `JourneyViewer(TrackingService trackingService, OriginVerifier originVerifier, PriceTracker priceTracker, QRCodeService qrCodeService)` | Constructor |
| `viewGemJourney()` | Prompts for gem ID, shows sub-menu for forward journey, backward journey, weight analysis, price appreciation, verification, QR code |
| `displayForwardJourney(String gemId)` | Calls trackingService.displayFullJourney() |
| `displayBackwardJourney(String gemId)` | Calls trackingService.displayReverseJourney() |
| `displayWeightAnalysis(String gemId)` | Calls trackingService.displayWeightAnalysis() |
| `displayPriceAppreciation(String gemId)` | Calls trackingService.displayPriceAppreciation() |
| `runVerification(String gemId)` | Calls originVerifier.runFullAuthentication() |
| `generateQRCode(String gemId)` | Calls qrCodeService.generateQRCode() and prints result path |
| `removeStage(String gemId)` | Prompts for position, calls trackingService.removeStage() |