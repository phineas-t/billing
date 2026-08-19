package com.saas.billing.invoice;

public enum InvoiceStatus {

    DRAFT,
    OPEN,
    PAID,
    VOID,
    UNCOLLECTIBLE,
    /**
     * Local-only status — not a native Stripe invoice status.
     * Applied when invoice.payment_failed webhook fires to distinguish
     * payment-failed invoices from open ones awaiting first payment.
     */
    FAILED;

    public static InvoiceStatus fromStripe(String stripeStatus) {
        if (stripeStatus == null) return OPEN;
        return switch (stripeStatus.toLowerCase()) {
            case "draft"         -> DRAFT;
            case "open"          -> OPEN;
            case "paid"          -> PAID;
            case "void"          -> VOID;
            case "uncollectible" -> UNCOLLECTIBLE;
            default              -> OPEN;
        };
    }
}