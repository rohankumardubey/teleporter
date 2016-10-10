/*
 * Copyright 2015 OrientDB LTD (info--at--orientdb.com)
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

package com.orientechnologies.teleporter.strategy.rdbms;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.teleporter.configuration.OConfigurationHandler;
import com.orientechnologies.teleporter.configuration.api.OConfiguredVertexClass;
import com.orientechnologies.teleporter.configuration.api.OSourceTable;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.context.OTeleporterStatistics;
import com.orientechnologies.teleporter.exception.OTeleporterRuntimeException;
import com.orientechnologies.teleporter.factory.ODataTypeHandlerFactory;
import com.orientechnologies.teleporter.factory.ONameResolverFactory;
import com.orientechnologies.teleporter.importengine.rdbms.dbengine.ODBQueryEngine;
import com.orientechnologies.teleporter.importengine.rdbms.graphengine.OGraphEngineForDB;
import com.orientechnologies.teleporter.mapper.OSource2GraphMapper;
import com.orientechnologies.teleporter.mapper.rdbms.OER2GraphMapper;
import com.orientechnologies.teleporter.model.OSourceInfo;
import com.orientechnologies.teleporter.model.dbschema.*;
import com.orientechnologies.teleporter.model.graphmodel.OEdgeType;
import com.orientechnologies.teleporter.model.graphmodel.OVertexType;
import com.orientechnologies.teleporter.nameresolver.ONameResolver;
import com.orientechnologies.teleporter.persistence.handler.ODBMSDataTypeHandler;
import com.orientechnologies.teleporter.persistence.util.OQueryResult;
import com.orientechnologies.teleporter.strategy.OWorkflowStrategy;
import com.orientechnologies.teleporter.util.OJSONConfigurationManager;
import com.orientechnologies.teleporter.util.OFunctionsHandler;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 *
 */

public abstract class ODBMSImportStrategy implements OWorkflowStrategy {

  protected OER2GraphMapper mapper;

  public ODBMSImportStrategy() {}

  @Override
  public ODocument executeStrategy(OSourceInfo sourceInfo, String outOrientGraphUri, String chosenMapper, String xmlPath, String nameResolverConvention,
                              List<String> includedTables, List<String> excludedTables, String migrationConfigPath, OTeleporterContext context) {

    OSourceDatabaseInfo sourceDBInfo = (OSourceDatabaseInfo) sourceInfo;
    Date globalStart = new Date();

    ODataTypeHandlerFactory dataTypeHandlerFactory = new ODataTypeHandlerFactory();
    ODBMSDataTypeHandler handler = (ODBMSDataTypeHandler) dataTypeHandlerFactory.buildDataTypeHandler(sourceDBInfo.getDriverName(), context);
    OConfigurationHandler configHandler = this.buildConfigurationHandler();

    /*
     * Step 1,2,3
     */
    ONameResolverFactory nameResolverFactory = new ONameResolverFactory();
    ONameResolver nameResolver = nameResolverFactory.buildNameResolver(nameResolverConvention, context);
    context.getStatistics().runningStepNumber = -1;

    // manage conf if present: loading
    OJSONConfigurationManager confManager = new OJSONConfigurationManager();
    ODocument config = confManager.loadMigrationConfig(outOrientGraphUri, migrationConfigPath, context);

    this.mapper = this.createSchemaMapper(sourceDBInfo, outOrientGraphUri, chosenMapper, xmlPath, nameResolver, handler,
            includedTables, excludedTables, config, configHandler, context);

    // Step 4: Import
    this.executeImport(sourceDBInfo, outOrientGraphUri, mapper, handler, context);
    context.getStatistics().notifyListeners();
    context.getOutputManager().info("\n");
    context.getStatistics().runningStepNumber = -1;

    Date globalEnd = new Date();

    context.getOutputManager().info("\n\nImporting complete in %s\n", OFunctionsHandler.getHMSFormat(globalStart, globalEnd));
    context.getOutputManager().info(context.getStatistics().toString());

    return null;

  }

  protected abstract OConfigurationHandler buildConfigurationHandler();

  public abstract OER2GraphMapper createSchemaMapper(OSourceDatabaseInfo sourceDBInfo, String outOrientGraphUri, String chosenMapper,
      String xmlPath, ONameResolver nameResolver, ODBMSDataTypeHandler handler, List<String> includedTables, List<String> excludedTables,
      ODocument migrationConfig, OConfigurationHandler configHandler, OTeleporterContext context);


  public abstract void executeImport(OSourceDatabaseInfo sourceDBInfo, String outOrientGraphUri, OSource2GraphMapper mapper, ODBMSDataTypeHandler handler, OTeleporterContext context);

  protected void importRecordsIntoVertexClass(List<OEntity> mappedEntities, String[][] aggregationColumns, OVertexType currentOutVertexType, ODBQueryEngine dbQueryEngine, OGraphEngineForDB graphEngine, OrientBaseGraph orientGraph, OTeleporterContext context) throws SQLException {

    OTeleporterStatistics statistics = context.getStatistics();
    OQueryResult queryResult;
    ResultSet records;
    OrientVertex currentOutVertex;
    OVertexType currentInVertexType;
    OEdgeType edgeType;// for each entity in dbSchema all records are retrieved
    int numberOfAggregatedClasses = mappedEntities.size();
    if(numberOfAggregatedClasses == 1) {
      queryResult = dbQueryEngine.getRecordsByEntity(mappedEntities.get(0), context);
    }
    else {
      queryResult = dbQueryEngine.getRecordsFromMultipleEntities(mappedEntities, aggregationColumns, context);
    }

    //if (handler.geospatialImplemented && super.hasGeospatialAttributes(entity, handler)) {
    //  String query = handler.buildGeospatialQuery(entity, context);
    //  queryResult = dbQueryEngine.executeQuery(query, context);
    //}

    records = queryResult.getResult();
    ResultSet currentRecord = null;

    // each record is imported as vertex in the orient graph
    while(records.next()) {

      // upsert of the vertex
      currentRecord = records;
      currentOutVertex = (OrientVertex) graphEngine.upsertVisitedVertex(orientGraph, currentRecord, currentOutVertexType, null, context);

      // for each attribute of the entity belonging to the primary key, correspondent relationship is
      // built as edge and for the referenced record a vertex is built (only id)
      for(OEntity entity: mappedEntities) {
        for (ORelationship currentRelationship : entity.getOutRelationships()) {
          OEntity currentParentEntity = mapper.getDataBaseSchema().getEntityByName(currentRelationship.getParentEntity().getName());
          currentInVertexType = mapper.getVertexTypeByEntity(currentParentEntity);

          edgeType = mapper.getRelationship2edgeType().get(currentRelationship);
          graphEngine.upsertReachedVertexWithEdge(orientGraph, currentRecord, currentRelationship, currentOutVertex, currentInVertexType, edgeType.getName(), context);
        }
      }

      // Statistics updated
      statistics.analyzedRecords += 1 * numberOfAggregatedClasses;

    }

    // closing resultset, connection and statement
    queryResult.closeAll(context);
  }


  protected void importEntitiesBelongingToHierarchies(ODBQueryEngine dbQueryEngine, OGraphEngineForDB graphEngine, OrientBaseGraph orientGraph, OTeleporterContext context) {

    for (OHierarchicalBag bag : this.mapper.getDataBaseSchema().getHierarchicalBags()) {

      switch (bag.getInheritancePattern()) {

        case "table-per-hierarchy":
          this.tablePerHierarchyImport(bag, this.mapper, dbQueryEngine, graphEngine, orientGraph, context);
          break;

        case "table-per-type":
          this.tablePerTypeImport(bag, this.mapper, dbQueryEngine, graphEngine, orientGraph, context);
          break;

        case "table-per-concrete-type":
          this.tablePerConcreteTypeImport(bag, this.mapper, dbQueryEngine, graphEngine, orientGraph, context);
          break;

      }
    }
  }

  /**
   * Performs import of all records of the entities contained in the hierarchical bag passed as parameter.
   * Adopted in case of "Table per Hierarchy" inheritance strategy.
   * @param bag
   * @param orientGraph
   * @param context
   */
  protected void tablePerHierarchyImport(OHierarchicalBag bag, OER2GraphMapper mapper, ODBQueryEngine dbQueryEngine, OGraphEngineForDB graphDBCommandEngine, OrientBaseGraph orientGraph, OTeleporterContext context) {

    try {

      OTeleporterStatistics statistics = context.getStatistics();

      OEntity currentParentEntity = null;

      OVertexType currentOutVertexType = null;
      OVertexType currentInVertexType = null;
      OrientVertex currentOutVertex = null;
      OEdgeType edgeType = null;

      String currentDiscriminatorValue;
      Iterator<OEntity> it = bag.getDepth2entities().get(0).iterator();
      OEntity physicalCurrentEntity = it.next();

      OQueryResult queryResult = null;
      ResultSet records = null;

      for(int i=bag.getDepth2entities().size()-1; i>=0; i--) {
        for(OEntity currentEntity: bag.getDepth2entities().get(i)) {
          currentDiscriminatorValue = bag.getEntityName2discriminatorValue().get(currentEntity.getName());

          // for each entity in dbSchema all records are retrieved
          queryResult = dbQueryEngine.getRecordsFromSingleTableByDiscriminatorValue(bag.getDiscriminatorColumn(), currentDiscriminatorValue, physicalCurrentEntity, context);
          records = queryResult.getResult();
          ResultSet currentRecord = null;

          currentOutVertexType = mapper.getVertexTypeByEntity(currentEntity);

          // each record is imported as vertex in the orient graph
          while(records.next()) {

            // upsert of the vertex
            currentRecord = records;
            currentOutVertex = (OrientVertex) graphDBCommandEngine.upsertVisitedVertex(orientGraph, currentRecord, currentOutVertexType, null, context);

            // for each attribute of the entity belonging to the primary key, correspondent relationship is
            // built as edge and for the referenced record a vertex is built (only id)
            for(ORelationship currentRelation: currentEntity.getAllOutRelationships()) {

              currentParentEntity = mapper.getDataBaseSchema().getEntityByNameIgnoreCase(currentRelation.getParentEntity().getName());

              // checking if parent table belongs to a hierarchical bag
              if(currentParentEntity.getHierarchicalBag() == null) {
                currentInVertexType = mapper.getVertexTypeByEntity(currentRelation.getParentEntity());
              }

                // if the parent entity belongs to hierarchical bag, we need to know which is it the more stringent subclass of the record with a certain id
              else {
                String[] propertyOfKey = new String[currentRelation.getForeignKey().getInvolvedAttributes().size()];
                String[] valueOfKey = new String[currentRelation.getForeignKey().getInvolvedAttributes().size()];

                int index = 0;
                for(OAttribute foreignAttribute: currentRelation.getForeignKey().getInvolvedAttributes())  {
                  propertyOfKey[index] = currentRelation.getPrimaryKey().getInvolvedAttributes().get(index).getName();
                  valueOfKey[index] = currentRecord.getString((foreignAttribute.getName()));
                  index++;
                }

                // search is performed only if all the values in the foreign key are different from null (the relationship is inherited and is also consistent)
                boolean ok = true;

                for(int j=0; j<valueOfKey.length; j++) {
                  if(valueOfKey[j] == null) {
                    ok = false;
                    break;
                  }
                }
                if(ok) {
                  it = currentParentEntity.getHierarchicalBag().getDepth2entities().get(0).iterator();
                  OEntity physicalArrivalEntity = it.next();
                  String currentArrivalEntityName = searchParentEntityType(currentParentEntity, propertyOfKey, valueOfKey, physicalArrivalEntity, dbQueryEngine, context);
                  OEntity currentArrivalEntity = mapper.getDataBaseSchema().getEntityByName(currentArrivalEntityName);
                  currentInVertexType = mapper.getVertexTypeByEntity(currentArrivalEntity);
                }
              }

              // if currentInVertexType is null then there isn't a relationship between to records, thus the edge will not be added.
              if(currentInVertexType != null) {
                edgeType = mapper.getRelationship2edgeType().get(currentRelation);
                graphDBCommandEngine.upsertReachedVertexWithEdge(orientGraph, currentRecord, currentRelation, currentOutVertex, currentInVertexType, edgeType.getName(), context);
              }
            }

            // Statistics updated
            statistics.analyzedRecords++;
          }
          // closing resultset, connection and statement
          queryResult.closeAll(context);
        }
      }
      statistics.notifyListeners();
      statistics.runningStepNumber = -1;
      context.getOutputManager().info("\n");

    } catch (Exception e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }

  }


  /**
   * Performs import of all records of the entities contained in the hierarchical bag passed as parameter.
   * Adopted in case of "Table per Type" inheritance strategy.
   * @param bag
   * @param mapper
   * @param dbQueryEngine
   * @param graphDBCommandEngine
   * @param orientGraph
   * @param context
   */
  protected void tablePerTypeImport(OHierarchicalBag bag, OER2GraphMapper mapper, ODBQueryEngine dbQueryEngine, OGraphEngineForDB graphDBCommandEngine, OrientBaseGraph orientGraph, OTeleporterContext context) {

    try {

      OTeleporterStatistics statistics = context.getStatistics();

      ResultSet aggregateTableRecords = null;

      OEntity currentParentEntity = null;

      OVertexType currentOutVertexType = null;
      OVertexType currentInVertexType = null;
      OrientVertex currentOutVertex = null;
      OEdgeType edgeType = null;
      ResultSet records;
      ResultSet currentRecord;
      ResultSet fullRecord;

      OQueryResult queryResult1 = null;
      OQueryResult queryResult2 = null;

      Iterator<OEntity> it = bag.getDepth2entities().get(0).iterator();
      OEntity rootEntity = it.next();

      for(int i=bag.getDepth2entities().size()-1; i>=0; i--) {
        for(OEntity currentEntity: bag.getDepth2entities().get(i)) {

          // for each entity in dbSchema all records are retrieved
          queryResult1 = dbQueryEngine.getRecordsByEntity(currentEntity, context);
          records = queryResult1.getResult();
          currentRecord = null;

          currentOutVertexType = mapper.getVertexTypeByEntity(currentEntity);

          // each record is imported as vertex in the orient graph
          while(records.next()) {
            queryResult2 = dbQueryEngine.buildAggregateTableFromHierarchicalBag(bag, context);
            aggregateTableRecords = queryResult2.getResult();

            // upsert of the vertex
            currentRecord = records;

            // lookup in the aggregateTable
            String[] propertyOfKey = new String[rootEntity.getPrimaryKey().getInvolvedAttributes().size()];
            String[] valueOfKey = new String[rootEntity.getPrimaryKey().getInvolvedAttributes().size()];
            String[] aggregateTablePropertyOfKey = new String[rootEntity.getPrimaryKey().getInvolvedAttributes().size()];

            for(int k=0; k<propertyOfKey.length; k++) {
              propertyOfKey[k] = currentEntity.getPrimaryKey().getInvolvedAttributes().get(k).getName();
            }

            for(int k=0; k<propertyOfKey.length; k++) {
              valueOfKey[k] = currentRecord.getString(propertyOfKey[k]);
            }

            for(int k=0; k<aggregateTablePropertyOfKey.length; k++) {
              aggregateTablePropertyOfKey[k] = rootEntity.getPrimaryKey().getInvolvedAttributes().get(k).getName();
            }
            // lookup
            fullRecord = this.getFullRecordByAggregateTable(aggregateTableRecords, aggregateTablePropertyOfKey, valueOfKey,context);

            // record imported if is not present in OrientDB
            Set<String> propertiesOfIndex = this.transformAggregateTablePropertyOfKey(aggregateTablePropertyOfKey, currentEntity);

            if(!graphDBCommandEngine.alreadyFullImportedInOrient(orientGraph, fullRecord, currentOutVertexType, propertiesOfIndex, context)) {

              currentOutVertex = (OrientVertex) graphDBCommandEngine.upsertVisitedVertex(orientGraph, fullRecord, currentOutVertexType, propertiesOfIndex, context);

              // for each attribute of the entity belonging to the primary key, correspondent relationship is
              // built as edge and for the referenced record a vertex is built (only id)
              for(ORelationship currentRelation: currentEntity.getAllOutRelationships()) {

                currentParentEntity = mapper.getDataBaseSchema().getEntityByNameIgnoreCase(currentRelation.getParentEntity().getName());
                currentInVertexType = null; // reset for the current iteration

                // checking if parent table belongs to a hierarchical bag
                if(currentParentEntity.getHierarchicalBag() == null) {
                  currentInVertexType = mapper.getVertexTypeByEntity(currentRelation.getParentEntity());
                }

                  // if the parent entity belongs to hierarchical bag, we need to know which is it the more stringent subclass of the record with a certain id
                else if(!currentEntity.getHierarchicalBag().equals(currentParentEntity.getHierarchicalBag())){
                  propertyOfKey = new String[currentRelation.getForeignKey().getInvolvedAttributes().size()];
                  valueOfKey = new String[currentRelation.getForeignKey().getInvolvedAttributes().size()];

                  int index = 0;
                  for(OAttribute foreignAttribute: currentRelation.getForeignKey().getInvolvedAttributes())  {
                    propertyOfKey[index] = currentRelation.getPrimaryKey().getInvolvedAttributes().get(index).getName();
                    valueOfKey[index] = fullRecord.getString((foreignAttribute.getName()));
                    index++;
                  }

                  // search is performed only if all the values in the foreign key are different from null (the relationship is inherited and is also consistent)
                  boolean ok = true;

                  for(int j=0; j<valueOfKey.length; j++) {
                    if(valueOfKey[j] == null) {
                      ok = false;
                      break;
                    }
                  }
                  if(ok) {
                    String currentArrivalEntityName = searchParentEntityType(currentParentEntity, propertyOfKey, valueOfKey, null, dbQueryEngine, context);
                    OEntity currentArrivalEntity = mapper.getDataBaseSchema().getEntityByName(currentArrivalEntityName);
                    currentInVertexType = mapper.getVertexTypeByEntity(currentArrivalEntity);
                  }
                }

                // if currentInVertexType is null then there isn't a relationship between to records, thus the edge will not be added.
                if(currentInVertexType != null) {
                  edgeType = mapper.getRelationship2edgeType().get(currentRelation);
                  graphDBCommandEngine.upsertReachedVertexWithEdge(orientGraph, fullRecord, currentRelation, currentOutVertex, currentInVertexType, edgeType.getName(), context);
                }
              }
            }

            // closing aggregateTable result
            queryResult2.closeAll(context);

            // Statistics updated
            statistics.analyzedRecords++;

          }
          // closing resultset, connection and statement
          queryResult1.closeAll(context);
        }
      }
      statistics.notifyListeners();
      statistics.runningStepNumber = -1;
      context.getOutputManager().info("\n");

    } catch (Exception e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }
  }


  /**
   * Performs import of all records of the entities contained in the hierarchical bag passed as parameter.
   * Adopted in case of "Table per Concrete Type" inheritance strategy.
   * @param bag
   * @param dbQueryEngine
   * @param orientGraph
   * @param context
   */
  protected void tablePerConcreteTypeImport(OHierarchicalBag bag, OER2GraphMapper mapper, ODBQueryEngine dbQueryEngine, OGraphEngineForDB graphDBCommandEngine, OrientBaseGraph orientGraph, OTeleporterContext context) {

    try {

      OTeleporterStatistics statistics = context.getStatistics();

      OEntity currentParentEntity = null;

      OVertexType currentOutVertexType = null;
      OVertexType currentInVertexType = null;
      OrientVertex currentOutVertex = null;
      OEdgeType edgeType = null;
      ResultSet records;
      ResultSet currentRecord;

      OQueryResult queryResult = null;

      for(int i=bag.getDepth2entities().size()-1; i>=0; i--) {
        for(OEntity currentEntity: bag.getDepth2entities().get(i)) {

          // for each entity in dbSchema all records are retrieved
          queryResult = dbQueryEngine.getRecordsByEntity(currentEntity, context);
          records = queryResult.getResult();
          currentRecord = null;

          currentOutVertexType = mapper.getVertexTypeByEntity(currentEntity);

          // each record is imported as vertex in the orient graph
          while(records.next()) {

            // upsert of the vertex
            currentRecord = records;

            // record imported if is not present in OrientDB
            String[] propertyOfKey = new String[currentEntity.getPrimaryKey().getInvolvedAttributes().size()];

            for(int k=0; k<propertyOfKey.length; k++) {
              propertyOfKey[k] = currentEntity.getPrimaryKey().getInvolvedAttributes().get(k).getName();
            }

            Set<String> propertiesOfIndex = this.transformAggregateTablePropertyOfKey(propertyOfKey, currentEntity);  // we need the key of original table, because we are working on it

            if(!graphDBCommandEngine.alreadyFullImportedInOrient(orientGraph, currentRecord, currentOutVertexType, propertiesOfIndex, context)) {

              currentOutVertex = (OrientVertex) graphDBCommandEngine.upsertVisitedVertex(orientGraph, currentRecord, currentOutVertexType, propertiesOfIndex, context);

              // for each attribute of the entity belonging to the primary key, correspondent relationship is
              // built as edge and for the referenced record a vertex is built (only id)
              for(ORelationship currentRelation: currentEntity.getAllOutRelationships()) {

                currentParentEntity = mapper.getDataBaseSchema().getEntityByNameIgnoreCase(currentRelation.getParentEntity().getName());
                currentInVertexType = null; // reset for the current iteration

                // checking if parent table belongs to a hierarchical bag
                if(currentParentEntity.getHierarchicalBag() == null) {
                  currentInVertexType = mapper.getVertexTypeByEntity(currentRelation.getParentEntity());
                }

                  // if the parent entity belongs to hierarchical bag, we need to know which is it the more stringent subclass of the record with a certain id
                else if(!currentEntity.getHierarchicalBag().equals(currentParentEntity.getHierarchicalBag())) {
                  propertyOfKey = new String[currentRelation.getForeignKey().getInvolvedAttributes().size()];
                  String[] valueOfKey = new String[currentRelation.getForeignKey().getInvolvedAttributes().size()];

                  int index = 0;
                  for(OAttribute foreignAttribute: currentRelation.getForeignKey().getInvolvedAttributes())  {
                    propertyOfKey[index] = currentRelation.getPrimaryKey().getInvolvedAttributes().get(index).getName();
                    valueOfKey[index] = currentRecord.getString((foreignAttribute.getName()));
                    index++;
                  }

                  // search is performed only if all the values in the foreign key are different from null (the relationship is inherited and is also consistent)
                  boolean ok = true;

                  for(int j=0; j<valueOfKey.length; j++) {
                    if(valueOfKey[j] == null) {
                      ok = false;
                      break;
                    }
                  }
                  if(ok) {
                    String currentArrivalEntityName = searchParentEntityType(currentParentEntity, propertyOfKey, valueOfKey, null, dbQueryEngine, context);
                    OEntity currentArrivalEntity = mapper.getDataBaseSchema().getEntityByName(currentArrivalEntityName);
                    currentInVertexType = mapper.getVertexTypeByEntity(currentArrivalEntity);
                  }
                }

                // if currentInVertexType is null then there isn't a relationship between to records, thus the edge will not be added.
                if(currentInVertexType != null) {
                  edgeType = mapper.getRelationship2edgeType().get(currentRelation);
                  graphDBCommandEngine.upsertReachedVertexWithEdge(orientGraph, currentRecord, currentRelation, currentOutVertex, currentInVertexType, edgeType.getName(), context);
                }
              }
            }

            // Statistics updated
            statistics.analyzedRecords++;
          }
          // closing resultset, connection and statement
          queryResult.closeAll(context);
        }
      }
      statistics.notifyListeners();
      statistics.runningStepNumber = -1;
      context.getOutputManager().info("\n");

    } catch (Exception e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }
  }

  private Set<String> transformAggregateTablePropertyOfKey(String[] aggregateTablePropertyOfKey, OEntity currentEntity) {
    Set<String> propertiesOfKey = new LinkedHashSet<String>();

    for(String tableKey: aggregateTablePropertyOfKey) {
      String correspondentProperty = ((OER2GraphMapper)this.mapper).getPropertyNameByEntityAndAttribute(currentEntity, tableKey);
      propertiesOfKey.add(correspondentProperty);
    }

    return propertiesOfKey;
  }

  /**
   * @param currentParentEntity
   * @param propertyOfKey
   * @param valueOfKey
   * @param physicalArrivalEntity
   * @param dbQueryEngine
   * @param context
   * @return
   */
  private String searchParentEntityType(OEntity currentParentEntity, String[] propertyOfKey, String[] valueOfKey, OEntity physicalArrivalEntity, ODBQueryEngine dbQueryEngine, OTeleporterContext context) {

    switch(currentParentEntity.getHierarchicalBag().getInheritancePattern()) {

    case "table-per-hierarchy": return searchParentEntityTypeFromSingleTable(currentParentEntity, propertyOfKey, valueOfKey, physicalArrivalEntity, dbQueryEngine, context);

    case "table-per-type": return searchParentEntityTypeFromSubclassTable(currentParentEntity, propertyOfKey, valueOfKey, dbQueryEngine, context);

    case "table-per-concrete-type": return searchParentEntityTypeFromConcreteTable(currentParentEntity, propertyOfKey, valueOfKey, dbQueryEngine, context);

    }

    return null;
  }



  /**
   * @param currentParentEntity
   * @param valueOfKey
   * @param propertyOfKey
   * @param physicalEntity
   * @param dbQueryEngine
   * @param context
   * @return
   */
  private String searchParentEntityTypeFromSingleTable(OEntity currentParentEntity, String[] propertyOfKey, String[] valueOfKey, OEntity physicalEntity, ODBQueryEngine dbQueryEngine, OTeleporterContext context) {

    OHierarchicalBag hierarchicalBag = currentParentEntity.getHierarchicalBag();
    String discriminatorColumn = hierarchicalBag.getDiscriminatorColumn();
    String entityName = null;

    try {

      OQueryResult queryResult = dbQueryEngine.getEntityTypeFromSingleTable(discriminatorColumn, physicalEntity, propertyOfKey, valueOfKey, context);
      ResultSet result = queryResult.getResult();
      result.next();
      String discriminatorValue = result.getString(discriminatorColumn);

      for(String currentEntityName: hierarchicalBag.getEntityName2discriminatorValue().keySet()) {
        if(hierarchicalBag.getEntityName2discriminatorValue().get(currentEntityName).equals(discriminatorValue)) {
          entityName = currentEntityName;
          break;
        }
      }

    } catch (Exception e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }

    return entityName;
  }


  /**
   * @param currentParentEntity
   * @param propertyOfKey
   * @param valueOfKey
   * @param dbQueryEngine
   * @param context
   * @return
   */
  private String searchParentEntityTypeFromSubclassTable(OEntity currentParentEntity, String[] propertyOfKey, String[] valueOfKey, ODBQueryEngine dbQueryEngine, OTeleporterContext context) {

    OHierarchicalBag hierarchicalBag = currentParentEntity.getHierarchicalBag();
    String entityName = null;

    try {

      OQueryResult queryResult = null;
      ResultSet result = null;

      for(int i=hierarchicalBag.getDepth2entities().size()-1; i>=0; i--) {
        for(OEntity currentEntity: hierarchicalBag.getDepth2entities().get(i)) {

          // Overwriting propertyOfKey with the currentEntity attributes' names
          for(int j=0; j<currentEntity.getPrimaryKey().getInvolvedAttributes().size(); j++) {
            propertyOfKey[j] = currentEntity.getPrimaryKey().getInvolvedAttributes().get(j).getName();
          }

          queryResult = dbQueryEngine.getRecordById(currentEntity, propertyOfKey, valueOfKey, context);
          result = queryResult.getResult();

          if(result != null) {
            entityName = currentEntity.getName();
            break;
          }
        }

        if(result != null) {
          break;
        }
      }

    } catch (Exception e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }

    return entityName;
  }


  /**
   * @param currentParentEntity
   * @param propertyOfKey
   * @param valueOfKey
   * @param dbQueryEngine
   * @param context
   * @return
   */
  private String searchParentEntityTypeFromConcreteTable(OEntity currentParentEntity, String[] propertyOfKey, String[] valueOfKey, ODBQueryEngine dbQueryEngine, OTeleporterContext context) {

    OHierarchicalBag hierarchicalBag = currentParentEntity.getHierarchicalBag();
    String entityName = null;

    try {

      OQueryResult queryResult = null;
      ResultSet result = null;

      for(int i=hierarchicalBag.getDepth2entities().size()-1; i>=0; i--) {
        for(OEntity currentEntity: hierarchicalBag.getDepth2entities().get(i)) {

          // Overwriting propertyOfKey with the currentEntity attributes' names
          for(int j=0; j<currentEntity.getPrimaryKey().getInvolvedAttributes().size(); j++) {
            propertyOfKey[j] = currentEntity.getPrimaryKey().getInvolvedAttributes().get(j).getName();
          }

          queryResult = dbQueryEngine.getRecordById(currentEntity, propertyOfKey, valueOfKey, context);
          result = queryResult.getResult();

          if(result != null) {
            entityName = currentEntity.getName();
            break;
          }
        }

        if(result != null) {
          break;
        }
      }

    } catch (Exception e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }

    return entityName;
  }


  /**
   * @param aggregateTableRecords
   * @param aggregateTablePropertyOfKey
   * @param valueOfKey
   * @param context
   * @return
   */
  private ResultSet getFullRecordByAggregateTable(ResultSet aggregateTableRecords, String[] aggregateTablePropertyOfKey, String[] valueOfKey, OTeleporterContext context) {

    ResultSet fullRecord = null;
    String[] currentValueOfKey = new String[aggregateTablePropertyOfKey.length];
    boolean equals;

    try {

      while(aggregateTableRecords.next()) {

        for(int i=0; i<aggregateTablePropertyOfKey.length; i++) {
          currentValueOfKey[i] = aggregateTableRecords.getString(aggregateTablePropertyOfKey[i]);
        }

        equals = true;
        for(int j=0; j<valueOfKey.length; j++) {
          if(!valueOfKey[j].equals(currentValueOfKey[j])) {
            equals = false;
            break;
          }
        }

        if(equals) {
          fullRecord = aggregateTableRecords;
          return fullRecord;
        }

      }
    } catch (Exception e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }

    return fullRecord;
  }


  protected boolean hasGeospatialAttributes(OEntity entity, ODBMSDataTypeHandler handler) {

    for(OAttribute currentAttribute: entity.getAllAttributes()) {
      if(handler.isGeospatial(currentAttribute.getDataType()))
        return true;
    }

    return false;
  }

  protected String[][] buildAggregationColumnsFromAggregatedVertex(OConfiguredVertexClass configuredVertex) {

    String[][] columns;
    List<OSourceTable> sourceTables = configuredVertex.getMapping().getSourceTables();
    columns = new String[sourceTables.size()][sourceTables.get(0).getAggregationColumns().size()];
    int j = 0;
    for (OSourceTable currentSourceTable : sourceTables) {
      List<String> aggregationColumns = currentSourceTable.getAggregationColumns();

      if (aggregationColumns != null) {
        int k = 0;
        for (String attribute : aggregationColumns) {
          columns[j][k] = attribute;
          k++;
        }
      }
      j++;
    }
    return columns;
  }

}
