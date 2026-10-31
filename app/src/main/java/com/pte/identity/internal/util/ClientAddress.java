package com.pte.identity.internal.util;

/**
 * Works out which address a request really came from. The edge proxy appends the address it saw to
 * {@code X-Forwarded-For}, so only the last entry is trustworthy: anything before it was written by the client.
 * With one trusted proxy in front, that last entry is the caller; a second hop would need this revisited.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String resolve(String forwardedFor, String socketAddress) {
        if (forwardedFor != null) {
            String[] entries = forwardedFor.split(",");
            for (int i = entries.length - 1; i >= 0; i--) {
                String entry = entries[i].trim();
                if (!entry.isEmpty()) {
                    return entry;
                }
            }
        }
        return socketAddress;
    }
}
