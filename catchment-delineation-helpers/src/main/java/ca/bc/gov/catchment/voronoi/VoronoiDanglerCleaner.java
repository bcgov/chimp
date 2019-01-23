package ca.bc.gov.catchment.voronoi;

import java.io.IOException;
import java.util.Date;

import javax.measure.Unit;

import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.collection.SpatialIndexFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class VoronoiDanglerCleaner {
	
	private static final double TOUCHES_DISTANCE_TOLERANCE = 0.5; 
	private static final int NUM_X_TILES = 10;
	private static final int NUM_Y_TILES = 10;
	
	private String voronoiEdgesTypeName;
	private String keptTypeName;
	private String discardedTypeName;
	
	private SimpleFeatureSource voronoiEdgesFeatureSource;
	private SimpleFeatureType voronoiEdgesFeatureType;
	private SimpleFeatureSource waterFeatureSource;
	
	private SimpleFeatureType keptFeatureType;
	private SimpleFeatureType discardedFeatureType;
	private SimpleFeatureBuilder keptFeatureBuilder;
	private SimpleFeatureBuilder discardedFeatureBuilder;
	
	private CoordinateReferenceSystem voronoiEdgesCrs;
	private Unit<?> distanceUnit;
	
	public VoronoiDanglerCleaner(SimpleFeatureSource voronoiEdgesFeatureSource,
			SimpleFeatureSource waterFeatureSource,
			String keptTypeName,
			String discardedTypeName) throws IOException, FactoryException {
		
		//SpatialIndexFeatureCollection fc = new SpatialIndexFeatureCollection(voronoiEdgesFeatureSource.getFeatures());
		this.voronoiEdgesFeatureSource = voronoiEdgesFeatureSource;
		voronoiEdgesFeatureType = voronoiEdgesFeatureSource.getSchema();
		this.waterFeatureSource = waterFeatureSource;
		
		this.voronoiEdgesTypeName = voronoiEdgesFeatureType.getTypeName();
		this.keptTypeName = keptTypeName;
		this.discardedTypeName = discardedTypeName;
		
		voronoiEdgesCrs = voronoiEdgesFeatureType.getGeometryDescriptor().getCoordinateReferenceSystem();
		this.distanceUnit = voronoiEdgesCrs.getCoordinateSystem().getAxis(0).getUnit();

		int srid = CRS.lookupEpsgCode(voronoiEdgesCrs, true);
		
		keptFeatureType = null;
		try {
			keptFeatureType = DataUtilities.createType(keptTypeName, "geometry:LineString:srid="+srid+",num_end_points_touching:int");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+keptTypeName);
			System.exit(1);
		}
		
		discardedFeatureType = null;
		try {
			discardedFeatureType = DataUtilities.createType(discardedTypeName, "geometry:LineString:srid="+srid+",num_end_points_touching:int");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+discardedTypeName);
			System.exit(1);
		}		
		
		keptFeatureBuilder = new SimpleFeatureBuilder(keptFeatureType);
		discardedFeatureBuilder = new SimpleFeatureBuilder(discardedFeatureType);
		
		System.out.println("   - Distance tolerance for 'touching' lines is: "+TOUCHES_DISTANCE_TOLERANCE + " " +distanceUnit.toString());

	}
	
	public KeptAndDiscarded clean() throws IOException {
		KeptAndDiscarded prevResult = null;
		KeptAndDiscarded latestResult = null;
		KeptAndDiscarded finalResult = null;

		SimpleFeatureCollection featureCollectionToProcess = this.voronoiEdgesFeatureSource.getFeatures();
		//keep looping on the refined data set until no more features are found to discard
		int passNum = 1;
		do {
			Date t1 = new Date();
			System.out.println("   - Pass "+passNum + " starting...");
			prevResult = latestResult;
			
			latestResult = cleanCycle(featureCollectionToProcess);
			if (finalResult == null) {
				finalResult = new KeptAndDiscarded(latestResult.getKept().getSchema(), latestResult.getDiscarded().getSchema());
			}
			finalResult.addDiscarded(latestResult.getDiscarded());
			featureCollectionToProcess = latestResult.getKept();
			
			Date t2 = new Date();
			long elapsedTime = (t2.getTime()-t1.getTime())/1000;
			int totalNumProcessed = latestResult.getNumKept() + latestResult.getNumDiscarded();
			System.out.println("     - Finished at: "+t2);
			System.out.println("       - Elapsed time: "+elapsedTime+" s");
			System.out.println("       - # processed: "+totalNumProcessed);
			System.out.println("       - # kept: "+latestResult.getNumKept());
			System.out.println("       - # discarded: "+ latestResult.getNumDiscarded());
			if (elapsedTime > 0) {
				System.out.println("       - Average rate: "+Math.round(totalNumProcessed / elapsedTime*1.0f)+" features/s");
			}
						
			passNum++;
		} while(prevResult == null || latestResult.getNumDiscarded() != 0);

		finalResult.addKept(latestResult.getKept());
		return finalResult;
	}
	
	public KeptAndDiscarded cleanCycle(SimpleFeatureCollection voronoiEdges) throws IOException {

		//setup
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		String voronoiEdgesGeometryPropertyName = voronoiEdgesFeatureType.getGeometryDescriptor().getLocalName();

		KeptAndDiscarded allResults = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);
		
		//do work
		
		SimpleFeatureCollection voronoiEdgesToIterate = voronoiEdges;
		FeatureIterator<SimpleFeature> iterator = voronoiEdgesToIterate.features();
		
		Date t1 = new Date();
		
		SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection2(voronoiEdges, 10);
		SpatialIndexFeatureSource indexedFeatureSource = new SpatialIndexFeatureSource(fastFeatureCollection);
		
		int numProcessed = 0;
		int numDuplicates = 0;
		
		int progressIncrement = 50000;
		
		Date t0 = new Date();
		while(iterator.hasNext()) {
			numProcessed++;
			SimpleFeature voronoiEdgeFeature = iterator.next();
			LineString voronoiEdgeGeometry = (LineString)voronoiEdgeFeature.getDefaultGeometry();

			//get the point at each end of the line
			Coordinate[] coordinates = voronoiEdgeGeometry.getCoordinates();
			Geometry firstPoint = geometryFactory.createPoint(coordinates[0]);
			firstPoint.setSRID(voronoiEdgeGeometry.getSRID());
			Geometry secondPoint = geometryFactory.createPoint(coordinates[coordinates.length-1]);
			secondPoint.setSRID(voronoiEdgeGeometry.getSRID());
			//System.out.println(firstPoint.toText() +"|"+secondPoint.toText()+"|"+voronoiEdgeGeometry.toText());
			
			
			Filter notSelf = filterFactory.not(filterFactory.id(filterFactory.featureId(voronoiEdgeFeature.getID())));
			Filter firstPointTouchesOtherVoronoiEdges = filterFactory.dwithin(filterFactory.property(voronoiEdgesGeometryPropertyName), filterFactory.literal(firstPoint), TOUCHES_DISTANCE_TOLERANCE, distanceUnit.toString());
			Filter firstTouchesOther = filterFactory.and(notSelf, firstPointTouchesOtherVoronoiEdges);
			
			SimpleFeatureCollection featuresTouchingFirstPoint = indexedFeatureSource.getFeatures(firstTouchesOther);
			int numEndpointsTouching = featuresTouchingFirstPoint.size() > 0 ? 1 : 0; //including self
			
			//determine whether any of the touching features are topological duplicates of the
			//current feature (i.e. geometry is an exact match)
			Filter topoMatchesFilter = filterFactory.equals(filterFactory.property(voronoiEdgesGeometryPropertyName), filterFactory.literal(voronoiEdgeGeometry));
			SimpleFeatureCollection topoMatches = featuresTouchingFirstPoint.subCollection(topoMatchesFilter);
			
			//if the current edge ("edge A)" has a duplicate ("edge B"), we only want to remove
			//one or the other. To determine which to remove use this rule: remove only the one that has
			//the larger FID (by string comparison, since FIDs are strings).
			String fid = voronoiEdgeFeature.getID();
			boolean isDuplicateToBeRemoved = topoMatches.size() > 0 && fid.compareTo(getHighestFid(topoMatches)) > 0; 
			
			if (isDuplicateToBeRemoved) {
				numDuplicates++;
			}
			
			if (numEndpointsTouching != 0) {
				Filter secondPointTouchesOtherVoronoiEdges = filterFactory.dwithin(filterFactory.property(voronoiEdgesGeometryPropertyName), filterFactory.literal(secondPoint), TOUCHES_DISTANCE_TOLERANCE, distanceUnit.toString());
				Filter secondTouchesOther = filterFactory.and(notSelf, secondPointTouchesOtherVoronoiEdges);
				SimpleFeatureCollection featuresTouchingSecondPoint = indexedFeatureSource.getFeatures(secondTouchesOther);
				featuresTouchingSecondPoint = subtract(featuresTouchingSecondPoint, featuresTouchingFirstPoint); //don't count a features as touching the second point if it also touches the first.  this means the line is very short.
				numEndpointsTouching += featuresTouchingSecondPoint.size() > 0 ? 1 : 0; //self not include
			}
			
			
			boolean discard = isDuplicateToBeRemoved || numEndpointsTouching < 2;
			boolean keep = !discard;
			
			if (keep) {
				Object[] attributeValues = new Object[] { voronoiEdgeGeometry, numEndpointsTouching};
				SimpleFeature keptFeature = keptFeatureBuilder.buildFeature(voronoiEdgeFeature.getID(), attributeValues);
				allResults.addKept(keptFeature);
			}
			else {
				Object[] attributeValues = new Object[] { voronoiEdgeGeometry, numEndpointsTouching };
				SimpleFeature discardedFeature = discardedFeatureBuilder.buildFeature(voronoiEdgeFeature.getID(), attributeValues);
				allResults.addDiscarded(discardedFeature);
			}
			
			if (numProcessed % progressIncrement == 0) {				
				Date t2 = new Date();
				String rateSummary = "";
				int secondsSinceLastSummary = Math.round((t2.getTime() - t1.getTime())/1000);
				int secondsSinceStart = Math.round((t2.getTime() - t0.getTime())/1000);
				if (secondsSinceLastSummary > 0) {
					int rate = progressIncrement / secondsSinceLastSummary;
					rateSummary = ", rate: "+rate+" features/s";
				}
				System.out.println("     - Progress: # processed: "+numProcessed+", # kept: "+allResults.getNumKept()+", # discarded:"+allResults.getNumDiscarded()+" (# dups discarded: "+numDuplicates+"), elapsed time: "+secondsSinceLastSummary+" s"+rateSummary);
				t1 = t2;
			}
			
		} //while
		iterator.close();

		return allResults;
	}
	
	public KeptAndDiscarded cleanCycleAllTiles(SimpleFeatureSource voronoiEdgesFeatureSource) throws IOException {
		
		KeptAndDiscarded allResults = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);
		
		//do work
		//---------------------------------------------------------------------
		
		//iterate over each voronoi edge, and decide whether to keep or discard it based on 
		//  these rules:
		// 1. IF the voronoi edge does not touch any other voronoi edges at one or both of its
		//    endpoints then discard
		// 2. ELSE keep
		
		
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		String geometryPropertyName = voronoiEdgesFeatureType.getGeometryDescriptor().getLocalName();
		ReferencedEnvelope bounds = waterFeatureSource.getBounds();
		bounds.expandBy(10000); //expand 10km
		double tileWidth = bounds.getWidth() / NUM_X_TILES;
		double tileHeight = bounds.getHeight() / NUM_Y_TILES;
				
		int tileNum = 1;
		for (int i = 0; i < NUM_X_TILES; i++) {
			for(int j = 0; j < NUM_Y_TILES; j++) {
				
				System.out.println("   - Starting tile "+tileNum+"/"+(NUM_X_TILES*NUM_Y_TILES));
				ReferencedEnvelope tileBbox = new ReferencedEnvelope(
						bounds.getMinX()+tileWidth*i,						
						bounds.getMinX()+tileWidth*(i+1), 
						bounds.getMinY()+tileHeight*j, 
						bounds.getMinY()+tileHeight*(j+1), 
						voronoiEdgesCrs);
				Filter tileFilter = ff.bbox(ff.property(geometryPropertyName), tileBbox);
				tileBbox.expandBy(10000);
				Filter expandedTileFilter = ff.bbox(ff.property(geometryPropertyName), tileBbox);
				SimpleFeatureCollection voronoiEdges = voronoiEdgesFeatureSource.getFeatures(tileFilter);
				
				//merge the results from this tile into the full results set
				KeptAndDiscarded tileResults = cleaningCycleOneTile(voronoiEdges, expandedTileFilter);
				allResults.addKept(tileResults.getKept());
				allResults.addDiscarded(tileResults.getDiscarded());
				
				tileNum++;
			} //for tile
		} //for tile
				
		return allResults;
	}
	
	/*
	 * TODO: delete this and modify cleanCycleAllTiles to call cleanCycleFull instead
	 */
	public KeptAndDiscarded cleaningCycleOneTile(FeatureCollection voronoiEdges, Filter searchAreaFilter) throws IOException {
		
		//setup

		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		String voronoiEdgesGeometryPropertyName = voronoiEdgesFeatureType.getGeometryDescriptor().getLocalName();

		KeptAndDiscarded allResults = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);
		
		//do work
		
		FeatureIterator<SimpleFeature> iterator = voronoiEdges.features();
		
		Date t1 = new Date();
		
		SimpleFeatureCollection featuresToIndex = voronoiEdgesFeatureSource.getFeatures(searchAreaFilter);
		SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection2(featuresToIndex, 12);
		SpatialIndexFeatureSource indexedFeatureSource = new SpatialIndexFeatureSource(fastFeatureCollection);
		
		System.out.print("      - Progress:");
		int numProcessed = 0;
		int progressIncrement = 5000;
		while(iterator.hasNext()) {
			numProcessed++;
			Date te = new Date();
			int numSpatialTestsRequiredForThisFeature = 0;
			SimpleFeature voronoiEdgeFeature = iterator.next();
			LineString voronoiEdgeGeometry = (LineString)voronoiEdgeFeature.getDefaultGeometry();


			//get the point at each end of the line
			Coordinate[] coordinates = voronoiEdgeGeometry.getCoordinates();
			Geometry firstPoint = geometryFactory.createPoint(coordinates[0]);
			firstPoint.setSRID(voronoiEdgeGeometry.getSRID());
			Geometry secondPoint = geometryFactory.createPoint(coordinates[coordinates.length-1]);
			secondPoint.setSRID(voronoiEdgeGeometry.getSRID());
			//System.out.println(firstPoint.toText() +"|"+secondPoint.toText()+"|"+voronoiEdgeGeometry.toText());
			
			
			Filter firstPointTouchesOtherVoronoiEdges = filterFactory.dwithin(filterFactory.property(voronoiEdgesGeometryPropertyName), filterFactory.literal(firstPoint), TOUCHES_DISTANCE_TOLERANCE, distanceUnit.toString());
			SimpleFeatureCollection featuresTouchingFirstPoint = indexedFeatureSource.getFeatures(firstPointTouchesOtherVoronoiEdges);
			int numEndpointsTouching = featuresTouchingFirstPoint.size() > 1 ? 1 : 0; //including self
			
			if (numEndpointsTouching != 0) {
				Filter secondPointTouchesOtherVoronoiEdges = filterFactory.dwithin(filterFactory.property(voronoiEdgesGeometryPropertyName), filterFactory.literal(secondPoint), TOUCHES_DISTANCE_TOLERANCE, distanceUnit.toString());
				SimpleFeatureCollection featuresTouchingSecondPoint = indexedFeatureSource.getFeatures(secondPointTouchesOtherVoronoiEdges);
				featuresTouchingSecondPoint = subtract(featuresTouchingSecondPoint, featuresTouchingFirstPoint);
				numEndpointsTouching += featuresTouchingSecondPoint.size() > 0 ? 1 : 0; //self not include
			}
			
			
			boolean discard = numEndpointsTouching < 2;
			boolean keep = !discard;
			
			if (keep) {
				Object[] attributeValues = new Object[] { voronoiEdgeGeometry, numEndpointsTouching};
				SimpleFeature keptFeature = keptFeatureBuilder.buildFeature(voronoiEdgeFeature.getID(), attributeValues);
				allResults.addKept(keptFeature);
			}
			else {
				Object[] attributeValues = new Object[] { voronoiEdgeGeometry, numEndpointsTouching };
				SimpleFeature discardedFeature = discardedFeatureBuilder.buildFeature(voronoiEdgeFeature.getID(), attributeValues);
				allResults.addDiscarded(discardedFeature);
			}
			
			if (numProcessed % progressIncrement == 0) {
				System.out.print(".");
			}
			
		} //while
		System.out.println("");
		iterator.close();

		return allResults;
	}
	
	/**
	 * checks whether the geometry associated with the given feature exists in the
	 * set of features
	 */
	String getHighestFid(SimpleFeatureCollection features) {
		String highestFid = null;
		SimpleFeatureIterator it = features.features();
		while (it.hasNext()) {
			SimpleFeature f = it.next();
			String thisId = f.getID();
			if (thisId == null) {
				continue;
			}
			if (highestFid == null || thisId.compareTo(highestFid) > 0) {
				highestFid = thisId;
			}
		}
		return highestFid;
	}
	
	/**
	 * returns A - B
	 * @param featuresTouchingSecondPoint
	 * @param featuresTouchingFirstPoint
	 * @return
	 */
	private SimpleFeatureCollection subtract(SimpleFeatureCollection A, SimpleFeatureCollection B) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator aIt = A.features();
		while (aIt.hasNext()) {
			SimpleFeature a = aIt.next();
			if (!contains(B, a)) {
				result.add(a);
			}
		}
		if (result.size() != A.size()) {
			//System.out.println("segment not counted twice");
		}
		aIt.close();
		return result;
	}
	
	/**
	 * checks whether the feature collection has any object with the same ID as the given simplefeature
	 * @param A
	 * @param b
	 * @return
	 */
	private boolean contains(SimpleFeatureCollection A, SimpleFeature b) {
		SimpleFeatureIterator aIt = A.features();
		while (aIt.hasNext()) {
			SimpleFeature a = aIt.next();
			if (a.getID() == b.getID()) {
				return true;
			}
		}
		aIt.close();
		return false;
	}
	
}
