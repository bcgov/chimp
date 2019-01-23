#include "pch.h"
#include <string>
#include <iostream>

// define the kernel
#include <CGAL/Interval_nt.h>
#include <CGAL/Simple_cartesian.h>
#include <CGAL/Filtered_kernel.h>
#include <CGAL/Exact_predicates_exact_constructions_kernel.h>
#include <CGAL/Exact_predicates_inexact_constructions_kernel.h>
#include <CGAL/Segment_Delaunay_graph_storage_traits_2.h>

typedef CGAL::Field_with_sqrt_tag		CM;
typedef CGAL::Field_with_sqrt_tag		FM;
typedef CGAL::Field_tag					EM;
typedef CGAL::Simple_cartesian<CGAL::Gmpq>			K;
typedef CGAL::Simple_cartesian<double>    CK;
//typedef CGAL::Simple_cartesian<double>		FK;
typedef CGAL::Simple_cartesian<Interval_nt<false> >  FK;
typedef CGAL::Exact_predicates_inexact_constructions_kernel EK;
//typedef CGAL::Filtered_kernel<CK>         K;
// typedefs for the traits and the algorithm
//#include <CGAL/Segment_Delaunay_graph_filtered_traits_2.h>
#include <CGAL/Segment_Delaunay_graph_filtered_traits_2.h>
#include <CGAL/Segment_Delaunay_graph_2.h>
#include <CGAL/Segment_Delaunay_graph_traits_2.h>
//typedef CGAL::Segment_Delaunay_graph_traits_2<EK>  Gt;
typedef CGAL::Segment_Delaunay_graph_filtered_traits_2<EK>  Gt;
//typedef CGAL::Segment_Delaunay_graph_filtered_traits_without_intersections_2<EK>  Gt;
typedef CGAL::Segment_Delaunay_graph_2<Gt>             SDG2;
typedef Gt                                     Geom_traits;
//typedef typename Storage_traits::Storage_site_2   Storage_site_2;

using namespace std;

class Voronoi_3 {

private:
	string _inFile;
	string _outFile;
	int _precision;

	string cleanLine(string line);
	void writeCleaned(ostream& out, string s);
	string lineToWkt(string line, int& num_dup_verticies_discarded);
	
	inline
	bool same_points(const SDG2::Site_2& p, const SDG2::Site_2& q, SDG2* sdg) const {
		return sdg->geom_traits().equal_2_object()(p, q);
	}

	inline
	bool is_endpoint_of_segment(const SDG2::Site_2& p, const SDG2::Site_2& s, SDG2* sdg) const
	{
		CGAL_precondition(p.is_point() && s.is_segment());
		return (same_points(p, s.source_site(), sdg) ||
			same_points(p, s.target_site(), sdg));
	}

	template < class Stream >
	int draw_skeleton(Stream& str, SDG2* sdg)
	{
		int count = 0;
		SDG2::Finite_edges_iterator eit = sdg->finite_edges_begin();
		for (; eit != sdg->finite_edges_end(); ++eit) {
			SDG2::Site_2 p = eit->first->vertex(sdg->cw(eit->second))->site();
			SDG2::Site_2 q = eit->first->vertex(sdg->ccw(eit->second))->site();

			bool is_endpoint_of_seg =
				(p.is_segment() && q.is_point() &&
					is_endpoint_of_segment(q, p, sdg)) ||
					(p.is_point() && q.is_segment() &&
						is_endpoint_of_segment(p, q, sdg));
			//bool keep = !is_endpoint_of_seg;
			bool keep = true;

			if (keep) {
				sdg->draw_dual_edge(*eit, str);
				str << endl;
				count++;
			}
		}
		return count;
	}


	bool is_edge_kept(SDG2::Edge e, SDG2* sdg)
	{
		//this logic is based on code within SDG2::draw_skeleton.
		// The 'is_endpoint_of_seg' test doesn't filter out all unneeded segments, but it does 
		// seem to filter about 40% of the unneeded segments.  Additional filters would be helpful,
		// but even without additional filters the 40% saving is at least helpful to
		// reduce the amount of processing any external filtering application needs to do

		SDG2::Site_2 p = e.first->vertex(sdg->cw(e.second))->site();
		SDG2::Site_2 q = e.first->vertex(sdg->ccw(e.second))->site();

		bool is_endpoint_of_seg =
			(p.is_segment() && q.is_point() &&
				is_endpoint_of_segment(q, p, sdg)) ||
				(p.is_point() && q.is_segment() &&
					is_endpoint_of_segment(p, q, sdg));
		bool keep = !is_endpoint_of_seg;
		return keep;
	}

	template< class Stream >
	bool drawDualEdge(SDG2::Edge e, Stream& str, SDG2* sdg)
	{
		CGAL_precondition(!sdg.is_infinite(e));

		typename SDG2::Geom_traits::Line_2          l;
		typename SDG2::Geom_traits::Segment_2       s;
		typename SDG2::Geom_traits::Ray_2           r;
		CGAL::Parabola_segment_2<Gt> ps;
		
		CGAL::Object o = sdg->primal(e);

		bool kept = false;
		if (CGAL::assign(l, o)) {
			//str << l;
			//kept = true;
		}
		if (CGAL::assign(s, o)) {
			//str << s;
			//kept = true;
		}
		if (CGAL::assign(r, o)) {
			//str << r;
			//kept = true;
		}
		if (CGAL::assign(ps, o)) {
			ps.draw(str);
			kept = true;
		}

		return kept;
	}

public:
	Voronoi_3(string inFile, string outFile);
	void process();
};