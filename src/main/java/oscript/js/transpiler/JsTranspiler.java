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
        JsEmitterVisitor emitter = new JsEmitterVisitor(pset);
        JsSourceBuilder builder = emitter.emitProgram(file);
        return "//# sourceURL="+name+"\n(function(){\n"+builder.constdef+"\nreturn ("+builder.toString()+")})()";  
    }
}
