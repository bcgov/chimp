package ca.bc.gov.catchments.utils;
import java.util.ArrayList;
import java.util.List;

import org.geotools.factory.CommonFactoryFinder;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

public class FilterUtils {

	/**
	 * builds a composite filter of multiple EQUALS filters, all of which are OR'd together
	 * 	e.g. filter where:
	 * 	property = a OR property = b OR property = c OR...
	 * @param propertyName
	 * @param values
	 * @return
	 */
	public static Filter orEquals(String propertyName, String[] values) {
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		
			List<Filter> equalFilters = new ArrayList<Filter>();
			for (String value : values) {
				Filter equalFilter = filterFactory.equals(filterFactory.property(propertyName), filterFactory.literal(value));
				equalFilters.add(equalFilter);
			}
			Filter orFilter = filterFactory.or(equalFilters);
			return orFilter;
		
	}
	
	/**
	 * builds a composite filter of multiple NOT EQUALS filters, all of which are AND'd together
	 * 	e.g. filter where:
	 * 	property != a AND property = !b AND property != c AND...
	 * @param propertyName
	 * @param values
	 * @return
	 */
	public static Filter andNotEquals(String propertyName, String[] values) {
		
		FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
		
			List<Filter> notEqualFilters = new ArrayList<Filter>();
			for (String value : values) {
				Filter notEqualFilter = filterFactory.notEqual(filterFactory.property(propertyName), filterFactory.literal(value));
				notEqualFilters.add(notEqualFilter);
			}
			Filter andFilter = filterFactory.and(notEqualFilters);
			return andFilter;
		
	}
	
	/**
	 * Returns the AND of two filters, but if either is null, just returns the other which is non-null.
	 * @param f1
	 * @param f2
	 * @return
	 */
	public static Filter and(Filter f1, Filter f2) {
		if (f1 != null && f2 != null) {
			FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();
			return filterFactory.and(f1, f2);
		}
		else if (f1 == null && f2 != null) {
			return f2;
		}
		else if (f1 != null && f2 == null) {
			return f1;
		}
		return null;
	}
	
	/**
	 * Parses the filter property name from a string of the form:
	 * 	"[property]:val1,val2,val3,..."
	 * @param s
	 * @return
	 */
	public static String parseFilterPropertyName(String s) {
		int a = s.indexOf(":");
		if (a == -1) {
			throw new IllegalArgumentException("unknown filter format.  expecting [property]:val1,val2,val3,...");
		}
		String property = s.substring(0, a);
		return property;
	}
	
	/**
	 * Parses the filter property values from a string of the form:
	 * 	"[property]:val1,val2,val3,..."
	 * @param s
	 * @return
	 */
	public static String[] parseFilterPropertyValues(String s) {
		int a = s.indexOf(":");
		if (a == -1) {
			throw new IllegalArgumentException("unknown filter format.  expecting [property]:val1,val2,val3,...");
		}
		String valuesCsv = s.substring(a+1);
		String[] values = valuesCsv.split(",");
		return values;
	}
}
