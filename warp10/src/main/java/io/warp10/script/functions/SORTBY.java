//
//   Copyright 2016  Cityzen Data
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.script.functions;

import io.warp10.continuum.gts.GTSHelper;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.continuum.gts.MetadataTextComparator;
import io.warp10.continuum.store.thrift.data.Metadata;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptStack.Macro;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sort a list of GTS according to a macro
 */
public class SORTBY extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  private static final Comparator<GeoTimeSerie> LAST_COMPARATOR = new Comparator<GeoTimeSerie>() {
    
    private final Comparator<Metadata> INNER_COMP = new MetadataTextComparator(null);
    
    @Override
    public int compare(GeoTimeSerie o1, GeoTimeSerie o2) {
      
      //
      // Empty GTS are at the end
      //
      
      if (0 == o1.size()) {
        if (0 == o2.size()) {
          //
          // If both GTS are empty, order them by their class/labels
          //
          return INNER_COMP.compare(o1.getMetadata(), o2.getMetadata());
        } else {
          return 1;
        }
      }
      
      if (0 == o2.size()) {
        return -1;
      }
      
      //
      // Sort GTS if needed in reverse order
      //
      
      GTSHelper.sort(o1, true);
      GTSHelper.sort(o2, true);
      
      Object last1 = GTSHelper.valueAtIndex(o1, 0);
      Object last2 = GTSHelper.valueAtIndex(o2, 0);
      
      int res = 0;
      
      if (last1 instanceof Long && last2 instanceof Long) {
        res = ((Long) last1).compareTo((Long) last2);
      } else if (last1 instanceof Double && last2 instanceof Double) {
        res = ((Double) last1).compareTo((Double) last2);
      } else if (last1 instanceof String && last2 instanceof String) {
        res = ((String) last1).compareTo((String) last2);
      } else if (last1 instanceof Boolean && last2 instanceof Boolean) {
        if (((Boolean) last1).equals((Boolean) last2)) {
          res = 0;
        } else if (Boolean.TRUE.equals(last1)) {
          return 1;
        } else {
          return -1;
        }
      } else if (last1 instanceof Long && last2 instanceof Double || last1 instanceof Double && last2 instanceof Long) {
        res = ((Double)((Number) last1).doubleValue()).compareTo((Double)((Number) last2).doubleValue());
      } else {
        // In last resort, compare the String representations
        res = last1.toString().compareTo(last2.toString());
      }
      
      if (0 != res) {
        return res;
      }
      
      //
      // Compare last ticks if values are identical
      //

      long tick1 = GTSHelper.tickAtIndex(o1, 0);
      long tick2 = GTSHelper.tickAtIndex(o2, 0);
        
      if (tick1 > tick2) {
        return -1;
      } else if (tick1 < tick2) {
        return 1;
      }
      
      //
      // Compare metadatas if values and last ticks are identical
      //
      
      return INNER_COMP.compare(o1.getMetadata(), o2.getMetadata());
    }
  };
  
  public SORTBY(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();
    
    if (!(top instanceof Macro)) {
      throw new WarpScriptException(getName() + " expects a macro on top of the stack.");
    }
    
    Macro macro = (Macro) top;
    
    top = stack.pop();
    
    //
    // Check if list on the top of the stack is a list of GTS
    //

    if (!(top instanceof List)) {
      throw new WarpScriptException(getName() + " operates on a list of Geo Time Series.");
    }

    for (Object o: ((List) top)) {
      if (!(o instanceof GeoTimeSerie)) {
        throw new WarpScriptException(getName() + " operates on a list of Geo Time Series.");
      }
    }

    //
    // Generate the result of the macro for the various GTS
    //
    
    final Map<GeoTimeSerie,Object> values = new HashMap<GeoTimeSerie,Object>();
    
    String type = null;
    
    for (Object gts: (List) top) {
      stack.push(gts);
      stack.exec(macro);
      Object value = stack.pop();
      
      String valtype = null;
      
      if (value instanceof Long) {
        valtype = "LONG";
      } else if (value instanceof Double) {
        valtype = "DOUBLE";
      } else if (value instanceof String) {
        valtype = "STRING";
      }
      
      if (null == value || null == valtype || (null != type && (!type.equals(valtype)))) {
        throw new WarpScriptException(getName() + " expects its macro to return a non null double,long or string in a consistent manner.");
      }
      
      type = valtype;
      
      values.put((GeoTimeSerie) gts, value); 
    }
    
    final String valtype = type;
    
    Collections.sort((List<GeoTimeSerie>) top, new Comparator<GeoTimeSerie>() {
      @Override
      public int compare(GeoTimeSerie o1, GeoTimeSerie o2) {
        if ("LONG".equals(valtype)) {
          return ((Long) values.get(o1)).compareTo((Long) values.get(o2));
        } else if ("DOUBLE".equals(valtype)) {
          return ((Double) values.get(o1)).compareTo((Double) values.get(o2));
        } else if ("STRING".equals(valtype)) {
          return ((String) values.get(o1)).compareTo((String) values.get(o2));
        } else {
          return 0;
        }
      }
    });
    
    stack.push(top);
    
    return stack;
  }
}
