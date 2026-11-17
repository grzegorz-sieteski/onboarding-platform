package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.shared.Result;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A surname as used for blacklist matching. Matching is done on the normalized form, so that
 * "Kowalski", " KOWALSKI " and "Kowalskí" are the same person for screening purposes, and Polish "ł" matches "l".
 */
public record Surname(String value) {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Entry point for untrusted text: validates and strips. The constructor is for already valid values. */
    public static Result<Surname, ScreeningError> of(String text) {
        if (text == null || text.isBlank()) {
            return Result.failure(new InvalidValue("Surname must not be blank"));
        }
        return Result.success(new Surname(text.strip()));
    }

    public String normalized() {
        String lowerCase = value.strip().toLowerCase(Locale.ROOT).replace('ł', 'l');
        String withoutDiacritics = COMBINING_MARKS.matcher(Normalizer.normalize(lowerCase, Normalizer.Form.NFD))
                .replaceAll("");
        return WHITESPACE.matcher(withoutDiacritics).replaceAll(" ");
    }

    public boolean matches(Surname other) {
        return normalized().equals(other.normalized());
    }
}
