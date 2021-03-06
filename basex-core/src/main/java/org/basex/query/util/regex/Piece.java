package org.basex.query.util.regex;

/**
 * An atom together with a quantifier.
 *
 * @author BaseX Team 2005-15, BSD License
 * @author Leo Woerteler
 */
public final class Piece extends RegExp {
  /** Wrapped atom. */
  private final RegExp atom;
  /** Quantifier. */
  private final Quantifier quant;

  /**
   * Constructor.
   * @param at atom
   * @param qu quantifier
   */
  public Piece(final RegExp at, final Quantifier qu) {
    atom = at;
    quant = qu;
  }

  @Override
  StringBuilder toRegEx(final StringBuilder sb) {
    return quant.toRegEx(atom.toRegEx(sb));
  }

}
