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
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.CommonFactoryFinder;
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
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ca.bc.gov.catchment.voronoi.SpatialIndexFeatureCollection2;

public class CheckCrosses {

	private static final String GEOPKG_ID = "geopkg";
	private static final double DEFAULT_PRECISION_SCALE = 1000; //3 decimal places
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("tables", true, "csv list of table names to process");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeopackageFilename = null;
		String outputGeopackageFilename = null;
		String tableNamesCsv = null;
		double precisionScale = 0;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeopackageFilename = cmd.getOptionValue("i");
			tableNamesCsv = cmd.getOptionValue("tables");
		} catch (ParseException e2) {
			formatter.printHelp( WKTList2GeoPackage.class.getSimpleName(), options );
		}
		
		String[] tableNamesToProcess = tableNamesCsv.split(","); 
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeopackageFilename);
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
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		int totalNumCrosses = 0;
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
			SimpleFeatureSource inFeatureSource = null;
			try {
				inFeatureSource = inDatastore.getFeatureSource(featureTypeName);
			} catch (IOException e3) {
				// TODO Auto-generated catch block
				System.out.println("Unable to get input feature source");
				e3.printStackTrace();
				continue;
			}
						

			//iterate over each feature in the input datastore.  
			//check whether it crosses any other features.  
			try {
				SimpleFeatureCollection inFeatureCollection = inFeatureSource.getFeatures();
				SimpleFeatureIterator it = inFeatureCollection.features();
				String geomPropertyName = featureType.getGeometryDescriptor().getLocalName();
				 
				SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection(inFeatureCollection);
				SpatialIndexFeatureSource fastFeatureSource = new SpatialIndexFeatureSource(fastFeatureCollection);
				
				
				int numCrosses = 0;
	            while (it.hasNext()) {
	            	
	            	//get the input feature
	            	SimpleFeature inFeature = it.next();
	            	Geometry inGeom = (Geometry)inFeature.getDefaultGeometry();
	            	
	            	Filter crossesFilter = filterFactory.crosses(filterFactory.property(geomPropertyName), filterFactory.literal(inGeom));
	            	SimpleFeatureCollection crossingFeatures = fastFeatureSource.getFeatures(crossesFilter);
	            	numCrosses += crossingFeatures.size();

	            }
	            it.close();	  
	            totalNumCrosses += numCrosses;
	            
	    		Date t1 = new Date();
	    		long runTimeMs = t1.getTime() - t0.getTime();
	    		
	    		System.out.println("Summary");
	    		System.out.println(" - "+inFeatureCollection.size()+" features processed");
	    		System.out.println(" - # crossings: "+numCrosses);	
	    		System.out.println(" - run time: "+runTimeMs+" ms");	
	    		
	            
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		System.out.println("All done");
		System.exit(totalNumCrosses);
		
	}
	 

}
