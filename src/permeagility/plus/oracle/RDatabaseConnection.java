/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.plus.oracle;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Locale;

public class RDatabaseConnection {

	public static boolean DEBUG = false;
	
	RDatabase db = null;
	Connection c = null;
	RQueryResult keys = null;  // Generated keys after an insert/update

	public RQueryResult getGeneratedKeys() {
		return keys;
	}
	
	protected RDatabaseConnection(RDatabase _db, Connection _c) {
		db = _db;
		c = _c;
		if (DEBUG) System.out.println("New database connection initiated");
	}
	
	protected void close() {
		try {
			c.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean isConnected() {
		if (c != null) {
			return true;
		} else {
			return false;
		}
	}

	public String getUser() {
		return db.getUser();
	}
	
	public Locale getLocale() {
		return db.getLocale();
	}
	
	/** Allows overriding the locale for the connection. 
	 *  This is for supporting non-users changing the language for the request
	 */
	public void setLocale(Locale l) {
		db.setLocale(l);
	}
	
	public synchronized RQueryResult trapQuery(String expression) {
		RQueryResult data = null;
		try {
			data = query(expression);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
	}
	
    public synchronized RQueryResult query(String expression) throws SQLException {
    	if (c == null) {
    		throw new SQLException("This DatabaseConnection is closed");
    	}
        ResultSet rs = queryResultSet(expression);
       	RQueryResult data = getResult(rs);
        return data;
    }

    public synchronized ResultSet queryResultSet(String expression) throws SQLException {
    	if (c == null) {
    		throw new SQLException("This DatabaseConnection is closed");
    	}
		Statement st = null;
        ResultSet rs = null;
    	try {
	        st = c.createStatement();
	        rs = st.executeQuery(expression);
	        return rs;
	    } catch (SQLException e) {
	    	// If database was closed (OS went to standby) try to restore the connection
	    	if (e.getErrorCode() == 90067 || e.getErrorCode() == 90121) {
	    		System.out.println("Database has been closed");
	    		db.clearConnections();
	    		try {
	    			db.fillPool();
	    			RDatabaseConnection dbc2 = db.getConnection();
	    			c = dbc2.c;
	            	st = c.createStatement();
	            	rs = st.executeQuery(expression);
	            	db.freeConnection(dbc2);
	                return rs;
	    		} catch (Exception e2) {
	    			System.out.println("Database connection restore fillPool problem "+e2.getMessage());
	    		}
	    	}
	    	System.out.println("Caught exception "+e.getErrorCode()+"-"+e.getMessage());
			throw new SQLException("Database error: "+e.getErrorCode()+"-"+e.getMessage());        	
	    }
    }

    public synchronized int update(String expression) throws SQLException {

        Statement st = null;
        st = c.createStatement();    // statements
        int i = st.executeUpdate(expression);    // run the query
        if (i == -1) {
            System.out.println("db error : " + expression);
        }
        keys = null;  // Only valid for the last update
        int rowCount = st.getUpdateCount();
        ResultSet rskeys = st.getGeneratedKeys();
        if (rskeys != null) {
        	keys = getResult(rskeys);
        	//System.out.println(keys);
        }
        st.close();
        return rowCount;
    }

    public static RQueryResult getResult(ResultSet rs) throws SQLException {

        ResultSetMetaData meta   = rs.getMetaData();
        int               colmax = meta.getColumnCount();
        Object            o = null;
        RQueryResult result = new RQueryResult(meta);

        for (; rs.next(); ) {
        	HashMap<String,Object> row = new HashMap<String,Object>();
            for (int i = 0; i < colmax; ++i) {
                o = rs.getObject(i + 1);
                if (meta.getColumnType(i+1) == Types.BOOLEAN && o == null) {
                	o = new Boolean(false);
                }
                row.put(meta.getColumnName(i + 1),o);
            }
            result.add(row);
        }
        return result;
    }

    public PreparedStatement prepareStatement(String query) throws SQLException {
    	return c.prepareStatement(query);
    }
    
    /*
     * Not used very often if at all. Dumps the contents of a result set to System.out
     */
    public static void dump(ResultSet rs) throws SQLException {
    	RQueryResult rows = getResult(rs);
    	if (rows.size() > 0) {
    		for (Object header : rows.get(0).keySet()) {
    			System.out.print((String)header+"\t");
    		}
	    	System.out.println("");
	    	for (HashMap<String,Object> row : rows) {
	    		for (Object data : row.values()) {
	    			System.out.print(""+data+"\t");
	    		}
	    	}
	    	System.out.println("");
    	} else {
    		System.out.println("No rows");
    	}
    }
 
    public RQueryResult getExportedKeys(String schema, String table) {
    	try {
    		DatabaseMetaData dbMeta = c.getMetaData();
    		ResultSet rs = dbMeta.getExportedKeys(null, schema, table);
    		return(getResult(rs));
    	} catch (Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    	
    }
}
