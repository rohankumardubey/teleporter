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

package com.orientechnologies.teleporter.context;

import com.orientechnologies.orient.output.OPluginMessageHandler;
import com.orientechnologies.teleporter.importengine.rdbms.dbengine.ODBQueryEngine;
import com.orientechnologies.teleporter.nameresolver.ONameResolver;
import com.orientechnologies.teleporter.persistence.handler.ODriverDataTypeHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Context class for Drakkar execution.
 *
 * @author Gabriele Ponzi
 * @email <g.ponzi--at--orientdb.com>
 */

public class OTeleporterContext {

  private static OTeleporterContext instance = null;

  private OTeleporterStatistics  statistics;
  private OPluginMessageHandler messageHandler;
  private ODriverDataTypeHandler dataTypeHandler;
  private ONameResolver          nameResolver;
  private String                 driverDependencyPath;
  private String                 executionStrategy;
  private ODBQueryEngine         dbQueryEngine;

  private OTeleporterContext() {
    this.statistics = new OTeleporterStatistics();
  }

  public static OTeleporterContext getInstance() {
    if (instance == null) {
      instance = new OTeleporterContext();
    }
    return instance;
  }

  public static OTeleporterContext newInstance() {
    instance = new OTeleporterContext();
    return instance;
  }

  public OTeleporterStatistics getStatistics() {
    return this.statistics;
  }

  public void setStatistics(OTeleporterStatistics statistics) {
    this.statistics = statistics;
  }

  public OPluginMessageHandler getMessageHandler() {
    return this.messageHandler;
  }

  public void setMessageHandler(OPluginMessageHandler messageHandler) {
    this.messageHandler = messageHandler;
  }

  public ODriverDataTypeHandler getDataTypeHandler() {
    return this.dataTypeHandler;
  }

  public void setDataTypeHandler(ODriverDataTypeHandler dataTypeHandler) {
    this.dataTypeHandler = dataTypeHandler;
  }

  public ONameResolver getNameResolver() {
    return this.nameResolver;
  }

  public void setNameResolver(ONameResolver nameResolver) {
    this.nameResolver = nameResolver;
  }

  public String getDriverDependencyPath() {
    return this.driverDependencyPath;
  }

  public void setDriverDependencyPath(String driverDependencyPath) {
    this.driverDependencyPath = driverDependencyPath;
  }

  public String getExecutionStrategy() {
    return this.executionStrategy;
  }

  public void setExecutionStrategy(String executionStrategy) {
    this.executionStrategy = executionStrategy;
  }

  public ODBQueryEngine getDbQueryEngine() {
    return dbQueryEngine;
  }

  public void setDbQueryEngine(ODBQueryEngine dbQueryEngine) {
    this.dbQueryEngine = dbQueryEngine;
  }

  /**
   * Prints the error message for a caught exception according to a level passed as argument. It's composed of:
   * - defined error message
   * - exception message
   *
   * @param e
   * @param message
   * @param level
   *
   * @return printedMessage
   */
  public String printExceptionMessage(Exception e, String message, String level) {

    if (e.getMessage() != null)
      message += "\n" + e.getClass().getName() + " - " + e.getMessage();
    else
      message += "\n" + e.getClass().getName();

    switch (level) {
    case "debug":
      this.messageHandler.debug(this, message);
      break;
    case "info":
      this.messageHandler.info(this, message);
      break;
    case "warn":
      this.messageHandler.warn(this, message);
      break;
    case "error":
      this.messageHandler.error(this, message);
      break;
    }

    return message;
  }

  /**
   * Builds the exception stack trace and prints it according to a level passed as argument.
   *
   * @param e
   * @param level
   *
   * @return printedMessage
   */
  public String printExceptionStackTrace(Exception e, String level) {

    // copying the exception stack trace in the string
    Writer writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    String s = writer.toString();

    switch (level) {
    case "debug":
      this.messageHandler.debug(this, "\n" + s + "\n");
      break;
    case "info":
      this.messageHandler.info(this, "\n" + s + "\n");
      break;
    case "warn":
      this.messageHandler.warn(this, "\n" + s + "\n");
      break;
    case "error":
      this.messageHandler.error(this, "\n" + s + "\n");
      break;
    }

    return s;
  }

}
