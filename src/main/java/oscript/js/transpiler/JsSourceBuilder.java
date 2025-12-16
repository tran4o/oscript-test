package oscript.js.transpiler;

/**
 * Utility for producing readable JavaScript with consistent indentation. The
 * emitter relies on this helper to keep formatting predictable without adding
 * dependencies on a separate templating engine. The builder also tracks
 * generated locations for source map emission.
 */
final class JsSourceBuilder {

    private static final java.util.Map<String, SourceMapBuilder> inlineMappings =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    private final StringBuilder out = new StringBuilder();
    private final SourceMapBuilder sourceMap;
    private final java.util.List<SourceLocation> pendingLocations = new java.util.ArrayList<>();
    private SourceLocation lastLocation;
    private int lastMappedLine = -1;
    private int lastMappedColumn = -1;
    private boolean mappingAddedForLine;
    private int indent = 0;
    private int line = 0;
    private int column = 0;
    public String constdef = "";

    static void registerInlineMapping(String text, SourceMapBuilder map) {
        if ((text != null) && (map != null)) {
            inlineMappings.put(text, map);
        }
    }

    JsSourceBuilder() {
        this(null);
    }

    JsSourceBuilder(SourceMapBuilder sourceMap) {
        this.sourceMap = sourceMap;
    }

    JsSourceBuilder append(String text) {
        SourceMapBuilder inline = inlineMappings.remove(text);
        if ((inline != null) && (sourceMap != null)) {
            sourceMap.merge(inline, line, column);
        }
        applyPendingLocation();
        out.append(text);
        trackPosition(text);
        return this;
    }

    JsSourceBuilder append(JsSourceBuilder other) {
        applyPendingLocation();
        int lineOffset = line;
        int columnOffset = column;
        out.append(other.out);
        if ((sourceMap != null) && (other.sourceMap != null)) {
            sourceMap.merge(other.sourceMap, lineOffset, columnOffset);
        }
        trackPosition(other.out);
        return this;
    }

    JsSourceBuilder newline() {
        applyPendingLocation();
        out.append('\n');
        line++;
        column = 0;
        mappingAddedForLine = false;
        carryForwardMapping();
        for (int i = 0; i < indent; i++) {
            out.append(' ');
            out.append(' ');
            column += 2;
        }
        return this;
    }

    JsSourceBuilder line(String text) {
        return append(text).newline();
    }

    void indent() {
        indent++;
    }

    void dedent() {
        indent = Math.max(0, indent - 1);
    }

    int indentLevel() {
        return indent;
    }

    void setIndent(int value) {
        indent = Math.max(0, value);
    }

    int position() {
        return out.length();
    }

    void insert(int position, String text) {
        out.insert(position, text);
    }

    void mark(SourceLocation location) {
        if ((location == null) || (sourceMap == null)) {
            return;
        }
        pendingLocations.add(location);
        lastLocation = location;
    }

    SourceMapBuilder getSourceMapBuilder() {
        return sourceMap;
    }

    private void trackPosition(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                line++;
                column = 0;
                mappingAddedForLine = false;
            } else {
                column++;
            }
        }
    }

    private void applyPendingLocation() {
        if (pendingLocations.isEmpty()) {
            return;
        }
        for (SourceLocation location : pendingLocations) {
            recordMapping(location);
        }
        pendingLocations.clear();
    }

    private void recordMapping(SourceLocation location) {
        if ((sourceMap == null) || (location == null)) {
            return;
        }
        if ((lastMappedLine == line) && (lastMappedColumn == column) && mappingAddedForLine) {
            return;
        }
        sourceMap.addMapping(line, column, location);
        lastMappedLine = line;
        lastMappedColumn = column;
        lastLocation = location;
        mappingAddedForLine = true;
    }

    private void carryForwardMapping() {
        if (!mappingAddedForLine && (lastLocation != null)) {
            recordMapping(lastLocation);
        }
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
