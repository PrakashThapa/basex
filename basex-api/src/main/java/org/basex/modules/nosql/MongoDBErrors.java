package org.basex.modules.nosql;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * This module contains static error functions for the Mongodb module.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Prakash Thapa
 */
public final class MongoDBErrors {
  /** Error namespace. */
  private static final byte[] NS = QueryText.EXPERROR_URI;
  /** Namespace and error code prefix. */
  private static final String PREFIX =
      new TokenBuilder(QueryText.EXPERROR_URI).add(":mongo").toString();

  /** Private constructor, preventing instantiation. */
  private MongoDBErrors() { }

  /**
   * MONGODB0001: General Exceptions.
   * @param e error object
   * @return query exception
   */
  public static QueryException generalExceptionError(final Object e) {
      return thrw(1, "%", e);
  }
  /**
   * MONGODB0002: JSON format error.
   * @param e error object
   * @return query exception
   */
  public static QueryException jsonFormatError(final Object e) {
      return thrw(2, "Invalid JSON syntax: '%'", e);
  }
  /**
   * MONGODB0003: Incorrect username or password.
   * @return query exception
   */
  public static QueryException unauthorised() {
    return thrw(3, "Invalid username or password");
  }
  /**
   * MONGODB0004: Connection error.
   * @param e error object
   * @return query exception
   */
  public static QueryException mongoExceptionError(final Object e) {
    return thrw(4, "%", e);
  }
  /**
   * MONGODB0005: Mongodb handler don't exists.
   * * @param mongoClient supplied mongodb Client.
   * @return query exception
   */
  public static QueryException mongoClientError(final Object mongoClient) {
    return thrw(5, "Unknown MongoDB handler: '%'", mongoClient);
  }
  /**
   * MONGODB0006: Mongodb DB handler don't exists.
   * * @param db MongoDB DB object handler
   * @return query exception
   */
  public static QueryException mongoDBError(final Object db) {
    return thrw(6, "Invalid database identifier: '%'", db);
  }
  /**
   * take two parameters.
   * @param msg Message
   * @param key key
   * @return QueryException
   */
  public static QueryException mongoMessageOneKey(final String msg,
          final Object key) {
    return thrw(7, msg, key);
  }
  /**
   * MONGODB0008: Query parameter does not exists in findAndModify().
   * @return query exception
   */
  public static QueryException findAndModifyQuery() {
    return thrw(8, "'query' parameter cannot be empty in findAndModify()");
  }
  /**
   * MONGODB0009: Query parameter does not exists in findAndModify().
   * @return query exception
   */
  public static QueryException findAndModifyUpdate() {
    return thrw(9, "'update' parameter cannot be empty in findAndModify()");
  }
  /**
   * MONGODB0010: Connection NullExceptions().
   * @return query exception
   */
  public static QueryException connectionNullException() {
    return thrw(10, "Parameters are not defined " +
        "well like: {'url':'mongodb://user:pass@127.0.0.1:27017/basex'}");
  }

  /**
   * Returns a query exception.
   * @param code code
   * @param msg message
   * @param ext extension
   * @return query exception
   */
  private static QueryException thrw(final int code, final String msg,
      final Object... ext) {
    return new QueryException(null, qname(code), msg, ext);
  }

  /**
   * Creates an error QName for the specified code.
   * @param code code
   * @return query exception
   */
  public static QNm qname(final int code) {
    return new QNm(String.format("%s:MONGO%04d", PREFIX, code), NS);
  }
}
