package com.linepyjay.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/**
 * Renders a QR code as Unicode block characters directly to System.out.
 *
 * Uses the "half-block" technique:
 *   Each terminal row covers TWO QR rows using Unicode:
 *     '█'  (U+2588) = top black, bottom black
 *     '▀'  (U+2580) = top black, bottom white
 *     '▄'  (U+2584) = top white, bottom black
 *     ' '           = top white, bottom white
 *
 * This gives a compact, square-looking QR code even in terminals
 * where character height ≈ 2× character width.
 */
public class QrCodePrinter {

    private static final String RESET  = "\033[0m";
    private static final String BLACK  = "\033[40m";  // black background
    private static final String WHITE  = "\033[107m"; // bright white background

    /**
     * Print a QR code for the given text to stdout.
     *
     * @param text    The content to encode (e.g. the LINE callback URL)
     * @param margin  Quiet zone size in modules (LINE scanners need at least 1)
     */
    public static void print(String text, int margin) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0, 0,  // 0 = auto-size
                Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, margin,
                    EncodeHintType.CHARACTER_SET, "UTF-8"
                )
            );

            int width  = matrix.getWidth();
            int height = matrix.getHeight();

            StringBuilder sb = new StringBuilder();

            // Top border padding
            sb.append("\n");

            // Process two rows at a time using half-block characters
            for (int y = 0; y < height; y += 2) {
                sb.append("  "); // left margin
                for (int x = 0; x < width; x++) {
                    boolean top    = matrix.get(x, y);
                    boolean bottom = (y + 1 < height) && matrix.get(x, y + 1);

                    if (top && bottom)       sb.append("█"); // both black
                    else if (top)            sb.append("▀"); // top black only
                    else if (bottom)         sb.append("▄"); // bottom black only
                    else                     sb.append(" "); // both white
                }
                sb.append("\n");
            }

            sb.append("\n");
            System.out.println(sb);

        } catch (WriterException e) {
            // Fallback — just print the raw URL if QR rendering fails
            System.out.println("[QR render failed] " + e.getMessage());
            System.out.println(text);
        }
    }

    /** Convenience overload with default margin=2. */
    public static void print(String text) {
        print(text, 2);
    }
}