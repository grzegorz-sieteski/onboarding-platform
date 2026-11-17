package com.example.screening.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SurnameTest {

    @ParameterizedTest
    @CsvSource({
            "Kowalski,   kowalski",
            "'  KOWALSKI ', kowalski",
            "Michałowski, michalowski",
            "Żółć,       zolc",
            "Nowak-Wąs,  nowak-was",
    })
    void normalizesForMatching(String raw, String expected) {
        assertThat(new Surname(raw).normalized()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Michałowski, MICHALOWSKI", "Wąs, Was"})
    void matchesRegardlessOfCaseAndDiacritics(String first, String second) {
        assertThat(new Surname(first).matches(new Surname(second))).isTrue();
    }
}
