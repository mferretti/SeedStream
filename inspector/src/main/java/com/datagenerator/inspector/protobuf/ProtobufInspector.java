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

package com.datagenerator.inspector.protobuf;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.MappedType;
import com.datagenerator.inspector.Names;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads a compiled protobuf {@code FileDescriptorSet} ({@code .desc}/{@code .binpb}/{@code
 * .protoset}) and maps every non-synthetic message to a SeedStream {@link DataStructure}. See
 * {@code docs/INSPECT-V1-SPEC.md}.
 */
public class ProtobufInspector {

  static final long MAX_DESCRIPTOR_BYTES = 64L * 1024 * 1024;

  static void validateSize(long size) {
    if (size > MAX_DESCRIPTOR_BYTES) {
      throw new InspectorException(
          "Protobuf descriptor set too large: "
              + size
              + " bytes (max "
              + MAX_DESCRIPTOR_BYTES
              + ")");
    }
  }

  /** Inspects a compiled FileDescriptorSet file and returns the structures plus diagnostics. */
  public Inspection inspect(Path descriptorSetFile) {
    byte[] bytes = readBytes(descriptorSetFile);
    FileDescriptorSet set = parseDescriptorSet(bytes, descriptorSetFile);
    List<FileDescriptor> fileDescriptors = buildFileDescriptors(set, descriptorSetFile);

    List<DataStructure> structures = new ArrayList<>();
    Map<String, Map<String, String>> comments = new LinkedHashMap<>();
    List<String> warnings = new ArrayList<>();

    List<Descriptor> messages =
        fileDescriptors.stream().flatMap(fd -> allMessages(fd).stream()).toList();
    Map<String, String> names = assignStructureNames(messages, warnings);
    ProtobufTypeMapper mapper =
        new ProtobufTypeMapper(
            d -> names.getOrDefault(d.getFullName(), Names.toSnakeCase(d.getName())));

    for (Descriptor message : messages) {
      DataStructure structure =
          toStructure(message, names.get(message.getFullName()), mapper, comments, warnings);
      if (structure != null) {
        structures.add(structure);
      }
    }

    return Inspection.of(structures, comments, warnings);
  }

  private byte[] readBytes(Path file) {
    try {
      validateSize(Files.size(file));
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new InspectorException("Failed to read protobuf descriptor set: " + file, e);
    }
  }

  private FileDescriptorSet parseDescriptorSet(byte[] bytes, Path file) {
    try {
      return FileDescriptorSet.parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      throw new InspectorException("Failed to read protobuf descriptor set: " + file, e);
    }
  }

  private List<FileDescriptor> buildFileDescriptors(FileDescriptorSet set, Path file) {
    LinkedHashMap<String, FileDescriptor> built = new LinkedHashMap<>();
    try {
      for (FileDescriptorProto fdp : set.getFileList()) {
        FileDescriptor[] deps =
            fdp.getDependencyList().stream()
                .map(built::get)
                .filter(d -> d != null)
                .toArray(FileDescriptor[]::new);
        FileDescriptor fd = FileDescriptor.buildFrom(fdp, deps);
        built.put(fdp.getName(), fd);
      }
    } catch (DescriptorValidationException e) {
      throw new InspectorException("Failed to read protobuf descriptor set: " + file, e);
    }
    return new ArrayList<>(built.values());
  }

  /** Returns all messages in a file: top-level and recursively nested (excluding map entries). */
  private List<Descriptor> allMessages(FileDescriptor fd) {
    List<Descriptor> result = new ArrayList<>();
    for (Descriptor top : fd.getMessageTypes()) {
      collectMessages(top, result);
    }
    return result;
  }

  private void collectMessages(Descriptor descriptor, List<Descriptor> result) {
    if (descriptor.getOptions().getMapEntry()) {
      return;
    }
    result.add(descriptor);
    for (Descriptor nested : descriptor.getNestedTypes()) {
      collectMessages(nested, result);
    }
  }

  /**
   * Assigns each message (by full name) a structure name. The snake-cased short name is used when
   * it is unique; messages sharing a short name (e.g. {@code Order.Item} and {@code Invoice.Item},
   * or {@code v1.Item} and {@code v2.Item}) are qualified by their enclosing messages and then, if
   * still ambiguous, by their package, so no structure overwrites another (#350).
   *
   * @throws InspectorException if names are still ambiguous after full qualification
   */
  static Map<String, String> assignStructureNames(
      List<Descriptor> messages, List<String> warnings) {
    Map<String, String> names = new LinkedHashMap<>();
    messages.forEach(d -> names.put(d.getFullName(), Names.toSnakeCase(d.getName())));
    for (boolean withPackage : new boolean[] {false, true}) {
      Set<String> ambiguous = duplicates(names.values());
      if (ambiguous.isEmpty()) {
        break;
      }
      for (Descriptor d : messages) {
        if (ambiguous.contains(names.get(d.getFullName()))) {
          names.put(d.getFullName(), qualifiedName(d, withPackage));
        }
      }
    }
    Set<String> ambiguous = duplicates(names.values());
    if (!ambiguous.isEmpty()) {
      throw new InspectorException(
          "Cannot derive unique structure names for protobuf messages; ambiguous: " + ambiguous);
    }
    for (Descriptor d : messages) {
      String shortName = Names.toSnakeCase(d.getName());
      String name = names.get(d.getFullName());
      if (!name.equals(shortName)) {
        warnings.add(
            "message '"
                + d.getFullName()
                + "' emitted as '"
                + name
                + "' — short name '"
                + shortName
                + "' is shared by another message");
      }
    }
    return names;
  }

  private static String qualifiedName(Descriptor d, boolean withPackage) {
    Objects.requireNonNull(d);
    Deque<String> parts = new ArrayDeque<>();
    for (Descriptor c = d; c != null; c = c.getContainingType()) {
      parts.addFirst(Names.toSnakeCase(c.getName()));
    }
    String pkg = d.getFile().getPackage();
    if (withPackage && !pkg.isEmpty()) {
      List<String> pkgParts = Arrays.stream(pkg.split("\\.")).map(Names::toSnakeCase).toList();
      for (int i = pkgParts.size() - 1; i >= 0; i--) {
        parts.addFirst(pkgParts.get(i));
      }
    }
    return String.join("_", parts);
  }

  private static Set<String> duplicates(Collection<String> values) {
    Set<String> seen = new HashSet<>();
    Set<String> dups = new LinkedHashSet<>();
    values.forEach(v -> (seen.add(v) ? seen : dups).add(v));
    return dups;
  }

  private DataStructure toStructure(
      Descriptor descriptor,
      String name,
      ProtobufTypeMapper mapper,
      Map<String, Map<String, String>> comments,
      List<String> warnings) {

    if (descriptor.getFields().isEmpty()) {
      warnings.add("message '" + descriptor.getName() + "' has no fields — skipped");
      return null;
    }

    Map<String, FieldDefinition> data = new LinkedHashMap<>();
    Map<String, String> fieldComments = new LinkedHashMap<>();

    for (FieldDescriptor f : descriptor.getFields()) {
      String fieldName = f.getName();
      MappedType mapped = mapper.map(f);

      String comment = null;
      if (mapped.flagged()) {
        comment = mapped.comment();
      }
      if (f.getRealContainingOneof() != null) {
        String oneofComment =
            "part of oneof '"
                + f.getRealContainingOneof().getName()
                + "' — only one is set at runtime";
        comment = comment == null ? oneofComment : comment + "; " + oneofComment;
      }
      if (comment != null) {
        fieldComments.put(fieldName, comment);
      }

      data.put(fieldName, new FieldDefinition(mapped.datatype(), null));
    }

    if (!fieldComments.isEmpty()) {
      comments.put(name, fieldComments);
    }

    return new DataStructure(name, null, data);
  }
}
