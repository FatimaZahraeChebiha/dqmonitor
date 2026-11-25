package com.dataquality.backend.ai;

/** Thrown when the configured LLM provider can't be reached or errors out. */
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
