package com.gs5.onboarding.domain.shared;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Outcome of an operation that can fail. A failure is a value, not an exception: the compiler forces every caller
 * to handle both cases, usually with an exhaustive pattern-matching {@code switch}.
 * <pre>{@code
 * switch (commands.handle(command)) {
 *     case Result.Success(var customerId) -> ResponseEntity.ok(customerId);
 *     case Result.Failure(var error)      -> problemFor(error);
 * }
 * }</pre>
 * Exceptions are left for what the domain cannot recover from (a database or broker outage, a bug) and for the few
 * framework callbacks that only understand failure as an exception. There a {@code Result} is converted once, at
 * the edge, with {@link #orElseThrow(Function)}.
 *
 * @param <T> value of a successful outcome
 * @param <E> error of a failed outcome - by convention a sealed interface of records
 */
public sealed interface Result<T, E> permits Result.Success, Result.Failure {

    record Success<T, E>(T value) implements Result<T, E> {
    }

    record Failure<T, E>(E error) implements Result<T, E> {
    }

    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    /** Lifts an {@link Optional}: an empty one becomes the supplied error. */
    static <T, E> Result<T, E> of(Optional<T> optional, Supplier<? extends E> errorIfEmpty) {
        if (optional.isPresent()) {
            return success(optional.get());
        }
        return failure(errorIfEmpty.get());
    }

    default <R> Result<R, E> map(Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case Success<T, E>(var value) -> success(mapper.apply(value));
            case Failure<T, E>(var error) -> failure(error);
        };
    }

    default <R> Result<R, E> flatMap(Function<? super T, Result<R, E>> mapper) {
        return switch (this) {
            case Success<T, E>(var value) -> mapper.apply(value);
            case Failure<T, E>(var error) -> failure(error);
        };
    }

    default <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper) {
        return switch (this) {
            case Success<T, E>(var value) -> success(value);
            case Failure<T, E>(var error) -> failure(mapper.apply(error));
        };
    }

    default <R> R fold(Function<? super T, ? extends R> onSuccess, Function<? super E, ? extends R> onFailure) {
        return switch (this) {
            case Success<T, E>(var value) -> onSuccess.apply(value);
            case Failure<T, E>(var error) -> onFailure.apply(error);
        };
    }

    /** Side effect on the failure path (logging, metrics); the result is passed on unchanged. */
    default Result<T, E> peekFailure(Consumer<? super E> action) {
        if (this instanceof Failure<T, E>(var error)) {
            action.accept(error);
        }
        return this;
    }

    /**
     * Edge-only escape hatch: startup configuration and framework callbacks (a Kafka listener has to throw to get
     * its message retried or dead-lettered). Domain and application code never calls this.
     */
    default <X extends RuntimeException> T orElseThrow(Function<? super E, ? extends X> exceptionFactory) {
        return switch (this) {
            case Success<T, E>(var value) -> value;
            case Failure<T, E>(var error) -> throw exceptionFactory.apply(error);
        };
    }
}
