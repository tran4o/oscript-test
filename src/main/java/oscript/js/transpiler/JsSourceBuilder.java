package oscript.js.transpiler;

/**
 * Utility for producing readable JavaScript with consistent indentation. The
 * emitter relies on this helper to keep formatting predictable without adding
 * dependencies on a separate templating engine.
 */
final class JsSourceBuilder {

    private final StringBuilder out = new StringBuilder();
    private int indent = 0;
    public String constdef="";
    
    JsSourceBuilder append(String text) {
        out.append(text);
        return this;
    }

    JsSourceBuilder newline() {
        out.append('\n');
        for (int i = 0; i < indent; i++) {
            out.append(' ');
            out.append(' ');
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

    @Override
    public String toString() {
        return out.toString();
    }
}