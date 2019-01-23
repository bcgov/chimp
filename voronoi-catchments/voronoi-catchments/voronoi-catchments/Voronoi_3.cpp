#include "pch.h"
#include "Voronoi_3.h"

//standard includes
#include <iostream>
#include <fstream>
#include <cassert>
#include <string>
#include <chrono>
#include <sstream> 

//CGAL general
#include <CGAL/exceptions.h>
#include <CGAL/assertions.h>
#include <CGAL/assertions_behaviour.h>



using namespace std;

Voronoi_3::Voronoi_3(string inFile, string outFile)
{
	_inFile = inFile;
	_outFile = outFile;
	_precision = 8;
}


void my_failure_handler(
	const char *type,
	const char *expr,
	const char* file,
	int line,
	const char* msg)
{
	cout << "err: " << msg << endl;
}

/*
This voronoi implementation uses the Segment Delaunay Graph directly.  (The dual of
a Segment Delaunay Graph is a voronoi diagram.)
Benefits of this approach:
 - site inserts can be done with an insert(iterator_of_sites,...) method, which is faster than
   insert(site) method.
 - saving to file can be done relatively fast using a slightly customized (hacked) version
   of the draw_dual(stream) method
-  this implementation has successfully run against large data sets fairly fast. eg.
	 Test 1: 500000+ input sites, 3000000+ output edges, total run time ~3min (including saving), total memory usage ~700MB
Drawbacks of this approach:
 - outputting the voronoi edges relies on a buggy CGAL function which a) writes all edges to the same line of a file
   and b) incorrectly concatenates coordinates rather than separating by whitespace.
   a) is overcome with a slight hack re-implements CGAL's draw_dual function
   b) is overcome with a custom function to clean the line
Output:
 - Well-known Text LINESTRINGS, each to its own line in the output file
*/
void Voronoi_3::process()
{

	CGAL::set_error_behaviour(CGAL::CONTINUE);
	CGAL::set_error_handler(my_failure_handler);

	cout << std::fixed << std::setprecision(_precision) << endl;

	ifstream ifs(_inFile);
	assert(ifs);
	SDG2          sdg;
	SDG2::Site_2  site;

	cout << "Loading input sites into memory..." << endl;
	// read the sites from the stream and insert them in the diagram
	auto t1 = std::chrono::system_clock::now();
	int numInputSites = 0;
	// read the input sites into memory
	std::vector<SDG2::Site_2> sites;
	while (ifs >> site) {
		sites.push_back(site);
		numInputSites++;
		//cout << "site #" << num_sites << ": " << site << endl;
	}
	ifs.close();
	cout << " - " << numInputSites << " input sites loaded" << endl;


	//insert all sites at once into the diagram to take advantage of a spatial sorting
	//ability
	cout << "Inserting sites into diagram..." << endl;
	sdg.insert(sites.begin(), sites.end(), CGAL::Tag_true());
	cout << " - " << numInputSites << " sites added" << endl;

	int approximateNumberOfOutputSites = numInputSites * 5;
	int output_progress_increment = approximateNumberOfOutputSites / 10;

	auto t2 = std::chrono::system_clock::now();
	// validate the diagram
	assert(sdg.is_valid(true, 1));
	//cout << endl << endl;

	cout << "Diagram complete" << endl;
	cout << " - Time to create diagram: " << chrono::duration_cast<chrono::milliseconds>(t2 - t1).count() << " ms" << endl;
	cout << " - Is valid?: " << sdg.is_valid(false, 1) << endl;
	cout << "Saving..." << endl;

	ofstream os(_outFile);
	os << std::fixed << std::setprecision(_precision);

	stringstream ss;
	ss << std::fixed << std::setprecision(_precision);
	
	//old 1.  sdg.draw_skeleton is no longer used because I reimplemented it with some slight improvements
	//sdg.draw_skeleton(ss);
	//int num_output_edges = draw_skeleton(ss, &sdg);
	
	//old 2.  cleanLine is no longer used because the code below adds newlines after each output line.
	//string cleaned = cleanLine(ss.str());
	//writeCleaned(os, cleaned);

	int num_kept = 0;
	int num_discarded = 0;
	int num_edges = 0;
	int num_dup_points_removed = 0;
	SDG2::Finite_edges_iterator eit = sdg.finite_edges_begin();
	for (; eit != sdg.finite_edges_end(); ++eit) {
		ss.str("");
		bool keep = is_edge_kept(*eit, &sdg);
		if (keep) {
			sdg.draw_dual_edge(*eit, ss);
			string line = ss.str();
			line = cleanLine(line);
			int num_dups;
			string wkt = lineToWkt(line, num_dups);
			num_dup_points_removed += num_dups;
			os << wkt << endl;
			num_kept++;
		}
		else {
			num_discarded++;
		}

		if (num_edges % output_progress_increment == 1) {
			cout << " " << num_edges;
		}
		num_edges++;
	}


	//cleanup

	os.close();
	sdg.clear();

	//print summary

	cout << endl;
	cout << " - Done" << endl;
	auto t3 = std::chrono::system_clock::now();
	cout << " - Time to save: " << chrono::duration_cast<chrono::milliseconds>(t3 - t2).count() << " ms" << endl;
	cout << "Summary:" << endl;
	cout << " - " << num_edges << " voronoi edges total" << endl;
	cout << " - " << num_kept << " (" << (num_kept * 100.0f / num_edges) << "%)" << " voronoi edges kept" << endl;
	cout << " - " << num_discarded << " (" << (num_discarded * 100.0f / num_edges) << "%)" << " voronoi edges discarded" << endl;
	cout << " - " << num_dup_points_removed << " duplicated vertices removed" << endl;
	cout << " - Total run time: " << chrono::duration_cast<chrono::milliseconds>(t3 - t1).count() << " ms" << endl;
}



void Voronoi_3::writeCleaned(ostream& out, string s) {
	string delim = "\n";
	size_t prev = 0, pos = 0;
	do
	{
		pos = s.find(delim, prev);
		if (pos == string::npos) {
			pos = s.length();
		}
		string line = s.substr(prev, pos - prev);
		int num_dups;
		string wkt = lineToWkt(line, num_dups);
		out << wkt << endl;

		prev = pos + delim.length();
	} while (pos < s.length() && prev < s.length());
}

/*
- fix concatenated coordinates (split them apart)
example input:
  1741783.51288650 521798.558799971741771.36900000 521930.93300000
example output:
  1741783.51288650 521798.55879997 1741771.36900000 521930.93300000
								  ^
								  Added a space
*/
string Voronoi_3::cleanLine(string line) {
	string cleanedLine = "";
	string delim = " ";
	string replacement = " ";
	size_t prev = 0, pos = 0;

	//iterate over each space-delimited token of the input line
	//check each token for validity.  if valid, keep it "as is".
	//if invalid, fix.
	do
	{
		pos = line.find(delim, prev);
		if (pos == string::npos) {
			pos = line.length();
		}
		string token = line.substr(prev, pos - prev);

		//check if the token should actually be two tokens
		int firstDot = token.find(".");
		int endOfFirstVal = firstDot + 1 + _precision;
		bool isTwoPieces = token.length() > endOfFirstVal;
		if (isTwoPieces) {
			string part1 = token.substr(0, endOfFirstVal);
			string part2 = token.substr(endOfFirstVal);
			token = part1 + replacement + part2;
		}

		cleanedLine.append(token);
		cleanedLine.append(" ");
		prev = pos + delim.length();
	} while (pos < line.length() && prev < line.length());

	//if (line != cleanedLine) {
	//	cout << "orig:" << line << endl;
	//	cout << "cleaned:" << cleanedLine << endl;
	//}

	return cleanedLine;
}


/*
converts point string into WKT string.  also filters out sequential repeated points.
example input:
  "1741783.51288650 521798.55879997 1741771.36900000 521930.93300000"
example output:
  "LINESTRING(1741783.51288650 521798.55879997,1741771.36900000 521930.93300000)"

*/
string Voronoi_3::lineToWkt(string line, int& num_dup_verticies_discarded) {
	num_dup_verticies_discarded = 0;
	string wkt = "LINESTRING(";
	string delim = " ";
	size_t prev = 0, pos = 0;
	int index = 1;
	int num_pairs_kept = 0;
	string last_pair = "";
	string pair = "";
	do
	{
		pos = line.find(delim, prev);
		if (pos == string::npos) {
			pos = line.length();
		}
		string token = line.substr(prev, pos - prev);
		pair.append(token);

		prev = pos + delim.length();
		
		//each "x" and "y" are separated by a space
		//each pair of "x y" is separated by a comma
		

		bool is_pair_complete = index % 2 == 0;
		bool is_not_first_pair = index >= 3;
		if (is_pair_complete) { //token is y of pair (pair finished)
			//cout << "'" << last_pair << "', '" << pair << "'" << endl;
			if (pair != last_pair) { //don't repeat duplicate points
				if (is_not_first_pair) {
					wkt.append(",");
				}
				wkt.append(pair);
				num_pairs_kept++;
			}
			else {
				num_dup_verticies_discarded++;
			}
			last_pair = pair;
			pair = "";
		}
		else { //token is x of pair (pair just started)
			pair.append(" ");
		}
		
		index++;
	} while (pos < line.length() && prev < line.length());

	wkt.append(")");

	if (num_pairs_kept == 1) {
		return "invalid: "+line;
	}
	return wkt;
}