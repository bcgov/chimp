package ca.bc.gov.catchment.scripts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;

public class SimplifyApp {

	private static final String[] FEATURE_TYPES_TO_PROCESS = {"STREAM_NETWORKS", "LINEAR_BOUNDARIES"};
	private static final String GEOPKG_ID = "geopkg";
	private static final double DISTANCE_TOLERANCE = 2; //unit = metres??
	
	public static void main(String[] args) {
		
		if (args.length != 2) {
			showUsage();
			return;
		}
		String inputGeopackageFilename = args[0];
		String outputGeopackageFilename = args[1];
		 
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
		
		
		for(String featureTypeName : FEATURE_TYPES_TO_PROCESS) {
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
	            while (reader.hasNext()) {
	            	
	            	//get the input feature
	            	SimpleFeature infeature = reader.next();
	            	
	            	//SimpleFeature outFeature = (SimpleFeature) writer.next();
	            	SimpleFeature outFeature = SimpleFeatureBuilder.copy(infeature);
	            	
	            	//simplify geometry
	                Geometry originalGeometry = (Geometry)infeature.getDefaultGeometry();
	                totalNumPointsOriginal += originalGeometry.getNumPoints();
	                
	                TopologyPreservingSimplifier simplifier = new TopologyPreservingSimplifier(originalGeometry);
	                //DouglasPeuckerSimplifier simplifier = new DouglasPeuckerSimplifier(originalGeometry);
	                simplifier.setDistanceTolerance(DISTANCE_TOLERANCE);
	                Geometry simplifiedGeometry = simplifier.getResultGeometry();
	                int numPointsRemoved = originalGeometry.getNumPoints() - simplifiedGeometry.getNumPoints();
	                totalNumPointsRemoved += numPointsRemoved;
	                
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
	                }	
	                */
	                
	                //copy the attributes from the existing feature to the new feature,
	                //the overwrite the origin geometry with the simplfied geometry
	                outFeature.setAttributes(infeature.getAttributes());
	                outFeature.setDefaultGeometry(simplifiedGeometry);	
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
	    		
	    		float percentSimplification = (float)totalNumPointsRemoved / (float)totalNumPointsOriginal * 100;
	    		
	    		System.out.println("Summary");
	    		System.out.println(" - "+outFeatureCollection.size()+" features processed");
	    		System.out.println(" - run time: "+runTimeMs+" ms");	
	    		System.out.println(" - "+totalNumPointsOriginal+" points reduced to "+(totalNumPointsOriginal-totalNumPointsRemoved)+". ("+percentSimplification+"% reduction)");	
	            
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
		System.out.println("Simplification complete");
		
	}
	 
	 public static void showUsage() {
		 System.out.println("usage: java ca.bc.gov.catchments.SimplifyApp [input_geopackage_filename] [output_geopackage_filename]");
	 }
	
}
