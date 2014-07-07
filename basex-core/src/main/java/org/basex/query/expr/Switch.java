package org.basex.query.expr;

import static org.basex.query.QueryText.*;

import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Switch expression.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class Switch extends ParseExpr {
  /** Cases. */
  private final SwitchCase[] cases;
  /** Condition. */
  private Expr cond;

  /**
   * Constructor.
   * @param info input info
   * @param cond condition
   * @param cases cases (last one is default case)
   */
  public Switch(final InputInfo info, final Expr cond, final SwitchCase[] cases) {
    super(info);
    this.cases = cases;
    this.cond = cond;
  }

  @Override
  public void checkUp() throws QueryException {
    checkNoUp(cond);
    for(final SwitchCase sc : cases) sc.checkUp();
    // check if none or all return expressions are updating
    final Expr[] tmp = new Expr[cases.length];
    for(int i = 0; i < tmp.length; ++i) tmp[i] = cases[i].exprs[0];
    checkAllUp(tmp);
  }

  @Override
  public Expr compile(final QueryContext qc, final VarScope scp) throws QueryException {
    cond = cond.compile(qc, scp);
    for(final SwitchCase sc : cases) sc.compile(qc, scp);

    // check if expression can be pre-evaluated
    Expr ex = this;
    if(cond.isValue()) {
      final Item it = cond.item(qc, info);
      LOOP:
      for(final SwitchCase sc : cases) {
        final int sl = sc.exprs.length;
        for(int e = 1; e < sl; e++) {
          if(!sc.exprs[e].isValue()) break LOOP;

          // includes check for empty sequence (null reference)
          final Item cs = sc.exprs[e].item(qc, info);
          if(it == cs || cs != null && it != null && it.equiv(cs, null, info)) {
            ex = sc.exprs[0];
            break LOOP;
          }
        }
        if(sl == 1) ex = sc.exprs[0];
      }
    }
    if(ex != this) return optPre(ex, qc);

    // expression could not be pre-evaluated
    type = cases[0].exprs[0].type();
    for(int c = 1; c < cases.length; c++) {
      type = type.union(cases[c].exprs[0].type());
    }
    return ex;
  }

  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    return qc.iter(getCase(qc));
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    return qc.value(getCase(qc));
  }

  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    return getCase(qc).item(qc, ii);
  }

  @Override
  public boolean isVacuous() {
    for(final SwitchCase sc : cases) if(!sc.exprs[0].isVacuous()) return false;
    return true;
  }

  @Override
  public boolean has(final Flag flag) {
    for(final SwitchCase sc : cases) if(sc.has(flag)) return true;
    return cond.has(flag);
  }

  @Override
  public boolean removable(final Var v) {
    for(final SwitchCase sc : cases) if(!sc.removable(v)) return false;
    return cond.removable(v);
  }

  @Override
  public VarUsage count(final Var v) {
    VarUsage max = VarUsage.NEVER, curr = VarUsage.NEVER;
    for(final SwitchCase cs : cases) {
      curr = curr.plus(cs.countCases(v));
      max = max.max(curr.plus(cs.count(v)));
    }
    return max.plus(cond.count(v));
  }

  @Override
  public Expr inline(final QueryContext qc, final VarScope scp, final Var v, final Expr e)
      throws QueryException {
    boolean change = inlineAll(qc, scp, cases, v, e);
    final Expr cn = cond.inline(qc, scp, v, e);
    if(cn != null) {
      change = true;
      cond = cn;
    }
    return change ? optimize(qc, scp) : null;
  }

  /**
   * Chooses the selected {@code case} expression.
   * @param qc query context
   * @return case expression
   * @throws QueryException query exception
   */
  private Expr getCase(final QueryContext qc) throws QueryException {
    final Item it = cond.item(qc, info);
    for(final SwitchCase sc : cases) {
      final int sl = sc.exprs.length;
      for(int e = 1; e < sl; e++) {
        // includes check for empty sequence (null reference)
        final Item cs = sc.exprs[e].item(qc, info);
        if(it == cs || it != null && cs != null && it.equiv(cs, null, info))
          return sc.exprs[0];
      }
      if(sl == 1) return sc.exprs[0];
    }
    // will never be evaluated
    return null;
  }

  @Override
  public Expr copy(final QueryContext qc, final VarScope scp, final IntObjMap<Var> vs) {
    return new Switch(info, cond.copy(qc, scp, vs), Arr.copyAll(qc, scp, vs, cases));
  }

  @Override
  public void plan(final FElem plan) {
    addPlan(plan, planElem(), cond, cases);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(SWITCH + PAR1 + cond + PAR2);
    for(final SwitchCase sc : cases) sb.append(sc);
    return sb.toString();
  }

  @Override
  public void markTailCalls(final QueryContext qc) {
    for(final SwitchCase sc : cases) sc.markTailCalls(qc);
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    return cond.accept(visitor) && visitAll(visitor, cases);
  }

  @Override
  public int exprSize() {
    int sz = 1;
    for(final Expr e : cases) sz += e.exprSize();
    return sz;
  }
}
