#include "pch.h"
#include <string>
using namespace std;
class Voronoi_1 {
private:
	string _inFile;
	string _outFile;
	int _precision;
public:
	Voronoi_1(string inFile, string outFile);
	void process();
};