package api;

import static spark.Spark.before;
import static spark.Spark.options;

/**
 * CorsFilter configures Cross-Origin Resource Sharing headers for the API.
 *
 * CORS is a browser security mechanism that blocks web pages from making
 * requests to a different domain or port than the one that served the page.
 * Without this filter, the React frontend running on localhost:3000 would
 * be blocked from calling the Java API running on localhost:4567 because
 * they are on different ports, which counts as a different origin.
 *
 * This filter runs before every request and adds the necessary HTTP headers
 * that tell the browser it is safe to allow cross-origin requests.
 * The filter also handles OPTIONS preflight requests which browsers send
 * automatically before any POST, PUT, or DELETE request to check permissions.
 *
 * All origins are allowed so the frontend can be served from any port
 * or domain during development and production without configuration changes.
 */
public class CorsFilter {

    // ---------------------------------------------------------
    // CORS header constants
    // ---------------------------------------------------------

    /**
     * Tells the browser which origins are allowed to call this API.
     * The wildcard asterisk means all origins are permitted.
     * This allows the React frontend to call the API regardless of
     * which port or domain it is served from.
     */
    private static final String ALLOW_ORIGIN = "*";

    /**
     * Tells the browser which HTTP methods are allowed.
     * GET retrieves data, POST creates resources, PUT updates resources,
     * DELETE removes resources, OPTIONS is used for preflight checks,
     * and PATCH is used for partial updates.
     */
    private static final String ALLOW_METHODS =
            "GET, POST, PUT, DELETE, OPTIONS, PATCH";

    /**
     * Tells the browser which request headers the frontend is allowed to send.
     * Content-Type is needed for JSON request bodies.
     * Authorization is needed if token-based auth is added later.
     * Accept tells the server what response format the client wants.
     * X-Requested-With is sent by some frontend frameworks automatically.
     */
    private static final String ALLOW_HEADERS =
            "Content-Type, Authorization, Accept, X-Requested-With";

    /**
     * Tells the browser which response headers the frontend JavaScript
     * is allowed to read. By default browsers only expose a limited set
     * of headers to JavaScript, so this expands what the frontend can access.
     */
    private static final String EXPOSE_HEADERS =
            "Content-Type, Authorization";

    /**
     * Tells the browser how long in seconds it can cache the preflight
     * response before sending another OPTIONS request.
     * 86400 seconds is 24 hours, reducing unnecessary preflight requests.
     */
    private static final String MAX_AGE = "86400";

    // ---------------------------------------------------------
    // Filter application
    // ---------------------------------------------------------

    /**
     * Applies CORS headers to all responses and handles OPTIONS preflight.
     *
     * This method must be called once during server startup before any
     * routes are defined. It registers two Spark handlers:
     *
     * 1. A before() filter that runs before every request and adds
     *    the CORS headers to the response so the browser accepts it.
     *
     * 2. An options() handler that responds to preflight OPTIONS requests
     *    with a 200 OK status so the browser knows the actual request
     *    is permitted and proceeds to send it.
     *
     * The order matters — before() must be registered before routes so
     * CORS headers are present even on error responses.
     */
    public static void apply() {

        // Before filter runs before every single request including errors
        // It adds the CORS headers to every response that leaves the server
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin",  ALLOW_ORIGIN);
            response.header("Access-Control-Allow-Methods", ALLOW_METHODS);
            response.header("Access-Control-Allow-Headers", ALLOW_HEADERS);
            response.header("Access-Control-Expose-Headers", EXPOSE_HEADERS);
            response.header("Access-Control-Max-Age",       MAX_AGE);

            // Tell the browser that credentials such as cookies and
            // authorization headers can be included in cross-origin requests
            // This is needed if the frontend sends authentication tokens
            response.header("Access-Control-Allow-Credentials", "true");

            // Set the content type to JSON for all responses by default
            // Individual handlers can override this if they return other types
            // such as the QR code handler which returns image/png
            response.type("application/json");
        });

        // Options handler responds to all preflight requests with 200 OK
        // Browsers send an OPTIONS request before POST, PUT, DELETE to check
        // if the server allows the actual request from this origin
        // Without this handler the browser would block the actual request
        options("/*", (request, response) -> {

            // Read which headers and methods the browser is asking about
            String accessControlRequestHeaders =
                    request.headers("Access-Control-Request-Headers");
            String accessControlRequestMethod =
                    request.headers("Access-Control-Request-Method");

            // Echo back the requested headers and method as allowed
            // This tells the browser exactly what it asked about is permitted
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers",
                        accessControlRequestHeaders);
            }

            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods",
                        accessControlRequestMethod);
            }

            // Return 200 OK with the word OK as the body
            // This is the standard response for a successful OPTIONS preflight
            return "OK";
        });

        System.out.println("💎 CORS filter applied — all origins permitted.");
    }
}