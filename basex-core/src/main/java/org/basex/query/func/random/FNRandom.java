package org.basex.query.func.random;

import static org.basex.query.util.Err.*;

import java.util.*;

import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Random functions.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Dirk Kirsten
 */
public final class FNRandom extends StandardFunc {
  /** Random instance. */
  private static final Random RND = new Random();

  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    switch(func) {
      case _RANDOM_DOUBLE:  return Dbl.get(randomDouble());
      case _RANDOM_INTEGER: return Int.get(randomInt(qc));
      case _RANDOM_UUID:    return Str.get(UUID.randomUUID().toString());
      default:              return super.item(qc, ii);
    }
  }
  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    switch(func) {
      case _RANDOM_SEEDED_DOUBLE:  return randomSeededDouble(qc);
      case _RANDOM_SEEDED_INTEGER: return randomSeededInt(qc);
      case _RANDOM_GAUSSIAN:       return randomGaussian(qc);
      default:                     return super.iter(qc);
    }
  }

  /**
   * Returns a random integer, either in the whole integer range or if a maximum is given between 0
   * (inclusive) and the given maximum (exclusive).
   * @param qc query context
   * @return random integer
   * @throws QueryException query exception
   */
  private int randomInt(final QueryContext qc) throws QueryException {
    if(exprs.length == 0) return RND.nextInt();
    final long max = toLong(exprs[0], qc);
    if(max < 1 || max > Integer.MAX_VALUE) throw BXRA_BOUNDS_X.get(info, max);
    return RND.nextInt((int) max);
  }

  /**
   * Returns a sequence of random integers with exactly $num items using a seed for initializing the
   * random function, either in the whole integer range or if a maximum is given between 0
   * (inclusive) and the given maximum (exclusive).
   * @param qc query context
   * @return random integer
   * @throws QueryException query exception
   */
  private Iter randomSeededInt(final QueryContext qc) throws QueryException {
    final long seed = toLong(exprs[0], qc);
    final long num = toLong(exprs[1], qc);
    if(num < 0) throw BXRA_NUM_X.get(info, num);
    final long max = exprs.length > 2 ? toLong(exprs[2], qc) : Integer.MAX_VALUE;
    if(max < 1 || max > Integer.MAX_VALUE) throw BXRA_BOUNDS_X.get(info, max);

    return new Iter() {
      final Random r = new Random(seed);
      long c;

      @Override
      public Item next() throws QueryException {
        return ++c <= num ? Int.get(
            max == Integer.MAX_VALUE ? r.nextInt() : r.nextInt((int) max)) : null;
      }
    };
  }

  /**
   * Returns a random double between 0.0 (inclusive) and 1.0 (exclusive).
   * @return random double
   */
  private static double randomDouble() {
    return RND.nextDouble();
  }

  /**
   * Returns a sequence of random double with exactly $num items using a seed between 0.0
   * (inclusive) and 1.0 (exclusive).
   * @param qc query context
   * @return random double
   * @throws QueryException query exception
   */
  private Iter randomSeededDouble(final QueryContext qc) throws QueryException {
    final long seed = toLong(exprs[0], qc);
    final long num = toLong(exprs[1], qc);
    if(num < 0) throw BXRA_NUM_X.get(info, num);

    return new Iter() {
      final Random r = new Random(seed);
      long c;

      @Override
      public Item next() {
        return ++c <= num ? Dbl.get(r.nextDouble()) : null;
      }
    };
  }

  /**
   * Returns a sequence of random doubles with exactly $num items using a Gaussian (i.e. normal)
   * distribution with a mean of 0.0 and a derivation of 1.0
   * @param qc query context
   * @return random double
   * @throws QueryException query exception
   */
  private Iter randomGaussian(final QueryContext qc) throws QueryException {
    return new Iter() {
      final int num = (int) toLong(exprs[0], qc);
      int count;
      @Override
      public Item next() {
        return ++count <= num ? Dbl.get(RND.nextGaussian()) : null;
      }
    };
  }
}
