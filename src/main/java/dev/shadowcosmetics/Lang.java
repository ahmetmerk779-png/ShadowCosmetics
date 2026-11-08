package dev.shadowcosmetics;

import net.minecraft.text.Text;

/** Translation helper. Strings live in assets/shadowcosmetics/lang/*.json. */
public final class Lang {

    private Lang() {
    }

    /** Translates a key in the player's current language (falls back to English). */
    public static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    /** Turns any exception into a short message in the player's language. */
    public static String describe(Throwable throwable) {
        if (throwable instanceof CosmeticException exception) {
            return tr(exception.key(), exception.args());
        }

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        message = message.replace('\n', ' ').trim();

        return CapeFormat.ellipsizeEnd(message, 90);
    }
}
