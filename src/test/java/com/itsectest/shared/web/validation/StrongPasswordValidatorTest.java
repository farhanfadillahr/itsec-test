package com.itsectest.shared.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = { "Str0ng#Pass", "Aa1!aaaa", "SuperAdmin#2026", "p@ssW0rd" })
    void acceptsPasswordsWithAllFourCharacterClasses(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Aa1!aaa",
            "alllowercase1!",
            "ALLUPPERCASE1!",
            "NoDigitsHere!",
            "NoSpecial123"
    })
    void rejectsWeakPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsAnythingLongerThanBcryptWillHash() {
        assertThat(validator.isValid("Aa1!" + "x".repeat(69), null)).isFalse();
        assertThat(validator.isValid("Aa1!" + "x".repeat(68), null)).isTrue();
    }

    @Test
    void leavesNullToTheNotBlankConstraint() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
