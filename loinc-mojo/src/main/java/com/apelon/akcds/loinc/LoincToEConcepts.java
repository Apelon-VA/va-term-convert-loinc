/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright
 * protection in the United States. Foreign copyrights may apply.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.apelon.akcds.loinc;

import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import gov.va.oia.terminology.converters.sharedUtils.ConverterBaseMojo;
import gov.va.oia.terminology.converters.sharedUtils.EConceptUtility;
import gov.va.oia.terminology.converters.sharedUtils.EConceptUtility.DescriptionType;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.Property;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.PropertyType;
import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.ValuePropertyPair;
import gov.va.oia.terminology.converters.sharedUtils.stats.ConverterUUID;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.ihtsdo.otf.tcc.api.coordinate.Status;
import org.ihtsdo.otf.tcc.dto.TtkConceptChronicle;
import org.ihtsdo.otf.tcc.dto.component.relationship.TtkRelationshipChronicle;
import com.apelon.akcds.loinc.propertyTypes.PT_Annotations;
import com.apelon.akcds.loinc.propertyTypes.PT_ContentVersion;
import com.apelon.akcds.loinc.propertyTypes.PT_Descriptions;
import com.apelon.akcds.loinc.propertyTypes.PT_Refsets;
import com.apelon.akcds.loinc.propertyTypes.PT_Relations;
import com.apelon.akcds.loinc.propertyTypes.PT_SkipAxis;
import com.apelon.akcds.loinc.propertyTypes.PT_SkipClass;
import com.apelon.akcds.loinc.propertyTypes.PT_SkipOther;

/**
 * 
 * Loader code to convert Loinc into the workbench.
 * 
 * Paths are typically controlled by maven, however, the main() method has paths configured so that they
 * match what maven does for test purposes.
 */
@Mojo( name = "convert-loinc-to-jbin", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class LoincToEConcepts extends ConverterBaseMojo
{
	// Want a specific handle to this - adhoc usage.
	private PT_ContentVersion contentVersion_;

	// Need a handle to these too.
	private PropertyType pt_SkipAxis_;
	private PropertyType pt_SkipClass_;
	private PT_Refsets pt_refsets_;

	private final ArrayList<PropertyType> propertyTypes_ = new ArrayList<PropertyType>();
	
	protected Hashtable<String, Integer> fieldMap_;
	protected Hashtable<Integer, String> fieldMapInverse_;
	
	private HashMap<String, HashMap<String, String>> mapToData = new HashMap<>();

	// Various caches for performance reasons
	private Hashtable<String, PropertyType> propertyToPropertyType_ = new Hashtable<String, PropertyType>();

	private final SimpleDateFormat sdf_ = new SimpleDateFormat("yyyyMMdd");

	Hashtable<UUID, TtkConceptChronicle> concepts_ = new Hashtable<UUID, TtkConceptChronicle>();

	private NameMap classMapping_;
	
	private int skippedDeletedItems = 0;

	/**
	 * Used for debug. Sets up the same paths that maven would use.... allow the code to be run standalone.
	 */
	public static void main(String[] args) throws Exception
	{
		LoincToEConcepts loincConverter = new LoincToEConcepts();
		loincConverter.outputDirectory = new File("../loinc-econcept/target/");
		loincConverter.inputFileLocation = new File("../loinc-econcept/target/generated-resources/src");
		loincConverter.converterResultVersion = "foo";
		loincConverter.loaderVersion = "foo";
		loincConverter.execute();
	}
	
	@Override
	protected boolean supportsAnnotationSkipList()
	{
		return true;
	}

	private void initProperties()
	{
		// Can't init these till we know the data version
		propertyTypes_.add(new PT_Annotations(annotationSkipList));
		propertyTypes_.add(new PT_Descriptions());
		propertyTypes_.add(pt_SkipAxis_);
		propertyTypes_.add(pt_SkipClass_);
		PT_Relations r = new PT_Relations();
		// Create relations out of the skipAxis and SkipClass
		for (String s : pt_SkipAxis_.getPropertyNames())
		{
			r.addProperty("Has_" + s);
		}
		for (String s : pt_SkipClass_.getPropertyNames())
		{
			r.addProperty("Has_" + s);
		}
		propertyTypes_.add(r);
		propertyTypes_.add(new PT_SkipOther(annotationSkipList));

		propertyTypes_.add(contentVersion_);
		
		pt_refsets_ = new PT_Refsets();
		propertyTypes_.add(pt_refsets_);
	}

	@Override
	public void execute() throws MojoExecutionException
	{
		ConsoleUtil.println("LOINC Processing Begins " + new Date().toString());
		
		LOINCReader loincData = null;
		LOINCReader mapTo = null;
		LOINCReader sourceOrg = null;
		LOINCReader loincMultiData = null;

		try
		{
			super.execute();

			if (!inputFileLocation.isDirectory())
			{
				throw new MojoExecutionException("LoincDataFiles must point to a directory containing the 3 required loinc data files");
			}
			

			for (File f : inputFileLocation.listFiles())
			{
				if (f.getName().toLowerCase().equals("loincdb.txt"))
				{
					loincData = new TxtFileReader(f);
				}
				else if (f.getName().toLowerCase().equals("loinc.csv"))
				{
					loincData = new CSVFileReader(f);
				}
				else if (f.getName().toLowerCase().equals("map_to.csv"))
				{
					mapTo = new CSVFileReader(f);
				}
				else if (f.getName().toLowerCase().equals("source_organization.csv"))
				{
					sourceOrg = new CSVFileReader(f);
				}
				else if (f.getName().toLowerCase().endsWith("multi-axial_hierarchy.csv"))
				{
					loincMultiData = new CSVFileReader(f);
				}
			}

			if (loincData == null)
			{
				throw new MojoExecutionException("Could not find the loinc data file in " + inputFileLocation.getAbsolutePath());
			}
			if (loincMultiData == null)
			{
				throw new MojoExecutionException("Could not find the multi-axial file in " + inputFileLocation.getAbsolutePath());
			}
			
			SimpleDateFormat dateReader = new SimpleDateFormat("MMMMMMMMMMMMM yyyy"); //Parse things like "June 2014"
			Date releaseDate = dateReader.parse(loincData.getReleaseDate());
			
			File binaryOutputFile = new File(outputDirectory, "loincEConcepts.jbin");
			dos_ = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(binaryOutputFile)));
			conceptUtility_ = new EConceptUtility(IsaacMetadataAuxiliaryBinding.LOINC.getPrimodialUuid(), dos_, releaseDate.getTime());
			
			contentVersion_ = new PT_ContentVersion();
			pt_SkipAxis_ = new PT_SkipAxis();
			pt_SkipClass_ = new PT_SkipClass();
			
			String version = loincData.getVersion() ;
			//String releaseDate = ;
			fieldMap_ = loincData.getFieldMap();
			fieldMapInverse_ = loincData.getFieldMapInverse();

			String mapFileName = null;

			if (version.contains("2.36"))
			{
				PropertyType.setSourceVersion(1);
				mapFileName = "classMappings-2.36.txt";
			}
			else if (version.contains("2.38"))
			{
				PropertyType.setSourceVersion(2);
				mapFileName = "classMappings-2.36.txt";  // Yes, wrong one, never made the file for 2.38
			}
			else if (version.contains("2.40"))
			{
				PropertyType.setSourceVersion(3);
				mapFileName = "classMappings-2.40.txt";
			}
			else if (version.contains("2.44"))
			{
				PropertyType.setSourceVersion(4);
				mapFileName = "classMappings-2.44.txt";
			}
			else if (version.contains("2.46"))
			{
				PropertyType.setSourceVersion(4);
				mapFileName = "classMappings-2.46.txt";
			}
			else if (version.contains("2.48"))
			{
				PropertyType.setSourceVersion(4);
				mapFileName = "classMappings-2.48.txt";
			}
			else if (version.contains("2.50"))
			{
				PropertyType.setSourceVersion(5);
				mapFileName = "classMappings-2.52.txt";  //never did a 2.50, skipped to 2.52
			}
			else if (version.contains("2.52"))
			{
				PropertyType.setSourceVersion(6);
				mapFileName = "classMappings-2.52.txt";
			}
			else
			{
				ConsoleUtil.printErrorln("ERROR: UNTESTED VERSION - NO TESTED PROPERTY MAPPING EXISTS!");
				PropertyType.setSourceVersion(6);
				mapFileName = "classMappings-2.52.txt";
			}

			classMapping_ = new NameMap(mapFileName);
			
			if (mapTo != null)
			{
				String[] line = mapTo.readLine();
				while (line != null)
				{
					if (line.length > 0)
					{
						HashMap<String, String> nestedData = mapToData.get(line[0]);
						if (nestedData == null)
						{
							nestedData = new HashMap<>();
							mapToData.put(line[0], nestedData);
						}
						if (nestedData.put(line[1], line[2]) != null)
						{
							throw new Exception("Oops - " + line[0] + " " + line[1] + " " + line[2]);
						}
					}
					line = mapTo.readLine();
				}
			}

			initProperties();

			ConsoleUtil.println("Loading Metadata");

			// Set up a meta-data root concept
			UUID metaDataRoot = ConverterUUID.createNamespaceUUIDFromString("metadata");
			conceptUtility_.createAndStoreMetaDataConcept(metaDataRoot, "LOINC Metadata", IsaacMetadataAuxiliaryBinding.ISAAC_ROOT.getPrimodialUuid(), null, dos_);

			conceptUtility_.loadMetaDataItems(propertyTypes_, metaDataRoot, dos_);

			// Load up the propertyType map for speed, perform basic sanity check
			for (PropertyType pt : propertyTypes_)
			{
				for (String propertyName : pt.getPropertyNames())
				{
					if (propertyToPropertyType_.containsKey(propertyName))
					{
						ConsoleUtil.printErrorln("ERROR: Two different property types each contain " + propertyName);
					}
					propertyToPropertyType_.put(propertyName, pt);
				}
			}
			
			if (sourceOrg != null)
			{
				TtkConceptChronicle sourceOrgConcept = conceptUtility_.createAndStoreMetaDataConcept("Source Organization", metaDataRoot, dos_);
				String[] line = sourceOrg.readLine();
				while (line != null)
				{
					//﻿"COPYRIGHT_ID","NAME","COPYRIGHT","TERMS_OF_USE","URL"
					if (line.length > 0)
					{
						TtkConceptChronicle c = conceptUtility_.createConcept(line[0], sourceOrgConcept.getPrimordialUuid());
						conceptUtility_.addDescription(c, line[1], DescriptionType.SYNONYM, true, propertyToPropertyType_.get("NAME").getProperty("NAME").getUUID(), null, Status.ACTIVE);
						conceptUtility_.addStringAnnotation(c, line[2], propertyToPropertyType_.get("COPYRIGHT").getProperty("COPYRIGHT").getUUID(), Status.ACTIVE);
						conceptUtility_.addStringAnnotation(c, line[3], propertyToPropertyType_.get("TERMS_OF_USE").getProperty("TERMS_OF_USE").getUUID(), Status.ACTIVE);
						conceptUtility_.addStringAnnotation(c, line[4], propertyToPropertyType_.get("URL").getProperty("URL").getUUID(), Status.ACTIVE);
						c.writeExternal(dos_);
					}
					line = sourceOrg.readLine();
				}
			}

			// write this at the end
			TtkConceptChronicle loincRefset = pt_refsets_.getConcept(PT_Refsets.Refsets.ALL.getProperty());

			// The next line of the file is the header.
			String[] headerFields = loincData.getHeader();

			// validate that we are configured to map all properties properly
			checkForLeftoverPropertyTypes(headerFields);
			
			ConsoleUtil.println("Metadata summary:");
			for (String s : conceptUtility_.getLoadStats().getSummary())
			{
				ConsoleUtil.println("  " + s);
			}
			conceptUtility_.clearLoadStats();

			// Root
			TtkConceptChronicle rootConcept = conceptUtility_.createConcept("LOINC", IsaacMetadataAuxiliaryBinding.ISAAC_ROOT.getPrimodialUuid());
			conceptUtility_.addDescription(rootConcept, "LOINC", DescriptionType.SYNONYM, true, null, null, Status.ACTIVE);
			conceptUtility_.addDescription(rootConcept, "Logical Observation Identifiers Names and Codes", DescriptionType.SYNONYM, false, null, null, Status.ACTIVE);
			ConsoleUtil.println("Root concept FSN is 'LOINC' and the UUID is " + rootConcept.getPrimordialUuid());

			conceptUtility_.addStringAnnotation(rootConcept, version, contentVersion_.getProperty("Source Version").getUUID(), Status.ACTIVE);
			conceptUtility_.addStringAnnotation(rootConcept, loincData.getReleaseDate(), contentVersion_.getProperty("Release Date").getUUID(), Status.ACTIVE);
			conceptUtility_.addStringAnnotation(rootConcept, converterResultVersion, contentVersion_.RELEASE.getUUID(), Status.ACTIVE);
			conceptUtility_.addStringAnnotation(rootConcept, loaderVersion, contentVersion_.LOADER_VERSION.getUUID(), Status.ACTIVE);

			concepts_.put(rootConcept.getPrimordialUuid(), rootConcept);

			// Build up the Class metadata

			TtkConceptChronicle classConcept = conceptUtility_.createConcept(pt_SkipClass_.getPropertyTypeUUID(), pt_SkipClass_.getPropertyTypeDescription(),
					rootConcept.getPrimordialUuid());
			concepts_.put(classConcept.getPrimordialUuid(), classConcept);

			for (String property : pt_SkipClass_.getPropertyNames())
			{
				TtkConceptChronicle temp = conceptUtility_.createConcept(pt_SkipClass_.getProperty(property).getUUID(), property, classConcept.getPrimordialUuid());
				concepts_.put(temp.getPrimordialUuid(), temp);
			}

			// And the axis metadata
			TtkConceptChronicle axisConcept = conceptUtility_.createConcept(pt_SkipAxis_.getPropertyTypeUUID(), pt_SkipAxis_.getPropertyTypeDescription(),
					rootConcept.getPrimordialUuid());
			concepts_.put(axisConcept.getPrimordialUuid(), axisConcept);

			for (String property : pt_SkipAxis_.getPropertyNames())
			{
				TtkConceptChronicle temp = conceptUtility_.createConcept(pt_SkipAxis_.getProperty(property).getUUID(), property, axisConcept.getPrimordialUuid());
				concepts_.put(temp.getPrimordialUuid(), temp);
			}

			// load the data
			ConsoleUtil.println("Reading data file into memory.");

			int dataRows = 0;
			{
				String[] line = loincData.readLine();
				dataRows++;
				while (line != null)
				{
					if (line.length > 0)
					{
						processDataLine(line);
					}
					line = loincData.readLine();
					dataRows++;
					if (dataRows % 1000 == 0)
					{
						ConsoleUtil.showProgress();
					}
				}
			}
			loincData.close();

			ConsoleUtil.println("Read " + dataRows + " data lines from file");

			ConsoleUtil.println("Processing multi-axial file");

			{
				// header - PATH_TO_ROOT,SEQUENCE,IMMEDIATE_PARENT,CODE,CODE_TEXT
				int lineCount = 0;
				String[] line = loincMultiData.readLine();
				while (line != null)
				{
					lineCount++;
					if (line.length > 0)
					{
						processMultiAxialData(rootConcept.getPrimordialUuid(), line);
					}
					line = loincMultiData.readLine();
					if (lineCount % 1000 == 0)
					{
						ConsoleUtil.showProgress();
					}
				}
				loincMultiData.close();
				ConsoleUtil.println("Read " + lineCount + " data lines from file");
			}

			ConsoleUtil.println("Writing jbin file");

			int conCounter = 0;
			for (TtkConceptChronicle concept : concepts_.values())
			{
				conceptUtility_.addDynamicRefsetMember(loincRefset, concept.getPrimordialUuid(), null, Status.ACTIVE, null);
				concept.writeExternal(dos_);
				conCounter++;

				if (conCounter % 10 == 0)
				{
					ConsoleUtil.showProgress();
				}
				if ((conCounter % 10000) == 0)
				{
					ConsoleUtil.println("Processed: " + conCounter + " - just completed " + concept.getDescriptions().get(0).getText());
				}
			}
			
			ConsoleUtil.println("Processed " + conCounter + " concepts total");

			conceptUtility_.storeRefsetConcepts(pt_refsets_, dos_);

			ConsoleUtil.println("Data Load Summary:");
			for (String s : conceptUtility_.getLoadStats().getSummary())
			{
				ConsoleUtil.println("  " + s);
			}

			ConsoleUtil.println("Skipped " + skippedDeletedItems + " Loinc codes because they were flagged as DELETED and they had no desriptions.");
			
			// this could be removed from final release. Just added to help debug editor problems.
			ConsoleUtil.println("Dumping UUID Debug File");
			ConverterUUID.dump(outputDirectory, "loincUuid");
			ConsoleUtil.println("LOINC Processing Completes " + new Date().toString());
			ConsoleUtil.writeOutputToFile(new File(outputDirectory, "ConsoleOutput.txt").toPath());
		}
		catch (Exception ex)
		{
			throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
		}
		finally
		{
			if (dos_ != null)
			{
				try
				{
					dos_.flush();
					dos_.close();
					loincData.close();
					loincMultiData.close();
					if (mapTo != null)
					{
						mapTo.close();
					}
					if (sourceOrg != null)
					{
						sourceOrg.close();
					}
				}
				catch (IOException e)
				{
					throw new MojoExecutionException(e.getLocalizedMessage(), e);
				}
			}
		}
	}

	private void processDataLine(String[] fields) throws ParseException, IOException
	{
		Integer index = fieldMap_.get("DT_LAST_CH");
		if (index == null)
		{
			index = fieldMap_.get("DATE_LAST_CHANGED");  // They changed this in 2.38 release
		}
		String lastChanged = fields[index];
		long time = (StringUtils.isBlank(lastChanged) ? conceptUtility_.defaultTime_ : sdf_.parse(lastChanged).getTime());

		Status status = mapStatus(fields[fieldMap_.get("STATUS")]);

		String code = fields[fieldMap_.get("LOINC_NUM")];

		TtkConceptChronicle concept = conceptUtility_.createConcept(buildUUID(code), time, status);
		ArrayList<ValuePropertyPair> descriptions = new ArrayList<>();

		for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++)
		{
			if (fields[fieldIndex] != null && fields[fieldIndex].length() > 0)
			{
				PropertyType pt = propertyToPropertyType_.get(fieldMapInverse_.get(fieldIndex));
				if (pt == null)
				{
					ConsoleUtil.printErrorln("ERROR: No property type mapping for the property " + fieldMapInverse_.get(fieldIndex) + ":" + fields[fieldIndex]);
					continue;
				}

				Property p = pt.getProperty(fieldMapInverse_.get(fieldIndex));

				if (pt instanceof PT_Annotations)
				{
					if ((p.getSourcePropertyNameFSN().equals("COMMON_TEST_RANK") || p.getSourcePropertyNameFSN().equals("COMMON_ORDER_RANK") 
							|| p.getSourcePropertyNameFSN().equals("COMMON_SI_TEST_RANK")) && fields[fieldIndex].equals("0"))
					{
						continue;  //Skip attributes of these types when the value is 0
					}
					else if (p.getSourcePropertyNameFSN().equals("RELATEDNAMES2") || p.getSourcePropertyNameFSN().equals("RELAT_NMS"))
					{
						String[] values = fields[fieldIndex].split(";");
						TreeSet<String> uniqueValues = new TreeSet<>();
						for (String s : values)
						{
							s = s.trim();
							if (s.length() > 0)
							{
								uniqueValues.add(s);
							}
						}
						for (String s : uniqueValues)
						{
							conceptUtility_.addStringAnnotation(concept, s, p.getUUID(), (p.isDisabled() ? Status.INACTIVE : Status.ACTIVE));
						}
					}
					else
					{
						conceptUtility_.addStringAnnotation(concept, fields[fieldIndex], p.getUUID(), (p.isDisabled() ? Status.INACTIVE : Status.ACTIVE));
					}
				}
				else if (pt instanceof PT_Descriptions)
				{
					//Gather for later
					descriptions.add(new ValuePropertyPair(fields[fieldIndex], p));
				}
				else if (pt instanceof PT_SkipAxis)
				{
					// See if this class object exists yet.
					UUID potential = ConverterUUID.createNamespaceUUIDFromString(pt_SkipAxis_.getPropertyTypeDescription() + ":" +
							fieldMapInverse_.get(fieldIndex) + ":" + fields[fieldIndex], true);

					TtkConceptChronicle axisConcept = concepts_.get(potential);
					if (axisConcept == null)
					{
						axisConcept = conceptUtility_.createConcept(potential, fields[fieldIndex]);
						conceptUtility_.addRelationship(axisConcept, pt_SkipAxis_.getProperty(fieldMapInverse_.get(fieldIndex)).getUUID());
						concepts_.put(axisConcept.getPrimordialUuid(), axisConcept);
					}
					// We changed these from attributes to relations
					// conceptUtility_.addAnnotation(concept, axisConcept, pt_SkipAxis_.getPropertyUUID(fieldMapInverse_.get(fieldIndex)));
					String relTypeName = "Has_" + fieldMapInverse_.get(fieldIndex);
					PropertyType relType = propertyToPropertyType_.get(relTypeName);
					conceptUtility_.addRelationship(concept, axisConcept.getPrimordialUuid(), relType.getProperty(relTypeName).getUUID(), null);
				}
				else if (pt instanceof PT_SkipClass)
				{
					// See if this class object exists yet.
					UUID potential = ConverterUUID.createNamespaceUUIDFromString(pt_SkipClass_.getPropertyTypeDescription() + ":" +
							fieldMapInverse_.get(fieldIndex) + ":" + fields[fieldIndex], true);

					TtkConceptChronicle classConcept = concepts_.get(potential);
					if (classConcept == null)
					{
						classConcept = conceptUtility_.createConcept(potential, classMapping_.getMatchValue(fields[fieldIndex]));
						if (classMapping_.hasMatch(fields[fieldIndex]))
						{
							conceptUtility_.addStringAnnotation(classConcept, fields[fieldIndex], propertyToPropertyType_.get("ABBREVIATION").getProperty("ABBREVIATION")
									.getUUID(), Status.ACTIVE);
						}
						conceptUtility_.addRelationship(classConcept, pt_SkipClass_.getProperty(fieldMapInverse_.get(fieldIndex)).getUUID());
						concepts_.put(classConcept.getPrimordialUuid(), classConcept);
					}
					// We changed these from attributes to relations
					// conceptUtility_.addAnnotation(concept, classConcept, pt_SkipClass_.getPropertyUUID(fieldMapInverse_.get(fieldIndex)));
					String relTypeName = "Has_" + fieldMapInverse_.get(fieldIndex);
					PropertyType relType = propertyToPropertyType_.get(relTypeName);
					conceptUtility_.addRelationship(concept, classConcept.getPrimordialUuid(), relType.getProperty(relTypeName).getUUID(), null);
				}
				else if (pt instanceof PT_Relations)
				{
					conceptUtility_.addRelationship(concept, buildUUID(fields[fieldIndex]), pt.getProperty(fieldMapInverse_.get(fieldIndex)), null);
				}
				else if (pt instanceof PT_SkipOther)
				{
					conceptUtility_.getLoadStats().addSkippedProperty();
				}
				else
				{
					ConsoleUtil.printErrorln("oops - unexpected property type: " + pt);
				}
			}
		}
		
		//MAP_TO moved to a different file in 2.42.
		HashMap<String, String> mappings = mapToData.get(code);
		if (mappings != null)
		{
			for (Entry<String, String> mapping : mappings.entrySet())
			{
				String target = mapping.getKey();
				String comment = mapping.getValue();
				TtkRelationshipChronicle r = conceptUtility_.addRelationship(concept, buildUUID(target), propertyToPropertyType_.get("MAP_TO").getProperty("MAP_TO"), null);
				if (comment != null && comment.length() > 0)
				{
					conceptUtility_.addStringAnnotation(r, comment, propertyToPropertyType_.get("COMMENT").getProperty("COMMENT").getUUID(), Status.ACTIVE);
				}
			}
		}
		
		//Now add all the descriptions
		if (descriptions.size() == 0)
		{
			if ("DEL".equals(fields[fieldMap_.get("CHNG_TYPE")]))
			{
				//They put a bunch of these in 2.44... leaving out most of the important info... just makes a mess.  Don't load them.
				skippedDeletedItems++;
				return;
			}
			else
			{
				ConsoleUtil.printErrorln("ERROR: no name for " + code);
				conceptUtility_.addFullySpecifiedName(concept, code);
			}
		}
		else
		{
			conceptUtility_.addDescriptions(concept, descriptions);
		}

		TtkConceptChronicle current = concepts_.put(concept.getPrimordialUuid(), concept);
		if (current != null)
		{
			ConsoleUtil.printErrorln("Duplicate LOINC code (LOINC_NUM):" + code);
		}
	}

	private void processMultiAxialData(UUID rootConcept, String[] line)
	{
		// PATH_TO_ROOT,SEQUENCE,IMMEDIATE_PARENT,CODE,CODE_TEXT
		// This file format used to be a disaster... but it looks like since 2.40, they encode proper CSV, so I've thrown out the custom parsing.
		// If you need the old custom parser that reads the crap they used to produce as 'CSV', look at the SVN history for this method. 

		String pathString = line[0];
		String[] pathToRoot = (pathString.length() > 0 ? pathString.split("\\.") : new String[] {});

		String sequence = line[1];
		
		String immediateParentString = line[2];

		UUID immediateParent = (immediateParentString == null || immediateParentString.length() == 0 ? rootConcept : buildUUID(immediateParentString));

		String code = line[3];

		String codeText = line[4];

		if (code.length() == 0 || codeText.length() == 0)
		{
			ConsoleUtil.printErrorln("missing code or text!");
		}

		UUID potential = buildUUID(code);

		TtkConceptChronicle concept = concepts_.get(potential);
		if (concept == null)
		{
			concept = conceptUtility_.createConcept(potential);
			if (sequence != null && sequence.length() > 0)
			{
				conceptUtility_.addStringAnnotation(concept, sequence, propertyToPropertyType_.get("SEQUENCE").getProperty("SEQUENCE").getUUID(), Status.ACTIVE);
			}

			if (immediateParentString != null && immediateParentString.length() > 0)
			{
				conceptUtility_.addStringAnnotation(concept, immediateParentString, propertyToPropertyType_.get("IMMEDIATE_PARENT").getProperty("IMMEDIATE_PARENT")
						.getUUID(), Status.ACTIVE);
			}

			ValuePropertyPair vpp = new ValuePropertyPair(codeText, propertyToPropertyType_.get("CODE_TEXT").getProperty("CODE_TEXT"));
			conceptUtility_.addDescriptions(concept, Arrays.asList(vpp));  //This will get added as FSN

			conceptUtility_.addRelationship(concept, immediateParent, propertyToPropertyType_.get("Multiaxial Child Of").getProperty("Multiaxial Child Of"), null);

			if (pathString != null && pathString.length() > 0)
			{
				conceptUtility_.addStringAnnotation(concept, pathString, propertyToPropertyType_.get("PATH_TO_ROOT").getProperty("PATH_TO_ROOT").getUUID(), Status.ACTIVE);
			}
			conceptUtility_.addStringAnnotation(concept, code, propertyToPropertyType_.get("CODE").getProperty("CODE").getUUID(), Status.ACTIVE);

			concepts_.put(concept.getPrimordialUuid(), concept);
		}

		// Make sure everything in pathToRoot is linked.
		checkPath(concept, pathToRoot);
	}

	private void checkPath(TtkConceptChronicle concept, String[] pathToRoot)
	{
		// The passed in concept should have a relation to the item at the end of the root list.
		for (int i = (pathToRoot.length - 1); i >= 0; i--)
		{
			boolean found = false;
			UUID target = buildUUID(pathToRoot[i]);
			List<TtkRelationshipChronicle> rels = concept.getRelationships();
			if (rels != null)
			{
				for (TtkRelationshipChronicle rel : rels)
				{
					if (rel.getC1Uuid().equals(concept.getPrimordialUuid()) && rel.getC2Uuid().equals(target))
					{
						found = true;
						break;
					}
				}
			}
			if (!found)
			{
				conceptUtility_.addRelationship(concept, target, propertyToPropertyType_.get("Multiaxial Child Of").getProperty("Multiaxial Child Of"), null);
			}
			concept = concepts_.get(target);
			if (concept == null)
			{
				ConsoleUtil.printErrorln("Missing concept! " + pathToRoot[i]);
				break;
			}
		}
	}

	private Status mapStatus(String status) throws IOException
	{
		//TODO going to need to capture this as an attribute now - since WB only allows t/f
		if (status.equals("ACTIVE") || status.equals("TRIAL") || status.equals("DISCOURAGED"))
		{
			return Status.ACTIVE;
		}
		else if (status.equals("DEPRECATED"))
		{
			return Status.INACTIVE;
		}
		else
		{
			ConsoleUtil.printErrorln("No mapping for status: " + status);
			return Status.ACTIVE;
		}
	}

	private void checkForLeftoverPropertyTypes(String[] fileColumnNames) throws Exception
	{
		for (String name : fileColumnNames)
		{
			PropertyType pt = propertyToPropertyType_.get(name);
			if (pt == null)
			{
				ConsoleUtil.printErrorln("ERROR:  No mapping for property type: " + name);
			}
		}
	}

	/**
	 * Utility to help build UUIDs in a consistent manner.
	 */
	private UUID buildUUID(String uniqueIdentifier)
	{
		return ConverterUUID.createNamespaceUUIDFromString(uniqueIdentifier, true);
	}
}
