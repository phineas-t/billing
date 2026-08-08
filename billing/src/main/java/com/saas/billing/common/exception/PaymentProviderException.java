package com.saas.billing.common.exception;

public class PaymentProviderException
        extends RuntimeException {

    private final String providerMessage;

    public PaymentProviderException(
            String message,
            String providerMessage,
            Throwable cause) {
        super(message, cause);
        this.providerMessage = providerMessage;
    }

    public PaymentProviderException(String message) {
        super(message);
        this.providerMessage = message;
    }

    public String getProviderMessage() {
        return providerMessage;
    }
}