# chimp

This project provides a suite of tools to support catchment delineation:

1. voronoi-catchments

This is a C++ application which creates a first approximation of catchment 
boundaries by applying a line Voronoi algorithm to water feature line segments.

2. catchment-delineation-helpers

This is a collection of Java-based tools which help to transfrom a) input water feature
data into a form that can be processed by voronoi-catchments and b) output from 
voronoi-catchments into a standard geospatial data format (GeoPackage).

3. pipeline

This is a python script which runs various tools from catchment-delineation-helpers
and also the voronoi-catchments tool.  The output of each tool is fed into the input 
of the next tool.  The specific set of tools to run is controlled via a run 
configuration file.

See the README files under each of the above project folders for more information.