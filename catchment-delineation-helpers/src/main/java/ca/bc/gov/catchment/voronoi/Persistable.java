package ca.bc.gov.catchment.voronoi;
import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureCollection;

public interface Persistable {
	public void persist(SimpleFeatureCollection fc) throws IOException;
	public SimpleFeatureCollection getFeatureCollection() throws IOException;
}
