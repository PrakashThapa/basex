package org.basex.query.func;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.map.*;
import org.basex.util.*;

/**
 * Functions on maps.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Leo Woerteler
 */
public final class FNMap extends StandardFunc {
  /**
   * Constructor.
   * @param sc static context
   * @param info input info
   * @param func function definition
   * @param args arguments
   */
  public FNMap(final StaticContext sc, final InputInfo info, final Function func,
      final Expr... args) {
    super(sc, info, func, args);
  }

  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    switch(func) {
      case _MAP_GET:  return get(qc).iter();
      case _MAP_KEYS: return map(qc).keys().iter();
      default:        return super.iter(qc);
    }
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    switch(func) {
      case _MAP_GET:  return get(qc);
      case _MAP_KEYS: return map(qc).keys();
      default:        return super.value(qc);
    }
  }

  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    switch(func) {
      case _MAP_NEW:       return newMap(qc, ii);
      case _MAP_ENTRY:     return entry(qc, ii);
      case _MAP_CONTAINS:  return Bln.get(contains(qc, ii));
      case _MAP_SIZE:      return Int.get(map(qc).mapSize());
      case _MAP_REMOVE:    return remove(qc, ii);
      case _MAP_COLLATION: return map(qc).collation();
      case _MAP_SERIALIZE: return Str.get(map(qc).serialize(info));
      default:             return super.item(qc, ii);
    }
  }

  /**
   * Removes a key from a map.
   * @param qc query context
   * @param ii input info
   * @return new map with key removed from it
   * @throws QueryException query exception
   */
  private Map remove(final QueryContext qc, final InputInfo ii) throws QueryException {
    return map(qc).delete(exprs[1].item(qc, ii), ii);
  }

  /**
   * Creates a new map containing the given key-value pair.
   * @param qc query context
   * @param ii input info
   * @return the singleton map
   * @throws QueryException query exception
   */
  private Map entry(final QueryContext qc, final InputInfo ii) throws QueryException {
    return Map.EMPTY.insert(exprs[0].item(qc, ii), qc.value(exprs[1]), ii);
  }

  /**
   * Creates a new map from a list of old ones.
   * @param qc query context
   * @param ii input info
   * @return new map
   * @throws QueryException query exception
   */
  private Map newMap(final QueryContext qc, final InputInfo ii) throws QueryException {
    if(exprs.length == 0) return Map.EMPTY;
    // collations are ignored here as they may disappear in a future version
    checkColl(exprs.length == 2 ? exprs[1] : null, qc, sc);

    Map map = Map.EMPTY;
    final Iter maps = exprs[0].iter(qc);
    for(Item m; (m = maps.next()) != null;)
      map = map.addAll(checkMap(m), ii);
    return map;
  }

  /**
   * Looks up the given key in the given map.
   * @param qc query context
   * @return bound value or empty sequence if none exists
   * @throws QueryException query exception
   */
  private Value get(final QueryContext qc) throws QueryException {
    return map(qc).get(exprs[1].item(qc, info), info);
  }

  /**
   * Checks if the given key is contained in the given map.
   * @param qc query context
   * @param ii input info
   * @return result of check
   * @throws QueryException query exception
   */
  private boolean contains(final QueryContext qc, final InputInfo ii) throws QueryException {
    return map(qc).contains(exprs[1].item(qc, ii), ii);
  }

  /**
   * Gets the map at the first argument position.
   * @param qc query context
   * @return map
   * @throws QueryException query exception
   */
  private Map map(final QueryContext qc) throws QueryException {
    return checkMap(checkItem(exprs[0], qc));
  }
}
