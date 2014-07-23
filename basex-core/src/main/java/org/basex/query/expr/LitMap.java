package org.basex.query.expr;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.query.value.map.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * A literal map expression.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Leo Woerteler
 */
public final class LitMap extends Arr {
  /**
   * Constructor.
   * @param info input info
   * @param expr key and value expression, interleaved
   */
  public LitMap(final InputInfo info, final Expr[] expr) {
    super(info, expr);
    seqType = SeqType.MAP_O;
  }

  @Override
  public Expr compile(final QueryContext qc, final VarScope scp) throws QueryException {
    super.compile(qc, scp);
    return allAreValues() ? preEval(qc) : this;
  }

  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    Map map = Map.EMPTY;
    final int es = exprs.length;
    for(int i = 0; i < es; i++) {
      map = map.insert(exprs[i].item(qc, info), qc.value(exprs[++i]), ii);
    }
    return map;
  }

  @Override
  public Expr copy(final QueryContext qc, final VarScope scp, final IntObjMap<Var> vs) {
    return new LitMap(info, copyAll(qc, scp, vs, exprs));
  }

  @Override
  public String toString() {
    final TokenBuilder tb = new TokenBuilder("{ ");
    boolean key = true;
    for(final Expr e : exprs) {
      tb.add(key ? tb.size() > 2 ? ", " : "" : ":").add(e.toString());
      key ^= true;
    }
    return tb.add(" }").toString();
  }

  @Override
  public String description() {
    return QueryText.MAPSTR;
  }
}
