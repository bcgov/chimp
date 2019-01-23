#include "pch.h"
#include <string>
using namespace std;
class Voronoi_2 {

private:
	string _inFile;
	string _outFile;
	int _precision;
	
	double integerDivisionToDouble(string integerDivisionAsString);
	int getSplitIndex(string token, double min, double max);
	string cleanLineGmpq(string line);
	string cleanLine(string line);
	string lineToWkt(string line);

public:
	Voronoi_2(string inFile, string outFile);
	void process();
};