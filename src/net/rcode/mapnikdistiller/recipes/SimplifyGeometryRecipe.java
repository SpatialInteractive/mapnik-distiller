package net.rcode.mapnikdistiller.recipes;

import mapnik.FeatureSet;
import net.rcode.mapnikdistiller.DistillerTable;
import net.rcode.mapnikdistiller.ImportGeometry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

public class SimplifyGeometryRecipe extends DelegatingRecipe {
	private Logger logger=LoggerFactory.getLogger(SimplifyGeometryRecipe.class);
	
	private double distanceTolerance;

	// stats
	private long count=0;
	private long original=0;
	private long revised=0;
	private long reductions=0;
	private long reportTime=0;
	
	public SimplifyGeometryRecipe(DistillerRecipe delegate, double distanceTolerance) {
		super(delegate);
		this.distanceTolerance=distanceTolerance;
	}

	public void setLoggerName(String name) {
		logger=LoggerFactory.getLogger(name);
	}
	
	@Override
	public void customizeGeometry(DistillerTable table, FeatureSet fs,
			ImportGeometry importGeometry) {
		if (importGeometry.geometry.getNumPoints()==1) return;
		
		Geometry simplified;
		try {
			simplified=DouglasPeuckerSimplifier.simplify(importGeometry.geometry, distanceTolerance);
			if (simplified.getNumPoints()>=importGeometry.geometry.getNumPoints()) {
				return;
			}
		} catch (OutOfMemoryError e) {
			System.gc();
			logger.warn("Out of memory simplifying geometry with " + importGeometry.geometry.getNumPoints() + " vertexes");
			return;
		}
		count++;
		
		int reduction=importGeometry.geometry.getNumPoints() - simplified.getNumPoints();
		revised+=simplified.getNumPoints();
		original+=importGeometry.geometry.getNumPoints();
		reductions+=reduction;
		
		if ((System.currentTimeMillis()-reportTime)>10000) {
			logger.debug("Simplification stats: Total geometries=" + count + ", Reductions=" + reductions + "/" + (reductions/(double)count) + ", Original=" + original + "/" + (original/(double)count) + 
					", Ratio=" + (((double)original)/revised));
			reportTime=System.currentTimeMillis();
		}
		
		importGeometry.geometry=simplified;
	}
}
