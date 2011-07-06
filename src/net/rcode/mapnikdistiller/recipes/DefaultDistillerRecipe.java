package net.rcode.mapnikdistiller.recipes;

import net.rcode.mapnikdistiller.DistillerTable;
import net.rcode.mapnikdistiller.ImportGeometry;
import net.rcode.mapnikdistiller.MapSourceLayer;
import net.rcode.mapnikdistiller.RenderConditions;
import mapnik.FeatureSet;

/**
 * Customizable logic for the distillation process
 * @author stella
 *
 */
public class DefaultDistillerRecipe implements DistillerRecipe {
	private RenderConditions conditions;
	
	public DefaultDistillerRecipe(RenderConditions conditions) {
		this.conditions=conditions;
	}
	
	public DefaultDistillerRecipe() {
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mapnikdistiller.recipes.IDistillerRecipe#layerDisposition(net.rcode.mapnikdistiller.MapSourceLayer)
	 */
	public LayerDisposition layerDisposition(MapSourceLayer layer) {
		if (conditions!=null) {
			if (!layer.doesRender(conditions)) {
				return LayerDisposition.DROP;
			}
		}
		
		String datasourceType=layer.getDatasourceParam("type");
		if ("postgis".equals(datasourceType) || "sqlite".equals(datasourceType) || "shape".equals(datasourceType)) {
			return LayerDisposition.PROCESS;
		} else {
			return LayerDisposition.IGNORE;
		}
	}

	/* (non-Javadoc)
	 * @see net.rcode.mapnikdistiller.recipes.IDistillerRecipe#layerTableName(net.rcode.mapnikdistiller.MapSourceLayer)
	 */
	public String layerTableName(MapSourceLayer layer) {
		return null;
	}

	/* (non-Javadoc)
	 * @see net.rcode.mapnikdistiller.recipes.IDistillerRecipe#processFeature(mapnik.FeatureSet)
	 */
	public boolean processFeature(FeatureSet fs) {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see net.rcode.mapnikdistiller.recipes.IDistillerRecipe#customizeGeometry(net.rcode.mapnikdistiller.DistillerTable, mapnik.FeatureSet, net.rcode.mapnikdistiller.ImportGeometry)
	 */
	public void customizeGeometry(DistillerTable table, FeatureSet fs,
			ImportGeometry importGeometry) {
	}

	
}
