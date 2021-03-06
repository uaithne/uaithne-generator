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
package org.uaithne.generator.processors.database.providers.oracle;

import org.uaithne.generator.commons.EntityInfo;
import org.uaithne.generator.commons.OperationInfo;

public class OracleSqlPackageHeaderGenerator extends OracleSqlAbstractProcedureGenerator {

    @Override
    protected void appendProcedureStart(StringBuilder query) {
        query.append(getFirstLevelIdentation());
        query.append("procedure ");
    }

    private void appendEndDeclaration(StringBuilder query, boolean requireSeparator) {
        if (requireSeparator) {
            query.append("\n");
            query.append(getFirstLevelIdentation());
            query.append(");\n");
        } else {
            query.append(";\n");
        }
    }

    @Override
    protected String getFirstLevelIdentation() {
        return "    ";
    }

    @Override
    protected String getSecondLevelIdentation() {
        return "        ";
    }

    @Override
    public void appendEndEntityDeleteByIdQuery(StringBuilder query, EntityInfo entity, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }

    @Override
    public void appendEndEntityInsertQuery(StringBuilder query, EntityInfo entity, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }

    @Override
    public void appendEndEntityLastInsertedIdQuery(StringBuilder query, EntityInfo entity, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }

    @Override
    public void appendEndEntityMergeQuery(StringBuilder query, EntityInfo entity, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }

    @Override
    public void appendEndEntityUpdateQuery(StringBuilder query, EntityInfo entity, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }

    @Override
    public void appendEndCustomDeleteQuery(StringBuilder query, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }

    @Override
    public void appendEndCustomInsertQuery(StringBuilder query, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }

    @Override
    public void appendEndCustomUpdateQuery(StringBuilder query, OperationInfo operation, boolean requireSeparator) {
        appendEndDeclaration(query, requireSeparator);
    }
}
