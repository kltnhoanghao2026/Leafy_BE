package com.leafy.notificationservice.utils;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Mustache-inspired template renderer.
 *
 * <p>Supports two constructs:
 * <ul>
 *   <li><b>Variable substitution:</b> {@code {{variableName}}} — replaced with the string
 *       value from the payload map. Missing variables render as empty string.</li>
 *   <li><b>Conditional blocks:</b> {@code {{#condition}}content{{/condition}}} — the content
 *       is included only if the payload value for {@code condition} is truthy
 *       (non-null, non-zero number, {@code true} boolean).</li>
 * </ul>
 *
 * <p>Port of CNM's {@code TemplateEngine} — stateless and thread-safe.
 *
 * <h3>Example</h3>
 * <pre>
 * template: "{{actorName}} đã bình luận{{#commentPreview}}: {{commentPreview}}{{/commentPreview}}"
 * payload:  { actorName: "Alice", commentPreview: "Great post!" }
 * result:   "Alice đã bình luận: Great post!"
 * </pre>
 */
@Component
public class TemplateEngine {

    private static final Pattern CONDITIONAL_PATTERN =
            Pattern.compile("\\{\\{#(.+?)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);

    public String render(String template, Map<String, Object> payload) {
        if (template == null) return "";
        if (payload == null) payload = Collections.emptyMap();

        // Step 1: evaluate conditional blocks
        Matcher matcher = CONDITIONAL_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String content = matcher.group(2);
            Object value = payload.get(key);
            boolean show = isTruthy(value);
            matcher.appendReplacement(sb, show ? Matcher.quoteReplacement(content) : "");
        }
        matcher.appendTail(sb);
        String result = sb.toString();

        // Step 2: substitute simple variables
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            result = result.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : ""
            );
        }

        return result;
    }

    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).longValue() > 0;
        return true;
    }
}
