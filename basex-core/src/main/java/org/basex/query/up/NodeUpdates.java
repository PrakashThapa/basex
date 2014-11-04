package org.basex.query.up;

import static org.basex.query.up.primitives.UpdateType.*;

import java.util.*;

import org.basex.query.*;
import org.basex.query.up.primitives.*;

/**
 * This container holds all update primitives for a specific database node.
 * It is identified by its target node's PRE value and data reference.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Lukas Kircher
 */
final class NodeUpdates {
  /** Container for update primitives. */
  List<NodeUpdate> updates = new ArrayList<>(1);

  /**
   * Adds an update to this container.
   * @param update node update
   * @throws QueryException query exception
   */
  void add(final NodeUpdate update) throws QueryException {
    // Primitives can be merged eventually ...
    final int index = indexOf(update.type);
    if(index == -1) {
      updates.add(update);
    } else {
      final NodeUpdate oldUpdate = updates.get(index);
      // if an insertInto is part of a substitute of a replaceElementContent,
      // we cannot merge them, as this would insert unwanted nodes.
      // if the new primitive substitutes, then replace the old one with the new one
      if(oldUpdate instanceof InsertInto && update instanceof ReplaceContent) {
        updates.set(index, update);
      } else {
        // all other primitives can be merged regardless
        oldUpdate.merge(update);
      }
    }
  }

  /**
   * Finds the update primitive with the given type. In case there is no
   * primitive of the given type, null is returned.
   * @param type update type
   * @return primitive of type t, null if not found
   */
  private NodeUpdate find(final UpdateType type) {
    for(final NodeUpdate p : updates) {
      if(p.type == type) return p;
    }
    return null;
  }

  /**
   * Finds the update primitive with the given {@link UpdateType} and returns its index.
   * @param t PrimitiveType
   * @return index of primitive with given type or -1 if not found
   */
  private int indexOf(final UpdateType t) {
    final int us = updates.size();
    for(int u = 0; u < us; u++) {
      if(updates.get(u).type == t) return u;
    }
    return -1;
  }

  /**
   * Prepares the updates. Makes sure that ...
   * - replaceElementContent has the desired effect.
   * - a replace and a delete on a target T result in replacing the node.
   * @return list with update primitives
   * This method can only be once, as the internal update list will eventually be removed.
   */
  List<NodeUpdate> finish() {
    List<NodeUpdate> primnew = new ArrayList<>();

    /* Check if target node T is deleted and remove superfluous primitives. */
    final DeleteNode del = (DeleteNode) find(DELETENODE);
    /* If T is deleted due to a substitution of a replaceElementContent
     * expression all other primitives operating on this node must be removed. According
     * to the XQUF, a target T of a replaceElementContent contains, after the end of the
     * snapshot, either a text node as a single child or no child node at all. */
    if(del != null && del.rec) {
      primnew.add(del);
      updates = null;
      return primnew;
    }

    // If T is replaced, all primitives other than InsertBefore and InsertAfter can be
    // removed.
    final NodeUpdate replace = find(REPLACENODE);
    if(replace != null) {
      for(final NodeUpdate p : updates) {
        if(p.type == REPLACENODE || p.type == INSERTBEFORE || p.type == INSERTAFTER)
          primnew.add(p);
      }
      updates = null;
      return primnew;
    }

    /* If the target T is affected by a replaceElementContent expression, all
     * primitives that insert any nodes into T have to be removed from the list.
     * An InsertInto which is part of the substitution of the replaceElementContent
     * expression forms the only exception. */
    final ReplaceValue rec = (ReplaceValue) find(REPLACEVALUE);
    if(rec != null && rec.rec) {
      for(final NodeUpdate p : updates) {
        /* Add only InsertIntos that are part of the substitution and make sure no
         * other primitive is added, that adds nodes to the child axis of T. */
        if(p.type != INSERTINTOFIRST && p.type != INSERTINTO && p.type != INSERTINTOLAST
            || p.type == INSERTINTO && p instanceof ReplaceContent)
          primnew.add(p);
      }
      updates = null;
      return primnew;
    }

    // otherwise, return old list
    primnew = updates;
    updates = null;
    return primnew;
  }
}
