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

package com.orientechnologies.teleporter.test.rdbms.configuration.importing;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.context.OTeleporterMessageHandler;
import com.orientechnologies.teleporter.importengine.rdbms.dbengine.ODBQueryEngine;
import com.orientechnologies.teleporter.model.dbschema.OSourceDatabaseInfo;
import com.orientechnologies.teleporter.nameresolver.OJavaConventionNameResolver;
import com.orientechnologies.teleporter.persistence.handler.OHSQLDBDataTypeHandler;
import com.orientechnologies.teleporter.strategy.rdbms.ODBMSNaiveStrategy;
import com.orientechnologies.teleporter.util.OFileManager;
import com.orientechnologies.teleporter.util.OMigrationConfigManager;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Gabriele Ponzi
 * @email <g.ponzi--at--orientdb.com>
 */

public class ImportWithAggregationTest {

  private OTeleporterContext context;
  private ODBMSNaiveStrategy naiveStrategy;
  private String             dbParentDirectoryPath;
  private final String configPathJson = "src/test/resources/configuration-mapping/aggregation-from2tables-mapping.json";
  private ODBQueryEngine dbQueryEngine;
  private String driver   = "org.hsqldb.jdbc.JDBCDriver";
  private String jurl     = "jdbc:hsqldb:mem:mydb";
  private String username = "SA";
  private String password = "";
  private String              outOrientGraphUri;
  private OSourceDatabaseInfo sourceDBInfo;

  @Before
  public void init() {
    this.context = OTeleporterContext.newInstance();
    this.dbQueryEngine = new ODBQueryEngine(this.driver);
    this.context.setDbQueryEngine(this.dbQueryEngine);
    this.context.setMessageHandler(new OTeleporterMessageHandler(0));
    this.context.setNameResolver(new OJavaConventionNameResolver());
    this.context.setDataTypeHandler(new OHSQLDBDataTypeHandler());
    this.naiveStrategy = new ODBMSNaiveStrategy();
    this.outOrientGraphUri = "plocal:target/testOrientDB";
    this.dbParentDirectoryPath = this.outOrientGraphUri.replace("plocal:", "");
    this.sourceDBInfo = new OSourceDatabaseInfo("source", this.driver, this.jurl, this.username, this.password);
  }

  @Test

  /**
   *  Source DB schema:
   *
   *  - 1 mysql source
   *  - 1 relationship from person to department (not declared through foreign key definition)
   *  - 3 tables: "person", "vat_profile", "department"
   *
   *  person(id, name, surname, dep_id)
   *  vat_profile(id, vat, updated_on)
   *  department(id, name, location, updated_on)
   *
   *  Desired Graph Model:
   *
   *  - 2 vertex classes: "Person" (aggregation of person and vat_profile entities) and "Department"
   *  - 1 edge class "WorksAt", corresponding to the logic relationship between "person" and "department"
   *
   *  Person(extKey1, extKey2, firstName, lastName, VAT)
   *  Department(id, departmentName, location)
   */

  public void test1() {

    Connection connection = null;
    Statement st = null;
    OrientGraphNoTx orientGraph = null;

    try {

      Class.forName(this.driver);
      connection = DriverManager.getConnection(this.jurl, this.username, this.password);

      String personTableBuilding = "create memory table PERSON (ID varchar(256) not null,"
          + " NAME varchar(256) not null, SURNAME varchar(256) not null, DEP_ID varchar(256) not null, primary key (ID))";
      st = connection.createStatement();
      st.execute(personTableBuilding);

      String vatProfileTableBuilding = "create memory table VAT_PROFILE (ID varchar(256),"
          + " VAT varchar(256) not null, UPDATED_ON date not null, primary key (ID))";
      st.execute(vatProfileTableBuilding);

      String departmentTableBuilding = "create memory table DEPARTMENT (ID  varchar(256),"
          + " NAME varchar(256) not null, LOCATION varchar(256) not null, UPDATED_ON date not null, primary key (ID))";
      st.execute(departmentTableBuilding);

      // Records Inserting

      String personFilling = "insert into PERSON (ID,NAME,SURNAME,DEP_ID) values (" + "('P001','Joe','Black','D001'),"
          + "('P002','Thomas','Anderson','D002')," + "('P003','Tyler','Durden','D001')," + "('P004','John','McClanenei','D001'),"
          + "('P005','Ellen','Ripley','D002')," + "('P006','Marty','McFly','D002'))";
      st.execute(personFilling);

      String vatProfileFilling = "insert into VAT_PROFILE (ID,VAT,UPDATED_ON) values (" + "('P001','173845012','2014-08-16'),"
          + "('P002','627390164','2010-02-06')," + "('P003','472889102','2008-10-23')," + "('P004','564856410','2012-12-21'),"
          + "('P005','467280751','2015-05-05')," + "('P006','389450126','2015-04-25'))";
      st.execute(vatProfileFilling);

      String departmentFilling =
          "insert into DEPARTMENT (ID,NAME,LOCATION,UPDATED_ON) values (" + "('D001','Data Migration','London','2016-05-10'),"
              + "('D002','Contracts Update','Glasgow','2016-05-10'))";
      st.execute(departmentFilling);

      ODocument configDoc = OMigrationConfigManager.loadMigrationConfigFromFile(this.configPathJson);

      this.naiveStrategy
          .executeStrategy(this.sourceDBInfo, this.outOrientGraphUri, "basicDBMapper", null, "java", null, null, configDoc);

      /**
       *  Testing context information
       */

      assertEquals(14, context.getStatistics().totalNumberOfRecords);
      assertEquals(14, context.getStatistics().analyzedRecords);
      assertEquals(8, context.getStatistics().orientAddedVertices);
      assertEquals(6, context.getStatistics().orientAddedEdges);

      /**
       *  Testing built OrientDB
       */
      orientGraph = new OrientGraphNoTx(this.outOrientGraphUri);

      // vertices check

      int count = 0;
      for (Vertex v : orientGraph.getVertices()) {
        assertNotNull(v.getId());
        count++;
      }
      assertEquals(8, count);

      count = 0;
      for (Vertex v : orientGraph.getVerticesOfClass("Person")) {
        assertNotNull(v.getId());
        count++;
      }
      assertEquals(6, count);

      count = 0;
      for (Vertex v : orientGraph.getVerticesOfClass("Department")) {
        assertNotNull(v.getId());
        count++;
      }
      assertEquals(2, count);

      // edges check
      count = 0;
      for (Edge e : orientGraph.getEdges()) {
        assertNotNull(e.getId());
        count++;
      }
      assertEquals(6, count);

      count = 0;
      for (Edge e : orientGraph.getEdgesOfClass("WorksAt")) {
        assertNotNull(e.getId());
        count++;
      }
      assertEquals(6, count);

      // vertex properties and connections check
      Iterator<Edge> edgesIt = null;
      String[] keys = { "id" };
      String[] values = { "D001" };

      Vertex v = null;
      Iterator<Vertex> iterator = orientGraph.getVertices("Department", keys, values).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("D001", v.getProperty("id"));
        assertEquals("Data Migration", v.getProperty("departmentName"));
        assertEquals("London", v.getProperty("location"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.IN, "WorksAt").iterator();
        assertEquals("P001", edgesIt.next().getVertex(Direction.OUT).getProperty("extKey1"));
        assertEquals("P003", edgesIt.next().getVertex(Direction.OUT).getProperty("extKey1"));
        assertEquals("P004", edgesIt.next().getVertex(Direction.OUT).getProperty("extKey1"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

      values[0] = "D002";
      iterator = orientGraph.getVertices("Department", keys, values).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("D002", v.getProperty("id"));
        assertEquals("Contracts Update", v.getProperty("departmentName"));
        assertEquals("Glasgow", v.getProperty("location"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.IN, "WorksAt").iterator();
        assertEquals("P002", edgesIt.next().getVertex(Direction.OUT).getProperty("extKey1"));
        assertEquals("P005", edgesIt.next().getVertex(Direction.OUT).getProperty("extKey1"));
        assertEquals("P006", edgesIt.next().getVertex(Direction.OUT).getProperty("extKey1"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

      String[] personKeys = { "extKey1", "extKey2" };
      String[] personValues = { "P001", "P001" };
      iterator = orientGraph.getVertices("Person", personKeys, personValues).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("P001", v.getProperty("extKey1"));
        assertEquals("Joe", v.getProperty("firstName"));
        assertEquals("Black", v.getProperty("lastName"));
        assertNull(v.getProperty("depId"));
        assertEquals("P001", v.getProperty("extKey2"));
        assertEquals("173845012", v.getProperty("VAT"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.OUT, "WorksAt").iterator();
        assertEquals("D001", edgesIt.next().getVertex(Direction.IN).getProperty("id"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

      personValues[0] = "P002";
      personValues[1] = "P002";
      iterator = orientGraph.getVertices("Person", personKeys, personValues).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("P002", v.getProperty("extKey1"));
        assertEquals("Thomas", v.getProperty("firstName"));
        assertEquals("Anderson", v.getProperty("lastName"));
        assertNull(v.getProperty("depId"));
        assertEquals("P002", v.getProperty("extKey2"));
        assertEquals("627390164", v.getProperty("VAT"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.OUT, "WorksAt").iterator();
        assertEquals("D002", edgesIt.next().getVertex(Direction.IN).getProperty("id"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

      personValues[0] = "P003";
      personValues[1] = "P003";
      iterator = orientGraph.getVertices("Person", personKeys, personValues).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("P003", v.getProperty("extKey1"));
        assertEquals("Tyler", v.getProperty("firstName"));
        assertEquals("Durden", v.getProperty("lastName"));
        assertNull(v.getProperty("depId"));
        assertEquals("P003", v.getProperty("extKey2"));
        assertEquals("472889102", v.getProperty("VAT"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.OUT, "WorksAt").iterator();
        assertEquals("D001", edgesIt.next().getVertex(Direction.IN).getProperty("id"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

      personValues[0] = "P004";
      personValues[1] = "P004";
      iterator = orientGraph.getVertices("Person", personKeys, personValues).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("P004", v.getProperty("extKey1"));
        assertEquals("John", v.getProperty("firstName"));
        assertEquals("McClanenei", v.getProperty("lastName"));
        assertNull(v.getProperty("depId"));
        assertEquals("P004", v.getProperty("extKey2"));
        assertEquals("564856410", v.getProperty("VAT"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.OUT, "WorksAt").iterator();
        assertEquals("D001", edgesIt.next().getVertex(Direction.IN).getProperty("id"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

      personValues[0] = "P005";
      personValues[1] = "P005";
      iterator = orientGraph.getVertices("Person", personKeys, personValues).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("P005", v.getProperty("extKey1"));
        assertEquals("Ellen", v.getProperty("firstName"));
        assertEquals("Ripley", v.getProperty("lastName"));
        assertNull(v.getProperty("depId"));
        assertEquals("P005", v.getProperty("extKey2"));
        assertEquals("467280751", v.getProperty("VAT"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.OUT, "WorksAt").iterator();
        assertEquals("D002", edgesIt.next().getVertex(Direction.IN).getProperty("id"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

      personValues[0] = "P006";
      personValues[1] = "P006";
      iterator = orientGraph.getVertices("Person", personKeys, personValues).iterator();
      assertTrue(iterator.hasNext());
      if (iterator.hasNext()) {
        v = iterator.next();
        assertEquals("P006", v.getProperty("extKey1"));
        assertEquals("Marty", v.getProperty("firstName"));
        assertEquals("McFly", v.getProperty("lastName"));
        assertNull(v.getProperty("depId"));
        assertEquals("P006", v.getProperty("extKey2"));
        assertEquals("389450126", v.getProperty("VAT"));
        assertNull(v.getProperty("updatedOn"));
        edgesIt = v.getEdges(Direction.OUT, "WorksAt").iterator();
        assertEquals("D002", edgesIt.next().getVertex(Direction.IN).getProperty("id"));
        assertEquals(false, edgesIt.hasNext());
      } else {
        fail("Query fail!");
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail();
    } finally {
      try {

        // Dropping Source DB Schema and OrientGraph
        String dbDropping = "drop schema public cascade";
        st.execute(dbDropping);
        connection.close();

        if (orientGraph != null) {
          orientGraph.drop();
          orientGraph.shutdown();
        }

        OFileManager.deleteResource(this.dbParentDirectoryPath);
      } catch (Exception e) {
        e.printStackTrace();
        fail();
      }
    }
  }
}
