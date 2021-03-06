/*
 * Copyright 2012 and beyond, Juan Luis Paz
 *
 * This file is part of Uaithne.
 *
 * Uaithne is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Uaithne is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Uaithne. If not, see <http://www.gnu.org/licenses/>.
 */
package org.uaithne.generator.processors.database.myBatis;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.uaithne.annotations.myBatis.MyBatisBackend;
import org.uaithne.annotations.myBatis.MyBatisBackendConfiguration;
import org.uaithne.annotations.myBatis.MyBatisCustomSqlStatementId;
import org.uaithne.annotations.myBatis.MyBatisMapper;
import org.uaithne.annotations.sql.CustomSqlQuery;
import org.uaithne.generator.commons.*;
import org.uaithne.generator.processors.database.QueryGenerator;
import org.uaithne.generator.processors.database.QueryGeneratorConfiguration;
import org.uaithne.generator.templates.operations.myBatis.MyBatisTemplate;

@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes("org.uaithne.annotations.myBatis.MyBatisMapper")
public class MyBatisMapperProcessor extends TemplateProcessor {
    
    @Override
    public boolean doProcess(Set<? extends TypeElement> set, RoundEnvironment re) {
        for (Element element : re.getElementsAnnotatedWith(MyBatisMapper.class)) {
            if (element.getKind() == ElementKind.CLASS) {
                MyBatisMapper myBatisMapper = element.getAnnotation(MyBatisMapper.class);
                if (myBatisMapper != null) {
                    MyBatisBackendConfiguration[] configurations = myBatisMapper.backendConfigurations();
                    if (configurations.length <= 0) {
                        configurations = getGenerationInfo().getMyBatisBackends();
                    }
                    for (MyBatisBackendConfiguration configuration : configurations) {
                        try {
                            process(re, element, configuration);
                        } catch(Exception ex) {
                            Logger.getLogger(MyBatisMapperProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        }
        return true; // no further processing of this annotation type
    }

    //<editor-fold defaultstate="collapsed" desc="Process">
    public void process(RoundEnvironment re, Element element, MyBatisBackendConfiguration configuration) {
        TypeElement classElement = (TypeElement) element;
        
        ExecutorModuleInfo module = getGenerationInfo().getExecutorModuleByRealName(classElement);
        if (module == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "For create a myBatis mapper the annotated class must be an operation module", element);
            return;
        }

        String packageName = NamesGenerator.createGenericPackageName(classElement, configuration.subPackageName());
        String name = configuration.mapperPrefix() + module.getNameUpper() + configuration.mapperSuffix();

        String namespace;
        if (packageName == null || packageName.isEmpty()) {
            namespace = name;
        } else {
            namespace = packageName + "." + name;
        }
        
        MyBatisBackend backend = configuration.backend();
        boolean useGeneratedKeys = backend.useGeneratedKeys();
        boolean useAutoIncrementId = configuration.useAutoIncrementId().solve(backend.useAutoIncrementId());
        
        QueryGeneratorConfiguration config = new QueryGeneratorConfiguration();
        config.setProcessingEnv(processingEnv);
        config.setUseAutoIncrementId(useAutoIncrementId);
        config.setUseGeneratedKeys(useGeneratedKeys);
        config.setIdSecuenceNameTemplate(configuration.idSecuenceNameTemplate());
        config.setModule(module);
        config.setPackageName(packageName);
        config.setName(name);
        config.loadCustomJdbcTypeMap(configuration.defaultJdbcTypes());
        config.setDefaultValue(configuration.defaultValue());
        config.setApplicationParameter(getGenerationInfo().getApplicationParameter());
        
        QueryGenerator sqlGenerator = backend.getGenerator();
        sqlGenerator.setConfiguration(config);
        
        boolean useParameterType = configuration.useParameterType();

        Writer writer = null;
        boolean hasUnimplementedOperations = false;
        try {
            FileObject fo = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, packageName, name + ".xml", element);
            writer = fo.openWriter();
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' 'http://mybatis.org/dtd/mybatis-3-mapper.dtd'>\n");
            writer.write("<mapper namespace='");
            writer.write(namespace);
            writer.write("'>\n\n");
            sqlGenerator.begin();
            HashSet<EntityInfo> entitiesWithLastInsertedId = new HashSet<EntityInfo>();
            for (OperationInfo operation : module.getOperations()) {
                processOperation(sqlGenerator, operation, namespace, writer, entitiesWithLastInsertedId, useParameterType);
                hasUnimplementedOperations = hasUnimplementedOperations || operation.isManually();
            }
            sqlGenerator.end();
            writer.write("</mapper>");
        } catch (IOException ex) {
            Logger.getLogger(MyBatisMapperProcessor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(MyBatisMapperProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        processClassTemplate(new MyBatisTemplate(module, packageName, name, namespace, sqlGenerator.useAliasInOrderByTranslation(), hasUnimplementedOperations), element);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Process operation">
    public void processOperation(QueryGenerator sqlGenerator, OperationInfo operation, String namespace, Writer writer, HashSet<EntityInfo> entitiesWithLastInsertedId, boolean useParameterType) throws IOException {
        if (operation.isManually()) {
            return;
        }
        MyBatisCustomSqlStatementId myBatisCustomSqlStatement = operation.getAnnotation(MyBatisCustomSqlStatementId.class);
        if (myBatisCustomSqlStatement != null) {
            String statementId = myBatisCustomSqlStatement.value();
            if (statementId.trim().isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "You must provide a valid mybatis statement id", operation.getElement());
            }
            operation.setQueryId(statementId);
            if (operation.getOperationKind() == OperationKind.SELECT_PAGE) {
                String countStatementId = myBatisCustomSqlStatement.countStatementId();
                if (countStatementId.trim().isEmpty()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "You must provide a valid mybatis statement id for count the numbers of the rows", operation.getElement());
                }
                operation.setCountQueryId(countStatementId);
            } else {
                String countStatementId = myBatisCustomSqlStatement.countStatementId();
                if (!countStatementId.isEmpty()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "For this kind of operation is not allowed provide a mybatis statement id for count the numbers of the rows", operation.getElement());
                }
            }
            if (operation.getOperationKind() == OperationKind.SAVE || operation.getOperationKind() == OperationKind.JUST_SAVE) {
                String saveInsertStatementId = myBatisCustomSqlStatement.saveInsertStatementId();
                if (saveInsertStatementId.trim().isEmpty()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "You must provide a valid mybatis statement id for insert a new row in a save operation", operation.getElement());
                }
                operation.setSaveInsertQueryId(saveInsertStatementId);
            } else {
                String saveInsertStatementId = myBatisCustomSqlStatement.saveInsertStatementId();
                if (!saveInsertStatementId.isEmpty()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "For this kind of operation is not allowed provide a mybatis statement id for insert value in a save operation", operation.getElement());
                }
            }
            return;
        }
        EntityInfo entity = operation.getEntity();
        if (entity != null) {
            entity = entity.getCombined();
        }
        
        sqlGenerator.getConfiguration().setUsedApplicationParameters(null);
        
        boolean isProcedureInvocation = false;
        CustomSqlQuery customSqlQuery = operation.getAnnotation(CustomSqlQuery.class);
        if (customSqlQuery != null) {
            isProcedureInvocation = customSqlQuery.isProcedureInvocation().solve(operation.getOperationKind().isComplexCall());
        }

        InsertedIdOrigin defaultIdOrigin;
        if (getGenerationInfo().isSetResultingIdInOperationEnabled()) {
            defaultIdOrigin = InsertedIdOrigin.FIELD;
        } else {
            defaultIdOrigin = InsertedIdOrigin.RETAINED;
        }

        switch (operation.getOperationKind()) {
            case CUSTOM: {
            }
            break;
            case SELECT_COUNT: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getSelectCountQuery(operation);
                if (query != null) {
                    writeSelect(writer,
                            operation.getMethodName(),
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            operation.getReturnDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case SELECT_ONE: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getSelectOneQuery(operation);
                if (query != null) {
                    writeSelect(writer,
                            operation.getMethodName(),
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            operation.getReturnDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case SELECT_MANY: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getSelectManyQuery(operation);
                if (query != null) {
                    writeSelect(writer,
                            operation.getMethodName(),
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            operation.getOneItemReturnDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case SELECT_PAGE: {
                operation.setQueryId(namespace + "." + operation.getMethodName() + "Page");
                operation.setCountQueryId(namespace + "." + operation.getMethodName() + "Count");
                String[] query = sqlGenerator.getSelectPageQuery(operation);
                if (query != null) {
                    writeSelect(writer,
                            operation.getMethodName() + "Page",
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            operation.getOneItemReturnDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
                query = sqlGenerator.getSelectPageCountQuery(operation);
                if (query != null) {
                    writeSelect(writer,
                            operation.getMethodName() + "Count",
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            DataTypeInfo.PAGE_INFO_DATA_TYPE.getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case DELETE_BY_ID: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }
                String[] query = sqlGenerator.getEntityDeleteByIdQuery(entity, operation);
                if (query != null) {
                    writeDelete(writer,
                            operation.getMethodName(),
                            entity.getFirstIdField().getDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case INSERT: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }
                FieldInfo id = entity.getFirstIdField();
                boolean useGeneratedKey = id.isIdentifierAutogenerated();
                operation.setInsertedIdOrigin(defaultIdOrigin);
                if (useGeneratedKey) {
                    QueryGeneratorConfiguration config = sqlGenerator.getConfiguration();
                    if (!config.useAutoIncrementId()) {
                        String[] query = sqlGenerator.getEntityLastInsertedIdQuery(entity, operation, config.useGeneratedKeys());
                        if (query != null) {
                            if (!entitiesWithLastInsertedId.contains(entity)) {
                                writeSelectWithoutParameter(writer,
                                        "lastInsertedIdFor" + entity.getDataType().getSimpleNameWithoutGenerics(),
                                        operation.getReturnDataType().getQualifiedNameWithoutGenerics(),
                                        query,
                                        isProcedureInvocation);
                                entitiesWithLastInsertedId.add(entity);
                            }
                            useGeneratedKey = false;
                            operation.setInsertedIdOrigin(InsertedIdOrigin.QUERY);
                        } else {
                            useGeneratedKey = config.useGeneratedKeys();
                        }
                    }
                }
                String[] query = sqlGenerator.getEntityInsertQuery(entity, operation);
                if (query != null) {
                    if (useGeneratedKey) {
                        String keyProperty = id.getName();
                        String keyColumn = id.getMappedNameOrName();
                        operation.setInsertedIdOrigin(defaultIdOrigin);
                        writeInsertWithGeneratedKeyFromOrigin(writer,
                                operation.getMethodName(),
                                entity.getDataType().getQualifiedNameWithoutGenerics(),
                                query,
                                keyProperty,
                                keyColumn,
                                isProcedureInvocation,
                                useParameterType,
                                operation.getInsertedIdOrigin(),
                                id);
                    } else {
                        writeInsert(writer,
                                operation.getMethodName(),
                                entity.getDataType().getQualifiedNameWithoutGenerics(),
                                query,
                                isProcedureInvocation,
                                useParameterType);
                    }
                }
            }
            break;
            case JUST_INSERT: {
                String name;
                if (operation.isReuseEntityOperations()) {
                    name = "insert" + operation.getEntity().getDataType().getSimpleNameWithoutGenerics();
                } else {
                    name = operation.getMethodName();
                }
                operation.setQueryId(namespace + "." + name);
                if (!operation.isReuseEntityOperations()) {
                    if (entity == null) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                        break;
                    }
                    String[] query = sqlGenerator.getEntityInsertQuery(entity, operation);
                    if (query != null) {
                        writeInsert(writer,
                                operation.getMethodName(),
                                entity.getDataType().getQualifiedNameWithoutGenerics(),
                                query,
                                isProcedureInvocation,
                                useParameterType);
                    }
                }
            }
            break;
            case SAVE: {
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }

                if (operation.isReuseEntityOperations()) {
                    operation.setSaveInsertQueryId("insert" + entity.getDataType().getSimpleNameWithoutGenerics());
                    operation.setQueryId("update" + entity.getDataType().getSimpleNameWithoutGenerics());
                } else {
                    operation.setSaveInsertQueryId(operation.getMethodName() + "-Insert");
                    operation.setQueryId(operation.getMethodName() + "-Update");
                }
                
                FieldInfo id = entity.getFirstIdField();
                boolean useGeneratedKey = id.isIdentifierAutogenerated();
                operation.setInsertedIdOrigin(defaultIdOrigin);
                if (useGeneratedKey) {
                    QueryGeneratorConfiguration config = sqlGenerator.getConfiguration();
                    if (!config.useAutoIncrementId()) {
                        String[] query = sqlGenerator.getEntityLastInsertedIdQuery(entity, operation, config.useGeneratedKeys());
                        if (query != null) {
                            if (!entitiesWithLastInsertedId.contains(entity)) {
                                writeSelectWithoutParameter(writer,
                                        "lastInsertedIdFor" + entity.getDataType().getSimpleNameWithoutGenerics(),
                                        operation.getReturnDataType().getQualifiedNameWithoutGenerics(),
                                        query,
                                        isProcedureInvocation);
                                entitiesWithLastInsertedId.add(entity);
                            }
                            useGeneratedKey = false;
                            operation.setInsertedIdOrigin(InsertedIdOrigin.QUERY);
                        } else {
                            useGeneratedKey = config.useGeneratedKeys();
                        }
                    }
                }
                if (!operation.isReuseEntityOperations()) {
                    String[] insertQuery = sqlGenerator.getEntityInsertQuery(entity, operation);
                    if (insertQuery != null) {
                        if (useGeneratedKey) {
                            String keyProperty = id.getName();
                            String keyColumn = id.getMappedNameOrName();
                            operation.setInsertedIdOrigin(defaultIdOrigin);
                            writeInsertWithGeneratedKeyFromOrigin(writer,
                                    operation.getMethodName() + "-Insert",
                                    entity.getDataType().getQualifiedNameWithoutGenerics(),
                                    insertQuery,
                                    keyProperty,
                                    keyColumn,
                                    isProcedureInvocation,
                                    useParameterType,
                                    operation.getInsertedIdOrigin(),
                                    id);
                        } else {
                            writeInsert(writer,
                                    operation.getMethodName() + "-Insert",
                                    entity.getDataType().getQualifiedNameWithoutGenerics(),
                                    insertQuery,
                                    isProcedureInvocation,
                                    useParameterType);
                        }
                    }
                    String[] updateQuery = sqlGenerator.getEntityUpdateQuery(entity, operation);
                    if (updateQuery != null) {
                        writeUpdate(writer,
                                operation.getMethodName() + "-Update",
                                entity.getDataType().getQualifiedNameWithoutGenerics(),
                                updateQuery,
                                isProcedureInvocation,
                                useParameterType);
                    }
                }
            }
            break;
            case JUST_SAVE: {
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }

                if (operation.isReuseEntityOperations()) {
                    operation.setSaveInsertQueryId("insert" + entity.getDataType().getSimpleNameWithoutGenerics());
                    operation.setQueryId("update" + entity.getDataType().getSimpleNameWithoutGenerics());
                } else {
                    operation.setSaveInsertQueryId(operation.getMethodName() + "-Insert");
                    operation.setQueryId(operation.getMethodName() + "-Update");
                }
                
                if (!operation.isReuseEntityOperations()) {
                    String[] insertQuery = sqlGenerator.getEntityInsertQuery(entity, operation);
                    if (insertQuery != null) {
                        writeInsert(writer,
                                operation.getMethodName() + "-Insert",
                                entity.getDataType().getQualifiedNameWithoutGenerics(),
                                insertQuery,
                                isProcedureInvocation,
                                useParameterType);
                    }
                    String[] updateQuery = sqlGenerator.getEntityUpdateQuery(entity, operation);
                    if (updateQuery != null) {
                        writeUpdate(writer,
                                operation.getMethodName() + "-Update",
                                entity.getDataType().getQualifiedNameWithoutGenerics(),
                                updateQuery,
                                isProcedureInvocation,
                                useParameterType);
                    }
                }
            }
            break;
            case SELECT_BY_ID: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }
                String[] query = sqlGenerator.getEntitySelectByIdQuery(entity, operation);
                if (query != null) {
                    writeSelect(writer,
                            operation.getMethodName(),
                            entity.getFirstIdField().getDataType().getQualifiedNameWithoutGenerics(),
                            operation.getReturnDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case UPDATE: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }
                String[] query = sqlGenerator.getEntityUpdateQuery(entity, operation);
                if (query != null) {
                    writeUpdate(writer,
                            operation.getMethodName(),
                            entity.getDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case CUSTOM_INSERT: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getCustomInsertQuery(operation);
                if (query != null) {
                    writeInsert(writer,
                            operation.getMethodName(),
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case CUSTOM_UPDATE: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getCustomUpdateQuery(operation);
                if (query != null) {
                    writeUpdate(writer,
                            operation.getMethodName(),
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case CUSTOM_DELETE: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getCustomDeleteQuery(operation);
                if (query != null) {
                    writeDelete(writer,
                            operation.getMethodName(),
                            operation.getDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case CUSTOM_INSERT_WITH_ID: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }
                FieldInfo id = entity.getFirstIdField();
                boolean useGeneratedKey = id.isIdentifierAutogenerated();
                operation.setInsertedIdOrigin(defaultIdOrigin);
                if (useGeneratedKey) {
                    QueryGeneratorConfiguration config = sqlGenerator.getConfiguration();
                    if (config.useAutoIncrementId()) {
                        if (!operation.hasReferenceToField(id)) {
                            operation.setInsertedIdOrigin(InsertedIdOrigin.RETAINED);
                        }
                    } else {
                        String[] query = sqlGenerator.getEntityLastInsertedIdQuery(entity, operation, config.useGeneratedKeys());
                        if (query != null) {
                            if (!entitiesWithLastInsertedId.contains(entity)) {
                                writeSelectWithoutParameter(writer,
                                        "lastInsertedIdFor" + entity.getDataType().getSimpleNameWithoutGenerics(),
                                        operation.getReturnDataType().getQualifiedNameWithoutGenerics(),
                                        query,
                                        isProcedureInvocation);
                                entitiesWithLastInsertedId.add(entity);
                            }
                            useGeneratedKey = false;
                            operation.setInsertedIdOrigin(InsertedIdOrigin.QUERY);
                        } else {
                            useGeneratedKey = config.useGeneratedKeys();
                        }
                    }
                }
                String[] query = sqlGenerator.getCustomInsertQuery(operation);
                if (query != null) {
                    if (useGeneratedKey) {
                        String keyProperty = id.getName();
                        String keyColumn = id.getMappedNameOrName();
                        if (operation.hasReferenceToField(id)) {
                            operation.setInsertedIdOrigin(defaultIdOrigin);
                        } else {
                            operation.setInsertedIdOrigin(InsertedIdOrigin.RETAINED);
                        }
                        writeInsertWithGeneratedKeyFromOrigin(writer,
                                operation.getMethodName(),
                                operation.getDataType().getQualifiedNameWithoutGenerics(),
                                query,
                                keyProperty,
                                keyColumn,
                                isProcedureInvocation,
                                useParameterType,
                                operation.getInsertedIdOrigin(),
                                id);
                    } else {
                        writeInsert(writer,
                                operation.getMethodName(),
                                operation.getDataType().getQualifiedNameWithoutGenerics(),
                                query,
                                isProcedureInvocation,
                                useParameterType);
                    }
                }
            }
            break;
            case MERGE: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                if (entity == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find the entity related to the operation", operation.getElement());
                    break;
                }
                String[] query = sqlGenerator.getEntityMergeQuery(entity, operation);
                if (query != null) {
                    writeUpdate(writer,
                            operation.getMethodName(),
                            entity.getDataType().getQualifiedNameWithoutGenerics(),
                            query,
                            isProcedureInvocation,
                            useParameterType);
                }
            }
            break;
            case COMPLEX_SELECT_CALL: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getComplexSelectCallQuery(operation);
                if (query != null) {
                    writeSelectWithoutResult(writer,
                            operation.getMethodName(),
                            null,
                            query,
                            isProcedureInvocation,
                            false);
                }
            }
            break;
            case COMPLEX_INSERT_CALL: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getComplexInsertCallQuery(operation);
                if (query != null) {
                    writeInsert(writer,
                            operation.getMethodName(),
                            null,
                            query,
                            isProcedureInvocation,
                            false);
                }
            }
            break;
            case COMPLEX_UPDATE_CALL: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getComplexUpdateCallQuery(operation);
                if (query != null) {
                    writeUpdate(writer,
                            operation.getMethodName(),
                            null,
                            query,
                            isProcedureInvocation,
                            false);
                }
            }
            break;
            case COMPLEX_DELETE_CALL: {
                operation.setQueryId(namespace + "." + operation.getMethodName());
                String[] query = sqlGenerator.getComplexDeleteCallQuery(operation);
                if (query != null) {
                    writeDelete(writer,
                            operation.getMethodName(),
                            null,
                            query,
                            isProcedureInvocation,
                            false);
                }
            }
            break;
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Write xml entries">
    public void writeUpdate(Writer writer, String id, String parameterType, String[] lines, boolean isProcedureInvocation, boolean useParameterType) throws IOException {
        writer.write("    <update id='");
        writer.write(id);
        if (useParameterType) {
            writer.write("' parameterType='");
            writer.write(parameterType);
        }
        if (isProcedureInvocation) {
            writer.write("' statementType='CALLABLE");
        }
        writer.write("'>\n");
        if (lines != null) {
            for (String line : lines) {
                writer.write("        ");
                writer.write(line);
                writer.write("\n");
            }
        }
        writer.write("    </update>\n\n");
    }

    public void writeSelect(Writer writer, String id, String parameterType, String resultType, String[] lines, boolean isProcedureInvocation, boolean useParameterType) throws IOException {
        writer.write("    <select id='");
        writer.write(id);
        if (useParameterType) {
            writer.write("' parameterType='");
            writer.write(parameterType);
        }
        writer.write("' resultType='");
        writer.write(resultType);
        if (isProcedureInvocation) {
            writer.write("' statementType='CALLABLE");
        }
        writer.write("'>\n");
        if (lines != null) {
            for (String line : lines) {
                writer.write("        ");
                writer.write(line);
                writer.write("\n");
            }
        }
        writer.write("    </select>\n\n");
    }

    public void writeSelectWithoutParameter(Writer writer, String id, String resultType, String[] lines, boolean isProcedureInvocation) throws IOException {
        writer.write("    <select id='");
        writer.write(id);
        writer.write("' resultType='");
        writer.write(resultType);
        if (isProcedureInvocation) {
            writer.write("' statementType='CALLABLE");
        }
        writer.write("'>\n");
        if (lines != null) {
            for (String line : lines) {
                writer.write("        ");
                writer.write(line);
                writer.write("\n");
            }
        }
        writer.write("    </select>\n\n");
    }

    public void writeSelectWithoutResult(Writer writer, String id, String parameterType, String[] lines, boolean isProcedureInvocation, boolean useParameterType) throws IOException {
        writer.write("    <select id='");
        writer.write(id);
        if (useParameterType) {
            writer.write("' parameterType='");
            writer.write(parameterType);
        }
        if (isProcedureInvocation) {
            writer.write("' statementType='CALLABLE");
        }
        writer.write("'>\n");
        if (lines != null) {
            for (String line : lines) {
                writer.write("        ");
                writer.write(line);
                writer.write("\n");
            }
        }
        writer.write("    </select>\n\n");
    }

    public void writeInsert(Writer writer, String id, String parameterType, String[] lines, boolean isProcedureInvocation, boolean useParameterType) throws IOException {
        writer.write("    <insert id='");
        writer.write(id);
        if (useParameterType) {
            writer.write("' parameterType='");
            writer.write(parameterType);
        }
        if (isProcedureInvocation) {
            writer.write("' statementType='CALLABLE");
        }
        writer.write("'>\n");
        if (lines != null) {
            for (String line : lines) {
                writer.write("        ");
                writer.write(line);
                writer.write("\n");
            }
        }
        writer.write("    </insert>\n\n");
    }

    public void writeInsertWithGeneratedKey(Writer writer, String id, String parameterType, String[] lines, String keyProperty, String keyColumn, boolean isProcedureInvocation, boolean useParameterType) throws IOException {
        writer.write("    <insert id='");
        writer.write(id);
        if (useParameterType) {
            writer.write("' parameterType='");
            writer.write(parameterType);
        }
        writer.write("' useGeneratedKeys='true' keyProperty='");
        writer.write(keyProperty);
        writer.write("' keyColumn='");
        writer.write(keyColumn);
        if (isProcedureInvocation) {
            writer.write("' statementType='CALLABLE");
        }
        writer.write("'>\n");
        if (lines != null) {
            for (String line : lines) {
                writer.write("        ");
                writer.write(line);
                writer.write("\n");
            }
        }
        writer.write("    </insert>\n\n");
    }
    
    public void writeInsertWithGeneratedKeyFromOrigin(Writer writer, String id, String parameterType, String[] lines, String keyProperty, String keyColumn, boolean isProcedureInvocation, boolean useParameterType, InsertedIdOrigin origin, FieldInfo idField) throws IOException {
        if (origin == InsertedIdOrigin.RETAINED) {
            String qualifiedName = idField.getDataType().getQualifiedNameWithoutGenerics();
            String alias = typeAlias.get(qualifiedName);
            if (alias != null) {
                keyProperty = "__retain_" + alias;
            } else {
                keyProperty = "__retain_" + qualifiedName;
            }
            writeInsertWithGeneratedKey(writer, id, parameterType, lines, keyProperty, keyColumn, isProcedureInvocation, useParameterType);
        } else {
            writeInsertWithGeneratedKey(writer, id, parameterType, lines, keyProperty, keyColumn, isProcedureInvocation, useParameterType);
        }
    }

    public void writeDelete(Writer writer, String id, String parameterType, String[] lines, boolean isProcedureInvocation, boolean useParameterType) throws IOException {
        writer.write("    <delete id='");
        writer.write(id);
        if (useParameterType) {
            writer.write("' parameterType='");
            writer.write(parameterType);
        }
        if (isProcedureInvocation) {
            writer.write("' statementType='CALLABLE");
        }
        writer.write("'>\n");
        if (lines != null) {
            for (String line : lines) {
                writer.write("        ");
                writer.write(line);
                writer.write("\n");
            }
        }
        writer.write("    </delete>\n\n");
    }
    //</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="type aliases">
    private static final HashMap<String,String> typeAlias;
    static {
        typeAlias = new HashMap<String, String>();
        typeAlias.put("java.lang.Boolean", "boolean");
        typeAlias.put("java.lang.Byte", "byte");
        typeAlias.put("java.lang.Short", "short");
        typeAlias.put("java.lang.Integer", "int");
        typeAlias.put("java.lang.Long", "long");
        typeAlias.put("java.lang.Float", "float");
        typeAlias.put("java.lang.Double", "double");
        typeAlias.put("java.math.BigDecimal", "bigdecimal");
        typeAlias.put("java.lang.String", "string");
        typeAlias.put("java.util.Date", "date");
    }
    //</editor-fold>
}
