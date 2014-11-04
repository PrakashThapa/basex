package org.basex.query.func.inspect;

import static org.basex.query.util.Err.*;

import java.io.*;
import java.util.*;

import org.basex.io.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.item.*;
import org.basex.query.var.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class InspectFunctions extends StandardFunc {
  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    checkCreate(qc);
    // about to be updated in a future version
    final ArrayList<StaticFunc> old = new ArrayList<>();
    if(exprs.length > 0) {
      // cache existing functions
      Collections.addAll(old, qc.funcs.funcs());
      try {
        final IO io = checkPath(exprs[0], qc);
        qc.parse(Token.string(io.read()), io.path(), sc);
        qc.compile();
      } catch(final IOException ex) {
        throw IOERR_X.get(info, ex);
      }
    }

    final ValueBuilder vb = new ValueBuilder();
    for(final StaticFunc sf : qc.funcs.funcs()) {
      if(old.contains(sf)) continue;
      final FuncItem fi = Functions.getUser(sf, qc, sf.sc, info);
      if(sc.mixUpdates || !fi.annotations().contains(Ann.Q_UPDATING)) vb.add(fi);
    }
    return vb;
  }

  @Override
  protected Expr opt(final QueryContext qc, final VarScope scp) throws QueryException {
    if(exprs.length == 0) {
      for(final StaticFunc sf : qc.funcs.funcs()) sf.compile(qc);
      return iter(qc).value();
    }
    return this;
  }

  @Override
  public boolean has(final Flag flag) {
    // do not relocate function, as it introduces new code
    return flag == Flag.NDT && exprs.length == 1 || super.has(flag);
  }
}
