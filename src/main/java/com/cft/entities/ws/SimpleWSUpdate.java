package com.cft.entities.ws;

public record SimpleWSUpdate<T>(UpdateType type, T data) {
    public enum UpdateType {
        POST, PUT, PUT_INDIRECT, DELETE;
    }
}
