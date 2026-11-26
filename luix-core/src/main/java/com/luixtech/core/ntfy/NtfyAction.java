package com.luixtech.core.ntfy;

/**
 * Interactive action button attached to an ntfy notification.
 *
 * @param action action type (e.g. {@code "view"})
 * @param label  button label shown to the user
 * @param url    target URL opened when clicked
 * @param clear  whether tapping the action clears the notification
 */
public record NtfyAction(String action, String label, String url, boolean clear) {

    public NtfyAction {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }

    /**
     * Creates a standard ntfy {@code view} action button that opens {@code url}.
     */
    public static NtfyAction view(String label, String url) {
        return new NtfyAction("view", label, url, false);
    }

    /**
     * Creates a standard ntfy {@code view} action button with explicit clear behavior.
     */
    public static NtfyAction view(String label, String url, boolean clear) {
        return new NtfyAction("view", label, url, clear);
    }
}
