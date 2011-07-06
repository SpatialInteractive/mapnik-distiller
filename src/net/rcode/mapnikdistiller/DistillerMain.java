package net.rcode.mapnikdistiller;

import java.io.File;

import net.rcode.mapnikdistiller.recipes.DefaultDistillerRecipe;
import net.rcode.mapnikdistiller.recipes.SimplifyGeometryRecipe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistillerMain {
	private static final Logger logger=LoggerFactory.getLogger(DistillerMain.class);
	
	public static void main(String[] args) throws Exception {
		Globals.initialize();
		
		File mapFile=new File(args[0]);
		File location=new File(args[1]);
		location.mkdirs();
		System.out.println("Distilling " + mapFile + " to " + location);

		MapSource source=new MapSource(mapFile);
		DistillerReactor reactor=new DistillerReactor(source);
		
		addJob(reactor, location, "mqstreet_dl1", 0, 400000, Double.NaN);
		addJob(reactor, location, "mqstreet_dl2", 0, 400000, 15);
		addJob(reactor, location, "mqstreet_dl3", 400000, Double.POSITIVE_INFINITY, 150);
		addJob(reactor, location, "mqstreet_dl4", 6500000, Double.POSITIVE_INFINITY, 2400);
		
		reactor.analyze();
		reactor.runJobs();
		logger.info("Done.");
	}
	
	private static void addJob(DistillerReactor reactor, File location, String name, double minScale, double maxScale, double tolerance) {
		RenderConditions conditions=null;
		if (!Double.isNaN(minScale) || !Double.isNaN(maxScale)) {
			conditions=new RenderConditions();
			conditions.setMinScaleDenominator(minScale);
			conditions.setMaxScaleDenominator(maxScale);
		}
		DefaultDistillerRecipe recipe=new DefaultDistillerRecipe(conditions);
		if (!Double.isNaN(tolerance)) {
			SimplifyGeometryRecipe simplifier=new SimplifyGeometryRecipe(recipe, tolerance);
			simplifier.setLoggerName("simplifier." + name);
			recipe=simplifier;
		}
		
		LocalStore store=new LocalStore(location, name);
		DistillerJob job=new DistillerJob(name, store, recipe);
		
		reactor.getJobs().add(job);
	}
}
