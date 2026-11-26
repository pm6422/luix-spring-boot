package com.luixtech.core.ntfy;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable ntfy notification payload published via ntfy's JSON API.
 */
public final class NtfyMessage {

    public static final int PRIORITY_MIN = 1;
    public static final int PRIORITY_LOW = 2;
    public static final int PRIORITY_DEFAULT = 3;
    public static final int PRIORITY_HIGH = 4;
    public static final int PRIORITY_URGENT = 5;

    private final String topic;
    private final String title;
    private final String message;
    private final Integer priority;
    private final List<String> tags;
    private final String icon;
    private final String click;
    private final List<NtfyAction> actions;

    private NtfyMessage(Builder builder) {
        if (builder.topic == null || builder.topic.isBlank()) {
            throw new IllegalArgumentException("ntfy topic must not be blank");
        }
        if (builder.priority != null && (builder.priority < PRIORITY_MIN || builder.priority > PRIORITY_URGENT)) {
            throw new IllegalArgumentException("ntfy priority must be between 1 and 5, got: " + builder.priority);
        }
        this.topic = builder.topic.trim();
        this.title = builder.title;
        this.message = builder.message;
        this.priority = builder.priority;
        this.tags = List.copyOf(builder.tags);
        this.icon = builder.icon;
        this.click = builder.click;
        this.actions = List.copyOf(builder.actions);
    }

    public static Builder builder(String topic) {
        return new Builder(topic);
    }

    public String topic() {
        return topic;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public Integer priority() {
        return priority;
    }

    public List<String> tags() {
        return tags;
    }

    public String icon() {
        return icon;
    }

    public String click() {
        return click;
    }

    public List<NtfyAction> actions() {
        return actions;
    }

    public static final class Builder {

        private final String topic;
        private String title;
        private String message;
        private Integer priority;
        private final List<String> tags = new ArrayList<>();
        private String icon;
        private String click;
        private final List<NtfyAction> actions = new ArrayList<>();

        private Builder(String topic) {
            this.topic = topic;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags.clear();
            if (tags != null) {
                for (String tag : tags) {
                    if (tag != null && !tag.isBlank()) {
                        this.tags.add(tag.trim());
                    }
                }
            }
            return this;
        }

        public Builder addTag(String tag) {
            if (tag != null && !tag.isBlank()) {
                this.tags.add(tag.trim());
            }
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder click(String click) {
            this.click = click;
            return this;
        }

        public Builder actions(List<NtfyAction> actions) {
            this.actions.clear();
            if (actions != null) {
                this.actions.addAll(actions);
            }
            return this;
        }

        public Builder addAction(NtfyAction action) {
            if (action != null) {
                this.actions.add(action);
            }
            return this;
        }

        public NtfyMessage build() {
            return new NtfyMessage(this);
        }
    }
}
