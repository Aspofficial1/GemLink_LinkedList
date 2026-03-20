package api;

import api.handlers.AlertHandler;
import api.handlers.GemHandler;
import api.handlers.QRHandler;
import api.handlers.ReportHandler;
import api.handlers.StageHandler;
import api.handlers.StatsHandler;
import api.handlers.VerificationHandler;
import service.OriginVerifier;
import service.PriceTracker;
import service.QRCodeService;
import service.TrackingService;
import report.ReportGenerator;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.delete;
import static spark.Spark.path;
import static spark.Spark.notFound;
import static spark.Spark.internalServerError;

/**
 * ApiRouter is responsible for registering all REST API routes.
 * It maps every URL endpoint to its corresponding handler method.
 *
 * All routes follow REST conventions:
 * GET    /api/gems            — retrieve all or search
 * GET    /api/gems/:id        — retrieve one by ID
 * POST   /api/gems            — create a new resource
 * PUT    /api/gems/:id        — update an existing resource
 * DELETE /api/gems/:id        — delete a resource
 *
 * Every route is prefixed with /api to distinguish API endpoints
 * from any future static file serving or other server responses.
 *
 * All handler methods follow the same Spark signature:
 * (Request request, Response response) -> String
 * where the returned String is the JSON response body.
 */
public class ApiRouter {

    // ---------------------------------------------------------
    // Handler instances
    // ---------------------------------------------------------

    /**
     * Handler for all gem-related endpoints.
     * Handles registration, retrieval, search, and deletion of gems.
     */
    private GemHandler gemHandler;

    /**
     * Handler for all stage-related endpoints.
     * Handles adding stages, viewing stages, and removing stages.
     */
    private StageHandler stageHandler;

    /**
     * Handler for all origin verification endpoints.
     * Handles Ceylon verification, certificate checks, and bulk verification.
     */
    private VerificationHandler verificationHandler;

    /**
     * Handler for all fraud alert endpoints.
     * Handles retrieving, resolving, and filtering alerts.
     */
    private AlertHandler alertHandler;

    /**
     * Handler for all statistics and dashboard endpoints.
     * Handles gem counts, Ceylon rates, and system metrics.
     */
    private StatsHandler statsHandler;

    /**
     * Handler for all QR code endpoints.
     * Handles generating, regenerating, and downloading QR codes.
     */
    private QRHandler qrHandler;

    /**
     * Handler for all report generation endpoints.
     * Handles full journey reports and summary reports.
     */
    private ReportHandler reportHandler;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new ApiRouter and initialises all handler instances.
     * All service layer objects are created once here and injected
     * into each handler so they share the same service instances.
     * This prevents duplicate database connections and memory waste.
     */
    public ApiRouter() {
        // Create all service layer instances
        TrackingService trackingService = new TrackingService();
        OriginVerifier  originVerifier  = new OriginVerifier(trackingService);
        PriceTracker    priceTracker    = new PriceTracker(trackingService);
        QRCodeService   qrCodeService   = new QRCodeService(trackingService);
        ReportGenerator reportGenerator = new ReportGenerator(
                trackingService, originVerifier, priceTracker);

        // Inject services into each handler
        this.gemHandler          = new GemHandler(trackingService, originVerifier);
        this.stageHandler        = new StageHandler(trackingService);
        this.verificationHandler = new VerificationHandler(
                trackingService, originVerifier);
        this.alertHandler        = new AlertHandler(trackingService);
        this.statsHandler        = new StatsHandler(trackingService, originVerifier);
        this.qrHandler           = new QRHandler(qrCodeService);
        this.reportHandler       = new ReportHandler(
                trackingService, reportGenerator, priceTracker);
    }

    // ---------------------------------------------------------
    // Route registration
    // ---------------------------------------------------------

    /**
     * Registers all API routes with the Spark framework.
     * This method is called once during server startup after
     * the port and CORS filter have been configured.
     *
     * Routes are grouped by resource type for readability.
     * Each route maps an HTTP method and URL pattern to a handler method.
     */
    public void registerRoutes() {

        registerHealthRoutes();
        registerGemRoutes();
        registerStageRoutes();
        registerVerificationRoutes();
        registerAlertRoutes();
        registerStatsRoutes();
        registerQRRoutes();
        registerReportRoutes();
        registerErrorHandlers();

        System.out.println("💎 All API routes registered successfully.");
        printRouteTable();
    }

    // ---------------------------------------------------------
    // Health check routes
    // ---------------------------------------------------------

    /**
     * Registers the health check endpoint.
     * The frontend calls this on startup to confirm the API is running.
     * Returns a simple JSON object with status OK and the server timestamp.
     */
    private void registerHealthRoutes() {
        get("/api/health", (req, res) -> {
            res.status(200);
            return ApiResponse.success("Gem Origin Tracking API is running",
                    new java.util.HashMap<String, Object>() {{
                        put("status",    "OK");
                        put("version",   "1.0.0");
                        put("timestamp", System.currentTimeMillis());
                        put("database",  "SQLite Connected");
                    }}
            ).toJson();
        });

        System.out.println("  Registered: GET  /api/health");
    }

    // ---------------------------------------------------------
    // Gem routes
    // ---------------------------------------------------------

    /**
     * Registers all gem-related endpoints.
     *
     * GET  /api/gems              — returns all gem IDs with summaries
     * GET  /api/gems/:id          — returns full journey for one gem
     * GET  /api/gems/search       — search gems by type or district
     * GET  /api/gems/ceylon       — returns only Ceylon verified gems
     * POST /api/gems              — registers a new gem
     * DELETE /api/gems/:id        — deletes a gem and all its stages
     */
    private void registerGemRoutes() {
        // Get all gems
        get("/api/gems",
                gemHandler::getAllGems);

        // Get a specific gem by ID
        get("/api/gems/:id",
                gemHandler::getGemById);

        // Search gems by type or district
        // Query params: ?type=Blue+Sapphire or ?district=Ratnapura
        get("/api/gems/search",
                gemHandler::searchGems);

        // Get only Ceylon verified gems
        get("/api/gems/ceylon",
                gemHandler::getCeylonGems);

        // Register a new gem — body contains all mining details
        post("/api/gems",
                gemHandler::registerGem);

        // Delete a gem and all its stages
        delete("/api/gems/:id",
                gemHandler::deleteGem);

        System.out.println("  Registered: GET    /api/gems");
        System.out.println("  Registered: GET    /api/gems/:id");
        System.out.println("  Registered: GET    /api/gems/search");
        System.out.println("  Registered: GET    /api/gems/ceylon");
        System.out.println("  Registered: POST   /api/gems");
        System.out.println("  Registered: DELETE /api/gems/:id");
    }

    // ---------------------------------------------------------
    // Stage routes
    // ---------------------------------------------------------

    /**
     * Registers all stage-related endpoints.
     *
     * GET    /api/gems/:id/stages         — returns all stages for a gem
     * POST   /api/gems/:id/stages         — adds a new stage to a gem
     * DELETE /api/gems/:id/stages/:pos    — removes a stage at a position
     * PUT    /api/gems/:id/stages/current/certificate — adds certificate to current stage
     * PUT    /api/gems/:id/stages/current/export      — adds export details to current stage
     * PUT    /api/gems/:id/stages/current/notes       — adds notes to current stage
     */
    private void registerStageRoutes() {
        // Get all stages for a gem as an ordered list
        get("/api/gems/:id/stages",
                stageHandler::getAllStages);

        // Add a new stage to a gem journey
        post("/api/gems/:id/stages",
                stageHandler::addStage);

        // Remove a stage at a specific position
        delete("/api/gems/:id/stages/:position",
                stageHandler::removeStage);

        // Add certificate details to the current stage
        put("/api/gems/:id/stages/current/certificate",
                stageHandler::addCertificate);

        // Add export details to the current exporting stage
        put("/api/gems/:id/stages/current/export",
                stageHandler::addExportDetails);

        // Add notes to the current stage
        put("/api/gems/:id/stages/current/notes",
                stageHandler::addNotes);

        System.out.println("  Registered: GET    /api/gems/:id/stages");
        System.out.println("  Registered: POST   /api/gems/:id/stages");
        System.out.println("  Registered: DELETE /api/gems/:id/stages/:position");
        System.out.println("  Registered: PUT    /api/gems/:id/stages/current/certificate");
        System.out.println("  Registered: PUT    /api/gems/:id/stages/current/export");
        System.out.println("  Registered: PUT    /api/gems/:id/stages/current/notes");
    }

    // ---------------------------------------------------------
    // Verification routes
    // ---------------------------------------------------------

    /**
     * Registers all origin verification endpoints.
     *
     * GET  /api/gems/:id/verify          — runs full authentication
     * GET  /api/gems/:id/verify/origin   — checks origin location only
     * GET  /api/gems/:id/verify/certificate — checks certificate presence
     * GET  /api/verify/all               — verifies all gems in system
     * GET  /api/verify/locations         — returns valid Ceylon locations
     * GET  /api/gems/:id/risk            — returns fraud risk score
     */
    private void registerVerificationRoutes() {
        // Run full authentication on a gem
        get("/api/gems/:id/verify",
                verificationHandler::runFullAuthentication);

        // Check origin location only
        get("/api/gems/:id/verify/origin",
                verificationHandler::verifyOrigin);

        // Check certificate presence only
        get("/api/gems/:id/verify/certificate",
                verificationHandler::verifyCertificate);

        // Verify all gems in the system
        get("/api/verify/all",
                verificationHandler::verifyAllGems);

        // Get the list of valid Ceylon mining locations
        get("/api/verify/locations",
                verificationHandler::getValidLocations);

        // Get the fraud risk score for a gem
        get("/api/gems/:id/risk",
                verificationHandler::getFraudRiskScore);

        System.out.println("  Registered: GET    /api/gems/:id/verify");
        System.out.println("  Registered: GET    /api/gems/:id/verify/origin");
        System.out.println("  Registered: GET    /api/gems/:id/verify/certificate");
        System.out.println("  Registered: GET    /api/verify/all");
        System.out.println("  Registered: GET    /api/verify/locations");
        System.out.println("  Registered: GET    /api/gems/:id/risk");
    }

    // ---------------------------------------------------------
    // Alert routes
    // ---------------------------------------------------------

    /**
     * Registers all fraud alert endpoints.
     *
     * GET  /api/alerts              — returns all alerts
     * GET  /api/alerts/unresolved   — returns only unresolved alerts
     * GET  /api/alerts/:gemId       — returns alerts for a specific gem
     * PUT  /api/alerts/:id/resolve  — marks an alert as resolved
     */
    private void registerAlertRoutes() {
        // Get all alerts
        get("/api/alerts",
                alertHandler::getAllAlerts);

        // Get only unresolved alerts
        get("/api/alerts/unresolved",
                alertHandler::getUnresolvedAlerts);

        // Get alerts for a specific gem
        get("/api/alerts/gem/:gemId",
                alertHandler::getAlertsByGem);

        // Mark an alert as resolved
        put("/api/alerts/:id/resolve",
                alertHandler::resolveAlert);

        System.out.println("  Registered: GET    /api/alerts");
        System.out.println("  Registered: GET    /api/alerts/unresolved");
        System.out.println("  Registered: GET    /api/alerts/gem/:gemId");
        System.out.println("  Registered: PUT    /api/alerts/:id/resolve");
    }

    // ---------------------------------------------------------
    // Statistics routes
    // ---------------------------------------------------------

    /**
     * Registers all statistics and dashboard endpoints.
     *
     * GET  /api/stats              — returns all system statistics
     * GET  /api/stats/summary      — returns a brief dashboard summary
     * GET  /api/gems/:id/price     — returns price history for a gem
     * GET  /api/gems/:id/weight    — returns weight analysis for a gem
     * GET  /api/gems/compare       — compares two gems side by side
     */
    private void registerStatsRoutes() {
        // Get all system statistics for the dashboard
        get("/api/stats",
                statsHandler::getAllStats);

        // Get a brief summary for the dashboard header cards
        get("/api/stats/summary",
                statsHandler::getDashboardSummary);

        // Get price appreciation history for a gem
        get("/api/gems/:id/price",
                statsHandler::getPriceHistory);

        // Get weight analysis for a gem
        get("/api/gems/:id/weight",
                statsHandler::getWeightAnalysis);

        // Compare two gems side by side
        // Query params: ?gem1=BS-123&gem2=RB-456
        get("/api/gems/compare",
                statsHandler::compareGems);

        System.out.println("  Registered: GET    /api/stats");
        System.out.println("  Registered: GET    /api/stats/summary");
        System.out.println("  Registered: GET    /api/gems/:id/price");
        System.out.println("  Registered: GET    /api/gems/:id/weight");
        System.out.println("  Registered: GET    /api/gems/compare");
    }

    // ---------------------------------------------------------
    // QR code routes
    // ---------------------------------------------------------

    /**
     * Registers all QR code endpoints.
     *
     * GET  /api/gems/:id/qr          — checks if QR code exists
     * POST /api/gems/:id/qr          — generates a QR code for a gem
     * PUT  /api/gems/:id/qr          — regenerates the QR code
     * GET  /api/gems/:id/qr/download — returns the QR code image file
     * GET  /api/gems/:id/qr/preview  — returns the QR code text content
     * GET  /api/qr/status            — returns QR status for all gems
     */
    private void registerQRRoutes() {
        // Check if a QR code exists for a gem
        get("/api/gems/:id/qr",
                qrHandler::getQRStatus);

        // Generate a new QR code for a gem
        post("/api/gems/:id/qr",
                qrHandler::generateQRCode);

        // Regenerate the QR code for a gem
        put("/api/gems/:id/qr",
                qrHandler::regenerateQRCode);

        // Download the QR code image as a PNG file
        get("/api/gems/:id/qr/download",
                qrHandler::downloadQRCode);

        // Get the text content that is encoded in the QR code
        get("/api/gems/:id/qr/preview",
                qrHandler::previewQRContent);

        // Get QR code status for all gems
        get("/api/qr/status",
                qrHandler::getAllQRStatus);

        System.out.println("  Registered: GET    /api/gems/:id/qr");
        System.out.println("  Registered: POST   /api/gems/:id/qr");
        System.out.println("  Registered: PUT    /api/gems/:id/qr");
        System.out.println("  Registered: GET    /api/gems/:id/qr/download");
        System.out.println("  Registered: GET    /api/gems/:id/qr/preview");
        System.out.println("  Registered: GET    /api/qr/status");
    }

    // ---------------------------------------------------------
    // Report routes
    // ---------------------------------------------------------

    /**
     * Registers all report generation endpoints.
     *
     * POST /api/gems/:id/report/full    — generates a full journey report
     * POST /api/gems/:id/report/summary — generates a summary report
     * POST /api/report/all              — generates a system-wide report
     * GET  /api/reports                 — lists all saved report files
     */
    private void registerReportRoutes() {
        // Generate a full journey report for a gem
        post("/api/gems/:id/report/full",
                reportHandler::generateFullReport);

        // Generate a summary report for a gem
        post("/api/gems/:id/report/summary",
                reportHandler::generateSummaryReport);

        // Generate a full system report for all gems
        post("/api/report/all",
                reportHandler::generateAllGemsReport);

        // List all saved report files
        get("/api/reports",
                reportHandler::listSavedReports);

        System.out.println("  Registered: POST   /api/gems/:id/report/full");
        System.out.println("  Registered: POST   /api/gems/:id/report/summary");
        System.out.println("  Registered: POST   /api/report/all");
        System.out.println("  Registered: GET    /api/reports");
    }

    // ---------------------------------------------------------
    // Global error handlers
    // ---------------------------------------------------------

    /**
     * Registers global error handlers for 404 and 500 responses.
     * These catch any request that does not match a registered route
     * or any unexpected exception thrown inside a handler.
     * Returns a standard ApiResponse JSON instead of Spark's default HTML.
     */
    private void registerErrorHandlers() {
        // Handle requests to unknown routes
        notFound((req, res) -> {
            res.type("application/json");
            res.status(404);
            return ApiResponse.error(
                    "Endpoint not found: " + req.requestMethod()
                    + " " + req.pathInfo(), 404
            ).toJson();
        });

        // Handle unexpected server errors
        internalServerError((req, res) -> {
            res.type("application/json");
            res.status(500);
            return ApiResponse.serverError(
                    "An unexpected server error occurred. Please try again."
            ).toJson();
        });

        System.out.println("  Registered: 404 not found handler");
        System.out.println("  Registered: 500 internal server error handler");
    }

    // ---------------------------------------------------------
    // Route table printer
    // ---------------------------------------------------------

    /**
     * Prints a formatted table of all registered routes to the console.
     * This helps developers quickly see all available endpoints
     * when the server starts up without needing external documentation.
     */
    private void printRouteTable() {
        System.out.println();
        System.out.println("  API is available at: http://localhost:4567");
        System.out.println();
        System.out.println("  Quick reference:");
        System.out.println("  GET  http://localhost:4567/api/health");
        System.out.println("  GET  http://localhost:4567/api/gems");
        System.out.println("  GET  http://localhost:4567/api/stats");
        System.out.println("  GET  http://localhost:4567/api/alerts/unresolved");
        System.out.println();
    }
}