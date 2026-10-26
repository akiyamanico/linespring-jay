package com.linepyjay.model;

/**
 * Represents an in-progress WhatsApp number verification.
 * Maps the verification_codes dict in main.py / whatsappMention.py.
 */
public class VerificationEntry {
    private final String whatsappNumber;
    private final int    code;

    public VerificationEntry(String whatsappNumber, int code) {
        this.whatsappNumber = whatsappNumber;
        this.code = code;
    }

    public String getWhatsappNumber() { return whatsappNumber; }
    public int getCode()              { return code; }
}
