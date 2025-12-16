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

import oscript.util.StackFrame;
import oscript.util.MemberTable;
import oscript.exceptions.*;
import oscript.NodeEvaluator;

public final class FunctionData {
	/**
	 * The node-evaluator for evaluating the body of this function.
	 * <p>
	 * only public for {@link StackFrame}
	 */
	public NodeEvaluator program;

	/**
	 * The node-evaluator for evaluating the static body of this function.
	 */
	NodeEvaluator sprogram;

	/**
	 * The expressions to evaluate to determine args to superFxn.
	 */
	NodeEvaluator exprList;

	/**
	 * The id of this function.
	 */
	int id;

	/**
	 * The ids of the arguments and there permissions. The n'th parameter to the
	 * function has its <code>id</code> specified by the 2*n element in the array,
	 * and <code>attr</code> specified by the 2*n+1 element in the array.
	 */
	int[] argIds;

	/**
	 * The number of args (if not vararg fxn), or minimum number of args (if vararg
	 * fxn).
	 */
	public int nargs;

	/**
	 * Is this a var-args function. If it is, then the zero or more remaining args
	 * to the function are copied into an array that is assigned to the last arg to
	 * the function.
	 */
	public boolean varargs;

	/**
	 * A hint from the parser about whether we need to create an extra level of
	 * scope (ie. there are variables declared in that scope)
	 */
	public boolean hasVarInScope;

	/**
	 * A hint from the parser about whether scope storage can be allocated from the
	 * stack, which can only be done if there are no functions declared within this
	 * function
	 * <p>
	 * only public for {@link StackFrame}
	 */
	public boolean hasFxnInScope;

	/**
	 * Comment generated from javadoc comment block in src file.
	 */
	Value comment;

	/**
	 * Class Constructor.
	 * 
	 * @param id            the id of the symbol that maps to the member, ie. it's
	 *                      name
	 * @param argIds        array of argument ids and attributes
	 * @param varargs       is this a function that can take a variable number of
	 *                      args?
	 * @param exprList      expressions to evaluate to get args to
	 *                      <code>superFxn</code> or <code>null</code> if
	 *                      <code>superFxn</code> is <code>null</code>
	 * @param program       the body of the function
	 * @param sprogram      the static body of the function, or <code>null</code>
	 * @param hasVarInScope whether one or more vars/functions are declared in the
	 *                      function body's scope... this is a hint from the parser
	 *                      to tell us if we can avoid creating a scope object at
	 *                      runtime
	 * @param hasFxnInScope whether one or more functions are enclosed by this
	 *                      function body's scope... this is a hint from the parser
	 *                      to tell us if we can allocate scope storage from the
	 *                      stack
	 * @param comment       html formatted comment generated from javadoc comment in
	 *                      src file, or <code>null</code>
	 */
	public FunctionData(int id, int[] argIds, boolean varargs, NodeEvaluator exprList, NodeEvaluator program,
			NodeEvaluator sprogram, boolean hasVarInScope, boolean hasFxnInScope, Value comment) {
		this.id = id;
		this.argIds = argIds;
		this.varargs = varargs;
		this.exprList = exprList;
		this.program = program;
		this.sprogram = sprogram;
		this.hasVarInScope = hasVarInScope;
		this.hasFxnInScope = hasFxnInScope;
		this.comment = comment;

		if (varargs)
			nargs = (argIds.length / 2) - 1;
		else
			nargs = (argIds.length / 2);
	}
	
	public FunctionData() {
	}

	/**
	 * Map arguments to a function into the member-table which is used for a
	 * function scope. Since the compiler ensures that the function parameters map
	 * to idx 0 thru n in the function-scope, all this has to do is collapse the
	 * var-arg parameter (if present) into a single array, and if there is a
	 * function within this function's body copy into new table.. This also ensures
	 * that the correct number of parameters is passed to the function. This is used
	 * instead of {@link #addArgs} when calling as a function.
	 * <p>
	 * XXX this could be used in case of constructor scope, by stripping out private
	 * parameters... maybe
	 * 
	 * @param args the input arguments
	 * @return
	 */
	public static final OArray __empty = new OArray();

	public final MemberTable mapArgs(MemberTable args) {
		if (hasFxnInScope)
			args = args.safeCopy();
		int alen = args.length();
		int diff1 = alen - nargs;
		if (diff1 == 0 || (varargs && (alen >= nargs))) {
			if (varargs) {
				OArray arr = diff1 == 0 ? __empty : new OArray(diff1);
				for (int i = nargs; i < alen; i++)
					arr.elementAt(i - nargs).opAssign(args.referenceAt(i));
				args.ensureCapacity(nargs);
				args.referenceAt(nargs).reset(arr);
			}
			return args;
		} else {
			throw PackagedScriptObjectException
					.makeExceptionWrapper(new OIllegalArgumentException("wrong number of args!"));
		}
	}

	/**
	 * A helper to populate a fxn-scope with args
	 */
	public final void addArgs(FunctionScope fxnScope, MemberTable args) {
		int len = (args == null) ? 0 : args.length();

		if ((len == nargs) || (varargs && (len >= nargs))) {
			for (int i = 0; i < nargs; i++) {
				int id = argIds[2 * i];
				int attr = argIds[2 * i + 1];

				fxnScope.createMember(id, attr).opAssign(args.referenceAt(i));
			}

			if (varargs) {
				int id = argIds[2 * nargs];
				int attr = argIds[2 * nargs + 1];
				// XXX in theory, it should be possible to bring back an optimization
				// to avoid the copy, if nargs==0....
				OArray arr = __empty; // EMPTY ARRAY ON NO DIFF
				if (len > nargs) {
					arr = new OArray(len - nargs);
					for (int i = nargs; i < len; i++)
						arr.elementAt(i - nargs).opAssign(args.referenceAt(i));
				}
				fxnScope.createMember(id, attr).opAssign(arr);
			}
		} else {
			throw PackagedScriptObjectException
					.makeExceptionWrapper(new OIllegalArgumentException("wrong number of args!"));
		}
	}

	public Value getName() {
		return Symbol.getSymbol(id);
	}


}
