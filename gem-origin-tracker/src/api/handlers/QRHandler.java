package api.handlers;

import api.ApiResponse;
import service.QRCodeService;

import spark.Request;
import spark.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * QRHandler handles all HTTP requests related to QR code generation,
 * retrieval, regeneration, download, and status checking.
 *
 * This handler exposes novel feature 1 of the project through the
 * REST API. The React frontend calls these endpoints to generate
 * QR codes for gems and display them on the track gem page.
 *
 * The most important endpoint here is the download endpoint which
 * serves the QR code PNG image file directly to the browser so the
 * frontend can display it inline using an img tag with the API URL
 * as the source.
 *
 * Each method validates the request, calls QRCodeService, and returns
 * either a JSON ApiResponse string or a raw PNG image byte stream
 * for the download endpoint.
 */
public class QRHandler {

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The QRCodeService handles all QR code generation and management.
     * Injected via constructor from ApiRouter.
     */
    private QRCodeService qrCodeService;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new QRHandler with the required service dependency.
     *
     * @param qrCodeService the service for all QR code operations
     */
    public QRHandler(QRCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/qr — get QR code status
    // ---------------------------------------------------------

    /**
     * Returns the QR code status for a specific gem.
     * Tells the frontend whether a QR code has been generated
     * for this gem and provides the download URL if it exists.
     *
     * The frontend uses this to decide whether to show a Generate
     * button or a Download button on the track gem page.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the QR code status and download URL
     */
    public String getQRStatus(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            String cleanGemId = gemId.trim();
            boolean exists    = qrCodeService.qrCodeExists(cleanGemId);
            String filePath   = qrCodeService.getQRCodePath(cleanGemId);

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",         cleanGemId);
            result.put("exists",        exists);
            result.put("filePath",      exists ? filePath : null);
            result.put("downloadUrl",
                    exists
                        ? "/api/gems/" + cleanGemId + "/qr/download"
                        : null);
            result.put("status",
                    exists ? "GENERATED" : "NOT_GENERATED");
            result.put("statusMessage",
                    exists
                        ? "QR code is available for download."
                        : "No QR code has been generated yet for this gem.");

            // Get file size if exists
            if (exists) {
                File qrFile = new File(filePath);
                if (qrFile.exists()) {
                    result.put("fileSizeBytes", qrFile.length());
                    result.put("fileSizeKB",
                            String.format("%.1f KB",
                                    qrFile.length() / 1024.0));
                }
            }

            response.status(200);
            return ApiResponse.success(
                    exists
                        ? "QR code exists for gem: " + cleanGemId
                        : "No QR code found for gem: " + cleanGemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to get QR code status: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // POST /api/gems/:id/qr — generate QR code
    // ---------------------------------------------------------

    /**
     * Generates a new QR code for a specific gem and saves it as a PNG file.
     * If a QR code already exists this endpoint returns an error telling
     * the frontend to use the regenerate endpoint instead.
     *
     * The QR code encodes the complete gem journey summary so anyone
     * who scans it can see the full origin-to-buyer chain instantly.
     *
     * After successful generation the response includes the file path
     * and a download URL the frontend can use to display the QR image.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the generated QR code details
     */
    public String generateQRCode(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            String cleanGemId = gemId.trim();

            // Check if QR code already exists
            // If it does, tell the frontend to use the regenerate endpoint
            if (qrCodeService.qrCodeExists(cleanGemId)) {
                response.status(400);
                return ApiResponse.badRequest(
                        "A QR code already exists for gem: " + cleanGemId
                        + ". Use PUT /api/gems/"
                        + cleanGemId
                        + "/qr to regenerate it."
                ).toJson();
            }

            // Generate the QR code
            String savedPath = qrCodeService.generateQRCode(cleanGemId);

            if (savedPath == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "QR code generation failed for gem: " + cleanGemId
                        + ". Ensure the gem has at least one stage recorded."
                ).toJson();
            }

            // Get file details for the response
            File qrFile = new File(savedPath);
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",       cleanGemId);
            result.put("filePath",    savedPath);
            result.put("downloadUrl",
                    "/api/gems/" + cleanGemId + "/qr/download");
            result.put("previewUrl",
                    "/api/gems/" + cleanGemId + "/qr/preview");
            result.put("status",      "GENERATED");
            result.put("statusMessage",
                    "QR code generated successfully. "
                    + "Scan it with any mobile QR scanner to view the gem journey.");

            if (qrFile.exists()) {
                result.put("fileSizeBytes", qrFile.length());
                result.put("fileSizeKB",
                        String.format("%.1f KB", qrFile.length() / 1024.0));
            }

            response.status(201);
            return ApiResponse.created(
                    "QR code generated successfully for gem: " + cleanGemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "QR code generation failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // PUT /api/gems/:id/qr — regenerate QR code
    // ---------------------------------------------------------

    /**
     * Regenerates the QR code for a gem by deleting the existing one
     * and creating a fresh one with the latest journey data.
     *
     * This must be called after adding a new stage to a gem because
     * the old QR code would encode outdated journey information.
     * The frontend automatically calls this endpoint after every
     * successful stage addition.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the regenerated QR code details
     */
    public String regenerateQRCode(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            String cleanGemId = gemId.trim();

            // Regenerate the QR code
            // QRCodeService handles deletion of the old file automatically
            String savedPath = qrCodeService.regenerateQRCode(cleanGemId);

            if (savedPath == null) {
                response.status(500);
                return ApiResponse.serverError(
                        "QR code regeneration failed for gem: " + cleanGemId
                ).toJson();
            }

            File qrFile = new File(savedPath);
            Map<String, Object> result = new HashMap<>();
            result.put("gemId",       cleanGemId);
            result.put("filePath",    savedPath);
            result.put("downloadUrl",
                    "/api/gems/" + cleanGemId + "/qr/download");
            result.put("previewUrl",
                    "/api/gems/" + cleanGemId + "/qr/preview");
            result.put("status",      "REGENERATED");
            result.put("statusMessage",
                    "QR code regenerated with the latest journey data. "
                    + "Old QR codes for this gem are now outdated.");

            if (qrFile.exists()) {
                result.put("fileSizeBytes", qrFile.length());
                result.put("fileSizeKB",
                        String.format("%.1f KB", qrFile.length() / 1024.0));
            }

            response.status(200);
            return ApiResponse.success(
                    "QR code regenerated successfully for gem: " + cleanGemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "QR code regeneration failed: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/qr/download — download QR code image
    // ---------------------------------------------------------

    /**
     * Serves the QR code PNG image file directly to the browser.
     * This endpoint returns raw binary image data instead of JSON.
     *
     * The frontend uses this URL as the src attribute of an img tag:
     * <img src="http://localhost:4567/api/gems/BS-123/qr/download" />
     *
     * This is the most important QR endpoint because it allows the
     * frontend to display the QR code image directly without needing
     * to download a file separately.
     *
     * The Content-Type is set to image/png instead of application/json
     * so the browser knows to render it as an image.
     *
     * The Content-Disposition header is set to inline so the image
     * displays in the browser rather than triggering a file download.
     * Setting it to attachment would force a file download instead.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return null because the image bytes are written directly to the
     *         response output stream, not returned as a string
     */
    public String downloadQRCode(Request request, Response response) {
        String gemId = request.params(":id");

        if (gemId == null || gemId.trim().isEmpty()) {
            response.status(400);
            response.type("application/json");
            return ApiResponse.badRequest("Gem ID is required.").toJson();
        }

        String cleanGemId = gemId.trim();
        String filePath   = qrCodeService.getQRCodePath(cleanGemId);
        File   qrFile     = new File(filePath);

        // Check if the QR code file exists on disk
        if (!qrFile.exists()) {
            // If not found, automatically generate one
            String generated = qrCodeService.generateQRCode(cleanGemId);
            if (generated == null) {
                response.status(404);
                response.type("application/json");
                return ApiResponse.error(
                        "QR code not found and could not be generated "
                        + "for gem: " + cleanGemId
                        + ". Ensure the gem exists and has stages recorded.",
                        404
                ).toJson();
            }
            qrFile = new File(generated);
        }

        try {
            // Set the response headers for image serving
            // image/png tells the browser this is a PNG image file
            response.type("image/png");

            // inline tells the browser to display the image in the page
            // Use attachment instead of inline to force a file download
            response.header("Content-Disposition",
                    "inline; filename=\"" + cleanGemId + "_QR.png\"");

            // Set content length so the browser knows how many bytes to expect
            response.header("Content-Length",
                    String.valueOf(qrFile.length()));

            // Allow caching for 1 hour since QR codes rarely change
            // This reduces repeated downloads of the same QR code image
            response.header("Cache-Control",
                    "public, max-age=3600");

            // Write the image bytes directly to the response output stream
            // This bypasses the normal JSON response mechanism
            OutputStream outputStream =
                    response.raw().getOutputStream();
            FileInputStream fileInputStream =
                    new FileInputStream(qrFile);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            fileInputStream.close();
            outputStream.flush();

            // Return null because we wrote directly to the output stream
            // Spark will not try to write the return value as the body
            return null;

        } catch (Exception e) {
            response.status(500);
            response.type("application/json");
            return ApiResponse.serverError(
                    "Failed to serve QR code image: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/gems/:id/qr/preview — preview QR code content
    // ---------------------------------------------------------

    /**
     * Returns the text content that is encoded inside the QR code
     * without generating or serving the actual image file.
     *
     * The frontend uses this to show a preview of what information
     * is encoded in the QR code before the user generates or downloads it.
     * Displayed on the track gem page in a modal or expandable panel.
     *
     * @param request  the incoming HTTP request with :id path parameter
     * @param response the outgoing HTTP response
     * @return a JSON string with the QR code text content
     */
    public String previewQRContent(Request request, Response response) {
        try {
            String gemId = request.params(":id");

            if (gemId == null || gemId.trim().isEmpty()) {
                response.status(400);
                return ApiResponse.badRequest("Gem ID is required.").toJson();
            }

            String cleanGemId = gemId.trim();

            // Capture the QR code content by redirecting console output
            // We use a ByteArrayOutputStream to capture what QRCodeService
            // would normally print to the console
            java.io.ByteArrayOutputStream baos =
                    new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            System.setOut(new java.io.PrintStream(baos));

            qrCodeService.previewQRContent(cleanGemId);

            System.setOut(originalOut);
            String content = baos.toString().trim();

            if (content.isEmpty()) {
                response.status(404);
                return ApiResponse.notFound("Gem", cleanGemId).toJson();
            }

            // Remove the header line that says "QR Code Content Preview"
            // and return just the actual encoded content
            String[] lines = content.split("\n");
            StringBuilder encodedContent = new StringBuilder();
            boolean started = false;
            for (String line : lines) {
                if (started) {
                    encodedContent.append(line).append("\n");
                }
                if (line.trim().isEmpty() && !started) {
                    started = true;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("gemId",          cleanGemId);
            result.put("fullContent",    content);
            result.put("contentLines",   lines.length);
            result.put("downloadUrl",
                    "/api/gems/" + cleanGemId + "/qr/download");
            result.put("note",
                    "This is the exact text encoded in the QR code. "
                    + "Any QR scanner app will display this information "
                    + "when the QR code is scanned.");

            response.status(200);
            return ApiResponse.success(
                    "QR code content preview for gem: " + cleanGemId,
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to preview QR content: " + e.getMessage()
            ).toJson();
        }
    }

    // ---------------------------------------------------------
    // GET /api/qr/status — QR status for all gems
    // ---------------------------------------------------------

    /**
     * Returns the QR code status for every gem in the system.
     * Shows which gems have QR codes and which ones still need them.
     *
     * The frontend uses this on the QR management section to show
     * an overview of QR code coverage across all registered gems.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a JSON string with QR status for all gems
     */
    public String getAllQRStatus(Request request, Response response) {
        try {
            // Get all gem IDs from the QRCodeService by checking
            // which gems have QR code files in the qrcodes folder
            File qrFolder = new File("qrcodes");
            List<Map<String, Object>> statusList = new ArrayList<>();

            int withQR    = 0;
            int withoutQR = 0;

            if (qrFolder.exists() && qrFolder.isDirectory()) {
                // Get all PNG files in the qrcodes folder
                File[] qrFiles = qrFolder.listFiles(
                        (dir, name) -> name.endsWith("_QR.png"));

                if (qrFiles != null) {
                    for (File qrFile : qrFiles) {
                        String fileName = qrFile.getName();
                        // Extract gem ID from filename by removing _QR.png
                        String gemId = fileName.replace("_QR.png", "");

                        Map<String, Object> status = new HashMap<>();
                        status.put("gemId",       gemId);
                        status.put("exists",      true);
                        status.put("filePath",    qrFile.getPath());
                        status.put("fileSizeKB",
                                String.format("%.1f KB",
                                        qrFile.length() / 1024.0));
                        status.put("downloadUrl",
                                "/api/gems/" + gemId + "/qr/download");
                        status.put("lastModified",
                                new java.util.Date(
                                        qrFile.lastModified()).toString());

                        statusList.add(status);
                        withQR++;
                    }
                }
            }

            // Build the summary response
            Map<String, Object> result = new HashMap<>();
            result.put("qrCodes",         statusList);
            result.put("totalWithQR",     withQR);
            result.put("qrFolderPath",
                    qrFolder.exists() ? qrFolder.getAbsolutePath() : "Not created yet");
            result.put("summary",
                    withQR == 0
                        ? "No QR codes have been generated yet."
                        : withQR + " QR code(s) available for download.");

            response.status(200);
            return ApiResponse.success(
                    "QR code status retrieved for all gems",
                    result
            ).toJson();

        } catch (Exception e) {
            response.status(500);
            return ApiResponse.serverError(
                    "Failed to retrieve QR code status: " + e.getMessage()
            ).toJson();
        }
    }
}