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

package com.orientechnologies.teleporter.configuration.api;

/**
 * @author Gabriele Ponzi
 * @email <g.ponzi--at--orientdb.com>
 */
public class OConfiguredPropertyMapping {

  private String sourceName; // mandatory
  private String columnName; // mandatory
  private String type; // mandatory

  public OConfiguredPropertyMapping(final String sourceName) {
    this.sourceName = sourceName;
  }

  public String getSourceName() {
    return this.sourceName;
  }

  public void setSourceName(final String sourceName) {
    this.sourceName = sourceName;
  }

  public String getColumnName() {
    return this.columnName;
  }

  public void setColumnName(final String columnName) {
    this.columnName = columnName;
  }

  public String getType() {
    return this.type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
