/**
 * 
 */
package net.rcode.mapnikdistiller.recipes;

public enum LayerDisposition {
	/**
	 * Distill the layer
	 */
	PROCESS,
	
	/**
	 * Delete the layer from the resultant map
	 */
	DROP,
	
	/**
	 * Ignore the layer but leave it in the map
	 */
	IGNORE;
}