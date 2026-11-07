package com.gadgetmart.notification.service;

import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fills {{placeholders}} of a stored template. Plain text only (no HTML), so there is nothing to escape. */
@Component
@RequiredArgsConstructor
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final TemplateRepository templates;

    public record Rendered(String subject, String body) {
    }

    public Optional<Rendered> render(String code, Channel channel, Map<String, String> vars) {
        return templates.findByCodeAndChannel(code, channel)
                .map(t -> new Rendered(fill(t.getSubjectTemplate(), vars), fill(t.getBodyTemplate(), vars)));
    }

    private String fill(String template, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
        }
        m.appendTail(out);
        return out.toString();
    }
}
