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
package gov.vha.isaac.loincTP.convert;

import gov.vha.isaac.mojo.external.QuasiMojo;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.Util;
import gov.vha.isaac.ochre.api.task.TimedTask;
import gov.vha.isaac.ochre.util.WorkExecutors;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.jvnet.hk2.annotations.Service;

/**
 * 
 * Loader code to convert Loinc into the workbench.
 * 
 * Paths are typically controlled by maven, however, the main() method has paths configured so that they
 * match what maven does for test purposes.
 */
@Service(name = "load-loinc-tech-preview")
public class LoincTPLoaderMojo extends QuasiMojo
{

	/**
	 * Location of the input source file(s). May be a file or a directory, depending on the specific loader.
	 * Usually a directory.
	 */
	protected File loincFileLocation;

	/**
	 * Location of the input source file(s). May be a file or a directory, depending on the specific loader.
	 * Usually a directory.
	 */
	protected File loincTPFileLocation;

	/**
	 * Loader version number
	 */
	protected String loaderVersion;

	protected Hashtable<String, Integer> fieldMap_;
	protected Hashtable<Integer, String> fieldMapInverse_;

	@Override
	public void execute() throws MojoExecutionException
	{
		getLog().info("LOINC Tech Preview Processing Begins " + new Date().toString());
		
		TimedTask<Void> task = new LoincWorker();
		LookupService.getService(WorkExecutors.class).getExecutor().submit(task);
		try
		{
			Util.addToTaskSetAndWaitTillDone(task);
		}
		catch (InterruptedException | ExecutionException e)
		{
			if (task.getException() != null && task.getException() instanceof MojoExecutionException)
			{
				throw (MojoExecutionException)task.getException();
			}
			throw new MojoExecutionException("Failure", e);
		}

		getLog().info("LOINC Tech Preview Processing Ends " + new Date().toString());
	}

	private class LoincWorker extends TimedTask<Void>
	{

		@Override
		protected Void call() throws Exception
		{
			getLog().info("Processing LOINC");
			updateTitle("Processing LOINC");
			updateProgress(1, 5);
			LOINCReader loincData = null;

			try
			{
				if (!loincFileLocation.isDirectory())
				{
					throw new MojoExecutionException("loincFileLocation must point to a directory containing the required loinc data files");
				}

				if (!loincTPFileLocation.isDirectory())
				{
					throw new MojoExecutionException("loincTPFileLocation must point to a directory containing the required loinc data files");
				}

				for (File f : loincFileLocation.listFiles())
				{
					if (f.getName().toLowerCase().equals("loinc.csv"))
					{
						loincData = new CSVFileReader(f);
					}
				}

				if (loincData == null)
				{
					throw new MojoExecutionException("Could not find the loinc data file in " + loincFileLocation.getAbsolutePath());
				}

				SimpleDateFormat dateReader = new SimpleDateFormat("MMMMMMMMMMMMM yyyy"); //Parse things like "June 2014"
				Date releaseDate = dateReader.parse(loincData.getReleaseDate());

				String version = loincData.getVersion();
				fieldMap_ = loincData.getFieldMap();
				fieldMapInverse_ = loincData.getFieldMapInverse();

				getLog().info("Loading Metadata");
				updateTitle("Loading Metadata");
				updateProgress(2, 5);

				//			// Set up a meta-data root concept
				//			UUID metaDataRoot = ConverterUUID.createNamespaceUUIDFromString("metadata");
				//			conceptUtility_.createAndStoreMetaDataConcept(metaDataRoot, "LOINC Metadata", IsaacMetadataAuxiliaryBinding.ISAAC_ROOT.getPrimodialUuid(), null, dos_);
				//
				//			conceptUtility_.loadMetaDataItems(propertyTypes_, metaDataRoot, dos_);
				//
				//			String[] headerFields = loincData.getHeader();

				//Root
				//			TtkConceptChronicle rootConcept = conceptUtility_.createConcept("LOINC", IsaacMetadataAuxiliaryBinding.ISAAC_ROOT.getPrimodialUuid());
				//			conceptUtility_.addDescription(rootConcept, "LOINC", DescriptionType.SYNONYM, true, null, null, Status.ACTIVE);
				//			conceptUtility_.addDescription(rootConcept, "Logical Observation Identifiers Names and Codes", DescriptionType.SYNONYM, false, null, null, Status.ACTIVE);
				//			ConsoleUtil.println("Root concept FSN is 'LOINC' and the UUID is " + rootConcept.getPrimordialUuid());
				//
				//			conceptUtility_.addStringAnnotation(rootConcept, version, contentVersion_.getProperty("Source Version").getUUID(), Status.ACTIVE);
				//			conceptUtility_.addStringAnnotation(rootConcept, loincData.getReleaseDate(), contentVersion_.getProperty("Release Date").getUUID(), Status.ACTIVE);
				//			conceptUtility_.addStringAnnotation(rootConcept, projectVersion, contentVersion_.RELEASE.getUUID(), Status.ACTIVE);
				//			conceptUtility_.addStringAnnotation(rootConcept, loaderVersion, contentVersion_.LOADER_VERSION.getUUID(), Status.ACTIVE);

				// load the data
				getLog().info("Reading data file into memory.");
				updateTitle("Reading data file into memory");
				updateProgress(2, 5);
				int conCounter = 0;

				int dataRows = 0;
				{
					String[] line = loincData.readLine();
					dataRows++;
					while (line != null)
					{
						if (line.length > 0)
						{
							//	processDataLine(line);
						}
						line = loincData.readLine();
						dataRows++;
						if (dataRows % 1000 == 0)
						{
							updateTitle("Read " + dataRows + "lines");
						}
					}
				}
				loincData.close();

				getLog().info("Read " + dataRows + " data lines from file");
				updateTitle("Read " + dataRows + " data lines from file");
				updateProgress(3, 5);
				

				getLog().info("Processed " + conCounter + " concepts total");
			}

			catch (Exception ex)
			{
				throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
			}
			finally
			{
				try
				{
					loincData.close();
				}
				catch (IOException e)
				{
					throw new RuntimeException("Failure", e);
				}
			}
			return null;
		}
	}
}
