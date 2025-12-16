/*=============================================================================
 *     Copyright Texas Instruments 2003.  All Rights Reserved.
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


package oscript.util;

import java.io.File;
import java.util.*;

import oscript.exceptions.*;
import oscript.data.*;
import oscript.NodeEvaluator;


/**
 * The "chain" of stack frames is used to track execution context of a
 * particular thread and, when debugging is enabled, give the debugger a 
 * chance to run breakpoints.
 * <p>
 * Where possible, the head of the chain of stack frames is passed on the 
 * stack, but in cases where it cannot be, such as when control passes to
 * java code and back, a hashtable is used to map the current thread to
 * a <code>StackFrame</code>.  To access the current stack frame, or
 * create one if needed, use {@link #currentStackFrame}.
 * <p>
 * While on the interface, the stack frame behaves as a chain of 
 * <code>StackFrame</code> objects, behind the scenes an array is used
 * for the stack, and a fly-weight pattern is used for the stack frame
 * objects.  This way we (1) avoid extra memory allocations, and (2)
 * can have different implementations of {@link #setLineNumber} depending
 * on whether debugging is enabled or not.  (Debugging is automatically
 * enabled when a breakpoint is set.)
 * <p>
 * In order to maintain this allusion, calls to {@link NodeEvaluator#evalNode}
 * must go through the {@link #evalNode} call-gate.
 * <p>
 * The stack frame object is intentionally not thread safe, since it is only 
 * accessed from a single thread context.  Because of the use of the fly-
 * weight pattern, a stack frame object no longer validly represents a stack
 * frame that has exited, either by normally or via an exception.  Because
 * of this, any code that wishes to save a reference to a stack frame object
 * must {@link #clone} it.
 * <p>
 * Because the <code>StackFrame</code> is only accessed from a single thread
 * context, it can provide a lightweight mechanism to allocate storage for 
 * {@link BasicScope} objects.  This can be used in cases where the scope
 * object only exists on the stack, and is not held after the program 
 * enclosed by the scope has finished execution, ie. there is no function 
 * enclosed by the scope.  For cases of an enclosed function, the scope 
 * storage must be allocated from the heap so that it can be valid at some 
 * point in the future when the enclosed function is potentially called.
 * 
 * @author Rob Clark (rob@ti.com)
 * @version 1
 */
public abstract class StackFrame
{
  /**
   * note impericially derived stack size number based on
   * a value sufficiently large that a StackOverFlowException
   * occurs before the stack frame index reaches this value.
   */ 
  private static final int STACK_SIZE = 1600; // reduct when growing stack implemented?? ORIGINAL 808
  
  /**
   * Maps thread to stack frame.  Because of the fly-weight pattern, there
   * is only one stack frame object (well, actually two), so all we need
   * is to map the current frame to a stack frame... no need to go
   * searching for the tail, or do any extra book-keeping to track the
   * tail.
   */
  private static final RegularStackFrame crr = new RegularStackFrame();

  /**
   * Get the stack frame for the current thread.  If one does not already
   * exist, this will create a new one, otherwise it will return the 
   * current top of the stack.
   */
  public static StackFrame currentStackFrame()
  {
	  return crr;
  }
    

  
  /**
   * StackFrame to use when not debugging.  This one's {@link #setLineNumber} 
   * does not have extra checks for breakpoints for better performance.  This
   * should be treated as final.
   */
  protected StackFrame regularStackFrame;
  
  /**
   * StackFrame to use whe debugging.  This one's {@link #setLineNumber} does
   * have extra checks for breakpoints, so breakpoints can work properly.
   * This should be treated as final.
   */
  /**
   * The current index, boxed in array so it can be shared between the
   * two stack-frame instances (regular & debug)
   */
  protected final int[] idx;
  
  /**
   * The node evaluator, which has file, and id info needed when filling
   * in stack trace.
   */
  protected final NodeEvaluator[] nes;
  
  /**
   * The current line number at each stack frame.
   */
  protected final int[] lines;
  
  /**
   * The current scopes at each stack frame.
   */
  protected final Scope[] scopes;
  
  /**
   * The list of scopes allocated at the current frame, which should be
   * recycled once the stack frame is released.
   */
  protected final StackFrameBasicScope[] scopeLists;
  
  /**
   * Pool of available, pre-allocated scope objects.  Whenever possible,
   * allocating a new scope will re-use one from the pool, in order to avoid
   * dynamic memory allocation.
   * @see #allocateBasicScope(Scope, SymbolTable)
   */
  private StackFrameBasicScope basicScopePool = null;  // XXX should be shared between both StackFrame objects
  
  /**
   * Pool of available, pre-allocated fxn-scope objects.  Whenever possible,
   * allocating a new scope will re-use one from the pool, in order to avoid
   * dynamic memory allocation.
   * @see #allocateFunctionScope(Function, Scope, SymbolTable, MemberTable)
   */
  private StackFrameFunctionScope functionScopePool = null;  // XXX should be shared between both StackFrame objects

  /**
   * Class Constructor.
   */
  private StackFrame(int[] idx, NodeEvaluator[] nes, int[] lines, 
                      Scope[] scopes, StackFrameBasicScope[] scopeLists)
  {
    this.idx    = idx;
    this.nes    = nes;
    this.lines  = lines;
    this.scopes = scopes;
    this.scopeLists = scopeLists;
    
  }
  
  /**
   * Push a new stack frame onto the stack, and pass it to <code>ne</code>'s 
   * {@link #evalNode} method, returning the result.
   * 
   * @param ne     the node-evaluator for the node to evaluate
   * @param scope  the scope to evalute in
   * @return the result
   */
  public final Object evalNode( NodeEvaluator ne, Scope scope )
  {
    StackFrame sf = regularStackFrame;    
    int idx = ++this.idx[0];
    // grow stack, if needed:
    if( idx >= nes.length )
      throw new ProgrammingErrorException("growing stack is not implemented yet, so STACK OVERFLOW! (idx=" + idx + ")"); // XXX
    nes[idx] = ne;
    try
    {
      return ne.evalNode( sf, scope );
    }
    catch(PackagedScriptObjectException e)
    {
      if( e.val instanceof OException )
        ((OException)(e.val)).preserveStackFrame();
      throw e;
    }
    finally
    {
      StackFrameBasicScope scopeList = scopeLists[idx];
      scopeLists[idx] = null;
      while( scopeList != null )
      {
        StackFrameBasicScope head = scopeList;
        scopeList = scopeList.next;
        head.next = basicScopePool;
        basicScopePool = head;
      }
      
      // so things can be GC'd:
      scopes[idx] = null;
      nes[idx]    = null;   // is this needed??
      this.idx[0] = (int)(idx-1);
    }
  }
  
  /**
   * Called by node evaluator to store line number info, and to give the
   * debugger a chance to see if we've hit a breakpoint.
   * 
   * @param scope        the current scope
   * @param line         the current line number
   */
  public final void setLineNumber( Scope scope, int line )
  {
	  // VISIONR FIX > moved in debugging stack frame. During normal execution we dont need line number meta information
      final int idx = this.idx[0];
	  scopes[idx] = scope;
      lines[idx]  = line;
  }
  
  /**
   * Called by node evaluator to store line number info, and to give the
   * debugger a chance to see if we've hit a breakpoint.  This method
   * is used by the compiler in cases where the scope hasn't changed
   * sinced last line number, to save a few instructions.
   * 
   * @param scope        the current scope
   * @param line         the current line number
   */
  public final void setLineNumber( int line )
  {
	  // VISIONR FIX > moved in debugging stack frame. During normal execution we dont need line number meta information
  }
  
  /**
   * Allocate a scope from the stack.  The basic-scope is freed automatically
   * when the stack-frame is disposed
   */
  public final BasicScope allocateBasicScope( Scope prev, SymbolTable smit )
  {
    int idx = this.idx[0];
    StackFrameBasicScope scope;
    if( basicScopePool != null )
    {
      scope = basicScopePool;
      scope.reinit( prev, smit );
      basicScopePool = basicScopePool.next;
    }
    else
    {
      scope = new StackFrameBasicScope( prev, smit );
    }
    scope.next = scopeLists[idx];
    scopeLists[idx] = scope;
    return scope;
  }
  
  /**
   * A scope allocated by the stack-frame, which can be recycled
   */
  private final class StackFrameBasicScope
    extends BasicScope
  {
    StackFrameBasicScope next;
    
    StackFrameBasicScope( Scope previous, SymbolTable smit )
    {
      super( previous, smit, allocateMemberTable( (int)(smit.size())) );
    }
    
    final void reinit( Scope previous, SymbolTable smit )
    {
      this.previous = previous;
      this.smit     = smit;
      if( members != null)
        members.reinit( (int)(smit.size()) );
      else
        members = allocateMemberTable( (int)(smit.size()) );
    }
  }
  
  /**
   * Allocate a fxn-scope from the stack.  The fxn-scope must be
   * freed by the caller.
   */
  public final FunctionScope allocateFunctionScope( Function fxn, Scope prev, SymbolTable smit, MemberTable members,Value _that,Value _super )
  {
    StackFrameFunctionScope scope;
    if( functionScopePool != null )
    {
      scope = functionScopePool;
      scope.reinit( fxn, prev, smit, members,_that,_super );
      functionScopePool = functionScopePool.next;
    }
    else
    {
      scope = new StackFrameFunctionScope( fxn, prev, smit, members,_that,_super);
    }
    return scope;
  }
  
  // VISIONR version with _that && _super 
  public final FunctionScope allocateFunctionScope( Function fxn, Scope prev, SymbolTable smit, MemberTable members)
  {
    return allocateFunctionScope(fxn,prev,smit,members,null,null);
  }
  
  /**
   * A scope allocated by the stack-frame, which can be recycled
   */
  public final class StackFrameFunctionScope
    extends FunctionScope
  {
    StackFrameFunctionScope next;
    private Value _that;
    private Value _super;
    
    StackFrameFunctionScope( Function fxn, Scope prev, SymbolTable smit, MemberTable members,Value _that,Value _super )
    {
      super( fxn, prev, smit, members );
      this._that=_that;
      this._super=_super;
    }

    @Override
    public Value getThis() {
    	if (_that != null)
    		return _that;
    	return super.getThis();
    }
    
    @Override
    public Value getThis(Value val) {
    	if (_that != null)
    		return _that;
    	return super.getThis(val);
    }
    
    @Override
    public Value getSuper() {
    	if (_that != null)
    		return _super;
    	return super.getSuper();
    }
    
    final void reinit( Function fxn, Scope prev, SymbolTable smit, MemberTable members,Value _that,Value _super )
    {
      this._that=_that;
      this._super=_super;
      this.fxn      = fxn;
      this.previous = prev;
      this.smit     = smit;
      this.members  = members;
    }
    
    public final void free()
    {
      this.next = functionScopePool;
      this._that = null;
      this._super = null;      
      this.fxn = null;
      this.previous = null;
      this.smit = null;
      this.members = null;
      functionScopePool = this;
    }
  }
  
  /**
   * Allocate @ 11.12.2025 NO STACK IMPL ! NORMAL MEMORY OBJ !
   */
  public final MemberTableImpl allocateMemberTable( int sz )
  {
	MemberTableImpl sfa = new MemberTableImpl();
    sfa.reinit(sz);
    return sfa;
  }
  
  /**
   * Convenience wrapper for {@link #getId}, mainly provided for the benefit
   * of script code that probably doesn't want to know about ids, and just
   * wants to think in terms of names.
   */
  public oscript.data.Value getName()
  {
    int id = getId();
    if( id == -1 )
      return null;
    return Symbol.getSymbol(id);
  }
  
  /**
   * The function name for the current stack frame, if there is one, otherwise
   * <code>-1</code>.
   */
  public final int getId()
  {
    return nes[ idx[0] ].getId();
  }
  
  /**
   * The file corresponding to the current stack frame.
   */
  public final File getFile()
  {
    return nes[ idx[0] ].getFile();
  }

  public final NodeEvaluator getNodeEvaluator()
  {
    return nes[ idx[0] ];
  }

  /**
   * The current line number in the current stack frame.
   */
  public final int getLineNumber()
  {
    return lines[ idx[0] ];
  }
  
  /**
   * The current scope in the current line number.
   */
  public final Scope getScope()
  {
    return scopes[ idx[0] ];
  }
  
  /**
   * Return an iterator of stack frames, starting with the top of the
   * stack, and iterating to root of stack.
   */
  public Iterator iterator()
  {
    return new CollectionIterator( new Iterator() {
        
        private int idx = StackFrame.this.idx[0];
        
        public boolean hasNext()
        {
          return idx > 0;
        }
        
        public Object next()
        {
          return new ReadOnlyStackFrame( idx--, nes, lines, scopes );
        }
        
        public void remove()
        {
          throw new UnsupportedOperationException("remove");
        }
        
      } );
  }
  
  public StackFrame getSafeCopy()
  {
    return (StackFrame)clone();
  }
  
  /**
   * Clone the stack frame, which is necessary in cases where you need to keep
   * a reference to the stack frame (and it's parents) after the original
   * stack frame has exited, such as to store in an exception.
   */
  public final Object clone()
  {
    int idx = this.idx[0];
    
    NodeEvaluator[] nes = new NodeEvaluator[idx+1];
    System.arraycopy( this.nes, 0, nes, 0, idx+1 );
    
    int[] lines = new int[idx+1];
    System.arraycopy( this.lines, 0, lines, 0, idx+1 );
    
    Scope[] scopes = new Scope[idx+1];
    for( int i=0; i<scopes.length; i++ )
      if( this.scopes[i] != null )
        scopes[i] = this.scopes[i].getSafeCopy();
    
    return new ReadOnlyStackFrame( idx, nes, lines, scopes );
  }
  
  /**
   * Convert to string, to print out a line in the stack-trace.
   */
  public String toString()
  {
    String fileline = getFile() + ":" + getLineNumber();
    int id = getId();
    if( id == -1 )
      return fileline;
    return Symbol.getSymbol(id) + " (" + fileline + ")";
  }
  
  public int hashCode()
  {
    return getId() ^ getLineNumber();
  }
  
  public boolean equals( Object obj )
  {
    if( obj instanceof StackFrame )
    {
      StackFrame other = (StackFrame)obj;
      File file = getFile();
      return (other.getId() == getId()) &&
        (other.getLineNumber() == getLineNumber()) &&
        ( (file == null) ? 
          (other.getFile() == null) :
          other.getFile().equals(getFile()) );
    }
    return false;
  }
  
  /*=======================================================================*/
  /**
   * StackFrame fly-weight to use when not debugging.
   */
  private static class RegularStackFrame
    extends StackFrame
  {
    private RegularStackFrame(int[] idx, NodeEvaluator[] nes, int[] lines, 
                       Scope[] scopes, StackFrameBasicScope[] scopeLists)
    {
      super( idx, nes, lines, scopes, scopeLists);
      
      regularStackFrame = this;
    }
    
    RegularStackFrame()
    {
      this( 
        new int[] { 0 },
        new NodeEvaluator[STACK_SIZE],
        new int[STACK_SIZE],
        new Scope[STACK_SIZE],
        new StackFrameBasicScope[STACK_SIZE]
      );
    }
  }
  /*=======================================================================*/
  /**
   * A read-only copy of a stack frame, used for a variety of purposes.
   */
  private static class ReadOnlyStackFrame
    extends RegularStackFrame
  {
    ReadOnlyStackFrame( int idx, NodeEvaluator[] nes, int[] lines, Scope[] scopes )
    {
      super( new int[] { idx }, nes, lines, scopes, null);
    }    
    
    public StackFrame getSafeCopy()
    {
      return this;
    }
  }
  
  /*=======================================================================*/
  /**
   * For debugging
   */
  
  public static final void dumpStack( java.io.OutputStream out )
  {
    dumpStack( new java.io.OutputStreamWriter(out) );
  }
  
  public static final void dumpStack( java.io.Writer out )
  {
    java.io.PrintWriter ps;
    
    if( out instanceof java.io.PrintWriter )
      ps = (java.io.PrintWriter)out;
    else
      ps = new java.io.PrintWriter(out);
    
    for( Iterator itr=currentStackFrame().iterator(); itr.hasNext(); )
      ps.println(" at " + itr.next());
    
    ps.flush();
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

