package api;

import static spark.Spark.port;
import static spark.Spark.awaitInitialization;
import static spark.Spark.stop;
import static spark.Spark.staticFiles;

/**
 * ApiServer is responsible for starting and stopping the HTTP server.
 * It configures the Spark framework with the correct port, thread pool
 * size, static file serving, CORS headers, and all API routes.
 *
 * Spark is used as the HTTP server because it is extremely lightweight,
 * requires zero XML configuration, and starts up in under one second.
 * It runs on an embedded Jetty server internally which is production grade.
 *
 * The server starts on port 4567 by default which is Spark's standard port.
 * The React frontend calls this server at http://localhost:4567/api
 *
 * This class follows the Singleton pattern so only one server instance
 * can ever be running at a time, preventing port conflicts.
 */
public class ApiServer {

    // ---------------------------------------------------------
    // Constants
    // ---------------------------------------------------------

    /**
     * The port the API server listens on.
     * 4567 is Spark's default port and is used here for consistency.
     * The React frontend must be configured to call this port.
     * If port 4567 is already in use on your machine change this value.
     */
    private static final int PORT = 4567;

    /**
     * The maximum number of threads the server uses to handle requests.
     * 8 threads is sufficient for a development and demo environment.
     * Each thread handles one HTTP request at a time.
     */
    private static final int MAX_THREADS = 8;

    /**
     * The minimum number of threads kept alive waiting for requests.
     * Keeping 2 threads alive avoids the overhead of creating new threads
     * when requests arrive after a quiet period.
     */
    private static final int MIN_THREADS = 2;

    /**
     * How long in milliseconds an idle thread waits before being destroyed.
     * 30000 milliseconds is 30 seconds.
     */
    private static final int IDLE_TIMEOUT = 30000;

    // ---------------------------------------------------------
    // Singleton instance
    // ---------------------------------------------------------

    /**
     * The single shared instance of ApiServer.
     * Created once and reused for the lifetime of the application.
     */
    private static ApiServer instance;

    /**
     * Tracks whether the server is currently running.
     * Prevents calling start twice or stop before starting.
     */
    private boolean running;

    /**
     * The router that registers all API routes.
     * Created once when the server starts.
     */
    private ApiRouter router;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Private constructor prevents external instantiation.
     * Use getInstance() to get the shared server instance.
     */
    private ApiServer() {
        this.running = false;
        this.router  = new ApiRouter();
    }

    // ---------------------------------------------------------
    // Singleton access
    // ---------------------------------------------------------

    /**
     * Returns the single shared instance of ApiServer.
     * Creates it on the first call and returns the same instance
     * on every subsequent call.
     *
     * @return the shared ApiServer instance
     */
    public static ApiServer getInstance() {
        if (instance == null) {
            instance = new ApiServer();
        }
        return instance;
    }

    // ---------------------------------------------------------
    // Server lifecycle
    // ---------------------------------------------------------

    /**
     * Starts the HTTP server and registers all routes.
     * This method must be called once to make the API available.
     *
     * The startup sequence is:
     * 1. Configure the port
     * 2. Configure the thread pool
     * 3. Apply the CORS filter so the frontend can connect
     * 4. Register all API routes
     * 5. Wait for Spark to finish initialising
     * 6. Print the startup confirmation message
     *
     * awaitInitialization() blocks until Spark has bound the port
     * and is ready to accept requests. Without this call the server
     * might not be ready when the first request arrives.
     *
     * @return true if the server started successfully, false otherwise
     */
    public boolean start() {
        if (running) {
            System.out.println("💎 API server is already running on port " + PORT);
            return false;
        }

        try {
            System.out.println();
            System.out.println("  Starting Gem Origin Tracking API Server...");
            System.out.println();

            // Step 1 — Configure the port before anything else
            // Port must be set before any routes are registered
            port(PORT);

            // Step 2 — Configure the thread pool for handling requests
            // threadPool(maxThreads, minThreads, idleTimeoutMillis)
            spark.Spark.threadPool(MAX_THREADS, MIN_THREADS, IDLE_TIMEOUT);

            // Step 3 — Apply CORS headers before routes are registered
            // CORS filter must run before routes so all responses get headers
            CorsFilter.apply();

            // Step 4 — Register all API routes
            router.registerRoutes();

            // Step 5 — Wait for Spark to finish binding the port
            // This ensures the server is ready before returning
            awaitInitialization();

            // Step 6 — Mark as running and print confirmation
            running = true;
            printStartupBanner();

            return true;

        } catch (Exception e) {
            System.out.println();
            System.out.println("  ERROR: Failed to start API server.");
            System.out.println("  Reason: " + e.getMessage());
            System.out.println("  Check that port " + PORT
                    + " is not already in use by another application.");
            System.out.println();
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Stops the HTTP server gracefully.
     * This method should be called when the application exits
     * to release the port and clean up Spark's thread pool.
     *
     * Calling stop() allows the port to be reused immediately
     * after the server stops, which is important during development
     * when the server is frequently restarted.
     */
    public void stop() {
        if (!running) {
            System.out.println("  API server is not running.");
            return;
        }

        try {
            System.out.println();
            System.out.println("  Stopping API server...");
            spark.Spark.stop();
            running = false;
            System.out.println("  API server stopped successfully.");
            System.out.println();
        } catch (Exception e) {
            System.out.println("  Warning: Error while stopping API server.");
            e.printStackTrace();
        }
    }

    /**
     * Returns whether the server is currently running.
     *
     * @return true if the server is running and accepting requests
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the port number the server is listening on.
     *
     * @return the server port number
     */
    public int getPort() {
        return PORT;
    }

    /**
     * Returns the base URL of the API including the port.
     * The frontend uses this URL as the base for all API calls.
     *
     * @return the full base URL string
     */
    public String getBaseUrl() {
        return "http://localhost:" + PORT + "/api";
    }

    // ---------------------------------------------------------
    // Startup banner
    // ---------------------------------------------------------

    /**
     * Prints a formatted startup banner to the console.
     * Shows all the key information a developer needs after startup:
     * the base URL, port, key endpoints, and how to test the API.
     * This makes development much faster by having everything visible
     * immediately after the server starts.
     */
    private void printStartupBanner() {
        System.out.println();
        System.out.println("  +---------------------------------------------------------+");
        System.out.println("  |                                                         |");
        System.out.println("  |        GEM ORIGIN TRACKING SYSTEM - REST API           |");
        System.out.println("  |                                                         |");
        System.out.println("  +---------------------------------------------------------+");
        System.out.println("  |                                                         |");
        System.out.println("  |  Status   : RUNNING                                    |");
        System.out.println("  |  Port     : " + PORT
                + "                                       |");
        System.out.println("  |  Base URL : http://localhost:" + PORT + "/api            |");
        System.out.println("  |                                                         |");
        System.out.println("  +---------------------------------------------------------+");
        System.out.println("  |                                                         |");
        System.out.println("  |  Key Endpoints:                                         |");
        System.out.println("  |                                                         |");
        System.out.println("  |  GET  /api/health          API health check            |");
        System.out.println("  |  GET  /api/gems            All gems                    |");
        System.out.println("  |  POST /api/gems            Register new gem            |");
        System.out.println("  |  GET  /api/gems/:id        Gem journey                 |");
        System.out.println("  |  GET  /api/stats           Dashboard stats             |");
        System.out.println("  |  GET  /api/alerts          All alerts                  |");
        System.out.println("  |  GET  /api/gems/:id/verify Verify origin               |");
        System.out.println("  |  POST /api/gems/:id/qr     Generate QR code            |");
        System.out.println("  |                                                         |");
        System.out.println("  +---------------------------------------------------------+");
        System.out.println("  |                                                         |");
        System.out.println("  |  Test the API:                                          |");
        System.out.println("  |  Open your browser and go to:                           |");
        System.out.println("  |  http://localhost:" + PORT + "/api/health               |");
        System.out.println("  |                                                         |");
        System.out.println("  |  CLI mode is still available — use menu option 0       |");
        System.out.println("  |  to exit or Ctrl+C to stop the server                  |");
        System.out.println("  |                                                         |");
        System.out.println("  +---------------------------------------------------------+");
        System.out.println();
    }
}