/*
 *
 *  *  Copyright 2010-2017 OrientDB LTD (http://orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://orientdb.com
 *
 */

package com.orientechnologies.teleporter.importengine.rdbms.graphengine;

import com.orientechnologies.orient.core.exception.OValidationException;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.output.OOutputStreamManager;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.context.OTeleporterStatistics;
import com.orientechnologies.teleporter.exception.OTeleporterRuntimeException;
import com.orientechnologies.teleporter.mapper.rdbms.OAggregatorEdge;
import com.orientechnologies.teleporter.mapper.rdbms.OER2GraphMapper;
import com.orientechnologies.teleporter.model.dbschema.OAttribute;
import com.orientechnologies.teleporter.model.dbschema.OCanonicalRelationship;
import com.orientechnologies.teleporter.model.dbschema.OEntity;
import com.orientechnologies.teleporter.model.dbschema.OLogicalRelationship;
import com.orientechnologies.teleporter.model.graphmodel.OEdgeType;
import com.orientechnologies.teleporter.model.graphmodel.OModelProperty;
import com.orientechnologies.teleporter.model.graphmodel.OVertexType;
import com.orientechnologies.teleporter.persistence.handler.ODBMSDataTypeHandler;
import com.orientechnologies.teleporter.util.OFunctionsHandler;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientEdge;
import com.tinkerpop.blueprints.impls.orient.OrientElement;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Executes the necessary operations of insert and upsert for the destination Orient DB populating.
 *
 * @author Gabriele Ponzi
 * @email <g.ponzi--at--orientdb.com>
 */

public class OGraphEngineForDB {

  private OER2GraphMapper      mapper;
  private ODBMSDataTypeHandler handler;

  public OGraphEngineForDB(OER2GraphMapper mapper, ODBMSDataTypeHandler handler) {
    this.mapper = mapper;
    this.handler = handler;
  }

  /**
   * Return true if the record is "full-imported" in OrientDB: the correspondent vertex is visited (all properties are set).
   *
   * @param record
   *
   * @throws SQLException
   */
  public boolean alreadyFullImportedInOrient(OrientBaseGraph orientGraph, ResultSet record, OVertexType vertexType,
      Set<String> propertiesOfIndex) throws SQLException {

    String propsAndValuesOfKey = "";

    try {

      boolean toResolveNames = false;
      // building keys and values for the lookup

      if (propertiesOfIndex == null) {
        toResolveNames = true;
        propertiesOfIndex = new LinkedHashSet<String>();

        for (OModelProperty currentProperty : vertexType.getAllProperties()) {
          // only attribute coming from the primary key are given
          if (currentProperty.isFromPrimaryKey())
            propertiesOfIndex.add(currentProperty.getName());
        }
      }

      String[] propertyOfKey = new String[propertiesOfIndex.size()];
      String[] valueOfKey = new String[propertiesOfIndex.size()];

      int cont = 0;
      for (String property : propertiesOfIndex) {
        propertyOfKey[cont] = property;
        if (toResolveNames)
          valueOfKey[cont] = record.getString(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, property));
        else
          valueOfKey[cont] = record.getString(property);

        cont++;
      }

      String s = "Keys and values in the lookup (upsertVisitedVertex):\t";
      for (int i = 0; i < propertyOfKey.length; i++) {
        propsAndValuesOfKey += propertyOfKey[i] + ":" + valueOfKey[i] + ",";
      }
      if (propsAndValuesOfKey.length() > 0)
        propsAndValuesOfKey = propsAndValuesOfKey.substring(0, propsAndValuesOfKey.length() - 1);
      else
        propsAndValuesOfKey = "no identifier for the current record.";
      s += propsAndValuesOfKey;

      // lookup
      OrientVertex vertex = this.getVertexByIndexedKey(orientGraph, propertyOfKey, valueOfKey, vertexType.getName());

      if (vertex != null && vertexType.getAllProperties().size() <= vertex.getPropertyKeys()
          .size()) // there aren't properties to add into the vertex (<=)
        return true;

    } catch (Exception e) {
      String mess = "Problem encountered during the visit of an inserted vertex. Vertex Type: " + vertexType.getName()
          + ";\tOriginal Record: " + propsAndValuesOfKey;
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      if (orientGraph != null)
        orientGraph.shutdown();
      throw new OTeleporterRuntimeException(e);
    }
    return false;
  }

  /**
   * The method performs a lookup on the passed ODatabaseDocument for a OVertex, starting from a record and from a vertex type.
   * It returns the vertex if present, null if not present.
   *
   * @param orientGraph
   * @param keys
   * @param values
   * @param vertexClassName
   *
   * @return
   */
  public OrientVertex getVertexByIndexedKey(OrientBaseGraph orientGraph, String[] keys, String[] values, String vertexClassName) {

    OrientVertex vertex = null;

    Iterator<Vertex> iterator = orientGraph.getVertices(vertexClassName, keys, values).iterator();

    if (iterator.hasNext())
      vertex = (OrientVertex) iterator.next();

    return vertex;
  }

  /**
   * @param record
   */
  public Vertex upsertVisitedVertex(OrientBaseGraph orientGraph, ResultSet record, OVertexType vertexType,
      Set<String> propertiesOfIndex) {

    OrientVertex vertex = null;
    String[] propertyOfKey = null;
    String[] valueOfKey = null;
    String propsAndValuesOfKey = "";

    try {

      OTeleporterStatistics statistics = OTeleporterContext.getInstance().getStatistics();

      // building keys and values for the lookup
      propertyOfKey = new String[propertiesOfIndex.size()];
      valueOfKey = new String[propertiesOfIndex.size()];
      String currentValue;

      int cont = 0;
      for (String property : propertiesOfIndex) {
        propertyOfKey[cont] = property;
        String attributeName = this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, property);
        currentValue = record.getString(attributeName);

        // converting eventual "t" or "f" values in "true" and "false"
        OModelProperty prop = vertexType.getPropertyByNameAmongAll(property);
        if (prop.getOriginalType().equalsIgnoreCase("boolean")) {
          switch (currentValue) {
          case "t":
            currentValue = "true";
            break;
          case "f":
            currentValue = "false";
            break;
          default:
            break;
          }
        }

        valueOfKey[cont] = currentValue;
        cont++;
      }

      String s = "Keys and values in the lookup (upsertVisitedVertex):\t";
      for (int i = 0; i < propertyOfKey.length; i++) {
        propsAndValuesOfKey += propertyOfKey[i] + ":" + valueOfKey[i] + ",";
      }
      if (propsAndValuesOfKey.length() > 0)
        propsAndValuesOfKey = propsAndValuesOfKey.substring(0, propsAndValuesOfKey.length() - 1);
      else
        propsAndValuesOfKey = "no identifier for the current record.";
      s += propsAndValuesOfKey;
      if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
        OTeleporterContext.getInstance().getMessageHandler().debug(this, "\n" + s + "\n");
      }

      // lookup (only if properties and values are different from null)
      if (propertyOfKey.length > 0 && valueOfKey.length > 0)
        vertex = this.getVertexByIndexedKey(orientGraph, propertyOfKey, valueOfKey, vertexType.getName());

      // extraction of inherited and not inherited properties from the record (through "getAllProperties()" method)
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      String currentPropertyType;
      String currentPropertyName = null;

      for (OModelProperty currentProperty : vertexType.getAllProperties()) {

        if (currentProperty.isIncludedInMigration()) {
          currentPropertyName = currentProperty.getName();
          currentPropertyType = OTeleporterContext.getInstance().getDataTypeHandler()
              .resolveType(currentProperty.getOriginalType().toLowerCase(Locale.ENGLISH)).toString();
          String currentOriginalType = currentProperty.getOriginalType();

          try {
            extractPropertiesFromRecordIntoVertex(record, properties, currentPropertyType, currentPropertyName, currentOriginalType,
                vertexType);
          } catch (Exception e) {
            String mess =
                "Problem encountered during the extraction of the values from the records. Vertex Type: " + vertexType.getName()
                    + ";\tProperty: " + currentPropertyName + ";\tRecord: " + propsAndValuesOfKey;
            OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
            OTeleporterContext.getInstance().printExceptionStackTrace(e, "debug");
          }
        }
      }
      if (vertexType.getName().equalsIgnoreCase("generationnotificationevent")) {
        int a = 4 + 5;
      }

      if (vertex == null) {
        String classAndClusterName = vertexType.getName();
        vertex = this.addVertexToGraph(orientGraph, classAndClusterName, properties);
        statistics.orientAddedVertices++;
        if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
          OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nLoaded properties: %s\n", properties.toString());
          OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNew vertex inserted (all props set): %s\n", vertex.toString());
        }
      } else {

        // discerning between a reached-vertex updating (only original primary key's properties are present) and a full-vertex updating
        boolean justReachedVertex = true;

        // first check: if the current vertex has less properties then the correspondent vertex type then it's justReached
        if (vertex.getPropertyKeys().size() >= vertexType.getAllProperties().size()) {
          justReachedVertex = false;
        }

        // second check: the set properties on the current vertex are only those correspondent to the primary key's attributes
        if (justReachedVertex) {
          for (String property : vertex.getPropertyKeys()) {
            if (!this.containsProperty(propertiesOfIndex, property)) {
              justReachedVertex = false;
              break;
            }
          }
        }

        // UPDATING A REACHED VERTEX (only original primary key's properties are present)
        if (justReachedVertex) {

          // setting new properties and save
          this.setElementProperties(vertex, properties);
          vertex.save();
          if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
            OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nLoaded properties: %s\n", properties.toString());
            OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNew vertex inserted (all props set): %s\n", vertex.toString());
          }
        }

        // UPDATING A FULL VERTEX
        else {

          // comparing old version of vertex with the new one: if the two versions are equals no rewriting is performed

          boolean equalVersions = true;
          boolean equalProperties = true;

          if (vertex.getPropertyKeys().size() == properties.size()) {

            // comparing properties
            for (String propertyName : vertex.getPropertyKeys()) {
              if (!properties.keySet().contains(propertyName)) {
                equalProperties = false;
                equalVersions = false;
                break;
              }
            }

            if (equalProperties) {
              // comparing values of the properties
              for (String propertyName : vertex.getPropertyKeys()) {
                if (!(vertex.getProperty(propertyName) == null && properties.get(propertyName) == null)) {
                  currentPropertyType = OTeleporterContext.getInstance().getDataTypeHandler()
                      .resolveType(vertexType.getPropertyByName(propertyName).getOriginalType().toLowerCase(Locale.ENGLISH))
                      .toString();
                  if (!this.areEquals(vertex.getProperty(propertyName), properties.get(propertyName), currentPropertyType,
                      currentPropertyName)) {
                    equalVersions = false;
                    break;
                  }
                }
              }
            } else {
              equalVersions = false;
            }
          } else {
            equalVersions = false;
          }

          if (!equalVersions) {
            // removing old eventual properties
            for (String propertyKey : vertex.getPropertyKeys()) {
              vertex.removeProperty(propertyKey);
            }

            // setting new properties and save
            this.setElementProperties(vertex, properties);
            statistics.orientUpdatedVertices++;
            if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
              OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nLoaded properties: %s\n", properties.toString());
              OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNew vertex upserted (all props set): %s\n", vertex.toString());
            }
          }
        }
      }
    } catch (Exception e) {
      String mess = "Problem encountered during the migration of the records. Vertex Type: " + vertexType.getName() + ";\tRecord: "
          + propsAndValuesOfKey;
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      if (orientGraph != null)
        orientGraph.shutdown();
      throw new OTeleporterRuntimeException(e);
    }

    return vertex;
  }

  public void extractPropertiesFromRecordIntoVertex(ResultSet record, Map<String, Object> properties, String currentPropertyType,
      String currentPropertyName, String currentOriginalType, OVertexType vertexType) throws SQLException {

    Date currentDateValue;
    byte[] currentBinaryValue;
    String currentAttributeValue;

    // disambiguation on OrientDB Schema type

    if (currentPropertyType.equals("DATE")) {
      currentDateValue = record.getDate(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, currentPropertyName));
      properties.put(currentPropertyName, currentDateValue);
    } else if (currentPropertyType.equals("DATETIME")) {
      currentDateValue = record.getTimestamp(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, currentPropertyName));
      properties.put(currentPropertyName, currentDateValue);
    } else if (currentPropertyType.equals("BINARY")) {
      currentBinaryValue = record.getBytes(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, currentPropertyName));
      properties.put(currentPropertyName, currentBinaryValue);
    } else if (currentPropertyType.equals("BOOLEAN")) {
      currentAttributeValue = record
          .getString(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, currentPropertyName));

      switch (currentAttributeValue) {

      case "t":
        properties.put(currentPropertyName, "true");
        break;
      case "f":
        properties.put(currentPropertyName, "false");
        break;
      default:
        break;
      }
    }

    // JSON
    else if (handler.jsonImplemented && currentPropertyType.equals("EMBEDDED")) {
      currentAttributeValue = record
          .getString(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, currentPropertyName));
      ODocument currentEmbeddedValue = this.handler.convertJSONToDocument(currentPropertyName, currentAttributeValue);
      properties.put(currentPropertyName, currentEmbeddedValue);
    }

    // GEOSPATIAL
    else if (handler.geospatialImplemented && handler.isGeospatial(currentOriginalType)) {
      currentAttributeValue = record
          .getString(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, currentPropertyName));
      //						currentEmbeddedValue = OShapeFactory.INSTANCE.toDoc(currentAttributeValue);  // to change with transformation from wkt (currentAttrValue) into embedded
      ODocument currentEmbeddedValue = null;
      properties.put(currentPropertyName, currentEmbeddedValue);
    } else {
      currentAttributeValue = record
          .getString(this.mapper.getAttributeNameByVertexTypeAndProperty(vertexType, currentPropertyName));
      properties.put(currentPropertyName, currentAttributeValue);
    }

  }

  public void extractPropertiesFromRecordIntoEdge(ResultSet record, Map<String, Object> properties, String currentPropertyType,
      String currentPropertyName, String currentOriginalType, OEdgeType edgeType) throws SQLException {

    Date currentDateValue;
    byte[] currentBinaryValue;
    String currentAttributeValue;

    // disambiguation on OrientDB Schema type

    if (currentPropertyType.equals("DATE")) {
      currentDateValue = record.getDate(this.mapper.getAttributeNameByEdgeTypeAndProperty(edgeType, currentPropertyName));
      properties.put(currentPropertyName, currentDateValue);
    } else if (currentPropertyType.equals("DATETIME")) {
      currentDateValue = record.getTimestamp(this.mapper.getAttributeNameByEdgeTypeAndProperty(edgeType, currentPropertyName));
      properties.put(currentPropertyName, currentDateValue);
    } else if (currentPropertyType.equals("BINARY")) {
      currentBinaryValue = record.getBytes(this.mapper.getAttributeNameByEdgeTypeAndProperty(edgeType, currentPropertyName));
      properties.put(currentPropertyName, currentBinaryValue);
    } else if (currentPropertyType.equals("BOOLEAN")) {
      currentAttributeValue = record.getString(this.mapper.getAttributeNameByEdgeTypeAndProperty(edgeType, currentPropertyName));

      switch (currentAttributeValue) {

      case "t":
        properties.put(currentPropertyName, "true");
        break;
      case "f":
        properties.put(currentPropertyName, "false");
        break;
      default:
        break;
      }
    }

    // JSON
    else if (handler.jsonImplemented && currentPropertyType.equals("EMBEDDED")) {
      currentAttributeValue = record.getString(this.mapper.getAttributeNameByEdgeTypeAndProperty(edgeType, currentPropertyName));
      ODocument currentEmbeddedValue = this.handler.convertJSONToDocument(currentPropertyName, currentAttributeValue);
      properties.put(currentPropertyName, currentEmbeddedValue);
    }

    // GEOSPATIAL
    else if (handler.geospatialImplemented && handler.isGeospatial(currentOriginalType)) {
      currentAttributeValue = record.getString(this.mapper.getAttributeNameByEdgeTypeAndProperty(edgeType, currentPropertyName));
      //						currentEmbeddedValue = OShapeFactory.INSTANCE.toDoc(currentAttributeValue);  // to change with transformation from wkt (currentAttrValue) into embedded
      ODocument currentEmbeddedValue = null;
      properties.put(currentPropertyName, currentEmbeddedValue);
    } else {
      currentAttributeValue = record.getString(this.mapper.getAttributeNameByEdgeTypeAndProperty(edgeType, currentPropertyName));
      properties.put(currentPropertyName, currentAttributeValue);
    }

  }

  /**
   * @param propertiesOfIndex
   * @param property
   *
   * @return
   */
  private boolean containsProperty(Set<String> propertiesOfIndex, String property) {

    for (String currentProp : propertiesOfIndex) {
      if (currentProp.equalsIgnoreCase(property))
        return true;
    }

    return false;
  }

  /**
   * @param oldProperty
   * @param newProperty
   * @param currentPropertyType
   *
   * @return
   */
  private boolean areEquals(Object oldProperty, Object newProperty, String currentPropertyType, String currentPropertyName) {

    if (oldProperty != null && newProperty != null) {

      if (currentPropertyType.equals("BINARY")) {
        byte[] oldPropertyBinary = (byte[]) oldProperty;
        byte[] newPropertyBinary = (byte[]) newProperty;
        return Arrays.equals(oldPropertyBinary, newPropertyBinary);
      } else if (currentPropertyType.equals("BOOLEAN")) {

        if (oldProperty.toString().equalsIgnoreCase(newProperty.toString()))
          return true;

        else if (oldProperty.toString().equalsIgnoreCase("t") && newProperty.toString().equalsIgnoreCase("true")
            || oldProperty.toString().equalsIgnoreCase("f") && newProperty.toString().equalsIgnoreCase("false"))
          return true;

        else
          return false;
      } else if (currentPropertyType.equals("DATE")) {

        // oldProperty : Date (year, month, day)
        Calendar oldDate = Calendar.getInstance();
        oldDate.setTime((Date) oldProperty);
        Calendar newDate = Calendar.getInstance();
        newDate.setTime((Date) newProperty);

        if (oldDate.get(Calendar.ERA) == newDate.get(Calendar.ERA) && oldDate.get(Calendar.YEAR) == newDate.get(Calendar.YEAR)
            && oldDate.get(Calendar.MONTH) == newDate.get(Calendar.MONTH) && oldDate.get(Calendar.DAY_OF_MONTH) == newDate
            .get(Calendar.DAY_OF_MONTH)) {
          return true;
        } else {
          return false;
        }
      } else if (currentPropertyType.equals("DATETIME")) {
        // oldProperty : Date (year, month, day, hours, minutes, seconds, millis)
        return ((Date) oldProperty).equals((Date) newProperty);
      } else if (currentPropertyType.equals("DECIMAL")) {
        return oldProperty.equals(new BigDecimal(newProperty.toString()));
      } else if (currentPropertyType.equals("DOUBLE")) {
        return oldProperty.equals(new Double(newProperty.toString()));
      } else if (currentPropertyType.equals("FLOAT")) {
        return oldProperty.equals(new Float(newProperty.toString()));
      } else if (currentPropertyType.equals("INTEGER")) {
        return oldProperty.equals(new Integer(newProperty.toString()));
      } else if (currentPropertyType.equals("LONG")) {
        return oldProperty.equals(new Long(newProperty.toString()));
      } else if (currentPropertyType.equals("SHORT")) {
        return oldProperty.equals(new Short(newProperty.toString()));
      } else if (handler.jsonImplemented && currentPropertyType.equals("EMBEDDED")) {
        boolean areEquals = OFunctionsHandler.haveDocumentsSameContent(((ODocument) oldProperty), ((ODocument) newProperty));
        return areEquals;
      } else {
        return oldProperty.toString().equals(newProperty.toString());
      }
    } else if (oldProperty == null && newProperty == null)
      return true;

    else
      return false;
  }

  /**
   * @param foreignRecord       the record correspondent to the current-out-vertex
   * @param relation            the relation between two entities
   * @param currentOutVertex    the current-out-vertex
   * @param currentInVertexType the type correspondent to the current-in-vertex
   * @param edgeTypeName        type of the OEdgeType present between the two OVertexType, used as label during the insert of the
   *                            edge in the graph
   *                            <p>
   *                            The method executes insert on reached vertex: - if the vertex is not already reached, it's inserted
   *                            in the graph and an edge between the out-visited-vertex and the in-reached-vertex is added - if the
   *                            vertex is already present in the graph no update is performed, neither on reached-vertex neither on
   *                            the relative edge
   *
   * @throws SQLException
   */

  public OrientVertex upsertReachedVertexWithEdge(OrientBaseGraph orientGraph, ResultSet foreignRecord,
      OCanonicalRelationship relation, OrientVertex currentOutVertex, OVertexType currentInVertexType, String edgeTypeName)
      throws SQLException {

    OrientVertex currentInVertex = null;
    String propsAndValuesOfKey = "";
    String direction = relation.getDirection();

    try {

      OTeleporterStatistics statistics = OTeleporterContext.getInstance().getStatistics();

      // building keys and values for the lookup
      List<OAttribute> fromColumns = relation.getFromColumns();
      String[] propertyOfKey = new String[fromColumns.size()];
      String[] valueOfKey = new String[fromColumns.size()];

      int index = 0;
      for (OAttribute foreignAttribute : fromColumns) {
        String attributeName = relation.getToColumns().get(index).getName();
        propertyOfKey[index] = mapper.getPropertyNameByVertexTypeAndAttribute(currentInVertexType, attributeName);
        valueOfKey[index] = foreignRecord.getString((foreignAttribute.getName()));
        index++;
      }

      if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
        String s = "Keys and values in the lookup (upsertVisitedVertex):\t";
        for (int i = 0; i < propertyOfKey.length; i++) {
          propsAndValuesOfKey += propertyOfKey[i] + ":" + valueOfKey[i] + ",";
        }
        if (propsAndValuesOfKey.length() > 0)
          propsAndValuesOfKey = propsAndValuesOfKey.substring(0, propsAndValuesOfKey.length() - 1);
        else
          propsAndValuesOfKey = "no identifier for the current record.";
        s += propsAndValuesOfKey;
        OTeleporterContext.getInstance().getMessageHandler().debug(this, "\n" + s + "\n");
      }

      // new vertex is added only if all the values in the foreign key are different from null
      boolean ok = true;

      for (int i = 0; i < valueOfKey.length; i++) {
        if (valueOfKey[i] == null) {
          ok = false;
          break;
        }
      }

      // all values are different from null, thus vertex is searched in the graph and in case is added if not found.
      if (ok) {

        currentInVertex = this.getVertexByIndexedKey(orientGraph, propertyOfKey, valueOfKey, currentInVertexType.getName());

        /*
         *  if the vertex is not already present in the graph it's built, set and inserted to the graph,
         *  then the edge between the current-out-vertex and the current-in-vertex is added
         */
        if (currentInVertex == null) {

          Map<String, Object> partialProperties = new LinkedHashMap<String, Object>();

          // for each attribute in the foreign key belonging to the relationship, attribute name and correspondent value are added to a 'properties map'
          for (int i = 0; i < propertyOfKey.length; i++) {
            partialProperties.put(propertyOfKey[i], valueOfKey[i]);
          }

          String classAndClusterName = currentInVertexType.getName();
          currentInVertex = this.addVertexToGraph(orientGraph, classAndClusterName, partialProperties);
          statistics.orientAddedVertices++;
          if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
            OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNEW Reached vertex (id:value) --> %s:%s\n", Arrays.toString(propertyOfKey), Arrays.toString(valueOfKey));
            OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNew vertex inserted (only pk props set): %s\n", currentInVertex.toString());
          }

        } else {
          if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
            OTeleporterContext.getInstance().getMessageHandler()
                .debug(this, "\nNOT NEW Reached vertex, vertex %s:%s already present in the Orient Graph.\n", Arrays.toString(propertyOfKey), Arrays.toString(valueOfKey));
          }
        }

        // upsert of the edge between the currentOutVertex and the currentInVertex
        this.upsertEdge(orientGraph, currentOutVertex, currentInVertex, edgeTypeName, null, direction);
      }

    } catch (Exception e) {
      String mess = "Problem encountered during the upsert of a reached vertex. Vertex Type: " + currentInVertexType.getName()
          + ";\tOriginal Record: " + propsAndValuesOfKey;
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      if (orientGraph != null)
        orientGraph.shutdown();
      throw new OTeleporterRuntimeException(e);
    }

    return currentInVertex;
  }

  /**
   * Dead!
   *
   * @param orientGraph
   * @param relation
   * @param currentOutVertex
   * @param currentOutVertexType
   * @param currentInVertexType
   * @param edgeTypeName
   */
  public void connectVertexToRelatedVertices(OrientBaseGraph orientGraph, OLogicalRelationship relation, Vertex currentOutVertex,
      OVertexType currentOutVertexType, OVertexType currentInVertexType, String edgeTypeName) {

    String propsAndValuesOfKey = "";
    String direction = relation.getDirection();

    try {

      // building keys and values for the lookup
      List<OAttribute> fromColumns = relation.getFromColumns();
      String[] propertyOfKey = new String[fromColumns.size()];
      String[] valueOfKey = new String[fromColumns.size()];

      int index = 0;
      for (OAttribute foreignAttribute : fromColumns) {
        String attributeName = relation.getToColumns().get(index).getName();
        propertyOfKey[index] = mapper.getPropertyNameByVertexTypeAndAttribute(currentInVertexType, attributeName);

        String outVertexPropertyName = mapper
            .getPropertyNameByVertexTypeAndAttribute(currentOutVertexType, foreignAttribute.getName());
        valueOfKey[index] = currentOutVertex.getProperty(outVertexPropertyName);
        index++;
      }

      if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
        String s = "Keys and values in the lookup (upsertVisitedVertex):\t";
        for (int i = 0; i < propertyOfKey.length; i++) {
          propsAndValuesOfKey += propertyOfKey[i] + ":" + valueOfKey[i] + ",";
        }
        if (propsAndValuesOfKey.length() > 0)
          propsAndValuesOfKey = propsAndValuesOfKey.substring(0, propsAndValuesOfKey.length() - 1);
        else
          propsAndValuesOfKey = "no identifier for the current record.";
        s += propsAndValuesOfKey;
        OTeleporterContext.getInstance().getMessageHandler().debug(this, "\n" + s + "\n");
      }

      // new vertex is added only if all the values in the foreign key are different from null
      boolean ok = true;

      for (int i = 0; i < valueOfKey.length; i++) {
        if (valueOfKey[i] == null) {
          ok = false;
          break;
        }
      }

      // all values are different from null, thus vertex is searched in the graph and in case is added if not found.
      if (ok) {

        int verticesCount = (int) orientGraph.getVertexType(currentInVertexType.getName()).count();
        OTeleporterContext.getInstance().getStatistics().leftVerticesCurrentLogicalRelationship = verticesCount;
        Iterable<Vertex> inVertices = orientGraph.getVertices(currentInVertexType.getName(), propertyOfKey, valueOfKey);

        for (Vertex currentInVertex : inVertices) {
          this.insertEdge(orientGraph, (OrientVertex) currentOutVertex, (OrientVertex) currentInVertex, edgeTypeName, null,
              direction);
        }

      }

    } catch (Exception e) {
      String mess =
          "Problem encountered during the insert of the edge between two vertices. outVertexType: " + currentOutVertexType.getName()
              + ", inVertexType: " + currentInVertexType.getName();
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      if (orientGraph != null)
        orientGraph.shutdown();
      throw new OTeleporterRuntimeException(e);
    }

  }

  public void upsertEdge(OrientBaseGraph orientGraph, OrientVertex currentOutVertex, OrientVertex currentInVertex, String edgeType,
      Map<String, Object> properties, String direction) {

    try {

      boolean edgeAlreadyPresent = false;
      Iterator<Edge> it = currentOutVertex.getEdges(Direction.OUT, edgeType).iterator();
      Edge currentEdge;

      OTeleporterStatistics statistics = OTeleporterContext.getInstance().getStatistics();

      if (it.hasNext()) {
        while (it.hasNext()) {
          currentEdge = it.next();

          if (((OrientVertex) currentEdge.getVertex(Direction.IN)).getId().equals(currentInVertex.getId())) {
            edgeAlreadyPresent = true;
            break;
          }
        }
        if (edgeAlreadyPresent) {
          if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
            OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nEdge between '%s' and '%s' already present.\n", currentOutVertex.toString(), currentInVertex.toString());
          }
        } else {
          OrientEdge edge = null;
          if (direction != null && direction.equals("direct")) {
            edge = this.addEdgeToGraph(orientGraph, null, currentOutVertex, currentInVertex, edgeType);
          } else if (direction != null && direction.equals("inverse")) {
            edge = this.addEdgeToGraph(orientGraph, null, currentInVertex, currentOutVertex, edgeType);
          }
          this.setElementProperties(edge, properties);
          statistics.orientAddedEdges++;
          if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
            OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNew edge inserted: %s\n", edge.toString());
          }
        }
      } else {
        OrientEdge edge = null;
        if (direction != null && direction.equals("direct")) {
          edge = this.addEdgeToGraph(orientGraph, null, currentOutVertex, currentInVertex, edgeType);
        } else if (direction != null && direction.equals("inverse")) {
          edge = this.addEdgeToGraph(orientGraph, null, currentInVertex, currentOutVertex, edgeType);
        }
        this.setElementProperties(edge, properties);
        statistics.orientAddedEdges++;
        if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
          OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNew edge inserted: %s\n", edge.toString());
        }
      }
    } catch (Exception e) {
      String mess =
          "Problem encountered during the upsert of an edge. Vertex-out: " + currentOutVertex + ";\tVertex-in: " + currentInVertex;
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      if (orientGraph != null)
        orientGraph.shutdown();
      throw new OTeleporterRuntimeException(e);
    }
  }

  /**
   * Dead!
   *
   * @param orientGraph
   * @param currentOutVertex
   * @param currentInVertex
   * @param edgeType
   * @param properties
   * @param direction
   */
  public void insertEdge(OrientBaseGraph orientGraph, OrientVertex currentOutVertex, OrientVertex currentInVertex, String edgeType,
      Map<String, Object> properties, String direction) {

    OTeleporterStatistics statistics = OTeleporterContext.getInstance().getStatistics();

    try {
      OrientEdge edge = null;
      if (direction != null && direction.equals("direct")) {
        edge = this.addEdgeToGraph(orientGraph, null, currentOutVertex, currentInVertex, edgeType);
      } else if (direction != null && direction.equals("inverse")) {
        edge = this.addEdgeToGraph(orientGraph, null, currentInVertex, currentOutVertex, edgeType);
      }
      this.setElementProperties(edge, properties);
      statistics.orientAddedEdges++;
      statistics.doneLeftVerticesCurrentLogicalRelationship++;
      if(OTeleporterContext.getInstance().getMessageHandler().getOutputManagerLevel() == OOutputStreamManager.DEBUG_LEVEL) {
        OTeleporterContext.getInstance().getMessageHandler().debug(this, "\nNew edge inserted: %s\n", edge.toString());
      }

    } catch (Exception e) {
      String mess =
          "Problem encountered during the insert of an edge. Vertex-out: " + currentOutVertex + ";\tVertex-in: " + currentInVertex;
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      if (orientGraph != null)
        orientGraph.shutdown();
      throw new OTeleporterRuntimeException(e);
    }
  }

  public void upsertAggregatorEdge(OrientBaseGraph orientGraph, ResultSet jointTableRecord, OEntity joinTable,
      OAggregatorEdge aggregatorEdge) throws SQLException {

    try {

      Iterator<OCanonicalRelationship> it = joinTable.getOutCanonicalRelationships().iterator();
      OCanonicalRelationship relationship1 = it.next();
      OCanonicalRelationship relationship2 = it.next();

      // Building keys and values for out-vertex lookup

      String[] keysOutVertex = new String[relationship1.getFromColumns().size()];
      String[] valuesOutVertex = new String[relationship1.getFromColumns().size()];

      int index = 0;
      for (OAttribute foreignKeyAttribute : relationship1.getFromColumns()) {
        keysOutVertex[index] = this.mapper.getPropertyNameByEntityAndAttribute(relationship1.getParentEntity(),
            relationship1.getToColumns().get(index).getName());
        valuesOutVertex[index] = jointTableRecord.getString(foreignKeyAttribute.getName());
        index++;
      }

      // Building keys and values for in-vertex lookup

      String[] keysInVertex = new String[relationship2.getToColumns().size()];
      String[] valuesInVertex = new String[relationship2.getToColumns().size()];

      index = 0;
      for (OAttribute foreignKeyAttribute : relationship2.getFromColumns()) {
        keysInVertex[index] = this.mapper.getPropertyNameByEntityAndAttribute(relationship2.getParentEntity(),
            relationship2.getToColumns().get(index).getName());
        valuesInVertex[index] = jointTableRecord.getString(foreignKeyAttribute.getName());
        index++;
      }

      // String direction
      String direction = joinTable.getDirectionOfN2NRepresentedRelationship();

      OrientVertex currentOutVertex;
      OrientVertex currentInVertex;
      if (direction.equals("direct")) {
        currentOutVertex = this
            .getVertexByIndexedKey(orientGraph, keysOutVertex, valuesOutVertex, aggregatorEdge.getOutVertexClassName());
        currentInVertex = this
            .getVertexByIndexedKey(orientGraph, keysInVertex, valuesInVertex, aggregatorEdge.getInVertexClassName());
      } else {
        currentOutVertex = this
            .getVertexByIndexedKey(orientGraph, keysOutVertex, valuesOutVertex, aggregatorEdge.getInVertexClassName());
        currentInVertex = this
            .getVertexByIndexedKey(orientGraph, keysInVertex, valuesInVertex, aggregatorEdge.getOutVertexClassName());
      }

      // extracting edge properties from the join table
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      OEdgeType edgeType = aggregatorEdge.getEdgeType();

      for (OModelProperty currentProperty : edgeType.getAllProperties()) {

        String currentPropertyName = currentProperty.getName();
        String currentPropertyType = currentProperty.getOrientdbType();
        if (currentProperty.getOrientdbType() == null) { // superfluous ?!
          currentPropertyType = OTeleporterContext.getInstance().getDataTypeHandler()
              .resolveType(currentProperty.getOriginalType().toLowerCase(Locale.ENGLISH)).toString();
        }
        String currentOriginalType = currentProperty.getOriginalType();
        OVertexType joinVertexType = this.mapper.getJoinVertexTypeByAggregatorEdge(edgeType.getName());

        try {
          extractPropertiesFromRecordIntoVertex(jointTableRecord, properties, currentPropertyType, currentPropertyName,
              currentOriginalType, joinVertexType);
        } catch (Exception e) {
          String mess = "Problem encountered during the extraction of the values from the records. Edge Type: " + edgeType.getName()
              + ";\tProperty: " + currentProperty.getName() + ";\tOriginal join table: " + joinTable.getName();
          OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
          OTeleporterContext.getInstance().printExceptionStackTrace(e, "debug");
        }
      }

      this.upsertEdge(orientGraph, currentOutVertex, currentInVertex, aggregatorEdge.getEdgeType().getName(), properties,
          direction);

    } catch (Exception e) {
      String mess = "";
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      if (orientGraph != null)
        orientGraph.shutdown();
      throw new OTeleporterRuntimeException(e);
    }
  }

  private OrientVertex addVertexToGraph(OrientBaseGraph orientGraph, String classAndClusterName, Map<String, Object> properties) {

    try {
      if (classAndClusterName != null)
        return orientGraph.addVertex("class:" + classAndClusterName, properties);
    } catch (OValidationException e) {
      OTeleporterContext.getInstance().getStatistics().errorMessages.add(e.getMessage());
    }
    return null;
  }

  private OrientEdge addEdgeToGraph(OrientBaseGraph orientGraph, Object id, OrientVertex currentOutVertex,
      OrientVertex currentInVertex, String edgeType) {

    try {
      return orientGraph.addEdge(id, currentOutVertex, currentInVertex, edgeType);
    } catch (OValidationException e) {
      OTeleporterContext.getInstance().getStatistics().errorMessages.add(e.getMessage());
    }
    return null;
  }

  private void setElementProperties(OrientElement element, Map<String, Object> properties) {

    try {
      element.setProperties(properties);
    } catch (OValidationException e) {
      OTeleporterContext.getInstance().getStatistics().errorMessages.add(e.getMessage());
    }

  }

  public void updateVertexAccordingToLogicalRelationship(OrientVertex currentOutVertex, OVertexType currentInVertexType,
      List<String> fromPropertiesToUpdate) {

    Map<String, String> updatedProps = new LinkedHashMap<String, String>();
    for (String property : fromPropertiesToUpdate) {
      String oldValue = currentOutVertex.getProperty(property);
      String newValue = "$" + currentInVertexType.getName() + ":" + oldValue;
      updatedProps.put(property, newValue);
    }
    currentOutVertex.setProperties(updatedProps);

  }
}
