package oscript.js.transpiler;

/**
 * Utility for producing readable JavaScript with consistent indentation. The
 * emitter relies on this helper to keep formatting predictable without adding
 * dependencies on a separate templating engine. The builder also tracks
 * generated locations for source map emission.
 */
final class JsSourceBuilder {

    private final StringBuilder out = new StringBuilder();
    private final SourceMapBuilder sourceMap;
    private SourceLocation pendingLocation;
    private int indent = 0;
    private int line = 0;
    private int column = 0;
    public String constdef = "";

    JsSourceBuilder() {
        this(null);
    }

    JsSourceBuilder(SourceMapBuilder sourceMap) {
        this.sourceMap = sourceMap;
    }

    JsSourceBuilder append(String text) {
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
        pendingLocation = location;
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
            } else {
                column++;
            }
        }
    }

    private void applyPendingLocation() {
        if (pendingLocation == null) {
            return;
        }
        sourceMap.addMapping(line, column, pendingLocation);
        pendingLocation = null;
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
