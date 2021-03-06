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
package org.uaithne.generator.templates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import org.uaithne.annotations.AnnotationConfigurationKeys;
import org.uaithne.generator.commons.DataTypeInfo;
import org.uaithne.generator.commons.FieldInfo;
import org.uaithne.generator.commons.GenerationInfo;
import org.uaithne.generator.commons.NamesGenerator;
import org.uaithne.generator.commons.TemplateProcessor;
import org.uaithne.generator.commons.ValidationRule;

public abstract class PojoTemplate extends WithFieldsTemplate {

    protected void writeDocumentation(Appendable appender, String[] doc) throws IOException {
        if (doc != null) {
            appender.append("    /**\n");
            for (String s : doc) {
                appender.append("     * ").append(s).append("\n");
            }
            appender.append("     */\n");
        }
    }

    protected void writeGetterDocumentation(Appendable appender, String[] doc, String fieldName) throws IOException {

        appender.append("    /**\n");
        if (doc != null) {
            for (String s : doc) {
                appender.append("     * ").append(s).append("\n");
            }
        }
        appender.append("     * @return the ");
        appender.append(fieldName);
        appender.append("\n");
        appender.append("     */\n");
    }

    protected void writeSetterDocumentation(Appendable appender, String[] doc, String fieldName) throws IOException {

        appender.append("    /**\n");
        if (doc != null) {
            for (String s : doc) {
                appender.append("     * ").append(s).append("\n");
            }
        }
        appender.append("     * @param ");
        appender.append(fieldName);
        appender.append(" the ");
        appender.append(fieldName);
        appender.append(" to set\n");
        appender.append("     */\n");
    }

    protected void writeField(Appendable appender, FieldInfo field) throws IOException {
        writeDocumentation(appender, field.getDocumentation());
        appendFieldBeanValidations(appender, field);
        appender.append("    private ");
        if (field.isMarkAsTransient()) {
            appender.append("transient ");
        }
        appender.append(field.getDataType().getSimpleName());
        appender.append(" ");
        appender.append(field.getName());
        String defaultValue = field.getDefaultValue();
        if (defaultValue != null && !defaultValue.isEmpty()) {
            appender.append(" = ");
            appender.append(field.getDefaultValue());
        }
        appender.append(";\n");
    }

    protected void writeFieldGetter(Appendable appender, FieldInfo field) throws IOException {
        writeGetterDocumentation(appender, field.getDocumentation(), field.getName());
        if (field.isMarkAsOvwrride()) {
            appender.append("    @Override\n");
        }
        if (field.isDeprecated()) {
            appender.append("    @Deprecated\n");
        }

        appender.append("    public ").append(field.getDataType().getSimpleName()).append(" ").append(field.getDataType().getGetterPrefix(getGenerationInfo())).append(field.getCapitalizedName()).append("() {\n");
        appender.append("        return ").append(field.getName()).append(";\n");
        appender.append("    }\n");
    }

    protected void writeFieldSetter(Appendable appender, FieldInfo field) throws IOException {
        writeSetterDocumentation(appender, field.getDocumentation(), field.getName());
        if (field.isMarkAsOvwrride()) {
            appender.append("    @Override\n");
        }
        if (field.isDeprecated()) {
            appender.append("    @Deprecated\n");
        }
        appender.append("    public void set").append(field.getCapitalizedName()).append("(").append(field.getDataType().getSimpleName()).append(" ").append(field.getName()).append(") {\n");
        appender.append("        this.").append(field.getName()).append(" = ").append(field.getName()).append(";\n");
        appender.append("    }\n");
    }

    protected void writeFieldsInitialization(Appendable appender, ArrayList<FieldInfo> fields) throws IOException {
        for (FieldInfo field : fields) {
            appender.append("        this.").append(field.getName()).append(" = ").append(field.getName()).append(";\n");
        }
    }

    protected void writeToString(Appendable appender, ArrayList<FieldInfo> fields, boolean callSuper) throws IOException {
        appender.append("    @Override\n"
                + "    public String toString(){\n"
                + "        return \"").append(getClassName()).append("{\" +\n");

        boolean requireSeparator = false;
        for (FieldInfo field : fields) {
            if (field.isExcludedFromToString()) {
                continue;
            }
            if (field.isExcludedFromObject()) {
                continue;
            }
            if (requireSeparator) {
                appender.append("\"| \" +\n");
            } else {
                requireSeparator = true;
            }
            appender.append("            \"").append(field.getName()).append("=\" + ").append(field.getName()).append(" + ");
        }
        if (callSuper) {
            if (requireSeparator) {
                appender.append("\"| \" +\n");
            }
            appender.append("            \"superclass=\" + super.toString() +");
            requireSeparator = true;
        }
        if (requireSeparator) {
            appender.append("\n");
        }
        appender.append("            \"}\";\n"
                + "    }\n");
    }

    protected void writeEquals(Appendable appender, ArrayList<FieldInfo> fields, boolean callSuper) throws IOException {
        appender.append("    @Override\n"
                + "    public boolean equals(Object obj) {\n"
                + "        if (this == obj) {\n"
                + "            return true;\n"
                + "        }\n"
                + "        if (obj == null) {\n"
                + "            return false;\n"
                + "        }\n"
                + "        if (getClass() != obj.getClass()) {\n"
                + "            return false;\n"
                + "        }\n");

        ArrayList<FieldInfo> filteredFields = new ArrayList<FieldInfo>(fields.size());
        for (FieldInfo field : fields) {
            if (field.isExcludedFromObject()) {
                continue;
            }
            if (!field.isMarkAsTransient()) {
                filteredFields.add(field);
            }
        }

        if (callSuper || filteredFields.isEmpty()) {
            appender.append("        if (!super.equals(obj)) {\n"
                    + "            return false;\n"
                    + "        }\n");
        }

        if (!filteredFields.isEmpty()) {
            appender.append("        final ").append(getClassName()).append(" other = (").append(getClassName()).append(") obj;\n");
            for (FieldInfo field : filteredFields) {
                appender.append("        if (").append(field.generateEqualsRule()).append(") {\n"
                        + "            return false;\n"
                        + "        }\n");
            }
        }

        appender.append("        return true;\n"
                + "    }\n");
    }

    protected void writeHashCode(Appendable appender, ArrayList<FieldInfo> fields, boolean callSuper, String firstPrime, String secondPrime) throws IOException {
        appender.append("    @Override\n"
                + "    public int hashCode() {\n"
                + "        int hash = ").append(firstPrime).append(";\n");

        ArrayList<FieldInfo> filteredFields = new ArrayList<FieldInfo>(fields.size());
        for (FieldInfo field : fields) {
            if (field.isExcludedFromObject()) {
                continue;
            }
            if (field.isMarkAsTransient()) {
                continue;
            }
            filteredFields.add(field);
        }

        if (callSuper || filteredFields.isEmpty()) {
            appender.append("        hash = ").append(secondPrime).append(" * hash + super.hashCode();\n");
        }

        for (FieldInfo field : filteredFields) {
            appender.append("        hash = ").append(secondPrime).append(" * hash + ").append(field.generateHashCodeRule()).append(";\n");
        }

        appender.append("        return hash;\n"
                + "    }\n");
    }
    
    protected void appendClassAnnotationImports(String currentPackage, HashSet<String> imports, Element element) {
        if (element == null) {
            return;
        }
        FieldInfo fake = new FieldInfo();
        fake.setValidationAlreadyConfigured(true);
        GenerationInfo generationInfo = TemplateProcessor.getGenerationInfo();
        fake.ensureValidationsInfo(generationInfo);
        HashSet<String> loaded = new HashSet<String>();
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            appendAnnotationImports(currentPackage, imports, annotation, loaded, fake, true);
        }
    }

    protected void appendAnnotationImports(String currentPackage, HashSet<String> imports, FieldInfo field) {
        GenerationInfo generationInfo = TemplateProcessor.getGenerationInfo();
        field.ensureValidationsInfo(generationInfo);
        ArrayList<DataTypeInfo> validationAnnotations = field.getValidationAnnotations();
        HashSet<String> loaded = new HashSet<String>();
        FieldInfo relatedField = field;
        while (relatedField != null) {
            appendAnnotationImports(currentPackage, imports, relatedField, loaded);
            relatedField = relatedField.getRelated();
            if (relatedField != null) {
                for (DataTypeInfo validationAnnotation : relatedField.getValidationAnnotations()) {
                    if (!validationAnnotations.contains(validationAnnotation)) {
                        loaded.add(validationAnnotation.getQualifiedNameWithoutGenerics());
                    }
                }
            }
        }
        for (DataTypeInfo validationAnnotation : field.getValidationAnnotations()) {
            if (!loaded.contains(validationAnnotation.getQualifiedNameWithoutGenerics())) {
                validationAnnotation.appendImports(currentPackage, imports);
            }
        }
        for (DataTypeInfo validationGroups : field.getValidationGroups()) {
            validationGroups.appendImports(currentPackage, imports);
        }
        if (generationInfo.isEnableBeanValidations()) {
            if (field.getValidationRule() == ValidationRule.VALIDATE) {
                imports.add("javax.validation.Valid");
            } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_INSERT) {
                imports.add("javax.validation.Valid");
                ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.INSERT_GROUP);
                if (!notInsertGroups.isEmpty()) {
                    imports.add("javax.validation.groups.Default");
                    imports.add("javax.validation.groups.ConvertGroup");
                    notInsertGroups.get(0).appendImports(currentPackage, imports);
                }
            } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_UPDATE) {
                imports.add("javax.validation.Valid");
                ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.UPDATE_GROUP);
                if (!notInsertGroups.isEmpty()) {
                    imports.add("javax.validation.groups.Default");
                    imports.add("javax.validation.groups.ConvertGroup");
                    notInsertGroups.get(0).appendImports(currentPackage, imports);
                }
            } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_SAVE) {
                imports.add("javax.validation.Valid");
                ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.SAVE_GROUP);
                if (!notInsertGroups.isEmpty()) {
                    imports.add("javax.validation.groups.Default");
                    imports.add("javax.validation.groups.ConvertGroup");
                    notInsertGroups.get(0).appendImports(currentPackage, imports);
                }
            } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_MERGE) {
                imports.add("javax.validation.Valid");
                ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.MERGE_GROUP);
                if (!notInsertGroups.isEmpty()) {
                    imports.add("javax.validation.groups.Default");
                    imports.add("javax.validation.groups.ConvertGroup");
                    notInsertGroups.get(0).appendImports(currentPackage, imports);
                }
            }
        }
    }
    
    private void appendAnnotationImports(String currentPackage, HashSet<String> imports, FieldInfo field, HashSet<String> loaded) {
        VariableElement element = field.getElement();
        if (element != null) {
            for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
                appendAnnotationImports(currentPackage, imports, annotation, loaded, field, true);
            }
        }
    }

    private void appendAnnotationImports(String currentPackage, HashSet<String> imports, AnnotationMirror annotation, HashSet<String> loaded, FieldInfo field, boolean ignoreUaithne) {
        DataTypeInfo dataType = NamesGenerator.createDataTypeFor(annotation.getAnnotationType(), true);
        if (field.getValidationSubstitutions() != null) {
            DataTypeInfo s = field.getValidationSubstitutions().get(dataType);
            if (s != null) {
                dataType = s;
            }
        }
        String qualifiedName = dataType.getQualifiedNameWithoutGenerics();

        if (ignoreUaithne && qualifiedName.startsWith("org.uaithne.annotations.")) {
            return;
        }
        if (loaded != null && loaded.contains(qualifiedName)) {
            return;
        }
        
        dataType.appendImports(currentPackage, imports);
        if (loaded != null) {
            loaded.contains(dataType.getQualifiedNameWithoutGenerics());
        }
        
        for (AnnotationValue value : annotation.getElementValues().values()) {
            appendAnnotationImports(currentPackage, imports, value, loaded, field);
        }        
    }

    private void appendAnnotationImports(String currentPackage, HashSet<String> imports, AnnotationValue annotationValue, HashSet<String> loaded, FieldInfo field) {
        Object value = annotationValue.getValue();
        if (value instanceof TypeMirror) {
            DataTypeInfo type = NamesGenerator.createDataTypeFor((TypeMirror) value, true);
            if (field.getValidationSubstitutions() != null) {
                DataTypeInfo s = field.getValidationSubstitutions().get(type);
                if (s != null) {
                    s.appendImports(currentPackage, imports);
                } else {
                    type.appendImports(currentPackage, imports);
                }
            } else {
                type.appendImports(currentPackage, imports);
            }
        } else if (value instanceof VariableElement) {
            VariableElement variableElement = (VariableElement) value;
            DataTypeInfo type = NamesGenerator.createDataTypeFor(variableElement.asType(), true);
            if (field.getValidationSubstitutions() != null) {
                DataTypeInfo s = field.getValidationSubstitutions().get(type);
                if (s != null) {
                    s.appendImports(currentPackage, imports);
                } else {
                    type.appendImports(currentPackage, imports);
                }
            } else {
                type.appendImports(currentPackage, imports);
            }
        } else if (value instanceof AnnotationMirror) {
            appendAnnotationImports(currentPackage, imports, (AnnotationMirror) value, null, field, false);
        } else if (value instanceof List) {
            List<? extends AnnotationValue> annotations = (List<? extends AnnotationValue>) value;
            for (AnnotationValue annotation : annotations) {
                appendAnnotationImports(currentPackage, imports, annotation, null, field);
            }
        }
    }

    private void appendAnnotationParamsContent(Appendable appender, AnnotationValue annotationValue, String ident, ArrayList<DataTypeInfo> groups, FieldInfo field) {
        Object value = null;
        if (annotationValue != null) {
            value = annotationValue.getValue();
        }
        try {
            if (value instanceof String) {
                appender.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            } else if (value instanceof VariableElement) {
                VariableElement variableElement = (VariableElement) value;
                DataTypeInfo type = NamesGenerator.createDataTypeFor(variableElement.asType(), true);
                appender.append(type.getSimpleName());
                appender.append(".");
                appender.append(variableElement.getSimpleName());
            } else if (value instanceof TypeMirror) {
                DataTypeInfo type = NamesGenerator.createDataTypeFor((TypeMirror) value, true);
                appender.append(type.getSimpleName());
                if (groups != null) {
                    groups.remove(type);
                }
                appender.append(".class");
            } else if (value instanceof AnnotationMirror) {
                AnnotationMirror annotation = ((AnnotationMirror) value);
                DataTypeInfo dataType = NamesGenerator.createDataTypeFor(annotation.getAnnotationType(), true);
                appendAnnotation(appender, dataType, annotation, ident + "    ", false, field);
            } else if (value instanceof List) {
                List<? extends AnnotationValue> annotations = (List<? extends AnnotationValue>) value;
                boolean manyElements = annotations.size() > 1;
                if (groups != null) {
                    manyElements = true;
                }
                if (manyElements) {
                    appender.append("{");
                }
                boolean requireComma = false;
                for (AnnotationValue annotation : annotations) {
                    if (requireComma) {
                        appender.append(", ");
                    }
                    appendAnnotationParamsContent(appender, annotation, ident, groups, field);
                    requireComma = true;
                }
                if (groups != null) {
                    for (DataTypeInfo group : groups) {
                        if (requireComma) {
                            appender.append(", ");
                        }
                        appender.append(group.getSimpleName());
                        appender.append(".class");
                        requireComma = true;
                    }
                }
                if (manyElements) {
                    appender.append("}");
                }
            } else if (value != null) { // a wrapper class for a primitive type
                appender.append(value.toString());
            } else if (value == null && groups != null) {
                boolean manyElements = groups.size() > 1;
                if (manyElements) {
                    appender.append("{");
                }
                boolean requireComma = false;
                for (DataTypeInfo group : groups) {
                    if (requireComma) {
                        appender.append(", ");
                    }
                    appender.append(group.getSimpleName());
                    appender.append(".class");
                    requireComma = true;
                }
                if (manyElements) {
                    appender.append("}");
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(PojoTemplate.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void appendFieldBeanValidations(Appendable appender, FieldInfo field) {
        ArrayList<DataTypeInfo> validationAnnotations = field.getValidationAnnotations();
        HashSet<String> loaded = new HashSet<String>();
        FieldInfo relatedField = field;
        while (relatedField != null) {
            appendFieldBeanValidations(appender, relatedField, loaded);
            relatedField = relatedField.getRelated();
            if (relatedField != null) {
                for (DataTypeInfo validationAnnotation : relatedField.getValidationAnnotations()) {
                    if (!validationAnnotations.contains(validationAnnotation)) {
                        loaded.add(validationAnnotation.getQualifiedNameWithoutGenerics());
                    }
                }
            }
        }

        ArrayList<DataTypeInfo> validationGroups = field.getValidationGroups();
        if (validationGroups.isEmpty()) {
            validationGroups = null;
        }
        if (validationGroups == null) {
            for (DataTypeInfo validationAnnotation : field.getValidationAnnotations()) {
                if (!loaded.contains(validationAnnotation.getQualifiedNameWithoutGenerics())) {
                    try {
                        appender.append("    @");
                        appender.append(validationAnnotation.getSimpleName());
                        appender.append("\n");
                    } catch (IOException ex) {
                        Logger.getLogger(PojoTemplate.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } else if (validationGroups.size() == 1) {
            DataTypeInfo group = validationGroups.get(0);
            for (DataTypeInfo validationAnnotation : field.getValidationAnnotations()) {
                if (!loaded.contains(validationAnnotation.getQualifiedNameWithoutGenerics())) {
                    try {
                        appender.append("    @");
                        appender.append(validationAnnotation.getSimpleName());
                        appender.append("(groups = ");
                        appender.append(group.getSimpleName());
                        appender.append(".class)");
                        appender.append("\n");
                        
                    } catch (IOException ex) {
                        Logger.getLogger(PojoTemplate.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } else {
            for (DataTypeInfo validationAnnotation : field.getValidationAnnotations()) {
                if (!loaded.contains(validationAnnotation.getQualifiedNameWithoutGenerics())) {
                    try {
                        appender.append("    @");
                        appender.append(validationAnnotation.getSimpleName());
                        appender.append("(groups = {");
                        boolean requireComma = false;
                        for (DataTypeInfo group : validationGroups) {
                            if (requireComma) {
                                appender.append(", ");
                            }
                            appender.append(group.getSimpleName());
                            appender.append(".class");
                        }
                        appender.append("})");
                        appender.append("\n");
                    } catch (IOException ex) {
                        Logger.getLogger(PojoTemplate.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        
        GenerationInfo generationInfo = TemplateProcessor.getGenerationInfo();
        if (generationInfo.isEnableBeanValidations()) {
            try {
                if (field.getValidationRule() == ValidationRule.VALIDATE) {
                    if (!loaded.contains("javax.validation.Valid")) {
                        appender.append("    @Valid\n");
                    }
                } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_INSERT) {
                    if (!loaded.contains("javax.validation.Valid")) {
                        appender.append("    @Valid\n");
                    }
                    // TODO: handle multiple instances of ConvertGroup annotation
                    ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.INSERT_GROUP);
                    if (!notInsertGroups.isEmpty()) {
                        appender.append("    @ConvertGroup(from = Default.class, to = ");
                        appender.append(notInsertGroups.get(0).getSimpleName());
                        appender.append(".class)\n");
                    }
                } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_UPDATE) {
                    if (!loaded.contains("javax.validation.Valid")) {
                        appender.append("    @Valid\n");
                    }
                    // TODO: handle multiple instances of ConvertGroup annotation
                    ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.UPDATE_GROUP);
                    if (!notInsertGroups.isEmpty()) {
                        appender.append("    @ConvertGroup(from = Default.class, to = ");
                        appender.append(notInsertGroups.get(0).getSimpleName());
                        appender.append(".class)\n");
                    }
                } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_SAVE) {
                    if (!loaded.contains("javax.validation.Valid")) {
                        appender.append("    @Valid\n");
                    }
                    // TODO: handle multiple instances of ConvertGroup annotation
                    ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.SAVE_GROUP);
                    if (!notInsertGroups.isEmpty()) {
                        appender.append("    @ConvertGroup(from = Default.class, to = ");
                        appender.append(notInsertGroups.get(0).getSimpleName());
                        appender.append(".class)\n");
                    }
                } else if (field.getValidationRule() == ValidationRule.VALIDATE_FOR_MERGE) {
                    if (!loaded.contains("javax.validation.Valid")) {
                        appender.append("    @Valid\n");
                    }
                    // TODO: handle multiple instances of ConvertGroup annotation
                    ArrayList<DataTypeInfo> notInsertGroups = generationInfo.getValidationConfigurations().get(AnnotationConfigurationKeys.MERGE_GROUP);
                    if (!notInsertGroups.isEmpty()) {
                        appender.append("    @ConvertGroup(from = Default.class, to = ");
                        appender.append(notInsertGroups.get(0).getSimpleName());
                        appender.append(".class)\n");
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(PojoTemplate.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void appendFieldBeanValidations(Appendable appender, FieldInfo field, HashSet<String> loaded) {
        
        VariableElement element = field.getElement();
        if (element != null) {
            for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
                DataTypeInfo dataType = NamesGenerator.createDataTypeFor(annotation.getAnnotationType(), true);
                if (field.getValidationSubstitutions() != null) {
                    DataTypeInfo s = field.getValidationSubstitutions().get(dataType);
                    if (s != null) {
                        dataType = s;
                    }
                }
                String qualifiedName = dataType.getQualifiedNameWithoutGenerics();
                if (!loaded.contains(qualifiedName) && !qualifiedName.startsWith("org.uaithne.annotations.")) {
                    appendAnnotation(appender, dataType, annotation, "    ", true, field);
                    loaded.add(qualifiedName);
                }
            }
        }
    }
    
    protected void writeClassAnnotations(Appendable appender, Element element) {
        if (element == null) {
            return;
        }
        FieldInfo fake = new FieldInfo();
        fake.setValidationAlreadyConfigured(true);
        GenerationInfo generationInfo = TemplateProcessor.getGenerationInfo();
        fake.ensureValidationsInfo(generationInfo);
        HashSet<String> loaded = new HashSet<String>();
        loaded.add("java.lang.Deprecated");
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            DataTypeInfo dataType = NamesGenerator.createDataTypeFor(annotation.getAnnotationType(), true);
            String qualifiedName = dataType.getQualifiedNameWithoutGenerics();
            if (!loaded.contains(qualifiedName) && !qualifiedName.startsWith("org.uaithne.annotations.")) {
                appendAnnotation(appender, dataType, annotation, "", true, fake);
                loaded.add(qualifiedName);
            }
        }
    }

    private void appendAnnotation(Appendable appender, DataTypeInfo dataType, AnnotationMirror annotation, String ident, boolean identFirstLevel, FieldInfo field) {
        try {
            if (field.getValidationSubstitutions() != null) {
                DataTypeInfo s = field.getValidationSubstitutions().get(dataType);
                if (s != null) {
                    dataType = s;
                }
            }
            ArrayList<DataTypeInfo> validationGroups = field.getValidationGroups();
            if (validationGroups.isEmpty()) {
                validationGroups = null;
            }
            ArrayList<DataTypeInfo> groups = null;
            if (field.getValidationAnnotations() != null && validationGroups != null) {
                if (field.getValidationAnnotations().contains(dataType)) {
                    groups = new ArrayList<DataTypeInfo>(validationGroups);
                }
            }
            
            if (!identFirstLevel) {
                appender.append("\n");
            }
            appender.append(ident);
            appender.append("@");
            appender.append(dataType.getSimpleName());

            int numberOfElements = annotation.getElementValues().size();
            if (groups != null) {
                numberOfElements++;
            }
            if (numberOfElements != 0) {
                appender.append("(");
            }
            boolean requireComma = false;
            boolean hasOnlyOne = numberOfElements == 1 ;
            boolean groupsFound = false;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
                if (requireComma) {
                    appender.append(", ");
                }
                String name = entry.getKey().getSimpleName().toString();
                if (!"groups".equals(name) && groups != null) {
                    hasOnlyOne = false;
                }
                if (!"value".equals(name) || !hasOnlyOne) {
                    appender.append(name);
                    appender.append(" = ");
                }
                if ("groups".equals(name)) {
                    groupsFound = true;
                    appendAnnotationParamsContent(appender, entry.getValue(), ident, groups, field);
                } else {
                    appendAnnotationParamsContent(appender, entry.getValue(), ident, null, field);
                }
                requireComma = true;
            }
            if (!groupsFound && groups != null) {
                if (requireComma) {
                    appender.append(", ");
                }
                appender.append("groups = ");
                appendAnnotationParamsContent(appender, null, ident, groups, field);
            }
            if (numberOfElements != 0) {
                appender.append(")");
            }
            if (identFirstLevel) {
                appender.append("\n");
            }
        } catch (IOException ex) {
            Logger.getLogger(PojoTemplate.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
