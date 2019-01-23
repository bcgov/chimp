package ca.bc.gov.catchment.voronoi;

import java.io.IOException;

import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.feature.simple.SimpleFeatureType;

public class SpatialIndexFeatureCollection2 extends SpatialIndexFeatureCollection {

	public SpatialIndexFeatureCollection2(SimpleFeatureType schema, int nodeCapacity) {
        this.index = new STRtree(nodeCapacity);
        this.schema = schema;
    }
	
    public SpatialIndexFeatureCollection2(SimpleFeatureCollection copy, int nodeCapacity) throws IOException {
        this(copy.getSchema(), nodeCapacity);
        addAll(copy);
    }
}
