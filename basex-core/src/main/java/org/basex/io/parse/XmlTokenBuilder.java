package org.basex.io.parse;

import static org.basex.data.DataText.*;
import static org.basex.util.Token.*;

import org.basex.util.*;
import org.basex.util.list.*;

/**
 * Intermediate XML string builder for importing other data formats to a database.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class XmlTokenBuilder {
  /** XML string. */
  private final TokenBuilder cache = new TokenBuilder();
  /** Element stack. */
  private final TokenList open = new TokenList();

  /**
   * Opens an element.
   * @param name name of element
   * @param atts attribute names and values
   */
  public void open(final byte[] name, final byte[]... atts) {
    final TokenBuilder cch = cache;
    cch.add('<').add(name);
    final int al = atts.length;
    for(int a = 0; a < al; a += 2) {
      cch.add(' ').add(atts[a]).add('=').add('"');
      attribute(atts[a + 1]);
      cch.add('"');
    }
    cch.add('>');
    open.add(name);
  }

  /**
   * Closes an element.
   */
  public void close() {
    cache.add('<').add('/').add(open.pop()).add('>');
  }

  /**
   * Encodes the specified text.
   * @param value value to be encoded
   */
  public void text(final byte[] value) {
    final int tl = value.length;
    for(int k = 0; k < tl; k += cl(value, k)) add(cp(value, k));
  }

  /**
   * Returns the token as byte array, and invalidates the internal array.
   * @return XML token
   */
  public byte[] finish() {
    return cache.finish();
  }

  /**
   * Encodes the specified attribute value.
   * @param value value to be encoded
   */
  private void attribute(final byte[] value) {
    final int vl = value.length;
    for(int k = 0; k < vl; k += cl(value, k)) {
      final int ch = cp(value, k);
      if(ch == '"') {
        cache.add(E_QU);
      } else if(ch == 0x9 || ch == 0xA) {
        addHex(ch);
      } else {
        add(ch);
      }
    }
  }

  /**
   * Encodes the specified character.
   * @param ch character to be encoded
   */
  private void add(final int ch) {
    if(ch < ' ' && ch != '\n' && ch != '\t' || ch >= 0x7F && ch < 0xA0) {
      addHex(ch);
    } else if(ch == '&') {
      cache.add(E_AMP);
    } else if(ch == '>') {
      cache.add(E_GT);
    } else if(ch == '<') {
      cache.add(E_LT);
    } else if(ch == 0x2028) {
      cache.add(E_2028);
    } else {
      cache.add(ch);
    }
  }

  /**
   * Returns a hex entity for the specified character.
   * @param ch character
   */
  private void addHex(final int ch) {
    cache.add("&#x");
    final int h = ch >> 4;
    if(h != 0) cache.add(HEX[h]);
    cache.add(HEX[ch & 15]).add(';');
  }
}
