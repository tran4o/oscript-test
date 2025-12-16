/*=============================================================================
 *     Copyright Texas Instruments 2000-2003.  All Rights Reserved.
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * $ProjectHeader: OSCRIPT 0.155 Fri, 20 Dec 2002 18:34:22 -0800 rclark $
 */


package oscript.compiler;


import oscript.syntaxtree.*;
import oscript.data.*;
import oscript.exceptions.*;
import oscript.parser.OscriptParser;
import oscript.translator.*;


// The Bytecode Engineerign Library
import org.apache.bcel.generic.*;
import org.apache.bcel.Const;

import java.util.LinkedList;
import java.util.Iterator;


/**
 * The CompilerVisitor is the compiler, which compiles functions to an 
 * instance of <code>CompiledNodeEvaluator</code>.
 * <p>
 * Some assumptions are made with respect to tracking the info needed to
 * generate useful stack traces, for example that the entire function is
 * defined within the same file.
 * 
 * @author Rob Clark (rob@ti.com)
 * <!--$Format: " * @version $Revision$"$-->
 * @version 1.84
 */
public class CompilerVisitor implements oscript.visitor.Visitor, oscript.parser.OscriptParserConstants
{
  /**
   * The instruction list which becomes the code of the <code>evalNode</code>
   * method which is generated.
   */
  CompilerInstructionList il;
  
  /**
   * The current method being generated.
   */
  MethodGen mg;
  
  /**
   */
  CompilerContext ctx;
  
  /**
   * Some nodes, when visited, leave a return node on the top of the stack,
   * and some remove the return value from the stack.  Nodes that don't
   * put or remove the return value from the top of the stack leave this
   * flag unchanged.
   */
  private boolean retValOnStack;
  
  /**
   * Set <code>retValOnStack</code> with this function... it does some
   * error checking.
   */
  private void setRetValOnStack( boolean retValOnStack )
  {
    if( this.retValOnStack == retValOnStack )
      throw new ProgrammingErrorException("retValOnStack is already " + retValOnStack);
    else
      this.retValOnStack = retValOnStack;
  }
  
  /**
   * For sanity checking, sort of like an ASSERT() for <code>retValOnStack</code>.
   */
  private void checkRetValOnStack( boolean retValOnStack )
  {
    if( this.retValOnStack != retValOnStack )
      throw new ProgrammingErrorException("retValOnStack is not " + retValOnStack);
  }
  
  /*=======================================================================*/
  /**
   * The loop stack is used to track nested levels of loops, and cleanup
   * such as JSR to finally or releasing monitors, that needs to happen
   * when program execution jumps out of a loop, such as in the case of
   * "return", "continue", or "break"
   */
  private LoopStackNode loopStack;
  
  /*=======================================================================*/
  /**
   * List of runnables to invoke after the first pass of the compiler over
   * the syntax-tree
   */
  private LinkedList deferredRunnableList = new LinkedList();
  
  
  /*=======================================================================*/
  /**
   * Create a new compiler-visitor, which generates compiled code
   */
  CompilerVisitor( CompilerContext ctx, String name, Node node )
  {
    this( ctx, name, node, null );
  }
  private CompilerVisitor( CompilerContext ctx, String name, Node node, int[] argIds )
  {
    this.ctx = ctx;
    
    il = new CompilerInstructionList();
    
    mg = new MethodGen( Const.ACC_PRIVATE,
                        CompilerContext.OBJECT_TYPE,
                        CompilerContext.EVAL_NODE_ARG_TYPES,
                        CompilerContext.EVAL_NODE_ARG_NAMES,
                        "_" + (innerNodeIdx=ctx.getNextEvalNodeIdx(name)) + "_" + name,
                        ctx.className,
                        il, ctx.cp );
    
    scope = new CompilerScope( this, 2, argIds );
    
    ctx.addSMITs( innerNodeIdx, scope.getSharedMemberIndexTableIdxs() );
    
    compileNode(node);
  }
  
  private int innerNodeIdx;
  
  
  /*=======================================================================*/
  /**
   * The entry point to compile a node.
   * 
   * @param node         the node in syntaxtree to compile
   */
  private void compileNode( Node node )
  {
    retValOnStack = false;
    
    loopStack = new LoopStackNode(null);
    
    node.accept(this);
    
    loopStack.pop();
    
    if( ! retValOnStack )
      getInstanceConstant( Value.UNDEFINED );
    
    /* we may end up with two ARETURN's at the end, but that should be
     * ok... we need to add a return instruction at the end just in
     * case there isn't one, or if someone calls il.setNextAsTarget(...)
     */
    il.append( InstructionConst.ARETURN );
    
    for( Iterator itr=deferredRunnableList.iterator(); itr.hasNext(); )
      ((Runnable)(itr.next())).run();
    
    mg.setMaxStack();
    ctx.cg.addMethod( mg.getMethod() );
  }
  
  /*=======================================================================*/
  CompilerScope scope;
  
  
  /*=======================================================================*/
  /**
   */
  public void visit( NodeList n )            { throw new ProgrammingErrorException("unimplemented"); }
  public void visit( NodeListOptional n )    { throw new ProgrammingErrorException("unimplemented"); }
  public void visit( NodeOptional n )        { throw new ProgrammingErrorException("unimplemented"); }
  public void visit( NodeSequence n )        { throw new ProgrammingErrorException("unimplemented"); }
  
  public static boolean LINE_NUMBER_ENABLED = true; 
  /*=======================================================================*/
  private NodeToken         NodeToken_lastToken;
  private int               NodeToken_lastBeginLine = -1;
  private InstructionHandle NodeToken_lastBranchTarget = null;
  
  private CompilerScope NodeToken_lastScope = null;  // for deciding which setLineNumber() to call
  
  /**
   * Handles storing line # info for a node-token.  This should really be
   * called for every node-token to ensure that no line # info is lost.
   */
  void handle( NodeToken n )
  {
    NodeToken_lastToken = n;
    
    /*if( n.specialTokens != null )
      NodeToken_lastSpecials = n.specialTokens;*/
    
    if(LINE_NUMBER_ENABLED) // XXX we should be able to enable/disable inserting this extra code at runtime
    {
      // we only need to insert code to update the line # if the line # changed
      // or if there is a branch inst target between here and the last time we
      // updated the line #
      if( (NodeToken_lastBeginLine != n.beginLine) ||
          (NodeToken_lastBranchTarget != il.getLastBranchTarget()) )
      {
        InstructionHandle ih;
        
        if( scope != NodeToken_lastScope )
        {
          // sf.setLineNumber( scope, n.beginLine );
          il.append( InstructionConst.ALOAD_1 ); // sf
          il.append( new ALOAD(scope.getSlot()) );
          ctx.pushInt( il, n.beginLine );
          
          ih = il.append( new INVOKEVIRTUAL( ctx.methodref(
            "oscript.util.StackFrame",
            "setLineNumber",
            "(Loscript/data/Scope;I)V"
          ) ) );
          NodeToken_lastScope = scope;
        }
        else
        {
          // sf.setLineNumber(n.beginLine);
        
          il.append( InstructionConst.ALOAD_1 ); // sf
          ctx.pushInt( il, n.beginLine );
          
          ih = il.append( new INVOKEVIRTUAL( ctx.methodref(
            "oscript.util.StackFrame",
            "setLineNumber",
            "(I)V"
          ) ) );
        }
        
        mg.addLineNumber( ih, n.beginLine );
      }
      
      NodeToken_lastBeginLine    = n.beginLine;
      NodeToken_lastBranchTarget = il.getLastBranchTarget();
    }
  }
  
  String getDebugName()
  {
    return ctx.name;
  }
  
  /**
   */
  public void visit( NodeToken n )
  {
    handle(n);
    
    // XXX handle IDENTIFIER specially... it might be better to give IDENTIFIER 
    // it's own production in the grammar
    if( n.kind == IDENTIFIER )
    {
      ctx.pushSymbol( il, n.tokenImage );
      setRetValOnStack(true);
      return;
    }
    
    if( n.cachedValue == null )
    {
      switch(n.kind)
      {
        case INTEGER_LITERAL:
        case HEX_LITERAL:
        case OCTAL_LITERAL:
        case DECIMAL_LITERAL:
        case BINARY_LITERAL:
          n.cachedValue = OExactNumber.makeExactNumber( n.otokenImage.castToExactNumber() );
          break;
        case FLOATING_POINT_LITERAL:
          n.cachedValue = OInexactNumber.makeInexactNumber( n.otokenImage.castToInexactNumber() );
          break;
        case STRING_LITERAL:
          n.cachedValue = OString.makeString( OString.chop( n.tokenImage.substring( 1, n.tokenImage.length()-1 ) ) );  // should this be intern'd???
          break;
        case REGEXP_LITERAL:
          n.cachedValue = RegExp.createRegExp( n.otokenImage );
          break;
        case TRUE:
          n.cachedValue = OBoolean.TRUE;
          break;
        case FALSE:
          n.cachedValue = OBoolean.FALSE;
          break;
        case NULL:
          n.cachedValue = Value.NULL;
          break;
        case UNDEFINED:
          n.cachedValue = Value.UNDEFINED;
          break;
        case -1:
        default:
          // leave as null
      }
    }
    
    if( n.cachedValue != null )
    {
      getInstanceConstant( n.cachedValue );
    }
  }
  
  /**
   * Add a deferred runnable, which is run after the first pass over the
   * syntax-tree.  This gives various parts of the compiler a way to defer
   * a decision until after the first pass.
   * 
   * @param r  the runnable that is invoked in an unspecified order after
   *    the first pass
   */
  void defer( Runnable r )
  {
    deferredRunnableList.add(r);
  }
  
  /**
   * push the instance constant onto the stack, setting ret-val to true.
   */
  void getInstanceConstant( Object obj )
  {
    setRetValOnStack(true);
    ctx.pushInstanceConstant( il, obj );
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ( &lt;UNIX_SELF_EXECUTABLE_COMMENT&gt; )?
   * f1 -> Program(false)
   * f2 -> &lt;EOF&gt;
   * </PRE>
   */
  public void visit( ProgramFile n )
  {
    n.f1.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ( EvaluationUnit() )*
   * </PRE>
   */
  public void visit( Program n )
  {
    for( int i=0; i<n.f0.size(); i++ )
      n.f0.elementAt(i).accept(this);
  }

  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ScopeBlock()
   *       | VariableDeclarationBlock()
   *       | FunctionDeclaration()
   *       | TryStatement()
   *       | ForLoopStatement()
   *       | WhileLoopStatement()
   *       | ConditionalStatement()
   *       | SynchronizedStatement()
   *       | ReturnStatement()
   *       | BreakStatement()
   *       | ContinueStatement()
   *       | ExpressionBlock()
   *       | ThrowBlock()
   *       | ImportBlock()
   *       | MixinBlock()
   *       | EvalBlock()
   * </PRE>
   */
  public void visit( EvaluationUnit n )
  {
    n.f0.accept(this);
    
    if(retValOnStack)
    {
      il.append( InstructionConst.POP );
      setRetValOnStack(false);
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "{"
   * f1 -> Program()
   * f2 -> "}"
   * </PRE>
   */
  public void visit( ScopeBlock n )
  {
    if( n.hasVarInScope )
      scope = new CompilerScope( this, scope, n.hasFxnInScope );    // push new scope
    n.f1.accept(this);
    if( n.hasVarInScope )
      scope = scope.pop();                                  // pop scope
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> VariableDeclaration()
   * f1 -> ";"
   * </PRE>
   */
  public void visit( VariableDeclarationBlock n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> Expression()
   * f1 -> ";"
   * </PRE>
   */
  public void visit( ExpressionBlock n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "throw"
   * f1 -> Expression()
   * f2 -> ";"
   * </PRE>
   */
  public void visit( ThrowBlock n )
  {
    handle(n.f0);
    
    // evaluate expression:
    n.f1.accept(this);
    setRetValOnStack(false);
    // check that the user isn't trying to throw (undefined)
    il.append( new INVOKESTATIC( ctx.methodref( 
      "oscript.interpreter.EvaluateVisitor",
      "returnHelper",
      "(Loscript/data/Value;)Loscript/data/Value;"
    ) ) );
    
    // throw PackagedScriptObjectException.makeExceptionWrapper(retVal)
    il.append( new INVOKESTATIC( ctx.methodref( 
      "oscript.exceptions.PackagedScriptObjectException",
      "makeExceptionWrapper2",
      "(Loscript/data/Value;)Loscript/exceptions/PackagedScriptObjectException;" 
    ) ) );
    il.append( InstructionConst.ATHROW );
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "import"
   * f1 -> Expression()
   * f2 -> ";"
   * </PRE>
   */
  public void visit( ImportBlock n )
  {
    handle(n.f0);
    
    // evaluate expression:
    n.f1.accept(this);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref( 
      "oscript.data.Value",
      "castToString",
      "()Ljava/lang/String;" 
    ) ) );
    
    il.append( new ALOAD(scope.getSlot()) );
    
    il.append( new INVOKESTATIC( ctx.methodref( 
      "oscript.compiler.CompiledNodeEvaluator",
      "importHelper",
      "(Ljava/lang/String;Loscript/data/Scope;)Loscript/data/Value;" 
    ) ) );
    
    scope.markOpen();
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "mixin"
   * f1 -> Expression()
   * f2 -> ";"
   * </PRE>
   */
  public void visit( MixinBlock n )
  {
    handle(n.f0);
    
    il.append( new ALOAD( scope.getSlot() ) );
    n.f1.accept(this);
    setRetValOnStack(false);
    il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Scope",
                                                 "mixin",
                                                 "(Loscript/data/Value;)V" ) ) );
    
    scope.markOpen();    // XXX ???  need to ensure scope object is actually created, but that doesn't make it an open scope
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "eval"
   * f1 -> Expression()
   * f2 -> ";"
   * </PRE>
   */
  public void visit( EvalBlock n )
  {
    handle(n.f0);
    
    // evaluate expression:
    n.f1.accept(this);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                 "castToString",
                                                 "()Ljava/lang/String;" ) ) );
    
    il.append( new ALOAD(scope.getSlot()) );
    
    il.append( new INVOKESTATIC( ctx.methodref( "oscript.compiler.CompiledNodeEvaluator",
                                                "evalHelper",
                                                "(Ljava/lang/String;Loscript/data/Scope;)Loscript/data/Value;" ) ) );
    
    scope.markOpen();
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> Permissions()
   * f1 -> "var"
   * f2 -> &lt;IDENTIFIER&gt;
   * f3 -> ( "=" Expression() )?
   * </PRE>
   */
  public void visit( VariableDeclaration n )
  {
    // need to handle for special tokens, before n.f3
    int permissions = getPermissions( n.f0, Reference.ATTR_PROTECTED );
    
    // order is important here... have to evaluate f3 before createMember
    // because things like:
    // 
    //   var foo = foo;
    // 
    // should create a local which is a copy of the global with the
    // same name
    if( n.f3.present() )
    {
      ((NodeSequence)(n.f3.node)).elementAt(1).accept(this);
      setRetValOnStack(false);
    }
    
    // scope.createMember( memberName, Permissions_attr )
    scope.createMember( n.f2, permissions );
    
    if( n.f3.present() )
    {
      // var.opAssign(val)
      il.append( InstructionConst.SWAP );
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "opAssign",
                                                   "(Loscript/data/Value;)V" ) ) );
    }
    else
    {
      il.append( InstructionConst.POP );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> Permissions()
   * f1 -> "function"
   * f2 -> &lt;IDENTIFIER&gt;
   * f3 -> "("
   * f4 -> ( Arglist() )?
   * f5 -> ")"
   * f6 -> ( "extends" PrimaryExpressionWithTrailingFxnCallExpList() FunctionCallExpressionList() )?
   * f7 -> "{"
   * f8 -> Program()
   * f9 -> "}"
   * </PRE>
   */
  public void visit( FunctionDeclaration n )
  {
    FunctionDeclarationTranslator.translate(n).accept(this);
  }
  
  /*=======================================================================*/
  private boolean Arglist_varargs;
  
  /**
   * <PRE>
   * f0 -> Permissions()
   * f1 -> &lt;IDENTIFIER&gt;
   * f2 -> ( "," Permissions() &lt;IDENTIFIER&gt; )*
   * f3 -> ( "..." )?
   * </PRE>
   */
  public void visit( Arglist n )
  {
    throw new ProgrammingErrorException("shouldn't get here!");
  }
  
  private int[] getArglist( Arglist n )
  {
    if( n.cachedValue == null )
    {
      int len = 2 * (n.f2.size() + 1);
      
      handle(n.f1);
      
      n.cachedValue = new int[len];
      n.cachedValue[0] = Symbol.getSymbol(n.f1.otokenImage).getId();
      n.cachedValue[1] = getPermissions( n.f0, Reference.ATTR_PRIVATE );
      
      for( int i=0; i<n.f2.size(); i++ )
      {
        NodeToken nt = (NodeToken)(((NodeSequence)(n.f2.elementAt(i))).elementAt(2));
        handle(nt);
        n.cachedValue[2*(i+1)]   = Symbol.getSymbol(nt.otokenImage).getId();
        n.cachedValue[2*(i+1)+1] = getPermissions( (Permissions)(((NodeSequence)(n.f2.elementAt(i))).elementAt(1)), Reference.ATTR_PRIVATE );
      }
    }
    
    Arglist_varargs = n.f3.present();
    
    return n.cachedValue;
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "try"
   * f1 -> EvaluationUnit()
   * f2 -> ( "catch" "(" Expression() &lt;IDENTIFIER&gt; ")" EvaluationUnit() )*
   * f3 -> ( "catch" "(" &lt;IDENTIFIER&gt; ")" EvaluationUnit() )?
   * f4 -> ( "finally" EvaluationUnit() )?
   * </PRE>
   */
  public void visit( TryStatement n )
  {
    handle(n.f0);
    
    scope.enterConditional();
    
    LinkedList gotoList = new LinkedList();
    
    final InstructionHandle[] finally_start = new InstructionHandle[1];  // array used to have ref to ref..
    LoopStackNode.CleanupInstructionGenerator g = null;
    
    if( n.f4.present() )
    {
      g = new LoopStackNode.CleanupInstructionGenerator() {
        public void generate( final CompilerInstructionList il )
        {
          final BranchInstruction JSR = new JSR(null);
          il.append(JSR);
          
          defer( new Runnable() {
            public void run() {
              JSR.setTarget( finally_start[0] );
            }
          } );
        }
      };
      loopStack.addCleanupInstructionGenerator(g);
    }
    
    
    InstructionHandle try_start = il.append( InstructionConst.NOP );
    n.f1.accept(this);
    // jump to end/finally:
    il.append( addToBranchInstructionList( gotoList, new GOTO(null) ) );
    InstructionHandle try_end       = il.append( InstructionConst.NOP );
    InstructionHandle handler_start = il.append( InstructionConst.NOP );
    
    for( int i=0; i<n.f2.size(); i++ )
    {
      // stack:   ..., e
      
      NodeSequence seq = (NodeSequence)(n.f2.elementAt(i));
      
      // store java exception on stack:       ..., e  ->  ..., e, e
      il.append( InstructionConst.DUP );
      
      // get the script exception object:     ..., e, e  ->  ..., e, e.val
      il.append( new GETFIELD( ctx.fieldref(
        "oscript.exceptions.PackagedScriptObjectException",
        "val",
        "Loscript/data/Value;"
      ) ) );
      
      // check the type of the script exception:    ... e, e.val -> e, e.val
      il.append( InstructionConst.DUP );    //  ... e, e.val -> e, e.val, e.val
      seq.elementAt(2).accept(this);            //  ... e, e.val, e.val -> e, e.val, e.val, type
      setRetValOnStack(false);
      il.append( new INVOKEVIRTUAL( ctx.methodref(
        "oscript.data.Value",
        "bopInstanceOf",
        "(Loscript/data/Value;)Loscript/data/Value;"
      ) ) );
      il.append( new INVOKEVIRTUAL( ctx.methodref(
        "oscript.data.Value",
        "castToBoolean",
        "()Z"
      ) ) );
      
      BranchInstruction IFEQ = new IFEQ(null);  //  ... e, e.val, bool -> e, e.val
      il.append(IFEQ);
      
      scope = new CompilerScope( this, scope, true );     // push scope  (XXX need to know hasFxnInScope)
      
      // createMember & opAssign:     ..., e, e.val  ->  ..., e
      scope.createMember( (NodeToken)(seq.elementAt(3)), 0 );
      il.append( InstructionConst.SWAP );
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "opAssign",
                                                   "(Loscript/data/Value;)V" ) ) );
      
      // don't need stored java exception on stack:    ..., e  ->  ...
      il.append( InstructionConst.POP );
      
      seq.elementAt(5).accept(this);
      
      scope = scope.pop();                        // pop scope
      
      // jump to end/finally:
      il.append( addToBranchInstructionList( gotoList, new GOTO(null) ) );
      
      il.setNextAsTarget(IFEQ);
      
      // don't need script exception on stack:    ..., e, e.val  ->  ..., e
      il.append( InstructionConst.POP );
    }
    
    if( n.f3.present() )
    {
      // stack:   ..., e
      
      NodeSequence seq = (NodeSequence)(n.f3.node);
      
      // get the script exception object:     ..., e  ->  ..., e.val
      il.append( new GETFIELD( ctx.fieldref(
        "oscript.exceptions.PackagedScriptObjectException",
        "val",
        "Loscript/data/Value;"
      ) ) );
      
      scope = new CompilerScope( this, scope, false );    // push scope
      
      // createMember & opAssign:     ..., e.val  ->  ...
      scope.createMember( (NodeToken)(seq.elementAt(2)), 0 );
      il.append( InstructionConst.SWAP );
      il.append( new INVOKEVIRTUAL( ctx.methodref(
        "oscript.data.Value",
        "opAssign",
        "(Loscript/data/Value;)V"
      ) ) );
      
      seq.elementAt(4).accept(this);
      
      scope = scope.pop();                        // pop scope
      
      // jump to end/finally:
      il.append( addToBranchInstructionList( gotoList, new GOTO(null) ) ); // XXX ??? do I need this?
    }
    else
    {
      // don't need stored java exception on stack:    ..., e  ->  ...
      il.append( InstructionConst.ATHROW );
    }
    
    if( (n.f2.size() > 0) || n.f3.present() )
      mg.addExceptionHandler( try_start, try_end, handler_start, CompilerContext.EXCEPTION_TYPE );
    
    for( Iterator itr=gotoList.iterator(); itr.hasNext(); )
      il.setNextAsTarget( (BranchInstruction)(itr.next()) );
    
    if( n.f4.present() )
    {
      BranchInstruction REG_JSR  = new JSR(null);
      BranchInstruction GOTO_END = new GOTO(null);
      
      InstructionHandle any_exception_end = il.append(REG_JSR);   // target of "try" and "catch" gotos
      il.append(GOTO_END);                                        // normal execution skips exception handling glue
      InstructionHandle any_handler_start = il.append( InstructionConst.NOP );  // begin handler for <any>
      
      // we need to store the exception value:
      int exceptionSlot = mg.addLocalVariable( 
        CompilerContext.makeUniqueIdentifierName("e"),
        CompilerContext.ANY_EXCEPTION_TYPE,
        null,                  // XXX start
        null                   // XXX end
      ).getIndex();
      
      BranchInstruction ANY_EXCEPTION_JSR = new JSR(null);
      
      il.append( new ASTORE(exceptionSlot) );
      il.append(ANY_EXCEPTION_JSR);
      il.append( new ALOAD(exceptionSlot) );
      il.append( InstructionConst.ATHROW );
      
      
      il.setNextAsTarget(REG_JSR);
      il.setNextAsTarget(ANY_EXCEPTION_JSR);
      
      int retAddrSlot = mg.addLocalVariable(
        CompilerContext.makeUniqueIdentifierName("retaddr"),
        org.apache.bcel.generic.Type.OBJECT,
        null,                  // XXX start
        null                   // XXX end
      ).getIndex();
      
      loopStack.removeCleanupInstructionGenerator(g);
      finally_start[0] = il.append( new ASTORE(retAddrSlot) );
      
      ((NodeSequence)(n.f4.node)).elementAt(1).accept(this);
      
      il.append( new RET(retAddrSlot) );
      
      mg.addExceptionHandler( try_start, any_exception_end, any_handler_start, CompilerContext.ANY_EXCEPTION_TYPE );
      
      il.setNextAsTarget(GOTO_END);
    }
    
    scope.leaveConditional();
  }
  
  // XXX move this:
  private static final BranchInstruction addToBranchInstructionList( LinkedList list, BranchInstruction bi )
  {
    list.addFirst(bi);
    return bi;
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "for"
   * f1 -> "("
   * f2 -> ( PreLoopStatement() )?
   * f3 -> ";"
   * f4 -> ( Expression() )?
   * f5 -> ";"
   * f6 -> ( Expression() )?
   * f7 -> ")"
   * f8 -> EvaluationUnit()
   * </PRE>
   */
  public void visit( ForLoopStatement n )
  {
    ForLoopStatementTranslator.translate(n).accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "for"
   * f1 -> "("
   * f2 -> PreLoopStatement()
   * f3 -> ":"
   * f4 -> Expression()
   * f5 -> ")"
   * f6 -> EvaluationUnit()
   * </PRE>
   */
  public void visit( CollectionForLoopStatement n )
  {
    CollectionForLoopStatementTranslator.translate(n).accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> VariableDeclaration()
   *       | Expression()
   * </PRE>
   */
  public void visit( PreLoopStatement n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "while"
   * f1 -> "("
   * f2 -> Expression()
   * f3 -> ")"
   * f4 -> EvaluationUnit()
   * </PRE>
   */
  public void visit( WhileLoopStatement n )
  {
    handle(n.f0);
    
    BranchInstruction IFEQ = new IFEQ(null);
    BranchInstruction GOTO = new GOTO(null);
    
    il.setNextAsTarget(GOTO);
    n.f2.accept(this);
    setRetValOnStack(false);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                 "castToBooleanSoft",
                                                 "()Z" ) ) );
    
    il.append(IFEQ);
    
    // push a new LoopStackNode:
    loopStack = new LoopStackNode(loopStack);
    
    scope.enterConditional();
    n.f4.accept(this);
    scope.leaveConditional();
    
    il.append(GOTO);
    
    il.setNextAsTarget(IFEQ);
    
    for( Iterator itr=loopStack.getContinueInstructions().iterator(); itr.hasNext(); )
      ((BranchInstruction)(itr.next())).setTarget( GOTO.getTarget() );
    
    for( Iterator itr=loopStack.getBreakInstructions().iterator(); itr.hasNext(); )
      il.setNextAsTarget( (BranchInstruction)(itr.next()) );
    
    // pop LoopStackNode:
    loopStack = loopStack.pop();
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "if"
   * f1 -> "("
   * f2 -> Expression()
   * f3 -> ")"
   * f4 -> EvaluationUnit()
   * f5 -> ( "else" EvaluationUnit() )?
   * </PRE>
   */
  public void visit( ConditionalStatement n )
  {
    handle(n.f0);
    
    n.f2.accept(this);
    setRetValOnStack(false);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                 "castToBooleanSoft",
                                                 "()Z" ) ) );
    
    BranchInstruction IFEQ = new IFEQ(null);
    il.append(IFEQ);
    
    scope.enterConditional();
    n.f4.accept(this);
    
    if( n.f5.present() )
    {
      BranchInstruction GOTO = new GOTO(null);
      il.append(GOTO);
      
      il.setNextAsTarget(IFEQ);
      ((NodeSequence)(n.f5.node)).elementAt(1).accept(this);
      
      il.setNextAsTarget(GOTO);
    }
    else
    {
      il.setNextAsTarget(IFEQ);
    }
    
    scope.leaveConditional();
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "synchronized"
   * f1 -> "("
   * f2 -> Expression()
   * f3 -> ")"
   * f4 -> EvaluationUnit()
   * </PRE>
   */
  public void visit( SynchronizedStatement n )
  {
    n.f2.accept(this);
    setRetValOnStack(false);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                 "getMonitor",
                                                 "()Ljava/lang/Object;" ) ) );
    
    // we need to store the monitor value:
    LocalVariableGen monLg = mg.addLocalVariable( CompilerContext.makeUniqueIdentifierName("monitor"),
                                                  CompilerContext.VALUE_TYPE,
                                                  null,     // XXX
                                                  null );   // XXX
    final int monSlot = monLg.getIndex();
    
    LoopStackNode.CleanupInstructionGenerator g = new LoopStackNode.CleanupInstructionGenerator() {
      public void generate( CompilerInstructionList il )
      {
        il.append( new ALOAD(monSlot) );
        il.append( InstructionConst.MONITOREXIT );
      }
    };
    
    loopStack.addCleanupInstructionGenerator(g);
    
    il.append( InstructionConst.DUP );
    il.append( new ASTORE(monSlot) );
    InstructionHandle try_start = il.append( InstructionConst.MONITORENTER );
    
    n.f4.accept(this);
    
    loopStack.removeCleanupInstructionGenerator(g);
    
    il.append( new ALOAD(monSlot) );
    InstructionHandle try_end = il.append( InstructionConst.MONITOREXIT );
    BranchInstruction GOTO = new GOTO(null);
    il.append(GOTO);
    
    InstructionHandle handler_start = il.append( new ALOAD(monSlot) );
    il.append( InstructionConst.MONITOREXIT );
    il.append( InstructionConst.ATHROW );
    
    il.setNextAsTarget(GOTO);
    
    mg.addExceptionHandler( try_start, try_end, handler_start, CompilerContext.ANY_EXCEPTION_TYPE );
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "return"
   * f1 -> ( Expression() )?
   * </PRE>
   */
  public void visit( ReturnStatement n )
  {
    handle(n.f0);
    
    if( n.f1.present() )
    {
      n.f1.node.accept(this);  
      checkRetValOnStack(true);
      
      il.append( new INVOKESTATIC( ctx.methodref( "oscript.interpreter.EvaluateVisitor",
                                                  "returnHelper",
                                                  "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
    else
    {
      getInstanceConstant( Value.UNDEFINED );
      setRetValOnStack(false);
    }
    
    int retValSlot = mg.addLocalVariable( 
      CompilerContext.makeUniqueIdentifierName("retVal"),
      CompilerContext.VALUE_TYPE,
      null,                  // XXX start
      null                   // XXX end
    ).getIndex();
    
    il.append( new ASTORE(retValSlot) );
    loopStack.insertCleanupInstructions( il, true );
    il.append( new ALOAD(retValSlot) );    
    il.append( InstructionConst.ARETURN );
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "break"
   * f1 -> ";"
   * </PRE>
   */
  public void visit( BreakStatement n )
  {
    handle(n.f0);
    
    loopStack.insertCleanupInstructions( il, false );
    
    BranchInstruction GOTO = new GOTO(null);
    il.append(GOTO);
    
    loopStack.addBreakBranchInstruction(GOTO);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "continue"
   * f1 -> ";"
   * </PRE>
   */
  public void visit( ContinueStatement n )
  {
    handle(n.f0);
    
    loopStack.insertCleanupInstructions( il, false );
    
    BranchInstruction GOTO = new GOTO(null);
    il.append(GOTO);
    
    loopStack.addContinueBranchInstruction(GOTO);
  }
  
  /*=======================================================================*/
  /**
   * Note, <i>Expression</i> always returns a value on the stack, even
   * if that value is <code>Value.NULL</code>.
   * 
   * <PRE>
   * f0 -> AssignmentExpression()
   * f1 -> ( "," AssignmentExpression() )*
   * </PRE>
   */
  public void visit( Expression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      // get rid of previous value on stack:
      il.append( InstructionConst.POP );
      setRetValOnStack(false);
      
      ((NodeSequence)(n.f1.elementAt(i))).elementAt(1).accept(this);
    }
    
    checkRetValOnStack(true);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "("
   * f1 -> ( FunctionCallExpressionListBody() )?
   * f2 -> ")"
   * </PRE>
   */
  public void visit( FunctionCallExpressionList n )
  {
    n.f0.accept(this);                  // to record last NodeToken
    
    if( n.f1.present() )
    {
      FunctionCallExpressionList_sfIsNull = false;
      n.f1.node.accept(this);
    }
    else
    {
      FunctionCallExpressionList_sfIsNull = true;
      allocateMemberTable_allocateFromStack = false;
      il.append( InstructionConst.ACONST_NULL );
    }
    
    setRetValOnStack(true);
  }
  
  /**
   * If there were no args, the FunctionCallExpressionList visitor will
   * push <code>null</code> onto the stack
   */
  private boolean FunctionCallExpressionList_sfIsNull = false;
  
  /**
   * Set to true before accept()ing the the {@link FunctionCallExpressionList}
   * (or calling {@link #allocateMemberTable}, to indicate that the member-
   * table should be allocated from the stack (rather than allocating an
   * <code>OArray</code>).  This is reset back to <code>false</code> once
   * the <code>FunctionCallExpressionList</code> is visited.
   * <p>
   * If set to true, the code that the member-table is created for should
   * take care to call {@link MemberTable#free()}
   */
  private boolean allocateMemberTable_allocateFromStack = false;
  
  private void allocateMemberTable( int sz )
  {
    if( allocateMemberTable_allocateFromStack )
    {
      il.append( InstructionConst.ALOAD_1 );       // sf
      ctx.pushInt( il, sz );
      il.append( new INVOKEVIRTUAL( ctx.methodref(
        "oscript.util.StackFrame",
        "allocateMemberTable",
        "(I)Loscript/util/MemberTableImpl;"
      ) ) );
      allocateMemberTable_allocateFromStack = false;
    }
    else
    {
      il.append( new NEW( ctx.cp.addClass("oscript.data.OArray") ) );
      il.append( InstructionConst.DUP );
      ctx.pushInt( il, sz );
      il.append( new INVOKESPECIAL( ctx.methodref(
        "oscript.data.OArray",
        "<init>",
        "(I)V"
      ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> AssignmentExpression()
   * f1 -> ( "," AssignmentExpression() )*
   * </PRE>
   */
  public void visit( FunctionCallExpressionListBody n )
  {
    allocateMemberTable( 1 + n.f1.size() );
    
    int cnt = 0;
    
    il.append( InstructionConst.DUP );
    n.f0.accept(this);
    setRetValOnStack(false);
    cnt++;
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      if( cnt == 4 )
      {
        il.append( new INVOKEINTERFACE( ctx.ifmethodref(
          "oscript.util.MemberTable",
          PUSH_METHOD_NAMES[cnt],
          PUSH_METHOD_SIGNATURES[cnt]
        ), cnt + 1 ) );
        il.append( InstructionConst.DUP );
        cnt = 0;
      }
      
      NodeSequence seq = (NodeSequence)(n.f1.elementAt(i));
      seq.elementAt(1).accept(this);
      setRetValOnStack(false);
      cnt++;
    }
    
    if( cnt > 0 )
    {
      il.append( new INVOKEINTERFACE( ctx.ifmethodref(
        "oscript.util.MemberTable",
        PUSH_METHOD_NAMES[cnt],
        PUSH_METHOD_SIGNATURES[cnt]
      ), cnt + 1 ) );
    }
    else
    {
      // get rid of extra member-table reference on top of the stack
      il.append( InstructionConst.POP );
    }
  }
  
  private static final String[] PUSH_METHOD_NAMES = new String[] {
    null,
    "push1",
    "push2",
    "push3",
    "push4"
  };
  
  private static final String[] PUSH_METHOD_SIGNATURES = new String[] {
    null,
    "(Loscript/data/Value;)V",
    "(Loscript/data/Value;Loscript/data/Value;)V",
    "(Loscript/data/Value;Loscript/data/Value;Loscript/data/Value;)V",
    "(Loscript/data/Value;Loscript/data/Value;Loscript/data/Value;Loscript/data/Value;)V"
  };
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ConditionalExpression()
   * f1 -> ( ( "=" | "+=" | "-=" | "*=" | "/=" | "%=" | "&gt;&gt;=" | "&lt;&lt;=" | "&gt;&gt;&gt;=" | "&=" | "^=" | "|=" ) ConditionalExpression() )*
   * </PRE>
   */
  public void visit( AssignmentExpression n )
  {
    // the tricky part here is that things need to be evaluated backwards:
    int lastOp = -1;
    
    for( int i=n.f1.size()-1; i>= -1; i-- )
    {
      int op = lastOp;
      
      // evaluate <i>CondidtionalExpression</i>, should return a val on stack:
      if( i>= 0 )
      {
        NodeSequence seq = (NodeSequence)(n.f1.elementAt(i));
        seq.elementAt(1).accept(this);
        lastOp = ((NodeToken)(((NodeChoice)(seq.elementAt(0))).choice)).kind;
      }
      else
      {
        n.f0.accept(this);
      }
      
      // keep the val on the stack, don't return it
      setRetValOnStack(false);
      
      if( op != -1 )
      {
        // stack: ..., lastVal, val 
        
        String methodName = null;
        
        switch(op)
        {
          case ASSIGN:
            // no-op
            break;
          case PLUSASSIGN:
            methodName = "bopPlus";
            break;
          case MINUSASSIGN:
            methodName = "bopMinus";
            break;
          case STARASSIGN:
            methodName = "bopMultiply";
            break;
          case SLASHASSIGN:
            methodName = "bopDivide";
            break;
          case ANDASSIGN:
            methodName = "bopBitwiseAnd";
            break;
          case ORASSIGN:
            methodName = "bopBitwiseOr";
            break;
          case XORASSIGN:
            methodName = "bopBitwiseXor";
            break;
          case REMASSIGN:
            methodName = "bopRemainder";
            break;
          case LSHIFTASSIGN:
            methodName = "bopLeftShift";
            break;
          case RSIGNEDSHIFTASSIGN:
            methodName = "bopSignedRightShift";
            break;
          case RUNSIGNEDSHIFTASSIGN:
            methodName = "bopUnsignedRightShift";
            break;
          default:
            throw new ProgrammingErrorException("unknown operator: " + op);
        }
        
        if( methodName != null )
        {
          // stack: ..., lastVal, val => ..., val, val, lastVal
          il.append( InstructionConst.DUP_X1 );
          il.append( InstructionConst.SWAP );
          
          // stack: ..., val, val, lastVal => ..., val, lastVal
          il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
            methodName,
            "(Loscript/data/Value;)Loscript/data/Value;"
          ) ) );
        }
        else
        {
          // stack: ..., lastVal, val => ..., val, lastVal
          il.append( InstructionConst.SWAP );
        }
        
        // stack: ..., val, lastVal => ..., lastVal, val, lastVal:
        il.append( InstructionConst.DUP_X1 );
        
        // stack: ..., lastVal, val, lastVal => ..., lastVal
        il.append( new INVOKEVIRTUAL( ctx.methodref(
          "oscript.data.Value",
          "opAssign",
          "(Loscript/data/Value;)V"
        ) ) );
      }
    }
    
    setRetValOnStack(true);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> LogicalOrExpression()
   * f1 -> ( "?" LogicalOrExpression() ":" LogicalOrExpression() )?
   * </PRE>
   */
  public void visit( ConditionalExpression n )
  {
    n.f0.accept(this);
    
    if( n.f1.present() )
    {
      setRetValOnStack(false);
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "castToBooleanSoft",
                                                   "()Z" ) ) );
      
      scope.enterConditional();
      
      BranchInstruction IFEQ = new IFEQ(null);
      il.append(IFEQ);
      
      ((NodeSequence)(n.f1.node)).elementAt(1).accept(this);
      setRetValOnStack(false);
      
      BranchInstruction GOTO = new GOTO(null);
      il.append(GOTO);
      
      il.setNextAsTarget(IFEQ);
      
      ((NodeSequence)(n.f1.node)).elementAt(3).accept(this);
      
      il.setNextAsTarget(GOTO);
      
      scope.leaveConditional();
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> LogicalAndExpression()
   * f1 -> ( "||" LogicalAndExpression() )*
   * </PRE>
   */
  public void visit( LogicalOrExpression n )
  {
    n.f0.accept(this);
    
    if( n.f1.present() )
    {
      LinkedList branchInstructionList = new LinkedList();
      
      scope.enterConditional();
      
      for( int i=0; i<n.f1.size(); i++ )
      {
        setRetValOnStack(false);
        
        il.append( InstructionConst.DUP );
        il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                     "castToBooleanSoft",
                                                     "()Z" ) ) );
        
        BranchInstruction IFNE = new IFNE(null);
        branchInstructionList.add(IFNE);
        il.append(IFNE);
        
        il.append( InstructionConst.POP );
        
        ((NodeSequence)(n.f1.elementAt(i))).elementAt(1).accept(this);
      }
      
      for( Iterator itr=branchInstructionList.iterator(); itr.hasNext(); )
      {
        il.setNextAsTarget( (BranchInstruction)(itr.next()) );
      }
      
      scope.leaveConditional();
    }
  }
 
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> BitwiseOrExpression()
   * f1 -> ( "&&" BitwiseOrExpression() )*
   * </PRE>
   */
  public void visit( LogicalAndExpression n )
  {
    n.f0.accept(this);
    
    if( n.f1.present() )
    {
      LinkedList branchInstructionList = new LinkedList();
      
      scope.enterConditional();
      
      for( int i=0; i<n.f1.size(); i++ )
      {
        setRetValOnStack(false);
        
        il.append( InstructionConst.DUP );
        il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                     "castToBooleanSoft",
                                                     "()Z" ) ) );
        
        BranchInstruction IFEQ = new IFEQ(null);
        branchInstructionList.add(IFEQ);
        il.append(IFEQ);
        
        il.append( InstructionConst.POP );
        
        ((NodeSequence)(n.f1.elementAt(i))).elementAt(1).accept(this);
      }
      
      for( Iterator itr=branchInstructionList.iterator(); itr.hasNext(); )
      {
        il.setNextAsTarget( (BranchInstruction)(itr.next()) );
      }
      
      scope.leaveConditional();
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> BitwiseXorExpression()
   * f1 -> ( "|" BitwiseXorExpression() )*
   * </PRE>
   */
  public void visit( BitwiseOrExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      ((NodeSequence)(n.f1.elementAt(i))).elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "bopBitwiseOr",
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> BitwiseAndExpression()
   * f1 -> ( "^" BitwiseAndExpression() )*
   * </PRE>
   */
  public void visit( BitwiseXorExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      ((NodeSequence)(n.f1.elementAt(i))).elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "bopBitwiseXor",
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }

  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> EqualityExpression()
   * f1 -> ( "&" EqualityExpression() )*
   * </PRE>
   */
  public void visit( BitwiseAndExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      ((NodeSequence)(n.f1.elementAt(i))).elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "bopBitwiseAnd",
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }

  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> RelationalExpression()
   * f1 -> ( ( "==" | "!=" ) RelationalExpression() )*
   * </PRE>
   */
  public void visit( EqualityExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      NodeSequence seq = (NodeSequence)(n.f1.elementAt(i));
      NodeToken    op  = (NodeToken)(((NodeChoice)(seq.elementAt(0))).choice);
      
      seq.elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      String methodName = null;
      switch(op.kind)
      {
        case EQ:
          methodName = "bopEquals";
          break;
        case NE:
          methodName = "bopNotEquals";
          break;
        default:
          throw new ProgrammingErrorException("bad binary op: " + OscriptParser.getTokenString(op.kind));
      }
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   methodName,
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ShiftExpression()
   * f1 -> ( ( "&lt;" | "&gt;" | "&gt;=" | "&lt;=" | "instanceof" ) ShiftExpression() )*
   * </PRE>
   */
  public void visit( RelationalExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      NodeSequence seq = (NodeSequence)(n.f1.elementAt(i));
      NodeToken    op  = (NodeToken)(((NodeChoice)(seq.elementAt(0))).choice);
      
      seq.elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      String methodName = null;
      switch(op.kind)
      {
        case LT:
          methodName = "bopLessThan";
          break;
        case GT:
          methodName = "bopGreaterThan";
          break;
        case LE:
          methodName = "bopLessThanOrEquals";
          break;
        case GE:
          methodName = "bopGreaterThanOrEquals";
          break;
        case INSTANCEOF:
          methodName = "bopInstanceOf";
          break;
        default:
          throw new ProgrammingErrorException("bad binary op: " + OscriptParser.getTokenString(op.kind));
      }
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   methodName,
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> AdditiveExpression()
   * f1 -> ( ( "&lt;&lt;" | "&gt;&gt;" | "&gt;&gt;&gt;" ) AdditiveExpression() )*
   * </PRE>
   */
  public void visit( ShiftExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      NodeSequence seq = (NodeSequence)(n.f1.elementAt(i));
      NodeToken    op  = (NodeToken)(((NodeChoice)(seq.elementAt(0))).choice);
      
      seq.elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      String methodName = null;
      switch(op.kind)
      {
        case LSHIFT:
          methodName = "bopLeftShift";
          break;
        case RSIGNEDSHIFT:
          methodName = "bopSignedRightShift";
          break;
        case RUNSIGNEDSHIFT:
          methodName = "bopUnsignedRightShift";
          break;
        default:
          throw new ProgrammingErrorException("bad binary op: " + OscriptParser.getTokenString(op.kind));
      }
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   methodName,
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> MultiplicativeExpression()
   * f1 -> ( ( "+" | "-" ) MultiplicativeExpression() )*
   * </PRE>
   */
  public void visit( AdditiveExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      NodeSequence seq = (NodeSequence)(n.f1.elementAt(i));
      NodeToken    op  = (NodeToken)(((NodeChoice)(seq.elementAt(0))).choice);
      
      seq.elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      String methodName = null;
      switch(op.kind)
      {
        case PLUS:
          methodName = "bopPlus";
          break;
        case MINUS:
          methodName = "bopMinus";
          break;
        default:
          throw new ProgrammingErrorException("bad binary op: " + OscriptParser.getTokenString(op.kind));
      }
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   methodName,
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }

  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> UnaryExpression()
   * f1 -> ( ( "*" | "/" | "%" ) UnaryExpression() )*
   * </PRE>
   */
  public void visit( MultiplicativeExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      setRetValOnStack(false);
      
      NodeSequence seq = (NodeSequence)(n.f1.elementAt(i));
      NodeToken    op  = (NodeToken)(((NodeChoice)(seq.elementAt(0))).choice);
      
      seq.elementAt(1).accept(this);
      checkRetValOnStack(true);
      
      String methodName = null;
      switch(op.kind)
      {
        case STAR:
          methodName = "bopMultiply";
          break;
        case SLASH:
          methodName = "bopDivide";
          break;
        case REM:
          methodName = "bopRemainder";
          break;
        default:
          throw new ProgrammingErrorException("bad binary op: " + OscriptParser.getTokenString(op.kind));
      }
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   methodName,
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ( ( "++" | "--" | "+" | "-" | "~" | "!" ) )?
   * f1 -> PostfixExpression()
   * </PRE>
   */
  public void visit( UnaryExpression n )
  {
    n.f1.accept(this);
    
    if( n.f0.present() )
    {
      checkRetValOnStack(true);
      NodeToken op = (NodeToken)(((NodeChoice)(n.f0.node)).choice);
      
      String  methodName = null;
      boolean doOpAssign = false;
      
      switch(op.kind)
      {
        case INCR:
          doOpAssign = true;
          methodName = "uopIncrement";
          break;
        case DECR:
          doOpAssign = true;
          methodName = "uopDecrement";
          break;
        case PLUS:
          methodName = "uopPlus";
          break;
        case MINUS:
          methodName = "uopMinus";
          break;
        case TILDE:
          methodName = "uopBitwiseNot";
          break;
        case BANG:
          methodName = "uopLogicalNot";
          break;
        default:
          throw new ProgrammingErrorException("bad unary op: " + OscriptParser.getTokenString(op.kind));
      }
      
      if(doOpAssign)
      {
        il.append( InstructionConst.DUP );
      }
      
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   methodName,
                                                   "()Loscript/data/Value;" ) ) );
      
      if( doOpAssign )
      {
        il.append( InstructionConst.DUP_X1 );
        il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                     "opAssign",
                                                     "(Loscript/data/Value;)V" ) ) );
      }
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> TypeExpression()
   * f1 -> ( "++" | "--" )?
   * </PRE>
   */
  public void visit( PostfixExpression n )
  {
    n.f0.accept(this);
    
    if( n.f1.present() )
    {
      checkRetValOnStack(true);
      NodeToken op = (NodeToken)(((NodeChoice)(n.f1.node)).choice);
      
      String  methodName = null;
      
      switch(op.kind)
      {
        case INCR:
          methodName = "uopIncrement";
          break;
        case DECR:
          methodName = "uopDecrement";
          break;
        default:
          throw new ProgrammingErrorException("bad unary op: " + OscriptParser.getTokenString(op.kind));
      }
      
      // the result value is the original value... since you can't assign to it,
      // unhand() works fine to get the original value:
      il.append( InstructionConst.DUP );
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "unhand",
                                                   "()Loscript/data/Value;" ) ) );
      il.append( InstructionConst.SWAP );
      
      // now invoke the operation and assign it to the reference:
      il.append( InstructionConst.DUP );
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   methodName,
                                                   "()Loscript/data/Value;" ) ) );
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "opAssign",
                                                   "(Loscript/data/Value;)V" ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> AllocationExpression()
   *       | CastExpression()
   *       | PrimaryExpression()
   * </PRE>
   */
  public void visit( TypeExpression n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "new"
   * f1 -> PrimaryExpressionWithTrailingFxnCallExpList()
   * f2 -> FunctionCallExpressionList()
   * </PRE>
   */
  public void visit( AllocationExpression n )
  {
    handle(n.f0);
    
    n.f1.accept(this);
    setRetValOnStack(false);
    
    il.append( InstructionConst.ALOAD_1 );         // sf
    
    n.f2.accept(this);
    checkRetValOnStack(true);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref( 
      "oscript.data.Value",
      "callAsConstructor",
      "(Loscript/util/StackFrame;Loscript/util/MemberTable;)Loscript/data/Value;" 
    ) ) );
  }

  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "("
   * f1 -> PrimaryExpressionNotFunction()
   * f2 -> ")"
   * f3 -> PrimaryExpression()
   * </PRE>
   */
  public void visit( CastExpression n )
  {
    handle(n.f0);
    
    n.f1.accept(this);
    setRetValOnStack(false);
    
    n.f3.accept(this);
    checkRetValOnStack(true);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref( 
      "oscript.data.Value",
      "bopCast",
      "(Loscript/data/Value;)Loscript/data/Value;" 
    ) ) );
  }

  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> PrimaryPrefix()
   * f1 -> ( PrimaryPostfix() )*
   * </PRE>
   */
  public void visit( PrimaryExpression n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      checkRetValOnStack(true);
      n.f1.elementAt(i).accept(this);
    }
    
    checkRetValOnStack(true);
  }

  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> PrimaryPrefix()
   * f1 -> ( PrimaryPostfix() )*
   * </PRE>
   */
  public void visit( PrimaryExpressionNotFunction n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      checkRetValOnStack(true);
      n.f1.elementAt(i).accept(this);
    }
    
    checkRetValOnStack(true);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> PrimaryPrefix()
   * f1 -> ( PrimaryPostfixWithTrailingFxnCallExpList() )*
   * </PRE>
   */
  public void visit( PrimaryExpressionWithTrailingFxnCallExpList n )
  {
    n.f0.accept(this);
    
    for( int i=0; i<n.f1.size(); i++ )
    {
      checkRetValOnStack(true);
      n.f1.elementAt(i).accept(this);
    }
    
    checkRetValOnStack(true);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> PrimaryPrefixNotFunction()
   *       | FunctionPrimaryPrefix()
   *       | ShorthandFunctionPrimaryPrefix()
   * </PRE>
   */
  public void visit( PrimaryPrefix n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ThisPrimaryPrefix()
   *       | SuperPrimaryPrefix()
   *       | CalleePrimaryPrefix()
   *       | IdentifierPrimaryPrefix()
   *       | ParenPrimaryPrefix()
   *       | ArrayDeclarationPrimaryPrefix()
   *       | Literal()
   * </PRE>
   */
  public void visit( PrimaryPrefixNotFunction n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "this"
   * </PRE>
   */
  public void visit( ThisPrimaryPrefix n )
  {
    handle(n.f0);
    
    if( thisSlot == -1 )
    {
      LocalVariableGen lg = 
        mg.addLocalVariable( CompilerContext.makeUniqueIdentifierName("this"), CompilerContext.VALUE_TYPE, null, null );
      thisSlot = lg.getIndex();
      
      // insert at head in reverse order
      il.insert( new ASTORE(thisSlot) );
      il.insert( new INVOKEVIRTUAL( ctx.methodref(
        "oscript.data.Scope",
        "getThis",
        "()Loscript/data/Value;"
      ) ) );
      il.insert( InstructionConst.ALOAD_2 );  // scope
    }
    
    il.append( new ALOAD(thisSlot) );
    setRetValOnStack(true);
  }
  
  private int thisSlot = -1;
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "super"
   * </PRE>
   */
  public void visit( SuperPrimaryPrefix n )
  {
    handle(n.f0);
    
    if( superSlot == -1 )
    {
      LocalVariableGen lg = 
        mg.addLocalVariable( CompilerContext.makeUniqueIdentifierName("super"), CompilerContext.VALUE_TYPE, null, null );
      superSlot= lg.getIndex();
      
      // insert at head in reverse order
      il.insert( new ASTORE(superSlot) );
      il.insert( new INVOKEVIRTUAL( ctx.methodref( 
        "oscript.data.Scope",
        "getSuper",
        "()Loscript/data/Value;"
      ) ) );
      il.insert( InstructionConst.ALOAD_2 );  // scope
    }
    
    il.append( new ALOAD(superSlot) );
    setRetValOnStack(true);
  }
  
  private int superSlot = -1;
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "callee"
   * </PRE>
   */
  public void visit( CalleePrimaryPrefix n )
  {
    handle(n.f0);
    
    if( calleeSlot == -1 )
    {
      LocalVariableGen lg = 
        mg.addLocalVariable( CompilerContext.makeUniqueIdentifierName("callee"), CompilerContext.VALUE_TYPE, null, null );
      calleeSlot = lg.getIndex();
      
      // insert at head in reverse order
      il.insert( new ASTORE(calleeSlot) );
      il.insert( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Scope",
                                                   "getCallee",
                                                   "()Loscript/data/Value;" ) ) );
      il.insert( InstructionConst.ALOAD_2 );  // scope
    }
    
    il.append( new ALOAD(calleeSlot) );
    setRetValOnStack(true);
  }
  
  private int calleeSlot = -1;
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> &lt;IDENTIFIER&gt;
   * </PRE>
   */
  public void visit( IdentifierPrimaryPrefix n )
  {
    scope.lookupInScope( this, n.f0 );
    setRetValOnStack(true);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "("
   * f1 -> Expression()
   * f2 -> ")"
   * </PRE>
   */
  public void visit( ParenPrimaryPrefix n )
  {
    n.f1.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "function"
   * f1 -> "("
   * f2 -> ( Arglist() )?
   * f3 -> ")"
   * f4 -> ( "extends" PrimaryExpressionWithTrailingFxnCallExpList() FunctionCallExpressionList() )?
   * f5 -> "{"
   * f6 -> Program()
   * f7 -> "}"
   * </PRE>
   */
  public void visit( FunctionPrimaryPrefix n )
  {
    handle(n.f0);
    // new Function( scope, superFxn, n.fd )
    il.append( new NEW( ctx.cp.addClass("oscript.data.Function") ) );
    il.append( InstructionConst.DUP );
    il.append( new ALOAD(scope.getSlot()) );           // scope
    
    if( n.f4.present() )
    {
      // superFxn
      ((NodeSequence)(n.f4.node)).elementAt(1).accept(this);
      setRetValOnStack(false);
      il.append( new INVOKEVIRTUAL( ctx.methodref(
        "oscript.data.Value",
        "unhand",
        "()Loscript/data/Value;"
      ) ) );
    }
    else
    {
      il.append( InstructionConst.ACONST_NULL );   // superFxn
    }
    
    
    // fd
    {
      Value oname = Symbol.getSymbol( n.id );
      String name = oname.castToString();
      
      int[]   argIds;
      boolean varargs;
      
      // get arglist:
      if( n.f2.present() )
      {
        argIds  = getArglist( (Arglist)(n.f2.node) );
        varargs = Arglist_varargs;
      }
      else
      {
        argIds  = CompilerContext.EMPTY_ARG_IDS;
        varargs = false;
      }
      
      // get extends evaluator:
      int extendsIdx = -1;
      if( n.f4.present() )
      {
        FunctionCallExpressionList fcel = 
          (FunctionCallExpressionList)(((NodeSequence)(n.f4.node)).elementAt(2));
        
        extendsIdx = 
          (new CompilerVisitor( ctx, name + "$extends", fcel )).innerNodeIdx;
      }
      
      int fxnIdx = (new CompilerVisitor( ctx, name, n.f6, argIds )).innerNodeIdx;
      int staticIdx = -1;
      if( n.f6.staticNodes != null )
        staticIdx = (new CompilerVisitor( ctx, name + "$static", n.f6.staticNodes )).innerNodeIdx;
      
      // just in case, to get the specials...
      handle(n.f0);
      ctx.pushFunctionData( 
        il,
        Symbol.getSymbol(name).getId(),
        argIds,
        varargs,
        extendsIdx,
        fxnIdx,
        staticIdx,
        n.hasVarInScope,
        n.hasFxnInScope,
        n.comment 
      );
    }
    
    il.append( new INVOKESPECIAL( ctx.methodref( 
      "oscript.data.Function",
      "<init>",
      "(Loscript/data/Scope;" +
      "Loscript/data/Value;" +
      "Loscript/data/FunctionData;)V" 
    ) ) );
    
    setRetValOnStack(true);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "'{"
   * f1 -> Program(true)
   * f2 -> "}"
   * </PRE>
   */
  public void visit( ShorthandFunctionPrimaryPrefix n )
  {
    ShorthandFunctionPrimaryPrefixTranslator.translate(n).accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "["
   * f1 ->   (FunctionCallExpressionListBody())?
   * f2 -> "]"
   * </PRE>
   */
  public void visit( ArrayDeclarationPrimaryPrefix n )
  {
    if( n.f1.present() )
      n.f1.node.accept(this);
    else
      allocateMemberTable(0);
    
    setRetValOnStack(true);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> FunctionCallPrimaryPostfix()
   *       | ArraySubscriptPrimaryPostfix()
   *       | ThisScopeQualifierPrimaryPostfix()
   *       | PropertyIdentifierPrimaryPostfix()
   * </PRE>
   */
  public void visit( PrimaryPostfix n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ArraySubscriptPrimaryPostfix()
   *       | ThisScopeQualifierPrimaryPostfix()
   *       | PropertyIdentifierPrimaryPostfix()
   * </PRE>
   */
  public void visit( PrimaryPostfixWithTrailingFxnCallExpList n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> FunctionCallExpressionList()
   * </PRE>
   */
  public void visit( FunctionCallPrimaryPostfix n )
  {
    setRetValOnStack(false);
    
    il.append( InstructionConst.ALOAD_1 );         // sf
    
    allocateMemberTable_allocateFromStack = true;
    n.f0.accept(this);
    boolean sfIsNull = FunctionCallExpressionList_sfIsNull;
    checkRetValOnStack(true);
    
    // save mt:   ..., val, sf, mt => ..., [mt,] val, sf, mt
    if(!sfIsNull)
      il.append( InstructionConst.DUP_X2 );
    
    // call: ..., [mt,] val, sf, mt => ..., mt, rval
    il.append( new INVOKEVIRTUAL( ctx.methodref( 
      "oscript.data.Value",
      "callAsFunction",
      "(Loscript/util/StackFrame;Loscript/util/MemberTable;)Loscript/data/Value;"
    ) ) );
    
    if(!sfIsNull)
    {
      // swap: ..., mt, rval => ..., rval, mt
      il.append( InstructionConst.SWAP );
      
      // mt.free(): ..., rval, mt => ..., rval
      il.append( new INVOKEINTERFACE( ctx.ifmethodref( 
        "oscript.util.MemberTable",
        "free",
        "()V"
      ), 1 ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * <PRE>
   * f0 -> "["
   * f1 -> Expression()
   * f2 -> ( ".." Expression() )?
   * f3 -> "]"
   * </PRE>
   */
  public void visit( ArraySubscriptPrimaryPostfix n )
  {
    setRetValOnStack(false);
    
    n.f1.accept(this);
    
    if( n.f2.present() )
    {
      setRetValOnStack(false);
      ((NodeSequence)(n.f2.node)).elementAt(1).accept(this);
      
      checkRetValOnStack(true);
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "elementsAt",
                                                   "(Loscript/data/Value;Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
    else
    {
      checkRetValOnStack(true);
      il.append( new INVOKEVIRTUAL( ctx.methodref( "oscript.data.Value",
                                                   "elementAt",
                                                   "(Loscript/data/Value;)Loscript/data/Value;" ) ) );
    }
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "."
   * f1 -> &lt;IDENTIFIER&gt;
   * </PRE>
   */
  public void visit( PropertyIdentifierPrimaryPostfix n )
  {
    setRetValOnStack(false);
    
    n.f1.accept(this);
    checkRetValOnStack(true);
    
    il.append( new INVOKEVIRTUAL( ctx.methodref(
      "oscript.data.Value",
      "getMember",
      "(I)Loscript/data/Value;"
    ) ) );
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> "."
   * f1 -> "this"
   * </PRE>
   */
  public void visit( ThisScopeQualifierPrimaryPostfix n )
  {
    checkRetValOnStack(true);
    
    il.append( InstructionConst.ALOAD_2 );  // scope
    il.append( InstructionConst.SWAP );
    il.append( new INVOKEVIRTUAL( ctx.methodref(
      "oscript.data.Scope",
      "getThis",
      "(Loscript/data/Value;)Loscript/data/Value;"
    ) ) );
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> &lt;INTEGER_LITERAL&gt;
   *       | &lt;FLOATING_POINT_LITERAL&gt;
   *       | &lt;STRING_LITERAL&gt;
   *       | &lt;REGEXP_LITERAL&gt;
   *       | "true"
   *       | "false"
   *       | "null"
   *       | "undefined"
   * </PRE>
   */
  public void visit( Literal n )
  {
    n.f0.accept(this);
  }
  
  /*=======================================================================*/
  /**
   * <PRE>
   * f0 -> ( "static" | "const" | "private" | "protected" | "public" )*
   * </PRE>
   */
  public void visit( Permissions n )
  {
    throw new ProgrammingErrorException("shouldn't get here!");
  }
  
  /**
   * Get the permissions mask...
   * 
   * @param n            the permissions syntaxtree node
   * @param attr         the default permissions value
   * @return the permissions mask
   */
  private int getPermissions( Permissions n, int attr )
  {
    for( int i=0; i<n.f0.size(); i++ )
    {
      n.f0.elementAt(i).accept(this);
      
      switch(NodeToken_lastToken.kind)
      {
        case PRIVATE:
          attr = (attr & 0xf0) | Reference.ATTR_PRIVATE;
          break;
        case PROTECTED:
          attr = (attr & 0xf0) | Reference.ATTR_PROTECTED;
          break;
        case PUBLIC:
          attr = (attr & 0xf0) | Reference.ATTR_PUBLIC;
          break;
        case STATIC:
          attr |= Reference.ATTR_STATIC;
          break;
        case CONST:
          attr |= Reference.ATTR_CONST;
          break;
        default:
          throw new ProgrammingErrorException("bad kind: " + OscriptParser.getTokenString(NodeToken_lastToken.kind));
      }
    }
    
    return attr;
  }
  
  public String toString()
  {
    return mg.getName();
  }
}



/*
 *   Local Variables:
 *   tab-width: 2
 *   indent-tabs-mode: nil
 *   mode: java
 *   c-indentation-style: java
 *   c-basic-offset: 2
 *   eval: (c-set-offset 'substatement-open '0)
 *   eval: (c-set-offset 'case-label '+)
 *   eval: (c-set-offset 'inclass '+)
 *   eval: (c-set-offset 'inline-open '0)
 *   End:
 */

