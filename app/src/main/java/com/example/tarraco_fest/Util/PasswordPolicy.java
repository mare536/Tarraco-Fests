package com.example.tarraco_fest.Util;

import com.example.tarraco_fest.R;

/**
 * Reglas minimas de seguridad para contrasenas en toda la app.
 * Se centraliza aqui para mantener el mismo criterio en registro y perfil.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
        // Utility class
    }

    public enum Result {
        OK,
        REQUIRED,
        TOO_SHORT,
        MISSING_UPPER,
        MISSING_LOWER,
        MISSING_DIGIT,
        MISSING_SPECIAL,
        HAS_SPACES
    }

    // Valida password con una politica base de seguridad.
    public static Result validate(String password) {
        if (password == null || password.trim().isEmpty()) return Result.REQUIRED;
        if (password.length() < MIN_LENGTH) return Result.TOO_SHORT;

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isWhitespace(c)) return Result.HAS_SPACES;
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }

        if (!hasUpper) return Result.MISSING_UPPER;
        if (!hasLower) return Result.MISSING_LOWER;
        if (!hasDigit) return Result.MISSING_DIGIT;
        if (!hasSpecial) return Result.MISSING_SPECIAL;
        return Result.OK;
    }

    // Mapea el resultado de validacion al string mostrado al usuario.
    public static int errorRes(Result result) {
        if (result == null) return R.string.password_policy_required;
        switch (result) {
            case REQUIRED:
                return R.string.password_policy_required;
            case TOO_SHORT:
                return R.string.password_policy_too_short;
            case MISSING_UPPER:
                return R.string.password_policy_need_upper;
            case MISSING_LOWER:
                return R.string.password_policy_need_lower;
            case MISSING_DIGIT:
                return R.string.password_policy_need_digit;
            case MISSING_SPECIAL:
                return R.string.password_policy_need_special;
            case HAS_SPACES:
                return R.string.password_policy_no_spaces;
            default:
                return R.string.password_policy_required;
        }
    }
}
