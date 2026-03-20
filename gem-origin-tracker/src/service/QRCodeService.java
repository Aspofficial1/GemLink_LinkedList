package service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import database.DBConnection;
import model.GemLinkedList;
import model.GemNode;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * QRCodeService is responsible for generating unique QR codes
 * for each gemstone registered in the system.
 *
 * This is novel feature 1 of the project.
 * Each QR code encodes the complete journey summary of the gem
 * so that anyone with a mobile device can scan the QR code and
 * instantly see the full origin-to-buyer chain of the gemstone.
 *
 * This directly solves the trust problem in the gem industry
 * because buyers anywhere in the world can verify a gem's
 * authenticity with a simple scan, without needing to contact
 * the seller or check paper documents.
 *
 * ZXing (Zebra Crossing) library is used for QR code generation
 * because it is the most widely used open-source QR code library
 * for Java and produces high quality scannable QR images.
 */
public class QRCodeService {

    // ---------------------------------------------------------
    // Constants
    // ---------------------------------------------------------

    /**
     * The width of the generated QR code image in pixels.
     * 400px produces a clear, easily scannable QR code image.
     */
    private static final int QR_WIDTH = 400;

    /**
     * The height of the generated QR code image in pixels.
     * Kept equal to QR_WIDTH to produce a square QR code.
     */
    private static final int QR_HEIGHT = 400;

    /**
     * The image format used when saving the QR code to disk.
     * PNG is used because it is lossless and preserves the sharp
     * black and white pixels needed for accurate QR scanning.
     */
    private static final String IMAGE_FORMAT = "PNG";

    /**
     * The folder where all generated QR code images are saved.
     * Created automatically if it does not already exist.
     */
    private static final String QR_OUTPUT_FOLDER = "qrcodes";

    // ---------------------------------------------------------
    // Fields
    // ---------------------------------------------------------

    /**
     * The database connection used to update the QR code file path
     * in the gems table after the image has been generated.
     */
    private DBConnection db;

    /**
     * The TrackingService used to retrieve gem journey data
     * that will be encoded into the QR code content string.
     */
    private TrackingService trackingService;

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------

    /**
     * Creates a new QRCodeService and ensures the QR code output
     * folder exists on the file system.
     * If the folder does not exist it is created automatically so
     * the first QR code generation does not fail due to a missing folder.
     *
     * @param trackingService the TrackingService instance used to
     *                        retrieve gem data for QR code content
     */
    public QRCodeService(TrackingService trackingService) {
        this.trackingService = trackingService;
        this.db              = DBConnection.getInstance();

        // Create the QR code output folder if it does not already exist
        File outputFolder = new File(QR_OUTPUT_FOLDER);
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
            System.out.println("💎 QR code output folder created: " + QR_OUTPUT_FOLDER);
        }
    }

    // ---------------------------------------------------------
    // Primary QR code generation
    // ---------------------------------------------------------

    /**
     * Generates a QR code image for a specific gem and saves it
     * as a PNG file in the qrcodes folder.
     *
     * The QR code encodes the full journey summary of the gem
     * including the gem ID, type, origin, all stages, and the
     * Ceylon verification status.
     *
     * After the image is saved, the file path is updated in the
     * database so the gem record always points to its QR code.
     *
     * @param gemId the ID of the gem to generate the QR code for
     * @return the file path of the saved QR code image,
     *         or null if generation failed
     */
    public String generateQRCode(String gemId) {
        System.out.println("💎 Generating QR code for Gem ID: " + gemId);

        // Retrieve the gem's linked list to build the QR code content
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            System.out.println("  QR generation failed: Gem not found.");
            return null;
        }

        // Build the text content that will be encoded into the QR code
        String qrContent = buildQRContent(list);

        // Define the output file path for this gem's QR code
        String fileName  = QR_OUTPUT_FOLDER + File.separator + gemId + "_QR.png";

        // Generate and save the QR code image
        boolean success = createQRImage(qrContent, fileName);

        if (success) {
            // Update the database with the file path of the saved QR code
            db.updateQRCodePath(gemId, fileName);
            System.out.println("💎 QR code saved successfully: " + fileName);
            return fileName;
        } else {
            System.out.println("  QR code generation failed for Gem ID: " + gemId);
            return null;
        }
    }

    /**
     * Generates QR codes for all gems currently registered in the system.
     * Used by administrators to batch-produce QR codes for all gems
     * that do not yet have one.
     *
     * @return the number of QR codes successfully generated
     */
    public int generateQRCodesForAllGems() {
        List<String> allIds = trackingService.getAllGemIds();

        if (allIds.isEmpty()) {
            System.out.println("No gems found in the system.");
            return 0;
        }

        System.out.println("💎 Generating QR codes for all " + allIds.size() + " gems.");
        int successCount = 0;

        for (String gemId : allIds) {
            String path = generateQRCode(gemId);
            if (path != null) {
                successCount++;
            }
        }

        System.out.println("💎 QR code generation complete.");
        System.out.println("  Successfully generated : " + successCount);
        System.out.println("  Failed                 : " + (allIds.size() - successCount));
        return successCount;
    }

    // ---------------------------------------------------------
    // QR code content builder
    // ---------------------------------------------------------

    /**
     * Builds the text content string that will be encoded into
     * the QR code for a given gem.
     *
     * The content is structured as plain text with clear labels
     * so it is readable when someone scans the QR code with any
     * standard QR scanner app on a mobile device.
     *
     * The content includes the gem ID, gem type, origin details,
     * the full stage-by-stage journey, weight and price summary,
     * and the Ceylon verification status.
     *
     * @param list the GemLinkedList whose data will be encoded
     * @return a formatted plain text string for the QR code
     */
    private String buildQRContent(GemLinkedList list) {
        StringBuilder sb = new StringBuilder();

        // Header section
        sb.append("GEM ORIGIN TRACKER - CEYLON GEM PASSPORT\n");
        sb.append("Gem ID   : ").append(list.getGemId()).append("\n");

        // Mining node details (origin information)
        GemNode miningNode = list.getMiningNode();
        if (miningNode != null) {
            sb.append("Gem Type : ").append(miningNode.getGemType()).append("\n");
            sb.append("Origin   : ").append(miningNode.getLocation()).append("\n");
            sb.append("Mined on : ").append(miningNode.getStageDate()).append("\n");
            sb.append("Miner    : ").append(miningNode.getPersonName()).append("\n");
            sb.append("Orig. Wt : ").append(miningNode.getWeightInCarats())
              .append(" carats\n");
        }

        sb.append("\n");
        sb.append("JOURNEY STAGES\n");

        // Stage-by-stage journey details
        List<GemNode> stages = list.getAllStages();
        for (int i = 0; i < stages.size(); i++) {
            GemNode node = stages.get(i);
            sb.append("Stage ").append(i + 1).append(": ")
              .append(node.getStage().getLabel()).append("\n");
            sb.append("  Location : ").append(node.getLocation()).append("\n");
            sb.append("  Person   : ").append(node.getPersonName()).append("\n");
            sb.append("  Date     : ").append(node.getStageDate()).append("\n");
            sb.append("  Weight   : ").append(node.getWeightInCarats())
              .append(" carats\n");
            sb.append("  Price    : Rs. ").append(node.getPriceInRupees()).append("\n");

            // Include certificate details if present at this stage
            if (node.getCertificateNumber() != null) {
                sb.append("  Cert No  : ").append(node.getCertificateNumber()).append("\n");
                sb.append("  Cert By  : ").append(node.getIssuingAuthority()).append("\n");
            }

            // Include export details if this is the exporting stage
            if (node.getFlightNumber() != null) {
                sb.append("  Flight   : ").append(node.getFlightNumber()).append("\n");
                sb.append("  Invoice  : ").append(node.getInvoiceNumber()).append("\n");
                sb.append("  Dest     : ").append(node.getDestinationCountry()).append("\n");
            }

            sb.append("\n");
        }

        // Weight and price summary section
        sb.append("SUMMARY\n");
        sb.append(String.format("Weight Lost    : %.4f carats (%.2f%%)\n",
                list.calculateWeightLoss(),
                list.calculateWeightLossPercentage()));
        sb.append(String.format("Price Increase : Rs. %.2f\n",
                list.calculatePriceAppreciation()));

        // Current owner details from the tail node
        GemNode currentNode = list.getCurrentStageNode();
        if (currentNode != null) {
            sb.append("Current Owner  : ").append(currentNode.getPersonName()).append("\n");
            sb.append("Current Stage  : ").append(currentNode.getStage().getLabel()).append("\n");
        }

        // Verification footer
        sb.append("\n");
        sb.append("Verified by Gem Origin Tracking System\n");
        sb.append("National Gem and Jewellery Authority - Sri Lanka\n");

        return sb.toString();
    }

    // ---------------------------------------------------------
    // Image creation
    // ---------------------------------------------------------

    /**
     * Creates a QR code image from a content string and saves it
     * as a PNG file at the specified file path.
     *
     * ZXing's QRCodeWriter encodes the content string into a BitMatrix,
     * which is a 2D array of true/false values representing black and
     * white pixels. The BitMatrix is then converted into a BufferedImage
     * and saved to disk using Java's ImageIO.
     *
     * Error correction level H (High) is used so the QR code can still
     * be scanned even if up to 30% of the image is damaged or obscured.
     * This is important for physical QR codes on gem certificates that
     * may get scratched or worn over time.
     *
     * @param content  the text string to encode into the QR code
     * @param filePath the full file path where the PNG will be saved
     * @return true if the image was created and saved successfully
     */
    private boolean createQRImage(String content, String filePath) {
        try {
            // Configure QR code encoding hints
            Map<EncodeHintType, Object> hints = new HashMap<>();

            // Use high error correction so the QR code stays scannable
            // even if part of the image is damaged
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);

            // Set character encoding to UTF-8 to support all characters
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            // Set a margin of 1 around the QR code (in modules)
            // A smaller margin gives more space for the actual QR data
            hints.put(EncodeHintType.MARGIN, 1);

            // Use ZXing's QRCodeWriter to encode the content into a BitMatrix
            // BitMatrix is a 2D grid where true = black pixel, false = white pixel
            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix bitMatrix   = qrWriter.encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    QR_WIDTH,
                    QR_HEIGHT,
                    hints
            );

            // Convert the BitMatrix into a BufferedImage for saving
            BufferedImage qrImage = new BufferedImage(
                    QR_WIDTH, QR_HEIGHT, BufferedImage.TYPE_INT_RGB);

            // Draw each pixel of the QR code onto the BufferedImage
            // Black pixels represent the QR code modules (true in BitMatrix)
            // White pixels are the background (false in BitMatrix)
            for (int x = 0; x < QR_WIDTH; x++) {
                for (int y = 0; y < QR_HEIGHT; y++) {
                    qrImage.setRGB(x, y,
                            bitMatrix.get(x, y)
                                    ? Color.BLACK.getRGB()
                                    : Color.WHITE.getRGB());
                }
            }

            // Add a white border around the QR code image for better scannability
            // Some scanners have difficulty reading QR codes without a quiet zone
            BufferedImage finalImage = addBorderToImage(qrImage, 20);

            // Save the final image to disk as a PNG file
            File outputFile = new File(filePath);
            ImageIO.write(finalImage, IMAGE_FORMAT, outputFile);

            return true;

        } catch (WriterException e) {
            System.out.println("  QR encoding failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            System.out.println("  QR file save failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Adds a white border around a QR code image.
     * This white border is called the quiet zone in QR code standards.
     * The quiet zone helps QR scanners locate and align the QR code
     * correctly, especially when the code is printed on a dark background.
     *
     * @param original     the original QR code BufferedImage
     * @param borderSize   the number of white pixels to add on each side
     * @return a new BufferedImage with the white border added
     */
    private BufferedImage addBorderToImage(BufferedImage original, int borderSize) {
        int newWidth  = original.getWidth()  + (borderSize * 2);
        int newHeight = original.getHeight() + (borderSize * 2);

        // Create a new image with white background for the border
        BufferedImage bordered = new BufferedImage(
                newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = bordered.createGraphics();

        // Fill the entire image with white to create the border
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newWidth, newHeight);

        // Draw the original QR code centred within the white border
        g2d.drawImage(original, borderSize, borderSize, null);
        g2d.dispose();

        return bordered;
    }

    // ---------------------------------------------------------
    // QR code verification
    // ---------------------------------------------------------

    /**
     * Checks whether a QR code image file already exists for a gem.
     * Used before regenerating a QR code to avoid unnecessary overwrites.
     *
     * @param gemId the ID of the gem to check
     * @return true if a QR code file already exists for this gem
     */
    public boolean qrCodeExists(String gemId) {
        String filePath = QR_OUTPUT_FOLDER + File.separator + gemId + "_QR.png";
        File   file     = new File(filePath);
        return file.exists();
    }

    /**
     * Returns the expected file path for a gem's QR code.
     * Used by the UI to display or open the QR code image.
     *
     * @param gemId the ID of the gem
     * @return the expected file path string for this gem's QR code
     */
    public String getQRCodePath(String gemId) {
        return QR_OUTPUT_FOLDER + File.separator + gemId + "_QR.png";
    }

    /**
     * Deletes the QR code image file for a specific gem.
     * Called when a gem is deleted from the system so that
     * orphaned QR code files do not remain on disk.
     *
     * @param gemId the ID of the gem whose QR code to delete
     * @return true if the file was deleted successfully
     */
    public boolean deleteQRCode(String gemId) {
        String filePath = QR_OUTPUT_FOLDER + File.separator + gemId + "_QR.png";
        File   file     = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                System.out.println("💎 QR code deleted for Gem ID: " + gemId);
            }
            return deleted;
        }
        return false;
    }

    /**
     * Regenerates the QR code for a gem whose journey has been updated.
     * Called after a new stage is added to a gem so the QR code
     * always reflects the most current journey data.
     *
     * The old QR code file is deleted before the new one is created
     * to prevent outdated QR codes from remaining on disk.
     *
     * @param gemId the ID of the gem whose QR code to regenerate
     * @return the file path of the newly generated QR code image
     */
    public String regenerateQRCode(String gemId) {
        // Delete the old QR code first if it exists
        deleteQRCode(gemId);

        // Generate a fresh QR code with the updated journey data
        System.out.println("💎 Regenerating QR code for Gem ID: " + gemId);
        return generateQRCode(gemId);
    }

    // ---------------------------------------------------------
    // Display helpers
    // ---------------------------------------------------------

    /**
     * Prints a summary of all QR code files currently saved
     * in the qrcodes output folder.
     * Used by administrators to see which gems have QR codes
     * and which ones still need to be generated.
     */
    public void displayQRCodeStatus() {
        List<String> allIds = trackingService.getAllGemIds();

        System.out.println("💎 QR Code Status for All Gems");
        System.out.println();

        int withQR    = 0;
        int withoutQR = 0;

        for (String gemId : allIds) {
            boolean exists = qrCodeExists(gemId);
            System.out.println("  Gem ID : " + gemId
                    + "  |  QR Code : " + (exists ? "EXISTS" : "NOT GENERATED"));
            if (exists) {
                withQR++;
            } else {
                withoutQR++;
            }
        }

        System.out.println();
        System.out.println("  Total gems          : " + allIds.size());
        System.out.println("  With QR code        : " + withQR);
        System.out.println("  Without QR code     : " + withoutQR);
    }

    /**
     * Prints the content that would be encoded into a gem's QR code
     * without actually generating the image file.
     * Used for testing and previewing the QR code content before
     * generating the actual image.
     *
     * @param gemId the ID of the gem to preview
     */
    public void previewQRContent(String gemId) {
        GemLinkedList list = trackingService.getGemList(gemId);
        if (list == null) {
            System.out.println("Gem not found: " + gemId);
            return;
        }

        System.out.println("💎 QR Code Content Preview for Gem ID: " + gemId);
        System.out.println();
        System.out.println(buildQRContent(list));
    }
}