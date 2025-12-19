package oscript.js.transpiler;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import oscript.OscriptInterpreter;
import oscript.data.Reference;
import oscript.syntaxtree.AdditiveExpression;
import oscript.syntaxtree.AssignmentExpression;
import oscript.syntaxtree.BreakStatement;
import oscript.syntaxtree.CollectionForLoopStatement;
import oscript.syntaxtree.ConditionalExpression;
import oscript.syntaxtree.ConditionalStatement;
import oscript.syntaxtree.ContinueStatement;
import oscript.syntaxtree.BitwiseAndExpression;
import oscript.syntaxtree.BitwiseOrExpression;
import oscript.syntaxtree.BitwiseXorExpression;
import oscript.syntaxtree.CastExpression;
import oscript.syntaxtree.EvaluationUnit;
import oscript.syntaxtree.ExpressionBlock;
import oscript.syntaxtree.ForLoopStatement;
import oscript.syntaxtree.RelationalExpression;
import oscript.syntaxtree.Arglist;
import oscript.syntaxtree.FunctionCallExpressionList;
import oscript.syntaxtree.FunctionCallExpressionListBody;
import oscript.syntaxtree.FunctionCallPrimaryPostfix;
import oscript.syntaxtree.FunctionDeclaration;
import oscript.syntaxtree.FunctionPrimaryPrefix;
import oscript.syntaxtree.IdentifierPrimaryPrefix;
import oscript.syntaxtree.Literal;
import oscript.syntaxtree.LogicalAndExpression;
import oscript.syntaxtree.LogicalOrExpression;
import oscript.syntaxtree.EqualityExpression;
import oscript.syntaxtree.MultiplicativeExpression;
import oscript.syntaxtree.ParenPrimaryPrefix;
import oscript.syntaxtree.ThisPrimaryPrefix;
import oscript.syntaxtree.ThrowBlock;
import oscript.syntaxtree.TryStatement;
import oscript.syntaxtree.SuperPrimaryPrefix;
import oscript.syntaxtree.CalleePrimaryPrefix;
import oscript.syntaxtree.ArrayDeclarationPrimaryPrefix;
import oscript.syntaxtree.Node;
import oscript.syntaxtree.NodeChoice;
import oscript.syntaxtree.NodeList;
import oscript.syntaxtree.NodeListInterface;
import oscript.syntaxtree.NodeListOptional;
import oscript.syntaxtree.NodeOptional;
import oscript.syntaxtree.NodeSequence;
import oscript.syntaxtree.NodeToken;
import oscript.syntaxtree.PreLoopStatement;
import oscript.syntaxtree.PrimaryExpression;
import oscript.syntaxtree.PrimaryExpressionNotFunction;
import oscript.syntaxtree.PrimaryExpressionWithTrailingFxnCallExpList;
import oscript.syntaxtree.PrimaryPostfix;
import oscript.syntaxtree.PrimaryPostfixWithTrailingFxnCallExpList;
import oscript.syntaxtree.PrimaryPrefix;
import oscript.syntaxtree.PrimaryPrefixNotFunction;
import oscript.syntaxtree.Program;
import oscript.syntaxtree.ProgramFile;
import oscript.syntaxtree.PropertyIdentifierPrimaryPostfix;
import oscript.syntaxtree.AllocationExpression;
import oscript.syntaxtree.ReturnStatement;
import oscript.syntaxtree.ScopeBlock;
import oscript.syntaxtree.ShiftExpression;
import oscript.syntaxtree.PostfixExpression;
import oscript.syntaxtree.TypeExpression;
import oscript.syntaxtree.UnaryExpression;
import oscript.syntaxtree.VariableDeclaration;
import oscript.syntaxtree.VariableDeclarationBlock;
import oscript.syntaxtree.WhileLoopStatement;
import oscript.translator.CollectionForLoopStatementTranslator;
import oscript.translator.ForLoopStatementTranslator;
import oscript.translator.FunctionDeclarationTranslator;
import oscript.visitor.ObjectDepthFirst;

public final class JsEmitterVisitor extends ObjectDepthFirst {
	
	private static final HashSet<String> oglobs = new HashSet();
	//------------------------------------------------------------
	private static final String pSavedScope = "ŝᵽ"; // var name
	private static final String pJSExcpt    = "ɇẋ"; // var name
	private static final String pCONST = "ȼ";	// lower case
	private static final String pSYMB  = "š";	// lower case
	private static final String pNUMB  = "ȵ";	// lower case
	private static final String sScope = "Ŝ";	// scope 
	private static final String sSf    = "Ḟ";	// stackframe	
	private static final String fAEMTY  = "ȺɆ"; // arr0() value
	//------------------------------------------------------------
	private static final String fPLUS   = "Ᵽ",   tPLUS   = "PLUS";
	private static final String fMINUS  = "Ɱ",   tMINUS  = "MINUS";
	private static final String fREMAIN = "Ɍ",   tREMAIN = "REMAIN";
	private static final String fUPLUS  = "ƱⱣ",  tUPLUS  = "UPLUS";
	private static final String fUMINUS = "ƱⱮ",  tUMINUS = "UMINUS";

	private static final String fCALL   = "Ȼ",   tCALL   = "CALL";
	private static final String fSYMB   = "Š",   tSYMB   = "SYMB";
	private static final String fSTR    = "ŠŦ",  tSTR    = "STR";
	private static final String fNUME   = "ȠɆ",  tNUME   = "NUME";
	private static final String fNUMF   = "ȠƑ",  tNUMF   = "NUMF";

	private static final String fCAST   = "ȻŠ",  tCAST   = "CAST";
	private static final String fTRUE   = "Ŧ",   tTRUE   = "TRUE";
	private static final String fFALSE  = "Ƒ",   tFALSE  = "FALSE";
	private static final String fNULL   = "Ƞ",   tNULL   = "NULL";
	private static final String fASGN   = "Ⱥ",   tASGN   = "ASGN";
	private static final String fBOOL   = "Ƀ",   tBOOL   = "BOOL";
	private static final String fMEMB   = "ⱮɃ",  tMEMB   = "MEMB";
	private static final String fEL     = "Ɇ",   tEL     = "EL";
	private static final String fCONSTR = "ȻȠ",  tCONSTR = "CONSTR";
	private static final String fUNDEF  = "ƱĐ",  tUNDEF  = "UNDEF";

	private static final String fNREF = "ȵR",    tNREF = "NREF";

	private static final String fVAR    = "Ṽ",   tVAR    = "VAR";
	private static final String fBITA   = "ɃŦ",  tBITA   = "BITA";
	private static final String fMUL    = "ⱮŁ",  tMUL    = "MUL";
	private static final String fDIV    = "ĐṼ",  tDIV    = "DIV";
	private static final String fSHL    = "ŠŁ",  tSHL    = "SHL";
	private static final String fSHR    = "ŠɌ",  tSHR    = "SHR";
	private static final String fSHRU   = "ŠƱ",  tSHRU   = "SHRU";
	private static final String fBOR    = "ɃØ",  tBOR    = "BOR";
	private static final String fBXOR   = "ɃẊ",  tBXOR   = "BXOR";

	private static final String fEQ     = "ɆɊ",  tEQ     = "EQ";
	private static final String fNEQ    = "ȠɊ",  tNEQ    = "NEQ";   
	private static final String fLT     = "ŁŦ",  tLT     = "LT";
	private static final String fGT     = "ǤŦ",  tGT     = "GT";
	private static final String fGTE    = "ǤɆ",  tGTE    = "GTE";
	private static final String fLEQ    = "ŁɆ",  tLEQ    = "LEQ";
	private static final String fIOF    = "ƗØ",  tIOF    = "IOF";

	private static final String fINC    = "Ɨ",   tINC    = "INC";
	private static final String fDEC    = "Đ",   tDEC    = "DEC";
	private static final String fBITNOT = "ɃȠ",  tBITNOT = "BNOT";  
	private static final String fNOT    = "ȠŦ",  tNOT    = "NOT";
	private static final String fPINC   = "ⱣƗ",  tPINC   = "PINC";
	private static final String fPDEC   = "ⱣĐ",  tPDEC   = "PDEC";

	private static final String fFNCWRP = "ƑŴ",  tFNCWRP = "FNCWRP";
	private static final String fLKP    = "ŁƘ",  tLKP    = "LKP";
	private static final String fTHS    = "ŦŠ",  tTHS    = "THS";
	private static final String fSUPR   = "ŠⱣ",  tSUPR   = "SUPR";
	private static final String fCALE   = "ȻŁ",  tCALE   = "CALE";

	private static final String fARR0   = "ȺØ",  tARR0   = "ARR0";
	private static final String fARR    = "ȺɌ",  tARR    = "ARR";

	private static final String fTHROW  = "ŦŴ",  tTHROW  = "THROW";
	private static final String fWRPSCP = "ŴŠ",  tWRPSCP = "WRPSCP";
	private static final String fWRPEXP = "ŴẊ",  tWRPEXP = "WRPEXP";
	//---------------------------------------------------------------------
	
	// STATIC CONSTANTS | DEFINED OUTSIDE ONCE (in the script factory)
	// RETURN eval factory with assigned parent scope
	private static String _fsrc;
	private static void putc(StringBuilder sb,String from,String to) {
		sb.append("const "+from+"=_."+to+";\n");
	}
	public static String getFactorySrc() {
		if (_fsrc == null) {
			StringBuilder sb = new StringBuilder(
					"const TOSTR=_.TOSTR;\n"+   // debug helper
					"const "+fUNDEF+"=_."+tUNDEF+"();\n"+
					"const "+fNULL+"=_."+tNULL+"();\n"+
					"const "+fTRUE+"=_."+tTRUE+"();\n"+
					"const "+fFALSE+"=_."+tFALSE+"();\n"+
					"const "+fAEMTY+"=_."+tARR0+"();\n"
			);
			// GLOBALS
			if (oglobs.isEmpty()) {
				for (Object m : OscriptInterpreter.getGlobalScope().memberSet()) {
					String s = m.toString();
					oglobs.add(s);
					sb.append("const "+s+"=_.GLOB(\""+s+"\");\n");
				}
			}
			putc(sb,fPLUS,   tPLUS);
			putc(sb,fMINUS,  tMINUS);
			putc(sb,fREMAIN, tREMAIN);
			putc(sb,fUPLUS,  tUPLUS);
			putc(sb,fUMINUS, tUMINUS);

			putc(sb,fCALL,   tCALL);
			putc(sb,fSYMB,   tSYMB);
			putc(sb,fSTR,    tSTR);
			putc(sb,fNUME,   tNUME);
			putc(sb,fNUMF,   tNUMF);

			putc(sb,fCAST,   tCAST);
			putc(sb,fASGN,   tASGN);
			putc(sb,fBOOL,   tBOOL);
			putc(sb,fMEMB,   tMEMB);
			putc(sb,fEL,     tEL);
			putc(sb,fCONSTR, tCONSTR);

			putc(sb,fVAR,    tVAR);
			putc(sb,fBITA,   tBITA);
			putc(sb,fMUL,    tMUL);
			putc(sb,fDIV,    tDIV);
			putc(sb,fSHL,    tSHL);
			putc(sb,fSHR,    tSHR);
			putc(sb,fSHRU,   tSHRU);
			putc(sb,fBOR,    tBOR);
			putc(sb,fBXOR,   tBXOR);

			putc(sb,fEQ,     tEQ);
			putc(sb,fNEQ,    tNEQ);
			putc(sb,fLT,     tLT);
			putc(sb,fGT,     tGT);
			putc(sb,fGTE,    tGTE);
			putc(sb,fLEQ,    tLEQ);
			putc(sb,fIOF,    tIOF);

			putc(sb,fINC,    tINC);
			putc(sb,fDEC,    tDEC);
			putc(sb,fBITNOT, tBITNOT);
			putc(sb,fNOT,    tNOT);
			putc(sb,fPINC,   tPINC);
			putc(sb,fPDEC,   tPDEC);

			putc(sb,fFNCWRP, tFNCWRP);
			putc(sb,fLKP,    tLKP);
			putc(sb,fTHS,    tTHS);
			putc(sb,fSUPR,   tSUPR);
			putc(sb,fCALE,   tCALE);

			putc(sb,fARR0,   tARR0);
			putc(sb,fARR,    tARR);

			putc(sb,fTHROW,  tTHROW);
			putc(sb,fWRPSCP, tWRPSCP);
			putc(sb,fWRPEXP, tWRPEXP);
			
			putc(sb,fNREF, tNREF);

			sb.append("return (function(src) { return eval(src) });");
			_fsrc=sb.toString();
		}
		return _fsrc;
	}

	private static final class ConstantPool {
		int stringCounter;
		Map<String, String> stringNameMap;
		Map<String, String> symbolNameMap;
		Map<String, String> exactNumberNameMap;
		Map<String, String> inexactNumberNameMap;
	}

        private final JsSourceBuilder out;
        private final LinkedList<String> constants;
        private final LinkedList<String> fncParamVars = new LinkedList();

        private final ConstantPool pool;
        private final HashMap<String,String> declaredNames = new HashMap();
        private final Set<String> fncParams; // optional
        private String lastExpression = fUNDEF;

        JsEmitterVisitor(Set<String> optionalNamesSkipVarToken, SourceMapBuilder sourceMapBuilder) {
                this(new JsSourceBuilder(sourceMapBuilder), new LinkedList(), new ConstantPool(),optionalNamesSkipVarToken);
        }

        private JsEmitterVisitor(JsSourceBuilder out, LinkedList<String> constants, ConstantPool pool,Set<String> optionalNamesSkipVarToken) {
                this.out = out;
                this.constants = constants;
                this.pool = pool;
                this.fncParams = optionalNamesSkipVarToken;
        }

        private void map(Object node) {
                out.mark(SourceLocation.fromNode(node));
        }
 
	JsSourceBuilder emitProgram(Node file) {
		int hpos;
		out.line("(function("+sScope+","+sSf+"){");
		out.indent();
		hpos = out.position();
		file.accept(this, null);
		//----------------------------------------------------------------------
		if (!constants.isEmpty()) {
			StringBuilder sb = new StringBuilder("\nconst ");
			int p=0;
			for (String c : constants) {
				if (p != 0)
					sb.append(",");
				sb.append(c);
				if ((++p)%10 == 0)
					sb.append("\n\t");
			}
			sb.append(";\n\n");
			out.constdef=sb.toString();
		}
		if (!fncParamVars.isEmpty()) 
			out.insert(hpos, "var "+String.join(",",fncParamVars)+";");
		//----------------------------------------------------------------------
		out.dedent();
		out.line("})");
		return out;
	}
	
        private String emitFunctionExpression(FunctionPrimaryPrefix n) {
                boolean sHasVargs[] = new boolean[1];
                LinkedHashMap<String,String> params = collectArgNames(n.f2,sHasVargs);
                //------------------------------------------------------------------------------------------------
                SourceMapBuilder childMap = out.getSourceMapBuilder() == null ? null : new SourceMapBuilder(out.getSourceMapBuilder());
                JsEmitterVisitor fn = new JsEmitterVisitor(new JsSourceBuilder(childMap), constants, pool,params.keySet());
                fn.declaredNames.putAll(declaredNames);
                fn.declaredNames.putAll(params);
                String pstr = String.join(",", params.values());
		JsSourceBuilder builder = fn.out;
		builder.append(fFNCWRP+"("+sScope+","+(sHasVargs[0] ? -params.size():params.size())+",function("+sScope);
		if (!pstr.isEmpty()) {
			builder.append(",");
			builder.append(pstr);
		}
		builder.append("){");
		builder.indent();
		int hpos = out.position();
		n.f6.accept(fn, null);
		if (!fncParamVars.isEmpty()) 
			out.insert(hpos, "var "+String.join(",",fncParamVars)+";");
                builder.dedent();
                builder.append("})");
                String generated = builder.toString();
                JsSourceBuilder.registerInlineMapping(generated, childMap);
                return generated;
        }


	@Override
	public Object visit(ProgramFile n, Object argu) {
                map(n);
		n.f1.accept(this, argu);
		return null;
	}

	@Override
	public Object visit(Program n, Object argu) {
                map(n);
		n.f0.accept(this, argu);
		return null;
	}

	@Override
	public Object visit(oscript.syntaxtree.Expression n, Object argu) {
                map(n);
		if (argu == null) {
			String expr = (String) n.f0.accept(this, Boolean.TRUE);
			out.line(expr + ";");
			lastExpression = expr;
			return null;
		}
		return n.f0.accept(this, argu);
	}

	@Override
	public Object visit(NodeList n, Object argu) {
                map(n);
		for (Enumeration e = n.elements(); e.hasMoreElements();) {
			((Node) e.nextElement()).accept(this, argu);
		}
		return null;
	}

	@Override
	public Object visit(NodeListOptional n, Object argu) {
                map(n);
		if (n.present()) {
			for (Enumeration e = n.elements(); e.hasMoreElements();) {
				((Node) e.nextElement()).accept(this, argu);
			}
		}
		return null;
	}

	@Override
	public Object visit(EvaluationUnit n, Object argu) {
                map(n);
		n.f0.accept(this, argu);
		return null;
	}

	@Override
	public Object visit(FunctionDeclaration n, Object argu) {
                map(n);
		return FunctionDeclarationTranslator.translate(n).accept(this, argu);
	}

	@Override
	public Object visit(ScopeBlock n, Object argu) {
                map(n);
		out.line("{");
		out.indent();
		n.f1.accept(this, argu);
		out.dedent();
		out.line("}");
		return null;
	}

	@Override
	public Object visit(VariableDeclarationBlock n, Object argu) {
                map(n);
		n.f0.accept(this, argu);
		return null;
	}

	@Override
	public Object visit(PreLoopStatement n, Object argu) {
                map(n);
		return n.f0.accept(this, argu);
	}

	@Override
	public Object visit(ForLoopStatement n, Object argu) {
                map(n);
		return ForLoopStatementTranslator.translate(n).accept(this, argu);
	}

	@Override
	public Object visit(CollectionForLoopStatement n, Object argu) {
                map(n);
		return CollectionForLoopStatementTranslator.translate(n).accept(this, argu);
	}
	
	 @Override
    public Object visit(TryStatement n, Object argu) {
                map(n);
		// SKIP n.f2.size() > 0 : TYPED CATCH NOT  SUPPORTED!
        boolean hasCatch = /*n.f2.size() > 0 || */n.f3.present();
        if (hasCatch) {
            out.line("try{");
            out.indent();
            n.f1.accept(this, argu);
            out.dedent();
            out.line("} catch("+pJSExcpt+"){");
            out.indent();
            //for (int i = 0; i < n.f2.size(); i++) emitTypedCatch((NodeSequence) n.f2.elementAt(i), argu);
            emitDefaultCatch((NodeSequence) n.f3.node, argu);
            out.dedent();
            out.line("}");
        } else {
            out.line("try{");
            out.indent();
            n.f1.accept(this, argu);
            out.dedent();
            out.line("}");
        }
        if (n.f4.present()) {
            out.line("finally{");
            out.indent();
            ((EvaluationUnit) ((NodeSequence) n.f4.node).elementAt(1)).accept(this, argu);
            out.dedent();
            out.line("}");
        }
        return null;
    }
	 

    @Override
    public Object visit(ThrowBlock n, Object argu) {
                map(n);
        String expr = emitExpression(n.f1);
        out.line(fTHROW+"(" + expr + ");");
        return null;
    }
    
    /*private void emitTypedCatch(NodeSequence seq, Object argu) {
        String typeExpr = emitExpression((Node) seq.elementAt(2));
        String name = ((NodeToken) seq.elementAt(3)).tokenImage;
        EvaluationUnit body = (EvaluationUnit) seq.elementAt(5);
        emitCatchBody(name, typeExpr, body, argu);
    }*/

    private void emitDefaultCatch(NodeSequence seq, Object argu) {
        String name = ((NodeToken) seq.elementAt(2)).tokenImage;
        EvaluationUnit body = (EvaluationUnit) seq.elementAt(4);
        emitCatchBody(name, null, body, argu);
    }

    private void emitCatchBody(String name, String typeExpr, EvaluationUnit body, Object argu) {
        out.line("const "+pSavedScope+" = "+sScope+";");
        out.line(sScope+" = "+fWRPSCP+"("+sScope+");"); 
        String ename = encodeName(name);
        out.line("const "+ename+" = "+fVAR+"("+sScope+","+symbolId(name)+",0x2000000);"); // Reference.ATTR_PROTECTED
        out.line(assign(ename, fWRPEXP+"("+pJSExcpt+")")+";"); // e.val ?!
        String oename = declaredNames.get(name);
        declaredNames.put(name,ename);
        out.line("try{");
        out.indent();
        body.accept(this, argu);
        out.dedent();
        out.line("}finally{");
        out.indent();
        out.line(sScope+" = "+pSavedScope);
        out.dedent();
        out.line("}");
        if (oename == null) 
        	declaredNames.remove(name);
        else
        	declaredNames.put(name,oename);
    }


	@Override
	public Object visit(IdentifierPrimaryPrefix n, Object argu) {
                map(n);
		String name = n.f0.tokenImage;
		String ename = declaredNames.get(name);
		if (ename == null && fncParams != null && fncParams.contains(name)) {
			// using function parameter, cache as variable (fixes also issue with redefinition as variable)
			ename = encodeName(name);
			declaredNames.put(name,ename);
			fncParamVars.add(ename+"="+fLKP+"("+sScope+", " + symbolId(name) + ")");
		} else {
			if (ename == null && oglobs.contains(name))
				ename = name;
			if (ename != null)
				return ename;
		}
		return fLKP+"("+sScope+", " + symbolId(name) + ")";
	}

	@Override
	public Object visit(VariableDeclaration n, Object argu) {
                map(n);
		int permissions = getPermissions(n.f0, Reference.ATTR_PROTECTED);
		String name = n.f2.tokenImage;
		String ename = encodeName(name);
		boolean isitr = "$itr$".equals(name);
		if (!isitr && fncParams != null && fncParams.contains(name)) {
			// using function parameter, cache as variable (fixes also issue with redefinition as variable)
			fncParamVars.add(ename+"="+fLKP+"("+sScope+", " + symbolId(name) + ")");
		}
		//-----------------------------------------------------------------------
		declaredNames.put(name,ename);
		if (isitr) {
			// special iterator, can not be wrapped into scope 
			if (n.f3.present()) {
				NodeSequence seq = (NodeSequence) n.f3.node;
				String expr = emitExpression((Node) seq.elementAt(1));
				out.line("let "+ename+"="+fNREF+"("+expr+");");
			} else {
				out.line("let "+ename+"="+fNREF+"("+fNULL+");");
			}
		} else {
			// var instead of const, redefinition allowed (oscript)
			//out.line("var " +ename+" = "+fVAR+"("+sScope+", " + symbolId(name) + ",0x" + Integer.toString(permissions,16) + ");");
			if ((permissions & Reference.ATTR_PUBLIC) != 0) {
				// public, forced to create scope member
				out.line("var " +ename+" = "+fVAR+"("+sScope+", " + symbolId(name) + ",0x" + Integer.toString(permissions,16) + ");");
				if (n.f3.present()) {
					NodeSequence seq = (NodeSequence) n.f3.node;
					String expr = emitExpression((Node) seq.elementAt(1));
					out.line(assign(ename, expr) + ";");
				}
			} else {
				// protected, only this source 
				if (n.f3.present()) {
					NodeSequence seq = (NodeSequence) n.f3.node;
					String expr = emitExpression((Node) seq.elementAt(1));
					out.line("var "+ename+"="+fNREF+"("+expr+");"); 
				} else {
					out.line("var "+ename+"="+fNREF+"("+fNULL+");"); 
					
				}

			}
		}
		
		return null;
	}

	@Override
	public Object visit(ExpressionBlock n, Object argu) {
                map(n);
		String expr = emitExpression(n.f0);
		out.line(expr + ";");
		lastExpression = expr;
		return null;
	}

	@Override
	public Object visit(ReturnStatement n, Object argu) {
                map(n);
		if (n.f1.present()) {
			String expr = emitExpression((Node) n.f1.node);
			out.line("return " + expr + ";");
		} else {
			out.line("return "+fUNDEF+";");
		}
		return null;
	}

	@Override
	public Object visit(ConditionalStatement n, Object argu) {
                map(n);
		String cond = emitExpression(n.f2);
		emitConditionalArm("if(" + castToBooleanSoft(cond) + ")", n.f4, argu);
		if (n.f5.present()) {
			NodeSequence seq = (NodeSequence) n.f5.node;
			emitConditionalArm("else", (EvaluationUnit) seq.elementAt(1), argu);
		}
		return null;
	}

	private void emitConditionalArm(String head, EvaluationUnit body, Object argu) {
		boolean isScopeBlock = body.f0.choice instanceof ScopeBlock;
		if (isScopeBlock) {
			out.line(head);
			body.accept(this, argu);
			return;
		}
		String prefix = head.isEmpty() ? "{" : head + " {";
		out.line(prefix);
		out.indent();
		body.accept(this, argu);
		out.dedent();
		out.line("}");
	}

	@Override
	public Object visit(WhileLoopStatement n, Object argu) {
                map(n);
		String cond = emitExpression(n.f2);
		out.line("while(" + castToBooleanSoft(cond) + "){");
		out.indent();
		n.f4.accept(this, argu);
		out.dedent();
		out.line("}");
		return null;
	}

	@Override
	public Object visit(BreakStatement n, Object argu) {
                map(n);
		out.line("break;");
		return null;
	}

	@Override
	public Object visit(ContinueStatement n, Object argu) {
                map(n);
		out.line("continue;");
		return null;
	}

	private String emitExpression(Node node) {
		Object res = node.accept(this, Boolean.TRUE);
		if (res instanceof String) {
			lastExpression = (String) res;
			return lastExpression;
		}
		return lastExpression;
	}

	@Override
	public Object visit(AssignmentExpression n, Object argu) {
                map(n);
		if (n.f1.size() == 0)
			return emitExpression(n.f0);
		int last = n.f1.size() - 1;
		NodeSequence seq = (NodeSequence) n.f1.elementAt(last);
		String val = emitExpression((Node) seq.elementAt(1));
		for (int i = last; i >= 0; i--) {
			seq = (NodeSequence) n.f1.elementAt(i);
			NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
			String left = (i == 0) ? emitExpression(n.f0)
					: emitExpression((Node) ((NodeSequence) n.f1.elementAt(i - 1)).elementAt(1));
			val = emitAssignment(op.kind, left, val);
		}
		return val;
	}

	private String emitAssignment(int op, String left, String right) {
		switch (op) {
		case oscript.parser.OscriptParserConstants.ASSIGN:
			return assign(left, right);
		case oscript.parser.OscriptParserConstants.PLUSASSIGN:
			return assign(left, bop(fPLUS, left, right));
		case oscript.parser.OscriptParserConstants.MINUSASSIGN:
			return assign(left, bop(fMINUS, left, right));
		case oscript.parser.OscriptParserConstants.STARASSIGN:
			return assign(left, bop(fMUL, left, right));
		case oscript.parser.OscriptParserConstants.SLASHASSIGN:
			return assign(left, bop(fDIV, left, right));
		case oscript.parser.OscriptParserConstants.REMASSIGN:
			return assign(left, bop(fREMAIN, left, right));
		case oscript.parser.OscriptParserConstants.ANDASSIGN:
			return assign(left, bop(fBITA, left, right));
		case oscript.parser.OscriptParserConstants.ORASSIGN:
			return assign(left, bop(fBOR, left, right));
		case oscript.parser.OscriptParserConstants.XORASSIGN:
			return assign(left, bop(fBXOR, left, right));
		case oscript.parser.OscriptParserConstants.LSHIFTASSIGN:
			return assign(left, bop(fSHL, left, right));
		case oscript.parser.OscriptParserConstants.RSIGNEDSHIFTASSIGN:
			return assign(left, bop(fSHR, left, right));
		case oscript.parser.OscriptParserConstants.RUNSIGNEDSHIFTASSIGN:
			return assign(left, bop(fSHRU, left, right));
		default:
			return assign(left, fUNDEF);
		}
	}

	@Override
	public Object visit(ShiftExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
			String rhs = emitExpression((Node) seq.elementAt(1));
			switch (op.tokenImage) {
			case "<<":
				val = bop(fSHL, val, rhs);
				break;
			case ">>":
				val = bop(fSHR, val, rhs);
				break;
			case ">>>":
				val = bop(fSHRU, val, rhs);
				break;
			default:
				val = fUNDEF;
				break;
			}
		}
		return val;
	}
	
	@Override
	public Object visit(ConditionalExpression n, Object argu) {
                map(n);
		String left = emitExpression(n.f0);
		if (n.f1.present()) {
			NodeListInterface list = (NodeListInterface) n.f1.node;
			String t = emitExpression((Node) list.elementAt(1));
			String f = emitExpression((Node) list.elementAt(3));
			return "(" + castToBooleanSoft(left) + " ? " + t + " : " + f + ")";
		}
		return left;
	}

	@Override
	public Object visit(LogicalOrExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			String rhs = emitExpression((Node) seq.elementAt(1));
			val = "(" + castToBooleanSoft(val) + " ? " + val + " : " + rhs + ")";
		}
		return val;
	}

	@Override
	public Object visit(LogicalAndExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			String rhs = emitExpression((Node) seq.elementAt(1));
			val = "(" + castToBooleanSoft(val) + " ? " + rhs + " : " + val + ")";
		}
		return val;
	}

	@Override
	public Object visit(BitwiseOrExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			String rhs = emitExpression((Node) seq.elementAt(1));
			val = bop(fBOR, val, rhs);
		}
		return val;
	}

	@Override
	public Object visit(BitwiseXorExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			String rhs = emitExpression((Node) seq.elementAt(1));
			val = bop(fBXOR, val, rhs);
		}
		return val;
	}

	@Override
	public Object visit(BitwiseAndExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			String rhs = emitExpression((Node) seq.elementAt(1));
			val = bop(fBITA, val, rhs);
		}
		return val;
	}

	@Override
	public Object visit(EqualityExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
			String rhs = emitExpression((Node) seq.elementAt(1));
			switch (op.tokenImage) {
			case "==":
				val = bop(fEQ, val, rhs);
				break;
			case "!=":
				val = bop(fNEQ, val, rhs);
				break;
			default:
				val = fUNDEF;
				break;
			}
		}
		return val;
	}

	@Override
	public Object visit(RelationalExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
			String rhs = emitExpression((Node) seq.elementAt(1));
			switch (op.tokenImage) {
			case "<":
				val = bop(fLT, val, rhs);
				break;
			case ">":
				val = bop(fGT, val, rhs);
				break;
			case ">=":
				val = bop(fGTE, val, rhs);
				break;
			case "<=":
				val = bop(fLEQ, val, rhs);
				break;
			case "instanceof":
				val = bop(fIOF, val, rhs);
				break;
			default:
				val = fUNDEF;
				break;
			}
		}
		return val;
	}
	
	@Override
	public Object visit(AdditiveExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
			String rhs = emitExpression((Node) seq.elementAt(1));
			switch (op.tokenImage) {
			case "+":
				val = bop(fPLUS, val, rhs);
				break;
			case "-":
				val = bop(fMINUS, val, rhs);
				break;
			default:
				val = fUNDEF;
				break;
			}
		}
		return val;
	}

	@Override
	public Object visit(MultiplicativeExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
			NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
			String rhs = emitExpression((Node) seq.elementAt(1));
			switch (op.tokenImage) {
			case "*":
				val = bop(fMUL, val, rhs);
				break;
			case "/":
				val = bop(fDIV, val, rhs);
				break;
			case "%":
				val = bop(fREMAIN, val, rhs);
				break;
			default:
				val = fUNDEF;
				break;
			}
		}
		return val;
	}

	@Override
	public Object visit(UnaryExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f1);
		if (n.f0.present()) {
			NodeToken op = (NodeToken) ((NodeChoice) n.f0.node).choice;
			switch (op.tokenImage) {
			case "++":
				val = assign(val, uop(fINC, val));
				break;
			case "--":
				val = assign(val, uop(fDEC, val));
				break;
			case "+":
				val = "(" + uop(fUPLUS, val) + ")";
				break;
			case "-":
				val = "(" + uop(fUMINUS, val) + ")";
				break;
			case "~":
				val = "(" + uop(fBITNOT, val) + ")";
				break;
			case "!":
				val = "(" + uop(fNOT, val) + ")";
				break;
			default:
				break;
			}
		}
		return val;
	}

	@Override
	public Object visit(PostfixExpression n, Object argu) {
                map(n);
		String val = emitExpression(n.f0);
		if (n.f1.present()) {
			NodeToken op = (NodeToken) ((NodeChoice) n.f1.node).choice;
			switch (op.tokenImage) {
			case "++":
				return fPINC+"(" + val + ")";
			case "--":
				return fPDEC+"(" + val + ")";
			default:
				break;
			}
		}
		return val;
	}

	@Override
	public Object visit(TypeExpression n, Object argu) {
                map(n);
		return emitExpression(n.f0);
	}

	@Override
	public Object visit(PrimaryExpression n, Object argu) {
                map(n);
		String expr = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			expr = emitPostfix((Node) n.f1.elementAt(i), expr);
		}
		return expr;
	}

	@Override
	public Object visit(PrimaryExpressionNotFunction n, Object argu) {
                map(n);
		String expr = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			expr = emitPostfix((Node) n.f1.elementAt(i), expr);
		}
		return expr;
	}

	@Override
	public Object visit(PrimaryExpressionWithTrailingFxnCallExpList n, Object argu) {
                map(n);
		String expr = emitExpression(n.f0);
		for (int i = 0; i < n.f1.size(); i++) {
			expr = emitPostfix((Node) n.f1.elementAt(i), expr);
		}
		return expr;
	}

	@Override
	public Object visit(PrimaryPrefix n, Object argu) {
                map(n);
		return emitExpression(n.f0);
	}

	@Override
	public Object visit(PrimaryPrefixNotFunction n, Object argu) {
                map(n);
		return emitExpression(n.f0);
	}

	@Override
	public Object visit(FunctionPrimaryPrefix n, Object argu) {
                map(n);
		return emitFunctionExpression(n);
	}

	private LinkedHashMap<String,String> collectArgNames(NodeOptional argOpt, boolean[] sHasVargs) {
		LinkedHashMap<String,String> params = new LinkedHashMap();
		if (!argOpt.present()) 
			return params;
		Arglist args = (Arglist) argOpt.node;
		String name = args.f1.tokenImage;
		String ename = encodeName(name);
		params.put(name,ename);
		for (Enumeration e = args.f2.elements(); e.hasMoreElements();) {
			NodeSequence seq = (NodeSequence) e.nextElement();
			name = ((NodeToken) seq.elementAt(2)).tokenImage;
			ename = encodeName(name);
			params.put(name,ename);
		}
		if (args.f3.present() && !params.isEmpty()) 
			sHasVargs[0]=true;
		return params;
	}

	private String encodeName(String name) {
		switch (name) {
			case "default":
			case "in":
			case "for":
			case "of":
			case "const":
			case "let":
				return "ɵ"+name;
			case "$itr$":
				return "Ɵ"; // iterator
		}
		return name;
	}

	@Override
	public Object visit(ParenPrimaryPrefix n, Object argu) {
                map(n);
		return "(" + emitExpression(n.f1) + ")";
	}

	@Override
	public Object visit(ThisPrimaryPrefix n, Object argu) {
                map(n);
		return fTHS+"("+sScope+")";
	}

	@Override
	public Object visit(SuperPrimaryPrefix n, Object argu) {
                map(n);
		return fSUPR+"("+sScope+")";
	}

	@Override
	public Object visit(CalleePrimaryPrefix n, Object argu) {
                map(n);
		return fCALE+"("+sScope+")";
	}

	@Override
	public Object visit(ArrayDeclarationPrimaryPrefix n, Object argu) {
                map(n);
		String contents = "";
		if (n.f1.present()) {
			contents = emitInitializer((FunctionCallExpressionListBody) n.f1.node);
		}
		return contents.isEmpty() ? fARR0+"()" : fARR+"(" + contents + ")";
	}

	@Override
	public Object visit(Literal n, Object argu) {
                map(n);
		return emitLiteral((NodeToken) n.f0.choice);
	}

	@Override
	public Object visit(AllocationExpression n, Object argu) {
                map(n);
		String callee = emitExpression(n.f1);
		String args = emitArgs(n.f2);
		return emitInvocation(callee, args, true);
	}

	@Override
	public Object visit(CastExpression n, Object argu) {
                map(n);
		String target = emitExpression(n.f1);
		String expr = emitExpression(n.f3);
		return bop(fCAST, target, expr);
	}

	private String emitLiteral(NodeToken token) {
		switch (token.kind) {
		case oscript.parser.OscriptParserConstants.INTEGER_LITERAL:
			return exactNumberConst(token.tokenImage);
		case oscript.parser.OscriptParserConstants.FLOATING_POINT_LITERAL:
			return inexactNumberConst(token.tokenImage);
		case oscript.parser.OscriptParserConstants.STRING_LITERAL: {
			String raw = token.tokenImage;
			String inner = raw.substring(1, raw.length() - 1);
			return stringConst(inner);
		}
		case oscript.parser.OscriptParserConstants.TRUE:
			return fTRUE;
		case oscript.parser.OscriptParserConstants.FALSE:
			return fFALSE;
		case oscript.parser.OscriptParserConstants.NULL:
			return fNULL;
		/*case oscript.parser.OscriptParserConstants.UNDEFINED:
			return fUNDEF;*/
		default:
			return fUNDEF;
		}
	}

	@Override
	public Object visit(PrimaryPostfix n, Object argu) {
                map(n);
		return emitPostfix(n.f0.choice, (String) argu);
	}

	@Override
	public Object visit(PrimaryPostfixWithTrailingFxnCallExpList n, Object argu) {
                map(n);
		return emitPostfix(n.f0.choice, (String) argu);
	}

	@Override
	public Object visit(FunctionCallExpressionList n, Object argu) {
                map(n);
		return emitArgs(n);
	}

	private String emitArgs(FunctionCallExpressionList list) {
		if (!list.f1.present()) {
			return emptyArgsConst();
		}
		FunctionCallExpressionListBody body = (FunctionCallExpressionListBody) list.f1.node;
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		builder.append(emitExpression(body.f0));
		for (int i = 0; i < body.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) body.f1.elementAt(i);
			builder.append(", ");
			builder.append(emitExpression((Node) seq.elementAt(1)));
		}
		builder.append("]");
		return builder.toString();
	}

	private String emitInitializer(FunctionCallExpressionListBody body) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		builder.append(emitExpression(body.f0));
		for (int i = 0; i < body.f1.size(); i++) {
			NodeSequence seq = (NodeSequence) body.f1.elementAt(i);
			builder.append(", ");
			builder.append(emitExpression((Node) seq.elementAt(1)));
		}
		builder.append("]");
		return builder.toString();
	}

	private String emitPostfix(Node node, String base) {
		if (node instanceof FunctionCallPrimaryPostfix) {
			String args = emitArgs(((FunctionCallPrimaryPostfix) node).f0);
			return emitInvocation(base, args, false);
		}
		if (node instanceof PropertyIdentifierPrimaryPostfix) {
			String name = ((PropertyIdentifierPrimaryPostfix) node).f1.tokenImage;
			return member(base, symbolId(name));
		}
		if (node instanceof oscript.syntaxtree.ArraySubscriptPrimaryPostfix) {
			oscript.syntaxtree.ArraySubscriptPrimaryPostfix arr = (oscript.syntaxtree.ArraySubscriptPrimaryPostfix) node;
			String idx = emitExpression(arr.f1);
			return elementAt(base, idx);
		}
		if (node instanceof oscript.syntaxtree.ThisScopeQualifierPrimaryPostfix) {
			return member(base, symbolId("this"));
		}
		if (node instanceof PrimaryPostfix) {
			return emitPostfix(((PrimaryPostfix) node).f0.choice, base);
		}
		if (node instanceof PrimaryPostfixWithTrailingFxnCallExpList) {
			return emitPostfix(((PrimaryPostfixWithTrailingFxnCallExpList) node).f0.choice, base);
		}
		return base;
	}

	private static String assign(String target, String value) {
		return fASGN+"(" + target + ", " + value + ")";
	}

	private static String bop(String fn, String left, String right) {
		return fn + "(" + left + ", " + right + ")";
	}

	private static String uop(String fn, String value) {
		return fn + "(" + value + ")";
	}

	private String castToBooleanSoft(String expr) {
		return fBOOL+"(" + expr + ")";
	}

	private static String member(String target, String symbol) {
		return fMEMB+"(" + target + ", " + symbol + ")";
	}

	private static String elementAt(String target, String index) {
		return fEL+"(" + target + ", " + index + ")";
	}

	private String emitInvocation(String callee, String args, boolean constructor) {
		return constructor ? fCONSTR+"("+sSf+"," + callee + ", " + args + ")" : fCALL+"("+sSf+"," + callee + ", " + args + ")";
	}


	private String emptyArgsConst() {	
		return fAEMTY;
	}

	private String nextSeqStr() {
		return Integer.toString(pool.stringCounter++,16);
	}
	private String stringConst(String value) {
		String name = stringNames().get(value);
		if (name == null) {
			name = pCONST + nextSeqStr();
			stringNames().put(value, name);
			constants.add(name + " = "+fSTR+"(\"" +value + "\")");
		}
		return name;
	}

	private String symbolId(String name) {
		String id = symbolNames().get(name);
		if (id == null) {
			id = pSYMB + nextSeqStr();
			symbolNames().put(name, id);
			constants.add(id + " = "+fSYMB+"(\"" +name+ "\")");
		}
		return id;
	}

	private String exactNumberConst(String value) {
		String name = exactNumberNames().get(value);
		if (name == null) {
			name = pNUMB + nextSeqStr();
			exactNumberNames().put(value, name);
			constants.add(name + " = "+fNUME+"(" + value + ")");
		}
		return name;
	}

	private String inexactNumberConst(String value) {
		String name = inexactNumberNames().get(value);
		if (name == null) {
			name = pNUMB + nextSeqStr();
			inexactNumberNames().put(value, name);
			constants.add(name + " = "+fNUMF+"(" + value + ")");
		}
		return name;
	}

	private Map<String, String> stringNames() {
		if (pool.stringNameMap == null) {
			pool.stringNameMap = new LinkedHashMap<>();
		}
		return pool.stringNameMap;
	}

	private Map<String, String> symbolNames() {
		if (pool.symbolNameMap == null) {
			pool.symbolNameMap = new LinkedHashMap<>();
		}
		return pool.symbolNameMap;
	}

	private Map<String, String> exactNumberNames() {
		if (pool.exactNumberNameMap == null) {
			pool.exactNumberNameMap = new LinkedHashMap<>();
		}
		return pool.exactNumberNameMap;
	}

	private Map<String, String> inexactNumberNames() {
		if (pool.inexactNumberNameMap == null) {
			pool.inexactNumberNameMap = new LinkedHashMap<>();
		}
		return pool.inexactNumberNameMap;
	}

	private static int getPermissions(oscript.syntaxtree.Permissions n, int attr) {
		for (int i = 0; i < n.f0.size(); i++) {
			Node t = n.f0.elementAt(i);
			if (t instanceof NodeChoice)
				t = ((NodeChoice)t).choice;
			NodeToken token = (NodeToken)t;
			switch (token.kind) {
			case oscript.parser.OscriptParserConstants.PRIVATE:
				attr = (attr & 0xf0) | Reference.ATTR_PRIVATE;
				break;
			case oscript.parser.OscriptParserConstants.PROTECTED:
				attr = (attr & 0xf0) | Reference.ATTR_PROTECTED;
				break;
			case oscript.parser.OscriptParserConstants.PUBLIC:
				attr = (attr & 0xf0) | Reference.ATTR_PUBLIC;
				break;
			case oscript.parser.OscriptParserConstants.STATIC:
				attr |= Reference.ATTR_STATIC;
				break;
			case oscript.parser.OscriptParserConstants.CONST:
				attr |= Reference.ATTR_CONST;
				break;
			default:
				break;
			}
		}
		return attr;
	}
}
