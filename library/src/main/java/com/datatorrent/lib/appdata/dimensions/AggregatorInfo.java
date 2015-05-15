/*
 * Copyright (c) 2015 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datatorrent.lib.appdata.dimensions;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class AggregatorInfo implements Serializable
{
  private static final long serialVersionUID = 20154301642L;

  private transient boolean setup = false;
  private transient Map<Class<? extends DimensionsStaticAggregator>, String> classToStaticAggregatorName;
  private transient Map<String, DimensionsOTFAggregator> nameToOTFAggregator;
  private transient Map<String, List<String>> otfAggregatorToStaticAggregators;
  private transient Map<String, DimensionsStaticAggregator> staticAggregatorNameToStaticAggregator;
  private transient Map<Integer, DimensionsStaticAggregator> staticAggregatorIDToAggregator;

  private Map<String, DimensionsAggregator> nameToAggregator;
  private Map<String, Integer> staticAggregatorNameToID;

  private static final Map<String, Integer> autoGenIds(Map<String, DimensionsAggregator> nameToAggregator)
  {
    Map<String, Integer> staticAggregatorNameToID = Maps.newHashMap();

    for(Map.Entry<String, DimensionsAggregator> entry: nameToAggregator.entrySet()) {
      staticAggregatorNameToID.put(entry.getKey(), entry.getValue().getClass().getName().hashCode());
    }

    return staticAggregatorNameToID;
  }

  protected AggregatorInfo()
  {
    //for kryo
  }

  public AggregatorInfo(Map<String, DimensionsAggregator> nameToAggregator)
  {
    this(nameToAggregator,
         autoGenIds(nameToAggregator));
  }

  public AggregatorInfo(Map<String, DimensionsAggregator> nameToAggregator,
                        Map<String, Integer> staticAggregatorNameToID)
  {
    setNameToAggregator(nameToAggregator);
    setStaticAggregatorNameToID(staticAggregatorNameToID);

    validate();
  }

  private void validate()
  {
    for(Map.Entry<String, DimensionsAggregator> entry: nameToAggregator.entrySet()) {
      String aggregatorName = entry.getKey();
      DimensionsAggregator aggregator = entry.getValue();

      if(aggregator instanceof DimensionsOTFAggregator) {
        if(staticAggregatorNameToID.get(aggregatorName) != null) {
          throw new IllegalArgumentException("There should not be an id entry for an aggregator of type " +
                                             aggregator.getClass() +
                                             " and id " +
                                             staticAggregatorNameToID.get(aggregatorName));
        }
      }
      else if(aggregator instanceof DimensionsStaticAggregator) {
        Preconditions.checkArgument(staticAggregatorNameToID.get(aggregatorName) != null);
      }
      else {
        throw new IllegalArgumentException("Unsupported aggregator type " +
                                           aggregator.getClass());
      }
    }
  }

  public void setup()
  {
    if(setup) {
      return;
    }

    setup = true;

    staticAggregatorNameToStaticAggregator = Maps.newHashMap();
    nameToOTFAggregator = Maps.newHashMap();

    for(Map.Entry<String, DimensionsAggregator> entry: nameToAggregator.entrySet()) {
      String name = entry.getKey();
      DimensionsAggregator aggregator = entry.getValue();

      if(aggregator instanceof DimensionsStaticAggregator) {
        staticAggregatorNameToStaticAggregator.put(name, DimensionsStaticAggregator.class.cast(aggregator));
      }
      else if(aggregator instanceof DimensionsOTFAggregator) {
        nameToOTFAggregator.put(name, DimensionsOTFAggregator.class.cast(aggregator));
      }
      else {
        throw new UnsupportedOperationException("The class " + aggregator.getClass() + " is not supported");
      }
    }

    classToStaticAggregatorName = Maps.newHashMap();

    for(Map.Entry<String, DimensionsStaticAggregator> entry: staticAggregatorNameToStaticAggregator.entrySet()) {
      classToStaticAggregatorName.put(entry.getValue().getClass(), entry.getKey());
    }

    staticAggregatorIDToAggregator = Maps.newHashMap();

    for(Map.Entry<String, Integer> entry: staticAggregatorNameToID.entrySet()) {
      String aggregatorName = entry.getKey();
      int aggregatorID = entry.getValue();
      staticAggregatorIDToAggregator.put(aggregatorID,
                                         staticAggregatorNameToStaticAggregator.get(aggregatorName));
    }

    otfAggregatorToStaticAggregators = Maps.newHashMap();

    for(Map.Entry<String, DimensionsOTFAggregator> entry: nameToOTFAggregator.entrySet()) {
      String name = entry.getKey();
      List<String> staticAggregators = Lists.newArrayList();

      DimensionsOTFAggregator dotfAggregator = nameToOTFAggregator.get(name);

      for(Class<? extends DimensionsStaticAggregator> clazz: dotfAggregator.getChildAggregators()) {
        staticAggregators.add(classToStaticAggregatorName.get(clazz));
      }

      //TODO make unmodifiable
      //otfAggregatorToStaticAggregators.put(name, Collections.unmodifiableList(staticAggregators));
      otfAggregatorToStaticAggregators.put(name, staticAggregators);
    }

    //TODO make this map unmodifiable
    //otfAggregatorToStaticAggregators = Collections.unmodifiableMap(otfAggregatorToStaticAggregators);
  }

  private void setNameToAggregator(Map<String, DimensionsAggregator> nameToAggregator)
  {
    this.nameToAggregator = Preconditions.checkNotNull(nameToAggregator);
  }

  public boolean isAggregator(String aggregatorName)
  {
    return classToStaticAggregatorName.values().contains(aggregatorName) ||
           nameToOTFAggregator.containsKey(aggregatorName);
  }

  public boolean isStaticAggregator(String aggregatorName)
  {
    return classToStaticAggregatorName.values().contains(aggregatorName);
  }

  public Map<Class<? extends DimensionsStaticAggregator>, String> getClassToStaticAggregatorName()
  {
    return classToStaticAggregatorName;
  }

  public Map<Integer, DimensionsStaticAggregator> getStaticAggregatorIDToAggregator()
  {
    return staticAggregatorIDToAggregator;
  }

  public Map<String, DimensionsStaticAggregator> getStaticAggregatorNameToStaticAggregator()
  {
    return this.staticAggregatorNameToStaticAggregator;
  }

  private void setStaticAggregatorNameToID(Map<String, Integer> staticAggregatorNameToID)
  {
    Preconditions.checkNotNull(staticAggregatorNameToID);

    for(Map.Entry<String, Integer> entry: staticAggregatorNameToID.entrySet()) {
      Preconditions.checkNotNull(entry.getKey());
      Preconditions.checkNotNull(entry.getValue());
    }

    this.staticAggregatorNameToID = Maps.newHashMap(staticAggregatorNameToID);
    //TODO this map should be made unmodifiable
    //this.staticAggregatorNameToID = Collections.unmodifiableMap(staticAggregatorNameToID);
  }

  public Map<String, Integer> getStaticAggregatorNameToID()
  {
    return staticAggregatorNameToID;
  }

  public Map<String, DimensionsOTFAggregator> getNameToOTFAggregators()
  {
    return nameToOTFAggregator;
  }

  public Map<String, List<String>> getOTFAggregatorToStaticAggregators()
  {
    return otfAggregatorToStaticAggregators;
  }

  private static final Logger logger = LoggerFactory.getLogger(AggregatorInfo.class);
}
