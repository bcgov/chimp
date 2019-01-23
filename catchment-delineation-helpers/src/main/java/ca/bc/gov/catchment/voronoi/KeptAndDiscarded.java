package ca.bc.gov.catchment.voronoi;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class KeptAndDiscarded {

	private DefaultFeatureCollection keptVoronoiEdgesFeatureCollection;
	private DefaultFeatureCollection discardedVoronoiEdgesFeatureCollection;
	
	public KeptAndDiscarded(SimpleFeatureType keptFeatureType, SimpleFeatureType discardedFeatureType) {
		keptVoronoiEdgesFeatureCollection = new DefaultFeatureCollection(keptFeatureType.getTypeName(), keptFeatureType);
		discardedVoronoiEdgesFeatureCollection  = new DefaultFeatureCollection(discardedFeatureType.getTypeName(), discardedFeatureType);
	}
	
	public SimpleFeatureCollection getKept() {
		return keptVoronoiEdgesFeatureCollection;
	}
	
	public void addKept(SimpleFeature f) {
		keptVoronoiEdgesFeatureCollection.add(f);
	}
	
	public void addKept(SimpleFeatureCollection fc) {
		keptVoronoiEdgesFeatureCollection.addAll(fc);
	}
	
	public int getNumKept() {
		return keptVoronoiEdgesFeatureCollection.size();
	}
	
	public SimpleFeatureCollection getDiscarded() {
		return discardedVoronoiEdgesFeatureCollection;
	}
	
	public void addDiscarded(SimpleFeature f) {
		discardedVoronoiEdgesFeatureCollection.add(f);
	}
	
	public void addDiscarded(SimpleFeatureCollection fc) {
		discardedVoronoiEdgesFeatureCollection.addAll(fc);
	}
	
	public int getNumDiscarded() {
		return discardedVoronoiEdgesFeatureCollection.size();
	}
	
	public void dispose() {
		keptVoronoiEdgesFeatureCollection.clear();
		discardedVoronoiEdgesFeatureCollection.clear();
	}
}
