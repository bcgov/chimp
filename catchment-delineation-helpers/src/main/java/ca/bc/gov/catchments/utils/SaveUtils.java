package ca.bc.gov.catchments.utils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.feature.FeatureCollection;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class SaveUtils {

	private static final String GEOPKG_ID = "geopkg";
	
	public static GeoPackage openGeoPackage(String filename) throws IOException {
		File outFile = new File(filename);
		boolean exists = outFile.exists();
		
		Map<String, String> outputDatastoreParams = new HashMap<String, String>();
		outputDatastoreParams.put("dbtype", GEOPKG_ID);
		outputDatastoreParams.put("database", filename);
		
		GeoPackage outGeoPackage = null;
		outGeoPackage = new GeoPackage(outFile);
		if (!exists) {
			outGeoPackage.init();
		}
		return outGeoPackage;
	}
	
	public static void saveToGeoPackage(
			String filename, 
			SimpleFeatureCollection fc) throws IOException {
		saveToGeoPackage(filename, fc, false);
	}

	public static void appendToGeoPackage(GeoPackage gp, FeatureEntry entry, SimpleFeatureCollection fc) throws IOException {
		Transaction tx = new DefaultTransaction();
        try {
            SimpleFeatureWriter w = gp.writer(entry, true, null, tx);
            SimpleFeatureIterator it = fc.features();
            try {
                while (it.hasNext()) {
                    SimpleFeature f = it.next();
                    SimpleFeature g = w.next();
                    for (PropertyDescriptor pd : fc.getSchema().getDescriptors()) {
                        /* geopkg spec requires booleans to be stored as SQLite integers this fixes
                         * bug reported by GEOT-5904 */
                        String name = pd.getName().getLocalPart();
                        if (pd.getType().getBinding() == Boolean.class) {
                            int bool = 0;
                            if (f.getAttribute(name) != null) {
                                bool = (Boolean) (f.getAttribute(name)) ? 1 : 0;
                            }
                            g.setAttribute(name, bool);
                        } else {
                            g.setAttribute(name, f.getAttribute(name));
                        }
                    }
                    w.write();
                }
            } finally {
                w.close();
                it.close();
            }
            tx.commit();
        } catch (Exception ex) {
            tx.rollback();
            throw new IOException(ex);
        } finally {
            tx.close();
        }
	}
	
	public static void saveToGeoPackage(
			String filename, 
			SimpleFeatureCollection fc,
			boolean append) throws IOException {
		
		CoordinateReferenceSystem crs = fc.getSchema().getGeometryDescriptor().getCoordinateReferenceSystem();
		if (crs == null) {
			throw new IllegalArgumentException("Unable to determine CRS of input feature collection");
		}
		int srid;
		try {
			srid = CRS.lookupEpsgCode(crs, true);
		} catch (FactoryException e) {
			throw new IllegalStateException("Unable to determine SRID of input feature collection");
		}
		
		GeoPackage gp = openGeoPackage(filename);

		FeatureEntry entry = gp.feature(fc.getSchema().getTypeName());

		if (entry == null) { //create
			entry = new FeatureEntry();
			entry.setSrid(srid);
			entry.setBounds(fc.getBounds());
			entry.setTableName(fc.getSchema().getTypeName());
			gp.add(entry, fc);
		} 
		else { //append
			appendToGeoPackage(gp, entry, fc);
		}
		
		if (!gp.hasSpatialIndex(entry)) {
			gp.createSpatialIndex(entry);
		}
		
		gp.close();
	}
	
	
	
}
