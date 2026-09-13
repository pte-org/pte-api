package com.pte.scoring.vendor;

/**
 * Explicit provider-boundary failure. It propagates through the AI worker so
 * RabbitMQ retry/DLQ handling can mark the answer failed without inventing a
 * score.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
