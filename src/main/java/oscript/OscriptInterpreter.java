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


package oscript;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;

import oscript.data.GlobalScope;
import oscript.data.OJavaException;
import oscript.data.Scope;
import oscript.data.Value;
import oscript.exceptions.ProgrammingErrorException;
import oscript.interpreter.InterpretedNodeEvaluatorFactory;
import oscript.parser.ParseException;
import oscript.syntaxtree.FunctionCallExpressionList;
import oscript.syntaxtree.Node;
import oscript.syntaxtree.Program;
import oscript.syntaxtree.ProgramFile;
import oscript.util.StackFrame;



/**
 * The toplevel and main interface for the interpreter.  There can only be
 * one instance of this object.  The scope object can be used to create
 * logically isolated interpreter "instances".
 * <p>
 * This does need some cleanup, and perhaps should be a front-end for other
 * stuff someone embedding this in an application might want, such as
 * creating a new scope...
 * <p>
 * Description of properties that are interesting:
 * <table>
 *   <tr>
 *     <th>property</th>
 *     <th>description</th>
 *     <th>regular default</th>
 *     <th>webstart default</th>
 *   </tr>
 *   <tr>
 *     <td>oscript.cache.path</td>
 *     <td>where the cache directory is created</th>
 *     <td>$CWD/.cache</td>
 *     <td>$HOME/.cache</td>
 *   </tr>
 *   <tr>
 *     <td>oscript.cwd</td>
 *     <td>the current working directory</td>
 *     <td>$CWD</td>
 *     <td>$HOME/Desktop || $HOME</td>
 *   </tr>
 * </table>
 * 
 * @author Rob Clark (rob@ti.com)
 */
public class OscriptInterpreter
{
  
  private static GlobalScope   globalScope     = null;
  private static DefaultParser parser      	   = new DefaultParser();
  private static LinkedList    scriptPathList  = new LinkedList();
  
  // XXX clean this up!  It should have some way to register factories, etc...
  public static final NodeEvaluatorFactory nodeCompiler = OscriptHost.me.newCompiledNodeEvaluatorFactory();
  public static final NodeEvaluatorFactory nodeInterpreter = new InterpretedNodeEvaluatorFactory();
  
  public static Value DEFAULT_ARRAY_SORT_COMPARISION_FXN;
  /*=======================================================================*/
  /**
   * Set the input stream.
   * 
   * @param in           the stream to use for input
   */
  public static void setIn( InputStream in )
  {
    OscriptBuiltins.setIn(in);
  }
  
  /*=======================================================================*/
  /**
   * Set the output stream.
   * 
   * @param out          the stream to use for output
   */
  public static void setOut( PrintStream out )
  {
    OscriptBuiltins.setOut(out);
  }
  
  /*=======================================================================*/
  /**
   * Set the error stream.
   * 
   * @param err          the stream to use for error output
   */
  public static void setErr( PrintStream err )
  {
    OscriptBuiltins.setErr(err);
  }
  
  /*=======================================================================*/
  public static void resetGlobalScope() throws IOException,ParseException
  {
    if (globalScope == null)
    	globalScope = new GlobalScope();
    globalScope.reset();
    OscriptBuiltins.init();
    //eval( resolve( "etc/core/script/system/base.os", false ) );
    DEFAULT_ARRAY_SORT_COMPARISION_FXN = eval("__defaultArraySortComparision;");
	//oscript.data.RegExp.register(new RegExp.Factory());
  }

  /**
   * Get the global scope object.  The <code>globalScope</code> is static,
   * meaning that all script code in an application shares a single global
   * scope.  But, since the interpreter is multi-threaded, you can achieve
   * the same effect of having multiple interpreter instances by creating
   * a new level of scope (ie with <code>globalScope</code> as it's parent,
   * an evaluate within that scope.
   * 
   * @return the <code>globalScope</code> object
   */
  public static Scope getGlobalScope()
  {
    if( globalScope == null )
    {
      globalScope = new GlobalScope();
      OscriptBuiltins.init();
    }
    
    return globalScope;
  }
  
  /*=======================================================================*/
  /*=======================================================================*/
  /*=======================================================================*/
  
  /*=======================================================================*/
  /**
   * helper function to implement <code>import</code> statements.
   */
  public static Value importHelper( String path, Scope scope )
  {
      throw OJavaException.convertException(new RuntimeException("NOT IMPLEMENTED!"));
  }
  
  /*=======================================================================*/
  /**
   * Evaluate from the specified abstract file.  The stream is evaluated
   * until EOF is hit.
   * 
   * @param file         the file to evaluate
   * @return the result of evaluating the input
   * @throws ParseException if error parsing input
   * @throws IOException if something goes poorly when reading file
   */
  public static Value eval( MemoryFile file )
    throws ParseException, IOException
  {
    return eval( file, getGlobalScope() );
  }
  
  /*=======================================================================*/
  /**
   * Evaluate from the specified abstract file.  The stream is evaluated
   * until EOF is hit.
   * 
   * @param file         the file to evaluate
   * @param scope        the scope to evaluate in
   * @return the result of evaluating the input
   * @throws ParseException if error parsing input
   * @throws IOException if something goes poorly when reading file
   */
  public static Value eval( MemoryFile file, Scope scope )
    throws ParseException, IOException
  {
      return (Value)(StackFrame.currentStackFrame().evalNode( getNodeEvaluator(file), scope ));
  }
  
  public static long et;
  
  /*=======================================================================*/
  /**
   * Evaluate the specified sting.
   * 
   * @param str          the string to evaluate
   * @return the result of evaluating the string
   * @throws ParseException if error parsing string
   */
  public static Value eval( String str )
    throws ParseException
  {
    return eval( str, getGlobalScope() );
  }
  
  // XXX fixme: work around because "eval" is a object-script keyword
  public static final Value __eval( String str )
    throws ParseException
  {
    Value val = eval(str);
    if( val == Value.UNDEFINED )
      val = Value.NULL;
    return val;
  }
  
  /*=======================================================================*/
  // DEFAULT IMPLEMENTATION > EVAL INTERPRETER ONLY !!! ONLY for small evals not subject to not optimization (run once)
  /**
   * Evaluate the specified sting.
   * 
   * @param str          the string to evaluate
   * @param scope        the scope to evaluate in
   * @return the result of evaluating the string
   * @throws ParseException if error parsing string
   */
  public static Value eval( String str, Scope scope )
    throws ParseException
  {
    Node node = parse(str);
    NodeEvaluator ne =  nodeInterpreter.createNodeEvaluator( null/* NO NAME */, node );
    return (Value)(StackFrame.currentStackFrame().evalNode( ne, scope ));
  }
  
  // XXX fixme: work around because "eval" is a object-script keyword
  public static final Value __eval( String str, Scope scope )
    throws ParseException
  {
    Value val = eval( str, scope );
    if( val == Value.UNDEFINED )
      val = Value.NULL;
    return val;
  }
  
  public static final void __declareInScope( String name, Value val, Scope scope )
  {
    scope.createMember(name,0).opAssign(val);
  }
  
  /*=======================================================================*/
  /*=======================================================================*/
  /*=======================================================================*/
  
  /**
   * Parse the input stream to a syntaxtree.
   * 
   * @param file         the file to parse
   * @return the parsed syntaxtree
   */
  public static Node parse( MemoryFile file )
    throws ParseException, IOException
  {
	  return parser.parse(file);
  }
  
  /**
   * Get node-evaluator via cache.  If not in cache, and not loadable into
   * cache from cache-fs, then actually parse and create new node-evaluator.
   * If exists in cache, but <code>file</code> has been more recently modified,
   * the re-parse and create new node-evaluator.
   */
  public static NodeEvaluator getNodeEvaluator( MemoryFile file )
    throws ParseException, IOException
  {
	  return createNodeEvaluator( file.getName(), parse(file) );
  }
  
 
  
 
  
  /*=======================================================================*/
  /**
   * Parse the string to a syntaxtree.
   * 
   * @param str          the string to parse
   * @return the parsed syntaxtree
   */
  public static Node parse( String str ) throws ParseException
  {
  	  return DefaultParser.parse(str);
  }
  
  public static Node parse( String[] str ) throws ParseException
  {
        return DefaultParser.parse(str);
  }
  /*=======================================================================*/
  /*=======================================================================*/
  /*=======================================================================*/
  
  /* I should add some mechanism to add/remove NodeEvaluatorFactory?
   */
  
  /*=======================================================================*/
  /**
   * Create a NodeEvaluator to evaluate a node.  An application embedding
   * the interpreter should use this method to convert the parsed syntax
   * tree to something that can be evaluated within a scope, rather than
   * directly using the visitors.  This protects the application against
   * changes to the parsed representation of the program.
   * 
   * @param desc         description
   * @param node         the node
   * @return a NodeEvaluator
   */
  public static NodeEvaluator createNodeEvaluator( String name, Node node )
  {
    // XXX hack for the cached node evaluator...
    NodeEvaluator ne = null;
    
    if( node instanceof Program )
      ne = ((Program)node).nodeEvaluator;
    else if( node instanceof FunctionCallExpressionList )
      ne = ((FunctionCallExpressionList)node).nodeEvaluator;
    else if( node instanceof ProgramFile )
      ne = ((ProgramFile)node).nodeEvaluator;
    
    if( ne != null )
      return ne;
    
    ne = createNodeEvaluatorImpl( name, node );
    
    if( node instanceof Program )
      ((Program)node).nodeEvaluator = ne;
    else if( node instanceof FunctionCallExpressionList )
      ((FunctionCallExpressionList)node).nodeEvaluator = ne;
    else if( node instanceof ProgramFile )
      ((ProgramFile)node).nodeEvaluator = ne;
    
    return ne;
  }
  private static NodeEvaluator createNodeEvaluatorImpl( String name, Node node )
  {
    NodeEvaluator ne = null;
    
    if(nodeCompiler != null) {
    	try {
        	ne = nodeCompiler.createNodeEvaluator( name, node );
    	} catch (ProgrammingErrorException ex) {
    		// TOO BIG FOR COMPILATION ? TODO DISABLE FOR VSP?
    	   OscriptHost.me.warn("OScript compilation failed because of ProgrammingErrorException (generated class size > 64K) | Switching to interpretter mode!");
    	   ne = nodeInterpreter.createNodeEvaluator( name, node );
    	}
    }
    // nodeCompiler.createNodeEvaluator could return null if compile fails
    // for whatever reason...
    if( ne == null )
      ne = nodeInterpreter.createNodeEvaluator( name, node );
    
    return ne;
  }

  
  /*=======================================================================*/
  /**
   * Return an iterator of entries in the script-path.  Each entry is a path
   * that is prefixed to a relative path passed to {@link #resolve} in the
   * process of trying to resolve a file.
   * 
   * @return an iterator of strings
   */
  public static Iterator getScriptPath()
  {
    // copy list to avoid concurrent-mod problems
    synchronized(scriptPathList) {
      return new oscript.util.CollectionIterator( (new LinkedList(scriptPathList)).iterator() );
    }
  }
  
  /*=======================================================================*/
  /**
   * Try to load the specified file from one of the registered filesystems.
   * 
   * @param path         the path to the file to resolve
   * @param create       create the file if it does not exist
   * @throws IOException if something goes wrong when reading file
   * @see #addScriptPath
   */
  public static File resolve( String path, boolean create )
    throws IOException
  {
	File file = resolveImpl(path);
    if( !file.exists() )
    {
      if(create)
        file.createNewFile();
    }
    return file;
  }
  
  private static File resolveImpl( String path )
    throws IOException
  {
	  String s = OscriptHost.me.getVirtualFile(path);
	  if (s == null) return null;
	  return new File(s);
  }

  
  /**
   * utility to string out "%20" and other escape codes, and replace them
   * with the appropriate character.
   */
  @SuppressWarnings("unused")
private static final String sanitizeUrl( String str )
  {
    int lastIdx = 0;
    int idx;
    
    str = str.replace('\\','/');
    
    while( (idx=str.indexOf('%',lastIdx)) != -1 )
    {
      String a = str.substring( 0, idx );
      String b = str.substring( idx+3 );
      String hex = str.substring( idx+1, idx+3 );
      
      char c = (char)(Integer.parseInt( hex, 16 ));
      
      str = a + c + b;
      
      lastIdx = idx;
    }
    
    return str;
  }
  
  /*=======================================================================*/
  /*=======================================================================*/
  /*=======================================================================*/
  
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

