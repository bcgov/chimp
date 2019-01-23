package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.measure.Unit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.voronoi.GeoPackagePersistable;
import ca.bc.gov.catchment.voronoi.KeptAndDiscarded;
import ca.bc.gov.catchment.voronoi.Persistable;
import ca.bc.gov.catchment.voronoi.VoronoiLongLineCleaner;
import ca.bc.gov.catchment.voronoi.VoronoiTouchingWaterCleaner;
import ca.bc.gov.catchment.voronoi.VoronoiDanglerCleaner;
import ca.bc.gov.catchments.utils.FilterUtils;
import ca.bc.gov.catchments.utils.SaveUtils;

public class CleanVoronoiOutput {

	private static final String DEFAULT_VORONOI_EDGES_FEATURE_TYPE = "VORONOI_EDGES";
	private static final String GEOPKG_ID = "geopkg";
	
	
	public static void main(String[] args) {

		//command line options
		//---------------------------------------------------------------------
		
		Options options = new Options();
		options.addOption("voronoiEdgesFile", true, "Input GeoPackage file with voronoi edges");
		options.addOption("waterFeaturesFile", true, "Input GeoPackage file with water feature data");
		options.addOption("outFile", true, "Output GeoPackage file");
		options.addOption("voronoiEdgesTable", true, "Name of table containing voronoi edges");
		options.addOption("waterFeaturesTable", true, "Name of table containing water features");
		options.addOption("outKeptTable", true, "Name of output table containing kept voronoi edges");
		options.addOption("outDiscardedTable", true, "Name of output table containing discarded voronoi edges");
		options.addOption("startPhase", true, "Phase number to start on");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String voronoiEdgesFilename = null;
		String waterFeaturesFilename = null;
		String outputFilename = null;
		String voronoiEdgesTableName = null;
		String waterFeaturesTable = null;
		String outKeptTableName = null;
		String outDiscardedTableName = null;
		int startPhase = 1;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			voronoiEdgesFilename = cmd.getOptionValue("voronoiEdgesFile");
			waterFeaturesFilename = cmd.getOptionValue("waterFeaturesFile");
			outputFilename = cmd.getOptionValue("outFile");
			voronoiEdgesTableName = cmd.getOptionValue("voronoiEdgesTable", DEFAULT_VORONOI_EDGES_FEATURE_TYPE);
			waterFeaturesTable = cmd.getOptionValue("waterFeaturesTable");
			outKeptTableName = cmd.getOptionValue("outKeptTable", voronoiEdgesTableName+"_kept");
			outDiscardedTableName = cmd.getOptionValue("outDiscardedTable", voronoiEdgesTableName+"_discarded");
			startPhase = Integer.parseInt(cmd.getOptionValue("startPhase", "1"));
		} catch (ParseException e) {
			e.printStackTrace();
			formatter.printHelp( CleanVoronoiOutput.class.getSimpleName(), options );
			System.exit(1);
		}

		//validate inputs
		if (voronoiEdgesFilename == null) {
			formatter.printHelp( CleanVoronoiOutput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (waterFeaturesFilename == null) {
			formatter.printHelp( CleanVoronoiOutput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (outputFilename == null) {
			formatter.printHelp( CleanVoronoiOutput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (waterFeaturesTable == null) {
			formatter.printHelp( CleanVoronoiOutput.class.getSimpleName(), options );
			System.exit(1);
		}
		
		System.out.println("App: "+CleanVoronoiOutput.class.getSimpleName());
		System.out.println("Inputs:");
		System.out.println("- voronoiEdgesFile: "+voronoiEdgesFilename);
		System.out.println("   - voronoiEdgesTableName: " +voronoiEdgesTableName);
		System.out.println("- waterFeaturesFile: "+waterFeaturesFilename);
		System.out.println("   - waterFeaturesTable: " +waterFeaturesTable);
		System.out.println("- outFile: "+outputFilename);
		System.out.println("Connecting to input data...");

		//open input files
		//---------------------------------------------------------------------
			
		//voronoi edges
		
		Map<String, String> voronoiEdgesDatastoreParams = new HashMap<String, String>();
		voronoiEdgesDatastoreParams.put("dbtype", GEOPKG_ID);
		voronoiEdgesDatastoreParams.put("database", voronoiEdgesFilename);
		
		DataStore voronoiEdgesDatastore = null;
		try {
			voronoiEdgesDatastore = DataStoreFinder.getDataStore(voronoiEdgesDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+voronoiEdgesFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (voronoiEdgesDatastore == null) {
			System.out.println("Unable to open voronoi edges datastore");
			System.exit(1);
		}
		
		//water features
		
		Map<String, String> waterFeaturesDatastoreParams = new HashMap<String, String>();
		waterFeaturesDatastoreParams.put("dbtype", GEOPKG_ID);
		waterFeaturesDatastoreParams.put("database", waterFeaturesFilename);
		
		DataStore waterFeaturesDatastore = null;
		try {
			waterFeaturesDatastore = DataStoreFinder.getDataStore(waterFeaturesDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+waterFeaturesFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (voronoiEdgesDatastore == null) {
			System.out.println("Unable to open water features datastore");
			System.exit(1);
		}
		
		
		//initialize feature sources
		//---------------------------------------------------------------------
		
		SimpleFeatureSource voronoiEdgesFeatureSource = null;
		ReferencedEnvelope voronoiEdgesDataBounds = null;
		try {
			voronoiEdgesFeatureSource = voronoiEdgesDatastore.getFeatureSource(voronoiEdgesTableName);
			voronoiEdgesDataBounds = voronoiEdgesFeatureSource.getBounds();
		} catch (IOException e1) {
			System.out.println("Unable to read "+voronoiEdgesTableName+" features");
			e1.printStackTrace();
			System.exit(1);
		}
		
		SimpleFeatureSource waterFeatureSource = null;
		try {
			waterFeatureSource = waterFeaturesDatastore.getFeatureSource(waterFeaturesTable);
		} catch (IOException e1) {
			System.out.println("Unable to read "+waterFeaturesTable+" features");
			e1.printStackTrace();
			System.exit(1);
		}
				
		
		//do work
		//---------------------------------------------------------------------
		
		System.out.println("Starting to clean...");
		
		
		try {
			int phase = startPhase;
			SimpleFeatureSource featureSourceForNextPhase = voronoiEdgesFeatureSource;
			
			if (phase <= 0) {
				//this phase is probably unnecessary.  most of the bad edges are cleaned by the WKTList2GeoPackage script
				String phaseKeptTableName = outKeptTableName + "_p"+phase;
				String phaseDiscardedTableName = outDiscardedTableName + "_p"+phase;
				
				System.out.println(" - Phase "+phase+": Remove long voronoi edges");
				System.out.println("   - Initializing...");
				VoronoiLongLineCleaner cleaner = new VoronoiLongLineCleaner(featureSourceForNextPhase, waterFeatureSource, phaseKeptTableName, phaseDiscardedTableName);
				Date t1 = new Date();
				KeptAndDiscarded phaseResult = cleaner.clean();
				Date t2 = new Date();
				
				System.out.println("   - Run time: "+(t2.getTime()-t1.getTime())/1000+ " s");
	            System.out.println("   - Saving "+phaseResult.getNumKept()+" features to "+phaseKeptTableName+"...");
	            SaveUtils.saveToGeoPackage(outputFilename, phaseResult.getKept());
	            System.out.println("   - Saving "+phaseResult.getNumDiscarded()+" features to "+phaseDiscardedTableName+"...");
	            SaveUtils.saveToGeoPackage(outputFilename, phaseResult.getDiscarded());
	            System.out.println("   - Phase "+phase+" done");
	            
				featureSourceForNextPhase = DataUtilities.source(phaseResult.getKept());
				phase++;
			} 
			if (phase <= 1) {
				
				String phaseKeptTableName = outKeptTableName + "_p"+phase;
				String phaseDiscardedTableName = outDiscardedTableName + "_p"+phase;
				
				System.out.println(" - Phase "+phase+": Discard voronoi edges touching only one water feature");
				System.out.println("   - Initializing...");
				VoronoiTouchingWaterCleaner phase1 = new VoronoiTouchingWaterCleaner(featureSourceForNextPhase, waterFeatureSource, phaseKeptTableName, phaseDiscardedTableName);
				Date t1 = new Date();
				Persistable kept = new GeoPackagePersistable(outputFilename, phaseKeptTableName);
				Persistable discarded = new GeoPackagePersistable(outputFilename, phaseDiscardedTableName);
				phase1.clean(kept, discarded);
				Date t2 = new Date();
				System.out.println("   - Run time: "+(t2.getTime()-t1.getTime())/1000+ " s");
				System.out.println("   - Phase "+phase+" done");

				featureSourceForNextPhase = DataUtilities.source(kept.getFeatureCollection());
				phase++;
			} 
			if (phase <= 2) {
				String phaseKeptTableName = outKeptTableName + "_p"+phase;
				String phaseDiscardedTableName = outDiscardedTableName + "_p"+phase;
				
				System.out.println(" - Phase "+phase+": Discard dangling voronoi edges");
				System.out.println("   - Initializing...");
				
				VoronoiDanglerCleaner phase2 = new VoronoiDanglerCleaner(featureSourceForNextPhase, waterFeatureSource, phaseKeptTableName, phaseDiscardedTableName);
				
				Date t1 = new Date();
				KeptAndDiscarded phase2Result = phase2.clean();
				Date t2 = new Date();
				System.out.println("   - Run time: "+(t2.getTime()-t1.getTime())/1000+ " s");
				
				//save phase 2 results:
				// - kept edges
				// - discarded edges
				
	            System.out.println("   - Saving "+phase2Result.getNumKept()+" features to "+phaseKeptTableName+"...");
	            SaveUtils.saveToGeoPackage(outputFilename, phase2Result.getKept());
	            System.out.println("   - Saving "+phase2Result.getNumDiscarded()+" features to "+phaseDiscardedTableName+"...");
	            SaveUtils.saveToGeoPackage(outputFilename, phase2Result.getDiscarded());
	            System.out.println("   - Phase "+phase+" done");
	            phase++;
			}

			System.out.println("All done");
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
}
