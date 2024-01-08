package com.cft.entities.ws;

public record SimpleWSUpdate<T>(UpdateOrigin origin, UpdateType type, T data) {
    public enum UpdateType {
        POST, PUT, DELETE
    }

    public enum UpdateOrigin {
        FIGHTERS, FIGHTS, EVENTS
    }
}
