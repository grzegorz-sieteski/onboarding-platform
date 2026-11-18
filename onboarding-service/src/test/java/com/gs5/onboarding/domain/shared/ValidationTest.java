package com.gs5.onboarding.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationTest {

    @Test
    void buildsTheWholeWhenEveryPartPasses() {
        Validation<String> validation = mergingInto(" | ");
        var number = validation.check(Result.<Integer, String>success(1));
        var word = validation.check(Result.<String, String>success("two"));
        var another = validation.check(Result.<Integer, String>success(3));
        var flag = validation.check(Result.<Boolean, String>success(true));

        assertThat(validation.build(() -> number.value() + word.value() + another.value() + flag.value()))
                .isEqualTo(Result.success("1two3true"));
    }

    @Test
    void reportsASingleFailureAsItIs() {
        Validation<String> validation = mergingInto(" | ");
        validation.check(Result.<Integer, String>success(1));
        validation.check(Result.<Integer, String>failure("broken"));

        assertThat(validation.build(() -> "never")).isEqualTo(Result.failure("broken"));
    }

    @Test
    void reportsEveryFailureMergedInTheOrderOfTheParts() {
        Validation<String> validation = mergingInto(" | ");
        validation.check(Result.<Integer, String>failure("first"));
        validation.check(Result.<Integer, String>success(2));
        validation.check(Result.<Integer, String>failure("second"));
        validation.check(Result.<Integer, String>failure("third"));

        assertThat(validation.build(() -> "never")).isEqualTo(Result.failure("first | second | third"));
    }

    @Test
    void neverRunsTheFactoryWhenAPartFailed() {
        Validation<String> validation = mergingInto(" | ");
        validation.check(Result.<Integer, String>failure("broken"));
        var factoryRan = new AtomicBoolean();

        validation.build(() -> {
            factoryRan.set(true);
            return "built";
        });

        assertThat(factoryRan).isFalse();
    }

    private static Validation<String> mergingInto(String separator) {
        return Validation.of(errors -> String.join(separator, errors));
    }
}
