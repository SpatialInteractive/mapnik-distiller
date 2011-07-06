package net.rcode.mapnikdistiller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mapnik.Box2d;
import mapnik.Datasource;
import mapnik.FeatureSet;
import mapnik.Layer;
import mapnik.Projection;
import mapnik.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main controller for the distillation process
 * @author stella
 *
 */
public class DistillerReactor {
	private static final Logger logger=LoggerFactory.getLogger(DistillerReactor.class);
	
	private DistillerConfig config=new DistillerConfig();
	private List<DistillerJob> jobs=new ArrayList<DistillerJob>();
	private MapSource source;
	
	private List<LayerDispatch> dispatches;
	
	private static class LayerDispatch {
		public MapSourceLayer layer;
		public List<JobTarget> targets=new ArrayList<JobTarget>();
		
		public LayerDispatch(MapSourceLayer layer) {
			this.layer=layer;
		}
	}
	
	private static class JobTarget {
		public DistillerJob job;
		public String tableName;
		
		public JobTarget(DistillerJob job, String tableName) {
			this.job=job;
			this.tableName=tableName;
		}
		
		@Override
		public boolean equals(Object other) {
			JobTarget otherTarget=(JobTarget) other;
			return job==otherTarget.job && tableName.equals(otherTarget.tableName);
		}
		
		@Override
		public int hashCode() {
			return System.identityHashCode(job) ^ tableName.hashCode();
		}
	}
	
	public DistillerReactor(MapSource source) {
		this.source=source;
	}
	
	public List<DistillerJob> getJobs() {
		return jobs;
	}
	
	public DistillerConfig getConfig() {
		return config;
	}
	
	/**
	 * Analyzes all jobs and comes up with a plan
	 */
	public void analyze() {
		for (DistillerJob job: jobs) {
			logger.info("Analyzing job " + job.getName());
			job.analyze(source.copy(), this);
			
			logger.info("Finished analyzing job " + job.getName() + " which will produce " + job.getTables().size() + " tables");
		}
		
		// Compile the dispatches
		dispatches=new ArrayList<LayerDispatch>();
		Set<JobTarget> targets=new HashSet<JobTarget>();
		for (MapSourceLayer layer: source.getLayers()) {
			Set<String> layerAliases=extractLayerAliases(layer);
			
			LayerDispatch dispatch=new LayerDispatch(layer);
			
			// See if any tables that have not already been targeted depend on this
			for (DistillerJob job: jobs) {
				for (DistillerTable table: job.getTables().values()) {
					boolean referencesLayer=false;
					for (MapSourceLayer tableLayer: table.layers) {
						// Note: Cannot do reference equality because the job has cloned layers
						if (layerAliases.contains(tableLayer.getName())) {
							referencesLayer=true;
							break;
						}
					}
					
					if (referencesLayer) {
						JobTarget target=new JobTarget(job, table.tableName);
						// Skip table already targeted
						if (!targets.add(target)) continue;
						
						dispatch.targets.add(target);
					}
				}
			}
			
			if (!dispatch.targets.isEmpty()) dispatches.add(dispatch);
		}
		
		// Report
		StringBuilder report=new StringBuilder();
		report.append("Distiller will operate on " + dispatches.size() + " layers:\n");
		for (LayerDispatch dispatch: dispatches) {
			report.append("\tLayer " + dispatch.layer.getName() + " dispatches to " + dispatch.targets.size() + " jobs:\n");
			for (JobTarget target: dispatch.targets) {
				report.append("\t\t");
				report.append(target.job.getName() + "/" + target.tableName);
				report.append("\n");
			}
		}
		
		logger.info(report.toString());
	}

	public void runJobs() {
		for (int i=0; i<dispatches.size(); i++) {
			LayerDispatch dispatch=dispatches.get(i);
			
			// Conditions for doing quick debugs
			//if (!dispatch.layer.getName().equals("point-addressing")) continue;	// Debug
			
			String logPrefix="[Layer " + dispatch.layer.getName() + " (" + (i+1) + "/" + dispatches.size() + ")]: ";
			logger.info(logPrefix + "Beginning layer");
			
			// Indexed identically to dispatch.targets
			List<Object> jobStates=new ArrayList<Object>();
			Set<String> attributes=new HashSet<String>();
			boolean hasJobs=false;
			
			// Begin all tables
			for (JobTarget target: dispatch.targets) {
				logger.info(logPrefix + "Begin table " + target.job.getName() + "/" + target.tableName);
				Object jobState=target.job.beginTable(target.tableName, attributes);
				jobStates.add(jobState);
				if (jobState!=null) {
					hasJobs=true;
				}
			}
			
			if (!hasJobs) {
				logger.info(logPrefix + "Skipping layer because no jobs have tables to build");
				continue;
			}
			
			boolean success=false;
			try {
				// Calculate query extent in layer srs
				Layer layer=dispatch.layer.getNativeLayer();
				Projection projection=new Projection(layer.getSrs());
				Box2d extent=new Box2d(config.globalImportEnvelope);
				projection.forward(extent);
				
				// Start the query
				Datasource ds=layer.getDatasource();
				Query query=new Query(extent);
				for (String attrName: attributes) {
					query.addPropertyName(attrName);
				}
				FeatureSet fs;
				
				long startTime=System.currentTimeMillis();
				logger.info(logPrefix + "Querying datasource");
				fs=ds.features(query);
				logger.info(logPrefix + "Datasource query finished in " + ((System.currentTimeMillis()-startTime)/1000.0) + "s");

				long count=0;
				long lastReportTime=0;
				while (fs.next()) {
					for (int j=0; j<jobStates.size(); j++) {
						Object jobState=jobStates.get(j);
						JobTarget target=dispatch.targets.get(j);
						
						if (jobState!=null) {
							target.job.importFeature(jobState, fs);
						}
					}					
					
					count++;
					if ((System.currentTimeMillis()-lastReportTime)>10000) {
						logger.info(logPrefix + "Processed " + count + " features");
						lastReportTime=System.currentTimeMillis();
					}
				}
				
				success=true;
			} finally {
				// End all tables
				boolean endSuccess=true;
				for (int j=0; j<jobStates.size(); j++) {
					Object jobState=jobStates.get(j);
					JobTarget target=dispatch.targets.get(j);
					
					if (jobState!=null) {
						logger.info(logPrefix + "End table " + target.job.getName() + "/" + target.tableName);
						try {
							target.job.endTable(jobState, success && endSuccess);
						} catch (Exception e) {
							endSuccess=false;
						}
					}
				}
				
				if (!endSuccess) {
					throw new RuntimeException("One or more jobs failed to end");
				}
			}
		}
	}
	
	private Set<String> extractLayerAliases(MapSourceLayer layer) {
		String name=layer.getName();
		Set<String> ret=new HashSet<String>();
		ret.add(name);
		
		for (DistillerJob job: jobs) {
			for (DistillerTable table: job.getTables().values()) {
				Set<String> names=new HashSet<String>();
				for (MapSourceLayer tableLayer: table.layers) {
					names.add(tableLayer.getName());
				}
				
				if (names.contains(name)) {
					// They are all aliases for the same layer
					ret.addAll(names);
				}
			}
			
		}
		
		return ret;
	}
}
