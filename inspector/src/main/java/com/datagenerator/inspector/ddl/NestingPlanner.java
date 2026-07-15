/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datagenerator.inspector.ddl;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Inverts relational {@code 1:n} / {@code 1:1} foreign keys into nested {@code
 * array[object[child]]} / {@code object[child]} fields on the parent structure, the direction
 * SeedStream nests (parent embeds child) versus the direction a DB declares them (child references
 * parent). Pure and unit-testable: it takes neutral {@link TableInfo} records, not JSQLParser
 * types. See {@code docs/INSPECT-V1-SPEC.md} §9.
 *
 * <p>Safety: cycles (incl. self-references), composite FKs, and M:N junction tables are never
 * nested — they keep their flat {@code ref[]} and emit a warning ({@code AUTO}) or error ({@code
 * ALL}, cycles only). A child is embedded into at most one parent (first by declaration order);
 * other parents keep flat refs. Every structure is still written to disk even when embedded,
 * because {@code object[child]} auto-loads {@code child.yaml} — "demotion" here means a warning,
 * not file suppression.
 */
public final class NestingPlanner {

  /** A foreign-key constraint as captured from the DDL (may be composite). */
  public record ForeignKeyRef(
      List<String> localColumns, String refTable, List<String> refColumns) {}

  /**
   * Neutral per-table view the planner operates on.
   *
   * @param name snake_case structure name
   * @param data ordered field map (FK columns already carry their {@code ref[]} datatype); mutated
   *     in place as embeddings are applied
   * @param comments review comments keyed by field name; mutated in place
   * @param primaryKeyColumns PK column names
   * @param uniqueColumns UNIQUE column names (drives 1:1 vs 1:n)
   * @param foreignKeys FK constraints declared on this (child) table
   * @param order zero-based declaration order, for deterministic parent selection
   */
  public record TableInfo(
      String name,
      LinkedHashMap<String, FieldDefinition> data,
      LinkedHashMap<String, String> comments,
      Set<String> primaryKeyColumns,
      Set<String> uniqueColumns,
      List<ForeignKeyRef> foreignKeys,
      int order) {}

  private record Edge(String child, String childCol, String parent, String parentCol) {}

  private enum Color {
    WHITE,
    GRAY,
    BLACK
  }

  /** Plans and applies nesting, returning the rewritten structures plus comments and warnings. */
  public Inspection plan(List<TableInfo> tables, NestingOptions opts) {
    Map<String, TableInfo> byName =
        tables.stream()
            .collect(Collectors.toMap(t -> t.name().toLowerCase(Locale.ROOT), t -> t, (a, b) -> a));
    List<String> warnings = new ArrayList<>();

    List<Edge> candidates = collectCandidateEdges(tables, byName, warnings);
    removeCyclicEdges(candidates, tables, opts, warnings);
    List<Edge> chosen = chooseEmbeddings(candidates, byName, warnings);
    applyEmbeddings(chosen, byName, opts, warnings);

    return buildInspection(tables, warnings);
  }

  /** Walks every FK, dropping the ones that must stay flat (composite, self-ref, M:N, dangling). */
  private List<Edge> collectCandidateEdges(
      List<TableInfo> tables, Map<String, TableInfo> byName, List<String> warnings) {
    List<Edge> edges = new ArrayList<>();
    for (TableInfo table : tables) {
      boolean junction = isJunction(table);
      if (junction) {
        warnings.add(
            "table '"
                + table.name()
                + "' looks like an M:N junction — keeping flat refs, not nesting");
      }
      for (ForeignKeyRef fk : table.foreignKeys()) {
        candidateEdge(table, fk, junction, byName, warnings).ifPresent(edges::add);
      }
    }
    return edges;
  }

  /** Returns an Edge if {@code fk} is eligible for nesting, or empty if it must stay flat. */
  private Optional<Edge> candidateEdge(
      TableInfo table,
      ForeignKeyRef fk,
      boolean junction,
      Map<String, TableInfo> byName,
      List<String> warnings) {
    if (fk.localColumns().size() != 1) {
      warnings.add("composite FK on '" + table.name() + "' — single-field nesting only, kept flat");
      return Optional.empty();
    }
    String childCol = fk.localColumns().get(0);
    String parent = fk.refTable().toLowerCase(Locale.ROOT);
    if (!byName.containsKey(parent) || junction) {
      return Optional.empty();
    }
    if (parent.equals(table.name().toLowerCase(Locale.ROOT))) {
      warnings.add("self-referencing FK on '" + table.name() + "' — kept flat (cycle)");
      return Optional.empty();
    }
    String parentCol = fk.refColumns().isEmpty() ? "id" : fk.refColumns().get(0);
    return Optional.of(new Edge(table.name(), childCol, byName.get(parent).name(), parentCol));
  }

  /** A pure junction: exactly two single-column FKs and no payload columns beyond them. */
  private boolean isJunction(TableInfo table) {
    List<String> fkCols =
        table.foreignKeys().stream()
            .filter(fk -> fk.localColumns().size() == 1)
            .map(fk -> fk.localColumns().get(0).toLowerCase(Locale.ROOT))
            .toList();
    if (fkCols.size() != 2) {
      return false;
    }
    Set<String> fkColSet = new TreeSet<>(fkCols);
    long payload =
        table.data().keySet().stream()
            .map(c -> c.toLowerCase(Locale.ROOT))
            .filter(c -> !fkColSet.contains(c))
            .count();
    return payload == 0;
  }

  /** Detects back-edges in the parent→child embedding graph and removes (or errors on) them. */
  private void removeCyclicEdges(
      List<Edge> edges, List<TableInfo> tables, NestingOptions opts, List<String> warnings) {
    Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
    for (Edge e : edges) {
      adjacency.computeIfAbsent(e.parent(), k -> new ArrayList<>()).add(e);
    }
    Map<String, Color> color = new LinkedHashMap<>();
    tables.forEach(t -> color.put(t.name(), Color.WHITE));
    Set<Edge> backEdges = new java.util.LinkedHashSet<>();
    for (TableInfo t : tables) {
      if (color.get(t.name()) == Color.WHITE) {
        dfs(t.name(), adjacency, color, backEdges);
      }
    }
    if (backEdges.isEmpty()) {
      return;
    }
    if (opts.mode() == NestingOptions.Mode.ALL) {
      throw new InspectorException(
          "FK cycle detected ("
              + describe(backEdges)
              + ") — re-run with --nest=auto to keep it flat");
    }
    for (Edge back : backEdges) {
      warnings.add(
          "FK cycle on '" + back.child() + "' → '" + back.parent() + "' — kept flat to break it");
    }
    edges.removeAll(backEdges);
  }

  private void dfs(
      String node,
      Map<String, List<Edge>> adjacency,
      Map<String, Color> color,
      Set<Edge> backEdges) {
    color.put(node, Color.GRAY);
    for (Edge e : adjacency.getOrDefault(node, List.of())) {
      Color childColor = color.getOrDefault(e.child(), Color.WHITE);
      if (childColor == Color.GRAY) {
        backEdges.add(e);
      } else if (childColor == Color.WHITE) {
        dfs(e.child(), adjacency, color, backEdges);
      }
    }
    color.put(node, Color.BLACK);
  }

  /** Each child is embedded into at most one parent (lowest declaration order); rest stay flat. */
  private List<Edge> chooseEmbeddings(
      List<Edge> edges, Map<String, TableInfo> byName, List<String> warnings) {
    Map<String, List<Edge>> byChild = new LinkedHashMap<>();
    for (Edge e : edges) {
      byChild.computeIfAbsent(e.child(), k -> new ArrayList<>()).add(e);
    }
    List<Edge> chosen = new ArrayList<>();
    for (Map.Entry<String, List<Edge>> entry : byChild.entrySet()) {
      List<Edge> childEdges = entry.getValue();
      childEdges.sort(
          (a, b) -> {
            int ord = Integer.compare(orderOf(byName, a.parent()), orderOf(byName, b.parent()));
            return ord != 0 ? ord : a.childCol().compareToIgnoreCase(b.childCol());
          });
      chosen.add(childEdges.get(0));
      for (int i = 1; i < childEdges.size(); i++) {
        Edge demoted = childEdges.get(i);
        warnings.add(
            "child '"
                + demoted.child()
                + "' already embedded elsewhere — FK to '"
                + demoted.parent()
                + "' kept flat");
      }
    }
    return chosen;
  }

  private int orderOf(Map<String, TableInfo> byName, String tableName) {
    TableInfo t = byName.get(tableName.toLowerCase(Locale.ROOT));
    return t == null ? Integer.MAX_VALUE : t.order();
  }

  /** Adds the nested field to each parent and drops the now-redundant FK column from the child. */
  private void applyEmbeddings(
      List<Edge> chosen,
      Map<String, TableInfo> byName,
      NestingOptions opts,
      List<String> warnings) {
    for (Edge e : chosen) {
      TableInfo child = byName.get(e.child().toLowerCase(Locale.ROOT));
      TableInfo parent = byName.get(e.parent().toLowerCase(Locale.ROOT));
      boolean oneToOne =
          containsIgnoreCase(child.primaryKeyColumns(), e.childCol())
              || containsIgnoreCase(child.uniqueColumns(), e.childCol());

      String desired = oneToOne ? e.child() : Pluralizer.pluralize(e.child());
      String fieldName = resolveFieldName(parent.data(), desired, warnings, e.parent());
      String datatype =
          oneToOne
              ? "object[" + e.child() + "]"
              : "array[object["
                  + e.child()
                  + "], "
                  + opts.defaultMin()
                  + ".."
                  + opts.defaultMax()
                  + "]";
      parent.data().put(fieldName, new FieldDefinition(datatype, null));

      dropChildForeignKey(child, e.childCol());
      warnings.add(
          "nested '"
              + e.child()
              + "' into '"
              + e.parent()
              + "' as "
              + (oneToOne ? "object" : "array")
              + " field '"
              + fieldName
              + "'");
    }
  }

  /** Removes the FK column from the embedded child unless doing so would empty the structure. */
  private void dropChildForeignKey(TableInfo child, String childCol) {
    String key = actualKey(child.data().keySet(), childCol);
    if (key != null && child.data().size() > 1) {
      child.data().remove(key);
      child.comments().remove(key);
    }
  }

  /** Resolves a non-colliding field name, suffixing {@code _set} on collision (§5). */
  private String resolveFieldName(
      Map<String, FieldDefinition> parentData,
      String desired,
      List<String> warnings,
      String parentName) {
    if (actualKey(parentData.keySet(), desired) == null) {
      return desired;
    }
    warnings.add(
        "nested field '"
            + desired
            + "' collides on '"
            + parentName
            + "' — renamed to '"
            + desired
            + "_set'");
    String candidate = desired + "_set";
    int suffix = 2;
    while (actualKey(parentData.keySet(), candidate) != null) {
      candidate = desired + "_set" + suffix++;
    }
    return candidate;
  }

  private Inspection buildInspection(List<TableInfo> tables, List<String> warnings) {
    List<DataStructure> structures = new ArrayList<>();
    Map<String, Map<String, String>> comments = new LinkedHashMap<>();
    for (TableInfo t : tables) {
      structures.add(new DataStructure(t.name(), null, new LinkedHashMap<>(t.data())));
      if (!t.comments().isEmpty()) {
        comments.put(t.name(), new LinkedHashMap<>(t.comments()));
      }
    }
    return Inspection.of(structures, comments, warnings);
  }

  private static boolean containsIgnoreCase(Collection<String> values, String target) {
    return values.stream().anyMatch(v -> v.equalsIgnoreCase(target));
  }

  private static String actualKey(Collection<String> keys, String target) {
    return keys.stream().filter(k -> k.equalsIgnoreCase(target)).findFirst().orElse(null);
  }

  private static String describe(Set<Edge> edges) {
    return edges.stream().map(e -> e.parent() + "→" + e.child()).collect(Collectors.joining(", "));
  }
}
