package net.rcode.mapnikdistiller;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mapnik.AttributeDescriptor;
import mapnik.Box2d;
import mapnik.Datasource;
import mapnik.Layer;
import mapnik.LayerDescriptor;

public class DistillerTable {
	public String tableName;
	public List<MapSourceLayer> layers=new ArrayList<MapSourceLayer>();
	public Set<String> attributes=new HashSet<String>();
	
	public String sqlCreateTable;
	public String sqlImportTableName;
	public String sqlCreateImportTable;
	public String sqlIndexTableName;
	public String sqlCreateIndex;
	public String sqlInsertTable;
	public String sqlInsertImportTable;
	public String sqlSelectImportTable;
	public String sqlInsertIndex;
	public List<String> sqlInsertAttributes;
	
	public Box2d extent=new Box2d(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
	
	public DistillerTable(String tableName) {
		this.tableName=tableName;
	}
	
	public void buildSql() {
		if (layers.isEmpty()) return;
		
		// Access the datasource
		Datasource ds = getPrototypeDatasource();
		
		LayerDescriptor desc=ds.getDescriptor();
		LinkedHashMap<String, String> columns=new LinkedHashMap<String, String>();
		String primaryKeyColumn=null;
		
		for (AttributeDescriptor attr: desc.getDescriptors()) {
			String name=attr.getName();
			if (!attributes.contains(name)) continue;	// The column is not referenced by the map definition
			
			String type;
			switch (attr.getType()) {
			case AttributeDescriptor.TYPE_STRING:
				type="text";
				break;
			case AttributeDescriptor.TYPE_OBJECT:
				type="blob";
				break;
			case AttributeDescriptor.TYPE_INTEGER:
				type="integer";
				break;
			case AttributeDescriptor.TYPE_FLOAT:
			case AttributeDescriptor.TYPE_DOUBLE:
				type="real";
				break;
			case AttributeDescriptor.TYPE_GEOMETRY:
				// These don't come through from postgis, but ignore
				// them for good measure
				continue;
			default:
				type="text";	
			}
			
			if (attr.isPrimaryKey()) primaryKeyColumn=name;
			columns.put(name, type);
		}
		
		// Formulate the scratch table create statement
		sqlImportTableName="scratch.import_" + tableName;
		StringBuilder buffer=new StringBuilder();
		buffer.append("create table ").append(sqlImportTableName).append(" (");
		buffer.append("geocluster integer,xmin integer,xmax integer,ymin integer,ymax integer,the_geom blob,featureid integer");
		for (Map.Entry<String,String> entry: columns.entrySet()) {
			buffer.append(',');
			buffer.append("\"").append(entry.getKey()).append("\" ").append(entry.getValue());
		}
		buffer.append(")");
		sqlCreateImportTable=buffer.toString();
		buffer.setLength(0);
		
		// Formulate the base table create statement
		buffer.append("create table ").append(tableName).append(" (");
		buffer.append("the_geom blob,featureid integer,featureorder integer");
		for (Map.Entry<String,String> entry: columns.entrySet()) {
			buffer.append(',');
			buffer.append("\"").append(entry.getKey()).append("\" ").append(entry.getValue());
		}
		buffer.append(")");
		sqlCreateTable=buffer.toString();
		buffer.setLength(0);

		// Formulate the index create statement
		sqlIndexTableName="indexes.idx_" + tableName + "_the_geom";
		buffer.append("create virtual table ").append(sqlIndexTableName);
		buffer.append(" using rtree (pkid,xmin,xmax,ymin,ymax)");
		sqlCreateIndex=buffer.toString();
		buffer.setLength(0);
		
		// Scratch table insert statement
		sqlInsertAttributes=new ArrayList<String>();
		buffer.append("insert into ").append(sqlImportTableName).append(" (geocluster,xmin,xmax,ymin,ymax,the_geom,featureid");
		for (String name: columns.keySet()) {
			buffer.append(",\"");
			buffer.append(name);
			buffer.append("\"");
		}
		
		buffer.append(") values (?,?,?,?,?,?,?");
		for (String name: columns.keySet()) {
			buffer.append(",?");
			sqlInsertAttributes.add(name);
		}
		buffer.append(")");
		sqlInsertImportTable=buffer.toString();
		buffer.setLength(0);

		// Scratch table select statement
		buffer.append("select xmin,xmax,ymin,ymax,the_geom,featureid,rowid");
		for (String attr: sqlInsertAttributes) {
			buffer.append(",\"");
			buffer.append(attr);
			buffer.append("\"");
		}
		buffer.append(" from ").append(sqlImportTableName).append(" order by geocluster");
		sqlSelectImportTable=buffer.toString();
		buffer.setLength(0);
		
		// Base table insert statement
		buffer.append("insert into ").append(tableName).append(" (the_geom,featureid,featureorder");
		for (String name: columns.keySet()) {
			buffer.append(",\"");
			buffer.append(name);
			buffer.append("\"");
		}
		
		buffer.append(") values (?,?,?");
		for (String name: columns.keySet()) {
			buffer.append(",?");
		}
		buffer.append(")");
		sqlInsertTable=buffer.toString();
		buffer.setLength(0);

		// Index insert
		buffer.append("insert into ").append(sqlIndexTableName).append(" (pkid,xmin,xmax,ymin,ymax) values (?,?,?,?,?)");
		sqlInsertIndex=buffer.toString();
		buffer.setLength(0);
	}

	public Layer getPrototypeLayer() {
		MapSourceLayer protoLayer=layers.get(0);
		Layer nativeLayer=protoLayer.getNativeLayer();
		return nativeLayer;
	}
	
	public Datasource getPrototypeDatasource() {
		Datasource ds=getPrototypeLayer().getDatasource();
		ds.bind();
		return ds;
	}

	public void updateExtent(double minx, double miny, double maxx, double maxy) {
		if (Double.isNaN(extent.minx) || extent.minx>minx) extent.minx=minx;
		if (Double.isNaN(extent.miny) || extent.miny>miny) extent.miny=miny;
		if (Double.isNaN(extent.maxx) || extent.maxx<maxx) extent.maxx=maxx;
		if (Double.isNaN(extent.maxy) || extent.maxy<maxy) extent.maxy=maxy;
	}

	public String getExtentSpec() {
		DecimalFormat fmt=new DecimalFormat("0.0");
		
		if (!Double.isNaN(extent.minx) && !Double.isNaN(extent.miny) && !Double.isNaN(extent.maxx) && !Double.isNaN(extent.maxy)) {
			return fmt.format(extent.minx) + 
				"," + 
				fmt.format(extent.miny) + 
				"," + 
				fmt.format(extent.maxx) + 
				"," + 
				fmt.format(extent.maxy);
		} else {
			return null;
		}
	}

	@Override
	public String toString() {
		StringBuilder ret=new StringBuilder();
		ret.append("DistillerTable(").append(tableName);
		ret.append("(");
		boolean first=true;
		for (MapSourceLayer layer: layers) {
			if (first) first=false;
			else ret.append(',');
			
			ret.append(layer.getName());
		}
		ret.append("):");
		
		first=true;
		for (String attr: attributes) {
			if (first) first=false;
			else ret.append(',');
			
			ret.append(attr);
		}
		
		ret.append(")");
		if (sqlCreateTable!=null) {
			ret.append("\n\tCreate sql:");
			ret.append(sqlCreateTable);
		}
		if (sqlInsertTable!=null) {
			ret.append("\n\tInsert sql:");
			ret.append(sqlInsertTable);
		}
		if (sqlInsertAttributes!=null) {
			ret.append("\n\tInsert attributes:");
			for (String attr: sqlInsertAttributes) {
				ret.append(' ').append(attr);
			}
		}
		if (sqlCreateImportTable!=null) {
			ret.append("\n\tCreate import table sql:");
			ret.append(sqlCreateImportTable);
		}
		if (sqlInsertImportTable!=null) {
			ret.append("\n\tInsert import table sql:");
			ret.append(sqlInsertImportTable);
		}
		if (sqlSelectImportTable!=null) {
			ret.append("\n\tSelect import table sql:");
			ret.append(sqlSelectImportTable);
		}
		
		return ret.toString();
	}

}
