package api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * ApiResponse is the standard response wrapper for all REST API endpoints.
 * Every endpoint in the system returns a JSON object in this exact format:
 *
 * {
 *   "success": true or false,
 *   "message": "human readable description",
 *   "data": the actual response payload or null,
 *   "timestamp": the time the response was generated
 * }
 *
 * Using a standard wrapper means the frontend always knows what to expect.
 * It never receives raw data directly — it always receives this envelope.
 * This makes error handling on the frontend consistent and predictable.
 *
 * Gson is used to convert this object to a JSON string because Java does
 * not have built-in JSON serialization. Gson handles nested objects,
 * lists, null values, and special characters automatically.
 */
public class ApiResponse {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * Indicates whether the API operation succeeded or failed.
     * The frontend checks this first before reading the data field.
     * True means the operation completed successfully.
     * False means something went wrong and the message explains why.
     */
    private boolean success;

    /**
     * A human readable description of what happened.
     * On success this is a short confirmation message.
     * On failure this is a clear explanation of what went wrong.
     * The frontend can display this message directly to the user.
     */
    private String message;

    /**
     * The actual response payload.
     * This can be any Java object — a gem, a list of gems,
     * a statistics map, a verification result, or null.
     * Gson will serialize whatever object is placed here into JSON.
     * If the operation has no data to return this field is null.
     */
    private Object data;

    /**
     * The Unix timestamp in milliseconds when this response was created.
     * The frontend can use this to show when data was last fetched.
     * Also useful for debugging to know exactly when a request was processed.
     */
    private long timestamp;

    /**
     * The HTTP status code that was set for this response.
     * Stored here so the frontend can read it from the JSON body
     * in addition to the HTTP header status code.
     */
    private int statusCode;

    // ---------------------------------------------------------
    // Shared Gson instance
    // ---------------------------------------------------------

    /**
     * A shared Gson instance configured to output pretty-printed JSON.
     * Pretty printing adds indentation and newlines to the JSON output
     * which makes it readable in the browser and during development.
     * Declared static so one instance is shared across all responses.
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    // ---------------------------------------------------------
    // Private constructor
    // ---------------------------------------------------------

    /**
     * Private constructor forces all response creation through
     * the static factory methods below.
     * This ensures every response is correctly formed and no
     * fields are accidentally left unset.
     *
     * @param success    whether the operation succeeded
     * @param message    human readable description
     * @param data       the response payload or null
     * @param statusCode the HTTP status code
     */
    private ApiResponse(boolean success, String message,
                        Object data, int statusCode) {
        this.success    = success;
        this.message    = message;
        this.data       = data;
        this.statusCode = statusCode;
        this.timestamp  = System.currentTimeMillis();
    }

    // ---------------------------------------------------------
    // Static factory methods
    // ---------------------------------------------------------

    /**
     * Creates a success response with data.
     * Used when an operation succeeds and there is data to return.
     * HTTP status code is 200 OK.
     *
     * Example usage:
     * ApiResponse.success("Gem retrieved successfully", gemData)
     *
     * @param message a short confirmation message
     * @param data    the payload to send to the frontend
     * @return a new ApiResponse with success true and status 200
     */
    public static ApiResponse success(String message, Object data) {
        return new ApiResponse(true, message, data, 200);
    }

    /**
     * Creates a success response without data.
     * Used when an operation succeeds but there is nothing to return.
     * For example after deleting a gem or resolving an alert.
     * HTTP status code is 200 OK.
     *
     * @param message a short confirmation message
     * @return a new ApiResponse with success true, null data, and status 200
     */
    public static ApiResponse success(String message) {
        return new ApiResponse(true, message, null, 200);
    }

    /**
     * Creates a success response for newly created resources.
     * Used when a gem is registered or a stage is added.
     * HTTP status code is 201 Created.
     *
     * @param message a short confirmation message
     * @param data    the newly created resource data
     * @return a new ApiResponse with success true and status 201
     */
    public static ApiResponse created(String message, Object data) {
        return new ApiResponse(true, message, data, 201);
    }

    /**
     * Creates an error response.
     * Used when an operation fails for any reason.
     * The message should clearly explain what went wrong.
     * HTTP status code is 400 Bad Request by default.
     *
     * @param message a clear explanation of what went wrong
     * @return a new ApiResponse with success false and status 400
     */
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null, 400);
    }

    /**
     * Creates an error response with a specific HTTP status code.
     * Used when a specific HTTP error code is needed.
     * For example 404 when a gem is not found, 500 for server errors.
     *
     * @param message    a clear explanation of what went wrong
     * @param statusCode the specific HTTP status code to use
     * @return a new ApiResponse with success false and the given status code
     */
    public static ApiResponse error(String message, int statusCode) {
        return new ApiResponse(false, message, null, statusCode);
    }

    /**
     * Creates a not found error response.
     * Used specifically when a requested gem or resource does not exist.
     * HTTP status code is 404 Not Found.
     *
     * @param resourceName the name of what was not found e.g. "Gem"
     * @param id           the ID that was searched for
     * @return a new ApiResponse with success false and status 404
     */
    public static ApiResponse notFound(String resourceName, String id) {
        return new ApiResponse(
                false,
                resourceName + " not found with ID: " + id,
                null,
                404
        );
    }

    /**
     * Creates a server error response.
     * Used when an unexpected exception occurs during processing.
     * HTTP status code is 500 Internal Server Error.
     *
     * @param message a description of what went wrong internally
     * @return a new ApiResponse with success false and status 500
     */
    public static ApiResponse serverError(String message) {
        return new ApiResponse(false, message, null, 500);
    }

    /**
     * Creates a bad request error response.
     * Used when the request is missing required fields or has invalid data.
     * HTTP status code is 400 Bad Request.
     *
     * @param message a description of what is wrong with the request
     * @return a new ApiResponse with success false and status 400
     */
    public static ApiResponse badRequest(String message) {
        return new ApiResponse(false, message, null, 400);
    }

    // ---------------------------------------------------------
    // JSON conversion
    // ---------------------------------------------------------

    /**
     * Converts this ApiResponse object to a JSON string.
     * This is called by every endpoint handler to produce the
     * final response body that is sent to the frontend.
     *
     * The output looks like this:
     * {
     *   "success": true,
     *   "message": "Gem retrieved successfully",
     *   "data": { ... },
     *   "timestamp": 1711234567890,
     *   "statusCode": 200
     * }
     *
     * @return a pretty-printed JSON string representing this response
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Static utility method to convert any Java object to a JSON string.
     * Used when handlers need to serialize custom objects outside of
     * the standard ApiResponse wrapper.
     *
     * @param object the Java object to convert to JSON
     * @return a JSON string representing the object
     */
    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Static utility method to parse a JSON string into a Java object.
     * Used by handlers to deserialize the request body sent by the frontend.
     *
     * @param json  the JSON string from the request body
     * @param clazz the Java class to deserialize into
     * @param <T>   the type parameter
     * @return a Java object of the given type parsed from the JSON string
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    // ---------------------------------------------------------
    // Getters
    // ---------------------------------------------------------

    /** Returns whether the operation succeeded. */
    public boolean isSuccess() { return success; }

    /** Returns the human readable message. */
    public String getMessage() { return message; }

    /** Returns the response data payload. */
    public Object getData() { return data; }

    /** Returns the Unix timestamp when this response was created. */
    public long getTimestamp() { return timestamp; }

    /** Returns the HTTP status code for this response. */
    public int getStatusCode() { return statusCode; }
}