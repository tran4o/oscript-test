/*=============================================================================
 *     Copyright Texas Instruments 2000-2004.  All Rights Reserved.
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


package oscript.data;


import java.util.*;

import oscript.syntaxtree.FunctionPrimaryPrefix;
import oscript.util.StackFrame;
import oscript.util.MemberTable;
import oscript.util.SymbolTable;
import oscript.exceptions.*;
import oscript.NodeEvaluator;


/**
 * A script function/constructor.  Since native (and other) objects that
 * behave as a function can re-use some functionality (ie checking number
 * of args, type casting, etc., the stuff specific to a script function/
 * constructor is pushed out into a seperate class.
 * 
 * @author Rob Clark (rob@ti.com)
 */
public class Function extends Type 
{
  /**
   * The type object for an script function.
   */
  public final static String PARENT_TYPE_NAME = "oscript.data.OObject";
  public final static String TYPE_NAME        = "Function";
  public final static String[] MEMBER_NAMES   = new String[] {
    "getName",
    "getComment",
    "getMinimumArgCount",
    "takesVarArgs",
    "getArgNames",
    "isA",
    "castToString",
    "callAsFunction",
    "callAsConstructor",
    "callAsExtends"
  };
  public final static Value TYPE = BuiltinType.makeBuiltinType("oscript.data.Function");
  public final static OArray array0 = new OArray(0); // immutable!
  
  /**
   * The scope this function is defined in.  This does not change throughout
   * the life of the function.
   */
  public Scope enclosingScope;
  
  /**
   * The scope the static members of this function are defined in.  When
   * the function is constructed, if there are any static members, they
   * are evaluated within this scope.  Otherwise this is null.
   */
  private final Scope staticScope;
    
  /**
   * The function this function extends, if any.
   */
  private final Value superFxn;
  
  /**
   * The shared function data... parameters that are shared by all instances
   * of the same function.
   * <p>
   * public for {@link StackFrame#evalNode}
   */
  public final FunctionData fd;
  
  /**
   * If this function overrides a value, this is the previous value.
   */
  public Value overriden;
  
  /**
   * In order to keep function instances more lightweight, the values that
   * will be the same for any instance of a function representing the same
   * portion of the parse tree have been split out into this class, in order
   * to be shared between different function instances.
   */
   
  /*=======================================================================*/
  /**
   * Class Constructor.  Construct an anonymous function.
   * 
   * @param enclosingScope the context the function was declared in
   * @param superFxn     the function this function extends, or 
   *    <code>null</code>
   * @param fd           the shared function data, for all instances 
   *    of this function
   * 
   */
  public Function( Scope enclosingScope,
                   Value superFxn,
                   FunctionData fd )
  {
    super();
    
    // every script type implicitly inherits from <i>Object</i>
    if( superFxn == null )
      superFxn = OObject.TYPE;
    
    // if this function is overriding a function in an object scope, keep a
    // reference to the overriden function:
    if( (fd.id != FunctionPrimaryPrefix.ANON_FXN_ID) && (enclosingScope instanceof ConstructorScope) )
    {
      Scope scope = enclosingScope.getPreviousScope();
      while( !(scope instanceof ScriptObject) )
        scope = scope.getPreviousScope();
      overriden = scope.getMemberImpl(fd.id);
      if( overriden != null )
        overriden = overriden.unhand();
    }
    
// XXX seems to cause apple's VM to bus-error... not sure if it causes 
// problems on other platforms or not. --RDC
//    if(DEBUG)
//      if( !enclosingScope.isSafe() )
//        StackFrame.dumpStack(System.err);
    
    this.enclosingScope = enclosingScope;
    this.superFxn  = superFxn;
    this.fd   = fd;
    
    if( fd.sprogram != null )
    {
      staticScope = new BasicScope(enclosingScope);
      StackFrame.currentStackFrame().evalNode( fd.sprogram, staticScope );
    }
    else
    {
      staticScope = null;
    }
  }
  
  /*=======================================================================*/
  /**
   * Get the function that this function extends, or <code>null</code> if
   * none.
   */
  Value getSuper() { return superFxn; }
  
  /*=======================================================================*/
  /**
   * If this function overrides a value, this method returns it.  Otherwise
   * it returns <code>null</code>.
   */
  Value getOverriden() { return overriden; }
  
  /*=======================================================================*/
  /**
   * Get the type of this object.  The returned type doesn't have to take
   * into account the possibility of a script type extending a built-in
   * type, since that is handled by {@link #getType}.
   * 
   * @return the object's type
   */
  protected Value getTypeImpl()
  {
    return TYPE;
  }
  
  /*=======================================================================*/
  /**
   * Get the name of this function.  An anonymous function will have the
   * name "anon".
   * 
   * @return the function's name
   */
  public Value getName()
  {
    return fd.getName();
  }
  
  /*=======================================================================*/
  /**
   * Get the comment block.  If there was a javadoc comment block preceding
   * the definition of this function in the src file, it can be accessed
   * with this method.
   * 
   * @return the function's comment, or <code>null</code>
   */
  public Value getComment()
  {
    return fd.comment;
  }
  
  /*=======================================================================*/
  /**
   * Get the minimum number of args that should be passed to this function.
   * If {@link #isVarArgs} returns <code>true</code>, then it is possible
   * to pass more arguments to this function, otherwise, you should pass
   * exactly this number of args to the function.
   * 
   * @return minimum number of args to pass when calling this function
   * @see #isVarArgs
   * @see #getArgNames
   */
  public int getMinimumArgCount()
  {
    return fd.nargs;
  }
  
  /*=======================================================================*/
  /**
   * Can this function be called with a variable number of arguments?
   * @see #getMinimumArgCount
   * @see #getArgNames
   */
  public boolean takesVarArgs()
  {
    return fd.varargs;
  }
  
  /*=======================================================================*/
  /**
   * Get the names of the arguments to the function, in order.  If this 
   * function takes a variable number of arguments, the last name in the 
   * array is the "var-arg" variable, to which the array of all remaining
   * arguments are bound.
   */
  public Value[] getArgNames()
  {
    Value[] names = new Value[ fd.argIds.length / 2 ];
    for( int i=0; i<names.length; i++ )
      names[i] = Symbol.getSymbol( fd.argIds[2*i] );
    return names;
  }
  
  /* Note:  arg-permissions are not made visible, because that seems
   *        to me like an implementation detail, whereas the arg-names
   *        is (sort of) part of the interface
   */
  
  /*=======================================================================*/
  /**
   * If this object is a type, determine if an instance of this type is
   * an instance of the specified type, ie. if this is <code>type</code>,
   * or a subclass.
   * 
   * @param type         the type to compare this type to
   * @return <code>true</code> or <code>false</code>
   * @throws PackagedScriptObjectException(NoSuchMemberException)
   */
  public boolean isA( Value type )
  {
    return super.isA(type) || this.superFxn.isA(type);
  }
  
  private static final int BOPCAST = Symbol.getSymbol("_bopCast").getId();
  
  /*=======================================================================*/
  /**
   * Perform the cast operation, <code>(a)b</code> is equivalent to <code>a.bopCast(b)</code>
   * 
   * @param val          the other value
   * @return the result
   * @throws PackagedScriptObjectException(NoSuchMemberException)
   */
  public Value bopCast( Value val )
    throws PackagedScriptObjectException
  {
    Value bopCast = getMember( BOPCAST, false );
    if( bopCast != null )
      return bopCast.callAsFunction( new Value[] { val } );
    return super.bopCast(val);
  }
  
  // bopCastR would be an instance member, not static (class) member
  
  /*=======================================================================*/
  /**
   * Convert this object to a native java <code>String</code> value.
   * 
   * @return a String value
   * @throws PackagedScriptObjectException(NoSuchMethodException)
   */
  public String castToString()
    throws PackagedScriptObjectException
  {
    return "[function: " + getName() + "]";
  }
  
  /*=======================================================================*/
  /**
   * Call this object as a function.
   * 
   * @param sf           the current stack frame
   * @param args         the arguments to the function, or <code>null</code> if none
   * @return the value returned by the function
   * @throws PackagedScriptObjectException
   * @see Function
   */
  public Value callAsFunction( StackFrame sf, MemberTable args )
    throws PackagedScriptObjectException
  {
    if( superFxn != OObject.TYPE )
      throw PackagedScriptObjectException.makeExceptionWrapper( new OUnsupportedOperationException(getName() + ": cannot call as function!") );
    
    if( !fd.hasVarInScope && (fd.argIds.length == 0) && (args == null) )
    {
      return (Value)(sf.evalNode( fd.program, enclosingScope ));
    }
    else
    {
      Scope scope;
      SymbolTable smit = fd.program.getSharedMemberIndexTable(NodeEvaluator.ALL);
      // OPTIMIZED @ 13.12.25
      /*if( args == null ) args = fd.hasFxnInScope ? array0 : sf.allocateMemberTable(0);
      args = fd.mapArgs(args);  // even if args length is zero, to deal with var-args*/
      if (args == null)
    	  args = array0;
      else
    	  args = fd.mapArgs(args);
      if( !fd.hasFxnInScope )
        scope = sf.allocateFunctionScope( this, enclosingScope, smit, args );
      else
        scope = new FunctionScope( this, enclosingScope, smit, args );
      try {
        return (Value)(sf.evalNode( fd.program, scope ));
      } finally {
        scope.free();
      }
    }
  }
  
  /*=======================================================================*/
  /**
   * Call this object as a constructor.
   * 
   * @param sf           the current stack frame
   * @param args         the arguments to the function, or <code>null</code> if none
   * @return the newly constructed object
   * @throws PackagedScriptObjectException
   * @see Function
   */
  public Value callAsConstructor( StackFrame sf, MemberTable args )
    throws PackagedScriptObjectException
  {
    /* XXX we should only need to create ConstructorScope if the number of
     * args is greater than zero, and hasVarInScope
     */
    ScriptObject newThisScope = new ScriptObject( 
      this, enclosingScope, fd.program.getSharedMemberIndexTable(NodeEvaluator.PUBPROT)
    );
    ConstructorScope fxnScope = new ConstructorScope( 
      this, newThisScope, fd.program.getSharedMemberIndexTable(NodeEvaluator.PRIVATE)
    );
    
    fd.addArgs( fxnScope, args );
    
    MemberTable superFxnArgs;
    if( fd.exprList != null )
      superFxnArgs = (MemberTable)(sf.evalNode( fd.exprList, fxnScope ));
    else
      superFxnArgs = array0;
    
    superFxn.callAsExtends( sf, newThisScope, superFxnArgs );
    
    sf.evalNode( fd.program, fxnScope );
    
    return newThisScope;
  }
  
  /*=======================================================================*/
  /**
   * Call this object as a parent class constructor.
   * 
   * @param sf           the current stack frame
   * @param scope        the object
   * @param args         the arguments to the function, or <code>null</code> if none
   * @return the value returned by the function
   * @throws PackagedScriptObjectException
   * @see Function
   */
  public Value callAsExtends( StackFrame sf, Scope scope, MemberTable args )
    throws PackagedScriptObjectException
  {
    /* XXX we should only need to create ConstructorScope if the number of
     * args is greater than zero, and hasVarInScope
     */
    
    scope = new ForkScope( scope, enclosingScope );
    ConstructorScope fxnScope = new ConstructorScope( 
      this, scope, fd.program.getSharedMemberIndexTable(NodeEvaluator.PRIVATE)
    );
    
    fd.addArgs( fxnScope, args );
    
    MemberTable superFxnArgs;
    if( fd.exprList != null )
      superFxnArgs = (MemberTable)(sf.evalNode( fd.exprList, fxnScope ));
    else
      superFxnArgs = array0;
    
    superFxn.callAsExtends( sf, scope, superFxnArgs );
    
    return (Value)(sf.evalNode( fd.program, fxnScope ));
  }
  
  /*=======================================================================*/
  /**
   * Get a member of this object.
   * 
   * @param id           the id of the symbol that maps to the member
   * @param exception    whether an exception should be thrown if the
   *   member object is not resolved
   * @return a reference to the member
   * @throws PackagedScriptObjectException(NoSuchMethodException)
   * @throws PackagedScriptObjectException(NoSuchMemberException)
   */
  public Value getMember( int id, boolean exception )
    throws PackagedScriptObjectException
  {
    Value val = getStaticMember(id);
    
    if( val == null )
      val = super.getMember( id, exception );
    
    return val;
  }
  
  /*=======================================================================*/
  /**
   * Get a member of this type.  This is used to interface to the java
   * method of having members be attributes of a type.  Regular object-
   * script object's members are attributes of the object, but in the
   * case of java types (including built-in types), the members are
   * attributes of the type.
   * 
   * @param obj          an object of this type
   * @param id           the id of the symbol that maps to the member
   * @return a reference to the member, or null
   */
  protected Value getTypeMember( Value obj, int id )
  {
    Value val = superFxn.getTypeMember( obj, id );
    
    if( val == null )
      val = getStaticMember(id);
    
    return val;
  }
  
  /*=======================================================================*/
  /**
   * Get a static member of this function object.
   */
  final Value getStaticMember( int id )
  {
    Value val = null;
    
    if( staticScope != null )
      val = staticScope.getMember( id, false );
    
    return val;
  }
  
  /*=======================================================================*/
  /**
   * Derived classes that implement {@link #getMember} should also
   * implement this.
   * 
   * @param s   the set to populate
   * @param debugger  <code>true</code> if being used by debugger, in
   *   which case both public and private/protected field names should 
   *   be returned
   * @see #getMember
   */
  protected void populateMemberSet( Set s, boolean debugger )
  {
    if( staticScope != null )
      staticScope.populateMemberSet( s, debugger );
  }
  
  /*=======================================================================*/
  /**
   * Derived classes that implement {@link #getTypeMember} should also
   * implement this.
   * 
   * @param s   the set to populate
   * @param debugger  <code>true</code> if being used by debugger, in
   *   which case both public and private/protected field names should 
   *   be returned
   * @see #getTypeMember
   */
  protected void populateTypeMemberSet( Set s, boolean debugger )
  {
    if( superFxn != null )
      superFxn.populateTypeMemberSet( s, debugger );
  }

  public void setEnclosingScope(Scope scope) {
      this.enclosingScope=scope;
  }
  
  public Scope getEnclosingScope() {
      return enclosingScope;
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

