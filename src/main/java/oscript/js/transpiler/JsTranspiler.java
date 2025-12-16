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

    public static String transpile(String name,Node file) {
        HashSet<String> pset = null;
    	if (OscriptHostImpl.compileSourceContextScriptParams != null) {
    		pset = new HashSet();
    		for (String s : OscriptHostImpl.compileSourceContextScriptParams.split(","))
    			pset.add(s);
    		OscriptHostImpl.compileSourceContextScriptParams=null;
    	}
        SourceMapBuilder programSourceMap = new SourceMapBuilder(name);
        JsEmitterVisitor emitter = new JsEmitterVisitor(pset, programSourceMap);
        JsSourceBuilder programBuilder = emitter.emitProgram(file);

        JsSourceBuilder finalBuilder = new JsSourceBuilder(new SourceMapBuilder(name));
        finalBuilder.line("//# sourceURL=" + name);
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

        String encodedMap = finalBuilder.getSourceMapBuilder().build(null);
        if (!encodedMap.isEmpty()) {
                finalBuilder.newline();
                finalBuilder.append("//# sourceMappingURL=data:application/json;base64," + encodedMap);
        }
        return finalBuilder.toString();
    }
}
