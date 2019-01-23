#include "pch.h"

// standard includes
#include <string>
#include <iostream>


#include "Voronoi_1.h"
#include "Voronoi_2.h"
//#include "Voronoi_3.h"
#include "Voronoi_4.h"
#include "Voronoi_5.h"

using namespace std;

int main(int argc, char *argv[]) {
	
	string inFile;
	string outFile;
	string configNum;

	if (argc == 4) {
		inFile = argv[1];
		outFile = argv[2];
		configNum = argv[3];
		//cout << "Invalid input.  Usage: voronoi-catchments.exe [input filename] [output filename]" << endl;
		//return;
	}

	else {
		cout << "no input params specified.  using default values" << endl;

		//inFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/richelieu/test-areas/voronoi-input-richelieu-test-1.txt";
		//outFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/richelieu/test-areas/voronoi-output-richelieu-test-1.wkt";
		
		//inFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/richelieu-voronoi-input-1.txt";
		//outFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/richelieu-voronoi-output-1.wkt";

		//inFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/richelieu-voronoi-input-2.txt";
		//outFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/richelieu-voronoi-output-2.wkt";

		inFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/fwa-kotl-water-features-18.txt";
		outFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/fwa-kotl-voronoi-output-18.wkt";

		//inFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/fwa-kotl-water-features-simplified.txt";
		//outFile = "C:/Users/Brock/Documents/BandersGeo/projects/catchment-delineation/data/fwa-kotl-voronoi-output-21.wkt";

		configNum = 4;
	}
	

	
	
	//------------
	
	cout << "Inputs:" << endl;
	cout << " - inFile: " << inFile << endl;
	cout << " - outFile: " << outFile << endl;
	cout << " - voronoiConfigNum: " << configNum << endl;

	if (configNum == "1") {
		Voronoi_1 voronoi = Voronoi_1(inFile, outFile); //Voronoi Adapter
		voronoi.process();
	}
	else if (configNum == "2") {
		Voronoi_2 voronoi = Voronoi_2(inFile, outFile); //SDG
		voronoi.process();
	}
	else if (configNum == "3") {
		//#include "Voronoi_3.h"
		//Voronoi_3 voronoi = Voronoi_3(inFile, outFile); //SDG, alternative output
		//voronoi.process();
		cout << "configNum: " << configNum << " is not available" << endl;
		return 1;
	}
	else if (configNum == "4") {
		Voronoi_4 voronoi = Voronoi_4(inFile, outFile); //SDG, another alternative. Kernel<Cartesian<Quotient<Float>>>
		voronoi.process();
	}
	else if (configNum == "5") {
		Voronoi_5 voronoi = Voronoi_5(inFile, outFile); //SDG, another alternative.  Kernel Cartesian<Gmpq>
		voronoi.process();
	}
	else {
		cout << "Invalid configNum: " << configNum << endl;
		return 1;
	}

	
}

