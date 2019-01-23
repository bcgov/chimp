package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;

public class SimplifyThenDensity {

	private static final String DEFAULT_FEATURE_TYPES_TO_PROCESS = "STREAM_NETWORKS,LINEAR_BOUNDARIES";
	private static final String GEOPKG_ID = "geopkg";
	private static final double DEFAULT_SIMPLIFY_DISTANCE_TOLERANCE = 2; //unit is same as input data set
	private static final double DEFAULT_DENSIFY_DISTANCE_SPACING = 50; //unit is same as input data set
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("tables", true, "csv list of table names to process");
		options.addOption("simplify", false, "flag indicating that simplification will be performed");
		options.addOption("densify", false, "flag to indicate that densification will be performed");
		options.addOption("simplifyDistanceTolerance", true, "distance tolerance in unit of input data set");
		options.addOption("densifyDistanceSpacing", true, "distance spacing in unit of input data set");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String outputGeopackageFilename = null;
		String tableNamesCsv = null;
		boolean doSimplify = false;
		boolean doDensify = false;
		double simplifyDistanceTolerance = 0;
		double densifyDistanceSpacing = 0;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			tableNamesCsv = cmd.getOptionValue("tables",DEFAULT_FEATURE_TYPES_TO_PROCESS);
			doSimplify = cmd.hasOption("simplify");
			doDensify = cmd.hasOption("densify");
			simplifyDistanceTolerance = Double.parseDouble(cmd.getOptionValue("simplifyDistanceTolerance", DEFAULT_SIMPLIFY_DISTANCE_TOLERANCE+""));
			densifyDistanceSpacing = Double.parseDouble(cmd.getOptionValue("densifyDistanceSpacing", DEFAULT_DENSIFY_DISTANCE_SPACING+""));			
		} catch (ParseException e2) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
		}
		
		String[] tableNamesToProcess = tableNamesCsv.split(","); 
		
		 
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeopackageFilename);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- tables: "+tableNamesCsv);
		
		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", GEOPKG_ID);
		inputDatastoreParams.put("database", inputGeopackageFilename);
		System.out.println(inputDatastoreParams);
		
		//Open input datastore
		DataStore inDatastore = null;
		try {
			inDatastore = DataStoreFinder.getDataStore(inputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inputGeopackageFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (inDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
			
		//Create output datastore
	
		File outFile = new File(outputGeopackageFilename);
		Map<String, String> outputDatastoreParams = new HashMap<String, String>();
		outputDatastoreParams.put("dbtype", GEOPKG_ID);
		outputDatastoreParams.put("database", outputGeopackageFilename);

		GeoPackage outGeoPackage = null;
		try {
			outGeoPackage = new GeoPackage(outFile);
			outGeoPackage.init();
		} catch (IOException e3) {
			System.out.println("Unable to create geopackage "+outputGeopackageFilename);
			e3.printStackTrace();
			System.exit(1);
		}
		
		
		for(String featureTypeName : tableNamesToProcess) {
			Date t0 = new Date();
			
			System.out.println("Processing "+featureTypeName);

			SimpleFeatureType featureType;
			try {
				featureType = inDatastore.getSchema(featureTypeName);
			} catch (IOException e) {
				System.out.println("Unable to get schema for feature type "+featureTypeName+" in the input datastore");
				e.printStackTrace();
				continue;
			}
			
			ReferencedEnvelope bounds = null;
			try {
				SimpleFeatureSource featureSource = inDatastore.getFeatureSource(featureTypeName);
				bounds = featureSource.getBounds();
			} catch (IOException e3) {
				// TODO Auto-generated catch block
				System.out.println("Unable to get bounds from input");
				e3.printStackTrace();
				continue;
			}
			
			
			//initialize new schema in the output datastore

			FeatureEntry entry = new FeatureEntry();
			entry.setBounds(bounds);
			
			/*
			try {
				outGeoPackage.create(entry, featureType);
			} catch (IOException e2) {
				// TODO Auto-generated catch block
				System.out.println("Unable to initialize schema");
				e2.printStackTrace();
			}
			*/
			
			/*
			Query writerQuery = new Query(featureTypeName);
			SimpleFeatureWriter writer = null;
			try {
				writer = outGeoPackage.writer(entry, true, writerQuery.getFilter(), Transaction.AUTO_COMMIT);
			} catch (IOException e1) {
				System.out.println("Unable to create writer for "+featureTypeName);
				e1.printStackTrace();				
			}
			*/

			Query readerQuery = new Query(featureTypeName);
			FeatureReader<SimpleFeatureType, SimpleFeature> reader = null;

			//iterate over each feature in the input datastore.  
			//Copy the feature.  Simplify the geometry of the copy. Add the copy to a
			//feature collection in memory.
			try {
				reader = inDatastore.getFeatureReader(readerQuery, Transaction.AUTO_COMMIT);
				
				DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection(featureTypeName, featureType);
				
				int totalNumPointsOriginal = 0;
				int totalNumPointsRemoved = 0;
				int totalNumPointsAdded = 0;
	            while (reader.hasNext()) {
	            	
	            	//get the input feature
	            	SimpleFeature infeature = reader.next();
	            	
	            	//SimpleFeature outFeature = (SimpleFeature) writer.next();
	            	SimpleFeature outFeature = SimpleFeatureBuilder.copy(infeature);
	            	
	                Geometry originalGeometry = (Geometry)infeature.getDefaultGeometry();
	                totalNumPointsOriginal += originalGeometry.getNumPoints();
	                
	                Geometry geomToProcess = originalGeometry;
	                
	                //simplify geometry
	                if (doSimplify) {
		                TopologyPreservingSimplifier simplifier = new TopologyPreservingSimplifier(geomToProcess);
		                //DouglasPeuckerSimplifier simplifier = new DouglasPeuckerSimplifier(geomToProcess);
		                simplifier.setDistanceTolerance(simplifyDistanceTolerance);
		                Geometry simplifiedGeometry = simplifier.getResultGeometry();
		                int numPointsRemoved = geomToProcess.getNumPoints() - simplifiedGeometry.getNumPoints();
		                totalNumPointsRemoved += numPointsRemoved;
		                geomToProcess = simplifiedGeometry;
	                }
	                
	                //densify geometry
	                if (doDensify) {
		                Densifier densifier = new Densifier(geomToProcess);
		                densifier.setDistanceTolerance(densifyDistanceSpacing);
		                Geometry densifiedGeometry = densifier.getResultGeometry();
		                int numPointsAdded = densifiedGeometry.getNumPoints() - geomToProcess.getNumPoints();
		                totalNumPointsAdded += numPointsAdded;
		                geomToProcess = densifiedGeometry;
	                }
		                
	                /*
	                double linearFeatureId = (Double)(infeature.getAttribute("LINEAR_FEATURE_ID")); 
	                if (linearFeatureId == 707106786) {
	                	System.out.println("LINEAR_FEATURE_ID = "+linearFeatureId);
	                	System.out.println(" - simplified geom");
	                	Coordinate[] coords1 = simplifiedGeometry.getCoordinates();
	                	for(Coordinate c : coords1) {
	                		String s = c.x + " " + c.y;
	                		System.out.println("   - "+s);
	                	}
	                	System.out.println(" - densified geom");
	                	Coordinate[] coords2 = densifiedGeometry.getCoordinates();
	                	for(Coordinate c : coords2) {	                		
	                		String s = c.x + " " + c.y;
	                		System.out.println("   - "+s);
	                	}
	                }
	                */
	                
	                //copy the attributes from the existing feature to the new feature,
	                //the overwrite the origin geometry with the simplfied geometry
	                outFeature.setAttributes(infeature.getAttributes());
	                outFeature.setDefaultGeometry(geomToProcess);	
	                outFeatureCollection.add(outFeature);

	                //save the new feature to the output datastore
                    //writer.write();

	            }
	            
	            //Save the in-memory output feature collection to the output file
	            SimpleFeatureCollection simpleCollection = DataUtilities.simple(outFeatureCollection);
	            System.out.println("Saving...");
	            outGeoPackage.add(entry, simpleCollection);
	            System.out.println(" - Done");
	            System.out.println("Adding spatial index...");
	            outGeoPackage.createSpatialIndex(entry);
	            System.out.println(" - Done");	  
	            
	    		Date t1 = new Date();
	    		long runTimeMs = t1.getTime() - t0.getTime();
	    		
	    		int finalNumPoints = totalNumPointsOriginal - totalNumPointsRemoved + totalNumPointsAdded;
	    		float percentChange = (float)finalNumPoints / (float)totalNumPointsOriginal * 100 - 100;
	    		
	    		System.out.println("Summary");
	    		System.out.println(" - "+outFeatureCollection.size()+" features processed");
	    		System.out.println(" - run time: "+runTimeMs+" ms");	
	    		System.out.println(" - # original vertivies: "+totalNumPointsOriginal);
	    		System.out.println(" - # verticies removed by simplification: "+totalNumPointsRemoved);
	    		System.out.println(" - # verticies added by densification: "+totalNumPointsAdded);
	    		System.out.println(" - # vertificies in output: "+finalNumPoints+ "("+percentChange+"% change)");
	            
			} catch (IOException e) {
				System.out.println("Unable to read stream networks");
				e.printStackTrace();
				System.exit(1);
			}
			finally {
				
				try {
					reader.close();
				} catch (IOException e) {
					System.out.println("Unable to close reader for "+featureTypeName);
					e.printStackTrace();
					System.exit(1);
				}
	        }
		}

		outGeoPackage.close();
		System.out.println("All done");
		
	}
	 
	 public static void showUsage() {
		 System.out.println("usage: java ca.bc.gov.catchments.SimplifyApp [input_geopackage_filename] [output_geopackage_filename]");
	 }
	
}
