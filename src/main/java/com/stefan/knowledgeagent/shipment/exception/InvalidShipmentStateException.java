package com.stefan.knowledgeagent.shipment.exception;

public class InvalidShipmentStateException extends RuntimeException {

    public InvalidShipmentStateException(String message) {
        super(message);
    }
}
