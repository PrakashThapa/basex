package org.basex.io.serial.csv;

import static org.basex.util.Token.*;

import java.io.*;

import org.basex.build.csv.*;
import org.basex.io.serial.*;
import org.basex.util.*;
import org.basex.util.list.*;

/**
 * This class serializes data as CSV.
 *
 * @author BaseX Team 2005-15, BSD License
 * @author Christian Gruen
 */
abstract class CsvSerializer extends OutputSerializer {
  /** CSV options. */
  final CsvOptions copts;
  /** Separator. */
  private final int separator;
  /** Generate quotes. */
  private final boolean quotes;
  /** Generate backslashes. */
  private final boolean backslashes;

  /**
   * Constructor.
   * @param os output stream reference
   * @param opts serialization parameters
   * @throws IOException I/O exception
   */
  CsvSerializer(final OutputStream os, final SerializerOptions opts) throws IOException {
    super(os, opts);
    copts = opts.get(SerializerOptions.CSV);
    quotes = copts.get(CsvOptions.QUOTES);
    backslashes = copts.get(CsvOptions.BACKSLASHES);
    separator = copts.separator();
  }

  /**
   * Prints a record with the specified fields.
   * @param fields fields to be printed
   * @throws IOException I/O exception
   */
  void record(final TokenList fields) throws IOException {
    // print fields, skip trailing empty contents
    final int fs = fields.size();
    for(int i = 0; i < fs; i++) {
      final byte[] v = fields.get(i);
      if(i != 0) print(separator);

      byte[] txt = v == null ? EMPTY : v;
      if(contains(txt, separator) || (quotes || backslashes) &&
          (contains(txt, '\n') || contains(txt, '"'))) {
        final TokenBuilder tb = new TokenBuilder();
        if(quotes) tb.add('"');
        final int len = txt.length;
        for(int c = 0; c < len; c += cl(txt, c)) {
          final int cp = cp(txt, c);
          if(cp == '"') tb.add(backslashes ? '\\' : '"');
          tb.add(cp);
        }
        if(quotes) tb.add('"');
        txt = tb.finish();
      }
      print(txt);
    }
    print(nl);
  }
}
