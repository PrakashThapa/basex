package org.basex.query.gflwor;

import static org.basex.query.QueryText.*;

import java.util.List;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.gflwor.GFLWOR.Clause;
import org.basex.query.gflwor.GFLWOR.Eval;
import org.basex.query.iter.*;
import org.basex.query.path.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.value.type.SeqType.Occ;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * FLWOR {@code for} clause, iterating over a sequence.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Leo Woerteler
 */
public final class For extends ForLet {
  /** Position variable. */
  final Var pos;
  /** Score variable. */
  final Var score;
  /** {@code allowing empty} flag. */
  final boolean empty;

  /**
   * Constructor.
   * @param var item variable
   * @param pos position variable or {@code null}
   * @param score score variable or {@code null}
   * @param expr bound expression
   * @param empty {@code allowing empty} flag
   * @param info input info
   */
  public For(final Var var, final Var pos, final Var score, final Expr expr, final boolean empty,
      final InputInfo info) {
    super(info, var, expr, vars(var, pos, score));
    this.pos = pos;
    this.score = score;
    this.empty = empty;
  }

  @Override
  Eval eval(final Eval sub) {
    return new Eval() {
      /** Expression iterator. */
      private Iter iter;
      /** Current position. */
      private long p;
      @Override
      public boolean next(final QueryContext qc) throws QueryException {
        while(true) {
          final Item it = iter == null ? null : iter.next();
          if(it != null) {
            // there's another item to serve
            ++p;
            qc.set(var, it, info);
            if(pos != null) qc.set(pos, Int.get(p), info);
            if(score != null) qc.set(score, Dbl.get(it.score()), info);
            return true;
          }
          if(empty && iter != null && p == 0) {
            // expression yields no items, bind the empty sequence instead
            qc.set(var, Empty.SEQ, info);
            if(pos != null) qc.set(pos, Int.get(p), info);
            if(score != null) qc.set(score, Dbl.get(0), info);
            iter = null;
            return true;
          }
          // no more iterations from above, we're done here
          if(!sub.next(qc)) return false;

          // next iteration, reset iterator and counter
          iter = expr.iter(qc);
          p = 0;
        }
      }
    };
  }

  @Override
  public For optimize(final QueryContext qc, final VarScope scp) throws QueryException {
    final SeqType tp = expr.type();
    final boolean emp = empty && tp.mayBeZero();
    type = SeqType.get(tp.type, emp ? Occ.ZERO_ONE : Occ.ONE);
    var.refineType(type, qc, info);
    if(pos != null) pos.refineType(SeqType.ITR, qc, info);
    if(score != null) score.refineType(SeqType.DBL, qc, info);
    size = emp ? -1 : 1;
    return this;
  }

  @Override
  public For copy(final QueryContext qc, final VarScope scp, final IntObjMap<Var> vs) {
    final Var v = scp.newCopyOf(qc, var);
    vs.put(var.id, v);
    final Var p = pos == null ? null : scp.newCopyOf(qc, pos);
    if(p != null) vs.put(pos.id, p);
    final Var s = score == null ? null : scp.newCopyOf(qc, score);
    if(s != null) vs.put(score.id, s);
    return new For(v, p, s, expr.copy(qc, scp, vs), empty, info);
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    return expr.accept(visitor) && visitor.declared(var)
        && (pos == null || visitor.declared(pos))
        && (score == null || visitor.declared(score));
  }

  /**
   * Gathers all non-{@code null} variables.
   * @param v var
   * @param p pos
   * @param s scope
   * @return non-{@code null} variables
   */
  private static Var[] vars(final Var v, final Var p, final Var s) {
    return p == null ? s == null ? new Var[] { v } : new Var[] { v, s } :
      s == null ? new Var[] { v, p } : new Var[] { v, p, s };
  }

  /**
   * Tries to convert this for loop into a let binding.
   * @param clauses FLWOR clauses
   * @param p position
   * @return {@code true} if the clause was converted, {@code false} otherwise
   */
  boolean asLet(final List<Clause> clauses, final int p) {
    if(expr.size() != 1 && !expr.type().one()) return false;
    clauses.set(p, Let.fromFor(this));
    if(score != null) clauses.add(p + 1, Let.fromForScore(this));
    if(pos != null) clauses.add(p + 1, new Let(pos, Int.get(1), false, info));
    return true;
  }

  /**
   * Tries to add the given expression as an attribute to this loop's sequence.
   * @param qc query context
   * @param scp variable scope
   * @param ex expression to add as predicate
   * @return success
   * @throws QueryException query exception
   */
  boolean toPred(final QueryContext qc, final VarScope scp, final Expr ex) throws QueryException {
    if(empty || !(vars.length == 1 && ex.uses(var) && ex.removable(var))) return false;

    // reset context value (will not be accessible within predicate)
    final Value cv = qc.value;
    Expr pred = ex;
    try {
      qc.value = null;
      // assign type of iterated items to context expression
      final Context c = new Context(info);
      c.type = expr.type().type.seqType();
      final Expr r = ex.inline(qc, scp, var, c);
      if(r != null) pred = r;
    } finally {
      qc.value = cv;
    }

    // attach predicates to axis path or filter, or create a new filter
    if(pred.type().mayBeNumber()) pred = Function.BOOLEAN.get(null, info, pred);

    // add to clause expression
    if(expr instanceof AxisPath) {
      expr = ((Path) expr).addPreds(qc, scp, pred);
    } else if(expr instanceof Filter) {
      expr = ((Filter) expr).addPred(qc, scp, pred);
    } else {
      expr = Filter.get(info, expr, pred).optimize(qc, scp);
    }

    return true;
  }

  @Override
  long calcSize(final long count) {
    final long sz = expr.size();
    return sz < 0 ? -1 : sz > 0 ? sz * count : empty ? 1 : 0;
  }

  @Override
  public void plan(final FElem plan) {
    final FElem e = planElem();
    if(empty) e.add(planAttr(Token.token(EMPTYORD), Token.TRUE));
    var.plan(e);
    if(pos != null) {
      final FElem e2 = new FElem(AT);
      pos.plan(e2);
      e.add(e2);
    }

    if(score != null) {
      final FElem e2 = new FElem(SCORE);
      score.plan(e2);
      e.add(e2);
    }

    expr.plan(e);
    plan.add(e);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(FOR).append(' ').append(var);
    if(empty) sb.append(' ').append(ALLOWING).append(' ').append(EMPTYORD);
    if(pos != null) sb.append(' ').append(AT).append(' ').append(pos);
    if(score != null) sb.append(' ').append(SCORE).append(' ').append(score);
    return sb.append(' ').append(IN).append(' ').append(expr).toString();
  }
}
