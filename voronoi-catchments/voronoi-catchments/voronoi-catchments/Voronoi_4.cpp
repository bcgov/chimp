#include "pch.h"
#include "Voronoi_4.h"


//if using the CGAL:Gmpq exact number type in a kernel, consider wrapping it in CGAL::Lazy_exact_nt
// to get some extra performance from the algorithm

using namespace std;

Voronoi_4::Voronoi_4(string inFile, string outFile)
{
	_inFile = inFile;
	_outFile = outFile;
	_precision = 11;
	_inputMinX = 99999999999;
	_inputMaxX = -99999999999;
	_inputMinY = 99999999999;
	_inputMaxY = -99999999999;

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
void Voronoi_4::process()
{

	CGAL::set_error_behaviour(CGAL::CONTINUE);

	cout << std::fixed << std::setprecision(_precision) << endl;

	ifstream ifs(_inFile);
	assert(ifs);
	SDG2_Voronoi_4          sdg;
	SDG2_Voronoi_4::Site_2  site;

	cout << "Loading input sites into memory..." << endl;
	// read the sites from the stream and insert them in the diagram
	auto t1 = std::chrono::system_clock::now();
	int numInputSites = 0;
	// read the input sites into memory
	std::vector<SDG2_Voronoi_4::Site_2> sites;
	while (ifs >> site) {
		recordSite(site);
		sites.push_back(site);
		numInputSites++;
		//cout << site << endl;
	}
	
	ifs.close();
	cout << " - " << numInputSites << " input sites loaded" << endl;

	//insert all sites at once into the diagram to take advantage of a spatial sorting
	//ability
	cout << "Inserting sites into diagram..." << endl;
	sdg.insert(sites.begin(), sites.end(), CGAL::Tag_true());
	cout << " - " << numInputSites << " sites added" << endl;

	int approximateNumberOfOutputSites = numInputSites * 5;
	int outputProgressIncrement = approximateNumberOfOutputSites / 10;

	auto t2 = std::chrono::system_clock::now();
	// validate the diagram
	assert(sdg.is_valid(true, 1));
	//cout << endl << endl;


	cout << "Diagram complete" << endl;
	cout << " - Diagram run time: " << chrono::duration_cast<chrono::milliseconds>(t2 - t1).count() << " ms" << endl;
	cout << "Saving..." << endl;
	ofstream os(_outFile);
	os << std::fixed << std::setprecision(_precision);
	//sdg.draw_dual(os);


	array<double, 4> estimatedOutputBbox = estimateOutputBbox();
	cout << "Estimated output bbox:" << endl;
	cout << " " << estimatedOutputBbox[0] << "," << estimatedOutputBbox[1] << "," << estimatedOutputBbox[2] << "," << estimatedOutputBbox[3] << endl;

	SDG2_Voronoi_4::Finite_edges_iterator eit = sdg.finite_edges_begin();
	int numOutputFeaturesWritten = 0;
	stringstream ss;
	ss << std::fixed << std::setprecision(_precision);
	int numDiscardedEdges = 0;
	int numInvalidLines = 0;
	for (; eit != sdg.finite_edges_end(); ++eit) {

		//write edge to output as wkt
		ss.str(""); //clear stream when a new line starts
		try {
			sdg.draw_dual_edge(*eit, ss);
			string line = ss.str(); //can throw CGAL::Assertion_exception due to CGAL bug.  don't think this exception can be caught, but perhaps avoided with an exact kernal. possibly compile with CPPFLAG -frounding-math to help

			string cleanedLine = cleanLineWithFractions(line, estimatedOutputBbox); //when using Gmpq kernal			

			string wkt = lineToWkt(cleanedLine);

			os << wkt << endl; // << " " << line << endl;
		}
		catch (invalid_argument e) {
			numInvalidLines++;
			os << e.what() << endl;
		}

		if (numOutputFeaturesWritten % outputProgressIncrement == 0) {
			cout << numOutputFeaturesWritten << ". ";
		}

		numOutputFeaturesWritten++;
	}

	cout << endl;

	os.close();
	sdg.clear();
	cout << " - Done" << endl;
	cout << "Summary:" << endl;
	auto t3 = std::chrono::system_clock::now();
	cout << " - " << numOutputFeaturesWritten << " output features kept" << endl;
	cout << " - " << numInvalidLines << " output features discarded" << endl;
	cout << " - Save run time: " << chrono::duration_cast<chrono::milliseconds>(t3 - t2).count() << " ms" << endl;
	cout << " - Total run time: " << chrono::duration_cast<chrono::milliseconds>(t3 - t1).count() << " ms" << endl;
}

/*
returns array of minx,miny,maxx,maxy
*/
array<double, 4> Voronoi_4::estimateOutputBbox() {
	array<double, 4> bbox;
	double inputWidth = _inputMaxX - _inputMinX;
	double inputHeight = _inputMaxY - _inputMinY;
	bbox[0] = _inputMinX - inputWidth / 2;
	bbox[1] = _inputMinY - inputHeight / 2; 
	bbox[2] = _inputMaxX + inputWidth / 2;
	bbox[3] = _inputMaxY + inputHeight / 2;
	return bbox;
}

void Voronoi_4::recordPoint(SDG2_Voronoi_4::Point_2 point) {
	double x = CGAL::to_double(point.hx());
	double y = CGAL::to_double(point.hy());
	if (x < _inputMinX) {
		_inputMinX = x;
	}
	if (x > _inputMaxX) {
		_inputMaxX = x;
	}
	if (y < _inputMinY) {
		_inputMinY = y;
	}
	if (y > _inputMaxY) {
		_inputMaxY = y;
	}
}

void Voronoi_4::recordSite(SDG2_Voronoi_4::Site_2 site) {
	if (site.is_segment()) {
		recordPoint(site.source());
		recordPoint(site.target());
	}
	else {
		recordPoint(site.source());
	}
}

/*
@param integerDivisionAsString: a string of this form: "<integer numerator>/<integer denominator>"
e.g. "5248430583877686402863010563175940000051/10035543122429371099999870000000000"
*/
double Voronoi_4::integerDivisionToDouble(string integerDivisionAsString) {
	int firstSlash = integerDivisionAsString.find("/");
	int endOfNumerator = firstSlash;
	int startOfDenominator = endOfNumerator + 1;

	string numeratorStr = integerDivisionAsString.substr(0, endOfNumerator);
	string denominatorStr = integerDivisionAsString.substr(startOfDenominator);

	
	if (std::count(denominatorStr.begin(), denominatorStr.end(), '.') > 1) {
		throw invalid_argument("invalid string: "+ integerDivisionAsString);
	}
	if (std::count(numeratorStr.begin(), numeratorStr.end(), '.') > 1) {
		throw invalid_argument("invalid string: " + integerDivisionAsString);
	}
	

	double numerator = stod(numeratorStr);
	double denominator = stod(denominatorStr);
	//cout << numerator << " / " << denominator << endl;

	double result = numerator / denominator;
	return result;
}

int Voronoi_4::getSplitIndex(string token, array<double, 4> outputBbox) {

	vector<int> matches = {};
	double minX = outputBbox[0];
	double minY = outputBbox[1];
	double maxX = outputBbox[2];
	double maxY = outputBbox[3];

	double x, y;
	int firstSlash = token.find("/");
	int secondSlash = token.find("/", firstSlash + 1);

	//start at first char after first slash
	//e.g.
	//6563675647647126665710282616599166298892855689059490332797803/1258664406761206254518787045057609206874505216000000000087596319614645978899631490538634169297590969903459937446602901/50346576270448250180751481802304368274980208640000000000
	//                                                              ^
	//                                                              start here

	//cout << "--------------" << endl;
	 
	//keep advancing split index to the right until both parts are in range
	for (int splitIndex = firstSlash + 1; splitIndex < secondSlash; splitIndex++) {
		string part1 = token.substr(0, splitIndex);
		string part2 = token.substr(splitIndex);

		
		if (part2.at(0) == '0') { //if first character of part2 is "0" then skip this possibility
			continue;
		}

		//cout << part1 << " " << part2 << endl;
		try {
			y = integerDivisionToDouble(part1);
			x = integerDivisionToDouble(part2);
		}
		catch (invalid_argument e) {
			//cout << "can't parse token: " << token << endl;
			//cout << " part1:" << part1 << endl;
			//cout << " part2:" << part2 << endl;
			//ignore errors.  they generally (always?, hopefully) represent this scenario:
			//  part1: -25291405836214393330868248987488621426946954500597253931008.00000000000/-    <- ends with negative sign.
			//rather than catch this as an exception, better to ad a test for it above, then continue
			continue;
		}
		

		//cout << "|" << part1Num << " " << part2Num << endl;
		//part 1 will always be the y coordinate, and part 2 will always be the x coordinate

		if (y >= minY && y <= maxY && x >= minX && x <= maxX) {
			matches.push_back(splitIndex);
		}


	}

	if (matches.size() == 1) {
		return matches.at(0);
	}
	else if (matches.size() == 0) {
		throw invalid_argument("Unable to find split index of " + token);
	}
	else {
		int splitIndex = matches.at(1);
		string part1 = token.substr(0, splitIndex);
		string part2 = token.substr(splitIndex);
		throw invalid_argument("multiple split indexes are possible: " + token + ", part1: " + part1 + ", part2:" + part2);
	}


}

string Voronoi_4::cleanLineWithFractions(string line, array<double, 4> bbox) {
	string cleanedLine = "";
	string delim = " ";
	size_t prev = 0, pos = 0;
	bool hasBrokenToken = false;

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
		int firstSlash = token.find("/");
		int secondSlash = token.find("/", firstSlash + 1);

		bool isTwoPieces = secondSlash != -1;
		if (isTwoPieces) {
			hasBrokenToken = true;
			int splitIndex = getSplitIndex(token, bbox);

			string part1 = token.substr(0, splitIndex); //+1 to include the character at the split index
			string part2 = token.substr(splitIndex);
			//cout << "split " << token << " into " << endl;
			//cout << part1 << " and " << endl;
			//cout << part2 << endl;

			double y = integerDivisionToDouble(part1);
			double x = integerDivisionToDouble(part2);

			string part1Cleaned = to_string(y);
			string part2Cleaned = to_string(x);


			token = part1Cleaned + " " + part2Cleaned;
			//token = part1 + " " + part2;

		}
		else {
			token = to_string(integerDivisionToDouble(token));
			//token = "";
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
example input:
  "1741783.51288650 521798.55879997 1741771.36900000 521930.93300000"
example output:
  "LINESTRING(1741783.51288650 521798.55879997,1741771.36900000 521930.93300000)"
*/
string Voronoi_4::lineToWkt(string line) {
	string wkt = "LINESTRING(";
	string delim = " ";
	size_t prev = 0, pos = 0;
	int index = 0;
	do
	{
		//each "x" and "y" are separated by a space
		//each pair of "x y" is separated by a comma
		if (index > 0) {
			if (index % 2 == 0) {
				wkt.append(",");
			}
			else {
				wkt.append(" ");
			}
		}
		pos = line.find(delim, prev);
		if (pos == string::npos) {
			pos = line.length();
		}
		string token = line.substr(prev, pos - prev);
		wkt.append(token);
		prev = pos + delim.length();
		index++;
	} while (pos < line.length() && prev < line.length());

	wkt.append(")");

	return wkt;
}