# catchment delineation pipeline

This is a python script which runs various tools from catchment-delineation-helpers
and also the voronoi-catchments tool.  The output of each tool is fed into the input 
of the next tool.  The specific set of tools to run is controlled via a run 
configuration file.

## Setup

* Rename settings.example.json to settings.json
* Open settings.json in a text editor and set values for the following properties:

```
{
  "out_base_dir": "path where output files should be saved",
  "voronoi_catchment_path": "path to voronoi-catchments.exe",
  "java_path": "path to java.exe",
  "java_classpath": "classpath of the catchment-delineation-helper tools and all of their dependencies.  suggest copying from eclipse because the list of jar files is very long"
}
```

* Open run-config.example.json in a text editor and set values for the following properties

```
{
  "test_id": "example",
  "description": "example",
  "input": {
    "water_feature_file": "path to geopackage containing tables representing the input water features",
    "tables": "list of tables from water_feature_files to process",
    "data_bbox": "bounding box to process as minx,miny,maxx,maxy",
    "data_bbox_crs": "coordinate reference system that the bounding box is specified in.  e..g EPSG:3005",
    "whitelist": "a list of edge codes to include (may be null).  In the form of [ATTRIBUTE_NAME]:[CSV_LIST_OF_CODES].  e.g. EDGE_TYPE:1000,1050,1100,1150,1425,1500,1525,1550,1600,1800,1825,1850,1875,1900,1925,1950,1975,2000,2100,2300",
    "blacklist": "a list of edge codes to exclude (may be null).  Form is the same as whitelist"
  },
  "options": {
    "simplify": true,
    "simplify_dist_tolerance": 2,
    "densify": true,
    "densify_dist_spacing": 30,
    "snap": true,
    "snap_precision_scale": 10,
    "voronoi_config_num": 5
  }
}
```

## Run

python catchment_delineation_pipeline -run-config run-config.example.json