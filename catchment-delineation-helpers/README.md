# catchment-delineation-helpers

This is a collection of Java-based tools which help to transfrom a) input water feature
data into a form that can be processed by voronoi-catchments and b) output from 
voronoi-catchments into a standard geospatial data format (GeoPackage).

The tools are:

* SimplifyThenDensity: Opens water feature data (in GeoPackage format) and removes or adds vertices
  to the lines based on simplification and densification rules defined as input parameters.
* SnapToGrid.java: Opens water feature data (in GeosPackage format) snaps each vertex to a precision
  grid defined by an input parameter.
* PrepCgalVoronoiInput: Converts water feature data (in GeoPackage format) into a format that can be 
  loaded into voronoi-catchments.  Also provides some options to include/exclude features based on
  bounding box and edge codes.
* CheckCrosses: Checks an input geospatial data set for features that "cross".  This useful to run on
  data before inputting it into voronoi-catchments.
* WKTList2GeoPackage: Converts the Well-known text output from voronoi-catchments into a GeoPackage file
* CleanVoronoiOutput: Removes unwanted "construction edges" from the voronoi-catchments output, leaving
  only edges that correspond to boundaries of voronoi cells

## Install dependencies

All project dependencies are managed by maven.

## Open in Eclipse

1. Open Eclipse with an empty workspace.

  e.g. C:\Users\<me>\eclipse-workspaces\catchment-delineation

2. Import the project

```
File > Import > Maven > Existing Maven Projects
```

Set "Root Directory" to the folder that contains this project's pom.xml.

Eclipse will generate some files in the root directory 
(.project, .classpath, .settings).

3. Build the project from eclipse