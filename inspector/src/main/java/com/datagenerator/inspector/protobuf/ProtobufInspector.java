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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a compiled protobuf {@code FileDescriptorSet} ({@code .desc}/{@code .binpb}/{@code
 * .protoset}) and maps every non-synthetic message to a SeedStream {@link DataStructure}. See
 * {@code docs/INSPECT-V1-SPEC.md}.
 */
public class ProtobufInspector {

  static final long MAX_DESCRIPTOR_BYTES = 64L * 1024 * 1024;

  private final ProtobufTypeMapper mapper = new ProtobufTypeMapper();

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

    for (FileDescriptor fd : fileDescriptors) {
      for (Descriptor message : allMessages(fd)) {
        DataStructure structure = toStructure(message, comments, warnings);
        if (structure != null) {
          structures.add(structure);
        }
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

  private DataStructure toStructure(
      Descriptor descriptor, Map<String, Map<String, String>> comments, List<String> warnings) {
    String name = Names.toSnakeCase(descriptor.getName());

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
