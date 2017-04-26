/*
 * Copyright (C) 2017-2017 DataStax Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.api.core;

import com.datastax.oss.driver.internal.core.util.Strings;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * The identifier of CQL element (keyspace, table, column, etc).
 *
 * <p>It has two representations:
 *
 * <ul>
 *   <li>the "CQL" form, which is how you would type the identifier in a CQL query. It is
 *       case-insensitive unless enclosed in double quotation marks; in addition, identifiers that
 *       contain special characters (anything other than alphanumeric and underscore), or match CQL
 *       keywords, must be double-quoted (with inner double quotes escaped as {@code ""}).
 *   <li>the "internal" form, which is how the name is stored in Cassandra system tables. It is
 *       lower-case for case-sensitive identifiers, and in the exact case for case-sensitive
 *       identifiers.
 * </ul>
 *
 * Examples:
 *
 * <table>
 *   <tr><th>Create statement</th><th>Case-sensitive?</th><th>CQL id</th><th>Internal id</th></tr>
 *   <tr><td>CREATE TABLE t(foo int PRIMARY KEY)</td><td>No</td><td>foo</td><td>foo</td></tr>
 *   <tr><td>CREATE TABLE t(Foo int PRIMARY KEY)</td><td>No</td><td>foo</td><td>foo</td></tr>
 *   <tr><td>CREATE TABLE t("Foo" int PRIMARY KEY)</td><td>Yes</td><td>"Foo"</td><td>Foo</td></tr>
 *   <tr><td>CREATE TABLE t("foo bar" int PRIMARY KEY)</td><td>Yes</td><td>"foo bar"</td><td>foo bar</td></tr>
 *   <tr><td>CREATE TABLE t("foo""bar" int PRIMARY KEY)</td><td>Yes</td><td>"foo""bar"</td><td>foo"bar</td></tr>
 *   <tr><td>CREATE TABLE t("create" int PRIMARY KEY)</td><td>Yes (reserved keyword)</td><td>"create"</td><td>create</td></tr>
 * </table>
 *
 * This class provides a common representation and avoids any ambiguity about which form the
 * identifier is in. Driver clients will generally want to create instances from the CQL form with
 * {@link #fromCql(String)}.
 *
 * <p>There is no internal caching; if you reuse the same identifiers often,
 */
public class CqlIdentifier implements Serializable {

  private static final long serialVersionUID = 1;

  // IMPLEMENTATION NOTES:
  // This is used internally, and for all API methods where the overhead of requiring the client to
  // create an instance is acceptable (metadata, statement.getKeyspace, etc.)
  // One exception is named getters, where we keep raw strings with the 3.x rules.

  /** Creates an identifier from its {@link CqlIdentifier CQL form}. */
  public static CqlIdentifier fromCql(String cql) {
    Preconditions.checkNotNull(cql, "cql must not be null");
    final String internal;
    if (Strings.isDoubleQuoted(cql)) {
      internal = Strings.unDoubleQuote(cql);
    } else {
      internal = cql.toLowerCase();
      Preconditions.checkArgument(
          !Strings.needsDoubleQuotes(internal), "Invalid CQL form [%s]: needs double quotes", cql);
    }
    return fromInternal(internal);
  }

  /** Creates an identifier from its {@link CqlIdentifier internal form}. */
  public static CqlIdentifier fromInternal(String internal) {
    Preconditions.checkNotNull(internal, "internal must not be null");
    return new CqlIdentifier(internal);
  }

  /** @serial */
  private final String internal;

  private CqlIdentifier(String internal) {
    this.internal = internal;
  }

  /**
   * Returns the identifier in the "internal" format.
   *
   * @return the identifier in its exact case, unquoted.
   */
  public String asInternal() {
    return this.internal;
  }

  /**
   * Returns the identifier in a format appropriate for concatenation in a CQL query.
   *
   * @return the double-quoted form, always. Note that this is not the most compact representation
   *     of case-insensitive identifiers (see {@link #asPrettyCql()}.
   */
  public String asCql() {
    return Strings.doubleQuote(internal);
  }

  /**
   * Returns the identifier in a format appropriate for concatenation in a CQL query, using the
   * simplest possible representation.
   *
   * @return if the identifier is case-insensitive, an unquoted, lower-case string. Otherwise, the
   *     double-quoted form.
   */
  public String asPrettyCql() {
    return Strings.needsDoubleQuotes(internal) ? Strings.doubleQuote(internal) : internal;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (other instanceof CqlIdentifier) {
      CqlIdentifier that = (CqlIdentifier) other;
      return this.internal.equals(that.internal);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return internal.hashCode();
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    Preconditions.checkNotNull(internal, "internal must not be null");
  }
}