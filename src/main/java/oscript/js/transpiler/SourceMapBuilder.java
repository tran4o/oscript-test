package oscript.js.transpiler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal source map encoder for the JavaScript transpiler. Only the generated
 * line/column and the originating source locations are recorded; names are
 * omitted.
 */
final class SourceMapBuilder {

    private final String generatedName;
    private final String sourceName;
    private final List<Mapping> mappings = new ArrayList<>();

    SourceMapBuilder(String generatedName, String sourceName) {
        this.generatedName = generatedName;
        this.sourceName = sourceName;
    }

    SourceMapBuilder(SourceMapBuilder other) {
        this(other.generatedName, other.sourceName);
    }

    String getSourceName() {
        return sourceName;
    }

    String getGeneratedName() {
        return generatedName;
    }

    void addMapping(int generatedLine, int generatedColumn, SourceLocation source) {
        mappings.add(new Mapping(generatedLine, generatedColumn, source.line, source.column));
    }

    void merge(SourceMapBuilder other, int lineOffset, int columnOffset) {
        for (Mapping mapping : other.mappings) {
            int adjustedLine = mapping.generatedLine + lineOffset;
            int adjustedColumn = mapping.generatedColumn;
            if (mapping.generatedLine == 0) {
                adjustedColumn += columnOffset;
            }
            mappings.add(new Mapping(adjustedLine, adjustedColumn, mapping.sourceLine, mapping.sourceColumn));
        }
    }

    String build(String sourcesContent) {
        if (mappings.isEmpty()) {
            return "";
        }
        mappings.sort(Comparator.comparingInt((Mapping m) -> m.generatedLine)
                .thenComparingInt(m -> m.generatedColumn));

        StringBuilder encoded = new StringBuilder();
        List<String> pendingSegments = new ArrayList<>();
        int currentGeneratedLine = 0;
        int lastGenColumn = 0;
        int lastSourceLine = 0;
        int lastSourceColumn = 0;

        for (Mapping mapping : mappings) {
            while (currentGeneratedLine < mapping.generatedLine) {
                appendLine(encoded, pendingSegments);
                encoded.append(';');
                pendingSegments.clear();
                currentGeneratedLine++;
                lastGenColumn = 0;
            }

            StringBuilder segment = new StringBuilder();
            segment.append(encodeVlq(mapping.generatedColumn - lastGenColumn));
            segment.append(encodeVlq(0)); // single source only
            segment.append(encodeVlq(mapping.sourceLine - lastSourceLine));
            segment.append(encodeVlq(mapping.sourceColumn - lastSourceColumn));

            pendingSegments.add(segment.toString());

            lastGenColumn = mapping.generatedColumn;
            lastSourceLine = mapping.sourceLine;
            lastSourceColumn = mapping.sourceColumn;
        }

        appendLine(encoded, pendingSegments);

        StringBuilder json = new StringBuilder();
        json.append('{')
                .append("\"version\":3,")
                .append("\"file\":\"").append(generatedName).append("\",")
                .append("\"sources\":[\"").append(sourceName).append("\"],")
                .append("\"sourcesContent\":[");
        if (sourcesContent != null) {
            json.append("\"").append(escapeForJson(sourcesContent)).append("\"");
        } else {
            json.append("null");
        }
        json.append("],")
                .append("\"names\":[],")
                .append("\"mappings\":\"").append(encoded).append("\"")
                .append('}');

        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeVlq(int value) {
        int vlq = toVlqSigned(value);
        StringBuilder sb = new StringBuilder();
        do {
            int digit = vlq & 31;
            vlq >>>= 5;
            if (vlq > 0) {
                digit |= 32;
            }
            sb.append(toBase64(digit));
        } while (vlq > 0);
        return sb.toString();
    }

    private static int toVlqSigned(int value) {
        return (value < 0) ? ((-value) << 1) + 1 : (value << 1);
    }

    private static void appendLine(StringBuilder encoded, List<String> segments) {
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                encoded.append(',');
            }
            encoded.append(segments.get(i));
        }
    }

    private static char toBase64(int value) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        return chars.charAt(value & 63);
    }

    private static String escapeForJson(String content) {
        StringBuilder escaped = new StringBuilder();
        for (char c : content.toCharArray()) {
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static final class Mapping {
        final int generatedLine;
        final int generatedColumn;
        final int sourceLine;
        final int sourceColumn;

        Mapping(int generatedLine, int generatedColumn, int sourceLine, int sourceColumn) {
            this.generatedLine = generatedLine;
            this.generatedColumn = generatedColumn;
            this.sourceLine = sourceLine;
            this.sourceColumn = sourceColumn;
        }
    }
}
