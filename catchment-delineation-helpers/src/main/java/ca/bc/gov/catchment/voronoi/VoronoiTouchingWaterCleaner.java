package ca.bc.gov.catchment.voronoi;

import java.io.IOException;
import java.util.Date;

import javax.measure.Unit;

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
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class VoronoiTouchingWaterCleaner {
	
	private static final double TOUCHES_DISTANCE_TOLERANCE = 0.0001; 
	private static final double MAX_LENGTH_TO_KEEP_IN_VORONOI_UNITS = 20000;
	private static final double MIN_LENGTH_TO_KEEP_IN_VORONOI_UNITS = 0.01; //1 cm
	private static final int NUM_X_TILES = 10;
	private static final int NUM_Y_TILES = 10;
	
	private String keptTypeName;
	private String discardedTypeName;
	
	private SimpleFeatureSource voronoiEdgesFeatureSource;
	private SimpleFeatureSource waterFeatureSource;
	private CoordinateReferenceSystem voronoiEdgesCrs;
	
	SimpleFeatureType voronoiEdgesFeatureType;
	SimpleFeatureType waterFeaturesType;
	
	private SimpleFeatureType keptFeatureType;
	private SimpleFeatureType discardedFeatureType;
	private SimpleFeatureBuilder keptFeatureBuilder;
	private SimpleFeatureBuilder discardedFeatureBuilder;
	
	private Unit<?> distanceUnit;
	
	public VoronoiTouchingWaterCleaner(SimpleFeatureSource voronoiEdgesFeatureSource, 
			SimpleFeatureSource waterFeatureSource,
			String keptTypeName,
			String discardedTypeName) throws IOException, FactoryException {
		
		this.voronoiEdgesFeatureSource = voronoiEdgesFeatureSource;

		//add a spatial index to the water features
		SpatialIndexFeatureCollection wfc = new SpatialIndexFeatureCollection(waterFeatureSource.getFeatures());
		this.waterFeatureSource = new SpatialIndexFeatureSource(wfc);	
		
		this.voronoiEdgesFeatureType = voronoiEdgesFeatureSource.getSchema();
		this.waterFeaturesType = waterFeatureSource.getSchema();
		
		this.keptTypeName = keptTypeName;
		this.discardedTypeName = discardedTypeName;
		
		voronoiEdgesCrs = voronoiEdgesFeatureType.getGeometryDescriptor().getCoordinateReferenceSystem();
		this.distanceUnit = voronoiEdgesCrs.getCoordinateSystem().getAxis(0).getUnit();
	
		int srid = CRS.lookupEpsgCode(voronoiEdgesCrs, true);
		
		keptFeatureType = null;
		try {
			keptFeatureType = DataUtilities.createType(keptTypeName, "geometry:LineString:srid="+srid+",num_touch:int");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+keptTypeName);
			System.exit(1);
		}
		
		discardedFeatureType = null;
		try {
			discardedFeatureType = DataUtilities.createType(discardedTypeName, "geometry:LineString:srid="+srid+",num_touch:int");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+discardedTypeName);
			System.exit(1);
		}
		
		keptFeatureBuilder = new SimpleFeatureBuilder(keptFeatureType);
		discardedFeatureBuilder = new SimpleFeatureBuilder(discardedFeatureType);
		
		System.out.println("   - Distance tolerance for 'touching' lines is: "+TOUCHES_DISTANCE_TOLERANCE + " " +distanceUnit.toString());
		
	}
	
	public void clean(Persistable kept, Persistable discarded) throws IOException, FactoryException {
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		String geometryPropertyName = voronoiEdgesFeatureType.getGeometryDescriptor().getLocalName();
		ReferencedEnvelope bounds = waterFeatureSource.getBounds();
		bounds.expandBy(10000); //expand 10km
		double tileWidth = bounds.getWidth() / NUM_X_TILES;
		double tileHeight = bounds.getHeight() / NUM_Y_TILES;
				
		int tileNum = 1;
		int totalNumKept = 0;
		int totalNumDiscarded = 0;
		Date t0 = new Date();
		for (int i = 0; i < NUM_X_TILES; i++) {
			for(int j = 0; j < NUM_Y_TILES; j++) {
				Date t1 = new Date();
				System.out.println("   - Starting tile "+tileNum+"/"+(NUM_X_TILES*NUM_Y_TILES));
				ReferencedEnvelope bbox = new ReferencedEnvelope(
						bounds.getMinX()+tileWidth*i,						
						bounds.getMinX()+tileWidth*(i+1), 
						bounds.getMinY()+tileHeight*j, 
						bounds.getMinY()+tileHeight*(j+1), 
						voronoiEdgesCrs);
				Filter tileFilter = ff.bbox(ff.property(geometryPropertyName), bbox);
				SimpleFeatureCollection voronoiEdgesInTile = voronoiEdgesFeatureSource.getFeatures(tileFilter);
				SpatialIndexFeatureCollection fastFeatureCollection = new SpatialIndexFeatureCollection(voronoiEdgesInTile);
				System.out.println("     - "+fastFeatureCollection.size()+" voronoi edges in tile");
				
				//clean the features only in the given tile
				KeptAndDiscarded result = cleanFeatures(fastFeatureCollection);
				totalNumKept += result.getNumKept();
				totalNumDiscarded += result.getNumDiscarded();
				System.out.println("     - Kept: "+result.getNumKept() + ", discarded: "+ result.getNumDiscarded());
				
				System.out.print("     - Saving...");
				kept.persist(result.getKept());
				discarded.persist(result.getDiscarded());
				result.dispose();
				System.out.println("done");
				
				Date t2 = new Date();
				long elapsed = Math.round((t2.getTime()-t1.getTime())/1000.0);
				if (elapsed > 0) {
					System.out.println("     - Tile elapsed time: "+elapsed+" s ("+Math.round(voronoiEdgesInTile.size()/elapsed*1.0)+" features/s)");
				}
				
				System.out.println("   - Progress so far:");
				int totalNumProcessed = totalNumKept+totalNumDiscarded;
				System.out.println("     - Total voronoi edges processed: "+totalNumProcessed);
				System.out.println("     - Total kept: "+totalNumKept + ", total discarded: "+ totalNumDiscarded);

				long totalElapsed = Math.round((t2.getTime() - t0.getTime())/1000.0);
				System.out.println("     - Total elapsed time: "+totalElapsed+" s" );
				if (totalElapsed > 0) {
					System.out.println("     - Average speed: " +Math.round(totalNumProcessed / totalElapsed*1.0f)+" features/s");
				}
				
				tileNum++;
			}
		}
		
	}
	
	public KeptAndDiscarded cleanFeatures(SimpleFeatureCollection voronoiEdges) throws IOException, FactoryException {

		//setup
		//---------------------------------------------------------------------
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		String waterFeaturesGeometryPropertyName = waterFeatureSource.getSchema().getGeometryDescriptor().getLocalName();
		
				
		KeptAndDiscarded result = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);
		
		
		//do work
		//---------------------------------------------------------------------
		
		//iterate over each voronoi edge, and decide whether to keep or discard it based on 
		//  these rules:
		// 1. IF the voronoi edge is a 2-point segment and very long (> MAX_LENGTH_TO_KEEP_IN_VORONOI_UNITS) then discard 
		// 2. IF the voronoi edge wasn't discarded by rule 1 above, then :
		//    IF the voronoi edge touches exactly 1 water feature, discard it
		// 3. IF not discarded by either rule above, keep the feature
		
		
		
		int shortCircuitCount = 0;
		//FeatureCollection voronoiEdges = voronoiEdgesFeatureSource.getFeatures();
		FeatureIterator<SimpleFeature> iterator = voronoiEdges.features();
		int index = 0;
		while(iterator.hasNext()) {
			index++;
			SimpleFeature voronoiEdgeFeature = iterator.next();
			Geometry voronoiEdgeGeometry = (Geometry)voronoiEdgeFeature.getDefaultGeometry();
			
			double lengthInMapUnit = voronoiEdgeGeometry.getLength();
			boolean isLongTwoPointSegment = lengthInMapUnit > MAX_LENGTH_TO_KEEP_IN_VORONOI_UNITS;
			boolean isShortSegment = lengthInMapUnit < MIN_LENGTH_TO_KEEP_IN_VORONOI_UNITS;
			boolean keep = !isLongTwoPointSegment && !isShortSegment;

			Integer numTouchingWaterFeatures = -1;
			if (keep) {
				//Check if voronoi edge touches any water features.
				//  We will use the number of touches to determine whether to keep or discard. 
				//  The voronoi edges that we want to remove don't always touch *exactly* to the verticies of
				//  water features.  They sometimes do (i.e. distance = 0), but sometimes there is a precision issue
				//  whereby the distance between features that are intended to touch is actually about 10^-10 metres.
				//  Therefore, we cannot reliably use the JTS "touches" operation to detect these approximate touches. 
				//  Instead we use "dwithin" to identify features whose closest vertex is within some
				//  small distance tolerance.
				Filter waterTouchesVoronoiEdgeFilter = filterFactory.dwithin(filterFactory.property(waterFeaturesGeometryPropertyName), filterFactory.literal(voronoiEdgeGeometry), TOUCHES_DISTANCE_TOLERANCE, distanceUnit.toString());
				FeatureCollection touchingWaterFeatures = waterFeatureSource.getFeatures(waterTouchesVoronoiEdgeFilter);
				numTouchingWaterFeatures = touchingWaterFeatures.size();
				keep = numTouchingWaterFeatures == 0 || numTouchingWaterFeatures >= 3; //discard when num is 1 or 2 //numTouchingWaterFeatures != 1;
			}
			else {
				shortCircuitCount++;
			}
			
			if (keep) {
				Object[] attributeValues = new Object[] { voronoiEdgeGeometry, numTouchingWaterFeatures};
				SimpleFeature keptFeature = keptFeatureBuilder.buildFeature(voronoiEdgeFeature.getID(), attributeValues);
				result.addKept(keptFeature);
			}
			else {
				Object[] attributeValues = new Object[] { voronoiEdgeGeometry, numTouchingWaterFeatures };
				SimpleFeature discardedFeature = discardedFeatureBuilder.buildFeature(voronoiEdgeFeature.getID(), attributeValues);
				result.addDiscarded(discardedFeature);
			}
			
			
		} //while
		iterator.close();
		
		return result;
	}
	
}
