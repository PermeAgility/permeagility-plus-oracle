/* Copyright (c) 2012 PermeAgility Incorporated. 
This component and the accompanying materials are made available under the terms of the 
"Eclipse Public License v1.0" which accompanies this distribution, and is available
at the URL "http://www.eclipse.org/legal/epl-v10.html".
*/
package permeagility.plus.oracle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class RDatabase {
	static int POOL_SIZE = 1;

	private LinkedList<RDatabaseConnection> pooledConnections = new LinkedList<RDatabaseConnection>(); 
	private LinkedList<RDatabaseConnection> activeConnections = new LinkedList<RDatabaseConnection>(); 

	private RDatabaseConnection c = null;
	
	private String url = null;
	private String user = null;
	private String password = null;
	
	private Locale locale = Locale.getDefault(); 
	
	private Date lastAccessed = null;
	
	public RDatabase(String dbUrl, String dbUser, String dbPass) throws Exception {
		url = dbUrl;
		user = dbUser;
		password = dbPass;
		fillPool();		
	}

	public void setPassword(String pass) {
		password = pass;
	}
	
	public boolean isPassword(String pass) {
		if (pass == null) pass = "";
		return pass.equals(password);
	}
	
	public void setPoolSize(int ps) {
		POOL_SIZE = ps;
		try {
			fillPool();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String getUser() {
		return user;
	}
	
	public String getVersion(RDatabaseConnection con) {
		if (con != null) {
			try {
				return con.c.getMetaData().getDatabaseProductVersion();
			} catch (Exception e) {
				e.printStackTrace();
				return "Exception "+e.getLocalizedMessage();
			}
		} else {
			return "Unknown";
		}
	}

	public Date getLastAccessed() {
		return lastAccessed;
	}
	
	public void setLocale(Locale l) {
		System.out.println("Setting database locale to "+l);
		locale = l;
	}
	
	public Locale getLocale() {
		return locale;
	}
	
	/*
	 * Not very reliable. Can't be trusted to know if any of the connections are good
	 */
	public boolean isConnected() {   
		return pooledConnections.size() + activeConnections.size() > 0;
	}

	public RDatabaseConnection getConnection() {
		lastAccessed = new Date();
		if (pooledConnections.size() == 0) {
			try {
				fillPool();
			} catch (Exception e) {
				System.out.println("DatabaseConnection.getConnection - unable to get connection: "+e.getMessage());
				return null;
			}
		}	
		RDatabaseConnection c = null;
		try {
			c = pooledConnections.poll();
		} catch (Exception e) {
			System.out.println("!"+e.getMessage());
		}
		if (c != null) {
			activeConnections.add(c);
			return c;
		} else {
			return null;
		}
	}
	
	public void clearConnections() {
		activeConnections.removeAll(activeConnections);
		pooledConnections.removeAll(pooledConnections);
		
	}

	public void close() {
		for (RDatabaseConnection c : activeConnections) {
			c.close();
		}
		activeConnections.removeAll(activeConnections);
		for (RDatabaseConnection c : pooledConnections) {
			c.close();
		}
		pooledConnections.removeAll(pooledConnections);
		System.out.println("DatabaseConnections are closed.");		
	}
		
	public void fillPool() throws Exception {
		while ( pooledConnections.size() < POOL_SIZE) {
			Connection c = null;
			try {
				c = DriverManager.getConnection(url,user,password);
			} catch (Exception e) {
				throw new Exception(e.getLocalizedMessage());
			}
			if (c == null) {
				System.out.println("Unable to open a connection for the pool");
				break;
			}
			RDatabaseConnection dbc = new RDatabaseConnection(this,c);
			pooledConnections.add(dbc);
		}
	}

	public void freeConnection(RDatabaseConnection dbc) {
		activeConnections.remove(dbc);
		if (dbc != null && dbc.isConnected()) {
			pooledConnections.add(dbc);
		}
	}
	
    /*
     * This isn't being used and should probably be moved somewhere more appropriate
     */
	public synchronized int checkPoint() throws SQLException {
    	int i = c.update("CHECKPOINT");
        System.out.println("Checkpoint returned "+i);
        return i;
    }
	
}
