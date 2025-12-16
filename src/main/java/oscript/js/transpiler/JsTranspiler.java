package oscript.js.transpiler;

import java.util.HashSet;
import oscript.interpreter.InterpretedNodeEvaluator;
import oscript.syntaxtree.Node;

/**
 * Entry point for converting parsed Oscript code into JavaScript. The emitted
 * source wraps the program in a function that accepts {@code scope} and
 * {@code StackFrame} parameters, mirroring the calling convention used by
 * {@link InterpretedNodeEvaluator}.
 */
public final class JsTranspiler {

    private JsTranspiler() {
    }

    /**
     * Transpile an ObjectScript syntax tree to JavaScript without embedding the
     * original source text. Use {@link #transpile(String, String, Node)} when
     * the source content is available so it can be inlined in the source map.
     */
    public static String transpile(String name, Node file) {
        return transpile(name, null, file);
    }

    /**
     * Transpile an ObjectScript syntax tree to JavaScript and optionally embed
     * the original source code in the source map to keep virtual sources
     * debuggable in browsers that cannot resolve the source URL.
     */
    public static String transpile(String name, String originalSource, Node file) {
        HashSet<String> pset = null;
        if (OscriptHostImpl.compileSourceContextScriptParams != null) {
                pset = new HashSet();
                for (String s : OscriptHostImpl.compileSourceContextScriptParams.split(","))
                        pset.add(s);
                OscriptHostImpl.compileSourceContextScriptParams=null;
        }
        String generatedName = "vsc://" + name + ".generated.js";
        String sourceName = "vsc://" + name + ".source.os";
        SourceMapBuilder programSourceMap = new SourceMapBuilder(generatedName, sourceName);
        JsEmitterVisitor emitter = new JsEmitterVisitor(pset, programSourceMap);
        JsSourceBuilder programBuilder = emitter.emitProgram(file);

        JsSourceBuilder finalBuilder = new JsSourceBuilder(new SourceMapBuilder(generatedName, sourceName));
        finalBuilder.line("//# sourceURL=" + generatedName);
        finalBuilder.line("(function(){");
        finalBuilder.indent();
        if (!programBuilder.constdef.isEmpty()) {
                finalBuilder.append(programBuilder.constdef);
        }
        finalBuilder.append("return (");
        finalBuilder.append(programBuilder);
        finalBuilder.append(")");
        finalBuilder.newline();
        finalBuilder.dedent();
        finalBuilder.append("})()");

        String encodedMap = finalBuilder.getSourceMapBuilder().build(
                (originalSource != null) ? originalSource : "");
        if (!encodedMap.isEmpty()) {
                finalBuilder.newline();
                finalBuilder.append("//# sourceMappingURL=data:application/json;base64," + encodedMap);
        }
        return finalBuilder.toString();
    }
}
