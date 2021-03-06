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
package org.uaithne.generator.templates.shared;

import java.io.IOException;
import org.uaithne.generator.templates.ClassTemplate;

public class JustSaveValueOperationTemplate extends ClassTemplate {

    public JustSaveValueOperationTemplate(String packageName) {
        setPackageName(packageName);
        setClassName("JustSaveValueOperation");
        addGenericArgument("VALUE");
        addGenericArgument(RESULT_BASE_DEFINITION);
        addImplement("Operation<RESULT>");
        setInterface(true);
    }
    
    @Override
    protected void writeContent(Appendable appender) throws IOException {
        appender.append("    public VALUE getValue();\n"
                + "    public void setValue(VALUE value);");
    }
    
}
