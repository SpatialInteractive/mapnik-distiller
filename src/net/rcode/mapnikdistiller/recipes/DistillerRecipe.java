package net.rcode.mapnikdistiller.recipes;

import mapnik.FeatureSet;
import net.rcode.mapnikdistiller.DistillerTable;
import net.rcode.mapnikdistiller.ImportGeometry;
import net.rcode.mapnikdistiller.MapSourceLayer;

public interface DistillerRecipe {

	/**
	 * Determine how the given layer should be processed.  Default implementation
	 * return PROCESS for known database vector types and IGNORE for others.
	 * @param layer
	 * @return processing disposition
	 */
	public abstract LayerDisposition layerDisposition(MapSourceLayer layer);

	/**
	 * Allows assignment of a custom table name for a layer.  Default returns null
	 * @param layer
	 * @return table name or null to use default logic
	 */
	public abstract String layerTableName(MapSourceLayer layer);

	/**
	 * Called for each feature.  Default returns true.
	 * @param fs
	 * @return true to process the feature, false to ignore
	 */
	public abstract boolean processFeature(FeatureSet fs);

	/**
	 * Called for each feature with a geometry.  The geometry can be customized.
	 * Default implementation does nothing.
	 * @param table table being built
	 * @param fs feature the geometry was pulled from for reference
	 * @param importGeometry geometry
	 */
	public abstract void customizeGeometry(DistillerTable table, FeatureSet fs,
			ImportGeometry importGeometry);

}