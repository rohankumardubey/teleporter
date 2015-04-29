/*
 *
 *  *  Copyright 2015 Orient Technologies LTD (info(at)orientechnologies.com)
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
 *  * For more information: http://www.orientechnologies.com
 *
 */

package com.orientechnologies.orient.drakkar.context;

import java.util.ArrayList;
import java.util.List;

import com.orientechnologies.orient.drakkar.ui.OStatisticsListener;

/**
 * Collects and updates statistics about the Drakkar execution state.
 * It identifies and monitors 4 step in the global execution:
 * 1. Source DB Schema building
 * 2. Graph Model building
 * 3. OrientDB Schema writing
 * 4. OrientDB importing
 * 
 * @author Gabriele Ponzi
 * @email  gabriele.ponzi--at--gmail.com
 *
 */

public class ODrakkarStatistics {

  // Source DB Schema building statistics
  private int totalNumberOfEntities;
  private int builtEntities;
  private int doneEntity4Relationship;

  // Graph Model building statistics
  private int totalNumberOfModelVertices;
  private int builtModelVertexTypes;
  private int totalNumberOfModelEdges;
  private int builtModelEdgeTypes;

  // OrientDB Schema writing statistics
  private int totalNumberOfVertexType;
  private int wroteVertexType;
  private int totalNumberOfEdgeType;
  private int wroteEdgeType;
  private int totalNumberOfIndices;
  private int wroteIndices;

  // OrientDB importing
  private int importedEntities;
  
  // Listeners
  private List<OStatisticsListener> listeners;

  public ODrakkarStatistics() {
    this.init();
    this.listeners = new ArrayList<OStatisticsListener>();
  }


  private void init() {

    this.totalNumberOfEntities = 0;
    this.builtEntities = 0;
    this.doneEntity4Relationship = 0;

    this.totalNumberOfModelVertices = 0;
    this.builtModelVertexTypes = 0;
    this.totalNumberOfModelEdges = 0;
    this.builtModelEdgeTypes = 0;

    this.totalNumberOfVertexType = 0;
    this.wroteVertexType = 0;
    this.totalNumberOfEdgeType = 0;
    this.wroteEdgeType = 0;
    this.totalNumberOfIndices = 0;
    this.wroteIndices = 0;

    this.importedEntities = 0;

  }
  
  public void reset() {
    this.init();
  }
  
  /*
   * Publisher-Subscribers
   */
  
  public void registerListener(OStatisticsListener listener) {
    this.listeners.add(listener);
  }
  
  public void notifyListeners() {
    for(OStatisticsListener listener: this.listeners) {
      listener.updateOnEvent(this);
    }
  }
  

  /*
   *  Getters and Setters
   */

  public int getTotalNumberOfEntities() {
    return this.totalNumberOfEntities;
  }

  public void setTotalNumberOfEntities(int totalNumberOfEntities) {
    this.totalNumberOfEntities = totalNumberOfEntities;
  }

  public int getBuiltEntities() {
    return this.builtEntities;
  }


  public int getDoneEntity4Relationship() {
    return this.doneEntity4Relationship;
  }

  public int getTotalNumberOfModelVertices() {
    return this.totalNumberOfModelVertices;
  }

  public void setTotalNumberOfModelVertices(int totalNumberOfModelVertices) {
    this.totalNumberOfModelVertices = totalNumberOfModelVertices;
  }

  public int getBuiltModelVertexTypes() {
    return this.builtModelVertexTypes;
  }


  public int getTotalNumberOfModelEdges() {
    return this.totalNumberOfModelEdges;
  }

  public void setTotalNumberOfModelEdges(int totalNumberOfModelEdges) {
    this.totalNumberOfModelEdges = totalNumberOfModelEdges;
  }

  public int getBuiltModelEdgeTypes() {
    return this.builtModelEdgeTypes;
  }


  public int getTotalNumberOfVertexType() {
    return this.totalNumberOfVertexType;
  }

  public void setTotalNumberOfVertexType(int totalNumberOfVertexType) {
    this.totalNumberOfVertexType = totalNumberOfVertexType;
  }

  public int getWroteVertexType() {
    return this.wroteVertexType;
  }


  public int getTotalNumberOfEdgeType() {
    return this.totalNumberOfEdgeType;
  }

  public void setTotalNumberOfEdgeType(int totalNumberOfEdgeType) {
    this.totalNumberOfEdgeType = totalNumberOfEdgeType;
  }

  public int getWroteEdgeType() {
    return this.wroteEdgeType;
  }


  public int getTotalNumberOfIndices() {
    return this.totalNumberOfIndices;
  }

  public void setTotalNumberOfIndices(int totalNumberOfIndices) {
    this.totalNumberOfIndices = totalNumberOfIndices;
  }

  public int getWroteIndices() {
    return this.wroteIndices;
  }


  public int getImportedEntities() {
    return this.importedEntities;
  }


  /*
   *  Incrementing methods
   */


  public void incrementBuiltEntities() {
    this.builtEntities++;
    this.notifyListeners();
  }

  public void incrementDoneEntity4Relationship() {
    this.doneEntity4Relationship++;
    this.notifyListeners();
  }

  public void incrementBuiltModelVertexTypes() {
    this.builtModelVertexTypes++;
    this.notifyListeners();
  }

  public void incrementBuiltModelEdgeTypes() {
    this.builtModelEdgeTypes++;
    this.notifyListeners();
  }

  public void incrementWroteVertexType() {
    this.wroteVertexType++;
    this.notifyListeners();
  }

  public void incrementWroteEdgeType() {
    this.wroteEdgeType++;
    this.notifyListeners();
  }

  public void incrementWroteIndices() {
    this.wroteIndices++;
    this.notifyListeners();
  }

  public void incrementImportedEntities() {
    this.importedEntities++;
    this.notifyListeners();
  }

  
  /*
   *  toString methods
   */

  public String sourceDbSchemaBuildingProgress() {
    String s ="";
    s += "Built Entities: " + this.builtEntities + "/" + this.totalNumberOfEntities;
    s += "\nExplored Entities for Relationship: " + this.doneEntity4Relationship + "/" + this.totalNumberOfEntities;
    return s;
  }

  public String graphModelBuildingProgress() {
    String s ="";
    s += "Built Model Vertices: " + this.builtModelVertexTypes + "/" + this.totalNumberOfModelVertices;
    s += "\nBuilt Model Edges: " + this.builtModelEdgeTypes + "/" + this.totalNumberOfModelEdges;
    return s;
  }

  public String orientSchemaWritingProgress() {
    String s ="";
    s += "Wrote Vertex Type: " + this.wroteVertexType + "/" + this.totalNumberOfVertexType;
    s += "\nWrote Edge Type: " + this.wroteEdgeType + "/" + this.totalNumberOfEdgeType;
    s += "\nWrote Indices: " + this.wroteIndices + "/" + this.totalNumberOfIndices;
    return s;
  }

  public String importingProgress() {
    String s ="";
    s += "Imported Entities: " + this.importedEntities + "/" + this.totalNumberOfEntities;
    return s;
  }
  
  public String toString() {
    String s = "";
    s += this.sourceDbSchemaBuildingProgress() + "\n\n" + this.graphModelBuildingProgress() + "\n\n" + this.orientSchemaWritingProgress() + "\n\n" + this.importingProgress();
    return s;
  }



}
