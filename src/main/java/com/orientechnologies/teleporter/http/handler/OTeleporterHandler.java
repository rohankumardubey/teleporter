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

package com.orientechnologies.teleporter.http.handler;

import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.teleporter.context.OOutputStreamManager;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.persistence.util.ODBSourceConnection;
import com.orientechnologies.teleporter.util.ODriverConfigurator;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by Enrico Risa on 27/11/15.
 */
public class OTeleporterHandler {
  private ExecutorService pool = Executors.newFixedThreadPool(1);

  OTeleporterJob currentJob = null;

  /**
   * Executes import with configuration;
   *
   * @param args
   * @param currentServerInstance
   */
  public ODocument execute(ODocument args, OServer currentServerInstance) throws Exception {

    OTeleporterJob job = new OTeleporterJob(args, currentServerInstance, new OTeleporterListener() {
      @Override
      public void onEnd(OTeleporterJob oTeleporterJob) {
        currentJob = null;
      }
    });

    job.validate();

    currentJob = job;
    Future<ODocument> future = pool.submit(job);
    ODocument executionResult = null;

    //print the return value of Future, notice the output delay in console
    // because Future.get() waits for task to get completed
    executionResult = future.get();

    return executionResult;
  }

  /**
   * Checks If the connection with given parameters is alive
   *
   * @param args
   *
   * @throws Exception
   */
  public void checkConnection(ODocument args, OServer currentServerInstance) throws Exception {

    ODriverConfigurator configurator = new ODriverConfigurator();

    final String driver = args.field("driver");
    final String jurl = args.field("jurl");
    final String username = args.field("username");
    final String password = args.field("password");

    if(OTeleporterContext.getInstance() == null) {
      OTeleporterContext.newInstance(currentServerInstance.getContext());
    }
    OTeleporterContext.getInstance().setOutputManager(new OOutputStreamManager(2));

    configurator.checkConnection(driver, jurl, username, password);
  }

  /**
   * Status of the Running Jobs
   *
   * @return ODocument
   */
  public ODocument status() {

    ODocument status = new ODocument();

    Collection<ODocument> jobs = new ArrayList<ODocument>();
    if (currentJob != null) {
      jobs.add(currentJob.status());
    }
    status.field("jobs", jobs);
    return status;
  }


  /**
   * Retrieves all the tables contained in the specified source database.
   *
   * @return ODocument
   */
  public ODocument getTables(ODocument params, OServer currentServerInstance) throws Exception {

    ODriverConfigurator configurator = new ODriverConfigurator();
    List<ODocument> tables = new ArrayList<ODocument>();

    String driver = params.field("driver");
    String uri = params.field("jurl");
    String username = params.field("username");
    String password = params.field("password");

    if(OTeleporterContext.getInstance() == null) {
      OTeleporterContext.newInstance(currentServerInstance.getContext());
    }
    OTeleporterContext.getInstance().setOutputManager(new OOutputStreamManager(2));

    Connection connection = configurator.getDBMSConnection(driver, uri, username, password);
    DatabaseMetaData databaseMetaData = connection.getMetaData();
    String[] tableTypes = {"TABLE"};

    ResultSet resultTable = databaseMetaData.getTables(null, null, null, tableTypes);

    // Giving db's table names
    int id = 1;
    while (resultTable.next()) {
      String tableName = resultTable.getString("TABLE_NAME");
      ODocument currentTable = new ODocument();
      currentTable.field("id", id);
      currentTable.field("tableName", tableName);
      tables.add(currentTable);
      id++;
    }
    resultTable.close();

    ODocument result = new ODocument();
    result.field("tables", tables);
    return result;
  }
}