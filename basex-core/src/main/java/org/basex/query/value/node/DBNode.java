package org.basex.query.value.node;

import static org.basex.query.QueryText.*;
import static org.basex.query.func.Function.*;

import java.io.*;

import org.basex.build.*;
import org.basex.core.*;
import org.basex.data.*;
import org.basex.io.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;
import org.basex.query.value.type.Type.ID;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;
import org.basex.util.list.*;

/**
 * Database node.
 *
 * @author BaseX Team 2005-15, BSD License
 * @author Christian Gruen
 */
public class DBNode extends ANode {
  /** Data reference. */
  public final Data data;
  /** Pre value. */
  public int pre;
  /** Namespaces. */
  private Atts nsp;

  /**
   * Constructor, creating a document node from the specified data reference.
   * @param data data reference
   */
  public DBNode(final Data data) {
    this(data, 0);
  }

  /**
   * Constructor, creating a node from the specified data reference.
   * @param data data reference
   * @param pre pre value
   */
  public DBNode(final Data data, final int pre) {
    this(data, pre, data.kind(pre));
  }

  /**
   * Constructor, specifying full node information.
   * @param data data reference
   * @param pre pre value
   * @param kind node kind
   */
  public DBNode(final Data data, final int pre, final int kind) {
    this(data, pre, null, type(kind));
  }

  /**
   * Constructor, specifying full node information.
   * @param data data reference
   * @param pre pre value
   * @param par parent reference
   * @param type node type
   */
  DBNode(final Data data, final int pre, final ANode par, final NodeType type) {
    super(type);
    this.data = data;
    this.pre = pre;
    parent = par;
  }

  /**
   * Constructor, specifying an XML input reference.
   * @param input input reference
   * @throws IOException I/O exception
   */
  public DBNode(final IO input) throws IOException {
    this(Parser.xmlParser(input));
  }

  /**
   * Constructor, specifying a parser reference.
   * @param parser parser
   * @throws IOException I/O exception
   */
  public DBNode(final Parser parser) throws IOException {
    this(MemBuilder.build("", parser));
  }

  /**
   * Sets the node type.
   * @param p pre value
   * @param k node kind
   */
  private void set(final int p, final int k) {
    type = type(k);
    parent = null;
    value = null;
    nsp = null;
    pre = p;
  }

  @Override
  public final Data data() {
    return data;
  }

  @Override
  public final byte[] string() {
    if(value == null) value = data.atom(pre);
    return value;
  }

  @Override
  public final long itr(final InputInfo ii) throws QueryException {
    if(type == NodeType.ELM) {
      final int as = data.attSize(pre, Data.ELEM);
      if(data.size(pre, Data.ELEM) - as == 1 && data.kind(pre + as) == Data.TEXT) {
        final long l = data.textItr(pre + as, true);
        if(l != Long.MIN_VALUE) return l;
      }
    } else if(type == NodeType.TXT || type == NodeType.ATT) {
      final long l = data.textItr(pre, type == NodeType.TXT);
      if(l != Long.MIN_VALUE) return l;
    }
    return Int.parse(data.atom(pre), ii);
  }

  @Override
  public final double dbl(final InputInfo ii) throws QueryException {
    if(type == NodeType.ELM) {
      final int as = data.attSize(pre, Data.ELEM);
      if(data.size(pre, Data.ELEM) - as == 1 && data.kind(pre + as) == Data.TEXT) {
        return data.textDbl(pre + as, true);
      }
    } else if(type == NodeType.TXT || type == NodeType.ATT) {
      return data.textDbl(pre, type == NodeType.TXT);
    }
    return Dbl.parse(data.atom(pre), ii);
  }

  @Override
  public final byte[] name() {
    return type == NodeType.ELM || type == NodeType.ATT || type == NodeType.PI ?
      data.name(pre, kind(nodeType())) : Token.EMPTY;
  }

  @Override
  public final QNm qname() {
    return type == NodeType.ELM || type == NodeType.ATT || type == NodeType.PI ?
      qname(new QNm()) : null;
  }

  @Override
  public final QNm qname(final QNm name) {
    // update the name and uri strings in the specified QName
    final byte[] nm = name();
    byte[] uri = Token.EMPTY;
    final boolean pref = Token.indexOf(nm, ':') != -1;
    if(pref || data.nspaces.size() != 0) {
      final int n = pref ? data.nspaces.uri(nm, pre, data) :
        data.uri(pre, data.kind(pre));
      final byte[] u = n > 0 ? data.nspaces.uri(n) : pref ? NSGlobal.uri(Token.prefix(nm)) : null;
      if(u != null) uri = u;
    }
    name.set(nm, uri);
    return name;
  }

  @Override
  public final Atts namespaces() {
    if(type == NodeType.ELM && nsp == null) nsp = data.ns(pre);
    return nsp;
  }

  @Override
  public final byte[] baseURI() {
    if(type == NodeType.DOC) {
      final byte[] base = data.text(pre, true);
      if(data.inMemory()) {
        final String bs = Token.string(base);
        if(bs.endsWith(IO.BASEXSUFFIX + IO.XMLSUFFIX)) return Token.EMPTY;
        final String dir = data.meta.original;
        return Token.token(dir.isEmpty() ? bs : IO.get(dir).merge(bs).url());
      }
      return new TokenBuilder(data.meta.name).add('/').add(base).finish();
    }
    final byte[] b = attribute(new QNm(BASE, XML_URI));
    return b != null ? b : Token.EMPTY;
  }

  @Override
  public final boolean is(final ANode node) {
    return node == this || node instanceof DBNode &&
        data == node.data() && pre == ((DBNode) node).pre;
  }

  @Override
  public final int diff(final ANode node) {
    // compare fragment with database node; specify fragment first to save time
    if(node instanceof FNode) return -diff(node, this);
    // same database instance: compare pre values
    if(data == node.data()) {
      final int p = ((DBNode) node).pre;
      return pre > p ? 1 : pre < p ? -1 : 0;
    }
    // check order via lowest common ancestor
    return diff(this, node);
  }

  @Override
  public final DBNode copy() {
    final DBNode n = new DBNode(data, pre, parent, nodeType());
    n.score = score;
    return n;
  }

  @Override
  public final Value copy(final QueryContext qc, final VarScope scp, final IntObjMap<Var> vs) {
    return copy();
  }

  @Override
  public final DBNode dbCopy(final MainOptions opts) {
    final MemData md = new MemData(opts);
    new DataBuilder(md).build(this);
    return new DBNode(md).parent(parent);
  }

  @Override
  public final DBNode deepCopy(final MainOptions options) {
    return dbCopy(options);
  }

  @Override
  public final DBNode finish() {
    return copy();
  }

  @Override
  public final ANode parent() {
    if(parent != null) return parent;
    final int p = data.parent(pre, data.kind(pre));
    if(p == -1) return null;

    final DBNode node = copy();
    node.set(p, data.kind(p));
    return node;
  }

  @Override
  protected final DBNode parent(final ANode p) {
    parent = p;
    return this;
  }

  @Override
  public final boolean hasChildren() {
    final int k = data.kind(pre);
    return data.attSize(pre, k) != data.size(pre, k);
  }

  @Override
  public final AxisIter ancestor() {
    return new AxisIter() {
      private final DBNode node = copy();
      int p = pre, k = data.kind(p);

      @Override
      public ANode next() {
        p = data.parent(p, k);
        if(p == -1) return null;
        k = data.kind(p);
        node.set(p, k);
        return node;
      }
    };
  }

  @Override
  public final AxisIter ancestorOrSelf() {
    return new AxisIter() {
      private final DBNode node = copy();
      int p = pre, k = data.kind(p);

      @Override
      public ANode next() {
        if(p == -1) return null;
        k = data.kind(p);
        node.set(p, k);
        p = data.parent(p, k);
        return node;
      }
    };
  }

  @Override
  public final AxisMoreIter attributes() {
    return new AxisMoreIter() {
      final DBNode node = copy();
      final int s = pre + data.attSize(pre, data.kind(pre));
      int p = pre + 1;

      @Override
      public boolean more() {
        return p != s;
      }

      @Override
      public DBNode next() {
        if(!more()) return null;
        node.set(p++, Data.ATTR);
        return node;
      }
    };
  }

  @Override
  public final AxisMoreIter children() {
    return new AxisMoreIter() {
      int k = data.kind(pre), p = pre + data.attSize(pre, k);
      final int s = pre + data.size(pre, k);
      final DBNode node = copy();

      @Override
      public boolean more() {
        return p < s;
      }

      @Override
      public ANode next() {
        if(!more()) return null;
        k = data.kind(p);
        node.set(p, k);
        p += data.size(p, k);
        return node;
      }
    };
  }

  @Override
  public final AxisIter descendant() {
    return new AxisIter() {
      int k = data.kind(pre), p = pre + data.attSize(pre, k);
      final int s = pre + data.size(pre, k);
      final DBNode node = copy();

      @Override
      public DBNode next() {
        if(p == s) return null;
        k = data.kind(p);
        node.set(p, k);
        p += data.attSize(p, k);
        return node;
      }
    };
  }

  @Override
  public final AxisIter descendantOrSelf() {
    return new AxisIter() {
      final DBNode node = copy();
      final int s = pre + data.size(pre, data.kind(pre));
      int p = pre;

      @Override
      public ANode next() {
        if(p == s) return null;
        final int k = data.kind(p);
        node.set(p, k);
        p += data.attSize(p, k);
        return node;
      }
    };
  }

  @Override
  public final AxisIter following() {
    return new AxisIter() {
      private final DBNode node = copy();
      final int s = data.meta.size;
      int k = data.kind(pre);
      int p = pre + data.size(pre, k);

      @Override
      public ANode next() {
        if(p == s) return null;
        k = data.kind(p);
        node.set(p, k);
        p += data.attSize(p, k);
        return node;
      }
    };
  }

  @Override
  public final AxisIter followingSibling() {
    return new AxisIter() {
      private final DBNode node = copy();
      int k = data.kind(pre);
      private final int pp = data.parent(pre, k);
      final int s = pp == -1 ? 0 : pp + data.size(pp, data.kind(pp));
      int p = pp == -1 ? 0 : pre + data.size(pre, k);

      @Override
      public ANode next() {
        if(p == s) return null;
        k = data.kind(p);
        node.set(p, k);
        p += data.size(p, k);
        return node;
      }
    };
  }

  @Override
  public final AxisIter parentIter() {
    return new AxisIter() {
      /** First call. */
      private boolean more;

      @Override
      public ANode next() {
        if(more) return null;
        more = true;
        return parent();
      }
    };
  }

  @Override
  public final boolean sameAs(final Expr cmp) {
    return cmp instanceof DBNode && data == ((DBNode) cmp).data &&
      pre == ((DBNode) cmp).pre;
  }

  @Override
  public final void plan(final FElem plan) {
    addPlan(plan, planElem(NAM, data.meta.name, PRE, pre));
  }

  @Override
  public final byte[] xdmInfo() {
    final ByteList bl = new ByteList().add(typeId().bytes());
    if(type == NodeType.DOC) bl.add(baseURI()).add(0);
    else if(type == NodeType.ATT) bl.add(qname().uri()).add(0);
    return bl.finish();
  }

  @Override
  public final ID typeId() {
    // check if a document has a single element as child
    ID t = type.id();
    if(type == NodeType.DOC) {
      final AxisMoreIter ai = children();
      if(ai.more() && ai.next().type == NodeType.ELM && !ai.more()) t = NodeType.DEL.id();
    }
    return t;
  }

  @Override
  public String toErrorString() {
    return toString(false);
  }

  @Override
  public String toString() {
    return toString(!data.inMemory());
  }

  /**
   * Returns a string representation of the sequence.
   * @param func display function representation
   * @return string
   */
  private String toString(final boolean func) {
    if(func) return _DB_OPEN_PRE.args(data.meta.name, pre);

    final TokenBuilder tb = new TokenBuilder(type.string()).add(' ');
    switch((NodeType) type) {
      case ATT:
      case PI:
        tb.add(name()).add(" {\"").add(Token.chop(string(), 32)).add("\"}");
        break;
      case ELM:
        tb.add(name()).add(" {...}");
        break;
      case DOC:
        tb.add("{\"").add(data.text(pre, true)).add("\"}");
        break;
      default:
        tb.add("{\"").add(Token.chop(string(), 32)).add("\"}");
        break;
    }
    return tb.toString();
  }
}
