package ca.bc.gov.catchments.utils;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class SpatialUtils {

	public static SimpleFeature copyFeature(SimpleFeature feature, SimpleFeatureType newFeatureType) {
		SimpleFeatureBuilder fb = new SimpleFeatureBuilder(newFeatureType);
		Geometry geometry = (Geometry)feature.getDefaultGeometry();
		Object[] attributeValues = new Object[] { geometry };
		SimpleFeature copiedFeature = fb.buildFeature(feature.getID(), attributeValues);
		return copiedFeature;
	}
}
