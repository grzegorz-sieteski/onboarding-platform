package com.gs5.screening.domain.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Validation engine for any number of independent parts. Every part is validated by its own rules (usually the
 * factory of a value object) and arrives as a {@link Result}. The engine collects the parts, and {@link #build}
 * creates the whole only when every one of them passed. Otherwise it reports all failures at once: a single error
 * as it is, several merged into one by the given function.
 * <pre>{@code
 * var validation = Validation.of(InvalidInput::new);
 * var surname = validation.check(Surname.of(surnameText));
 * var reason  = validation.check(validReason(reasonText));
 * return validation.build(() -> new BlacklistEntry(surname.value(), reason.value()));
 * }</pre>
 * One instance validates one input, inside one method: create it, check the parts, build.
 *
 * @param <E> error of a failed part
 */
public final class Validation<E> {

    private final Function<List<E>, E> merge;
    private final List<E> errors = new ArrayList<>();

    private Validation(Function<List<E>, E> merge) {
        this.merge = merge;
    }

    /** @param merge turns the errors of several failed parts into one error */
    public static <E> Validation<E> of(Function<List<E>, E> merge) {
        return new Validation<>(merge);
    }

    /**
     * Registers one part and returns a handle to its value. The value is there only if the part passed, so it is
     * read inside the factory given to {@link #build} - which runs only when every part passed.
     */
    public <T> Checked<T> check(Result<T, E> part) {
        return switch (part) {
            case Result.Success<T, E>(var value) -> new Checked<>(value);
            case Result.Failure<T, E>(var error) -> {
                errors.add(error);
                yield new Checked<>(null);
            }
        };
    }

    /** Creates the whole if every part passed; otherwise the factory never runs and the failures are returned. */
    public <R> Result<R, E> build(Supplier<? extends R> factory) {
        return switch (errors.size()) {
            case 0 -> Result.success(factory.get());
            case 1 -> Result.failure(errors.getFirst());
            default -> Result.failure(merge.apply(List.copyOf(errors)));
        };
    }

    /** Handle to the value of a checked part. */
    public record Checked<T>(T value) {
    }
}
