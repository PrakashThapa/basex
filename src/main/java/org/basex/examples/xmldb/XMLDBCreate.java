package org.basex.examples.xmldb;

import org.basex.api.xmldb.BXCollection;
import org.xmldb.api.base.*;
import org.xmldb.api.*;

/**
 * This class serves as an example for creating a database with the XML:DB API.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author BaseX Team
 */
public final class XMLDBCreate {
  /** Database driver. */
  public static final String DRIVER = "org.basex.api.xmldb.BXDatabase";
  /** Name of the collection. */
  public static final String COLL = "XMLDBCollection";

  /** Private constructor. */
  private XMLDBCreate() { }

  /**
   * Main method of the example class.
   * @param args (ignored) command-line arguments
   * @throws Exception exception
   */
  public static void main(final String[] args) throws Exception {
    try {
      // Register the database.
      Class<?> c = Class.forName(DRIVER);
      Database db = (Database) c.newInstance();
      DatabaseManager.registerDatabase(db);

      // Create a new collection
      BXCollection coll = new BXCollection(COLL, false);

      // Close the connection
      coll.close();

      // Open an existing collection
      coll = new BXCollection(COLL, true);

      // Close the connection
      coll.close();

    } catch(final XMLDBException ex) {
      // Handle exceptions.
      System.err.println("XML:DB Exception occured " + ex.errorCode);
    }
  }
}
