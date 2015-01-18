package permeagility.plus.oracle;


import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import permeagility.util.DatabaseConnection;
import permeagility.util.Setup;
import permeagility.web.Message;
import permeagility.web.Server;
import permeagility.web.Table;

import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class OracleImporter extends Table {
    
    static ConcurrentHashMap<String,RDatabase> cachedConnections = new ConcurrentHashMap<String,RDatabase>();
 
	@Override
	public String getPage(DatabaseConnection con, HashMap<String, String> parms) {
	
		StringBuilder sb = new StringBuilder();
		StringBuilder errors = new StringBuilder();

		String submit = parms.get("SUBMIT");
		String connect = parms.get("CONNECT");
		String schema = parms.get("SCHEMA");
		String table = parms.get("TABLE");
		String editId = parms.get("EDIT_ID");
		String updateId = parms.get("UPDATE_ID");
		String runLog = parms.get("RUNLOG");
		String runSQL = parms.get("RUNSQL");
		String tableName = parms.get("TABLENAME");
		String newTableName = parms.get("NEWTABLENAME");
		String go = parms.get("GO");
		String sqlOverride = null;
		
		RDatabaseConnection dbc = null;
		RDatabase db = null;

		// Run a previous table import - replace some parameters
		if (runLog != null) {
			editId = null;
			ODocument logDoc = con.get(runLog);
			if (logDoc != null) {
				schema = logDoc.field("schema");
				table = logDoc.field("table");
				if (newTableName == null) {
					newTableName = logDoc.field("className");
				}
				ODocument cd = logDoc.field("connection");
				if (cd != null) {
					connect = cd.getIdentity().toString().substring(1);
				}
			}
		}
		
		// Run a previous SQL import - replace some parameters
		if (runSQL != null) {
			editId = null;
			ODocument sqlDoc = con.get(runSQL);
			if (sqlDoc != null) {
				sqlOverride = sqlDoc.field("SQL");
				schema = "SQL";
				table = sqlDoc.field("className");
				ODocument cd = sqlDoc.field("connection");
				if (cd != null) {
					connect = cd.getIdentity().toString().substring(1);
				}
			}
		}
		
		// Process update of work tables
		if (updateId != null && submit != null) {
			System.out.println("update_id="+updateId);
			if (submit.equals(Message.get(con.getLocale(), "DELETE"))) {
				if (deleteRow(con, tableName, parms, errors)) {
					submit = null;
				} else {
					return head("Could not delete", getDateControlScript(con.getLocale())+getColorControlScript())
							+ body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
				}
			} else if (submit.equals(Message.get(con.getLocale(), "UPDATE"))) {
				System.out.println("In updating row");
				if (updateRow(con, tableName, parms, errors)) {
				} else {
					return head("Could not update", getDateControlScript(con.getLocale())+getColorControlScript())
							+ body(standardLayout(con, parms, getTableRowForm(con, tableName, parms) + errors.toString()));
				}
			} 
			// Cancel is assumed
			editId = null;
			updateId = null;
			connect = parms.get(PARM_PREFIX+"connection");
		}

		// Create a SQL import directly - set the created date
		if (submit != null && submit.equals(Message.get(con.getLocale(), "CREATE_ROW"))) {
			parms.put(PARM_PREFIX+"created", formatDate(con.getLocale(), new java.util.Date(), "yyyy-MM-dd HH:mm:ss"));
			boolean inserted = insertRow(con,tableName,parms,errors);
			if (!inserted) {
				errors.append(paragraph("error","Could not insert"));
			}
		}
		
		// Show edit form if row selected for edit
		if (editId != null && submit == null && connect == null) {
			table = tableName;
			return head("Edit", getDateControlScript(con.getLocale())+getColorControlScript())
					+ body(standardLayout(con, parms, getTableRowForm(con, table, parms)));
		}
		
		if (connect != null) {
			System.out.println("Getting connection to "+connect);
			ODocument connectDoc = con.get(connect);
			if (connectDoc == null) {
				errors.append("Could not retrieve connections details using "+connect);
			} else {
				String url = connectDoc.field("databaseURL");
				String user = connectDoc.field("user");
				String pass = connectDoc.field("password");
				int importCount = 0;
				try {
					if (cachedConnections.containsKey(connect)) {
						db = cachedConnections.get(connect);
					} else {
						db = new RDatabase(url, user, pass);
						cachedConnections.put(connect,db);
					}
					if (db != null && db.isConnected()) {
						StringBuilder sbs = new StringBuilder();
						dbc = db.getConnection();
						errors.append(paragraph("success","Connected to Oracle database "+connectDoc.field("name")));
						if (schema == null || schema.equals("")) {
				    		parms.put("SERVICE", "OracleImporter: Select schema for table import");

				    		sb.append(paragraph("banner","Select a schema for a table import"));
							String q = "select distinct owner, count(*) TABLE_COUNT from all_objects where OBJECT_TYPE = 'TABLE' group by owner order by owner";
							RQueryResult qr = dbc.query(q);
							for (HashMap<String,Object> row : qr) {
								sbs.append(rowOnClick("clickable",
										column(row.get("OWNER").toString())
										+column(row.get("TABLE_COUNT").toString())
									,"window.location.href='" + this.getClass().getName()
										+"?CONNECT=" + connect + "&SCHEMA="+row.get("OWNER").toString() + "';"));
							}
							sb.append(table(0,row(columnHeader("Owner")+columnHeader("Table Count"))+sbs.toString()));
							sb.append(br());

							sb.append(paragraph("or run a previous table import"));
							sb.append(getTable(con,parms,PlusSetup.LOGTABLE,"SELECT FROM "+PlusSetup.LOGTABLE+" WHERE connection=#"+connect,"connection",0, "button(RUNLOG:Run), className, schema, table, created, executed"));

							parms.put("FORCE_connection", connect);  // sets foreign key for this connection (used by getTableRowFields)

							sb.append(paragraph("banner","Create a SQL import"));
							if ((Server.getTablePriv(con, PlusSetup.SQLTABLE) & PRIV_CREATE) > 0) {
								sb.append(form("CREATE_NEW_ROW",
									 hidden("CONNECT", connect)
									+hidden("TABLENAME", PlusSetup.SQLTABLE)
									+getTableRowFields(con, PlusSetup.SQLTABLE, parms, "SQL, className, -created, -executed")
									+submitButton(Message.get(con.getLocale(), "CREATE_ROW"))
								));
							}							
							sb.append(br()+br()+paragraph("or run a saved SQL import"));
							sb.append(getTable(con,parms,PlusSetup.SQLTABLE,"SELECT FROM "+PlusSetup.SQLTABLE+" WHERE connection=#"+connect,"connection",0, "button(RUNSQL:Run), className, SQL, created, executed"));

						} else if (table == null || table.equals("")) {
				    		parms.put("SERVICE", "OracleImporter: Select table to import");
							sb.append(paragraph("banner","Select a table"));
							String q = "select TABLE_NAME, NUM_ROWS, AVG_ROW_LEN, LAST_ANALYZED from all_tables where OWNER = '"+schema+"' order by TABLE_NAME";
							RQueryResult qr = dbc.query(q);
							for (HashMap<String,Object> row : qr) {
								Object numRows = row.get("NUM_ROWS");
								Object rowLen = row.get("AVG_ROW_LEN");
								Object lastAnal = row.get("LAST_ANALYZED");
								sbs.append(rowOnClick("clickable",
										column(row.get("TABLE_NAME").toString())
										+column(numRows == null ? "-" : numRows.toString())
										+column(rowLen == null ? "-" : rowLen.toString())
										+column(lastAnal == null ? "-" : lastAnal.toString())
									,"window.location.href='" + this.getClass().getName() + "?CONNECT=" + connect 
									+ "&SCHEMA="+schema+"&TABLE="+row.get("TABLE_NAME").toString() + "';"));
							}
							sb.append(table(0,row(columnHeader("Table Name")
										+columnHeader("Num Rows")
										+columnHeader("Avg Row Len")
										+columnHeader("Last Analyzed"))+sbs.toString()));														
						} else {
							boolean create = false;
							if (go != null && go.equals("YES")) {
								if (newTableName == null || newTableName.equals("")) {
									return "Table name needed - go back and change it";
								}
								System.out.println("****** Creating table for realz now ********");
								create = true;
							}
							if (!create) {	    		
								parms.put("SERVICE", "OracleImporter: confirm class creation and load data");
								sb.append(paragraph("banner","Confirm import"));

								sb.append("This is what I would create<BR>");
								sb.append("A class called "+makePrettyCamelCase(table)+"<BR>");
							} else {
								parms.put("SERVICE", "Import complete:");
								sb.append(paragraph("banner","Oracle Import Done"));
								
								sb.append("I created a class called "+newTableName+" and this data was imported<BR>");
							}
							
							OSchema oschema = null;
							if (create) {
								oschema = con.getDb().getMetadata().getSchema();
							}
							if (oschema != null && oschema.existsClass(newTableName)) {
								return "Table name already exists - go back and change it";
							}
							OClass cls = null;
							if (create && oschema != null) cls = oschema.createClass(newTableName);
							HashMap<String,String> colTypes = new HashMap<String,String>();
							String q = (sqlOverride == null ? "select * from "+schema+"."+table : sqlOverride);
							RQueryResult qr = dbc.query(q);
							if (qr.size() > 0) {
								StringBuilder sbth = new StringBuilder();
								// Assemble header and determine data types from return types,
								// look ahead in rows for null values until a value found
								// If no values, do not import column
								for (String colName : qr.get(0).keySet()) {
									Object o = qr.getValue(0, colName);
									if (o == null) {
										for (int la = 1; la < qr.size(); la++) {
											o = qr.getValue(la, colName);
											if (o != null) {
												break;
											}
										}
									}
									if (o != null) {
										colTypes.put(colName,o.getClass().getName());
										if (!create) sb.append("A property called "+makePrettyCamelCase(colName)+" copying data from "+colName+"<BR>");
									} else {
										errors.append(paragraph("warning","Column "+colName+" has no data and will not be imported"));										
									}
									sbth.append(columnHeader(colName
											+"<BR>"
											+(o == null ? "no data" : o.getClass().getName())
											+"<BR>"
											+(o == null ? "no import" : qr.determineOTypeFromClassName(o.getClass().getName()))
											)
										);
								}
								
								// Create the class and its properties based on the return data types
								if (create && cls != null && colTypes.size() > 0) {
									for (String cn : colTypes.keySet()) {
										String ct = colTypes.get(cn);
										
										Setup.checkCreateProperty(con, cls, makePrettyCamelCase(cn), qr.determineOTypeFromClassName(ct), errors);
									}
								}

								for (HashMap<String,Object> row : qr) {
									ODocument newDoc = null;
									if (create && cls != null) {
										newDoc = con.create(newTableName);
										if (newDoc == null) {
											errors.append(paragraph("error","failed to create "+newTableName+" ODocument"));
										}
									}
									StringBuilder sbtr = new StringBuilder();
									for (String colName : row.keySet()) {
										Object o = row.get(colName);
										sbtr.append(column(o == null ? "-" : o.toString()));
										if (create && newDoc != null && o != null) {
											newDoc.field(makePrettyCamelCase(colName),o.toString());
										}
									}
									sbs.append(row("data", sbtr.toString()));
									if (create && newDoc != null) {
										newDoc.save();
										importCount++;
									}
								}
								sb.append(table(0,row(sbth.toString()+sbs.toString())));
								errors.append(paragraph("success",importCount+" rows imported into "+newTableName));
								if (!create) {
									sb.append(paragraph(qr.size()+" rows will be imported"));
			    					sb.append(form("Confirm new table name "
			    							+(runLog == null ? "" : hidden("RUNLOG",runLog))  // Needs to be passed through
			    							+(runSQL == null ? "" : hidden("RUNSQL",runSQL))
			    							+input("NEWTABLENAME",newTableName != null ? newTableName : makePrettyCamelCase(table))
			    							+button("GO","YES","Create table")));
			    				} else {
			    					if (!schema.equals("SQL")) {
			    						if (runLog != null) {
			    							ODocument importLog = con.get(runLog);
			    							if (importLog != null) {
			    								importLog.field("executed",new java.util.Date());
			    								importLog.save();
			    								System.out.println("ImportLog executed date updated");
			    							}
			    						} else {
					    					ODocument importLog = con.create(PlusSetup.LOGTABLE);
					    					if (importLog != null) {
					    						importLog.field("schema",schema);
					    						importLog.field("table",table);
					    						importLog.field("connection",connectDoc);
					    						importLog.field("className",newTableName);
					    						importLog.field("created",new java.util.Date());
					    						importLog.save();
												errors.append(paragraph("success","Import saved in "+PlusSetup.LOGTABLE));
					    					}
			    						}
			    					} else {
			    						if (runSQL != null) {
			    							ODocument importSQL = con.get(runSQL);
			    							if (importSQL != null) {
			    								importSQL.field("executed",new java.util.Date());
			    								importSQL.save();
			    								System.out.println("ImportSQL executed date updated");
			    							}
			    						}			    						
			    					}
			    					sb.append(br()+link("permeagility.web.Table?TABLENAME="+newTableName ,"Go to the new "+newTableName+" table"));
			    					sb.append(br()+link("permeagility.plus.oracle.OracleImporter"+"?CONNECT="+connect+"&SCHEMA="+schema ,"Select another table from "+schema));
			    					sb.append(br()+link("permeagility.plus.oracle.OracleImporter"+"?CONNECT="+connect,"Select a different schema or saved import"));
			    					sb.append(br()+link("permeagility.plus.oracle.OracleImporter","Back to import connections"));
			    				}			
							} else {
								errors.append(paragraph("error","No rows to import - cannot import an empty table"));
							}
						}
					} else {
						errors.append(paragraph("error","Could not connect to Oracle database with url="+url+" user="+user));
					}
				} catch (Exception e) {
					return e.getMessage()+" - go back and correct it";
				} finally {
					if (db != null && dbc != null) {
						db.freeConnection(dbc);
					}					
				}
			}
		}
		if (sb.length() == 0) {
	    	try {
	    		parms.put("SERVICE", "OracleImporter: Setup/Select Oracle connection");
				sb.append(paragraph("banner","Select Oracle Connection"));
				sb.append(getTable(con,parms,PlusSetup.TABLE,"SELECT FROM "+PlusSetup.TABLE, null,0, "button(CONNECT:Connect), name, databaseURL, user"));
	    	} catch (Exception e) {  
	    		e.printStackTrace();
	    		sb.append("Error retrieving import patterns: "+e.getMessage());
	    	}
		}
		return 	head("Context")+body(standardLayout(con, parms, 
				errors.toString()
				+sb.toString()
				+((Server.getTablePriv(con, PlusSetup.TABLE) & PRIV_CREATE) > 0 && connect == null ? popupForm("CREATE_NEW_ROW",null,Message.get(con.getLocale(),"NEW_CONNECTION"),null,"NAME",
						paragraph("banner",Message.get(con.getLocale(), "CREATE_ROW"))
						+hidden("TABLENAME", PlusSetup.TABLE)
						+getTableRowFields(con, PlusSetup.TABLE, parms)
						+submitButton(Message.get(con.getLocale(), "CREATE_ROW"))) : "")
				));
	}

}

