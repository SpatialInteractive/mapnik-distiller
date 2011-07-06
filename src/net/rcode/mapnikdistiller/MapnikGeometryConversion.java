package net.rcode.mapnikdistiller;

import mapnik.Box2d;
import mapnik.Coord;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.PrecisionModel;

public class MapnikGeometryConversion {
	private GeometryFactory factory;
	
	public MapnikGeometryConversion() {
		PrecisionModel precisionModel=new PrecisionModel(PrecisionModel.FLOATING);
		factory=new GeometryFactory(precisionModel);
	}
	
	public ImportGeometry convert(mapnik.Geometry mapnikGeometry) {
		ImportGeometry ret=new ImportGeometry();
		
		// Mapnik gives us the envelope so just use that
		Box2d envelope=mapnikGeometry.getEnvelope();
		ret.xmin=envelope.minx;
		ret.ymin=envelope.miny;
		ret.xmax=envelope.maxx;
		ret.ymax=envelope.maxy;
		
		switch (mapnikGeometry.getType()) {
		case mapnik.Geometry.TYPE_POINT:
			ret.geometry=convertPoint(mapnikGeometry);
			break;
		case mapnik.Geometry.TYPE_POLYGON:
			ret.geometry=convertPolygon(mapnikGeometry);
			break;
		case mapnik.Geometry.TYPE_LINESTRING:
			ret.geometry=convertLineString(mapnikGeometry);
			break;
			
		default:
			// TODO: Support the MULTI variants
			throw new IllegalArgumentException("Unsupported mapnik geometry type " + mapnikGeometry.getType());
		}
		
		return ret;
	}

	private Geometry convertPoint(mapnik.Geometry mapnikGeometry) {
		if (mapnikGeometry.getNumPoints()!=1) {
			throw new IllegalArgumentException("Mapnik POINT geometry does not have one point");
		}
		
		Coord mc=new Coord();
		mapnikGeometry.getVertex(0, mc);
		
		Coordinate coordinate=new Coordinate(mc.x, mc.y);
		return factory.createPoint(coordinate);
	}

	private Geometry convertPolygon(mapnik.Geometry mapnikGeometry) {
		int count=mapnikGeometry.getNumPoints();
		if (count<0) throw new IllegalArgumentException("Bad geometry.  Polygon needs more than one coordinate");
		Coord mc=new Coord();
		
		boolean needsClose;
		double closex, closey;
		if (count==1) needsClose=true;
		mapnikGeometry.getVertex(0, mc);
		closex=mc.x;
		closey=mc.y;
		mapnikGeometry.getVertex(count-1, mc);
		if (closex!=mc.x || closey!=mc.y) needsClose=true; 
		else needsClose=false;
		
		Coordinate coordinates[];
		if (needsClose) {
			coordinates=new Coordinate[count+1];
			coordinates[count]=new Coordinate(closex, closey);
		} else {
			coordinates=new Coordinate[count];
		}
		
		for (int i=0; i<count; i++) {
			mapnikGeometry.getVertex(i, mc);
			coordinates[i]=new Coordinate(mc.x, mc.y);
		}
		
		LinearRing shell=factory.createLinearRing(coordinates);
		return factory.createPolygon(shell, new LinearRing[0]);
	}

	private Geometry convertLineString(mapnik.Geometry mapnikGeometry) {
		int count=mapnikGeometry.getNumPoints();
		Coordinate coordinates[]=new Coordinate[count];
		Coord mc=new Coord();
		for (int i=0; i<count; i++) {
			mapnikGeometry.getVertex(i, mc);
			coordinates[i]=new Coordinate(mc.x, mc.y);
		}
		
		return factory.createLineString(coordinates);
	}
}
