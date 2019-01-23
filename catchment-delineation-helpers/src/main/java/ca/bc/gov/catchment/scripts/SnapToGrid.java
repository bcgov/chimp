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
import org.geotools.referencing.CRS;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class SnapToGrid {

	private static final String GEOPKG_ID = "geopkg";
	private static final double DEFAULT_PRECISION_SCALE = 1000; //3 decimal places
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("tables", true, "csv list of table names to process");
		options.addOption("precisionScale", true, "number of decimal places specified as a scale factor.  For 3 decimal places use scale 1000.");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String outputGeopackageFilename = null;
		String tableNamesCsv = null;
		double precisionScale = 0;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			tableNamesCsv = cmd.getOptionValue("tables");
			precisionScale = Double.parseDouble(cmd.getOptionValue("precisionScale", DEFAULT_PRECISION_SCALE+""));
		} catch (ParseException e2) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
		}
		
		String[] tableNamesToProcess = tableNamesCsv.split(","); 
		
		PrecisionModel precisionModel = new PrecisionModel(precisionScale);
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeopackageFilename);
		System.out.println("- out file: "+outputGeopackageFilename);
		System.out.println("- tables: "+tableNamesCsv);
		System.out.println("- precisionScale: "+precisionScale + " ("+precisionModel.getMaximumSignificantDigits()+" significant digits)");		
		
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
			
			CoordinateReferenceSystem crs = featureType.getCoordinateReferenceSystem();
			int srid;
			try {
				srid = CRS.lookupEpsgCode(crs, true);
			} catch (FactoryException e1) {
				System.out.println("Unable to lookup SRID for feature type "+featureTypeName);
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
			
			Query readerQuery = new Query(featureTypeName);
			FeatureReader<SimpleFeatureType, SimpleFeature> reader = null;

			//iterate over each feature in the input datastore.  
			//Copy the feature, but recreate the geometry such that it is snapped to the 
			//desired precision model grid.  save the resulting features in memory until 
			//all features are processed.
			try {
				reader = inDatastore.getFeatureReader(readerQuery, Transaction.AUTO_COMMIT);
				
				DefaultFeatureCollection outFeatureCollection = new DefaultFeatureCollection(featureTypeName, featureType);
				
				GeometryFactory gf = new GeometryFactory(precisionModel, srid);
				 
				int totalNumPointsOriginal = 0;
				int totalNumPointsRemoved = 0;
				int totalNumPointsAdded = 0;
	            while (reader.hasNext()) {
	            	
	            	//get the input feature
	            	SimpleFeature inFeature = reader.next();
	            	Geometry inGeom = (Geometry)inFeature.getDefaultGeometry();
	            	Coordinate[] inCoords = inGeom.getCoordinates();
	            	
	            	
	            	//create the output feature
	            	SimpleFeature outFeature = SimpleFeatureBuilder.copy(inFeature);
	            	
	            	//create a new geometry for the output feature.  the new
	            	//geometry has all coordinates snapped to the precision model
	            	//specified
	            	Coordinate[] outCoords = new Coordinate[inCoords.length];
	            	for(int i = 0; i < inCoords.length; i++) {
	            		Coordinate inCoord = inCoords[i];
	            		Coordinate outCoord = inCoord.copy();
	            		precisionModel.makePrecise(outCoord);
	            		outCoords[i] = outCoord;
	            	}
	                Geometry outGeom = gf.createLineString(outCoords);
	                
	                Coordinate firstCoordOut = outGeom.getCoordinates()[0];
	                Coordinate firstCoordIn = inGeom.getCoordinates()[0];
	                Geometry p1In = gf.createPoint(firstCoordIn);
	                Geometry p1Out = gf.createPoint(firstCoordOut);
	                double dist = p1In.distance(p1Out);
	                //System.out.println(firstCoordIn + " -> " + firstCoordOut + " (dist: "+dist+")");
	                
	                if (dist > 1.0/precisionScale) {
	                	throw new IllegalStateException("Post condition failed: coordinate has been moved than it should have been");
	                }
	                
	                
	                
	                //copy the attributes from the existing feature to the new feature,
	                //the overwrite the original geometry with the new geometry
	                outFeature.setAttributes(inFeature.getAttributes());
	                outFeature.setDefaultGeometry(outGeom);	
	                outFeatureCollection.add(outFeature);


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
	            
			} catch (IOException e) {
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
	 

}
