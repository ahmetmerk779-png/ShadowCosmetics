package dev.shadowcosmetics;

import java.io.IOException;

/**
 * An error whose message is a translation key. The UI translates it with
 * {@code Lang.tr(key(), args())} so players see errors in their own language.
 * No Minecraft dependencies, so it can be used from pure logic.
 */
public final class CosmeticException extends IOException {

    private final String key;
    private final transient Object[] args;

    public CosmeticException(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args == null ? new Object[0] : args;
    }

    public String key() {
        return key;
    }

    public Object[] args() {
        return args;
    }
}
