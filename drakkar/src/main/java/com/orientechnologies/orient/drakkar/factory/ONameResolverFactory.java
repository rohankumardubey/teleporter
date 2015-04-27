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

package com.orientechnologies.orient.drakkar.factory;

import com.orientechnologies.orient.drakkar.nameresolver.OJavaConventionNameResolver;
import com.orientechnologies.orient.drakkar.nameresolver.ONameResolver;

/**
 * Factory used to instantiate a specific NameResolver starting from its name.
 * If the name is not specified (null value) a JavaConventionNameResolver is instantiated.
 *  
 * @author Gabriele Ponzi
 * @email  gabriele.ponzi--at--gmail.com
 *
 */

public class ONameResolverFactory {

  public ONameResolver buildNameResolver(String nameResolverConvention) {
    ONameResolver nameResolver = null;


    if(nameResolverConvention == null)  {
      nameResolver = new OJavaConventionNameResolver();
    }
    else {
      switch(nameResolverConvention) {

      default :  nameResolver = new OJavaConventionNameResolver();
      break;

      }
    }
    return nameResolver;
  }


}
