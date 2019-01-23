#include "pch.h"
#include "Voronoi_1.h"

//standard includes
#include <iostream>
#include <fstream>
#include <cassert>
#include <string>
#include <chrono>
/*
// define the kernel
#include <CGAL/Interval_nt.h>
#include <CGAL/Simple_cartesian.h>
#include <CGAL/Filtered_kernel.h>
//#include <CGAL/Exact_predicates_exact_constructions_kernel.h>
#include <CGAL/Exact_predicates_exact_constructions_kernel.h>

typedef CGAL::Field_with_sqrt_tag		CM;
typedef CGAL::Field_with_sqrt_tag		FM;
typedef CGAL::Field_tag					EM;
typedef CGAL::Simple_cartesian<double>    CK;
//typedef CGAL::Simple_cartesian<double>		FK;
typedef CGAL::Simple_cartesian<Interval_nt<false> >  FK;
typedef CGAL::Exact_predicates_exact_constructions_kernel EK;
typedef CGAL::Filtered_kernel<CK>         K;
// typedefs for the traits and the algorithm
//#include <CGAL/Segment_Delaunay_graph_filtered_traits_2.h>
#include <CGAL/Segment_Delaunay_graph_filtered_traits_2.h>
#include <CGAL/Segment_Delaunay_graph_2.h>
typedef CGAL::Segment_Delaunay_graph_filtered_traits_2<EK>  Gt;
typedef CGAL::Segment_Delaunay_graph_2<Gt>             SDG2;

*/
#include <CGAL/Simple_cartesian.h>
#include <CGAL/Exact_predicates_exact_constructions_kernel.h>
#include <CGAL/Segment_Delaunay_graph_filtered_traits_2.h>
//#include <CGAL/Segment_Delaunay_graph_Linf_filtered_traits_2.h>
//#include <CGAL/Segment_Delaunay_graph_Linf_2.h>
#include <CGAL/Segment_Delaunay_graph_2.h>
#include <CGAL/Voronoi_diagram_2.h>
#include <CGAL/Segment_Delaunay_graph_adaptation_traits_2.h>
#include <CGAL/Segment_Delaunay_graph_adaptation_policies_2.h>
//typedef CGAL::Simple_cartesian<CGAL::Gmpq>			K;
typedef CGAL::Simple_cartesian<double>    K;
//typedef CGAL::Exact_predicates_exact_constructions_kernel K;
typedef CGAL::Segment_Delaunay_graph_filtered_traits_2<K>               Gt;
typedef CGAL::Segment_Delaunay_graph_2<Gt>                              DT;
typedef CGAL::Segment_Delaunay_graph_adaptation_traits_2<DT>                 AT;
typedef CGAL::Segment_Delaunay_graph_degeneracy_removal_policy_2<DT>         AP;
typedef CGAL::Voronoi_diagram_2<DT, AT, AP>                                    VD;

using namespace std;

Voronoi_1::Voronoi_1(string inFile, string outFile)
{
	_inFile = inFile;
	_outFile = outFile;
	_precision = 8;
}

/*
This voronoi implementation uses the Voronoi Diagram Adapter on top of
the Segment Delaunay Graph.
Benefits of this approach:
 - coordinates of voronoi edges are easy directly, so can be easily formatted into 
   Well-Known Text format for output.
Drawbacks of this approach:
 - Sites must be inserted individually, which is slower than the approach available to Voronoi_2
 - Iterating over voronoi output edges is very slow.  It can take hours just to iterate over the 
   output edges when saving the results to file.  The Voronoi_2 approach to saving is much faster.
Output:
  - Well-known Text LINESTRINGS, each to its own line in the output file
*/
void Voronoi_1::process() 
{
	cout << std::fixed << std::setprecision(_precision) << endl;

	ifstream ifs(_inFile);
	assert(ifs);
	//SDG2          sdg;
	//SDG2::Site_2  site;
	AT::Site_2 site;
	VD vd;
	cout << "Inserting sites into diagram..." << endl;
	// read the sites from the stream and insert them in the diagram
	auto t1 = std::chrono::system_clock::now();
	int num_sites = 0;
	while (ifs >> site) {
		vd.insert(site);
		num_sites++;
		if (num_sites % 50000 == 0) {
			cout << " " << num_sites;
		}
	}
	cout << endl;

	/*
	// read the sites
	//std::vector<SDG2::Site_2> sites;
	std::vector<AT::Site_2> sites;
	while (ifs >> site) {
		sites.push_back(site);
		num_sites++;
		//cout << "site #" << num_sites << ": " << site << endl;
	}
	ifs.close();
	cout << "Found " << num_sites << " sites in the input file" << endl;
	*/

	//insert all sites at once into the diagram to take advantage of a spatial sorting
	//ability
	//sdg.insert(sites.begin(), sites.end(), CGAL::Tag_true());
	cout << " - " << num_sites << " sites added" << endl;

	auto t2 = std::chrono::system_clock::now();
	// validate the diagram
	assert(vd.is_valid());
	//cout << endl << endl;
	cout << "Creating voronoi diagram" << endl;


	cout << " - Diagram complete" << endl;
	cout << " - Diagram run time: " << chrono::duration_cast<chrono::milliseconds>(t2 - t1).count() << " ms" << endl;
	cout << "Saving..." << endl;
	ofstream os(_outFile);
	os << std::fixed << std::setprecision(_precision) << endl;
	//sdg.draw_dual(os);
	/*
	SDG2::Finite_edges_iterator eit = sdg.finite_edges_begin();
	int i = 1;
	for (; eit != sdg.finite_edges_end(); ++eit) {
		sdg.draw_dual_edge(*eit, os);
		os << endl;
		if (i % 100 == 0) {
			cout << i << ". ";
		}

		i++;
	}
	*/

	//save voronoi
	/*
	VD::Face_iterator it = vd.faces_begin(),
		beyond = vd.faces_end();
	for (int f = 0; it != beyond; ++f, ++it) {
		//std::cout << "Face" << f << ": \n";
		VD::Ccb_halfedge_circulator hec = it->ccb();
		os << "POLYGON((";
		int edgeNum = 0;
		bool firstPointNotInfinity = false;
		AT::Point_2 firstPoint;
		do {
			VD::Halfedge_handle heh = static_cast<VD::Halfedge_handle>(hec);

			if (heh->has_target()) {
				//std::cout << heh->target()->point() << "\n";
				AT::Point_2 point = heh->target()->point();
				if (edgeNum == 0){
					firstPoint = point;
					firstPointNotInfinity = true;
				}
				os << point << ", ";
			}
			else {
				os << "inf inf, ";
			}
			edgeNum++;
		} while (++hec != it->ccb());
		//std::cout << std::endl;

		if (firstPointNotInfinity) {
			os << firstPoint << "))" << endl;
		}
		else {
			os << "inf inf))" << endl;
		}

	}
	*/

	//save edges to WKT
	VD::Edge_iterator it = vd.edges_begin(),
		beyond = vd.edges_end();
	int index = 0;
	for (int f = 0; it != beyond; ++f, ++it) {

		VD::Halfedge_handle heh = static_cast<VD::Halfedge_handle>(it);

		if (heh->has_target() && heh->has_source()) {

			AT::Point_2 p1 = heh->source()->point();
			AT::Point_2 p2 = heh->target()->point();
			os << "LINESTRING(";
			os << p1 << ", " << p2 << " ";
			os << ")" << endl;
			if (index % 50000 == 0) {
				cout << index << ". ";
			}
			index++;
		}

	}


	cout << endl;

	os.close();
	//sdg.clear();
	//cout << " - " << i << " output edges" << endl;
	cout << " - Done" << endl;
	cout << " - " << index << " features written" << endl;
	auto t3 = std::chrono::system_clock::now();
	cout << " - Save run time: " << chrono::duration_cast<chrono::milliseconds>(t3 - t2).count() << " ms" << endl;
	cout << " - Total run time: " << chrono::duration_cast<chrono::milliseconds>(t3 - t1).count() << " ms" << endl;

}