package com.example.screening.domain;

import com.example.screening.domain.shared.InvalidValueException;

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

    public Surname {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("Surname must not be blank");
        }
        value = value.strip();
    }

    public String normalized() {
        String lowerCase = value.toLowerCase(Locale.ROOT).replace('ł', 'l');
        String withoutDiacritics = COMBINING_MARKS.matcher(Normalizer.normalize(lowerCase, Normalizer.Form.NFD))
                .replaceAll("");
        return WHITESPACE.matcher(withoutDiacritics).replaceAll(" ");
    }

    public boolean matches(Surname other) {
        return normalized().equals(other.normalized());
    }
}
