package net.rcode.mapnikdistiller;

import mapnik.Box2d;

/**
 * Configuration settings for a distiller job
 * @author stella
 *
 */
public class DistillerConfig {
	public Box2d globalImportEnvelope=new Box2d(-180, -85, 180, 85);
	
	// Western Washington
	//public Box2d globalImportEnvelope=new Box2d(-125.346973, 46.03879, -119.853809, 49.163622);
}
