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

package com.orientechnologies.teleporter.mapper.rdbms.classmapper;

import com.orientechnologies.teleporter.model.dbschema.OEntity;
import com.orientechnologies.teleporter.model.graphmodel.OEdgeType;

import java.util.Map;

/**
 * This class is responsible to map 2 classes of objects: OEntity and OEdgeType.
 * the following values are mapped:
 *  - name of the entity -> name of the edge type
 *  - each attribute of the entity -> correspondent property of the edge type
 *  - each property of the edge type -> correspondent attribute of the entity
 *
 * @author Gabriele Ponzi
 * @email  <g.ponzi--at--orientdb.com>
 *
 */

public class OEEClassMapper extends OEntityClassMapper {

    private OEdgeType edgeType;

    public OEEClassMapper(OEntity entity, OEdgeType edgeType, Map<String,String> attribute2property, Map<String,String> property2attribute) {
        super(entity, attribute2property, property2attribute);
        this.edgeType = edgeType;
    }

    public OEdgeType getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(OEdgeType edgeType) {
        this.edgeType = edgeType;
    }


    @Override
    public int hashCode() {
        int result = entity.hashCode();
        result = 31 * result + edgeType.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OEEClassMapper that = (OEEClassMapper) o;

        if (!entity.equals(that.entity)) return false;
        if (!edgeType.equals(that.edgeType)) return false;
        if (!attribute2property.equals(that.attribute2property)) return false;
        return property2attribute.equals(that.property2attribute);
    }

    @Override
    public String toString() {
        String s = "{" + "Entity = " + entity.getName() + ", Edge-Type = " + edgeType.getName() + ", attributes2properties: ";

        s += "[";
        for(String attribute: this.attribute2property.keySet()) {
            s += attribute + " --> " + attribute2property.get(attribute) + ", ";
        }
        s = s.substring(0, s.length()-1);
        s += "]}";
        return s;
    }
}
