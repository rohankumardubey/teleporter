/*
 * Copyright 2016 OrientDB LTD (info--at--orientdb.com)
 * All Rights Reserved. Commercial License.
 *
 * NOTICE:  All information contained herein is, and remains the property of
 * OrientDB LTD and its suppliers, if any.  The intellectual and
 * technical concepts contained herein are proprietary to
 * OrientDB LTD and its suppliers and may be covered by United
 * Kingdom and Foreign Patents, patents in process, and are protected by trade
 * secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from OrientDB LTD.
 *
 * For more information: http://www.orientdb.com
 */

package com.orientechnologies.teleporter.configuration.api;

import java.util.List;

/**
 *
 * @author Gabriele Ponzi
 * @email <gabriele.ponzi--at--gmail.com>
 *
 */

public class OConfiguredElement {

    private String name;                                        // mandatory
    private OVertexMappingInformation mapping;                  // mandatory
    private List<OConfiguredProperty> configuredProperties;     // mandatory

    public OConfiguredElement(String elementName) {
        this.name = elementName;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OVertexMappingInformation getMapping() {
        return this.mapping;
    }

    public void setMapping(OVertexMappingInformation mapping) {
        this.mapping = mapping;
    }

    public List<OConfiguredProperty> getConfiguredProperties() {
        return this.configuredProperties;
    }

    public void setConfiguredProperties(List<OConfiguredProperty> configuredProperties) {
        this.configuredProperties = configuredProperties;
    }
}
