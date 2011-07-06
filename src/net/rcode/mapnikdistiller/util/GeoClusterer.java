package net.rcode.mapnikdistiller.util;

import mapnik.Box2d;

/**
 * Produces hierarchical code for a given set of coordinates such that coordinates
 * that are physically near each other will tend to have codes that are close when
 * compared as standard 2's complement binary numbers.
 * <p>
 * The precision of the cluster code can be controlled by how many levels of hierarchy
 * to descend.
 * <p>
 * While descending each level, two bits are shifted onto the code.  The meaning of
 * the bits are:
 * <ul>
 * <li>00 = upper right
 * <li>01 = upper left
 * <li>10 = lower left
 * <li>11 = lower right
 * </ul>
 * 
 * @author stella
 *
 */
public class GeoClusterer {
	/**
	 * On the order of 1km resolution for something web mercator'y
	 */
	public static final GeoClusterer DEFAULT=new GeoClusterer(
			new Box2d(-20000000, -20000000, 20000000, 20000000), 15);
	
	private Box2d extent;
	private int levels;
	
	public GeoClusterer(Box2d extent, int levels) {
		this.extent=extent;
		this.levels=levels;
	}
	
	public long calculate(double x, double y) {
		long code=0;
		Box2d bounds=new Box2d(extent);
		for (int i=0; i<levels; i++) {
			double midx=(bounds.minx+bounds.maxx) / 2;
			double midy=(bounds.miny+bounds.maxy) / 2;
			long value;
			if (x>midx) {
				bounds.minx=midx;
				if (y>midy) {
					value=0;
					bounds.miny=midy;
				} else {
					value=3;
					bounds.maxy=midy;
				}
			} else {
				bounds.maxx=midx;
				if (y>midy) {
					value=1;
					bounds.miny=midy;
				} else {
					value=2;
					bounds.maxy=midy;
				}
			}
			
			code=(code<<2) | value;
		}
		
		return code;
	}
}
