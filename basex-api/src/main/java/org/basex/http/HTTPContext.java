package org.basex.http;

import static javax.servlet.http.HttpServletResponse.*;
import static org.basex.data.DataText.*;
import static org.basex.http.HTTPText.*;
import static org.basex.io.MimeTypes.*;
import static org.basex.util.Token.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.basex.*;
import org.basex.build.*;
import org.basex.build.JsonOptions.JsonFormat;
import org.basex.core.*;
import org.basex.io.*;
import org.basex.io.out.*;
import org.basex.io.serial.*;
import org.basex.server.*;
import org.basex.util.*;
import org.basex.util.options.*;

/**
 * Bundles context-based information on a single HTTP operation.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class HTTPContext {
  /** Quality factor pattern. */
  private static final Pattern QF = Pattern.compile("^(.*);\\s*q\\s*=(.*)$");

  /** Servlet request. */
  public final HttpServletRequest req;
  /** Servlet response. */
  public final HttpServletResponse res;
  /** Request method. */
  public final String method;
  /** Request method. */
  public final HTTPParams params;

  /** Serialization parameters. */
  private SerializerOptions sopts;
  /** Result wrapping. */
  public boolean wrapping;
  /** User name. */
  public String user;
  /** Password. */
  public String pass;

  /** Global static database context. */
  private static Context context;
  /** Initialization flag. */
  private static boolean init;
  /** Initialized failed. */
  private static IOException exception;

  /** Performance. */
  private final Performance perf = new Performance();
  /** Path, starting with a slash. */
  private final String path;

  /**
   * Constructor.
   * @param req request
   * @param res response
   * @param servlet calling servlet instance
   * @throws IOException I/O exception
   */
  public HTTPContext(final HttpServletRequest req, final HttpServletResponse res,
      final BaseXServlet servlet) throws IOException {

    this.req = req;
    this.res = res;
    params = new HTTPParams(this);

    method = req.getMethod();

    final StringBuilder uri = new StringBuilder(req.getRequestURL());
    final String qs = req.getQueryString();
    if(qs != null) uri.append('?').append(qs);
    log('[' + method + "] " + uri, null);

    // set UTF8 as default encoding (can be overwritten)
    res.setCharacterEncoding(UTF8);
    path = decode(normalize(req.getPathInfo()));

    // adopt servlet-specific credentials or use global ones
    final GlobalOptions mprop = context().globalopts;
    user = servlet.user != null ? servlet.user : mprop.get(GlobalOptions.USER);
    pass = servlet.pass != null ? servlet.pass : mprop.get(GlobalOptions.PASSWORD);

    // overwrite credentials with session-specific data
    final String auth = req.getHeader(AUTHORIZATION);
    if(auth != null) {
      final String[] values = auth.split(" ");
      if(values[0].equals(BASIC)) {
        final String[] cred = org.basex.util.Base64.decode(values[1]).split(":", 2);
        if(cred.length != 2) throw new LoginException(NOPASSWD);
        user = cred[0];
        pass = cred[1];
      } else {
        throw new LoginException(WHICHAUTH, values[0]);
      }
    }
  }

  /**
   * Returns the content type of a request (without an optional encoding).
   * @return content type
   */
  public String contentType() {
    final String ct = req.getContentType();
    return ct != null ? ct.replaceFirst(";.*", "") : null;
  }

  /**
   * Returns the content type extension of a request (without an optional encoding).
   * @return content type
   */
  public String contentTypeExt() {
    final String ct = req.getContentType();
    return ct != null ? ct.replaceFirst("^.*?;\\s*", "") : null;
  }

  /**
   * Initializes the output. Sets the expected encoding and content type.
   */
  public void initResponse() {
    // set content type and encoding
    final SerializerOptions opts = sopts();
    final String enc = opts.get(SerializerOptions.ENCODING);
    res.setCharacterEncoding(enc);
    final String ct = mediaType(opts);
    res.setContentType(new TokenBuilder(ct).add(CHARSET).add(enc).toString());
  }

  /**
   * Returns the media type defined in the specified serialization parameters.
   * @param sopts serialization parameters
   * @return media type
   */
  public static String mediaType(final SerializerOptions sopts) {
    // set content type
    final String type = sopts.get(SerializerOptions.MEDIA_TYPE);
    if(!type.isEmpty()) return type;

    // determine content type dependent on output method
    final SerialMethod sm = sopts.get(SerializerOptions.METHOD);
    if(sm == SerialMethod.RAW) return APP_OCTET;
    if(sm == SerialMethod.XML) return APP_XML;
    if(sm == SerialMethod.XHTML || sm == SerialMethod.HTML) return TEXT_HTML;
    if(sm == SerialMethod.JSON) {
      final JsonSerialOptions jprop = sopts.get(SerializerOptions.JSON);
      return jprop.get(JsonOptions.FORMAT) == JsonFormat.JSONML ? APP_JSONML : APP_JSON;
    }
    return TEXT_PLAIN;
  }

  /**
   * Returns the URL path.
   * @return path path
   */
  public String path() {
    return path;
  }

  /**
   * Returns the database path (i.e., all path entries except for the first).
   * @return database path
   */
  public String dbpath() {
    final int s = path.indexOf('/', 1);
    return s == -1 ? "" : path.substring(s + 1);
  }

  /**
   * Returns the addressed database (i.e., the first path entry).
   * @return database, or {@code null} if the root directory was specified.
   */
  public String db() {
    final int s = path.indexOf('/', 1);
    return path.substring(1, s == -1 ? path.length() : s);
  }

  /**
   * Returns all accepted media types.
   * @return accepted media types
   */
  public HTTPAccept[] accepts() {
    final String accept = req.getHeader(ACCEPT);
    if(accept == null) return new HTTPAccept[0];

    final ArrayList<HTTPAccept> list = new ArrayList<>();
    for(final String produce : accept.split("\\s*,\\s*")) {
      final HTTPAccept acc = new HTTPAccept();
      // check if quality factor was specified
      final Matcher m = QF.matcher(produce);
      if(m.find()) {
        acc.type = m.group(1);
        acc.qf = Token.toDouble(Token.token(m.group(2)));
      } else {
        acc.type = produce;
      }
      // only accept valid double values
      if(acc.qf > 0 && acc.qf <= 1) list.add(acc);
    }
    return list.toArray(new HTTPAccept[list.size()]);
  }

  /**
   * Sets a status and sends an info message.
   * @param code status code
   * @param message info message
   * @param error treat as error (use web server standard output)
   * @throws IOException I/O exception
   */
  public void status(final int code, final String message, final boolean error) throws IOException {
    try {
      log(message, code);
      res.resetBuffer();
      if(code == SC_UNAUTHORIZED) res.setHeader(WWW_AUTHENTICATE, BASIC);

      if(error && code >= SC_BAD_REQUEST) {
        res.sendError(code, message);
      } else {
        res.setStatus(code);
        if(message != null) {
          res.setContentType(TEXT_PLAIN);
          final ArrayOutput ao = new ArrayOutput();
          ao.write(token(message));
          res.getOutputStream().write(ao.normalize().finish());
        }
      }
    } catch(final IllegalStateException ex) {
      log(Util.message(ex), SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Updates the credentials.
   * @param u user
   * @param p password
   */
  public void credentials(final String u, final String p) {
    user = u;
    pass = p;
  }

  /**
   * Authenticate the user and returns a new client {@link Context} instance.
   * @return client context
   * @throws LoginException login exception
   */
  public Context authenticate() throws LoginException {
    final byte[] address = token(req.getRemoteAddr());
    try {
      if(user == null || user.isEmpty() || pass == null || pass.isEmpty())
        throw new LoginException(NOPASSWD);
      final Context ctx = new Context(context(), null);
      ctx.user = ctx.users.get(user);
      if(ctx.user == null || !ctx.user.password.equals(md5(pass))) throw new LoginException();

      context.blocker.remove(address);
      return ctx;
    } catch(final LoginException ex) {
      // delay users with wrong passwords
      for(int d = context.blocker.delay(address); d > 0; d--) Performance.sleep(100);
      throw ex;
    }
  }

  /**
   * Returns the database context.
   * @return context
   */
  public Context context() {
    return context;
  }

  /**
   * Returns an exception that may have been caught by the initialization of the database server.
   * @return exception
   */
  public static IOException exception() {
    return exception;
  }

  /**
   * Assigns serialization parameters.
   * @param opts serialization parameters.
   */
  public void sopts(final SerializerOptions opts) {
    sopts = opts;
  }

  /**
   * Returns the serialization parameters.
   * @return serialization parameters.
   */
  public SerializerOptions sopts() {
    if(sopts == null) sopts = new SerializerOptions();
    return sopts;
  }

  /**
   * Writes a log message.
   * @param info message info
   * @param type message type (true/false/null: OK, ERROR, REQUEST, Error Code)
   */
  public void log(final String info, final Object type) {
    // add evaluation time if any type is specified
    context.log.write(type != null ?
      new Object[] { address(), context.user.name, type, info, perf } :
      new Object[] { address(), context.user.name, null, info });
  }

  // STATIC METHODS =====================================================================

  /**
   * Initializes the HTTP context.
   * @return context;
   */
  public static synchronized Context init() {
    if(context == null) context = new Context();
    return context;
  }

  /**
   * Initializes the database context, based on the initial servlet context.
   * Parses all context parameters and passes them on to the database context.
   * @param sc servlet context
   * @throws IOException I/O exception
   */
  public static synchronized void init(final ServletContext sc) throws IOException {
    // check if HTTP context has already been initialized
    if(init) return;
    init = true;

    // set web application path as home directory and HTTPPATH
    final String webapp = sc.getRealPath("/");
    Options.setSystem(Prop.PATH, webapp);
    Options.setSystem(GlobalOptions.WEBPATH, webapp);

    // bind all parameters that start with "org.basex." to system properties
    final Enumeration<String> en = sc.getInitParameterNames();
    while(en.hasMoreElements()) {
      final String key = en.nextElement();
      if(!key.startsWith(Prop.DBPREFIX)) continue;

      String val = sc.getInitParameter(key);
      if(key.endsWith("path") && !new File(val).isAbsolute()) {
        // prefix relative path with absolute servlet path
        Util.debug(key.toUpperCase(Locale.ENGLISH) + ": " + val);
        val = new IOFile(webapp, val).path();
      }
      Options.setSystem(key, val);
    }

    // create context, update options
    if(context == null) {
      context = new Context(false);
    } else {
      context.globalopts.setSystem();
      context.options.setSystem();
    }

    // start server instance
    if(!context.globalopts.get(GlobalOptions.HTTPLOCAL)) {
      try {
        new BaseXServer(context);
      } catch(final IOException ex) {
        exception = ex;
        throw ex;
      }
    }
  }

  /**
   * Normalizes the path information.
   * @param path path, or {@code null}
   * @return normalized path
   */
  public static String normalize(final String path) {
    final TokenBuilder tmp = new TokenBuilder();
    if(path != null) {
      final TokenBuilder tb = new TokenBuilder();
      final int pl = path.length();
      for(int p = 0; p < pl; p++) {
        final char ch = path.charAt(p);
        if(ch == '/') {
          if(tb.isEmpty()) continue;
          tmp.add('/').add(tb.toArray());
          tb.reset();
        } else {
          tb.add(ch);
        }
      }
      if(!tb.isEmpty()) tmp.add('/').add(tb.finish());
    }
    if(tmp.isEmpty()) tmp.add('/');
    return tmp.toString();
  }

  /**
   * Decodes the specified path segments.
   * @param segments strings to be decoded
   * @return argument
   * @throws IllegalArgumentException invalid path segments
   */
  public static String decode(final String segments) {
    try {
      return URLDecoder.decode(segments, Prop.ENCODING);
    } catch(final UnsupportedEncodingException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  // PRIVATE METHODS ====================================================================

  /**
   * Returns a string with the remote user address.
   * @return user address
   */
  private String address() {
    return req.getRemoteAddr() + ':' + req.getRemotePort();
  }
}
