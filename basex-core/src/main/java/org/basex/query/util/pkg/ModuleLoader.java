package org.basex.query.util.pkg;

import static org.basex.query.QueryError.*;
import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;

import java.net.*;
import java.util.*;

import org.basex.core.*;
import org.basex.io.*;
import org.basex.query.*;
import org.basex.query.util.pkg.Package.Component;
import org.basex.query.util.pkg.Package.Dependency;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Module loader.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class ModuleLoader {
  /** Default class loader. */
  private static final ClassLoader LOADER = Thread.currentThread().getContextClassLoader();

  /** Database context. */
  private final Context context;
  /** Cached URLs to be added to the class loader. */
  private final ArrayList<URL> urls = new ArrayList<>(0);
  /** Current class loader. */
  private ClassLoader loader = LOADER;

  /** Java modules. */
  private HashSet<Object> javaModules;

  /**
   * Constructor.
   * @param context database context
   */
  public ModuleLoader(final Context context) {
    this.context = context;
  }

  /**
   * Closes opened jar files, and calls close method of {@link QueryModule} instances
   * implementing {@link QueryResource}.
   */
  public void close() {
    if(loader instanceof JarLoader) ((JarLoader) loader).close();
    if(javaModules != null) {
      for(final Object jm : javaModules) {
        for(final Class<?> c : jm.getClass().getInterfaces()) {
          if(c != QueryResource.class) continue;
          Reflect.invoke(Reflect.method(QueryResource.class, "close"), jm);
        }
      }
    }
  }

  /**
   * Adds a package from the repository or a Java class.
   * @param uri module uri
   * @param ii input info
   * @param qp query parser
   * @return if the package has been found
   * @throws QueryException query exception
   */
  public boolean addImport(final byte[] uri, final InputInfo ii, final QueryParser qp)
      throws QueryException {

    // add EXPath package
    final TokenSet pkgs = context.repo.nsDict().get(uri);
    if(pkgs != null) {
      Version ver = null;
      byte[] nm = null;
      for(final byte[] name : pkgs) {
        final Version v = new Version(Package.version(name));
        if(ver == null || v.compareTo(ver) > 0) {
          ver = v;
          nm = name;
        }
      }
      if(nm != null) {
        addRepo(nm, new TokenSet(), new TokenSet(), ii, qp);
        return true;
      }
    }

    // search module in repository: rewrite URI to file path
    final boolean java = startsWith(uri, JAVAPREF);
    String uriPath = uri2path(string(java ? substring(uri, JAVAPREF.length) : uri));
    if(uriPath == null) return false;

    if(!java) {
      // no "java:" prefix: first try to import module as XQuery
      final String path = context.globalopts.get(GlobalOptions.REPOPATH) + uriPath;
      // check for any file with XQuery suffix
      for(final String suf : IO.XQSUFFIXES) {
        final IOFile file = new IOFile(path + suf);
        if(file.exists()) {
          qp.module(token(file.path()), uri);
          return true;
        }
      }
    }

    // try to load Java module
    uriPath = capitalize(uriPath);
    final String path = context.globalopts.get(GlobalOptions.REPOPATH) + uriPath;
    final IOFile file = new IOFile(path + IO.JARSUFFIX);
    if(file.exists()) addURL(file);

    // try to create Java class instance
    final String cp = camelCase(uriPath.replace('/', '.').substring(1));
    final Class<?> clz;
    try {
      clz = findClass(cp);
    } catch(final ClassNotFoundException ex) {
      if(java) throw WHICHCLASS_X.get(ii, ex.getMessage());
      return false;
    } catch(final Throwable th) {
      throw MODINITERR_X.get(ii, th);
    }

    // add new instance to module cache
    final Object jm = Reflect.get(clz);
    if(jm == null) throw INSTERR_X.get(ii, cp);

    if(javaModules == null) javaModules = new HashSet<>();
    javaModules.add(jm);
    return true;
  }

  /**
   * Returns a reference to the specified class.
   * @param name fully classified class name
   * @return found class or {@code null}
   * @throws Throwable any exception or error: {@link ClassNotFoundException},
   *   {@link LinkageError} or {@link ExceptionInInitializerError}.
   */
  public Class<?> findClass(final String name) throws Throwable {
    // add cached URLs to class loader
    final int us = urls.size();
    if(us != 0) {
      loader = new JarLoader(urls.toArray(new URL[us]), loader);
      urls.clear();
    }
    // no external classes added: use default class loader
    return loader == LOADER ? Reflect.forName(name) : Class.forName(name, true, loader);
  }

  /**
   * Returns an instance of the specified Java module class.
   * @param clz class to be found
   * @return instance or {@code null}
   */
  public Object findImport(final String clz) {
    // check if class was imported as Java module
    if(javaModules != null) {
      for(final Object jm : javaModules) {
        if(jm.getClass().getName().equals(clz)) return jm;
      }
    }
    return null;
  }

  // STATIC METHODS =====================================================================

  /**
   * <p>Converts a URI to a directory path. The conversion is inspired by Zorba's
   * URI transformation
   * (http://www.zorba-xquery.com/html/documentation/2.2.0/zorba/uriresolvers):</p>
   * <ul>
   * <li>In the URI authority, the order of all substrings separated by dots is reversed.</li>
   * <li>Dots in the authority and the path are replaced by slashes.
   *     If no path exists, a single slash is appended.</li>
   * <li>If the resulting string ends with a slash, "index" is appended.</li>
   * <li>{@code null} is returned if the URI has an invalid syntax.</li>
   * </ul>
   * @param uri namespace uri
   * @return path or {@code null}
   */
  public static String uri2path(final String uri) {
    try {
      final URI u = new URI(uri);
      final TokenBuilder tb = new TokenBuilder();
      final String auth = u.getAuthority();
      if(auth != null) {
        // reverse authority, replace dots by slashes
        final String[] comp = auth.split("\\.");
        for(int c = comp.length - 1; c >= 0; c--) tb.add('/').add(comp[c]);
      } else {
        tb.add('/');
      }

      // add remaining path
      String path = u.getPath();
      if(path == null) return null;

      path = path.replace('.', '/');
      // add slash or path
      tb.add(path.isEmpty() ? "/" : path);
      final String pth = tb.toString();
      // add "index" string
      return pth.endsWith("/") ? pth + "index" : pth;
    } catch(final URISyntaxException ex) {
      Util.debug(ex);
      return null;
    }
  }

  /**
   * Capitalizes the last path segment.
   * @param path input path
   * @return capitalized path
   */
  public static String capitalize(final String path) {
    final int i = path.lastIndexOf('/');
    return i == -1 || i + 1 >= path.length() ? path : path.substring(0, i + 1) +
      Character.toUpperCase(path.charAt(i + 1)) + path.substring(i + 2);
  }

  // PRIVATE METHODS ====================================================================

  /**
   * Adds a package from the package repository.
   * @param name package name
   * @param toLoad list with packages to be loaded
   * @param loaded already loaded packages
   * @param ii input info
   * @param qp query parser
   * @throws QueryException query exception
   */
  private void addRepo(final byte[] name, final TokenSet toLoad, final TokenSet loaded,
      final InputInfo ii, final QueryParser qp) throws QueryException {

    // return if package is already loaded
    if(loaded.contains(name)) return;

    // find package in package dictionary
    final byte[] pDir = context.repo.pkgDict().get(name);
    if(pDir == null) throw BXRE_NOTINST_X.get(ii, name);
    final IOFile pkgDir = context.repo.path(string(pDir));

    // parse package descriptor
    final IO pkgDesc = new IOFile(pkgDir, PkgText.DESCRIPTOR);
    if(!pkgDesc.exists()) Util.debug(PkgText.MISSDESC, string(name));

    final Package pkg = new PkgParser(ii).parse(pkgDesc);
    // check if package contains a jar descriptor
    final IOFile jarDesc = new IOFile(pkgDir, PkgText.JARDESC);
    // add jars to classpath
    if(jarDesc.exists()) addJar(jarDesc, pkgDir, string(pkg.abbrev), ii);

    // package has dependencies -> they have to be loaded first => put package
    // in list with packages to be loaded
    if(!pkg.dep.isEmpty()) toLoad.add(name);
    for(final Dependency d : pkg.dep) {
      if(d.pkg != null) {
        // we consider only package dependencies here
        final byte[] depPkg = new PkgValidator(context.repo, ii).depPkg(d);
        if(depPkg == null) throw BXRE_NOTINST_X.get(ii, string(d.pkg));
        if(toLoad.contains(depPkg)) throw CIRCMODULE.get(ii);
        addRepo(depPkg, toLoad, loaded, ii, qp);
      }
    }
    for(final Component comp : pkg.comps) {
      final String p = new IOFile(new IOFile(pkgDir, string(pkg.abbrev)), string(comp.file)).path();
      qp.module(token(p), comp.uri);
    }
    if(toLoad.contains(name)) toLoad.delete(name);
    loaded.add(name);
  }

  /**
   * Adds the jar files registered in jarDesc.
   * @param jarDesc jar descriptor
   * @param pkgDir package directory
   * @param modDir module directory
   * @param ii input info
   * @throws QueryException query exception
   */
  private void addJar(final IOFile jarDesc, final IOFile pkgDir, final String modDir,
      final InputInfo ii) throws QueryException {

    // add new URLs
    final JarDesc desc = new JarParser(ii).parse(jarDesc);
    for(final byte[] u : desc.jars) addURL(new IOFile(new IOFile(pkgDir, modDir), string(u)));
  }

  /**
   * Adds a URL to the cache.
   * @param jar jar file to be added
   */
  private void addURL(final IOFile jar) {
    try {
      urls.add(new URL(jar.url()));
    } catch(final MalformedURLException ex) {
      Util.errln(ex);
    }
  }
}
