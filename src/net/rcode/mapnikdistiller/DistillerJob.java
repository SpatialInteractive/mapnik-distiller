package net.rcode.mapnikdistiller;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mapnik.Box2d;
import mapnik.FeatureSet;
import mapnik.Projection;
import net.rcode.mapnikdistiller.recipes.DistillerRecipe;
import net.rcode.mapnikdistiller.recipes.LayerDisposition;
import net.rcode.mapnikdistiller.util.GeoClusterer;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

/**
 * Perform distillation
 * @author stella
 *
 */
public class DistillerJob {
	private Logger logger;
	
	//private MapSource source;
	private String name;
	private LocalStore store;
	private Map<String, DistillerTable> tables=new LinkedHashMap<String, DistillerTable>();
	private MapnikGeometryConversion geometryConversion=new MapnikGeometryConversion();
	
	private DistillerRecipe recipe;

	// -- Set in analyze
	private MapSource source;
	private DistillerReactor reactor;
	private DistillerConfig config;
	
	public DistillerJob(String name, LocalStore store, DistillerRecipe recipe) {
		this.name=name;
		this.store=store;
		this.recipe=recipe;
		
		logger=LoggerFactory.getLogger("job." + name);
	}
	
	public String getName() {
		return name;
	}
	
	public Map<String, DistillerTable> getTables() {
		return tables;
	}
	
	protected String getLayerDigest(MapSourceLayer layer) {
		String type=layer.getDatasourceParam("type");
		if ("postgis".equals(type)) {
			String table=layer.getDatasourceParam("table");
			if (table==null) return null;
			return "postgis:" +
				layer.getDatasourceParam("host") + ":" +
				layer.getDatasourceParam("dbname") + ":" +
				Utils.normalizeSqlDigest(table);
		} else if ("sqlite".equals(type)) {
			String file=layer.getDatasourceParam("file");
			String table=layer.getDatasourceParam("table");
			String keyField=layer.getDatasourceParam("key_field");
			if (file==null || table==null) return null;
			return "sqlite:" + file + ":" + table + ":" + keyField;
		} else if ("shape".equals(type)) {
			String file=layer.getDatasourceParam("file");
			if (file==null) return null;
			return "shape:" + file;
		}
		
		return null;
	}

	private static class ImportState {
		public long startTime=System.currentTimeMillis();
		public SQLiteStatement insertTable;
		public DistillerTable table;
		public long count;
	}
	
	/**
	 * Setup to populate a table.
	 * @param tableName
	 * @return state object if the table should be processed
	 */
	public Object beginTable(String tableName, Set<String> attributes) {
		DistillerTable table=tables.get(tableName);
		if (table==null) throw new IllegalArgumentException("No table to import: " + tableName);
		if (store.tableExists(table.tableName)) {
			logger.info("Not importing table " + tableName + " because it already exists");
			
			// Update meta-data even though we are not processing rows
			commitMapDefinition(table);
			saveMapFile();
			return null;
		}
		
		store.attachScratch();
		store.begin();
		
		// Setup
		createSqlTables(table);

		ImportState state=new ImportState();
		state.table=table;
		state.insertTable=store.getDb().prepare(table.sqlInsertImportTable);
	
		attributes.addAll(table.sqlInsertAttributes);
		return state;
	}
	
	/**
	 * Ends processing of a table begun with beginTable
	 * @param stateObject
	 * @param success
	 * @throws Exception 
	 */
	public void endTable(Object stateObject, boolean success) throws Exception {
		ImportState state=(ImportState) stateObject;
		
		state.insertTable.dispose();

		if (success) {
			logger.info("Imported " + state.count + " rows to " + state.table.sqlImportTableName + " in " + ((System.currentTimeMillis()-state.startTime)/1000.0) + "s");
			logger.info("Building base tables");
			try {
				populateBaseTables(state);
			} catch (Exception e) {
				logger.error("Error building base tables for " + state.table.tableName, e);
				store.commitOrRollback(false);
				throw e;
			}
		}
		
		store.commitOrRollback(success);
		store.detachScratch();
		
		if (success) {
			commitMapDefinition(state.table);
			saveMapFile();
		}
	}
	
	/**
	 * Read from the import table and write to the base and index tables
	 * @param state
	 */
	private void populateBaseTables(ImportState state) {
		SQLiteStatement insertIndex=store.getDb().prepare(state.table.sqlInsertIndex);
		SQLiteStatement insertTable=store.getDb().prepare(state.table.sqlInsertTable);
		SQLiteStatement selectStatement=store.getDb().prepare(state.table.sqlSelectImportTable);

		try {
			int count=0;
			long lastReportTime=0;
			logger.info("Executing sql: " + state.table.sqlSelectImportTable);
			
			while (selectStatement.step()) {
				// Result columns: 
				//   0..3: xmin,xmax,ymin,ymax
				//   4..6: the_geom,featureid,featureorder
				//   7..n: table.sqlInsertAttributes list
				
				// Insert the base record so we can get the id
				insertTable.reset();
				int param=1;
				insertTable.bind(param++, selectStatement.columnBlob(4));
				insertTable.bind(param++, selectStatement.columnLong(5));
				insertTable.bind(param++, selectStatement.columnLong(6));
				
				for (int i=0; i<state.table.sqlInsertAttributes.size(); i++) {
					Object value=selectStatement.columnValue(7+i);
					if (value==null) {
						insertTable.bindNull(param++);
					} else if (value instanceof String) {
						insertTable.bind(param++, (String)value);
					} else if (value instanceof Integer) {
						insertTable.bind(param++, ((Integer)value).intValue());
					} else if (value instanceof Long) {
						insertTable.bind(param++, ((Long)value).longValue());
					} else if (value instanceof Double) {
						insertTable.bind(param++, ((Double)value).doubleValue());
					} else if (value instanceof byte[]) {
						insertTable.bind(param++, (byte[])value);
					} else {
						throw new IllegalStateException("Unknown type from import table at index " + i + ":" + value.getClass().getName());
					}
				}
				
				insertTable.stepThrough();
				long rowId=store.getDb().getLastInsertId();
			
				// Now insert the index record
				insertIndex.reset();
				insertIndex.bind(1, rowId);
				insertIndex.bind(2, selectStatement.columnDouble(0));
				insertIndex.bind(3, selectStatement.columnDouble(1));
				insertIndex.bind(4, selectStatement.columnDouble(2));
				insertIndex.bind(5, selectStatement.columnDouble(3));
				insertIndex.stepThrough();
				
				// Report
				count++;
				if ((System.currentTimeMillis()-lastReportTime)>10000) {
					logger.info("Transferred " + count + " records to " + state.table.tableName + " and " + state.table.sqlIndexTableName);
					lastReportTime=System.currentTimeMillis();
				}
			}
			
		} finally {
			selectStatement.dispose();
			insertTable.dispose();
			insertIndex.dispose();
		}
	}

	/**
	 * Called repeatedly between beginTable and endTable to add a feature
	 * @param stateObject
	 * @param fs
	 */
	public void importFeature(Object stateObject, FeatureSet fs) {
		ImportState state=(ImportState) stateObject;
		
		if (!recipe.processFeature(fs)) {
			return;
		}

		state.insertTable.reset();
		
		// Convert geometry
		int geometryCount=fs.getGeometryCount();
		if (geometryCount!=1) {
			logger.warn("Unexpected geometry count=" + fs.getGeometryCount());
			if (geometryCount<1) {
				return;
			}
		}
		
		mapnik.Geometry mapnikGeometry=fs.getGeometry(0);
		ImportGeometry importGeometry;
		
		try {
			importGeometry=geometryConversion.convert(mapnikGeometry);
		} catch (Exception e) {
			logger.warn("Error importing geometry of type " + mapnikGeometry.getType() + " with " + mapnikGeometry.getNumPoints() + " points", e);
			return;
		}
		
		// Customize geometry
		recipe.customizeGeometry(state.table, fs, importGeometry);
		
		byte[] wkb=importGeometry.toWKB();
		
		int param=1;
		long clusterCode=GeoClusterer.DEFAULT.calculate(importGeometry.xmin, importGeometry.ymin);
		//logger.debug("Cluster code=" + clusterCode);
		
		state.insertTable.bind(param++, clusterCode);	// Geocluster
		state.insertTable.bind(param++, importGeometry.xmin);
		state.insertTable.bind(param++, importGeometry.xmax);
		state.insertTable.bind(param++, importGeometry.ymin);
		state.insertTable.bind(param++, importGeometry.ymax);

		state.insertTable.bind(param++, wkb);	// Geom
		state.insertTable.bind(param++, fs.getId());	// Feature id
		
		// Insert into base table
		// Now bind attributes
		//String[] allNames=fs.getPropertyNames().toArray(new String[0]);
		for (String attrName: state.table.sqlInsertAttributes) {
			Object value=fs.getProperty(attrName);
			if (value==null) {
				state.insertTable.bindNull(param++);
			} else {
				if (value instanceof String) {
					state.insertTable.bind(param++, ((String)value));
				} else if (value instanceof Integer) {
					state.insertTable.bind(param++, ((Integer)value).intValue());
				} else if (value instanceof Long) {
					state.insertTable.bind(param++, ((Long)value).longValue());
				} else if (value instanceof Number) {
					state.insertTable.bind(param++, ((Number)value).doubleValue());
				} else if (value instanceof Boolean) {
					if (((Boolean)value).booleanValue()) {
						state.insertTable.bind(param++, 1);
					} else {
						state.insertTable.bind(param++, 0);
					}
				} else {
					state.insertTable.bind(param++, value.toString());
				}
			}
		}
		
		// And execute
		state.insertTable.stepThrough();
		
		// Insert into index
		/*
		long rowId=store.getDb().getLastInsertId();
		state.insertIndex.reset();
		state.insertIndex.bind(1, rowId);
		state.insertIndex.bind(2, importGeometry.xmin);
		state.insertIndex.bind(3, importGeometry.xmax);
		state.insertIndex.bind(4, importGeometry.ymin);
		state.insertIndex.bind(5, importGeometry.ymax);
		state.insertIndex.stepThrough();
		*/
		
		// Expand the extent
		state.table.updateExtent(importGeometry.xmin, importGeometry.ymin, importGeometry.xmax, importGeometry.ymax);
		state.count++;
	}


	private void commitMapDefinition(DistillerTable table) {
		for (MapSourceLayer layer: table.layers) {
			Element layerElt=layer.getElement();
			Element dsElt=layerElt.element("Datasource");
			if (dsElt!=null) layerElt.remove(dsElt);
			
			dsElt=DocumentHelper.createElement("Datasource");
			layerElt.add(dsElt);
			
			dsElt.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "type")
					.addText("sqlite"));

			dsElt.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "file")
					.addText(store.getDbFile().getName()));

			dsElt.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "key_field")
					.addText("rowid"));

			dsElt.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "table")
					.addText(table.tableName));

			dsElt.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "use_spatial_index")
					.addText("true"));
			
			dsElt.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "attachdb")
					.addText("indexes@" + store.getIndexFile().getName()));

			// Set the extent.  TODO: This is dumb.  We should get the extent from the
			// data but may not have it if we haven't processed yet
			String srs=table.getPrototypeLayer().getSrs();
			Projection projection=new Projection(srs);
			Box2d extent=new Box2d(config.globalImportEnvelope.minx, 
					config.globalImportEnvelope.miny,
					config.globalImportEnvelope.maxx,
					config.globalImportEnvelope.maxy);
			projection.forward(extent);
			DecimalFormat fmt=new DecimalFormat("0.0");
			
			dsElt.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "extent")
					.addText(fmt.format(extent.minx) + "," + fmt.format(extent.miny) + "," + fmt.format(extent.maxx) + "," + fmt.format(extent.maxy)));
		}
	}

	private void createSqlTables(DistillerTable table) {
		store.getDb().exec("drop table if exists " + table.tableName);
		store.getDb().exec("drop table if exists " + table.sqlIndexTableName);
		store.getDb().exec("drop table if exists " + table.sqlImportTableName);
		
		try {
			logger.info("Creating table: " + table.sqlCreateImportTable);
			store.getDb().exec(table.sqlCreateImportTable);
		} catch (SQLiteException e) {
			logger.warn("Error executing create table statement " + table.sqlCreateImportTable, e);
			throw e;
		}
		try {
			logger.info("Creating table: " + table.sqlCreateTable);
			store.getDb().exec(table.sqlCreateTable);
		} catch (SQLiteException e) {
			logger.warn("Error executing create table statement " + table.sqlCreateTable, e);
			throw e;
		}
		try {
			logger.info("Creating index: " + table.sqlCreateIndex);
			store.getDb().exec(table.sqlCreateIndex);
		} catch (SQLiteException e) {
			logger.warn("Error executing create index statement " + table.sqlCreateIndex, e);
			throw e;
		}
	}

	/**
	 * Anaylyze the MapSource and build necessary relationships
	 */
	public void analyze(MapSource source, DistillerReactor reactor) {
		this.source=source;
		this.reactor=reactor;
		this.config=reactor.getConfig();
		boolean success=false;
		store.begin();
		try {
			List<MapSourceLayer> layers=source.getLayers();
			for (MapSourceLayer layer: layers) {
				LayerDisposition disposition=recipe.layerDisposition(layer);
				if (disposition==LayerDisposition.IGNORE) {
					logger.info("Ignoring layer " + layer.getName());
					continue;
				}
				else if (disposition==LayerDisposition.DROP) {
					logger.info("Dropping layer " + layer.getName());
					layer.drop();
					continue;
				}
				
				String name=layer.getName();
				String digest=getLayerDigest(layer);
				if (digest==null) continue;
				
				String tableName=recipe.layerTableName(layer);
				if (tableName==null) tableName=store.mapLayerToTable(name, digest);
				logger.debug("Mapped layer " + name + " -> table " + tableName);
				
				DistillerTable table=tables.get(tableName);
				if (table==null) {
					table=new DistillerTable(tableName);
					tables.put(tableName, table);
				}
				table.layers.add(layer);
				table.attributes.addAll(layer.discoverAttributes());
			}
			success=true;
		} finally {
			store.commitOrRollback(success);
		}
		
		// Now go back through and build sql
		for (DistillerTable table: tables.values()) {
			table.buildSql();
			logger.debug("Table definition for job " + name + ": " + table);
		}
	}
	
	public void saveMapFile() {
		try {
			String mapContent=source.saveToString();
			Writer out=new OutputStreamWriter(new FileOutputStream(store.getMapFile()), "UTF-8");
			out.write(mapContent);
			out.flush();
			out.close();
		} catch (Exception e) {
			throw new RuntimeException("error saving map file " + store.getMapFile(), e);
		}
	}
}
