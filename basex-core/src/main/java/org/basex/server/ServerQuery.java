package org.basex.server;

import static org.basex.core.Text.*;
import static org.basex.io.serial.SerializerOptions.*;
import static org.basex.query.util.Err.*;

import java.io.*;

import org.basex.api.client.*;
import org.basex.core.*;
import org.basex.io.out.*;
import org.basex.io.serial.*;
import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.util.*;

/**
 * Server-side query, generated by {@link LocalQuery} and {@link ClientQuery}.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class ServerQuery extends Proc {
  /** Performance. */
  private final Performance perf = new Performance();
  /** Query string. */
  private final String query;
  /** Database context. */
  private final Context ctx;

  /** Query processor. */
  private QueryProcessor qp;
  /** Serialization parameters. */
  private SerializerOptions parameters;
  /** Parsing flag. */
  private boolean parsed;
  /** Query info. */
  private String info = "";

  /**
   * Constructor.
   * @param query query string
   * @param ctx database context
   */
  public ServerQuery(final String query, final Context ctx) {
    this.query = query;
    this.ctx = ctx;
  }

  /**
   * Binds a global variable.
   * @param name name of variable
   * @param value value to be bound
   * @param type type
   * @throws IOException query exception
   */
  public void bind(final String name, final Object value, final String type) throws IOException {
    try {
      init().bind(name, value, type);
    } catch(final QueryException ex) {
      Util.stack(ex);
      throw new BaseXException(ex);
    }
  }

  /**
   * Binds the context value.
   * @param value value to be bound
   * @param type type
   * @throws IOException query exception
   */
  public void context(final Object value, final String type) throws IOException {
    try {
      init().context(value, type);
    } catch(final QueryException ex) {
      throw new BaseXException(ex);
    }
  }

  /**
   * Returns the query info.
   * @return query info
   */
  public String info() {
    return info;
  }

  /**
   * Returns the serialization parameters.
   * @return serialization parameters
   * @throws IOException I/O Exception
   */
  public String parameters() throws IOException {
    if(parameters == null) parameters = parse().qc.serParams();
    return parameters.toString();
  }

  /**
   * Returns {@code true} if the query may perform updates.
   * @return updating flag
   * @throws IOException I/O Exception
   */
  public boolean updating() throws IOException {
    return parse().updating;
  }

  /**
   * Executes the query.
   * @param iter iterative evaluation
   * @param out output stream
   * @param encode encode stream
   * @param full return full type information
   * @throws IOException I/O Exception
   */
  public void execute(final boolean iter, final OutputStream out, final boolean encode,
      final boolean full) throws IOException {

    try {
      // parses the query and registers the process
      ctx.register(parse());

      // create serializer
      qp.compile();
      final QueryInfo qi = qp.qc.info;
      qi.compiling = perf.time();
      final Iter ir = qp.iter();
      qi.evaluating = perf.time();
      parameters();
      final boolean wrap = !parameters.get(WRAP_PREFIX).isEmpty();

      // iterate through results
      final PrintOutput po = PrintOutput.get(encode ? new EncodingOutput(out) : out);
      if(iter && wrap) {
        final FElem elem = new FElem("");
        po.write(full ? elem.xdmInfo() : elem.typeId().bytes());
      }

      final Serializer ser = Serializer.get(po, full ? null : parameters);
      int c = 0;
      for(Item it; (it = ir.next()) != null;) {
        if(iter && !wrap) {
          po.write(full ? it.xdmInfo() : it.typeId().bytes());
          ser.reset();
          ser.serialize(it, full, true);
          po.flush();
          out.write(0);
        } else {
          ser.serialize(it, full, false);
        }
        c++;
      }
      ser.close();
      if(iter && wrap) out.write(0);
      qi.serializing = perf.time();

      // generate query info
      info = qi.toString(qp, po.size(), c, ctx.options.get(MainOptions.QUERYINFO));

    } catch(final QueryException ex) {
      throw new BaseXException(ex);
    } catch(final StackOverflowError ex) {
      Util.debug(ex);
      throw new BaseXException(BASX_STACKOVERFLOW.desc);
    } catch(final ProcException ex) {
      throw new BaseXException(TIMEOUT_EXCEEDED);
    } finally {
      // close processor and unregisters the process
      if(qp != null) {
        qp.close();
        if(parsed) {
          ctx.unregister(qp);
          parsed = false;
        }
        qp = null;
      }
    }
  }

  /**
   * Initializes the query.
   * @return query processor
   * @throws IOException I/O Exception
   */
  private QueryProcessor parse() throws IOException {
    if(!parsed) {
      try {
        perf.time();
        init().parse();
        qp.qc.info.parsing = perf.time();
        parsed = true;
      } catch(final QueryException ex) {
        throw new BaseXException(ex);
      }
    }
    return qp;
  }

  /**
   * Returns an instance of the query processor.
   * @return query processor
   */
  private QueryProcessor init() {
    if(parsed || qp == null) {
      qp = new QueryProcessor(query, ctx);
      parsed = false;
    }
    return qp;
  }
}
