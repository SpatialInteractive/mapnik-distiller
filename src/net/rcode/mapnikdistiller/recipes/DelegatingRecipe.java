package net.rcode.mapnikdistiller.recipes;

import net.rcode.mapnikdistiller.DistillerTable;
import net.rcode.mapnikdistiller.ImportGeometry;
import net.rcode.mapnikdistiller.MapSourceLayer;
import mapnik.FeatureSet;

/**
 * Delegates to a recipe in a chain
 * @author stella
 *
 */
public class DelegatingRecipe extends DefaultDistillerRecipe {
	protected final DistillerRecipe delegate;
	
	public DelegatingRecipe(DistillerRecipe delegate) {
		this.delegate=delegate;
	}

	@Override
	public void customizeGeometry(DistillerTable table, FeatureSet fs,
			ImportGeometry importGeometry) {
		delegate.customizeGeometry(table, fs, importGeometry);
	}

	@Override
	public LayerDisposition layerDisposition(MapSourceLayer layer) {
		return delegate.layerDisposition(layer);
	}

	@Override
	public String layerTableName(MapSourceLayer layer) {
		return delegate.layerTableName(layer);
	}

	@Override
	public boolean processFeature(FeatureSet fs) {
		return delegate.processFeature(fs);
	}

}
