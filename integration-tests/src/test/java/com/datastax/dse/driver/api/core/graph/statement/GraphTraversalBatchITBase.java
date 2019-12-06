/*
 * Copyright DataStax, Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dse.driver.api.core.graph.statement;

import static com.datastax.dse.driver.api.core.graph.TinkerGraphAssertions.assertThat;
import static com.datastax.dse.driver.internal.core.graph.GraphTestUtils.assertThatContainsLabel;
import static com.datastax.dse.driver.internal.core.graph.GraphTestUtils.assertThatContainsProperties;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addV;
import static org.assertj.core.api.Assertions.fail;

import com.datastax.dse.driver.api.core.DseSession;
import com.datastax.dse.driver.api.core.graph.BatchGraphStatement;
import com.datastax.dse.driver.api.core.graph.FluentGraphStatement;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.testinfra.ccm.CustomCcmRule;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableList;
import java.util.Map;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.junit.Test;

public abstract class GraphTraversalBatchITBase {

  protected abstract DseSession session();

  protected abstract boolean isGraphBinary();

  protected abstract CustomCcmRule ccmRule();

  protected abstract GraphTraversalSource graphTraversalSource();

  @Test
  public void should_allow_vertex_and_edge_insertions_in_batch() {
    BatchGraphStatement batch =
        BatchGraphStatement.builder()
            .addTraversals(
                ImmutableList.of(
                    addV("person").property("name", "batch1").property("age", 1),
                    addV("person").property("name", "batch2").property("age", 2)))
            .build();

    BatchGraphStatement batch2 =
        BatchGraphStatement.builder()
            .addTraversals(batch)
            .addTraversal(
                addE("knows")
                    .from(__.<Edge>V().has("name", "batch1"))
                    .to(__.<Edge>V().has("name", "batch2"))
                    .property("weight", 2.3f))
            .build();

    assertThat(batch.size()).isEqualTo(2);
    assertThat(batch2.size()).isEqualTo(3);

    session().execute(batch2);

    if (isGraphBinary()) {
      Map<Object, Object> properties =
          session()
              .execute(
                  FluentGraphStatement.newInstance(
                      graphTraversalSource().V().has("name", "batch1").elementMap("age")))
              .one()
              .asMap();

      assertThatContainsProperties(properties, "age", 1);

      properties =
          session()
              .execute(
                  FluentGraphStatement.newInstance(
                      graphTraversalSource().V().has("name", "batch2").elementMap("age")))
              .one()
              .asMap();

      assertThatContainsProperties(properties, "age", 2);

      properties =
          session()
              .execute(
                  FluentGraphStatement.newInstance(
                      graphTraversalSource()
                          .V()
                          .has("name", "batch1")
                          .bothE()
                          .elementMap("weight", "person")))
              .one()
              .asMap();

      assertThatContainsProperties(properties, "weight", 2.3f);
      assertThatContainsLabel(properties, Direction.IN, "person");
      assertThatContainsLabel(properties, Direction.OUT, "person");

    } else {

      assertThat(
              session()
                  .execute(
                      FluentGraphStatement.newInstance(
                          graphTraversalSource().V().has("name", "batch1")))
                  .one()
                  .asVertex())
          .hasProperty("age", 1);

      assertThat(
              session()
                  .execute(
                      FluentGraphStatement.newInstance(
                          graphTraversalSource().V().has("name", "batch2")))
                  .one()
                  .asVertex())
          .hasProperty("age", 2);

      assertThat(
              session()
                  .execute(
                      FluentGraphStatement.newInstance(
                          graphTraversalSource().V().has("name", "batch1").bothE()))
                  .one()
                  .asEdge())
          .hasProperty("weight", 2.3f)
          .hasOutVLabel("person")
          .hasInVLabel("person");
    }
  }

  @Test
  public void should_fail_if_no_bytecode_in_batch() {
    BatchGraphStatement batch =
        BatchGraphStatement.builder().addTraversals(ImmutableList.of()).build();
    assertThat(batch.size()).isEqualTo(0);
    try {
      session().execute(batch);
      fail(
          "Should have thrown InvalidQueryException because batch does not contain any traversals.");
    } catch (InvalidQueryException e) {
      assertThat(e.getMessage())
          .contains(
              "Could not read the traversal from the request sent.",
              "The batch statement sent does not contain any traversal.");
    }
  }
}