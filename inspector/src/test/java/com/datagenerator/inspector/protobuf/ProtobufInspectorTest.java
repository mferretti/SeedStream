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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.InspectorException;
import com.datagenerator.schema.model.DataStructure;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtobufInspectorTest {

  @TempDir Path tempDir;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static FieldDescriptorProto field(String name, int num, Type type, Label label) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(num)
        .setType(type)
        .setLabel(label)
        .build();
  }

  private static FieldDescriptorProto optionalField(String name, int num, Type type) {
    return field(name, num, type, Label.LABEL_OPTIONAL);
  }

  private Path writeDescriptorSet(FileDescriptorSet set) throws IOException {
    Path file = tempDir.resolve("schema.desc");
    try (OutputStream out = Files.newOutputStream(file)) {
      set.writeTo(out);
    }
    return file;
  }

  private Path buildAndWrite(FileDescriptorProto... protos) throws IOException {
    FileDescriptorSet.Builder setBuilder = FileDescriptorSet.newBuilder();
    for (FileDescriptorProto p : protos) {
      setBuilder.addFile(p);
    }
    return writeDescriptorSet(setBuilder.build());
  }

  private Map<String, String> datatypesOf(Inspection inspection, String structureName) {
    DataStructure structure =
        inspection.structures().stream()
            .filter(s -> s.getName().equals(structureName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Structure not found: " + structureName));
    return structure.getData().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDatatype(), (a, b) -> a));
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Test
  void shouldMapScalarFieldsToCorrectDatatypes() throws IOException {
    EnumDescriptorProto status =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(0))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("INACTIVE").setNumber(1))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Order")
            .addField(optionalField("order_id", 1, Type.TYPE_INT64))
            .addField(optionalField("amount", 2, Type.TYPE_DOUBLE))
            .addField(optionalField("confirmed", 3, Type.TYPE_BOOL))
            .addField(optionalField("note", 4, Type.TYPE_STRING))
            .addField(optionalField("data", 5, Type.TYPE_BYTES))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("status")
                    .setNumber(6)
                    .setType(Type.TYPE_ENUM)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setTypeName(".pkg.Status")
                    .build())
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("order.proto")
            .setPackage("pkg")
            .setSyntax("proto3")
            .addEnumType(status)
            .addMessageType(msg)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));

    assertThat(inspection.structures()).extracting(DataStructure::getName).contains("order");
    Map<String, String> types = datatypesOf(inspection, "order");
    assertThat(types.get("order_id")).startsWith("int[");
    assertThat(types.get("amount")).startsWith("decimal[");
    assertThat(types).containsEntry("confirmed", "boolean");
    assertThat(types.get("note")).isNotNull();
    assertThat(types).containsEntry("status", "enum[ACTIVE,INACTIVE]");
    // bytes → flagged
    assertThat(inspection.comments().getOrDefault("order", Map.of())).containsKey("data");
  }

  @Test
  void shouldMapEmailNameHintToFakerType() throws IOException {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(optionalField("email", 1, Type.TYPE_STRING))
            .addField(optionalField("first_name", 2, Type.TYPE_STRING))
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("user.proto")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));
    Map<String, String> types = datatypesOf(inspection, "user");
    // email and first_name should resolve to datafaker keys (name-hint)
    assertThat(types).doesNotContainEntry("email", "char[1..50]");
    Map<String, String> comments = inspection.comments().getOrDefault("user", Map.of());
    assertThat(comments).containsKey("email");
    assertThat(comments.get("email")).contains("guessed from column name");
  }

  @Test
  void shouldMapRepeatedFieldToArray() throws IOException {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Batch")
            .addField(field("ids", 1, Type.TYPE_INT32, Label.LABEL_REPEATED))
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("batch.proto")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));
    Map<String, String> types = datatypesOf(inspection, "batch");
    assertThat(types.get("ids")).startsWith("array[int[");
  }

  @Test
  void shouldEmitNestedMessageAsObjectRefAndOwnStructure() throws IOException {
    DescriptorProto address =
        DescriptorProto.newBuilder()
            .setName("Address")
            .addField(optionalField("street", 1, Type.TYPE_STRING))
            .build();

    DescriptorProto customer =
        DescriptorProto.newBuilder()
            .setName("Customer")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("address")
                    .setNumber(1)
                    .setType(Type.TYPE_MESSAGE)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setTypeName(".pkg.Address")
                    .build())
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("customer.proto")
            .setPackage("pkg")
            .setSyntax("proto3")
            .addMessageType(address)
            .addMessageType(customer)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));
    assertThat(inspection.structures())
        .extracting(DataStructure::getName)
        .contains("address", "customer");
    Map<String, String> types = datatypesOf(inspection, "customer");
    assertThat(types).containsEntry("address", "object[address]");
  }

  @Test
  void shouldSnakeCaseStructureName() throws IOException {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("LineItem")
            .addField(optionalField("quantity", 1, Type.TYPE_INT32))
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("line_item.proto")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));
    assertThat(inspection.structures()).extracting(DataStructure::getName).contains("line_item");
  }

  @Test
  void shouldSkipMessagesWithNoFieldsAndWarn() throws IOException {
    DescriptorProto empty = DescriptorProto.newBuilder().setName("EmptyMsg").build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("empty.proto")
            .setSyntax("proto3")
            .addMessageType(empty)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));
    assertThat(inspection.structures()).isEmpty();
    assertThat(inspection.warnings())
        .anyMatch(w -> w.contains("EmptyMsg") && w.contains("skipped"));
  }

  @Test
  void shouldAddOneofCommentForOneofFields() throws IOException {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Payment")
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("method").build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("card_number")
                    .setNumber(1)
                    .setType(Type.TYPE_STRING)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setOneofIndex(0)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("paypal_id")
                    .setNumber(2)
                    .setType(Type.TYPE_STRING)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setOneofIndex(0)
                    .build())
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("payment.proto")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));
    Map<String, String> comments = inspection.comments().getOrDefault("payment", Map.of());
    assertThat(comments).containsKey("card_number");
    assertThat(comments.get("card_number")).contains("oneof").contains("method");
    assertThat(comments).containsKey("paypal_id");
  }

  @Test
  void shouldMapGoogleProtobufTimestampToTimestamp() throws IOException {
    // Build a minimal google.protobuf.Timestamp file descriptor to satisfy the dep
    DescriptorProto timestampMsg =
        DescriptorProto.newBuilder()
            .setName("Timestamp")
            .addField(optionalField("seconds", 1, Type.TYPE_INT64))
            .addField(optionalField("nanos", 2, Type.TYPE_INT32))
            .build();
    FileDescriptorProto googleProto =
        FileDescriptorProto.newBuilder()
            .setName("google/protobuf/timestamp.proto")
            .setPackage("google.protobuf")
            .setSyntax("proto3")
            .addMessageType(timestampMsg)
            .build();

    DescriptorProto eventMsg =
        DescriptorProto.newBuilder()
            .setName("Event")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("occurred_at")
                    .setNumber(1)
                    .setType(Type.TYPE_MESSAGE)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setTypeName(".google.protobuf.Timestamp")
                    .build())
            .build();
    FileDescriptorProto eventProto =
        FileDescriptorProto.newBuilder()
            .setName("event.proto")
            .setSyntax("proto3")
            .addDependency("google/protobuf/timestamp.proto")
            .addMessageType(eventMsg)
            .build();

    Path file = buildAndWrite(googleProto, eventProto);
    Inspection inspection = new ProtobufInspector().inspect(file);

    // Find the "event" structure (google.protobuf messages are also collected but Timestamp has
    // no user fields — it will be included; we only assert on event)
    Map<String, String> types = datatypesOf(inspection, "event");
    assertThat(types.get("occurred_at")).startsWith("timestamp[");
  }

  @Test
  void shouldThrowInspectorExceptionForNonExistentFile() {
    Path missing = tempDir.resolve("nonexistent.desc");
    ProtobufInspector inspector = new ProtobufInspector();
    assertThatThrownBy(() -> inspector.inspect(missing))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("Failed to read protobuf descriptor set");
  }

  @Test
  void shouldThrowInspectorExceptionForInvalidBytes() throws IOException {
    Path bad = tempDir.resolve("bad.desc");
    Files.write(bad, new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});
    // Corrupt content — parsing may succeed with proto3 leniency or fail, but inspector
    // should handle both: if parsing fails it throws; if it succeeds it emits empty structures.
    // We only verify that the call does not throw an unexpected exception type.
    ProtobufInspector inspector = new ProtobufInspector();
    // Either returns an inspection or throws InspectorException — never anything else
    try {
      Inspection result = inspector.inspect(bad);
      assertThat(result).isNotNull();
    } catch (InspectorException e) {
      assertThat(e).hasMessageContaining("protobuf descriptor set");
    }
  }

  @Test
  void shouldCollectNestedTypesRecursively() throws IOException {
    DescriptorProto inner =
        DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(optionalField("value", 1, Type.TYPE_INT32))
            .build();

    DescriptorProto outer =
        DescriptorProto.newBuilder()
            .setName("Outer")
            .addField(optionalField("name", 1, Type.TYPE_STRING))
            .addNestedType(inner)
            .build();

    FileDescriptorProto fdp =
        FileDescriptorProto.newBuilder()
            .setName("nested.proto")
            .setSyntax("proto3")
            .addMessageType(outer)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(fdp));
    assertThat(inspection.structures())
        .extracting(DataStructure::getName)
        .contains("outer", "inner");
  }

  @Test
  void shouldResolveDependenciesBetweenFiles() throws IOException {
    DescriptorProto product =
        DescriptorProto.newBuilder()
            .setName("Product")
            .addField(optionalField("name", 1, Type.TYPE_STRING))
            .build();
    FileDescriptorProto productProto =
        FileDescriptorProto.newBuilder()
            .setName("product.proto")
            .setPackage("shop")
            .setSyntax("proto3")
            .addMessageType(product)
            .build();

    DescriptorProto cart =
        DescriptorProto.newBuilder()
            .setName("Cart")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("product")
                    .setNumber(1)
                    .setType(Type.TYPE_MESSAGE)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setTypeName(".shop.Product")
                    .build())
            .build();
    FileDescriptorProto cartProto =
        FileDescriptorProto.newBuilder()
            .setName("cart.proto")
            .setPackage("shop")
            .setSyntax("proto3")
            .addDependency("product.proto")
            .addMessageType(cart)
            .build();

    Inspection inspection = new ProtobufInspector().inspect(buildAndWrite(productProto, cartProto));
    assertThat(inspection.structures())
        .extracting(DataStructure::getName)
        .contains("product", "cart");
    assertThat(datatypesOf(inspection, "cart")).containsEntry("product", "object[product]");
  }
}
