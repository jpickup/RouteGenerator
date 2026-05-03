package com.gpxroute;

/**
 * Discriminated union for operation results.
 * Use {@link #success(Object)} and {@link #failure(String)} factory methods to create instances.
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    record Success<T>(T value) implements Result<T> {}

    record Failure<T>(String message) implements Result<T> {}

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(String message) {
        return new Failure<>(message);
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default T getValue() {
        return ((Success<T>) this).value();
    }

    default String getError() {
        return ((Failure<T>) this).message();
    }
}
