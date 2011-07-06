package net.rcode.mapnikdistiller;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteConstants;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

/**
 * Local spatialite datasource that is the distillation target
 * @author stella
 *
 */
public class LocalStore {
	private static final Logger logger=LoggerFactory.getLogger(LocalStore.class);
	
	private File location;
	private String prefix;
	
	private File dbFile;
	private File indexFile;
	private File scratchFile;
	private SQLiteConnection db;
	private File mapFile;
	
	private boolean scratchAttached=false;
	
	public LocalStore(File location, String prefix) throws SQLiteException {
		this.location=location;
		this.prefix=prefix;
		
		// Setup sqlite
		this.dbFile=new File(location, prefix + ".sqlite");
		this.indexFile=new File(location, prefix + ".index.sqlite");
		this.scratchFile=new File(location, prefix + ".scratch.sqlite");
		this.db=new SQLiteConnection(dbFile);
		this.db.open(true);
		Globals.configureSQLite(db);
		initDb();
		
		this.mapFile=new File(location, prefix + ".mapnik.xml");
	}

	private void initDb() throws SQLiteException {
		// Attach indexes database
		db.exec("attach database '" + indexFile.toString() + "' as indexes");
		
		if (!tableExists("layertable")) {
			db.exec("create table layertable (tablename text not null primary key, tabledesc text not null unique)");
		}
		if (!tableExists("layertablemap")) {
			db.exec("create table layertablemap (layername text not null primary key on conflict replace, tablename text not null)");
		}
	}
	
	public void attachScratch() {
		if (scratchAttached) {
			detachScratch();
		}
		
		logger.info("Attaching scratch database " + scratchFile);
		db.exec("attach database '" + scratchFile.toString() + "' as scratch");
		scratchAttached=true;
	}
	
	public void detachScratch() {
		if (scratchAttached) {
			db.exec("detach database scratch");
			scratchAttached=false;
		}
		if (scratchFile.exists()) {
			if (!scratchFile.delete()) {
				throw new IllegalStateException("Scratch file " + scratchFile + " exists but cannot be deleted");
			}
		}
	}
	
	public boolean tableExists(String tableName) throws SQLiteException {
		SQLiteStatement stmt=db.prepare("select name from sqlite_master where name=? and type='table'");
		try {
			stmt.bind(1, tableName);
			if (stmt.step()) return true;
			else return false;
		} finally {
			stmt.dispose();
		}
	}
	
	public File getMapFile() {
		return mapFile;
	}
	
	public File getLocation() {
		return location;
	}
	
	public String getPrefix() {
		return prefix;
	}
	
	public SQLiteConnection getDb() {
		return db;
	}
	
	public File getDbFile() {
		return dbFile;
	}

	public File getIndexFile() {
		return indexFile;
	}
	
	public String mapLayerToTable(String name, String digest) throws SQLiteException {
		// See if we already have one
		SQLiteStatement checkExisting=db.prepare("select m.tablename from layertablemap m inner join layertable t on m.tablename=t.tablename where m.layername=? and t.tabledesc=?");
		try {
			checkExisting.bind(1, name);
			checkExisting.bind(2, digest);
			if (checkExisting.step()) {
				// Match
				return checkExisting.columnString(0);
			}
			
		} finally {
			checkExisting.dispose();
		}
		
		// No?  Allocate
		int index=0;
		String tableName;
		for (;;) {
			SQLiteStatement descQuery=db.prepare("select tablename from layertable where tabledesc=?");
			try {
				descQuery.bind(1, digest);
				if (descQuery.step()) {
					// Have an existing identical table for another layer
					tableName=descQuery.columnString(0);
					break;
				}
			} finally {
				descQuery.dispose();
			}
			
			// Try next
			SQLiteStatement insert=db.prepare("insert into layertable (tablename,tabledesc) values (?,?)");
			try {
				tableName=Utils.legalizeSqlName(name);
				if (index>0) {
					tableName=tableName + "_" + index;
				}
				
				insert.bind(1, tableName);
				insert.bind(2, digest);
				
				insert.stepThrough();
				break;
			} catch (SQLiteException e) {
				if (e.getErrorCode()==SQLiteConstants.SQLITE_CONSTRAINT) {
					index++;
					continue;
				}
			} finally {
				insert.dispose();
			}
		}
		
		return tableName;
	}
	
	public void begin() {
		db.exec("begin");
	}
	
	public void commit() {
		db.exec("commit");
	}
	
	public void rollback() {
		db.exec("rollback");
	}

	public void commitOrRollback(boolean success) {
		if (success) commit();
		else rollback();
	}
}
