# Java Stack — Ceylon Gem Origin Tracking System

## Core Java Version

| Item | Detail |
|---|---|
| JDK Version | Java 24.0.1 |
| JVM | OpenJDK 64-Bit Server VM |
| Language Level | Java 11+ features used |
| Execution | Command line via java.exe with classpath argument file |

---

## Java Standard Library Usage

### java.util

| Class | Used In | Purpose |
|---|---|---|
| `HashMap<String, GemLinkedList>` | TrackingService | In-memory cache of all loaded gem linked lists keyed by gem ID |
| `HashSet<String>` | OriginVerifier | Stores known Ceylon mining districts and villages for O(1) lookup |
| `ArrayList<GemNode>` | GemLinkedList | Used in getAllStages() to build ordered list of nodes |
| `ArrayList<String>` | DBConnection | Used when returning lists of gem IDs |
| `ArrayList<Map<String, Object>>` | All handler classes | Used to build JSON-serializable lists for API responses |
| `HashMap<String, Object>` | All handler classes | Used to build JSON-serializable maps for API responses |
| `Map<String, Object>` | All handler classes | Return type for structured response data |
| `List<String>` | TrackingService, DBConnection | Return type for ID lists |
| `List<GemNode>` | GemLinkedList, TrackingService | Return type for stage lists |
| `Scanner` | MainMenu, GemForm, JourneyViewer, Main | Reads user input from System.in |

### java.time

| Class | Used In | Purpose |
|---|---|---|
| `LocalDate` | GemNode | Stores the date of each stage as a date-only value with no time component |
| `LocalDate.parse(String, DateTimeFormatter)` | GemHandler, StageHandler, GemForm | Parses date strings in yyyy-MM-dd format |
| `LocalDate.toString()` | GemNode, all handlers | Converts LocalDate to ISO format string for JSON and database storage |
| `DateTimeFormatter` | GemHandler, StageHandler | Defines the expected date format pattern yyyy-MM-dd |
| `DateTimeParseException` | GemHandler, StageHandler | Caught when an invalid date string is provided |
| `java.time.temporal.ChronoUnit.DAYS` | GemHandler | Calculates number of days between two stage dates |

### java.sql

| Class | Used In | Purpose |
|---|---|---|
| `Connection` | DBConnection | The active JDBC connection to gem_tracker.db |
| `PreparedStatement` | DBConnection | Used for all parameterized SQL queries to prevent SQL injection |
| `ResultSet` | DBConnection | Iterates rows returned from SELECT queries |
| `DriverManager.getConnection(String url)` | DBConnection | Opens the SQLite connection using the JDBC URL jdbc:sqlite:gem_tracker.db |
| `SQLException` | DBConnection | Caught for all database errors |

### java.io

| Class | Used In | Purpose |
|---|---|---|
| `File` | QRCodeService, ReportGenerator, QRHandler, ReportHandler | File existence checks, directory creation, file listing |
| `File.mkdirs()` | QRCodeService, ReportGenerator | Creates the qrcodes and reports directories if they do not exist |
| `FileInputStream` | QRHandler.downloadQRCode() | Reads QR PNG bytes for streaming to HTTP response |
| `FileWriter` | ReportGenerator | Writes report text content to .txt files |
| `BufferedReader, FileReader` | ReportHandler | Reads saved report files back into strings |
| `OutputStream` | QRHandler.downloadQRCode() | Writes PNG bytes directly to the HTTP response output stream |
| `PrintStream` | QRHandler.previewQRContent() | Temporarily redirects System.out to capture QR content text |
| `ByteArrayOutputStream` | QRHandler.previewQRContent() | Captures console output during QR content preview |
| `InputStreamReader, BufferedReader` | DBConnection | Reads the schema.sql file from the classpath |

### java.awt.image

| Class | Used In | Purpose |
|---|---|---|
| `BufferedImage` | QRCodeService | Creates the in-memory PNG image from the ZXing BitMatrix |
| `BufferedImage(int width, int height, int type)` | QRCodeService | 350x350 pixel image with TYPE_INT_RGB |

### javax.imageio

| Class | Used In | Purpose |
|---|---|---|
| `ImageIO.write(BufferedImage, String format, File output)` | QRCodeService | Saves the BufferedImage as a PNG file to the qrcodes folder |

---

## Design Patterns Used

### Singleton Pattern
Applied in DBConnection and ApiServer. Only one instance of each class is ever created. The pattern uses a private constructor and a static `getInstance()` method that checks if the instance is null before creating it.

```java
// DBConnection.java
private static DBConnection instance;

public static DBConnection getInstance() {
    if (instance == null) {
        instance = new DBConnection();
    }
    return instance;
}
```

### Doubly Linked List (Custom Implementation)
The core data structure. Implemented from scratch in GemLinkedList.java and GemNode.java without using any Java collection classes. Each GemNode has explicit `next` and `prev` pointer fields that are set manually during addStage() and removeStageAt() operations.

```java
// GemLinkedList.java — addStage()
if (head == null) {
    head = newNode;
    tail = newNode;
} else {
    newNode.setPrev(tail);
    tail.setNext(newNode);
    tail = newNode;
}
size++;
```

### Factory Method Pattern
Applied in ApiResponse. Static factory methods create different types of responses with consistent field values so that callers never construct ApiResponse objects directly.

### Dependency Injection
Applied throughout the service and handler layers. TrackingService is created once in Main.java (CLI mode) or ApiRouter.java (API mode) and injected into all classes that need it. This ensures a single shared service instance with one shared in-memory gem cache.

### DAO Pattern (Data Access Object)
Applied in DBConnection. All database operations are contained in one class, separated from business logic. Service classes call DBConnection methods rather than writing SQL directly.

---

## Third-Party Libraries

### spark-core-2.9.4.jar — Spark Java Framework

| Class | Package | Used In | Purpose |
|---|---|---|---|
| `Spark.port(int)` | spark | ApiServer | Sets the HTTP listening port to 4567 |
| `Spark.threadPool(int, int, int)` | spark | ApiServer | Configures max threads, min threads, idle timeout |
| `Spark.awaitInitialization()` | spark | ApiServer | Blocks until Spark has fully started and bound the port |
| `Spark.stop()` | spark | ApiServer | Shuts down the embedded Jetty server |
| `Spark.before(Filter)` | spark | CorsFilter | Registers a filter that runs before every request |
| `Spark.options(String, Route)` | spark | CorsFilter | Handles browser OPTIONS preflight requests |
| `Spark.get(String, Route)` | spark | ApiRouter | Registers a GET endpoint |
| `Spark.post(String, Route)` | spark | ApiRouter | Registers a POST endpoint |
| `Spark.put(String, Route)` | spark | ApiRouter | Registers a PUT endpoint |
| `Spark.delete(String, Route)` | spark | ApiRouter | Registers a DELETE endpoint |
| `Spark.notFound(Route)` | spark | ApiRouter | Registers a 404 handler |
| `Spark.internalServerError(Route)` | spark | ApiRouter | Registers a 500 handler |
| `Request.params(String)` | spark | All handlers | Reads path parameters like :id |
| `Request.queryParams(String)` | spark | GemHandler, StatsHandler | Reads query string parameters like ?type= |
| `Request.body()` | spark | GemHandler, StageHandler, etc | Reads the JSON request body |
| `Response.status(int)` | spark | All handlers | Sets the HTTP response status code |
| `Response.type(String)` | spark | All handlers, QRHandler | Sets the Content-Type response header |
| `Response.header(String, String)` | spark | CorsFilter, QRHandler | Sets custom response headers |
| `Response.raw()` | spark | QRHandler | Gets the raw HttpServletResponse for direct stream writing |

---

### gson-2.10.1.jar — Google Gson

| Class | Package | Used In | Purpose |
|---|---|---|---|
| `GsonBuilder` | com.google.gson | ApiResponse | Builder that creates a configured Gson instance |
| `GsonBuilder.setPrettyPrinting()` | com.google.gson | ApiResponse | Enables indented JSON output for readability |
| `GsonBuilder.serializeNulls()` | com.google.gson | ApiResponse | Includes null fields in JSON output |
| `Gson.toJson(Object)` | com.google.gson | ApiResponse.toJson() | Converts any Java object to a JSON string |
| `Gson.fromJson(String, Class)` | com.google.gson | ApiResponse.fromJson() | Parses a JSON string into a Java object |

Gson is used because Java has no built-in JSON support. The frontend sends data as JSON in request bodies, and every API response is a JSON string. Gson handles nested objects, lists, null values, and special characters automatically.

---

### sqlite-jdbc-3.45.1.0.jar — SQLite JDBC Driver

| Class | Package | Used In | Purpose |
|---|---|---|---|
| `org.sqlite.SQLiteJDBCLoader` | org.sqlite | Loaded automatically | Loads the native SQLite library for the current OS |
| JDBC URL format | N/A | DBConnection | `jdbc:sqlite:gem_tracker.db` opens or creates the database file |

The SQLite JDBC driver is registered automatically when the JAR is on the classpath. It provides a native SQLite implementation for each operating system inside the JAR. The database file gem_tracker.db is created at the working directory if it does not exist.

---

### core-3.5.2.jar and javase-3.5.2.jar — ZXing QR Code Library

| Class | Package | Used In | Purpose |
|---|---|---|---|
| `QRCodeWriter` | com.google.zxing.qrcode | QRCodeService | Encodes a text string into a QR code BitMatrix |
| `QRCodeWriter.encode(String, BarcodeFormat, int, int, Map)` | com.google.zxing.qrcode | QRCodeService | Takes the content string, format, width, height, and hints |
| `BitMatrix` | com.google.zxing.common | QRCodeService | 2D grid of boolean values representing the QR code pixels |
| `BarcodeFormat.QR_CODE` | com.google.zxing | QRCodeService | Enum value specifying QR code format |
| `EncodeHintType.ERROR_CORRECTION` | com.google.zxing | QRCodeService | Hint key for setting error correction level |
| `EncodeHintType.CHARACTER_SET` | com.google.zxing | QRCodeService | Hint key for setting UTF-8 character encoding |
| `EncodeHintType.MARGIN` | com.google.zxing | QRCodeService | Hint key for setting quiet zone margin |
| `ErrorCorrectionLevel.H` | com.google.zxing.qrcode.decoder | QRCodeService | Highest error correction level — 30% data recovery |
| `WriterException` | com.google.zxing | QRCodeService | Thrown if QR encoding fails |

The core JAR contains the encoding logic. The javase JAR adds Java SE specific rendering utilities. Both are required together.

---

### Jetty Jars — Embedded Web Server

| JAR | Purpose |
|---|---|
| jetty-server-9.4.51.v20230217.jar | Core HTTP server implementation used internally by Spark |
| jetty-servlet-9.4.51.v20230217.jar | Servlet container support for Spark route handlers |
| jetty-webapp-9.4.51.v20230217.jar | Web application deployment support |
| jetty-util-9.4.51.v20230217.jar | Common Jetty utility classes |
| jetty-io-9.4.51.v20230217.jar | Jetty I/O classes for connection handling |
| jetty-http-9.4.51.v20230217.jar | HTTP protocol implementation |
| javax.servlet-api-3.1.0.jar | Servlet API specification required by Jetty |

Jetty is not used directly in application code. Spark uses Jetty internally as its embedded HTTP server. All Jetty JAR files must be on the classpath for Spark to start successfully.

---

### SLF4J Jars — Logging

| JAR | Purpose |
|---|---|
| slf4j-api-2.0.9.jar | Logging facade API used by Spark and Jetty |
| slf4j-simple-2.0.9.jar | Simple implementation that prints log messages to console |

SLF4J is not used directly in application code. Spark and Jetty use it internally for logging HTTP request details and server startup information.

---

## Java Enums Used

### GemStage enum

```java
public enum GemStage {
    MINING("Mining Stage", "Mine location, district, village"),
    CUTTING("Cutting and Polishing Stage", "Gem cutting workshop"),
    TRADING("Trading Stage", "Gem bureau or trading house"),
    EXPORTING("Exporting Stage", "International airport or customs"),
    BUYING("Buying Stage", "International buyer location");

    private final String label;
    private final String locationHint;
}
```

Used in GemNode to identify what type of stage the node represents. Used in StageHandler to parse the stageType string from the request body. Used in VerificationHandler to detect suspicious location combinations.

---

## Java Generics Used

| Usage | Location | Purpose |
|---|---|---|
| `HashMap<String, GemLinkedList>` | TrackingService | Type-safe map of gem ID to linked list |
| `HashSet<String>` | OriginVerifier | Type-safe set of location strings |
| `ArrayList<GemNode>` | GemLinkedList | Type-safe list of stage nodes |
| `List<Map<String, Object>>` | All handlers | Type-safe list of JSON-serializable maps |
| `ApiResponse<T>` | ApiResponse | Generic type parameter for the data field |
| `Gson.fromJson(String, Class<T>)` | ApiResponse | Generic deserialization method |

---

## Exception Handling Strategy

All handler methods wrap their entire body in a try-catch block. If any exception is caught, a 500 server error ApiResponse is returned rather than allowing Spark to return an HTML error page. This ensures the React frontend always receives JSON regardless of what goes wrong.

All database methods catch SQLException specifically. All date parsing catches DateTimeParseException specifically. All number parsing catches NumberFormatException specifically.

---

## Memory Management

The `activeGems` HashMap in TrackingService acts as an in-memory cache. All gems are loaded from the database on startup and stored in memory for the lifetime of the application. This means all gem list operations run at in-memory speed without hitting the database on every request. The tradeoff is that memory usage grows with the number of registered gems. The database is only queried on startup and when a gem ID is requested that is not already in the cache.