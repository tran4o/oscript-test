package oscript.js.transpiler;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import oscript.syntaxtree.Expression;
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
import oscript.syntaxtree.ThisScopeQualifierPrimaryPostfix;
import oscript.syntaxtree.TypeExpression;
import oscript.syntaxtree.UnaryExpression;
import oscript.syntaxtree.VariableDeclaration;
import oscript.syntaxtree.VariableDeclarationBlock;
import oscript.syntaxtree.WhileLoopStatement;
import oscript.translator.CollectionForLoopStatementTranslator;
import oscript.translator.ForLoopStatementTranslator;
import oscript.translator.FunctionDeclarationTranslator;
import oscript.visitor.ObjectDepthFirst;

final class JsEmitterVisitor extends ObjectDepthFirst {

    private static final class ConstantPool {
        int stringCounter;
        int symbolCounter;
        int exactNumberCounter;
        int inexactNumberCounter;
        boolean emptyArgsConstEmitted;
        Map<String, String> stringNameMap;
        Map<String, String> symbolNameMap;
        Map<String, String> exactNumberNameMap;
        Map<String, String> inexactNumberNameMap;
    }

    private final JsSourceBuilder out;
    private final JsSourceBuilder constants;
    private final ConstantPool pool;
    private int constantInsertPos;
    private final java.util.Set<String> declaredNames = new java.util.HashSet<>();
    private String lastExpression = "UNDEFINED";

    JsEmitterVisitor() {
        this(new JsSourceBuilder(), new JsSourceBuilder(), new ConstantPool());
    }

    private JsEmitterVisitor(JsSourceBuilder out, JsSourceBuilder constants, ConstantPool pool) {
        this.out = out;
        this.constants = constants;
        this.pool = pool;
    }

    JsSourceBuilder emitProgram(ProgramFile file) {
        out.append("(function(oscript){");
        out.indent();
        out.line("const {");
        out.line("  SYMB_GET, SYMB_ID, NEW_OARRAY,");
        out.line("  TRUE, FALSE, INVOKE, INVOKEC, POSTINC, POSTDEC, UNDEFINED, NULL,");
        out.line("  SCOPE_CM, SCOPE_L, SCOPE_THS, SCOPE_SPR, SCOPE_GCLE,");
        out.line("  VAL_OA, VAL_CB, VAL_PLS, VAL_MNS, VAL_MUL, VAL_DIV,");
        out.line("  VAL_bopRemainder, VAL_bopBitwiseAnd, VAL_bopBitwiseOr, VAL_bopBitwiseXor, VAL_bopLeftShift,");
        out.line("  VAL_bopSignedRightShift, VAL_bopUnsignedRightShift, VAL_EQ, VAL_NEQ, VAL_LT,");
        out.line("  VAL_GT, VAL_GTE, VAL_LEQ, VAL_IOF, VAL_bopCast,");
        out.line("  VAL_INC, VAL_DEC, VAL_OPLS, VAL_OMNS, VAL_uopBitwiseNot, VAL_uopLogicalNot,");
        out.line("  VAL_GM, VAL_EL,");
        out.line("  MAKE_STR, MAKE_EN, MAKE_IEN");
        out.line("} = oscript;");
        out.line("return function(scope,sf){");
        out.indent();
        constantInsertPos = out.position();
        constants.setIndent(out.indentLevel());
        out.line("let _r = UNDEFINED;");
        file.accept(this, null);
        out.insert(constantInsertPos, constants.toString());
        out.line("return _r;");
        out.dedent();
        out.line("};");
        out.dedent();
        out.line("})(oscript)");
        return out;
    }

    @Override
    public Object visit(ProgramFile n, Object argu) {
        n.f1.accept(this, argu);
        return null;
    }

    @Override
    public Object visit(Program n, Object argu) {
        n.f0.accept(this, argu);
        return null;
    }

    @Override
    public Object visit(oscript.syntaxtree.Expression n, Object argu) {
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
        for (Enumeration e = n.elements(); e.hasMoreElements();) {
            ((Node) e.nextElement()).accept(this, argu);
        }
        return null;
    }

    @Override
    public Object visit(NodeListOptional n, Object argu) {
        if (n.present()) {
            for (Enumeration e = n.elements(); e.hasMoreElements();) {
                ((Node) e.nextElement()).accept(this, argu);
            }
        }
        return null;
    }

    @Override
    public Object visit(EvaluationUnit n, Object argu) {
        n.f0.accept(this, argu);
        return null;
    }

    @Override
    public Object visit(FunctionDeclaration n, Object argu) {
        return FunctionDeclarationTranslator.translate(n).accept(this, argu);
    }

    @Override
    public Object visit(ScopeBlock n, Object argu) {
        out.line("{");
        out.indent();
        n.f1.accept(this, argu);
        out.dedent();
        out.line("}");
        return null;
    }

    @Override
    public Object visit(VariableDeclarationBlock n, Object argu) {
        n.f0.accept(this, argu);
        return null;
    }

    @Override
    public Object visit(PreLoopStatement n, Object argu) {
        return n.f0.accept(this, argu);
    }

    @Override
    public Object visit(ForLoopStatement n, Object argu) {
        return ForLoopStatementTranslator.translate(n).accept(this, argu);
    }

    @Override
    public Object visit(CollectionForLoopStatement n, Object argu) {
        return CollectionForLoopStatementTranslator.translate(n).accept(this, argu);
    }

    @Override
    public Object visit(VariableDeclaration n, Object argu) {
        int permissions = getPermissions(n.f0, Reference.ATTR_PROTECTED);
        String name = n.f2.tokenImage;
        declaredNames.add(name);
        out.line("const " + name + " = SCOPE_CM(scope, " + symbolId(name) + ", " + permissions + ");");
        if (n.f3.present()) {
            NodeSequence seq = (NodeSequence) n.f3.node;
            String expr = emitExpression((Node) seq.elementAt(1));
            out.line(assign(name, expr) + ";");
        }
        return null;
    }

    @Override
    public Object visit(ExpressionBlock n, Object argu) {
        String expr = emitExpression(n.f0);
        out.line(expr + ";");
        lastExpression = expr;
        return null;
    }

    @Override
    public Object visit(ReturnStatement n, Object argu) {
        if (n.f1.present()) {
            String expr = emitExpression((Node) n.f1.node);
            out.line("return " + expr + ";");
        } else {
            out.line("return UNDEFINED;");
        }
        return null;
    }

    @Override
    public Object visit(ConditionalStatement n, Object argu) {
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
        out.line("break;");
        return null;
    }

    @Override
    public Object visit(ContinueStatement n, Object argu) {
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
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
            String rhs = emitExpression((Node) seq.elementAt(1));
            val = emitAssignment(op.kind, val, rhs);
        }
        return val;
    }

    private String emitAssignment(int op, String left, String right) {
        switch (op) {
            case oscript.parser.OscriptParserConstants.ASSIGN:
                return assign(left, right);
            case oscript.parser.OscriptParserConstants.PLUSASSIGN:
                return assign(left, bop("VAL_PLS", left, right));
            case oscript.parser.OscriptParserConstants.MINUSASSIGN:
                return assign(left, bop("VAL_MNS", left, right));
            case oscript.parser.OscriptParserConstants.STARASSIGN:
                return assign(left, bop("VAL_MUL", left, right));
            case oscript.parser.OscriptParserConstants.SLASHASSIGN:
                return assign(left, bop("VAL_DIV", left, right));
            case oscript.parser.OscriptParserConstants.REMASSIGN:
                return assign(left, bop("VAL_bopRemainder", left, right));
            case oscript.parser.OscriptParserConstants.ANDASSIGN:
                return assign(left, bop("VAL_bopBitwiseAnd", left, right));
            case oscript.parser.OscriptParserConstants.ORASSIGN:
                return assign(left, bop("VAL_bopBitwiseOr", left, right));
            case oscript.parser.OscriptParserConstants.XORASSIGN:
                return assign(left, bop("VAL_bopBitwiseXor", left, right));
            case oscript.parser.OscriptParserConstants.LSHIFTASSIGN:
                return assign(left, bop("VAL_bopLeftShift", left, right));
            case oscript.parser.OscriptParserConstants.RSIGNEDSHIFTASSIGN:
                return assign(left, bop("VAL_bopSignedRightShift", left, right));
            case oscript.parser.OscriptParserConstants.RUNSIGNEDSHIFTASSIGN:
                return assign(left, bop("VAL_bopUnsignedRightShift", left, right));
            default:
                return assign(left, "UNDEFINED");
        }
    }

    @Override
    public Object visit(ConditionalExpression n, Object argu) {
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
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            String rhs = emitExpression((Node) seq.elementAt(1));
            val = bop("VAL_bopBitwiseOr", val, rhs);
        }
        return val;
    }

    @Override
    public Object visit(BitwiseXorExpression n, Object argu) {
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            String rhs = emitExpression((Node) seq.elementAt(1));
            val = bop("VAL_bopBitwiseXor", val, rhs);
        }
        return val;
    }

    @Override
    public Object visit(BitwiseAndExpression n, Object argu) {
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            String rhs = emitExpression((Node) seq.elementAt(1));
            val = bop("VAL_bopBitwiseAnd", val, rhs);
        }
        return val;
    }

    @Override
    public Object visit(EqualityExpression n, Object argu) {
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
            String rhs = emitExpression((Node) seq.elementAt(1));
            switch (op.tokenImage) {
                case "==":
                    val = bop("VAL_EQ", val, rhs);
                    break;
                case "!=":
                    val = bop("VAL_NEQ", val, rhs);
                    break;
                default:
                    val = "UNDEFINED";
                    break;
            }
        }
        return val;
    }

    @Override
    public Object visit(RelationalExpression n, Object argu) {
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
            String rhs = emitExpression((Node) seq.elementAt(1));
            switch (op.tokenImage) {
                case "<":
                    val = bop("VAL_LT", val, rhs);
                    break;
                case ">":
                    val = bop("VAL_GT", val, rhs);
                    break;
                case ">=":
                    val = bop("VAL_GTE", val, rhs);
                    break;
                case "<=":
                    val = bop("VAL_LEQ", val, rhs);
                    break;
                case "instanceof":
                    val = bop("VAL_IOF", val, rhs);
                    break;
                default:
                    val = "UNDEFINED";
                    break;
            }
        }
        return val;
    }

    @Override
    public Object visit(ShiftExpression n, Object argu) {
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
            String rhs = emitExpression((Node) seq.elementAt(1));
            switch (op.tokenImage) {
                case "<<":
                    val = bop("VAL_bopLeftShift", val, rhs);
                    break;
                case ">>":
                    val = bop("VAL_bopSignedRightShift", val, rhs);
                    break;
                case ">>>":
                    val = bop("VAL_bopUnsignedRightShift", val, rhs);
                    break;
                default:
                    val = "UNDEFINED";
                    break;
            }
        }
        return val;
    }

    @Override
    public Object visit(AdditiveExpression n, Object argu) {
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
            String rhs = emitExpression((Node) seq.elementAt(1));
            switch (op.tokenImage) {
                case "+":
                    val = bop("VAL_PLS", val, rhs);
                    break;
                case "-":
                    val = bop("VAL_MNS", val, rhs);
                    break;
                default:
                    val = "UNDEFINED";
                    break;
            }
        }
        return val;
    }

    @Override
    public Object visit(MultiplicativeExpression n, Object argu) {
        String val = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            NodeSequence seq = (NodeSequence) n.f1.elementAt(i);
            NodeToken op = (NodeToken) ((NodeChoice) seq.elementAt(0)).choice;
            String rhs = emitExpression((Node) seq.elementAt(1));
            switch (op.tokenImage) {
                case "*":
                    val = bop("VAL_MUL", val, rhs);
                    break;
                case "/":
                    val = bop("VAL_DIV", val, rhs);
                    break;
                case "%":
                    val = bop("VAL_bopRemainder", val, rhs);
                    break;
                default:
                    val = "UNDEFINED";
                    break;
            }
        }
        return val;
    }

    @Override
    public Object visit(UnaryExpression n, Object argu) {
        String val = emitExpression(n.f1);
        if (n.f0.present()) {
            NodeToken op = (NodeToken) ((NodeChoice) n.f0.node).choice;
            switch (op.tokenImage) {
                case "++":
                    val = assign(val, uop("VAL_INC", val));
                    break;
                case "--":
                    val = assign(val, uop("VAL_DEC", val));
                    break;
                case "+":
                    val = "(" + uop("VAL_OPLS", val) + ")";
                    break;
                case "-":
                    val = "(" + uop("VAL_OMNS", val) + ")";
                    break;
                case "~":
                    val = "(" + uop("VAL_uopBitwiseNot", val) + ")";
                    break;
                case "!":
                    val = "(" + uop("VAL_uopLogicalNot", val) + ")";
                    break;
                default:
                    break;
            }
        }
        return val;
    }

    @Override
    public Object visit(PostfixExpression n, Object argu) {
        String val = emitExpression(n.f0);
        if (n.f1.present()) {
            NodeToken op = (NodeToken) ((NodeChoice) n.f1.node).choice;
            switch (op.tokenImage) {
                case "++":
                    return "POSTINC(" + val + ")";
                case "--":
                    return "POSTDEC(" + val + ")";
                default:
                    break;
            }
        }
        return val;
    }

    @Override
    public Object visit(TypeExpression n, Object argu) {
        return emitExpression(n.f0);
    }

    @Override
    public Object visit(PrimaryExpression n, Object argu) {
        String expr = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            expr = emitPostfix((Node) n.f1.elementAt(i), expr);
        }
        return expr;
    }

    @Override
    public Object visit(PrimaryExpressionNotFunction n, Object argu) {
        String expr = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            expr = emitPostfix((Node) n.f1.elementAt(i), expr);
        }
        return expr;
    }

    @Override
    public Object visit(PrimaryExpressionWithTrailingFxnCallExpList n, Object argu) {
        String expr = emitExpression(n.f0);
        for (int i = 0; i < n.f1.size(); i++) {
            expr = emitPostfix((Node) n.f1.elementAt(i), expr);
        }
        return expr;
    }

    @Override
    public Object visit(PrimaryPrefix n, Object argu) {
        return emitExpression(n.f0);
    }

    @Override
    public Object visit(PrimaryPrefixNotFunction n, Object argu) {
        return emitExpression(n.f0);
    }

    @Override
    public Object visit(FunctionPrimaryPrefix n, Object argu) {
        return emitFunctionExpression(n);
    }

    private String emitFunctionExpression(FunctionPrimaryPrefix n) {
        JsEmitterVisitor fn = new JsEmitterVisitor(new JsSourceBuilder(), constants, pool);
        fn.declaredNames.addAll(declaredNames);
        List<String> params = collectArgNames(n.f2);
        fn.declaredNames.addAll(params);

        JsSourceBuilder builder = fn.out;
        builder.append("function(");
        builder.append(String.join(", ", params));
        builder.append("){");
        builder.indent();
        n.f6.accept(fn, null);
        builder.dedent();
        builder.append("}");
        return builder.toString();
    }

    private List<String> collectArgNames(NodeOptional argOpt) {
        List<String> params = new ArrayList<>();
        if (!argOpt.present()) {
            return params;
        }
        Arglist args = (Arglist) argOpt.node;
        params.add(args.f1.tokenImage);
        for (Enumeration e = args.f2.elements(); e.hasMoreElements();) {
            NodeSequence seq = (NodeSequence) e.nextElement();
            params.add(((NodeToken) seq.elementAt(2)).tokenImage);
        }
        if (args.f3.present()) {
            params.add("...rest");
        }
        return params;
    }

    @Override
    public Object visit(IdentifierPrimaryPrefix n, Object argu) {
        String name = n.f0.tokenImage;
        return declaredNames.contains(name) ? name : "SCOPE_L(scope, " + symbolId(name) + ")";
    }

    @Override
    public Object visit(ParenPrimaryPrefix n, Object argu) {
        return "(" + emitExpression(n.f1) + ")";
    }

    @Override
    public Object visit(ThisPrimaryPrefix n, Object argu) {
        return "SCOPE_THS(scope)";
    }

    @Override
    public Object visit(SuperPrimaryPrefix n, Object argu) {
        return "SCOPE_SPR(scope)";
    }

    @Override
    public Object visit(CalleePrimaryPrefix n, Object argu) {
        return "SCOPE_GCLE(scope)";
    }

    @Override
    public Object visit(ArrayDeclarationPrimaryPrefix n, Object argu) {
        String contents = "";
        if (n.f1.present()) {
            contents = emitInitializer((FunctionCallExpressionListBody) n.f1.node);
        }
        return contents.isEmpty() ? "NEW_OARRAY()" : "NEW_OARRAY(" + contents + ")";
    }

    @Override
    public Object visit(Literal n, Object argu) {
        return emitLiteral((NodeToken) n.f0.choice);
    }

    @Override
    public Object visit(AllocationExpression n, Object argu) {
        String callee = emitExpression(n.f1);
        String args = emitArgs(n.f2);
        return emitInvocation(callee, args, true);
    }

    @Override
    public Object visit(CastExpression n, Object argu) {
        String target = emitExpression(n.f1);
        String expr = emitExpression(n.f3);
        return bop("VAL_bopCast", target, expr);
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
                return "TRUE";
            case oscript.parser.OscriptParserConstants.FALSE:
                return "FALSE";
            case oscript.parser.OscriptParserConstants.NULL:
                return "NULL";
            case oscript.parser.OscriptParserConstants.UNDEFINED:
                return "UNDEFINED";
            default:
                return "UNDEFINED";
        }
    }

    @Override
    public Object visit(PrimaryPostfix n, Object argu) {
        return emitPostfix(n.f0.choice, (String) argu);
    }

    @Override
    public Object visit(PrimaryPostfixWithTrailingFxnCallExpList n, Object argu) {
        return emitPostfix(n.f0.choice, (String) argu);
    }

    @Override
    public Object visit(FunctionCallExpressionList n, Object argu) {
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

    private String emitArgs(NodeListInterface list) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(emitExpression((Node) ((NodeSequence) list.elementAt(i)).elementAt(1)));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String assign(String target, String value) {
        return "VAL_OA(" + target + ", " + value + ")";
    }

    private static String bop(String fn, String left, String right) {
        return fn + "(" + left + ", " + right + ")";
    }

    private static String uop(String fn, String value) {
        return fn + "(" + value + ")";
    }

    private String castToBooleanSoft(String expr) {
        return "VAL_CB(" + expr + ")";
    }

    private static String member(String target, String symbol) {
        return "VAL_GM(" + target + ", " + symbol + ")";
    }

    private static String elementAt(String target, String index) {
        return "VAL_EL(" + target + ", " + index + ")";
    }

    private String emitInvocation(String callee, String args, boolean constructor) {
        return constructor ? "INVOKEC(" + callee + ", " + args + ")" : "INVOKE(" + callee + ", " + args + ")";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    private String emptyArgsConst() {
        if (!pool.emptyArgsConstEmitted) {
            constants.line("const ARR_0 = [];");
            pool.emptyArgsConstEmitted = true;
        }
        return "ARR_0";
    }

    private String stringConst(String value) {
        String name = stringNames().get(value);
        if (name == null) {
            name = "STR_" + pool.stringCounter++;
            stringNames().put(value, name);
            constants.line("const " + name + " = MAKE_STR('" + escape(value) + "');");
        }
        return name;
    }

    private String symbolId(String name) {
        String id = symbolNames().get(name);
        if (id == null) {
            id = "SYMB_" + pool.symbolCounter++;
            symbolNames().put(name, id);
            constants.line("const " + id + " = SYMB_ID('" + escape(name) + "');");
        }
        return id;
    }

    private String exactNumberConst(String value) {
        String name = exactNumberNames().get(value);
        if (name == null) {
            name = "INT_" + pool.exactNumberCounter++;
            exactNumberNames().put(value, name);
            constants.line("const " + name + " = MAKE_EN(" + value + ");");
        }
        return name;
    }

    private String inexactNumberConst(String value) {
        String name = inexactNumberNames().get(value);
        if (name == null) {
            name = "FLT_" + pool.inexactNumberCounter++;
            inexactNumberNames().put(value, name);
            constants.line("const " + name + " = MAKE_IEN(" + value + ");");
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
            NodeToken token = (NodeToken) n.f0.elementAt(i);
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
