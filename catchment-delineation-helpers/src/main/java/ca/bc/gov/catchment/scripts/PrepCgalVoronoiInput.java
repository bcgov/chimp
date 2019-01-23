package ca.bc.gov.catchment.scripts;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.rogach.jopenvoronoi.Edge;
import org.rogach.jopenvoronoi.EdgeType;
import org.rogach.jopenvoronoi.HalfEdgeDiagram;
import org.rogach.jopenvoronoi.MedialAxisFilter;
import org.rogach.jopenvoronoi.PolygonInteriorFilter;
import org.rogach.jopenvoronoi.SvgOutput;
import org.rogach.jopenvoronoi.Vertex;
import org.rogach.jopenvoronoi.VertexStatus;
import org.rogach.jopenvoronoi.VertexType;
import org.rogach.jopenvoronoi.VoronoiDiagram;

import ca.bc.gov.catchments.utils.SaveUtils;
import ca.bc.gov.catchments.utils.SpatialUtils;

/*
 * Test bboxes: 
 * 		21 features:  -115.79381,49.21187,-115.75347,49.24806
 * 		1 feature:    -115.80751,49.23394,-115.79588,49.24468
 * 
 * 	
 * 
 * 
 */
public class PrepCgalVoronoiInput {

	private static final String DEFAULT_STREAM_NETWORKS_FEATURE_TYPE = "STREAM_NETWORKS";
	private static final String DEFAULT_LINEAR_BOUNDARIES_FEATURE_TYPE = "LINEAR_BOUNDARIES";
	private static final String REACH_CATCHMENTS_FEATURE_TYPE = "REACH_CATCHMENTS";
	private static final Envelope VORONOI_BOUNDS = new ReferencedEnvelope(-Math.sin(0.785398),Math.sin(0.785398),-Math.sin(0.785398),Math.sin(0.785398), null);
	//private static final String DEFAULT_WHITELIST_FILTER = "EDGE_TYPE:1000,1050,1100,1150,1300,1325,1350,1375,1400,1410,1425,1450,1475,1500,1525,1550,1600,1800,1825,1850,1875,1900,1925,1950,1975,2000,2100,2300";
				
	
	private static final String GEOPKG_ID = "geopkg";
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input GeoPackage file");
		options.addOption("outTextFile", true, "Output Text file");
		options.addOption("outGeoPackageFile", true, "Output GeoPackage file");
		options.addOption("bbox", true, "Bounding box representing area to process (format: 'xmin,ymin,xmax,ymax')");
		options.addOption("bboxcrs", true, "CRS of the bounding box.  e.g. 'EPSG:3005' or 'EPSG:4326'");
		options.addOption("streams", true, "name of streams table");
		options.addOption("linearboundaries", true, "name of linear boundaries table");
		options.addOption("whitelistfilter", true, "[attr]:val1,val2");
		options.addOption("blacklistfilter", true, "[attr]:val1,val2");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputGeoPackageFilename = null;
		String outputTxtFilename = null;
		String outputGeoPackageFilename = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		String streamsTableName = null;
		String linearBoundariesTableName = null;
		String whitelist = null;
		String blacklist = null;
		String outTableNameUnsegmented = "water_features";
		String outTableNameSegmented = "water_features_segmented";
		boolean segmentedGpgk = false;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputGeoPackageFilename = cmd.getOptionValue("i");
			outputTxtFilename = cmd.getOptionValue("outTextFile");
			outputGeoPackageFilename = cmd.getOptionValue("outGeoPackageFile");
			bboxStr = cmd.getOptionValue("bbox");
			bboxCrs = cmd.getOptionValue("bboxcrs");
			streamsTableName = cmd.getOptionValue("streams", DEFAULT_STREAM_NETWORKS_FEATURE_TYPE);
			linearBoundariesTableName = cmd.getOptionValue("linearboundaries", DEFAULT_LINEAR_BOUNDARIES_FEATURE_TYPE);
			whitelist = cmd.getOptionValue("whitelistfilter");
			blacklist = cmd.getOptionValue("blacklistfilter");
		} catch (ParseException e2) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
		}

		//validate inputs
		if (inputGeoPackageFilename == null) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (outputTxtFilename == null) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (bboxStr == null) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (bboxCrs == null) {
			formatter.printHelp( PrepCgalVoronoiInput.class.getSimpleName(), options );
			System.exit(1);
		}
		if (whitelist != null && blacklist != null) {
			System.out.println("Can only specify one of [whitelistfilter, blacklistfilter]");
			System.exit(1);
		}
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputGeoPackageFilename);
		System.out.println("- out text file: "+outputTxtFilename);
		if (outputGeoPackageFilename != null) {
			System.out.println("- out geopackage file: "+outputGeoPackageFilename);
		}
		System.out.println("- bbox: "+bboxStr);
		System.out.println("- bbox srs: "+bboxCrs);
		
		if (bboxCrs != null) {
			if (bboxCrs.startsWith("EPSG:")) {
				String srid = bboxCrs.substring(5);
				bboxSrid = Integer.parseInt(srid);
			}
			else {
				System.out.println("Unknown bboxcrs: "+bboxCrs);
				System.exit(1);
			}
		}
		
		
		Map<String, String> inputDatastoreParams = new HashMap<String, String>();
		inputDatastoreParams.put("dbtype", GEOPKG_ID);
		inputDatastoreParams.put("database", inputGeoPackageFilename);
		
		//Open input datastore
		DataStore inDatastore = null;
		try {
			inDatastore = DataStoreFinder.getDataStore(inputDatastoreParams);
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inputGeoPackageFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		if (inDatastore == null) {
			System.out.println("Unable to open input datastore");
			System.exit(1);
		}
			
		//Create output datastore
		BufferedWriter textFileWriter = null;
		try {
			textFileWriter = new BufferedWriter(new FileWriter(outputTxtFilename, false));
		} catch (IOException e) {
			System.out.println("Unable to open output file: "+outputTxtFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		FeatureSource streamFeatureSource = null;
		ReferencedEnvelope streamDataBounds = null;
		try {
			streamFeatureSource = inDatastore.getFeatureSource(streamsTableName);
			streamDataBounds = streamFeatureSource.getBounds();
		} catch (IOException e1) {
			System.out.println("Unable to read "+streamsTableName+" features");
			e1.printStackTrace();
			System.exit(1);
		}
		
		FeatureSource linearBoundariesFeatureSource = null;
		ReferencedEnvelope linearBoundariesDataBounds = null;
		try {
			linearBoundariesFeatureSource = inDatastore.getFeatureSource(linearBoundariesTableName);
			linearBoundariesDataBounds = linearBoundariesFeatureSource.getBounds();
		} catch (IOException e1) {
			System.out.println("Unable to read "+linearBoundariesTableName+" features");
			e1.printStackTrace();
			System.exit(1);
		}
		
		ReferencedEnvelope bboxInDataCrs = null;
		try {
			bboxInDataCrs = parseBbox(bboxStr, bboxCrs, streamDataBounds.getCoordinateReferenceSystem());
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e);
			System.exit(1);
		}
		
		try {
			int streamDataEpsgCode = CRS.lookupEpsgCode(streamDataBounds.getCoordinateReferenceSystem(), true);
			System.out.println("Input data summary:");
			System.out.println(" - "+streamsTableName);
			System.out.println("   - Data CRS: EPSG:"+streamDataEpsgCode);
			System.out.println("   - Data bounds");
			System.out.println("       EPSG:"+streamDataEpsgCode+": ["+streamDataBounds.getMinX()+","+streamDataBounds.getMinY()+","+streamDataBounds.getMaxX()+","+streamDataBounds.getMaxY()+"]");
			if (streamDataEpsgCode != 4326) {
				ReferencedEnvelope streamDataBounds4326 = reproject(streamDataBounds, "EPSG:4326");
				System.out.println("       ESGP:4326: ["+streamDataBounds4326.getMinX()+","+streamDataBounds4326.getMinY()+","+streamDataBounds4326.getMaxX()+","+streamDataBounds4326.getMaxY()+"]");
			}
			
			int linearBoundariesDataEpsgCode = CRS.lookupEpsgCode(linearBoundariesDataBounds.getCoordinateReferenceSystem(), true);
			System.out.println(" - "+linearBoundariesTableName);
			System.out.println("   - Data CRS: EPSG:"+linearBoundariesDataEpsgCode);
			System.out.println("   - Data bounds");
			System.out.println("       EPSG:"+linearBoundariesDataEpsgCode+": ["+linearBoundariesDataBounds.getMinX()+","+linearBoundariesDataBounds.getMinY()+","+linearBoundariesDataBounds.getMaxX()+","+linearBoundariesDataBounds.getMaxY()+"]");
			if (linearBoundariesDataEpsgCode != 4326) {
				ReferencedEnvelope linearBoundariesDataBounds4326 = reproject(linearBoundariesDataBounds, "EPSG:4326");
				System.out.println("       EPSG:4326: ["+linearBoundariesDataBounds4326.getMinX()+","+linearBoundariesDataBounds4326.getMinY()+","+linearBoundariesDataBounds4326.getMaxX()+","+linearBoundariesDataBounds4326.getMaxY()+"]");
			}			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//setup feature type for output geopackage
		//---------------------------------------------------------------------
		
		SimpleFeatureType unsegmentedFeatureType = null;
		try {
			String attrs = "geometry:LineString:srid="+bboxSrid;
			unsegmentedFeatureType = DataUtilities.createType(outTableNameUnsegmented, attrs);
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+outTableNameUnsegmented);
			System.exit(1);
		}
		
		SimpleFeatureType segmentedFeatureType = null;
		try {
			String attrs = "geometry:LineString:srid="+bboxSrid;
			segmentedFeatureType = DataUtilities.createType(outTableNameSegmented, attrs);
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+outTableNameSegmented);
			System.exit(1);
		}
		
		DefaultFeatureCollection unsegmentedFeatures = new DefaultFeatureCollection(outTableNameUnsegmented, unsegmentedFeatureType);
		DefaultFeatureCollection segmentedFeatures = new DefaultFeatureCollection(outTableNameSegmented, segmentedFeatureType);
		
		//output to files (txt and geopackage)
		//---------------------------------------------------------------------
		
		try {
			SimpleFeatureBuilder unsegmentedFeatureBuilder = new SimpleFeatureBuilder(unsegmentedFeatureType);
			GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
			Geometry boundingPolygon = geometryFactory.createPolygon(new Coordinate[] {
					new Coordinate(bboxInDataCrs.getMinX(), bboxInDataCrs.getMinY()),
					new Coordinate(bboxInDataCrs.getMaxX(), bboxInDataCrs.getMinY()),
					new Coordinate(bboxInDataCrs.getMaxX(), bboxInDataCrs.getMaxY()),
					new Coordinate(bboxInDataCrs.getMinX(), bboxInDataCrs.getMaxY()),
					new Coordinate(bboxInDataCrs.getMinX(), bboxInDataCrs.getMinY()),
					});
			
			System.out.println("Filters to apply to the input data:");
			System.out.println(" - geometry WITHIN "+bboxStr+", and");
			if (whitelist != null) {
				System.out.println(" - whitelist: "+whitelist);
			}
			if (blacklist != null) {
				System.out.println(" - blacklist: "+blacklist);
			}
			
			System.out.println("Geometries to use as input for voronoi diagram");
			
			//output the geometry of the target bbox itself
			writeGeometry(textFileWriter, boundingPolygon);
			System.out.println(" - 1 polygon defining the selected bbox");
			
			//streams
			//-------
			FeatureCollection streams = filterFeatures(streamFeatureSource, boundingPolygon, whitelist, blacklist);
			FeatureIterator streamIterator = streams.features();			
			System.out.println(" - "+streams.size() + " stream lines");
			while (streamIterator.hasNext()) {            	
            	//get the input feature
            	SimpleFeature inFeature = (SimpleFeature)streamIterator.next();            	
            	List<SimpleFeature> segmentFeatureList = splitIntoSegments(inFeature, segmentedFeatureType);
            	for(SimpleFeature segmentFeature : segmentFeatureList) {
            		Geometry geometry = (Geometry)segmentFeature.getDefaultGeometry();
            		writeGeometry(textFileWriter, geometry);
            	}
            		Object[] attrs = {inFeature.getDefaultGeometry()};
            		SimpleFeature featureCopy = unsegmentedFeatureBuilder.buildFeature(inFeature.getID(), attrs);
            		unsegmentedFeatures.add(featureCopy);
            		segmentedFeatures.addAll(segmentFeatureList);
            }
			streamIterator.close();
			
			//linear boundaries
			//-----------------
			FeatureCollection linearBoundaries = filterFeatures(linearBoundariesFeatureSource, boundingPolygon, whitelist, blacklist);
			FeatureIterator linearBoundaryIterator = linearBoundaries.features();
			System.out.println(" - "+linearBoundaries.size() + " linear boundary lines");
			while (linearBoundaryIterator.hasNext()) {            	
				//get the input feature
            	SimpleFeature inFeature = (SimpleFeature)linearBoundaryIterator.next();      	      	
            	List<SimpleFeature> segmentFeatureList = splitIntoSegments(inFeature, segmentedFeatureType);
            	for(SimpleFeature segmentFeature : segmentFeatureList) {
            		Geometry geometry = (Geometry)segmentFeature.getDefaultGeometry();
            		writeGeometry(textFileWriter, geometry);
            	}
        		Object[] attrs = {inFeature.getDefaultGeometry()};
        		SimpleFeature featureCopy = unsegmentedFeatureBuilder.buildFeature(inFeature.getID(), attrs);
        		unsegmentedFeatures.add(featureCopy);
        		segmentedFeatures.addAll(segmentFeatureList);

            	
            }
			linearBoundaryIterator.close();
					
			System.out.println("Saved Text File: "+outputTxtFilename);
            
			//save geopackage
			if (outputGeoPackageFilename != null) {
				System.out.println("Saving GeoPackage: "+outputGeoPackageFilename);
				System.out.println(" - "+outTableNameUnsegmented + ": "+unsegmentedFeatures.size() + " features");
				SaveUtils.saveToGeoPackage(outputGeoPackageFilename, unsegmentedFeatures);
				System.out.println(" - "+outTableNameSegmented + ": "+segmentedFeatures.size() + " features");
				SaveUtils.saveToGeoPackage(outputGeoPackageFilename, segmentedFeatures);				
			}
			
			//cleanup
			textFileWriter.close();
			inDatastore.dispose();
			
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		
		System.out.print("All Done");
	}
	
	/*
	 * splits a LineString feature into its segments, one feature for each.  Note: the attributes from the 
	 * original feature aren't copied into the new features.
	 */
	private static List<SimpleFeature> splitIntoSegments(SimpleFeature inFeature, SimpleFeatureType outFeatureType) {
		List<SimpleFeature> result = new ArrayList<SimpleFeature>();
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(outFeatureType);
		
		Geometry inGeometry = (Geometry)inFeature.getDefaultGeometry();
		Coordinate[] coordinates = inGeometry.getCoordinates();
		Coordinate prevCoord = null;
		int index = 0;
		for(Coordinate coord : coordinates) {
			index++;
			if (prevCoord != null) {
				//create new geometry
				Coordinate[] segmentCoords = {prevCoord, coord};
				Geometry segmentGeometry = geometryFactory.createLineString(segmentCoords);
				
				//create new feature
				String newId = inFeature.getID()+"-"+index;
				Object[] attributeValues = new Object[] { segmentGeometry };
				SimpleFeature segmentFeature = featureBuilder.buildFeature(newId, attributeValues);
				
				//add feature to collection
				result.add(segmentFeature);
			}
			prevCoord = coord;
		}
		return result;
	}
	
	/**
	 * Gets a feature collection with the following filters applied:
	 *  - GEOMETRY "within" the given bounding polygon, and
	 *  - EDGE_TYPE equal to any value from edgeTypeWhitelist
	 */
	private static FeatureCollection filterFeatures(FeatureSource featureSource, Geometry boundingPolygon, String whitelist, String blacklist) throws IOException {
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		FeatureType schema = featureSource.getSchema();
		String streamGeometryPropertyName = schema.getGeometryDescriptor().getLocalName();			
		//'within' only includes geometries that are fully inside the given bounds.  
		//use 'bbox' if also needing features that cross the bounds.
		//Filter bboxFilter = filterFactory.bbox(filterFactory.property(streamGeometryPropertyName), bboxInDataCrs);
		Filter areaFilter = filterFactory.within(filterFactory.property(streamGeometryPropertyName), filterFactory.literal(boundingPolygon));
		Filter propertyFilter = null;
		Filter compositeFilter = null;

		if (whitelist != null) {
			String propertyName = parseFilterProperty(whitelist);
			if (schema.getDescriptor(propertyName) != null) {
				String[] propertyValues = parseFilterValues(whitelist);
				List<Filter> propertyFilters = new ArrayList<Filter>();
				for (String value : propertyValues) {
					Filter edgeFilter = filterFactory.equals(filterFactory.property(propertyName), filterFactory.literal(value));
					propertyFilters.add(edgeFilter);
				}
				propertyFilter = filterFactory.or(propertyFilters);
			}
		}
		else if (blacklist != null) {
			String propertyName = parseFilterProperty(blacklist);
			if (schema.getDescriptor(propertyName) != null) {
				String[] propertyValues = parseFilterValues(blacklist);
				List<Filter> propertyFilters = new ArrayList<Filter>();
				for (String value : propertyValues) {
					Filter edgeFilter = filterFactory.notEqual(filterFactory.property(propertyName), filterFactory.literal(value));
					propertyFilters.add(edgeFilter);
				}
				Filter nullFilter = filterFactory.isNull(filterFactory.property(propertyName));
				Filter blacklistFilter = filterFactory.and(propertyFilters);
				//the filter to allow null values must be added explicitly, because 
				//the NOT EQUAL filter doesn't consider null not to be equal to a given (non-null) literal.  
				//is this a geotools bug?
				propertyFilter = filterFactory.or(blacklistFilter, nullFilter);
			}
		}
		
		if (propertyFilter != null) {
			compositeFilter = filterFactory.and(areaFilter, propertyFilter);
		}
		else {
			compositeFilter = areaFilter;
		}
		
		FeatureCollection streams = featureSource.getFeatures(compositeFilter);
		return streams;
	}
	

	private static String parseFilterProperty(String s) {
		int a = s.indexOf(":");
		if (a == -1) {
			throw new IllegalArgumentException("unknown filter format.  expecting [attr]:val1,val2,val3,...");
		}
		String property = s.substring(0, a);
		return property;
	}
	
	private static String[] parseFilterValues(String s) {
		int a = s.indexOf(":");
		if (a == -1) {
			throw new IllegalArgumentException("unknown filter format.  expecting [attr]:val1,val2,val3,...");
		}
		String valuesCsv = s.substring(a+1);
		String[] values = valuesCsv.split(",");
		return values;
	}
	
	public static void writeGeometry(Writer out, Geometry geometry) throws IOException {
		Coordinate[] coordinates = geometry.getCoordinates();
		Coordinate prevCoord = null;
		for(Coordinate coord : coordinates) {
			if (prevCoord != null) {
				String lineSegmentAsStr = "s " + prevCoord.x + " " + prevCoord.y + "  " + coord.x + " " + coord.y;
				out.write(lineSegmentAsStr+"\n");
			}
			prevCoord = coord;
		}
	}
	
	private static ReferencedEnvelope parseBbox(String bboxStr, String crsInStr, CoordinateReferenceSystem crsOut) {
		double xmin;
		double ymin;
		double xmax;
		double ymax;
		CoordinateReferenceSystem crsIn = null;
		try {
			crsIn = CRS.decode(crsInStr);
		} catch (NoSuchAuthorityCodeException e) {
			throw new IllegalArgumentException("Unable to lookup CRS");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to lookup CRS.  An internal error occurred.");
		}
			
		String[] pieces = bboxStr.split(",");
		if (pieces.length != 4) {
			throw new IllegalArgumentException("Unable to parse bbox");
		}
		try {
			xmin = Double.parseDouble(pieces[0]);
			ymin = Double.parseDouble(pieces[1]);
			xmax = Double.parseDouble(pieces[2]);
		    ymax = Double.parseDouble(pieces[3]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Unable to parse bbox");
		}
		ReferencedEnvelope envelopeInCrs = new ReferencedEnvelope(xmin, xmax, ymin, ymax, crsIn);
		ReferencedEnvelope envelopeOutCrs;
		try {
			envelopeOutCrs = envelopeInCrs.transform(crsOut, false);
		} catch (TransformException e) {
			throw new IllegalStateException("Unable to reproject bbox.");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to reproject bbox.  An internal error occurred.");
		}
		return envelopeOutCrs;
	}
	
	private static ReferencedEnvelope reproject(ReferencedEnvelope bounds, String targetCrsStr) {
		CoordinateReferenceSystem targetCrs = null;
		try {
			targetCrs = CRS.decode(targetCrsStr);
		} catch (NoSuchAuthorityCodeException e) {
			throw new IllegalArgumentException("Unable to lookup CRS");
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to lookup CRS.  An internal error occurred.");
		}
		
		ReferencedEnvelope outEnvelope = null;
		try {
			outEnvelope = bounds.transform(targetCrs, false);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to reproject");
		}
		return outEnvelope;
	}
	
	private static Coordinate normalizeCoordinate(Coordinate coordinate, Envelope inBounds, Envelope outBounds) {
		double inWidth = inBounds.getWidth();
		double outWidth = outBounds.getWidth();
		double inHeight = inBounds.getHeight();
		double outHeight = outBounds.getHeight();
		//System.out.println("source bounds: "+inWidth+","+inHeight+" -> target bounds: "+outWidth+","+outHeight);
		double newX = (coordinate.x - inBounds.getMinX()) * outWidth / inWidth + outBounds.getMinX();
		double newY = (coordinate.y - inBounds.getMinY()) * outHeight / inHeight + outBounds.getMinY();
		Coordinate normalizedCoord = new Coordinate(newX, newY);
		
		//System.out.println(coordinate.x+","+coordinate.y+" -> "+normalizedCoord.x+","+normalizedCoord.y);
		return normalizedCoord;
	}
	

}
