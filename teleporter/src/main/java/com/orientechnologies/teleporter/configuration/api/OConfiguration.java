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

import java.util.LinkedList;
import java.util.List;

/**
 * It collects all the information contained in the configuration submitted by the user.
 *
 * @author Gabriele Ponzi
 * @email <gabriele.ponzi--at--gmail.com>
 *
 */

public class OConfiguration {

    private List<OConfiguredVertex> configuredVertices;  // may be empty but not null
    private List<OConfiguredEdge> configuredEdges;       // may be empty but not null

    public OConfiguration() {
        this.configuredVertices = new LinkedList<OConfiguredVertex>();
        this.configuredEdges = new LinkedList<OConfiguredEdge>();
    }

    public List<OConfiguredVertex> getConfiguredVertices() {
        return this.configuredVertices;
    }

    public void setConfiguredVertices(List<OConfiguredVertex> configuredVertices) {
        this.configuredVertices = configuredVertices;
    }

    public List<OConfiguredEdge> getConfiguredEdges() {
        return this.configuredEdges;
    }

    public void setConfiguredEdges(List<OConfiguredEdge> configuredEdges) {
        this.configuredEdges = configuredEdges;
    }
}
