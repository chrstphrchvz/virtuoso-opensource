/*
 *  
 *  This file is part of the OpenLink Software Virtuoso Open-Source (VOS)
 *  project.
 *  
 *  Copyright (C) 1998-2006 OpenLink Software
 *  
 *  This project is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation; only version 2 of the License, dated June 1991.
 *  
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 *  
 *  
*/
/* VirtuosoPreparedStatement.java */
package virtuoso.jdbc2;

import java.sql.*;
import java.util.*;
import java.io.*;
import java.math.*;
import openlink.util.*;

/**
 * The VirtuosoPreparedStatement class is an implementation of the PreparedStatement interface
 * in the JDBC API which represents a prepared statement.
 * You can obtain a Statement like below :
 * <pre>
 *   <code>PreparedStatement s = connection.prepareStatement(...)</code>
 * </pre>
 *
 * @version 1.0 (JDBC API 2.0 implementation)
 * @see virtuoso.jdbc2.VirtuosoConnection#prepareStatement
 */
public class VirtuosoPreparedStatement extends VirtuosoStatement implements PreparedStatement
{
   // The sql string with ?
   private String sql;
#if JDK_VER <= 14
   private static final int _EXECUTE_FAILED = -3;
#else
   private static final int _EXECUTE_FAILED = Statement.ExecuteFailed;
#endif

   /**
    * Constructs a new VirtuosoPreparedStatement that is forward-only and read-only.
    *
    * @param connection The VirtuosoConnection which owns it.
    * @param sql        The sql string with ?.
    * @exception virtuoso.jdbc2.VirtuosoException An internal error occurred.
    */
   VirtuosoPreparedStatement(VirtuosoConnection connection, String sql) throws VirtuosoException
   {
      this (connection,sql,VirtuosoResultSet.TYPE_FORWARD_ONLY,VirtuosoResultSet.CONCUR_READ_ONLY);
   }

   /**
    * Constructs a new VirtuosoPreparedStatement with specific options.
    *
    * @param connection The VirtuosoConnection which owns it.
    * @param sql        The sql string with ?.
    * @param type       The result set type.
    * @param concurrency   The result set concurrency.
    * @exception virtuoso.jdbc2.VirtuosoException An internal error occurred.
    * @see java.sql.ResultSet
    */
   VirtuosoPreparedStatement(VirtuosoConnection connection, String sql, int type, int concurrency) throws VirtuosoException
   {
      super(connection,type,concurrency);
      synchronized (connection)
	{
	  try
	    {
	      // Parse the sql query
	      this.sql = sql;
	      parse_sql();
	      // Send RPC call
	      Object[] args = new Object[4];
	      args[0] = (statid == null) ? statid = new String("s" + connection.hashCode() + (req_no++)) : statid;
	      //args[0] = statid = new String("s" + connection.hashCode() + (req_no++));
	      args[1] = connection.escapeSQL(sql);
	      args[2] = new Long(0);
	      args[3] = getStmtOpts();
	      // Create a future
	      future = connection.getFuture(VirtuosoFuture.prepare,args, this.rpc_timeout);
	      // Process result to get information about results meta data
	      vresultSet = new VirtuosoResultSet(this,metaData);
              clearParameters();
	    }
	  catch(IOException e)
	    {
	      throw new VirtuosoException("Problem during serialization : " + e.getMessage(),VirtuosoException.IOERROR);
	    }
	}
   }

   /**
    * Method parses the sql string with ?
    */
   private void parse_sql()
   {
      String sql = this.sql;
      int count = 0;
      do
      {
         int index = sql.indexOf("?");
         if(index >= 0)
         {
            count++;
            sql = sql.substring(index + 1,sql.length());
            if(sql == null)
               sql = "";
         }
         else
            sql = "";
      }
      while(sql.length() != 0);
      parameters = new openlink.util.Vector(count);
      objparams = new openlink.util.Vector(count);
   }

   /**
    * Executes a SQL statement that returns a single ResultSet.
    *
    * @param sql  the SQL request.
    * @return ResultSet  A ResultSet that contains the data produced by
    * the query; never null.
    * @exception virtuoso.jdbc2.VirtuosoException  If a database access error occurs.
    */
   private void sendQuery() throws VirtuosoException
   {
     synchronized (connection)
       {
	 Object[] args = new Object[6];
	 openlink.util.Vector vect = new openlink.util.Vector(1);
	 // Set arguments to the RPC function
	 args[0] = statid;
	 args[2] = (cursorName == null) ? args[0] : cursorName;
	 args[1] = null;
	 args[3] = vect;
	 args[4] = null;
	 try
	   {
	     // Add parameters
	     vect.addElement(objparams);
	     // Put the options array in the args array
	     args[5] = getStmtOpts();
	     future = connection.getFuture(VirtuosoFuture.exec,args, this.rpc_timeout);
	     vresultSet.getMoreResults();
	   }
	 catch(IOException e)
	   {
	     throw new VirtuosoException("Problem during serialization : " + e.getMessage(),VirtuosoException.IOERROR);
	   }
       }
   }

   /**
    * Sets the designated parameter to a Java Vector value.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    */
   protected void setVector(int parameterIndex, openlink.util.Vector x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if(x == null) this.setNull(parameterIndex, Types.ARRAY);
		else objparams.setElementAt(x,parameterIndex - 1);
   }

   // --------------------------- JDBC 1.0 ------------------------------
   /**
    * Clears the current parameter values immediately.
    *
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#clearParameters
    */
   public void clearParameters() throws VirtuosoException
   {
      // Clear parameters
      objparams.removeAllElements();
      /*for(int i=0 ; i<parameters.capacity() ; i++)
         objparams.setElementAt(null, i);*/
   }

   /**
    * Executes any kind of SQL statement.
    * Some prepared statements return multiple results; the execute
    * method handles these complex statements as well as the simpler
    * form of statements handled by executeQuery and executeUpdate.
    *
    * @exception virtuoso.jdbc2.VirtuosoException  If a database access error occurs.
    * @see virtuoso.jdbc2.VirtuosoStatement#getResultSet
    * @see virtuoso.jdbc2.VirtuosoStatement#getUpdateCount
    * @see virtuoso.jdbc2.VirtuosoStatement#getMoreResults
    * @see java.sql.PreparedStatement#execute
    */
   public boolean execute() throws VirtuosoException
   {
     synchronized (connection)
       {
	 sendQuery();
	 // Test the kind of operation
	 return vresultSet.more_result();
       }
   }

   /**
    * Executes the SQL INSERT, UPDATE or DELETE statement
    * in this PreparedStatement object.
    * In addition,
    * SQL statements that return nothing, such as SQL DDL statements,
    * can be executed.
    *
    * @return either the row count for INSERT, UPDATE or DELETE statements;
    * or 0 for SQL statements that return nothing
    * @exception virtuoso.jdbc2.VirtuosoException  If a database access error occurs.
    * @see java.sql.PreparedStatement#executeUpdate
    */
   public int executeUpdate() throws VirtuosoException
   {
     synchronized (connection)
       {
	 sendQuery();
	 return vresultSet.getUpdateCount();
       }
   }

   public int[] executeBatchUpdate() throws VirtuosoException
   {
     int size = batch.size();
     int[] res = new int[size];  
     synchronized (connection)
       {
	 Object[] args = new Object[6];
	 // Set arguments to the RPC function
	 args[0] = statid;
	 args[2] = (cursorName == null) ? args[0] : cursorName;
	 args[1] = null;
	 args[3] = batch;
	 args[4] = null;
	 try
	   {
	     // Put the options array in the args array
	     args[5] = getStmtOpts();
	     future = connection.getFuture(VirtuosoFuture.exec,args, this.rpc_timeout);
	     for (int inx = 0; inx < size; inx++)
	     {
		 vresultSet.setUpdateCount (0);
		 vresultSet.getMoreResults ();
		 res[inx] = vresultSet.getUpdateCount();
	     }
	   }
	 catch(IOException e)
	   {
	     throw new VirtuosoException("Problem during serialization : " + e.getMessage(),VirtuosoException.IOERROR);
	   }
       }
     return res;
   }

   /**
    * Executes a SQL prepare statement that returns a single ResultSet.
    *
    * @return ResultSet  A ResultSet that contains the data produced by
    * the query; never null.
    * @exception virtuoso.jdbc2.VirtuosoException  If a database access error occurs.
    * @see java.sql.PreparedStatement#executeQuery
    */
   public ResultSet executeQuery() throws VirtuosoException
   {
     synchronized (connection)
       {
	 sendQuery();
	 return vresultSet;
       }
   }

   /**
    * Gets the number, types and properties of a ResultSet's columns.
    *
    * @return the description of a ResultSet's columns
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#getMetaData
    */
   public ResultSetMetaData getMetaData() throws VirtuosoException
   {
      if(vresultSet != null)
         return vresultSet.getMetaData();
      throw new VirtuosoException("Prepared statement closed",VirtuosoException.CLOSED);
   }

   /**
    * Releases this Statement object's database
    * and JDBC resources immediately instead of new wait for
    * this to happen when it is automatically closed.
    *
    * @exception virtuoso.jdbc2.VirtuosoException If a database access error occurs.
    * @see java.sql.Statement#close
    */
   public void close() throws VirtuosoException
   {
     synchronized (connection)
       {
	 try
	   {
	     // Check if a statement is treat
	     if(statid == null)
	       return;
	     // Cancel current result set
	     cancel();
	     // Build the args array
	     Object[] args = new Object[2];
	     args[0] = statid;
	     args[1] = new Long(VirtuosoTypes.STAT_CLOSE);
	     // Create and get a future for this
	     future = connection.getFuture(VirtuosoFuture.close,args, this.rpc_timeout);
	     // Read the answer
	     future.nextResult();
	     // Remove the future reference
	     connection.removeFuture(future);
	     future = null;
	   }
	 catch(IOException e)
	   {
	     throw new VirtuosoException("Problem during closing : " + e.getMessage(),VirtuosoException.IOERROR);
	   }
       }
   }

   /**
    * Sets the designated parameter to the given input stream, which will have
    * the specified number of bytes.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the Java input stream that contains the ASCII parameter value
    * @param length the number of bytes in the stream
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setAsciiStream
    */
   public void setAsciiStream(int parameterIndex, InputStream x, int length) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      // After check, check if a Blob object is already associated or not
      Object _obj = objparams.elementAt(parameterIndex - 1);
      if (parameters != null && parameters.elementAt(parameterIndex - 1) instanceof openlink.util.Vector)
	{
	  openlink.util.Vector pd = (openlink.util.Vector)parameters.elementAt(parameterIndex - 1);
	  int dtp = ((Number)pd.elementAt (0)).intValue();
	  if (dtp != VirtuosoTypes.DV_BLOB &&
	      dtp != VirtuosoTypes.DV_BLOB_BIN &&
	      dtp != VirtuosoTypes.DV_BLOB_WIDE)
	    throw new VirtuosoException ("Passing streams to non-blob columns not supported",
		"IM001", VirtuosoException.NOTIMPLEMENTED);
	  if (dtp == VirtuosoTypes.DV_BLOB_BIN)
	    throw new VirtuosoException ("Passing ASCII stream to LONG VARBINARY columns not supported",
		"IM001", VirtuosoException.NOTIMPLEMENTED);
	}

      // Check now if it's a Blob
      if(_obj instanceof VirtuosoBlob)
	{
	  ((VirtuosoBlob)_obj).setInputStream(x,length);
	  try
	    {
	      ((VirtuosoBlob)_obj).setReader(new InputStreamReader (x, "ASCII"),length);
	    }
	  catch (UnsupportedEncodingException e)
	    {
	      ((VirtuosoBlob)_obj).setReader(new InputStreamReader (x),length);
	    }
	}
      else
	{
	  // Else create a Clob
	  if(x == null)
	    this.setNull(parameterIndex, Types.CLOB);
	  else
	    {
	      InputStreamReader rd;
	      try
		{
		  rd = new InputStreamReader (x, "ASCII");
		}
	      catch (UnsupportedEncodingException e)
		{
		  rd = new InputStreamReader (x);
		}
	      VirtuosoBlob bl = new VirtuosoBlob(rd, length, parameterIndex - 1);
	      bl.setInputStream (x, length);
	      objparams.setElementAt(bl, parameterIndex - 1);
	    }
	}
   }

   /**
    * Sets the designated parameter to a java.lang.BigDecimal value.
    * The driver converts this to an SQL NUMERIC value when
    * it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setBigDecimal
    */
   public void setBigDecimal(int parameterIndex, BigDecimal x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if(x == null) this.setNull(parameterIndex, Types.NUMERIC);
		else objparams.setElementAt(x,parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to the given input stream, which will have
    * the specified number of bytes.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the java input stream which contains the binary parameter value
    * @param length the number of bytes in the stream
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setBinaryStream
    */
   public void setBinaryStream(int parameterIndex, InputStream x, int length) throws VirtuosoException
   {
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      // After check, check if a Blob object is already associated or not
      Object _obj = objparams.elementAt(parameterIndex - 1);

      if (parameters != null && parameters.elementAt(parameterIndex - 1) instanceof openlink.util.Vector)
	{
	  openlink.util.Vector pd = (openlink.util.Vector)parameters.elementAt(parameterIndex - 1);
	  int dtp = ((Number)pd.elementAt (0)).intValue();
	  if (dtp != VirtuosoTypes.DV_BLOB &&
	      dtp != VirtuosoTypes.DV_BLOB_BIN &&
	      dtp != VirtuosoTypes.DV_BLOB_WIDE)
	    throw new VirtuosoException ("Passing streams to non-blob columns not supported",
		"IM001", VirtuosoException.NOTIMPLEMENTED);
	  if (dtp == VirtuosoTypes.DV_BLOB_WIDE)
	    throw new VirtuosoException ("Passing binary stream to LONG NVARCHAR columns not supported",
		"IM001", VirtuosoException.NOTIMPLEMENTED);
	}

      // Check now if it's a Blob
      if(_obj instanceof VirtuosoBlob)
	{
	  ((VirtuosoBlob)_obj).setInputStream(x,length);
	  try
	    {
	      ((VirtuosoBlob)_obj).setReader(new InputStreamReader (x, "8859_1"),length);
	    }
	  catch (UnsupportedEncodingException e)
	    {
	      ((VirtuosoBlob)_obj).setReader(new InputStreamReader (x),length);
	    }
	}
      else
	{
	  // Else create a Blob
	  if(x == null)
	    this.setNull(parameterIndex, Types.BLOB);
	  else
	    {
	      InputStreamReader rd;
	      try
		{
		  rd = new InputStreamReader (x, "8859_1");
		}
	      catch (UnsupportedEncodingException e)
		{
		  rd = new InputStreamReader (x);
		}
	      VirtuosoBlob bl = new VirtuosoBlob(rd, length, parameterIndex - 1);
	      bl.setInputStream (x, length);
	      objparams.setElementAt(bl, parameterIndex - 1);
	    }
	}
   }

   /**
    * Sets the designated parameter to the given input stream, which will have
    * the specified number of unicode chars.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the java input stream which contains the binary parameter value
    * @param length the number of bytes in the stream
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setBinaryStream
    */
   public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws VirtuosoException
   {
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      // After check, check if a Blob object is already associated or not
      Object _obj = objparams.elementAt(parameterIndex - 1);
      if (parameters != null && parameters.elementAt(parameterIndex - 1) instanceof openlink.util.Vector)
	{
	  openlink.util.Vector pd = (openlink.util.Vector)parameters.elementAt(parameterIndex - 1);
	  int dtp = ((Number)pd.elementAt (0)).intValue();
	  if (dtp != VirtuosoTypes.DV_BLOB &&
	      dtp != VirtuosoTypes.DV_BLOB_BIN &&
	      dtp != VirtuosoTypes.DV_BLOB_WIDE)
	    throw new VirtuosoException ("Passing streams to non-blob columns not supported",
		"IM001", VirtuosoException.NOTIMPLEMENTED);
	  if (dtp == VirtuosoTypes.DV_BLOB_BIN)
	    throw new VirtuosoException ("Passing unicode stream to LONG VARBINARY columns not supported",
		"IM001", VirtuosoException.NOTIMPLEMENTED);
	}

      // Check now if it's a Blob
      if(_obj instanceof VirtuosoBlob)
	{
	  ((VirtuosoBlob)_obj).setInputStream(x,length);
	  try
	    {
	      ((VirtuosoBlob)_obj).setReader(new InputStreamReader (x, "UTF8"),length);
	    }
	  catch (UnsupportedEncodingException e)
	    {
	      ((VirtuosoBlob)_obj).setReader(new InputStreamReader (x),length);
	    }
	}
      else
	{
	  // Else create a Blob
	  if(x == null)
	    this.setNull(parameterIndex, Types.CLOB);
	  else
	    {
	      InputStreamReader rd;
	      try
		{
		  rd = new InputStreamReader (x, "UTF8");
		}
	      catch (UnsupportedEncodingException e)
		{
		  rd = new InputStreamReader (x);
		}
	      VirtuosoBlob bl = new VirtuosoBlob(rd, length, parameterIndex - 1);
	      bl.setInputStream (x, length);
	      objparams.setElementAt(bl, parameterIndex - 1);
	    }
	}
   }

   /**
    * Sets the designated parameter to a Java boolean value.  The driver converts this
    * to an SQL BIT value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setBoolean
    */
   public void setBoolean(int parameterIndex, boolean x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new Boolean(x),parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to a Java byte value.  The driver converts this
    * to an SQL TINYINT value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setByte
    */
   public void setByte(int parameterIndex, byte x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new Byte(x),parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to a Java array of bytes.  The driver converts
    * this to an SQL VARBINARY or LONGVARBINARY (depending on the
    * argument's size relative to the driver's limits on VARBINARYs)
    * when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setBytes
    */
   public void setBytes(int parameterIndex, byte x[]) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex +
	     " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);

      if(x == null)
	this.setNull(parameterIndex, Types.VARBINARY);
      else
	{
	  objparams.setElementAt(x, parameterIndex - 1);
	}
   }

   /**
    * Sets the designated parameter to a java.sql.Date value.  The driver converts this
    * to an SQL DATE value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setDate
    */
   public void setDate(int parameterIndex, java.sql.Date x) throws VirtuosoException
   {
      setDate(parameterIndex,x,null);
   }

   /**
    * Sets the designated parameter to a Java double value.  The driver converts this
    * to an SQL DOUBLE value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setDouble
    */
   public void setDouble(int parameterIndex, double x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new Double(x),parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to a Java float value.  The driver converts this
    * to an SQL FLOAT value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setFloat
    */
   public void setFloat(int parameterIndex, float x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new Float(x),parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to a Java int value.  The driver converts this
    * to an SQL INTEGER value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setInt
    */
   public void setInt(int parameterIndex, int x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new Integer(x),parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to a Java long value.  The driver converts this
    * to an SQL BIGINT value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setLong
    */
   public void setLong(int parameterIndex, long x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new Long(x),parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to SQL NULL.
    *
    * Note: You must specify the parameter's SQL type.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param sqlType the SQL type code defined in java.sql.Types
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setNull
    */
   public void setNull(int parameterIndex, int sqlType) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new VirtuosoNullParameter(sqlType, true),parameterIndex - 1);
   }

   /**
    * Sets the value of a parameter using an object; use the
    * java.lang equivalent objects for integral values.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the object containing the input parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setObject
    */
   public void setObject(int parameterIndex, Object x) throws VirtuosoException
   {
      setObject(parameterIndex,x,Types.OTHER);
   }

   /**
    * Sets the value of the designated parameter with the given object.
    * This method is like setObject above, except that it assumes a scale of zero.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the object containing the input parameter value
    * @param targetSqlType the SQL type (as defined in java.sql.Types) to be
    * sent to the database
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setObject
    */
   public void setObject(int parameterIndex, Object x, int targetSqlType) throws VirtuosoException
   {
      setObject(parameterIndex,x,targetSqlType, 0);
   }

   protected Object mapJavaTypeToSqlType (Object x, int targetSqlType, int scale) throws VirtuosoException
   {
     if (x == null)
       return x;
     if (x instanceof java.lang.Boolean)
       x = new Integer (((Boolean)x).booleanValue() ? 1 : 0);

     switch (targetSqlType)
       {
	  case Types.CHAR:
	  case Types.VARCHAR:
	      if (x instanceof java.util.Date || x instanceof java.lang.Number || x instanceof java.lang.String)
		return x;
	      else
		return x.toString();

	  case Types.LONGVARCHAR:
#if JDK_VER >= 12
              if (x instanceof java.sql.Clob || x instanceof java.sql.Blob || x instanceof java.lang.String)
#else
              if (x instanceof VirtuosoClob || x instanceof VirtuosoBlob || x instanceof java.lang.String)
#endif
                return x;
              else
		return x.toString();

	  case Types.DATE:
	  case Types.TIME:
	  case Types.TIMESTAMP:
	      if (x instanceof java.util.Date || x instanceof java.lang.String)
		return x;
              break;

          case Types.NUMERIC:
          case Types.DECIMAL:
              {
                java.math.BigDecimal bd = null;
		if (x instanceof java.math.BigDecimal)
		  bd = (java.math.BigDecimal) x;
                else if (x instanceof java.lang.String)
		  bd = new java.math.BigDecimal ((String) x);
		else if (x instanceof java.lang.Number)
		  bd = new java.math.BigDecimal (x.toString());
                if (bd != null)
		  return bd.setScale (scale);
              }
	      break;

          case Types.BIGINT:
              if (x instanceof java.math.BigDecimal || x instanceof java.lang.String)
                return x;
              else if (x instanceof java.lang.Number)
                return new java.math.BigDecimal (x.toString());
	      break;

          case Types.FLOAT:
          case Types.DOUBLE:
              if (x instanceof java.lang.Double)
                return x;
              else if (x instanceof java.lang.Number)
                return new Double (((Number)x).doubleValue());
              else if (x instanceof java.lang.String)
                return new Double ((String) x);
	      break;
          case Types.INTEGER:
              if (x instanceof java.lang.Integer)
                return x;
              else if (x instanceof java.lang.Number)
                return new Integer (((Number)x).intValue());
              else if (x instanceof java.lang.String)
                return new Integer ((String) x);
	      break;

          case Types.REAL:
              if (x instanceof java.lang.Float)
                return x;
              else if (x instanceof java.lang.Number)
                return new Float (((Number)x).floatValue());
              else if (x instanceof java.lang.String)
                return new Float ((String) x);
	      break;

          case Types.SMALLINT:
          case Types.TINYINT:
          case Types.BIT:
#if JDK_VER >= 14
          case Types.BOOLEAN:
#endif
              if (x instanceof java.lang.Short)
                return x;
              else if (x instanceof java.lang.String)
                return new Short ((String) x);
              else if (x instanceof java.lang.Number)
                return new Short (((Number)x).shortValue());
	      break;

	  case Types.ARRAY:
#if JDK_VER >= 14
	  case Types.DATALINK:
#endif
          case Types.DISTINCT:
	  case Types.REF:
	      throw new VirtuosoException ("Type not supported", VirtuosoException.NOTIMPLEMENTED);

          case Types.VARBINARY:
              if (x instanceof byte[])
                return x;
              break;

          case Types.LONGVARBINARY:
#if JDK_VER >= 12
              if (x instanceof java.sql.Blob || x instanceof byte [])
#else
              if (x instanceof VirtuosoBlob || x instanceof byte [])
#endif
                return x;
              break;

	  default:
	      return x;
       }
     throw new VirtuosoException ("Invalid value specified", VirtuosoException.BADPARAM);
   }

   /**
    * Sets the value of a parameter using an object. The second
    * argument must be an object type; for integral values, the
    * java.lang equivalent objects should be used.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the object containing the input parameter value
    * @param targetSqlType the SQL type (as defined in java.sql.Types) to be
    * sent to the database. The scale argument may further qualify this type.
    * @param scale for java.sql.Types.DECIMAL or java.sql.Types.NUMERIC types,
    * this is the number of digits after the decimal point.  For all other
    * types, this value will be ignored.
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setObject
    * @see java.sql.Types
    */
   public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws VirtuosoException
   {
      //System.err.println ("setObject (" + parameterIndex + ", " + x + ", " + targetSqlType + ", " + scale);
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if (x instanceof VirtuosoExplicitString)
	{
	  objparams.setElementAt(x, parameterIndex - 1);
	  return;
	}
      // After check, check if a Blob object is already associated or not
      Object _obj = objparams.elementAt(parameterIndex - 1);
      // Check now if it's a Blob
      if(_obj instanceof VirtuosoBlob)
      {
         ((VirtuosoBlob)_obj).setObject(x);
         return;
      }
      // Else create a Blob
      if(x == null) this.setNull(parameterIndex, Types.OTHER);
      x = mapJavaTypeToSqlType (x, targetSqlType, scale);
      if (x instanceof java.io.Serializable)
	{
	  //System.err.println ("setObject2 (" + parameterIndex + ", " + x + ", " + targetSqlType + ", " + scale);
	  objparams.setElementAt (x, parameterIndex - 1);
	}
      else
	throw new VirtuosoException ("Object " + x.getClass().getName() + " not serializable", "22023",
	    VirtuosoException.BADFORMAT);
   }

   /**
    * Sets the designated parameter to a Java short value.  The driver converts this
    * to an SQL SMALLINT value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setShort
    */
   public void setShort(int parameterIndex, short x) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      objparams.setElementAt(new Short(x),parameterIndex - 1);
   }

   /**
    * Sets the designated parameter to a Java String value.  The driver converts this
    * to an SQL VARCHAR or LONGVARCHAR value (depending on the argument's
    * size relative to the driver's limits on VARCHARs) when it sends
    * it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setString
    */
   public void setString(int parameterIndex, String x1) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " +
	     parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if(x1 == null)
	this.setNull(parameterIndex, Types.VARCHAR);
      else
	{
	  String x;
	  int zero_inx = x1.indexOf (0);
	  /*
	     if (zero_inx == -1)
	     {
	     char zeroc [] = { 0 };
	     x = x1 + (new String (zeroc));
	     }
	     else */if (zero_inx > 0 && zero_inx < x1.length())
	    {
	      //System.out.println ("truncate @ " + zero_inx);
	      x = x1.substring (0, zero_inx);
	    }
	  else
	    x = x1;
	  if (parameters != null && parameters.elementAt(parameterIndex - 1) instanceof openlink.util.Vector)
	    {
	      openlink.util.Vector pd = (openlink.util.Vector)parameters.elementAt(parameterIndex - 1);
	      int dtp = ((Number)pd.elementAt (0)).intValue();
	      VirtuosoExplicitString ret;
	      ret = new
		  VirtuosoExplicitString (x, dtp, connection);
	      objparams.setElementAt (ret, parameterIndex - 1);
	    }
	  else
	    objparams.setElementAt(x,parameterIndex - 1);
	}
   }

   protected void setString(int parameterIndex, VirtuosoExplicitString x1) throws VirtuosoException
   {
     if(parameterIndex < 1 || parameterIndex > parameters.capacity())
       throw new VirtuosoException("Index " +
	   parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
     if(x1 == null)
       this.setNull(parameterIndex, Types.VARCHAR);
     else
        objparams.setElementAt(x1, parameterIndex - 1);
   }
   /**
    * Sets the designated parameter to a java.sql.Time value.  The driver converts this
    * to an SQL TIME value when it sends it to the database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setTime
    */
   public void setTime(int parameterIndex, java.sql.Time x) throws VirtuosoException
   {
      setTime(parameterIndex,x,null);
   }

   /**
    * Sets the designated parameter to a java.sql.Timestamp value.  The driver
    * converts this to an SQL TIMESTAMP value when it sends it to the
    * database.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setTimeStamp
    */
   public void setTimestamp(int parameterIndex, java.sql.Timestamp x) throws VirtuosoException
   {
      setTimestamp(parameterIndex,x,null);
   }

   // --------------------------- JDBC 2.0 ------------------------------
   /**
    * Adds a set of parameters to the batch.
    *
    * @exception virtuoso.jdbc2.VirtuosoException  If a database access error occurs.
    * @see java.sql.PreparedStatement#addBatch
    */
   public void addBatch() throws VirtuosoException
   {
      // Check parameters and batch vector
      if(parameters == null)
         return;
      if(batch == null)
#if JDK_VER >= 12
         batch = new LinkedList();
#else
         batch = new openlink.util.Vector(10,10);
#endif
      // Add the sql request at the end
#if JDK_VER >= 12
      batch.add(objparams.clone());
#else
      batch.addElement(objparams.clone());
#endif
   }

   /**
    * Submits a batch of commands to the database for execution.
    *
    * @return an array of update counts containing one element for each
    * command in the batch.  The array is ordered according
    * to the order in which commands were inserted into the batch.
    * @exception BatchUpdateException if a database access error occurs or the
    * driver does not support batch statements
    */

   private void throwBatchUpdateException (int [] result, String msg, int inx) throws BatchUpdateException
   {
     int [] _result = new int[inx + 1];
     System.arraycopy (result, 0, _result, 0, inx);
     _result[inx] = _EXECUTE_FAILED;
     throw new BatchUpdateException(msg, _result);
   }

   public int[] executeBatch() throws BatchUpdateException
   {
      // Check if the batch vector exists
      if(batch == null)
         return new int[0];
      // Else execute one by one SQL request
      int[] result = new int[batch.size()];
      int inx = 0;
      // Flag to say if there's a problem
      boolean error = false;

      if (this instanceof VirtuosoCallableStatement && ((VirtuosoCallableStatement)this).hasOut())
	throwBatchUpdateException (result, "Batch can't execute calls with out params", 0);

      try
	{
      	  if (vresultSet.kindop()==VirtuosoTypes.QT_SELECT)
	    throwBatchUpdateException (result, "Batch executes only update statements", inx);

	  result = executeBatchUpdate ();

#if JDK_VER >= 12
	  batch.clear();
#else
	  batch.removeAllElements();
#endif
	}
      catch (SQLException e)
	{
#if JDK_VER >= 12
	  batch.clear();
#else
	  batch.removeAllElements();
#endif
	  throwBatchUpdateException (result, e.getMessage(), inx);
	}

      return result;
   }

#if JDK_VER >= 12
   /**
    * Sets an Array parameter.
    *
    * @param i the first parameter is 1, the second is 2, ...
    * @param x an object representing an SQL array
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setArray
    */
   public void setArray(int i, Array x) throws VirtuosoException
   {
      // Check parameters
      if(i < 1 || i > parameters.capacity())
         throw new VirtuosoException("Index " + i + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if(x == null) this.setNull(i, Types.ARRAY);
      else objparams.setElementAt(x,i - 1);
   }
#endif

   /**
    * Sets a BLOB parameter.
    *
    * @param i the first parameter is 1, the second is 2, ...
    * @param x an object representing a BLOB
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setBlob
    */
#if JDK_VER >= 12
   public void setBlob(int i, Blob x) throws VirtuosoException
#else
   public void setBlob(int i, VirtuosoBlob x) throws VirtuosoException
#endif
   {
      // Check parameters
      if(i < 1 || i > parameters.capacity())
         throw new VirtuosoException("Index " + i + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if(x == null) this.setNull(i, Types.BLOB);
      else objparams.setElementAt(x,i - 1);
   }

   /**
    * Sets the designated parameter to the given <code>Reader</code>
    * object, which is the given number of characters long.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the java reader which contains the UNICODE data
    * @param length the number of characters in the stream
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setCharacterStream
    */
   public void setCharacterStream(int parameterIndex, Reader x, int length) throws VirtuosoException
   {
      // Check parameters
      if(parameterIndex < 1 || parameterIndex > parameters.capacity())
         throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);

      if (parameters != null && parameters.elementAt(parameterIndex - 1) instanceof openlink.util.Vector)
	{
	  openlink.util.Vector pd = (openlink.util.Vector)parameters.elementAt(parameterIndex - 1);
	  int dtp = ((Number)pd.elementAt (0)).intValue();
	  if (dtp != VirtuosoTypes.DV_BLOB &&
	      dtp != VirtuosoTypes.DV_BLOB_BIN &&
	      dtp != VirtuosoTypes.DV_BLOB_WIDE)
	    {
	      try
		{
		  StringBuffer buf = new StringBuffer();
		  char chars[] = new char [4096];
		  int read;
		  int total_read = 0;
		  int to_read;
		  String ret;

		  do
		    {
		      to_read = (length - total_read) > chars.length ? chars.length : (length - total_read);
		      read = x.read (chars, 0, to_read);
		      if (read > 0)
			{
			  buf.append (chars, 0, read);
			  total_read += read;
			}
		    }
		  while (read > 0 && total_read < length);
		  ret = buf.toString();
		  //System.err.println ("setCharStream : len=" + ret.length() + " [" + ret + "]");
		  if (connection.charset != null)
		    {
		      objparams.setElementAt (
			  new VirtuosoExplicitString (connection.charsetBytes (ret),
			    VirtuosoTypes.DV_STRING),
			  parameterIndex - 1);
		      //System.err.println ("setting DV_LONG_STRING");
		    }
		  else
		    objparams.setElementAt (ret, parameterIndex - 1);
		  return;
		}
	      catch (java.io.IOException e)
		{
		  throw new VirtuosoException ("Error reading from a character stream " + e.getMessage(),
		      VirtuosoException.IOERROR);
		}
	    }

	  if (dtp == VirtuosoTypes.DV_BLOB_BIN)
	    throw new VirtuosoException ("Passing character stream to LONG VARBINARY columns not supported",
		"IM001", VirtuosoException.NOTIMPLEMENTED);
	}

      // After check, check if a Blob object is already associated or not
      //System.err.println ("after check");
      Object _obj = objparams.elementAt(parameterIndex - 1);
      // Check now if it's a Blob
      if(_obj instanceof VirtuosoBlob)
      {
         ((VirtuosoBlob)_obj).setReader(x,length);
         return;
      }
      // Else create a Clob
      if(x == null) this.setNull(parameterIndex, Types.BLOB);
      else objparams.setElementAt(new VirtuosoBlob(x,length,parameterIndex - 1),parameterIndex - 1);
   }

   /**
    * Sets a CLOB parameter.
    *
    * @param i the first parameter is 1, the second is 2, ...
    * @param x an object representing a CLOB
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setClob
    */
#if JDK_VER >= 12
   public void setClob(int i, Clob x) throws VirtuosoException
#else
   public void setClob(int i, VirtuosoClob x) throws VirtuosoException
#endif
   {
      // Check parameters
      if(i < 1 || i > parameters.capacity())
         throw new VirtuosoException("Index " + i + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if(x == null) this.setNull(i, Types.CLOB);
      else objparams.setElementAt(x,i - 1);
   }

   /**
    * Sets the designated parameter to SQL NULL.  This version of setNull should
    * be used for user-named types and REF type parameters.  Examples
    * of user-named types include: STRUCT, DISTINCT, JAVA_OBJECT, and
    * named array types.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param sqlType a value from java.sql.Types
    * @param typeName the fully-qualified name of an SQL user-named type,
    *  ignored if the parameter is not a user-named type or REF
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setNull
    */
   public void setNull(int paramIndex, int sqlType, String typeName) throws VirtuosoException
   {
      setNull(paramIndex,sqlType);
   }

#if JDK_VER >= 12
   /**
    * Sets a REF(&lt;structured-type&gt;) parameter.
    *
    * @param i the first parameter is 1, the second is 2, ...
    * @param x an object representing data of an SQL REF Type
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setRef
    */
   public void setRef(int i, Ref x) throws VirtuosoException
   {
      // Check parameters
      if(i < 1 || i > parameters.capacity())
         throw new VirtuosoException("Index " + i + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
      if(x == null) this.setNull(i, Types.REF);
      else objparams.setElementAt(x,i - 1);
   }
#endif

   /**
    * Sets the designated parameter to a java.sql.Date value,
    * using the given Calendar object.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @param cal the Calendar object the driver will use
    * to construct the date
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setDate
    */
   public void setDate(int parameterIndex, java.sql.Date x, Calendar cal) throws VirtuosoException
     {
       // Check parameters
       if(parameterIndex < 1 || parameterIndex > parameters.capacity())
	 throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
       if(x == null)
	 this.setNull(parameterIndex, Types.DATE);
       else
	 {
	   if(cal != null)
	     {
	       cal.setTime((java.util.Date)x);
	       x = new java.sql.Date (cal.getTime().getTime());
	     }
	   objparams.setElementAt(x,parameterIndex - 1);
	 }
     }

   /**
    * Sets the designated parameter to a java.sql.Time value,
    * using the given Calendar object.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @param cal the <code>Calendar</code> object the driver will use
    * to construct the time
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setTime
    */
   public void setTime(int parameterIndex, java.sql.Time x, Calendar cal) throws VirtuosoException
     {
       // Check parameters
       if(parameterIndex < 1 || parameterIndex > parameters.capacity())
	 throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
       if(x == null)
	 this.setNull(parameterIndex, Types.TIME);
       else
	 {
	   if(cal != null)
	     {
	       cal.setTime((java.util.Date)x);
	       x = new java.sql.Time (cal.getTime().getTime());
	     }
	   objparams.setElementAt(x,parameterIndex - 1);
	 }
     }

   /**
    * Sets the designated parameter to a java.sql.Timestamp value,
    * using the given Calendar object.
    *
    * @param parameterIndex the first parameter is 1, the second is 2, ...
    * @param x the parameter value
    * @param cal the <code>Calendar</code> object the driver will use
    * to construct the timestamp
    * @exception virtuoso.jdbc2.VirtuosoException if a database access error occurs
    * @see java.sql.PreparedStatement#setTimestamp
    */
   public void setTimestamp(int parameterIndex, java.sql.Timestamp x, Calendar cal) throws VirtuosoException
     {
       // Check parameters
       if(parameterIndex < 1 || parameterIndex > parameters.capacity())
	 throw new VirtuosoException("Index " + parameterIndex + " is not 1<n<" + parameters.capacity(),VirtuosoException.BADPARAM);
       if(x == null)
	 this.setNull(parameterIndex, Types.TIMESTAMP);
       else
	 {
	   if(cal != null)
	     {
	       int nanos = x.getNanos();
	       cal.setTime((java.util.Date)x);
	       x = new java.sql.Timestamp (cal.getTime().getTime());
               x.setNanos (nanos);
	     }
	   objparams.setElementAt(x,parameterIndex - 1);
	 }
     }
#if JDK_VER >= 14
   /* JDK 1.4 functions */

   public void setURL(int parameterIndex, java.net.URL x) throws SQLException
     {
       throw new VirtuosoException ("DATALINK not supported", VirtuosoException.NOTIMPLEMENTED);
     }

   public ParameterMetaData getParameterMetaData() throws SQLException
     {
       return paramsMetaData == null ? new VirtuosoParameterMetaData(null, connection) : paramsMetaData;
     }
#endif
}
