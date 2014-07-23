package org.basex.query.func;

import static org.basex.query.func.Function.*;
import static org.basex.query.util.Err.*;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;

/**
 * Functions on functions.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Leo Woerteler
 */
public final class FNFunc extends StandardFunc {
  /** Minimum size of a loop that should not be unrolled. */
  static final int UNROLL_LIMIT = 10;

  /**
   * Constructor.
   * @param sc static context
   * @param info input info
   * @param func function definition
   * @param args arguments
   */
  public FNFunc(final StaticContext sc, final InputInfo info, final Function func,
      final Expr... args) {
    super(sc, info, func, args);
  }

  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    switch(func) {
      case FOR_EACH:      return forEach(qc);
      case FILTER:        return filter(qc);
      case FOR_EACH_PAIR: return forEachPair(qc);
      case FOLD_LEFT:     return foldLeft(qc);
      case FOLD_RIGHT:    return foldRight(qc);
      default:            return super.iter(qc);
    }
  }

  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    switch(func) {
      case FUNCTION_ARITY:  return Int.get(checkFunc(exprs[0], qc).arity());
      case FUNCTION_NAME:   return checkFunc(exprs[0], qc).funcName();
      case FUNCTION_LOOKUP: return lookup(qc, ii);
      default:              return super.item(qc, ii);
    }
  }

  @Override
  Expr opt(final QueryContext qc, final VarScope scp) throws QueryException {
    if(oneOf(func, FOLD_LEFT, FOLD_RIGHT, FOR_EACH)
        && allAreValues() && exprs[0].size() < UNROLL_LIMIT) {
      // unroll the loop
      qc.compInfo(QueryText.OPTUNROLL, this);
      final Value seq = (Value) exprs[0];
      final int len = (int) seq.size();

      // fn:for-each(...)
      if (func == FOR_EACH) {
        final Expr[] results = new Expr[len];
        for(int i = 0; i < len; i++) {
          results[i] = new DynFuncCall(info, sc, false, exprs[1], seq.itemAt(i)).optimize(qc, scp);
        }
        return new List(info, results).optimize(qc, scp);
      }

      // folds
      Expr e = exprs[1];
      if (func == FOLD_LEFT) {
        for (final Item it : seq)
          e = new DynFuncCall(info, sc, false, exprs[2], e, it).optimize(qc, scp);
      } else {
        for (int i = len; --i >= 0;)
          e = new DynFuncCall(info, sc, false, exprs[2], seq.itemAt(i), e).optimize(qc, scp);
      }
      return e;
    }

    if(func == FUNCTION_LOOKUP) {
      for(final StaticFunc sf : qc.funcs.funcs()) sf.compile(qc);
    }
    return this;
  }

  /**
   * Looks up the specified function item.
   * @param qc query context
   * @param ii input info
   * @return function item
   * @throws QueryException query exception
   */
  private Item lookup(final QueryContext qc, final InputInfo ii) throws QueryException {
    final QNm name = checkQNm(exprs[0], qc, sc);
    final long arity = checkItr(exprs[1], qc);
    if(arity < 0 || arity > Integer.MAX_VALUE) throw FUNCUNKNOWN.get(ii, name);

    try {
      final Expr lit = Functions.getLiteral(name, (int) arity, qc, sc, ii);
      return lit == null ? null : lit.item(qc, ii);
    } catch(final QueryException e) {
      // function not found (in most cases: XPST0017)
      return null;
    }
  }

  /**
   * Maps a function onto a sequence of items.
   * @param qc query context
   * @return sequence of results
   * @throws QueryException exception
   */
  private Iter forEach(final QueryContext qc) throws QueryException {
    final FItem f = withArity(1, 1, qc);
    final Iter xs = exprs[0].iter(qc);
    return new Iter() {
      /** Results. */
      Iter ys = Empty.ITER;

      @Override
      public Item next() throws QueryException {
        do {
          final Item it = ys.next();
          if(it != null) return it;
          final Item x = xs.next();
          if(x == null) return null;
          ys = f.invokeValue(qc, info, x).iter();
        } while(true);
      }
    };
  }

  /**
   * Filters the given sequence with the given predicate.
   * @param qc query context
   * @return filtered sequence
   * @throws QueryException query exception
   */
  private Iter filter(final QueryContext qc) throws QueryException {
    final FItem f = withArity(1, 1, qc);
    final Iter xs = exprs[0].iter(qc);
    return new Iter() {
      @Override
      public Item next() throws QueryException {
        do {
          final Item it = xs.next();
          if(it == null) return null;
          if(checkBln(f.invokeItem(qc, info, it))) return it;
        } while(true);
      }
    };
  }

  /**
   * Zips two sequences with the given zipper function.
   * @param qc query context
   * @return sequence of results
   * @throws QueryException query exception
   */
  private Iter forEachPair(final QueryContext qc) throws QueryException {
    final FItem zipper = withArity(2, 2, qc);
    final Iter xs = exprs[0].iter(qc);
    final Iter ys = exprs[1].iter(qc);
    return new Iter() {
      /** Results. */
      Iter zs = Empty.ITER;

      @Override
      public Item next() throws QueryException {
        do {
          final Item it = zs.next();
          if(it != null) return it;
          final Item x = xs.next(), y = ys.next();
          if(x == null || y == null) return null;
          zs = zipper.invokeValue(qc, info, x, y).iter();
        } while(true);
      }
    };
  }

  /**
   * Folds a sequence into a return value, starting from the left.
   * @param qc query context
   * @return resulting sequence
   * @throws QueryException query exception
   */
  private Iter foldLeft(final QueryContext qc) throws QueryException {
    final FItem f = withArity(2, 2, qc);
    final Iter xs = exprs[0].iter(qc);
    Item x = xs.next();

    // don't convert to a value if not necessary
    if(x == null) return exprs[1].iter(qc);

    Value sum = qc.value(exprs[1]);
    do sum = f.invokeValue(qc, info, sum, x);
    while((x = xs.next()) != null);
    return sum.iter();
  }

  /**
   * Folds a sequence into a return value, starting from the left.
   * @param qc query context
   * @return resulting sequence
   * @throws QueryException query exception
   */
  private Iter foldRight(final QueryContext qc) throws QueryException {
    final FItem f = withArity(2, 2, qc);
    final Value xs = qc.value(exprs[0]);
    // evaluate start value lazily if it's passed straight through
    if(xs.isEmpty()) return exprs[1].iter(qc);

    Value res = qc.value(exprs[1]);
    for(long i = xs.size(); --i >= 0;) res = f.invokeValue(qc, info, xs.itemAt(i), res);
    return res.iter();
  }

  /**
   * Casts and checks the function item for its arity.
   * @param p position of the function
   * @param a arity
   * @param qc query context
   * @return function item
   * @throws QueryException query exception
   */
  private FItem withArity(final int p, final int a, final QueryContext qc) throws QueryException {
    final Item it = checkItem(exprs[p], qc);
    if(it instanceof FItem) {
      final FItem fi = (FItem) it;
      if(fi.arity() == a) return fi;
    }
    throw castError(info, it, FuncType.arity(a));
  }
}
