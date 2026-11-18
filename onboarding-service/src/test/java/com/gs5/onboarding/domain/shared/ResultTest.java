package com.gs5.onboarding.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void mapTransformsASuccessAndSkipsAFailure() {
        assertThat(Result.<Integer, String>success(2).map(n -> n * 10)).isEqualTo(Result.success(20));
        assertThat(Result.<Integer, String>failure("boom").map(n -> n * 10)).isEqualTo(Result.failure("boom"));
    }

    @Test
    void flatMapStopsAtTheFirstFailure() {
        List<String> steps = new ArrayList<>();

        Result<Integer, String> result = Result.<Integer, String>success(1)
                .flatMap(n -> {
                    steps.add("first");
                    return Result.<Integer, String>failure("first failed");
                })
                .flatMap(n -> {
                    steps.add("second");
                    return Result.success(n + 1);
                });

        assertThat(result).isEqualTo(Result.failure("first failed"));
        assertThat(steps).containsExactly("first");
    }

    @Test
    void mapErrorTranslatesOnlyTheError() {
        assertThat(Result.<Integer, String>failure("boom").mapError(String::length)).isEqualTo(Result.failure(4));
        assertThat(Result.<Integer, String>success(1).mapError(String::length)).isEqualTo(Result.success(1));
    }

    @Test
    void foldHandlesBothOutcomes() {
        assertThat(Result.<Integer, String>success(7).<String>fold(n -> "ok " + n, e -> "error " + e))
                .isEqualTo("ok 7");
        assertThat(Result.<Integer, String>failure("x").<String>fold(n -> "ok " + n, e -> "error " + e))
                .isEqualTo("error x");
    }

    @Test
    void liftsAnOptional() {
        assertThat(Result.<String, String>of(Optional.of("v"), () -> "missing")).isEqualTo(Result.success("v"));
        assertThat(Result.<String, String>of(Optional.empty(), () -> "missing")).isEqualTo(Result.failure("missing"));
    }
    @Test
    void peekFailureRunsOnlyForAFailure() {
        List<String> seen = new ArrayList<>();

        Result.<Integer, String>success(1).peekFailure(seen::add);
        Result.<Integer, String>failure("boom").peekFailure(seen::add);

        assertThat(seen).containsExactly("boom");
    }

    @Test
    void orElseThrowIsTheEdgeEscapeHatch() {
        assertThat(Result.<Integer, String>success(5).orElseThrow(IllegalStateException::new)).isEqualTo(5);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> Result.<Integer, String>failure("bad config").orElseThrow(IllegalStateException::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bad config");
    }
}
