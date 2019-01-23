package ca.bc.gov.catchment.scripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
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

/*
 * Test bboxes: 
 * 		21 features:  -115.79381,49.21187,-115.75347,49.24806
 * 		1 feature:    -115.80751,49.23394,-115.79588,49.24468
 * 
 * 	
 * 
 * 
 */
public class VoronoiOutput2GeoPackage {

	private static final String GEOPKG_ID = "geopkg";
	private static final String GEOPKG_VORONOI_EDGES_TABLE = "voronoi_edges";
	
	
	public static void main(String[] args) {
		
		// create Options object
		Options options = new Options();
		options.addOption("i", true, "Input Txt file");
		options.addOption("o", true, "Output GeoPackage file");
		options.addOption("bbox", true, "Bounding box: [minx,miny,maxx,maxy]");
		options.addOption("bboxcrs", true, "CRS of the bounding box.  e.g. 'EPSG:3005' or 'EPSG:4326'");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String inputTxtFilename = null;
		String outputGeopackageFilename = null;
		String bboxStr = null;
		String bboxCrs = null;
		int bboxSrid = -1;
		Envelope bounds = null;
		
		try {
			CommandLine cmd = parser.parse( options, args);
			inputTxtFilename = cmd.getOptionValue("i");
			outputGeopackageFilename = cmd.getOptionValue("o");	
			bboxStr = cmd.getOptionValue("bbox");
			bboxCrs = cmd.getOptionValue("bboxcrs");
		} catch (ParseException e2) {
			formatter.printHelp( VoronoiOutput2GeoPackage.class.getSimpleName(), options );
		}

		//validate inputs
		if (inputTxtFilename == null) {
			formatter.printHelp( VoronoiOutput2GeoPackage.class.getSimpleName(), options );
			System.exit(1);
		}
		if (outputGeopackageFilename == null) {
			formatter.printHelp( VoronoiOutput2GeoPackage.class.getSimpleName(), options );
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
		
		if(bboxStr != null) {
			String[] pieces = bboxStr.split(",");
			double minX = Double.parseDouble(pieces[0]);
			double minY = Double.parseDouble(pieces[1]);
			double maxX = Double.parseDouble(pieces[2]);
			double maxY = Double.parseDouble(pieces[3]);
			bounds = new ReferencedEnvelope(minX,maxX,minY,maxY, null);
		}
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
		
		System.out.println("Inputs:");
		System.out.println("- in file: "+inputTxtFilename);
		System.out.println("- out file: "+outputGeopackageFilename);
		
		//Open input file
		BufferedReader inReader = null;
		try {
			inReader = new BufferedReader(new FileReader(inputTxtFilename));
		} catch (IOException e) {
			System.out.println("Unable to open input file: "+inputTxtFilename);
			e.printStackTrace();
			System.exit(1);
		}
		
		//Create output geopackage
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
				
		try {
			//int streamDataEpsgCode = CRS.lookupEpsgCode(streamDataBounds.getCoordinateReferenceSystem(), true);
			System.out.println("Input data summary:");
			//System.out.println(" - "+STREAM_NETWORKS_FEATURE_TYPE);
			//System.out.println("   - Data CRS: EPSG:"+streamDataEpsgCode);
			//System.out.println("   - Data bounds");
			//System.out.println("       EPSG:"+streamDataEpsgCode+": ["+streamDataBounds.getMinX()+","+streamDataBounds.getMinY()+","+streamDataBounds.getMaxX()+","+streamDataBounds.getMaxY()+"]");
			//if (streamDataEpsgCode != 4326) {
			//	ReferencedEnvelope streamDataBounds4326 = reproject(streamDataBounds, "EPSG:4326");
			//	System.out.println("       ESGP:4326: ["+streamDataBounds4326.getMinX()+","+streamDataBounds4326.getMinY()+","+streamDataBounds4326.getMaxX()+","+streamDataBounds4326.getMaxY()+"]");
			//}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		SimpleFeatureType voronoiEdgesFeatureType = null;
		try {
			voronoiEdgesFeatureType = DataUtilities.createType(GEOPKG_VORONOI_EDGES_TABLE, "geometry:LineString");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+GEOPKG_VORONOI_EDGES_TABLE);
			System.exit(1);
		}
		
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(voronoiEdgesFeatureType);
		
		DefaultFeatureCollection voronoiEdgesFeatureCollection = new DefaultFeatureCollection(GEOPKG_VORONOI_EDGES_TABLE, voronoiEdgesFeatureType);
		
		
		//iterate over input, converting each line segment to a geometry
		
		try {
			String line = null;
			int lineNum = 0;
			int numSkipped = 0;
			while ((line = inReader.readLine()) != null) {
				String id = lineNum+"";
				//convert input line into a geometry
				Geometry geometry = null;
				
				try {
					line = cleanLine(line, bounds);
					geometry = voronoiLineToLineString(line, bounds);
				}
				catch (Exception e) {
					System.out.println(" skipping segment. "+line);
					numSkipped++;
					continue;
				}
				geometry.setSRID(bboxSrid);
				
				//add the geometry to a feature
				Object[] attributeValues = new Object[] { geometry };
				SimpleFeature feature = featureBuilder.buildFeature(id, attributeValues);
				
				//add feature to feature collection
				voronoiEdgesFeatureCollection.add(feature);
				
				lineNum++;
			}
			inReader.close();
			
			//write voronoi edges to output
			SimpleFeatureCollection simpleCollection = DataUtilities.simple(voronoiEdgesFeatureCollection);
			FeatureEntry voronoiEdgesEntry = new FeatureEntry();
			voronoiEdgesEntry.setSrid(bboxSrid);
			voronoiEdgesEntry.setBounds(simpleCollection.getBounds());
			
			System.out.println(numSkipped + " skipped");
            System.out.println("Saving "+GEOPKG_VORONOI_EDGES_TABLE+"...");
            System.out.println(" - Writing "+simpleCollection.size()+" features");
            outGeoPackage.add(voronoiEdgesEntry, simpleCollection);
            System.out.println(" - Done");
            System.out.println("Adding spatial index on "+GEOPKG_VORONOI_EDGES_TABLE+"...");
            outGeoPackage.createSpatialIndex(voronoiEdgesEntry);
            System.out.println(" - Done");	  
            
            
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		
		System.out.print("All Done");
	}
	
	private static final LineString voronoiLineToLineString(String line, Envelope bounds) {
		String[] pieces = line.split(" ");
		double UNSET = -1;
		double prevVal = UNSET;
		List<Coordinate> coords = new ArrayList<Coordinate>();
		for (String piece : pieces ) { 
			double val = Double.parseDouble(piece);
			
			if (prevVal != UNSET) {				
				Coordinate coord = new Coordinate(prevVal, val);
				if (!bounds.covers(coord.x, coord.y)) {
					throw new IllegalStateException("out of bounds: "+coord);
				}
				coords.add(coord);
				prevVal = UNSET;
			}
			else {
				prevVal = val;
			}
		}
		
		Coordinate[] coordArr = new Coordinate[] {};
		coordArr = coords.toArray(coordArr);
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		LineString lineString = geometryFactory.createLineString(coordArr);
		
		return lineString;
	}
	
	/*
	 * Identifies and fixes problems like this:
	 * 	  508785.7900311749131.570468
	 *                 ^
	 *                Should be a space here
	 */
	private static final String cleanLine(String line, Envelope bounds) {
		String cleanedLine = "";
		String[] pieces = line.split(" ");
		int precision = 8;
		for (String piece : pieces) {
				
			//if a piece contains two decimal points then it is invalid and must be fixed
			// e.g. invalid piece "508785.712900311749131.57043468" should be two pieces: 
			//        "508785.71290031" and "1749131.57043468"
			int firstDot = piece.indexOf(".");
			int endOfFirstVal = firstDot + 1 + precision;
			boolean isTwoPieces = piece.length() > endOfFirstVal;
			if (isTwoPieces) {
				String part1 = piece.substring(0, endOfFirstVal);
				String part2 = piece.substring(endOfFirstVal);
				piece = part1 + " " + part2;	
			}
			cleanedLine += piece + " ";			
		}
		cleanedLine = cleanedLine.trim();
		
		/*
		if (!cleanedLine.equals(line)) {
			System.out.println("original line: "+line);
			System.out.println(" cleaned line: "+cleanedLine);
		}
		*/
		return cleanedLine;
	}
	
	private static final boolean isOutOfBounds(double val, Envelope bounds) {
		boolean outOfBounds = (val < Math.min(bounds.getMinX(), bounds.getMinY()) || val > Math.max(bounds.getMaxX(), bounds.getMaxY()));
		return outOfBounds;
	}

}
