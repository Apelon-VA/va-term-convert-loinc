Steps to deploy new source content:

	1) Place the source files into the native-source folder
	2) Update the version number as appropriate in pom.xml
	3) Run a command like this to deploy - (maestro is a server name that must be defined with credentials in your maven configuration):
		mvn deploy -DaltDeploymentRepository=maestro::default::https://va.maestrodev.com/archiva/repository/data-files/
		
Note - new source content should not be checked into SVN.  When finished, simply empty the native-source folder.

For LOINC since Loader 2.7, we expect the csv distribution.  We require 5 files, in this case:
	1) loinc.csv
	2) map_to.csv
	3) source_organization.csv
	4) LOINC_V244_MULTI-AXIAL_HIERARCHY.CSV
	5) loinc_releasenotes.txt
	
For older versions of LOINC that predate the CSV distribution, the loader currently expects two files 
	1) - LOINCDB.TXT
	2) - LOINC_V240_MULTI_AXIAL_HIERARCHY.CSV


Each of these files should exist in the root of the native-source folder.
	
The version number in the multi-axial file can vary.
Note - Loinc also requires a classMappings file - which is part of the loinc-mojo project.  A new file should be generated 
when new data is downloaded (it comes from a non-computable PDF file).  See loinc-mojo\src\main\resources\classMappings-....
	
