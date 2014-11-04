package org.basex.query.expr;

import static org.basex.query.func.Function.*;
import static org.basex.query.util.Err.*;

import org.basex.query.*;
import org.junit.*;

/**
 * Lookup operator tests.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class LookupTest extends AdvancedQueryTest {
  /** Test. */
  @Test public void map() {
    query("map { 'a':'b' } ? a", "b");
    query("(map { 'a':'b' }, map { 'c':'d' }) ? a", "b");
    query("map { 'a':'b', 'c':'d' } ? ('a','c')", "b d");
    query("(map { 'a':'b' }, map { 'c':'d' }) ? ('a','c')", "b d");
    query(_MAP_MERGE.args(" for $i in 1 to 5 return map { $i: $i+1 }") + " ? 2", "3");

    query("map { 'first' : 'Jenna', 'last' : 'Scott' } ? first", "Jenna");
    query("(map {'first': 'Tom'}, map {'first': 'Dick'}, map {'first': 'Harry'}) ? first",
        "Tom Dick Harry");
  }

  /** Test. */
  @Test public void array() {
    query("array { 'a', 'b' } ? 1", "a");
    query("(array { 'a', 'b' }, array { 'c', 'd' }) ? 1", "a c");
    query("(array { 'a', 'b', 'c' }) ? (1, 2)", "a b");
    query("(array { 'a', 'b', 'c' }) ? (1 to 2)", "a b");
    query(_ARRAY_JOIN.args(" for $i in 1 to 5 return array { $i+1 }") + " ? 2", "3");

    query("[1, 2, 5, 7] ?*", "1 2 5 7");
    query("[[1, 2, 3], [4, 5, 6]] ?* ?*", "1 2 3 4 5 6");
    query("[4, 5, 6]?2", "5");
    query("([1,2,3], [4,5,6])?2", "2 5");
  }

  /** Test. */
  @Test public void mixed() {
    query("(map { 1: 'm' }, array { 'a' }) ? 1", "m a");
  }

  /** Test. */
  @Test public void wildcard() {
    query("(map { 1: 'm' }, array { 'a' }) ? *", "m a");
  }

  /** Test. */
  @Test public void unary() {
    query("(map { 1: 'm' }, array { 'a' }) ! ?*", "m a");
    query("array { 1 }[?1] ! ?1", "1");
  }

  /** Test. */
  @Test public void error() {
    error("1?a", CTXMAPARRAY);
  }
}
