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

public class VoronoiLongLineCleaner {
	
 
	private static final double MAX_LENGTH_TO_KEEP_IN_VORONOI_UNITS = 20000;

	
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
	
	public VoronoiLongLineCleaner(SimpleFeatureSource voronoiEdgesFeatureSource, 
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
			keptFeatureType = DataUtilities.createType(keptTypeName, "geometry:LineString:srid="+srid+",length:int,length_unit:String");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+keptTypeName);
			System.exit(1);
		}
		
		discardedFeatureType = null;
		try {
			discardedFeatureType = DataUtilities.createType(discardedTypeName, "geometry:LineString:srid="+srid+",length:int,length_unit:String");
		} catch (SchemaException e1) {
			System.out.println("Unable to create feature type "+discardedTypeName);
			System.exit(1);
		}
		
		keptFeatureBuilder = new SimpleFeatureBuilder(keptFeatureType);
		discardedFeatureBuilder = new SimpleFeatureBuilder(discardedFeatureType);
		
	}
	
	public KeptAndDiscarded clean() throws IOException, FactoryException {
		KeptAndDiscarded result = new KeptAndDiscarded(keptFeatureType, discardedFeatureType);
		int progressIncrement = 50000;
		
		SimpleFeatureCollection voronoiEdges = voronoiEdgesFeatureSource.getFeatures();
		SimpleFeatureIterator iterator = voronoiEdges.features();
		int index = 0;
		while(iterator.hasNext()) {
			SimpleFeature feature = iterator.next();
			Geometry geometry = (Geometry)feature.getDefaultGeometry();
			boolean discard = geometry.getLength() > MAX_LENGTH_TO_KEEP_IN_VORONOI_UNITS;
			if (discard) {
				Object[] attributeValues = new Object[] { geometry, geometry.getLength(), distanceUnit.toString() };
				SimpleFeature discardedFeature = discardedFeatureBuilder.buildFeature(feature.getID(), attributeValues);
				result.addDiscarded(discardedFeature);
			}
			else {
				Object[] attributeValues = new Object[] { geometry, geometry.getLength(), distanceUnit.toString() };
				SimpleFeature keptFeature = keptFeatureBuilder.buildFeature(feature.getID(), attributeValues);
				result.addKept(keptFeature);
			}
			if (index % progressIncrement == 0) {
				System.out.println("   - # processed: "+index+", # kept: "+result.getNumKept()+", # discarded: "+result.getNumDiscarded());
			}
			index++;
		}
		iterator.close();
		return result;
	}
	
	
}
