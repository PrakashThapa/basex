package org.basex.query.expr;

import static org.basex.query.QueryText.*;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Instance test.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class Instance extends Single {
  /** Sequence type. */
  private final SeqType seq;

  /**
   * Constructor.
   * @param info input info
   * @param expr expression
   * @param seq sequence type
   */
  public Instance(final InputInfo info, final Expr expr, final SeqType seq) {
    super(info, expr);
    this.seq = seq;
    type = SeqType.BLN;
  }

  @Override
  public Expr compile(final QueryContext qc, final VarScope scp) throws QueryException {
    super.compile(qc, scp);
    return expr.isValue() ? preEval(qc) : this;
  }

  @Override
  public Bln item(final QueryContext qc, final InputInfo ii) throws QueryException {
    return Bln.get(seq.instance(qc.value(expr)));
  }

  @Override
  public Expr copy(final QueryContext qc, final VarScope scp, final IntObjMap<Var> vs) {
    return new Instance(info, expr.copy(qc, scp, vs), seq);
  }

  @Override
  public void plan(final FElem plan) {
    addPlan(plan, planElem(TYP, seq), expr);
  }

  @Override
  public String toString() {
    return Util.info("% instance of %", expr, seq);
  }
}
