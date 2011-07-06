package net.rcode.mapnikdistiller;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ByteOrderValues;
import com.vividsolutions.jts.io.WKBWriter;


/**
 * Geometry to be imported into a distiller table
 * @author stella
 *
 */
public class ImportGeometry {
	public Geometry geometry;
	public double xmin,ymin,xmax,ymax;
	
	public byte[] toWKB() {
		WKBWriter w=new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN);
		return w.write(geometry);
	}
}
