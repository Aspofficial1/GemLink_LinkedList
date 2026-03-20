import api.ApiServer;
import database.DBConnection;
import ui.MainMenu;

/**
 * Main is the entry point of the Gem Origin Tracking System.
 * This is the first class that runs when the application starts.
 *
 * The main method performs four responsibilities:
 * 1. Prints a startup message to confirm the application is launching.
 * 2. Asks the user whether to start in CLI mode or API server mode.
 * 3. Starts the chosen mode — CLI launches MainMenu, API launches ApiServer.
 * 4. Ensures the database connection is closed cleanly on exit,
 *    even if the application crashes or exits unexpectedly.
 *
 * CLI mode preserves the original terminal-based interface so the
 * application can still be demonstrated and tested without the frontend.
 * This is important because the teacher may ask for a CLI demonstration.
 *
 * API mode starts the Spark HTTP server on port 4567 so the React
 * frontend can connect to it and use the full web interface.
 *
 * Both modes share the same database, service layer, and business logic.
 * Only the interface layer is different between the two modes.
 *
 * A shutdown hook is registered with the Java runtime to guarantee
 * the database connection and API server are always closed cleanly
 * when the JVM exits, regardless of how the application terminates.
 */
public class Main {

    /**
     * The main method — application entry point.
     * Called by the JVM when the program is launched.
     *
     * Accepts an optional command line argument to skip the mode
     * selection prompt:
     *   java Main cli    — starts directly in CLI mode
     *   java Main api    — starts directly in API server mode
     *   java Main        — shows the mode selection menu
     *
     * @param args optional command line arguments: "cli" or "api"
     */
    public static void main(String[] args) {

        // Print startup confirmation to the console
        System.out.println();
        System.out.println("  +---------------------------------------------------------+");
        System.out.println("  |                                                         |");
        System.out.println("  |        GEM ORIGIN TRACKING SYSTEM                      |");
        System.out.println("  |        Ceylon Gem Digital Passport                      |");
        System.out.println("  |                                                         |");
        System.out.println("  |        National Institute of Business Management        |");
        System.out.println("  |        HND Software Engineering — PDSA Coursework       |");
        System.out.println("  |                                                         |");
        System.out.println("  +---------------------------------------------------------+");
        System.out.println();
        System.out.println("  Starting Gem Origin Tracking System...");
        System.out.println("  Connecting to database...");
        System.out.println("  Loading gem records...");
        System.out.println();

        // Register a shutdown hook to close the database connection
        // and stop the API server cleanly whenever the JVM exits.
        // This runs even if the user closes the terminal window or
        // presses Ctrl+C instead of using the exit menu option.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Stop the API server if it is running
                ApiServer apiServer = ApiServer.getInstance();
                if (apiServer.isRunning()) {
                    apiServer.stop();
                }
            } catch (Exception e) {
                System.out.println("  Warning: Could not stop API server on shutdown.");
            }

            try {
                // Close the database connection cleanly
                DBConnection.getInstance().closeConnection();
                System.out.println();
                System.out.println("  Database connection closed on shutdown.");
            } catch (Exception e) {
                System.out.println("  Warning: Could not close database on shutdown.");
            }
        }));

        // ---------------------------------------------------------
        // Determine launch mode
        // ---------------------------------------------------------

        // Check if a command line argument was provided to skip
        // the mode selection prompt
        String launchMode = "";

        if (args != null && args.length > 0) {
            launchMode = args[0].trim().toLowerCase();
            System.out.println("  Launch mode from command line argument: "
                    + launchMode.toUpperCase());
            System.out.println();
        }

        // If no command line argument was given, show the mode
        // selection menu and ask the user to choose
        if (!launchMode.equals("cli") && !launchMode.equals("api")) {
            launchMode = showModeSelectionMenu();
        }

        // ---------------------------------------------------------
        // Start the chosen mode
        // ---------------------------------------------------------

        if (launchMode.equals("api")) {
            startApiMode();
        } else {
            startCliMode();
        }

        // Final exit message after the main loop ends
        System.out.println();
        System.out.println("  Application exited. Goodbye.");
        System.out.println();
    }

    // ---------------------------------------------------------
    // Mode selection menu
    // ---------------------------------------------------------

    /**
     * Displays the mode selection menu and waits for the user to
     * choose between CLI mode and API server mode.
     *
     * CLI mode is the original terminal-based interface.
     * API mode starts the HTTP server for the React frontend.
     *
     * The menu loops until a valid choice of 1 or 2 is entered.
     *
     * @return the selected mode string — either "cli" or "api"
     */
    private static String showModeSelectionMenu() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);

        while (true) {
            System.out.println("  +---------------------------------------------------------+");
            System.out.println("  |                                                         |");
            System.out.println("  |  Select Launch Mode:                                    |");
            System.out.println("  |                                                         |");
            System.out.println("  |  [1]  CLI Mode  — Terminal based interface              |");
            System.out.println("  |         Use this when your teacher asks for a           |");
            System.out.println("  |         command line demonstration                      |");
            System.out.println("  |                                                         |");
            System.out.println("  |  [2]  API Mode  — REST API server for React frontend    |");
            System.out.println("  |         Use this to run the full web application        |");
            System.out.println("  |         Start the React frontend separately after       |");
            System.out.println("  |         the API server is running                       |");
            System.out.println("  |                                                         |");
            System.out.println("  +---------------------------------------------------------+");
            System.out.println();
            System.out.print("  Enter your choice (1 or 2): ");

            String input = scanner.nextLine().trim();

            if (input.equals("1")) {
                System.out.println();
                System.out.println("  CLI mode selected.");
                System.out.println();
                return "cli";
            } else if (input.equals("2")) {
                System.out.println();
                System.out.println("  API server mode selected.");
                System.out.println();
                return "api";
            } else {
                System.out.println();
                System.out.println("  Invalid choice. Please enter 1 for CLI or 2 for API.");
                System.out.println();
            }
        }
    }

    // ---------------------------------------------------------
    // CLI mode startup
    // ---------------------------------------------------------

    /**
     * Starts the application in CLI mode using the original terminal
     * based MainMenu interface.
     *
     * This is the original mode of the application before the REST API
     * was added. It preserves full CLI functionality so the teacher
     * can test all features through the terminal interface.
     *
     * The MainMenu start() method runs in a loop until the user
     * selects exit from the main menu.
     */
    private static void startCliMode() {
        System.out.println("  Starting CLI mode...");
        System.out.println("  All features are available through the terminal menu.");
        System.out.println();

        try {
            MainMenu mainMenu = new MainMenu();
            mainMenu.start();
        } catch (Exception e) {
            // Catch any unexpected errors at the top level so the user
            // sees a clean error message instead of a raw stack trace
            System.out.println();
            System.out.println("  A critical error occurred in CLI mode: "
                    + e.getMessage());
            System.out.println("  Please restart the application.");
            System.out.println();
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------
    // API server mode startup
    // ---------------------------------------------------------

    /**
     * Starts the application in API server mode using the Spark
     * HTTP server so the React frontend can connect to it.
     *
     * The API server starts on port 4567 and stays running until
     * the user presses Enter in the terminal or Ctrl+C to stop it.
     *
     * While the server is running the user can:
     * - Open the React frontend in the browser
     * - Test the API directly at http://localhost:4567/api/health
     * - Press Enter in the terminal to stop the server cleanly
     *
     * The server runs in the background on Spark's internal thread pool
     * so the main thread can listen for the stop command in the console.
     */
    private static void startApiMode() {
        System.out.println("  Starting API server mode...");
        System.out.println("  The REST API will be available at:");
        System.out.println("  http://localhost:4567/api");
        System.out.println();

        try {
            // Start the API server
            ApiServer apiServer = ApiServer.getInstance();
            boolean started = apiServer.start();

            if (!started) {
                System.out.println();
                System.out.println("  ERROR: Failed to start the API server.");
                System.out.println("  Check that port 4567 is not already in use.");
                System.out.println("  Try running: netstat -ano | findstr :4567");
                System.out.println();
                return;
            }

            // Print instructions for the user
            System.out.println();
            System.out.println("  API server is running. Instructions:");
            System.out.println();
            System.out.println("  1. Open your React frontend in the browser");
            System.out.println("  2. The frontend will connect to http://localhost:4567/api");
            System.out.println("  3. Test the API at: http://localhost:4567/api/health");
            System.out.println("  4. Press Enter here to stop the server when done");
            System.out.println();
            System.out.println("  Available endpoints summary:");
            System.out.println("  GET  http://localhost:4567/api/health");
            System.out.println("  GET  http://localhost:4567/api/gems");
            System.out.println("  GET  http://localhost:4567/api/stats");
            System.out.println("  GET  http://localhost:4567/api/alerts/unresolved");
            System.out.println();
            System.out.println("  CLI mode is also still available.");
            System.out.println("  Restart the application and choose option 1 for CLI.");
            System.out.println();

            // Wait for the user to press Enter to stop the server
            // The server continues running on background threads
            // while we wait here on the main thread
            System.out.print("  Press Enter to stop the API server...");
            new java.util.Scanner(System.in).nextLine();

            // Stop the API server cleanly
            System.out.println();
            System.out.println("  Stopping API server...");
            apiServer.stop();

        } catch (Exception e) {
            System.out.println();
            System.out.println("  A critical error occurred in API mode: "
                    + e.getMessage());
            System.out.println("  Please restart the application.");
            System.out.println();
            e.printStackTrace();
        }
    }
}