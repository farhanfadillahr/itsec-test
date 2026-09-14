package com.itsectest.shared.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MINIMUM_LENGTH = 8;
    private static final int MAXIMUM_LENGTH = 72; // bcrypt only uses the first 72 bytes

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.length() < MINIMUM_LENGTH || value.length() > MAXIMUM_LENGTH) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char character : value.toCharArray()) {
            if (Character.isUpperCase(character)) {
                upper = true;
            } else if (Character.isLowerCase(character)) {
                lower = true;
            } else if (Character.isDigit(character)) {
                digit = true;
            } else {
                special = true;
            }
        }
        return upper && lower && digit && special;
    }
}
