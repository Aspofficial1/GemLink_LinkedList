package api;

import api.handlers.AlertHandler;
import api.handlers.AuditHandler;
import api.handlers.GemHandler;
import api.handlers.JourneyMapHandler;
import api.handlers.PriceEstimatorHandler;
import api.handlers.QRHandler;
import api.handlers.ReportHandler;
import api.handlers.StageHandler;
import api.handlers.StatsHandler;
import api.handlers.VerificationHandler;
import service.AuditService;
import service.JourneyMapService;
import service.OriginVerifier;
import service.PriceEstimator;
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
 * CRITICAL RULE — Spark matches routes in the exact order they are
 * registered. Static routes like /api/gems/search, /api/gems/ceylon,
 * and /api/gems/compare MUST be registered BEFORE /api/gems/:id.
 * If /:id is registered first, Spark captures "search", "ceylon",
 * and "compare" as gem IDs and routes them to getGemById().
 *
 * To guarantee correct order, ALL /api/gems/* GET routes are
 * registered together in registerAllGemRoutes() in the correct
 * sequence — static routes first, dynamic /:id last.
 *
 * NEW — AuditService is now shared across GemHandler, StageHandler,
 * and AuditHandler so every change is recorded in one audit log.
 * NEW — PUT /api/gems/:id/stages/:position registered for stage update.
 */
public class ApiRouter {

    // ---------------------------------------------------------
    // Original handlers
    // ---------------------------------------------------------
    private GemHandler          gemHandler;
    private StageHandler        stageHandler;
    private VerificationHandler verificationHandler;
    private AlertHandler        alertHandler;
    private StatsHandler        statsHandler;
    private QRHandler           qrHandler;
    private ReportHandler       reportHandler;

    // ---------------------------------------------------------
    // New feature handlers
    // ---------------------------------------------------------

    /**
     * Handler for Audit Trail endpoints.
     * Feature 1 — tracks every change made to any gem or stage.
     */
    private AuditHandler auditHandler;

    /**
     * Handler for Price Estimator endpoints.
     * Feature 2 — estimates market value based on linked list data.
     */
    private PriceEstimatorHandler priceEstimatorHandler;

    /**
     * Handler for Journey Map endpoints.
     * Feature 3 — converts linked list nodes into GPS map pins.
     */
    private JourneyMapHandler journeyMapHandler;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new ApiRouter and initialises all handler instances.
     * All service layer objects are created once here and injected
     * into each handler so they share the same service instances.
     *
     * AuditService is created once and shared across GemHandler,
     * StageHandler, and AuditHandler so all changes are recorded
     * in the same audit log using the same service instance.
     */
    public ApiRouter() {
        // ---------------------------------------------------------
        // Original services — unchanged
        // ---------------------------------------------------------
        TrackingService trackingService = new TrackingService();
        OriginVerifier  originVerifier  = new OriginVerifier(trackingService);
        PriceTracker    priceTracker    = new PriceTracker(trackingService);
        QRCodeService   qrCodeService   = new QRCodeService(trackingService);
        ReportGenerator reportGenerator = new ReportGenerator(
                trackingService, originVerifier, priceTracker);

        // ---------------------------------------------------------
        // AuditService — created ONCE and shared across handlers
        // GemHandler, StageHandler, and AuditHandler all use the
        // same instance so every change goes to the same audit log
        // ---------------------------------------------------------
        AuditService auditService = new AuditService(trackingService);

        // ---------------------------------------------------------
        // Updated handlers — GemHandler and StageHandler now receive
        // auditService so they log all changes automatically
        // ---------------------------------------------------------
        this.gemHandler          = new GemHandler(trackingService, originVerifier, auditService);
        this.stageHandler        = new StageHandler(trackingService, auditService);
        this.verificationHandler = new VerificationHandler(trackingService, originVerifier);
        this.alertHandler        = new AlertHandler(trackingService);
        this.statsHandler        = new StatsHandler(trackingService, originVerifier);
        this.qrHandler           = new QRHandler(qrCodeService);
        this.reportHandler       = new ReportHandler(trackingService, reportGenerator, priceTracker);

        // ---------------------------------------------------------
        // New feature handlers
        // ---------------------------------------------------------

        // Feature 1 — Audit Trail — uses the SAME auditService instance
        this.auditHandler = new AuditHandler(auditService, trackingService);

        // Feature 2 — Price Estimator
        PriceEstimator priceEstimator = new PriceEstimator(trackingService, originVerifier);
        this.priceEstimatorHandler = new PriceEstimatorHandler(priceEstimator, trackingService);

        // Feature 3 — Journey Map
        JourneyMapService journeyMapService = new JourneyMapService(trackingService, originVerifier);
        this.journeyMapHandler = new JourneyMapHandler(journeyMapService, trackingService);
    }

    // ---------------------------------------------------------
    // Route registration
    // ---------------------------------------------------------

    /**
     * Registers all API routes with the Spark framework.
     * Called once during server startup after port and CORS are configured.
     */
    public void registerRoutes() {
        registerHealthRoutes();
        registerAllGemRoutes();         // ALL /api/gems/* in one method — order guaranteed
        registerStageRoutes();
        registerVerificationRoutes();
        registerAlertRoutes();
        registerStatsRoutes();
        registerQRRoutes();
        registerReportRoutes();
        registerAuditRoutes();          // Feature 1 — Audit Trail
        registerPriceEstimatorRoutes(); // Feature 2 — Price Estimator
        registerJourneyMapRoutes();     // Feature 3 — Journey Map
        registerErrorHandlers();

        System.out.println("💎 All API routes registered successfully.");
        printRouteTable();
    }

    // ---------------------------------------------------------
    // Health
    // ---------------------------------------------------------

    private void registerHealthRoutes() {
        get("/api/health", (req, res) -> {
            res.status(200);
            return ApiResponse.success("Gem Origin Tracking API is running",
                    new java.util.HashMap<String, Object>() {{
                        put("status",    "OK");
                        put("version",   "2.0.0");
                        put("timestamp", System.currentTimeMillis());
                        put("database",  "SQLite Connected");
                        put("features",  "Audit Trail, Price Estimator, Journey Map");
                    }}
            ).toJson();
        });
        System.out.println("  Registered: GET  /api/health");
    }

    // ---------------------------------------------------------
    // ALL /api/gems/* routes — guaranteed correct order
    // Static routes registered BEFORE dynamic /:id
    // ---------------------------------------------------------

    /**
     * Registers ALL /api/gems/* GET routes in a single method.
     *
     * ORDER IS CRITICAL:
     *   Step 1 — /api/gems          exact match, no parameter
     *   Step 2 — /api/gems/search   static, BEFORE /:id
     *   Step 3 — /api/gems/ceylon   static, BEFORE /:id
     *   Step 4 — /api/gems/compare  static, BEFORE /:id
     *   Step 5 — /api/gems/:id      dynamic, registered LAST
     *
     * If /api/gems/:id is registered before any of steps 2-4,
     * Spark will route "search", "ceylon", and "compare" to
     * getGemById() instead of their correct handlers.
     */
    private void registerAllGemRoutes() {

        // Step 1 — exact match, no conflict possible
        get("/api/gems",
                gemHandler::getAllGems);

        // Step 2 — static path, MUST be before /:id
        get("/api/gems/search",
                gemHandler::searchGems);

        // Step 3 — static path, MUST be before /:id
        get("/api/gems/ceylon",
                gemHandler::getCeylonGems);

        // Step 4 — static path, MUST be before /:id
        // compareGems reads ?gem1=...&gem2=... query params
        get("/api/gems/compare",
                statsHandler::compareGems);

        // Step 5 — dynamic path, registered AFTER all static paths
        get("/api/gems/:id",
                gemHandler::getGemById);

        // POST and DELETE — method is different so no conflict with GET /:id
        post("/api/gems",
                gemHandler::registerGem);
        delete("/api/gems/:id",
                gemHandler::deleteGem);

        System.out.println("  Registered: GET    /api/gems");
        System.out.println("  Registered: GET    /api/gems/search");
        System.out.println("  Registered: GET    /api/gems/ceylon");
        System.out.println("  Registered: GET    /api/gems/compare");
        System.out.println("  Registered: GET    /api/gems/:id");
        System.out.println("  Registered: POST   /api/gems");
        System.out.println("  Registered: DELETE /api/gems/:id");
    }

    // ---------------------------------------------------------
    // Stages
    // ---------------------------------------------------------

    /**
     * Registers all stage-related endpoints.
     *
     * NEW — PUT /api/gems/:id/stages/:position added for stage update.
     * This must be registered BEFORE the current/certificate route
     * to avoid Spark matching "current" as a position parameter.
     */
    private void registerStageRoutes() {
        get("/api/gems/:id/stages",
                stageHandler::getAllStages);

        post("/api/gems/:id/stages",
                stageHandler::addStage);

        // Update stage at position — NEW endpoint for edit/update feature
        // Registered BEFORE current/* routes — no conflict because
        // "current" is a static segment and :position is dynamic
        put("/api/gems/:id/stages/:position",
                stageHandler::updateStage);

        delete("/api/gems/:id/stages/:position",
                stageHandler::removeStage);

        // current/* routes — static "current" segment registered after
        // /:position so Spark handles them correctly
        put("/api/gems/:id/stages/current/certificate",
                stageHandler::addCertificate);
        put("/api/gems/:id/stages/current/export",
                stageHandler::addExportDetails);
        put("/api/gems/:id/stages/current/notes",
                stageHandler::addNotes);

        System.out.println("  Registered: GET    /api/gems/:id/stages");
        System.out.println("  Registered: POST   /api/gems/:id/stages");
        System.out.println("  Registered: PUT    /api/gems/:id/stages/:position");
        System.out.println("  Registered: DELETE /api/gems/:id/stages/:position");
        System.out.println("  Registered: PUT    /api/gems/:id/stages/current/certificate");
        System.out.println("  Registered: PUT    /api/gems/:id/stages/current/export");
        System.out.println("  Registered: PUT    /api/gems/:id/stages/current/notes");
    }

    // ---------------------------------------------------------
    // Verification
    // ---------------------------------------------------------

    private void registerVerificationRoutes() {
        get("/api/gems/:id/verify",
                verificationHandler::runFullAuthentication);
        get("/api/gems/:id/verify/origin",
                verificationHandler::verifyOrigin);
        get("/api/gems/:id/verify/certificate",
                verificationHandler::verifyCertificate);
        get("/api/verify/all",
                verificationHandler::verifyAllGems);
        get("/api/verify/locations",
                verificationHandler::getValidLocations);
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
    // Alerts
    // ---------------------------------------------------------

    private void registerAlertRoutes() {
        get("/api/alerts",
                alertHandler::getAllAlerts);
        get("/api/alerts/unresolved",
                alertHandler::getUnresolvedAlerts);
        get("/api/alerts/gem/:gemId",
                alertHandler::getAlertsByGem);
        put("/api/alerts/:id/resolve",
                alertHandler::resolveAlert);

        System.out.println("  Registered: GET    /api/alerts");
        System.out.println("  Registered: GET    /api/alerts/unresolved");
        System.out.println("  Registered: GET    /api/alerts/gem/:gemId");
        System.out.println("  Registered: PUT    /api/alerts/:id/resolve");
    }

    // ---------------------------------------------------------
    // Statistics
    // NOTE: /api/gems/compare is registered in registerAllGemRoutes()
    // to guarantee it comes before /api/gems/:id
    // ---------------------------------------------------------

    private void registerStatsRoutes() {
        get("/api/stats",
                statsHandler::getAllStats);
        get("/api/stats/summary",
                statsHandler::getDashboardSummary);
        get("/api/gems/:id/price",
                statsHandler::getPriceHistory);
        get("/api/gems/:id/weight",
                statsHandler::getWeightAnalysis);

        System.out.println("  Registered: GET    /api/stats");
        System.out.println("  Registered: GET    /api/stats/summary");
        System.out.println("  Registered: GET    /api/gems/:id/price");
        System.out.println("  Registered: GET    /api/gems/:id/weight");
        System.out.println("  Registered: GET    /api/gems/compare (registered in gem routes)");
    }

    // ---------------------------------------------------------
    // QR Codes
    // ---------------------------------------------------------

    private void registerQRRoutes() {
        get("/api/gems/:id/qr",
                qrHandler::getQRStatus);
        post("/api/gems/:id/qr",
                qrHandler::generateQRCode);
        put("/api/gems/:id/qr",
                qrHandler::regenerateQRCode);
        get("/api/gems/:id/qr/download",
                qrHandler::downloadQRCode);
        get("/api/gems/:id/qr/preview",
                qrHandler::previewQRContent);
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
    // Reports
    // ---------------------------------------------------------

    private void registerReportRoutes() {
        post("/api/gems/:id/report/full",
                reportHandler::generateFullReport);
        post("/api/gems/:id/report/summary",
                reportHandler::generateSummaryReport);
        post("/api/report/all",
                reportHandler::generateAllGemsReport);
        get("/api/reports",
                reportHandler::listSavedReports);

        System.out.println("  Registered: POST   /api/gems/:id/report/full");
        System.out.println("  Registered: POST   /api/gems/:id/report/summary");
        System.out.println("  Registered: POST   /api/report/all");
        System.out.println("  Registered: GET    /api/reports");
    }

    // ---------------------------------------------------------
    // Feature 1 — Audit Trail routes
    // All change history for gems and stages
    // ---------------------------------------------------------

    /**
     * Registers all audit trail endpoints.
     *
     * GET /api/audit                         — all audit logs
     * GET /api/audit/summary                 — action type counts
     * GET /api/audit/recent                  — most recent N logs
     * GET /api/audit/gem/:gemId              — logs for one gem
     * GET /api/audit/action/:action          — logs by action type
     */
    private void registerAuditRoutes() {
        // IMPORTANT — static routes before dynamic /:gemId
        get("/api/audit/summary",
                auditHandler::getAuditSummary);
        get("/api/audit/recent",
                auditHandler::getRecentAuditLogs);
        get("/api/audit/action/:action",
                auditHandler::getAuditLogsByAction);
        get("/api/audit/gem/:gemId",
                auditHandler::getAuditLogsForGem);
        get("/api/audit",
                auditHandler::getAllAuditLogs);

        System.out.println("  Registered: GET    /api/audit");
        System.out.println("  Registered: GET    /api/audit/summary");
        System.out.println("  Registered: GET    /api/audit/recent");
        System.out.println("  Registered: GET    /api/audit/gem/:gemId");
        System.out.println("  Registered: GET    /api/audit/action/:action");
    }

    // ---------------------------------------------------------
    // Feature 2 — Price Estimator routes
    // Market value estimation based on linked list data
    // ---------------------------------------------------------

    /**
     * Registers all price estimator endpoints.
     *
     * GET /api/estimate/overview              — portfolio market overview
     * GET /api/estimate/all                   — estimates for all gems
     * GET /api/estimate/compare               — compare two gem estimates
     * GET /api/estimate/:gemId/summary        — brief estimate for one gem
     * GET /api/estimate/:gemId                — full estimate for one gem
     */
    private void registerPriceEstimatorRoutes() {
        // IMPORTANT — static routes before dynamic /:gemId
        get("/api/estimate/overview",
                priceEstimatorHandler::getMarketOverview);
        get("/api/estimate/all",
                priceEstimatorHandler::getAllEstimates);
        get("/api/estimate/compare",
                priceEstimatorHandler::compareEstimates);
        get("/api/estimate/:gemId/summary",
                priceEstimatorHandler::getEstimateSummaryForGem);
        get("/api/estimate/:gemId",
                priceEstimatorHandler::getEstimateForGem);

        System.out.println("  Registered: GET    /api/estimate/overview");
        System.out.println("  Registered: GET    /api/estimate/all");
        System.out.println("  Registered: GET    /api/estimate/compare");
        System.out.println("  Registered: GET    /api/estimate/:gemId/summary");
        System.out.println("  Registered: GET    /api/estimate/:gemId");
    }

    // ---------------------------------------------------------
    // Feature 3 — Journey Map routes
    // GPS coordinate map data from linked list traversal
    // ---------------------------------------------------------

    /**
     * Registers all journey map endpoints.
     *
     * GET /api/map/overview                   — all gems origin pins
     * GET /api/map/locations                  — known Sri Lankan locations
     * GET /api/map/:gemId/pins                — pin list for one gem
     * GET /api/map/:gemId/route               — route coordinates for one gem
     * GET /api/map/:gemId/stats               — route statistics for one gem
     * GET /api/map/:gemId                     — full map data for one gem
     */
    private void registerJourneyMapRoutes() {
        // IMPORTANT — static routes before dynamic /:gemId
        get("/api/map/overview",
                journeyMapHandler::getAllGemsMapOverview);
        get("/api/map/locations",
                journeyMapHandler::getKnownLocations);
        get("/api/map/:gemId/pins",
                journeyMapHandler::getJourneyPins);
        get("/api/map/:gemId/route",
                journeyMapHandler::getJourneyRoute);
        get("/api/map/:gemId/stats",
                journeyMapHandler::getJourneyStats);
        get("/api/map/:gemId",
                journeyMapHandler::getJourneyMapData);

        System.out.println("  Registered: GET    /api/map/overview");
        System.out.println("  Registered: GET    /api/map/locations");
        System.out.println("  Registered: GET    /api/map/:gemId/pins");
        System.out.println("  Registered: GET    /api/map/:gemId/route");
        System.out.println("  Registered: GET    /api/map/:gemId/stats");
        System.out.println("  Registered: GET    /api/map/:gemId");
    }

    // ---------------------------------------------------------
    // Error handlers
    // ---------------------------------------------------------

    private void registerErrorHandlers() {
        notFound((req, res) -> {
            res.type("application/json");
            res.status(404);
            return ApiResponse.error(
                    "Endpoint not found: " + req.requestMethod()
                    + " " + req.pathInfo(), 404
            ).toJson();
        });
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

    private void printRouteTable() {
        System.out.println();
        System.out.println("  API is available at: http://localhost:4567");
        System.out.println();
        System.out.println("  Quick reference:");
        System.out.println("  GET  http://localhost:4567/api/health");
        System.out.println("  GET  http://localhost:4567/api/gems");
        System.out.println("  GET  http://localhost:4567/api/stats");
        System.out.println("  GET  http://localhost:4567/api/alerts/unresolved");
        System.out.println("  GET  http://localhost:4567/api/audit");
        System.out.println("  GET  http://localhost:4567/api/estimate/overview");
        System.out.println("  GET  http://localhost:4567/api/map/overview");
        System.out.println();
    }
}