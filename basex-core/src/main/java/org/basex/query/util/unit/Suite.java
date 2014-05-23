package org.basex.query.util.unit;

import static org.basex.query.util.unit.Constants.*;

import java.io.*;
import java.util.*;

import org.basex.core.*;
import org.basex.io.*;
import org.basex.query.value.node.*;
import org.basex.util.*;

/**
 * XQuery Unit tests: Testing multiple modules.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class Suite {
  /** Failures. */
  public int failures;
  /** Errors. */
  public int errors;
  /** Skipped. */
  public int skipped;
  /** Tests. */
  public int tests;

  /**
   * Tests all test functions in the specified path.
   * @param ctx database context
   * @param root path to test modules
   * @return resulting value
   * @throws IOException I/O exception
   */
  public FElem test(final IOFile root, final Context ctx) throws IOException {
    final ArrayList<IOFile> files = new ArrayList<>();

    final Performance perf = new Performance();
    final FElem suites = new FElem(TESTSUITES);
    if(root.isDir()) {
      for(final String path : root.descendants()) {
        final IOFile file = new IOFile(root, path);
        if(file.hasSuffix(IO.XQSUFFIXES)) files.add(file);
      }
    } else {
      files.add(root);
    }

    for(final IOFile file : files) {
      final Unit unit = new Unit(file, ctx);
      unit.test(suites);
      errors += unit.errors;
      failures += unit.failures;
      skipped += unit.skipped;
      tests += unit.tests;
    }

    suites.add(TIME, Unit.time(perf));
    return suites;
  }
}
