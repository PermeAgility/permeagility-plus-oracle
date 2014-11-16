/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.plus.oracle;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;

import com.orientechnologies.orient.core.metadata.schema.OType;

@SuppressWarnings("serial")
public class RQueryResult extends ArrayList<HashMap<String,Object>> {
	    
	ArrayList<String> columns = new ArrayList<String>();
	ArrayList<String> types = new ArrayList<String>();

    public RQueryResult(ResultSet rs) throws SQLException {
    	this(rs.getMetaData());
    	ResultSetMetaData	meta = rs.getMetaData();
        int               colmax = meta.getColumnCount();
        Object            o = null;
        for (; rs.next(); ) {
        	HashMap<String,Object> row = new HashMap<String,Object>();
            for (int i = 0; i < colmax; ++i) {
                o = rs.getObject(i + 1);
                if (meta.getColumnType(i+1) == Types.BOOLEAN && o == null) {
                	o = new Boolean(false);
                }
                row.put(meta.getColumnName(i + 1),o);
            }
            add(row);
        }
    }
	
	public RQueryResult(ResultSetMetaData meta) throws SQLException {
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			columns.add(meta.getColumnName(i));
			types.add(meta.getColumnTypeName(i));
		}
	}
	
	public ArrayList<String> getColumns() {
		return columns;
	}
	
	public ArrayList<String> getTypes() {
		return types;
	}
	
	public Object getValue(int row, String column) {
		return get(row).get(column);
	}

	public int findFirstRow(String column, String value) {
		// could add some indexing capability here to improve performance
		for (int i=0;i<size();i++) {
			String o = getStringValue(i,column);
			if (o == null && value == null) return i;
			if (o.equals(value)) {
				return i;
			}
		}
		return -1;
	}
	
	public String getStringValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null) {
			return o.toString();
		}
		return null;
	}
	
	public Number getNumberValue(int row, String column) {
		Object o = getValue(row, column);
		if (o != null && o instanceof Number) {
			return (Number)o;
		}
		return null;
	}

	public Date getDateValue(int row, String column) {
		Object o = getValue(row, column);
		System.out.println("DT Class = "+o.getClass().getName());
		if (o != null && o instanceof Date) {
			return (Date)o;
		}
		return null;
	}

	public boolean hasChanged(int row, String column) {
		if (row < 1) {
			return true;
		} else {
			return changed(getValue(row, column), getValue(row - 1, column));
		}
	}

	public boolean willChange(int row, String column) {
		if (row > (size() - 2)) {
			return true;
		} else {
			return changed(getValue(row, column), getValue(row + 1, column));
		}
	}

	public boolean changed(Object o1, Object o2) {
		if (o1 == null && o2 == null) return false;
		if (o1 == null && o2 != null) return true;
		if (o1 != null && o2 == null) return true;
		return !o1.equals(o2);
	}
 
	/** Determine the best lossless OrientDB representation 
	 * of the given java classname (fully qualified) of an object given in a result set 
	 * (not necessarily the most efficient storage or what you expect) 
	 * Note: This is a good candidate to be a general utility function but this is the only place it is used right now*/
	public OType determineOTypeFromClassName(String className) {
		OType otype = OType.STRING;  // Default
		if (className.equals("java.math.BigDecimal")) {
			otype = OType.DECIMAL;
		} else if (className.equals("oracle.sql.TIMESTAMP") || className.equals("java.sql.Timestamp")) {
			otype = OType.DATETIME;
		} else if (className.equals("java.sql.Date")) {
			otype = OType.DATETIME;
		} else if (className.equals("java.")) {
			otype = OType.DOUBLE;
		}
		System.out.println(className+" becomes "+otype);
		return otype;
	}

}


