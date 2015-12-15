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

package com.orientechnologies.teleporter.mapper.rdbms;

/**
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 *
 */

public class OAggregatorEdge {
  
  private String outVertexClassName;
  private String inVertexClassName;
  private String edgeType;
  
  public OAggregatorEdge(String outVertexClassName, String inVertexClassName, String edgeType) {
    this.outVertexClassName = outVertexClassName;
    this.inVertexClassName = inVertexClassName;
    this.edgeType = edgeType;
  }

  public String getOutVertexClassName() {
    return this.outVertexClassName;
  }

  public void setOutVertexClassName(String outVertexClassName) {
    this.outVertexClassName = outVertexClassName;
  }

  public String getInVertexClassName() {
    return this.inVertexClassName;
  }

  public void setInVertexClassName(String inVertexClassName) {
    this.inVertexClassName = inVertexClassName;
  }

  public String getEdgeType() {
    return this.edgeType;
  }

  public void setEdgeType(String edgeType) {
    this.edgeType = edgeType;
  }
  
  



}
