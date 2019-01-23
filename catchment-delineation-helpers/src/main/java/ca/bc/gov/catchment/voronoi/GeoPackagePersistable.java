package ca.bc.gov.catchment.voronoi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;

import ca.bc.gov.catchments.utils.SaveUtils;

public class GeoPackagePersistable implements Persistable {

	
	private static final String GEOPKG_ID = "geopkg";
	
	private String filename;
	private String featureTypeName;
	public GeoPackagePersistable(String filename, String featureTypeName) {
		this.filename = filename;
		this.featureTypeName = featureTypeName;
	}
	
	@Override
	public void persist(SimpleFeatureCollection fc) throws IOException {
		SaveUtils.saveToGeoPackage(filename, fc, true);
	}

	@Override
	public SimpleFeatureCollection getFeatureCollection() throws IOException {
		Map<String, String> params = new HashMap<String, String>();
		params.put("dbtype", GEOPKG_ID);
		params.put("database", filename);
		
		DataStore ds = DataStoreFinder.getDataStore(params);
		SimpleFeatureSource fs = ds.getFeatureSource(featureTypeName);
		SimpleFeatureCollection fc = fs.getFeatures();
		return fc;
	}
	
}