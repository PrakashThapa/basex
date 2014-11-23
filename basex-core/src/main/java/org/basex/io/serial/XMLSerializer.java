package org.basex.io.serial;

import static org.basex.data.DataText.*;
import static org.basex.query.QueryError.*;

import java.io.*;

import org.basex.data.*;
import org.basex.query.*;
import org.basex.query.value.item.*;

/**
 * This class serializes data as XML.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public class XMLSerializer extends OutputSerializer {
  /** Root elements. */
  private boolean root;

  /**
   * Constructor, specifying serialization options.
   * @param os output stream reference
   * @param sopts serialization parameters
   * @throws IOException I/O exception
   */
  XMLSerializer(final OutputStream os, final SerializerOptions sopts) throws IOException {
    super(os, sopts, V10, V11);
  }

  @Override
  protected void startOpen(final byte[] name) throws IOException {
    if(elems.isEmpty()) {
      if(root) check();
      root = true;
    }
    super.startOpen(name);
  }

  @Override
  protected void text(final byte[] value, final FTPos ftp) throws IOException {
    if(elems.isEmpty()) check();
    super.text(value, ftp);
  }

  @Override
  protected void atomic(final Item it, final boolean iter) throws IOException {
    if(elems.isEmpty()) check();
    super.atomic(it, iter);
  }

  /**
   * Checks if document serialization is valid.
   * @throws QueryIOException query I/O exception
   */
  private void check() throws QueryIOException {
    if(!saomit) throw SERSA.getIO();
    if(docsys != null) throw SERDT.getIO();
  }
}
