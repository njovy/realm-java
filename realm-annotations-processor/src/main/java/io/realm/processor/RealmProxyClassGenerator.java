/*
 * Copyright 2014 Realm Inc.
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

package io.realm.processor;

import com.squareup.javawriter.JavaWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

public class RealmProxyClassGenerator {
    private ProcessingEnvironment processingEnvironment;
    private ClassMetaData metadata;
    private final String className;

    // Class metadata for generating proxy classes
    private Elements elementUtils;
    private Types typeUtils;
    private TypeMirror realmObject;
    private DeclaredType realmList;

    public RealmProxyClassGenerator(ProcessingEnvironment processingEnvironment, ClassMetaData metadata) {
        this.processingEnvironment = processingEnvironment;
        this.metadata = metadata;
        this.className = metadata.getSimpleClassName();
    }

    private static final Map<String, String> JAVA_TO_REALM_TYPES;
    static {
        JAVA_TO_REALM_TYPES = new HashMap<String, String>();
        JAVA_TO_REALM_TYPES.put("byte", "Long");
        JAVA_TO_REALM_TYPES.put("short", "Long");
        JAVA_TO_REALM_TYPES.put("int", "Long");
        JAVA_TO_REALM_TYPES.put("long", "Long");
        JAVA_TO_REALM_TYPES.put("float", "Float");
        JAVA_TO_REALM_TYPES.put("double", "Double");
        JAVA_TO_REALM_TYPES.put("boolean", "Boolean");
        JAVA_TO_REALM_TYPES.put("Byte", "Long");
        JAVA_TO_REALM_TYPES.put("Short", "Long");
        JAVA_TO_REALM_TYPES.put("Integer", "Long");
        JAVA_TO_REALM_TYPES.put("Long", "Long");
        JAVA_TO_REALM_TYPES.put("Float", "Float");
        JAVA_TO_REALM_TYPES.put("Double", "Double");
        JAVA_TO_REALM_TYPES.put("Boolean", "Boolean");
        JAVA_TO_REALM_TYPES.put("java.lang.String", "String");
        JAVA_TO_REALM_TYPES.put("java.util.Date", "Date");
        JAVA_TO_REALM_TYPES.put("byte[]", "BinaryByteArray");
        // TODO: add support for char and Char
    }

    // Types in this array are guarded by if != null and use default value if trying to insert null
    private static final Map<String, String> NULLABLE_JAVA_TYPES;
    static {
        NULLABLE_JAVA_TYPES = new HashMap<String, String>();
        NULLABLE_JAVA_TYPES.put("java.util.Date", "new Date(0)");
        NULLABLE_JAVA_TYPES.put("java.lang.String", "\"\"");
        NULLABLE_JAVA_TYPES.put("byte[]", "new byte[0]");
    }

    private static final Map<String, String> JAVA_TO_COLUMN_TYPES;
    static {
        JAVA_TO_COLUMN_TYPES = new HashMap<String, String>();
        JAVA_TO_COLUMN_TYPES.put("byte", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("short", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("int", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("long", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("float", "ColumnType.FLOAT");
        JAVA_TO_COLUMN_TYPES.put("double", "ColumnType.DOUBLE");
        JAVA_TO_COLUMN_TYPES.put("boolean", "ColumnType.BOOLEAN");
        JAVA_TO_COLUMN_TYPES.put("Byte", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("Short", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("Integer", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("Long", "ColumnType.INTEGER");
        JAVA_TO_COLUMN_TYPES.put("Float", "ColumnType.FLOAT");
        JAVA_TO_COLUMN_TYPES.put("Double", "ColumnType.DOUBLE");
        JAVA_TO_COLUMN_TYPES.put("Boolean", "ColumnType.BOOLEAN");
        JAVA_TO_COLUMN_TYPES.put("java.lang.String", "ColumnType.STRING");
        JAVA_TO_COLUMN_TYPES.put("java.util.Date", "ColumnType.DATE");
        JAVA_TO_COLUMN_TYPES.put("byte[]", "ColumnType.BINARY");
    }

    private static final Map<String, String> CASTING_TYPES;
    static {
        CASTING_TYPES = new HashMap<String, String>();
        CASTING_TYPES.put("byte", "long");
        CASTING_TYPES.put("short", "long");
        CASTING_TYPES.put("int", "long");
        CASTING_TYPES.put("long", "long");
        CASTING_TYPES.put("float", "float");
        CASTING_TYPES.put("double", "double");
        CASTING_TYPES.put("boolean", "boolean");
        CASTING_TYPES.put("Byte", "long");
        CASTING_TYPES.put("Short", "long");
        CASTING_TYPES.put("Integer", "long");
        CASTING_TYPES.put("Long", "long");
        CASTING_TYPES.put("Float", "float");
        CASTING_TYPES.put("Double", "double");
        CASTING_TYPES.put("Boolean", "boolean");
        CASTING_TYPES.put("java.lang.String", "String");
        CASTING_TYPES.put("java.util.Date", "Date");
        CASTING_TYPES.put("byte[]", "byte[]");
    }

    public void generate() throws IOException, UnsupportedOperationException {
        elementUtils = processingEnvironment.getElementUtils();
        typeUtils = processingEnvironment.getTypeUtils();
        realmObject = elementUtils.getTypeElement("io.realm.RealmObject").asType();
        realmList = typeUtils.getDeclaredType(elementUtils.getTypeElement("io.realm.RealmList"), typeUtils.getWildcardType(null, null));

        String qualifiedGeneratedClassName = String.format("%s.%s", Constants.REALM_PACKAGE_NAME, Utils.getProxyClassName(className));
        JavaFileObject sourceFile = processingEnvironment.getFiler().createSourceFile(qualifiedGeneratedClassName);
        JavaWriter writer = new JavaWriter(new BufferedWriter(sourceFile.openWriter()));

        // Set source code indent to 4 spaces
        writer.setIndent("    ");

        writer.emitPackage(Constants.REALM_PACKAGE_NAME)
                .emitEmptyLine();

        ArrayList<String> imports = new ArrayList<String>();
        imports.add("android.support.v11.util.JsonReader");
        imports.add("android.support.v11.util.JsonToken");
        imports.add("io.realm.RealmObject");
        imports.add("io.realm.exceptions.RealmException");
        imports.add("io.realm.internal.ColumnType");
        imports.add("io.realm.internal.Table");
        imports.add("io.realm.internal.TableOrView");
        imports.add("io.realm.internal.ImplicitTransaction");
        imports.add("io.realm.internal.LinkView");
        imports.add("io.realm.internal.android.JsonUtils");
        imports.add("java.io.IOException");
        imports.add("java.util.List");
        imports.add("java.util.Arrays");
        imports.add("java.util.Date");
        imports.add("java.util.Map");
        imports.add("java.util.HashMap");
        imports.add("org.json.JSONObject");
        imports.add("org.json.JSONException");
        imports.add("org.json.JSONArray");
        imports.add(metadata.getFullyQualifiedClassName());

        for (VariableElement field : metadata.getFields()) {
            String fieldTypeName = "";
            if (typeUtils.isAssignable(field.asType(), realmObject)) { // Links
                fieldTypeName = field.asType().toString();
            } else if (typeUtils.isAssignable(field.asType(), realmList)) { // LinkLists
                fieldTypeName = ((DeclaredType) field.asType()).getTypeArguments().get(0).toString();
            }
            if (fieldTypeName != "" && !imports.contains(fieldTypeName)) {
                imports.add(fieldTypeName);
            }
        }
        Collections.sort(imports);
        writer.emitImports(imports);
        writer.emitEmptyLine();

        // Begin the class definition
        writer.beginType(
                qualifiedGeneratedClassName, // full qualified name of the item to generate
                "class",                     // the type of the item
                EnumSet.of(Modifier.PUBLIC), // modifiers to apply
                className)                   // class to extend
                .emitEmptyLine();

        emitAccessors(writer);
        emitInitTableMethod(writer);
        emitValidateTableMethod(writer);
        emitGetFieldNamesMethod(writer);
        emitPopulateUsingJsonObjectMethod(writer);
        emitPopulateUsingJsonStreamMethod(writer);
        emitCopyOrUpdateMethod(writer);
        emitCopyMethod(writer);
        emitUpdateMethod(writer);
        emitToStringMethod(writer);
        emitHashcodeMethod(writer);
        emitEqualsMethod(writer);

        // End the class definition
        writer.endType();
        writer.close();
    }

    private void emitAccessors(JavaWriter writer) throws IOException {
        for (VariableElement field : metadata.getFields()) {
            String fieldName = field.getSimpleName().toString();
            String fieldTypeCanonicalName = field.asType().toString();

            if (JAVA_TO_REALM_TYPES.containsKey(fieldTypeCanonicalName)) {
                /**
                 * Primitives and boxed types
                 */
                String realmType = JAVA_TO_REALM_TYPES.get(fieldTypeCanonicalName);
                String castingType = CASTING_TYPES.get(fieldTypeCanonicalName);

                // Getter
                writer.emitAnnotation("Override");
                writer.beginMethod(fieldTypeCanonicalName, metadata.getGetter(fieldName), EnumSet.of(Modifier.PUBLIC));
                writer.emitStatement(
                        "realm.checkIfValid()"
                );
                writer.emitStatement(
                        "return (%s) row.get%s(Realm.columnIndices.get(\"%s\").get(\"%s\"))",
                        fieldTypeCanonicalName, realmType, className, fieldName);
                writer.endMethod();
                writer.emitEmptyLine();

                // Setter
                writer.emitAnnotation("Override");
                writer.beginMethod("void", metadata.getSetter(fieldName), EnumSet.of(Modifier.PUBLIC), fieldTypeCanonicalName, "value");
                writer.emitStatement(
                        "realm.checkIfValid()"
                );
                writer.emitStatement(
                        "row.set%s(Realm.columnIndices.get(\"%s\").get(\"%s\"), (%s) value)",
                        realmType, className, fieldName, castingType);
                writer.endMethod();
            } else if (typeUtils.isAssignable(field.asType(), realmObject)) {
                /**
                 * Links
                 */

                // Getter
                writer.emitAnnotation("Override");
                writer.beginMethod(fieldTypeCanonicalName, metadata.getGetter(fieldName), EnumSet.of(Modifier.PUBLIC));
                writer.beginControlFlow("if (row.isNullLink(Realm.columnIndices.get(\"%s\").get(\"%s\")))", className, fieldName);
                writer.emitStatement("return null");
                writer.endControlFlow();
                writer.emitStatement(
                        "return realm.get(%s.class, row.getLink(Realm.columnIndices.get(\"%s\").get(\"%s\")))",
                        fieldTypeCanonicalName, className, fieldName);
                writer.endMethod();
                writer.emitEmptyLine();

                // Setter
                writer.emitAnnotation("Override");
                writer.beginMethod("void", metadata.getSetter(fieldName), EnumSet.of(Modifier.PUBLIC), fieldTypeCanonicalName, "value");
                writer.beginControlFlow("if (value == null)");
                writer.emitStatement("row.nullifyLink(Realm.columnIndices.get(\"%s\").get(\"%s\"))", className, fieldName);
                writer.emitStatement("return");
                writer.endControlFlow();
                writer.emitStatement("row.setLink(Realm.columnIndices.get(\"%s\").get(\"%s\"), value.row.getIndex())", className, fieldName);
                writer.endMethod();
            } else if (typeUtils.isAssignable(field.asType(), realmList)) {
                /**
                 * LinkLists
                 */
                String genericType = Utils.getGenericType(field);

                // Getter
                writer.emitAnnotation("Override");
                writer.beginMethod(fieldTypeCanonicalName, metadata.getGetter(fieldName), EnumSet.of(Modifier.PUBLIC));
                writer.emitStatement(
                        "return new RealmList<%s>(%s.class, row.getLinkList(Realm.columnIndices.get(\"%s\").get(\"%s\")), realm)",
                        genericType, genericType, className, fieldName);
                writer.endMethod();
                writer.emitEmptyLine();

                // Setter
                writer.emitAnnotation("Override");
                writer.beginMethod("void", metadata.getSetter(fieldName), EnumSet.of(Modifier.PUBLIC), fieldTypeCanonicalName, "value");
                writer.emitStatement("LinkView links = row.getLinkList(Realm.columnIndices.get(\"%s\").get(\"%s\"))", className, fieldName);
                writer.beginControlFlow("if (value == null)");
                writer.emitStatement("return"); // TODO: delete all the links instead
                writer.endControlFlow();
                writer.beginControlFlow("for (RealmObject linkedObject : (RealmList<? extends RealmObject>) value)");
                writer.emitStatement("links.add(linkedObject.row.getIndex())");
                writer.endControlFlow();
                writer.endMethod();
            } else {
                throw new UnsupportedOperationException(
                        String.format("Type %s of field %s is not supported", fieldTypeCanonicalName, fieldName));
            }
            writer.emitEmptyLine();
        }
    }

    private void emitInitTableMethod(JavaWriter writer) throws IOException {
        writer.beginMethod(
                "Table", // Return type
                "initTable", // Method name
                EnumSet.of(Modifier.PUBLIC, Modifier.STATIC), // Modifiers
                "ImplicitTransaction", "transaction"); // Argument type & argument name

        writer.beginControlFlow("if(!transaction.hasTable(\"" + Constants.TABLE_PREFIX + this.className + "\"))");
        writer.emitStatement("Table table = transaction.getTable(\"%s%s\")", Constants.TABLE_PREFIX, this.className);

        // For each field generate corresponding table index constant
        for (VariableElement field : metadata.getFields()) {
            String fieldName = field.getSimpleName().toString();
            String fieldTypeCanonicalName = field.asType().toString();
            String fieldTypeSimpleName = Utils.getFieldTypeSimpleName(field);

            if (JAVA_TO_REALM_TYPES.containsKey(fieldTypeCanonicalName)) {
                writer.emitStatement("table.addColumn(%s, \"%s\")",
                        JAVA_TO_COLUMN_TYPES.get(fieldTypeCanonicalName),
                        fieldName);
            } else if (typeUtils.isAssignable(field.asType(), realmObject)) {
                writer.beginControlFlow("if (!transaction.hasTable(\"%s%s\"))", Constants.TABLE_PREFIX, fieldTypeSimpleName);
                writer.emitStatement("%s%s.initTable(transaction)", fieldTypeSimpleName, Constants.PROXY_SUFFIX);
                writer.endControlFlow();
                writer.emitStatement("table.addColumnLink(ColumnType.LINK, \"%s\", transaction.getTable(\"%s%s\"))",
                        fieldName, Constants.TABLE_PREFIX, fieldTypeSimpleName);
            } else if (typeUtils.isAssignable(field.asType(), realmList)) {
                String genericType = Utils.getGenericType(field);
                writer.beginControlFlow("if (!transaction.hasTable(\"%s%s\"))", Constants.TABLE_PREFIX, genericType);
                writer.emitStatement("%s%s.initTable(transaction)", genericType, Constants.PROXY_SUFFIX);
                writer.endControlFlow();
                writer.emitStatement("table.addColumnLink(ColumnType.LINK_LIST, \"%s\", transaction.getTable(\"%s%s\"))",
                        fieldName, Constants.TABLE_PREFIX, genericType);
            }
        }

        for (VariableElement field : metadata.getIndexedFields()) {
            String fieldName = field.getSimpleName().toString();
            writer.emitStatement("table.setIndex(table.getColumnIndex(\"%s\"))", fieldName);
        }

        if (metadata.hasPrimaryKey()) {
            String fieldName = metadata.getPrimaryKey().getSimpleName().toString();
            writer.emitStatement("table.setPrimaryKey(\"%s\")", fieldName);
        } else {
            writer.emitStatement("table.setPrimaryKey(\"\")");
        }

        writer.emitStatement("return table");
        writer.endControlFlow();
        writer.emitStatement("return transaction.getTable(\"%s%s\")", Constants.TABLE_PREFIX, this.className);
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitValidateTableMethod(JavaWriter writer) throws IOException {
        writer.beginMethod(
                "void", // Return type
                "validateTable", // Method name
                EnumSet.of(Modifier.PUBLIC, Modifier.STATIC), // Modifiers
                "ImplicitTransaction", "transaction"); // Argument type & argument name

        writer.beginControlFlow("if(transaction.hasTable(\"" + Constants.TABLE_PREFIX + this.className + "\"))");
        writer.emitStatement("Table table = transaction.getTable(\"%s%s\")", Constants.TABLE_PREFIX, this.className);

        // verify number of columns
        writer.beginControlFlow("if(table.getColumnCount() != " + metadata.getFields().size() + ")");
        writer.emitStatement("throw new IllegalStateException(\"Column count does not match\")");
        writer.endControlFlow();

        // create type dictionary for lookup
        writer.emitStatement("Map<String, ColumnType> columnTypes = new HashMap<String, ColumnType>()");
        writer.beginControlFlow("for(long i = 0; i < " + metadata.getFields().size() + "; i++)");
        writer.emitStatement("columnTypes.put(table.getColumnName(i), table.getColumnType(i))");
        writer.endControlFlow();

        // For each field verify there is a corresponding column
        for (VariableElement field : metadata.getFields()) {
            String fieldName = field.getSimpleName().toString();
            String fieldTypeCanonicalName = field.asType().toString();
            String fieldTypeSimpleName = Utils.getFieldTypeSimpleName(field);

            if (JAVA_TO_REALM_TYPES.containsKey(fieldTypeCanonicalName)) {
                // make sure types align
                writer.beginControlFlow("if (!columnTypes.containsKey(\"%s\"))", fieldName);
                writer.emitStatement("throw new IllegalStateException(\"Missing column '%s'\")", fieldName);
                writer.endControlFlow();
                writer.beginControlFlow("if (columnTypes.get(\"%s\") != %s)", fieldName, JAVA_TO_COLUMN_TYPES.get(fieldTypeCanonicalName));
                writer.emitStatement("throw new IllegalStateException(\"Invalid type '%s' for column '%s'\")",
                        fieldTypeSimpleName, fieldName);
                writer.endControlFlow();
            } else if (typeUtils.isAssignable(field.asType(), realmObject)) { // Links
                writer.beginControlFlow("if (!columnTypes.containsKey(\"%s\"))", fieldName);
                writer.emitStatement("throw new IllegalStateException(\"Missing column '%s'\")", fieldName);
                writer.endControlFlow();
                writer.beginControlFlow("if (columnTypes.get(\"%s\") != ColumnType.LINK)", fieldName);
                writer.emitStatement("throw new IllegalStateException(\"Invalid type '%s' for column '%s'\")",
                        fieldTypeSimpleName, fieldName);
                writer.endControlFlow();
                writer.beginControlFlow("if (!transaction.hasTable(\"%s%s\"))", Constants.TABLE_PREFIX, fieldTypeSimpleName);
                writer.emitStatement("throw new IllegalStateException(\"Missing table '%s%s' for column '%s'\")",
                        Constants.TABLE_PREFIX, fieldTypeSimpleName, fieldName);
                writer.endControlFlow();
                // TODO: Replace with a proper comparison
//                writer.emitStatement("Table table_%d = transaction.getTable(\"%s%s\")", columnNumber, TABLE_PREFIX, fieldTypeName);
//                writer.beginControlFlow("if (table.getLinkTarget(%d).equals(table_%d))", columnNumber, columnNumber);
//                writer.emitStatement("throw new IllegalStateException(\"Mismatching link tables for column '%s'\")",
//                        fieldName);
//                writer.endControlFlow();
            } else if (typeUtils.isAssignable(field.asType(), realmList)) { // Link Lists
                String genericType = Utils.getGenericType(field);
                writer.beginControlFlow("if(!columnTypes.containsKey(\"%s\"))", fieldName);
                writer.emitStatement("throw new IllegalStateException(\"Missing column '%s'\")", fieldName);
                writer.endControlFlow();
                writer.beginControlFlow("if(columnTypes.get(\"%s\") != ColumnType.LINK_LIST)", fieldName);
                writer.emitStatement("throw new IllegalStateException(\"Invalid type '%s' for column '%s'\")",
                        genericType, fieldName);
                writer.endControlFlow();
                writer.beginControlFlow("if (!transaction.hasTable(\"%s%s\"))", Constants.TABLE_PREFIX, genericType);
                writer.emitStatement("throw new IllegalStateException(\"Missing table '%s%s' for column '%s'\")",
                        Constants.TABLE_PREFIX, genericType, fieldName);
                writer.endControlFlow();
                // TODO: Replace with a proper comparison
//                writer.emitStatement("Table table_%d = transaction.getTable(\"%s%s\")", columnNumber, TABLE_PREFIX, genericType);
//                writer.beginControlFlow("if (table.getLinkTarget(%d).equals(table_%d))", columnNumber, columnNumber);
//                writer.emitStatement("throw new IllegalStateException(\"Mismatching link list tables for column '%s'\")",
//                        fieldName);
//                writer.endControlFlow();
            }
        }
        writer.endControlFlow();
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitGetFieldNamesMethod(JavaWriter writer) throws IOException {
        writer.beginMethod("List<String>", "getFieldNames", EnumSet.of(Modifier.PUBLIC, Modifier.STATIC));
        StringBuilder stringBuilder = new StringBuilder();
        Iterator<VariableElement> iterator = metadata.getFields().iterator();
        while (iterator.hasNext()) {
            String fieldName = iterator.next().getSimpleName().toString();
            stringBuilder.append("\"");
            stringBuilder.append(fieldName);
            stringBuilder.append("\"");
            if (iterator.hasNext()) {
                stringBuilder.append(", ");
            }
        }
        String statementSection = stringBuilder.toString();
        writer.emitStatement("return Arrays.asList(%s)", statementSection);
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitCopyOrUpdateMethod(JavaWriter writer) throws IOException {
        writer.beginMethod(
                className, // Return type
                "copyOrUpdate", // Method name
                EnumSet.of(Modifier.PUBLIC, Modifier.STATIC), // Modifiers
                "Realm", "realm", className, "object", "boolean", "update", "Map<RealmObject,RealmObject>", "cache" // Argument type & argument name
        );

        if (!metadata.hasPrimaryKey()) {
            writer.emitStatement("return copy(realm, object, update, cache)");
        } else {
            writer
                .emitStatement("%s realmObject = null", className)
                .emitStatement("boolean canUpdate = update")
                .beginControlFlow("if (canUpdate)")
                    .emitStatement("Table table = realm.getTable(%s.class)", className)
                    .emitStatement("long pkColumnIndex = table.getPrimaryKey()");

            if (Utils.isString(metadata.getPrimaryKey())) {
                writer.emitStatement("long rowIndex = table.findFirstString(pkColumnIndex, object.%s())", metadata.getPrimaryKeyGetter());
            } else {
                writer.emitStatement("long rowIndex = table.findFirstLong(pkColumnIndex, object.%s())", metadata.getPrimaryKeyGetter());
            }

            writer
                .beginControlFlow("if (rowIndex != TableOrView.NO_MATCH)")
                    .emitStatement("realmObject = new %s()", Utils.getProxyClassName(className))
                    .emitStatement("realmObject.realm = realm")
                    .emitStatement("realmObject.row = table.getRow(rowIndex)")
                    .emitStatement("cache.put(object, realmObject)")
                .nextControlFlow("else")
                    .emitStatement("canUpdate = false")
                .endControlFlow();

            writer.endControlFlow();

            writer
                .emitEmptyLine()
                .beginControlFlow("if (canUpdate)")
                    .emitStatement("return update(realm, realmObject, object, cache)")
                .nextControlFlow("else")
                    .emitStatement("return copy(realm, object, update, cache)")
                .endControlFlow();
        }

        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitCopyMethod(JavaWriter writer) throws IOException {
        writer.beginMethod(
                className, // Return type
                "copy", // Method name
                EnumSet.of(Modifier.PUBLIC, Modifier.STATIC), // Modifiers
                "Realm", "realm", className, "newObject", "boolean", "update", "Map<RealmObject,RealmObject>", "cache"); // Argument type & argument name

        writer.emitStatement("%s realmObject = realm.createObject(%s.class)", className, className);
        writer.emitStatement("cache.put(newObject, realmObject)");
        for (VariableElement field : metadata.getFields()) {
            String fieldName = field.getSimpleName().toString();
            String fieldType = field.asType().toString();
            if (typeUtils.isAssignable(field.asType(), realmObject)) {
                writer
                    .emitEmptyLine()
                    .emitStatement("%s %sObj = newObject.%s()", fieldType, fieldName, metadata.getGetter(fieldName))
                    .beginControlFlow("if (%sObj != null)", fieldName)
                        .emitStatement("%s cache%s = (%s) cache.get(%sObj)", fieldType, fieldName, fieldType, fieldName)
                        .beginControlFlow("if (cache%s != null)", fieldName)
                            .emitStatement("realmObject.%s(cache%s)", metadata.getSetter(fieldName), fieldName)
                        .nextControlFlow("else")
                            .emitStatement("realmObject.%s(%s.copyOrUpdate(realm, %sObj, update, cache))",
                                    metadata.getSetter(fieldName),
                                    Utils.getProxyClassSimpleName(field),
                                    fieldName)
                        .endControlFlow()
                    .endControlFlow();
            } else if (typeUtils.isAssignable(field.asType(), realmList)) {
                writer
                    .emitEmptyLine()
                    .emitStatement("RealmList<%s> %sList = newObject.%s()", Utils.getGenericType(field), fieldName, metadata.getGetter(fieldName))
                    .beginControlFlow("if (%sList != null)", fieldName)
                        .emitStatement("RealmList<%s> %sRealmList = realmObject.%s()", Utils.getGenericType(field), fieldName, metadata.getGetter(fieldName))
                        .beginControlFlow("for (int i = 0; i < %sList.size(); i++)", fieldName)
                                .emitStatement("%s %sItem = %sList.get(i)", Utils.getGenericType(field), fieldName, fieldName)
                                .emitStatement("%s cache%s = (%s) cache.get(%sItem)", Utils.getGenericType(field), fieldName, Utils.getGenericType(field), fieldName)
                                .beginControlFlow("if (cache%s != null)", fieldName)
                                        .emitStatement("%sRealmList.add(cache%s)", fieldName, fieldName)
                                .nextControlFlow("else")
                                        .emitStatement("%sRealmList.add(%s.copyOrUpdate(realm, %sList.get(i), update, cache))", fieldName, Utils.getProxyClassSimpleName(field), fieldName)
                                .endControlFlow()
                        .endControlFlow()
                    .endControlFlow()
                    .emitEmptyLine();

            } else {
                if (NULLABLE_JAVA_TYPES.containsKey(fieldType)) {
                    writer.emitStatement("realmObject.%s(newObject.%s() != null ? newObject.%s() : %s)",
                            metadata.getSetter(fieldName),
                            metadata.getGetter(fieldName),
                            metadata.getGetter(fieldName),
                            NULLABLE_JAVA_TYPES.get(fieldType));
                } else {
                    writer.emitStatement("realmObject.%s(newObject.%s())", metadata.getSetter(fieldName), metadata.getGetter(fieldName));
                }
            }
        }

        writer.emitStatement("return realmObject");
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitUpdateMethod(JavaWriter writer) throws IOException {

        writer.beginMethod(
                className, // Return type
                "update", // Method name
                EnumSet.of(Modifier.STATIC), // Modifiers
                "Realm", "realm", className, "realmObject", className, "newObject", "Map<RealmObject, RealmObject>", "cache"); // Argument type & argument name

        for (VariableElement field : metadata.getFields()) {
            String fieldName = field.getSimpleName().toString();
            if (typeUtils.isAssignable(field.asType(), realmObject)) {
                writer
                    .emitStatement("%s %sObj = newObject.%s()", Utils.getFieldTypeSimpleName(field), fieldName, metadata.getGetter(fieldName))
                    .beginControlFlow("if (%sObj != null)", fieldName)
                        .emitStatement("%s cache%s = (%s) cache.get(%sObj)", Utils.getFieldTypeSimpleName(field), fieldName, Utils.getFieldTypeSimpleName(field), fieldName)
                        .beginControlFlow("if (cache%s != null)", fieldName)
                            .emitStatement("realmObject.%s(cache%s)", metadata.getSetter(fieldName), fieldName)
                        .nextControlFlow("else")
                            .emitStatement("realmObject.%s(%s.copyOrUpdate(realm, %sObj, true, cache))",
                                    metadata.getSetter(fieldName),
                                    Utils.getProxyClassSimpleName(field),
                                    fieldName,
                                    Utils.getFieldTypeSimpleName(field)
                            )
                        .endControlFlow()
                    .nextControlFlow("else")
                        .emitStatement("realmObject.%s(null)", metadata.getSetter(fieldName))
                    .endControlFlow();
            } else if (typeUtils.isAssignable(field.asType(), realmList)) {
                writer
                    .emitStatement("RealmList<%s> %sList = newObject.%s()", Utils.getGenericType(field), fieldName, metadata.getGetter(fieldName))
                    .emitStatement("RealmList<%s> %sRealmList = realmObject.%s()", Utils.getGenericType(field), fieldName, metadata.getGetter(fieldName))
                    .emitStatement("%sRealmList.clear()", fieldName)
                    .beginControlFlow("if (%sList != null)", fieldName)
                        .beginControlFlow("for (int i = 0; i < %sList.size(); i++)", fieldName)
                            .emitStatement("%s %sItem = %sList.get(i)", Utils.getGenericType(field), fieldName, fieldName)
                            .emitStatement("%s cache%s = (%s) cache.get(%sItem)", Utils.getGenericType(field), fieldName, Utils.getGenericType(field), fieldName)
                            .beginControlFlow("if (cache%s != null)", fieldName)
                                .emitStatement("%sRealmList.add(cache%s)", fieldName, fieldName)
                            .nextControlFlow("else")
                                .emitStatement("%sRealmList.add(%s.copyOrUpdate(realm, %sList.get(i), true, cache))", fieldName, Utils.getProxyClassSimpleName(field), fieldName)
                            .endControlFlow()
                        .endControlFlow()
                    .endControlFlow();

            } else {
                if (field == metadata.getPrimaryKey()) {
                    continue;
                }

                String fieldType = field.asType().toString();
                if (NULLABLE_JAVA_TYPES.containsKey(fieldType)) {
                    writer.emitStatement("realmObject.%s(newObject.%s() != null ? newObject.%s() : %s)",
                            metadata.getSetter(fieldName),
                            metadata.getGetter(fieldName),
                            metadata.getGetter(fieldName),
                            NULLABLE_JAVA_TYPES.get(fieldType));
                } else {
                    writer.emitStatement("realmObject.%s(newObject.%s())", metadata.getSetter(fieldName), metadata.getGetter(fieldName));
                }
            }
        }

        writer.emitStatement("return realmObject");
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitToStringMethod(JavaWriter writer) throws IOException {
        writer.emitAnnotation("Override");
        writer.beginMethod("String", "toString", EnumSet.of(Modifier.PUBLIC));
        writer.beginControlFlow("if (!isValid())");
        writer.emitStatement("return \"Invalid object\"");
        writer.endControlFlow();
        writer.emitStatement("StringBuilder stringBuilder = new StringBuilder(\"%s = [\")", className);
        List<VariableElement> fields = metadata.getFields();
        for (int i = 0; i < fields.size(); i++) {
            VariableElement field = fields.get(i);
            String fieldName = field.getSimpleName().toString();

            writer.emitStatement("stringBuilder.append(\"{%s:\")", fieldName);
            if (typeUtils.isAssignable(field.asType(), realmObject)) {
                String fieldTypeSimpleName = Utils.getFieldTypeSimpleName(field);
                writer.emitStatement(
                        "stringBuilder.append(%s() != null ? \"%s\" : \"null\")",
                        metadata.getGetter(fieldName),
                        fieldTypeSimpleName
                );
            } else if (typeUtils.isAssignable(field.asType(), realmList)) {
                String genericType = Utils.getGenericType(field);
                writer.emitStatement("stringBuilder.append(\"RealmList<%s>[\").append(%s().size()).append(\"]\")",
                        genericType,
                        metadata.getGetter(fieldName));
            } else {
                writer.emitStatement("stringBuilder.append(%s())", metadata.getGetter(fieldName));
            }
            writer.emitStatement("stringBuilder.append(\"}\")");

            if (i < fields.size() - 1) {
                writer.emitStatement("stringBuilder.append(\",\")");
            }
        }

        writer.emitStatement("stringBuilder.append(\"]\")");
        writer.emitStatement("return stringBuilder.toString()");
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitHashcodeMethod(JavaWriter writer) throws IOException {
        writer.emitAnnotation("Override");
        writer.beginMethod("int", "hashCode", EnumSet.of(Modifier.PUBLIC));
        writer.emitStatement("String realmName = realm.getPath()");
        writer.emitStatement("String tableName = row.getTable().getName()");
        writer.emitStatement("long rowIndex = row.getIndex()");
        writer.emitEmptyLine();
        writer.emitStatement("int result = 17");
        writer.emitStatement("result = 31 * result + ((realmName != null) ? realmName.hashCode() : 0)");
        writer.emitStatement("result = 31 * result + ((tableName != null) ? tableName.hashCode() : 0)");
        writer.emitStatement("result = 31 * result + (int) (rowIndex ^ (rowIndex >>> 32))");
        writer.emitStatement("return result");
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitEqualsMethod(JavaWriter writer) throws IOException {
        String proxyClassName = className + Constants.PROXY_SUFFIX;
        writer.emitAnnotation("Override");
        writer.beginMethod("boolean", "equals", EnumSet.of(Modifier.PUBLIC), "Object", "o");
        writer.emitStatement("if (this == o) return true");
        writer.emitStatement("if (o == null || getClass() != o.getClass()) return false");
        writer.emitStatement("%s a%s = (%s)o", proxyClassName, className, proxyClassName);  // FooRealmProxy aFoo = (FooRealmProxy)o
        writer.emitEmptyLine();
        writer.emitStatement("String path = realm.getPath()");
        writer.emitStatement("String otherPath = a%s.realm.getPath()", className);
        writer.emitStatement("if (path != null ? !path.equals(otherPath) : otherPath != null) return false;");
        writer.emitEmptyLine();
        writer.emitStatement("String tableName = row.getTable().getName()");
        writer.emitStatement("String otherTableName = a%s.row.getTable().getName()", className);
        writer.emitStatement("if (tableName != null ? !tableName.equals(otherTableName) : otherTableName != null) return false");
        writer.emitEmptyLine();
        writer.emitStatement("if (row.getIndex() != a%s.row.getIndex()) return false", className);
        writer.emitEmptyLine();
        writer.emitStatement("return true");
        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitPopulateUsingJsonObjectMethod(JavaWriter writer) throws IOException {
        writer.beginMethod(
                "void",
                "populateUsingJsonObject",
                EnumSet.of(Modifier.PUBLIC, Modifier.STATIC),
                Arrays.asList(className, "obj", "JSONObject", "json"),
                Arrays.asList("JSONException"));

        writer.emitStatement("boolean standalone = obj.realm == null");
        for (VariableElement field : metadata.getFields()) {
            String fieldName = field.getSimpleName().toString();
            String qualifiedFieldType = field.asType().toString();
            if (typeUtils.isAssignable(field.asType(), realmObject)) {
                RealmJsonTypeHelper.emitFillRealmObjectWithJsonValue(
                        metadata.getSetter(fieldName),
                        fieldName,
                        qualifiedFieldType,
                        Utils.getProxyClassSimpleName(field),
                        writer);

            } else if (typeUtils.isAssignable(field.asType(), realmList)) {
                RealmJsonTypeHelper.emitFillRealmListWithJsonValue(
                        metadata.getGetter(fieldName),
                        metadata.getSetter(fieldName),
                        fieldName,
                        ((DeclaredType) field.asType()).getTypeArguments().get(0).toString(),
                        Utils.getProxyClassSimpleName(field),
                        writer);

            } else {
                RealmJsonTypeHelper.emitFillJavaTypeWithJsonValue(
                        metadata.getSetter(fieldName),
                        fieldName,
                        qualifiedFieldType,
                        writer);
            }
        }

        writer.endMethod();
        writer.emitEmptyLine();
    }

    private void emitPopulateUsingJsonStreamMethod(JavaWriter writer) throws IOException {
        writer.beginMethod(
                "void",
                "populateUsingJsonStream",
                EnumSet.of(Modifier.PUBLIC, Modifier.STATIC),
                Arrays.asList(className, "obj", "JsonReader", "reader"),
                Arrays.asList("IOException"));

        writer.emitStatement("boolean standalone = obj.realm == null");
        writer.emitStatement("reader.beginObject()");
        writer.beginControlFlow("while (reader.hasNext())");
        writer.emitStatement("String name = reader.nextName()");

        List<VariableElement> fields = metadata.getFields();
        for (int i = 0; i < fields.size(); i++) {
            VariableElement field = fields.get(i);
            String fieldName = field.getSimpleName().toString();
            String qualifiedFieldType = field.asType().toString();

            if (i == 0) {
                writer.beginControlFlow("if (name.equals(\"%s\") && reader.peek() != JsonToken.NULL)", fieldName);
            } else {
                writer.nextControlFlow("else if (name.equals(\"%s\")  && reader.peek() != JsonToken.NULL)", fieldName);
            }
            if (typeUtils.isAssignable(field.asType(), realmObject)) {
                RealmJsonTypeHelper.emitFillRealmObjectFromStream(
                        metadata.getSetter(fieldName),
                        fieldName,
                        qualifiedFieldType,
                        Utils.getProxyClassSimpleName(field),
                        writer);

            } else if (typeUtils.isAssignable(field.asType(), realmList)) {
                RealmJsonTypeHelper.emitFillRealmListFromStream(
                        metadata.getGetter(fieldName),
                        metadata.getSetter(fieldName),
                        ((DeclaredType) field.asType()).getTypeArguments().get(0).toString(),
                        Utils.getProxyClassSimpleName(field),
                        writer);

            } else {
                RealmJsonTypeHelper.emitFillJavaTypeFromStream(
                        metadata.getSetter(fieldName),
                        fieldName,
                        qualifiedFieldType,
                        writer);
            }
        }

        if (fields.size() > 0) {
            writer.nextControlFlow("else");
            writer.emitStatement("reader.skipValue()");
            writer.endControlFlow();
        }
        writer.endControlFlow();
        writer.emitStatement("reader.endObject()");
        writer.endMethod();
        writer.emitEmptyLine();
    }
}
